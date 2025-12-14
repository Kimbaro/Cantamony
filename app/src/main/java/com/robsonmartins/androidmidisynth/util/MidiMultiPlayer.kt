package com.robsonmartins.androidmidisynth.util

import android.os.SystemClock
import com.robsonmartins.androidmidisynth.SynthManager
import com.robsonmartins.androidmidisynth.dto.MidiEvent
import kotlin.concurrent.thread

class MidiMultiPlayer(private val synth: SynthManager) {

    data class TempoPoint(val tick: Long, val bpm: Double)

    private val allEvents = mutableListOf<MidiEvent>()
    private var playThread: Thread? = null
    private var isPlaying = false
    // 사용자(또는 기본) BPM. tempo map이 있으면 scale 기준으로 사용됨.
    private var bpm = 120.0
    private var ticksPerQuarter: Int = 480 // MIDI PPQ (기본값)
    private var baseTempoBpm: Double = 120.0 // tempo map의 기준 BPM(보통 tick 0)
    private var tempoPoints: List<TempoPoint> = emptyList() // tick 오름차순
    private var muteTracks = mutableMapOf<Int, Boolean>() // 트랙별 mute 상태
    
    // 재생 위치 추적
    @Volatile
    private var currentTick: Long = 0L
    private var totalTicks: Long = 0L
    private var currentEventIndex: Int = 0

    // 절대 시간 기반 스케줄링 기준점
    @Volatile
    private var baseRealtimeMs: Long = 0L
    @Volatile
    private var baseStartTick: Long = 0L
    @Volatile
    private var scheduleResetSeq: Long = 0L
    
    // Seek 기능을 위한 플래그
    @Volatile
    private var seekToTick: Long? = null
    
    // 재생 중인 노트 추적 (stopAll 시 모두 끄기 위해)
    private val activeNotes = mutableSetOf<Int>()

    fun getMuteTracks(): MutableMap<Int, Boolean> = muteTracks
    
    fun isPlaying(): Boolean = isPlaying
    
    fun getCurrentTick(): Long = currentTick
    fun getTotalTicks(): Long = totalTicks

    /** MIDI PPQ(resolution) 설정. midiFile.resolution 값을 주입해야 정확한 타이밍이 나옵니다. */
    fun setTicksPerQuarter(ppq: Int) {
        ticksPerQuarter = ppq.coerceAtLeast(1)
    }

    /**
     * MIDI tempo 이벤트(tempo map) 주입.
     * - tick 기준 오름차순이어야 하며, tick 0이 없으면 자동으로 앞에 붙입니다.
     */
    fun setTempoPoints(points: List<TempoPoint>, baseBpmFallback: Double = 120.0) {
        val sorted = points
            .filter { it.tick >= 0 && it.bpm > 0.0 }
            .sortedBy { it.tick }
        val tick0 = sorted.firstOrNull { it.tick == 0L }
        baseTempoBpm = tick0?.bpm ?: baseBpmFallback
        tempoPoints = if (tick0 != null) {
            sorted
        } else {
            listOf(TempoPoint(0L, baseTempoBpm)) + sorted
        }
    }

    /** tick 구간(fromTick..toTick)의 기대 경과시간(ms)을 tempo map + 사용자 BPM(scale)로 계산 */
    fun ticksToMs(fromTick: Long, toTick: Long): Long {
        if (toTick <= fromTick) return 0L
        val ppq = ticksPerQuarter.coerceAtLeast(1)
        val scale = if (baseTempoBpm > 0.0) (bpm / baseTempoBpm) else 1.0

        if (tempoPoints.isEmpty()) {
            val msPerTick = 60000.0 / (bpm * ppq)
            return ((toTick - fromTick) * msPerTick).toLong()
        }

        var ms = 0.0
        var curTick = fromTick
        var idx = tempoIndexAt(curTick)
        while (curTick < toTick) {
            val curTempoBpm = (tempoPoints.getOrNull(idx)?.bpm ?: baseTempoBpm) * scale
            val nextTick = tempoPoints.getOrNull(idx + 1)?.tick ?: Long.MAX_VALUE
            val endTick = minOf(toTick, nextTick)
            val msPerTick = 60000.0 / (curTempoBpm * ppq)
            ms += (endTick - curTick) * msPerTick
            curTick = endTick
            if (curTick >= nextTick) idx++
        }
        return ms.toLong()
    }

    /** 현재 tick에서 플레이어가 실제로 적용 중인 유효 BPM(tempo map + 사용자 BPM scale). */
    fun getEffectiveBpmAtTick(tick: Long): Double {
        val scale = if (baseTempoBpm > 0.0) (bpm / baseTempoBpm) else 1.0
        if (tempoPoints.isEmpty()) return bpm
        val idx = tempoIndexAt(tick)
        val rawTempo = (tempoPoints.getOrNull(idx)?.bpm ?: baseTempoBpm)
        return rawTempo * scale
    }

    fun getUserBpm(): Double = bpm

    fun getBaseTempoBpm(): Double = baseTempoBpm

    fun hasTempoMap(): Boolean = tempoPoints.isNotEmpty()

    private fun tempoIndexAt(tick: Long): Int {
        if (tempoPoints.isEmpty()) return -1
        val i = tempoPoints.binarySearch { it.tick.compareTo(tick) }
        return if (i >= 0) i else (-i - 2).coerceAtLeast(0)
    }

    private fun resetScheduleBase(startTick: Long) {
        baseRealtimeMs = SystemClock.elapsedRealtime()
        baseStartTick = startTick
        scheduleResetSeq++
    }

    /** 트랙 이벤트 추가 (trackIndex 기준) */
    fun addTrack(events: List<MidiEvent>, trackIndex: Int) {
        events.forEach { it.trackIndex = trackIndex } // 이벤트에 트랙 정보 추가
        allEvents.addAll(events)
        muteTracks[trackIndex] = false
        allEvents.sortBy { it.tick } // tick 기준 정렬
        // 최대 tick 저장
        totalTicks = allEvents.maxOfOrNull { it.tick } ?: 0L
    }

    /** 특정 tick 위치로 이동 (seek) */
    fun seekTo(tick: Long) {
        val targetTick = tick.coerceIn(0, totalTicks)
        seekToTick = targetTick
        
        // 정지 상태에서도 currentTick을 즉시 업데이트
        if (!isPlaying) {
            currentTick = targetTick
            // 해당 위치의 이벤트 인덱스 찾기
            currentEventIndex = allEvents.indexOfFirst { it.tick >= targetTick }
                .takeIf { it >= 0 } ?: allEvents.size
        }
    }

    /** 전체 재생 시작 */
    fun startAll() {
        if (isPlaying) return
        if (allEvents.isEmpty()) return // 이벤트가 없으면 재생하지 않음
        isPlaying = true
        activeNotes.clear() // 재생 시작 시 초기화
        playThread = thread {
            var eventIndex: Int
            
            // Seek 처리: 현재 위치보다 앞으로 이동한 경우
            val initialSeek = seekToTick
            if (initialSeek != null) {
                val seekTick = initialSeek
                // seekTick 이후의 첫 이벤트 찾기
                eventIndex = allEvents.indexOfFirst { it.tick >= seekTick }
                    .takeIf { it >= 0 } ?: allEvents.size
                currentTick = seekTick
                currentEventIndex = eventIndex
                seekToTick = null
            } else {
                // Seek가 없으면 현재 위치에서 시작 (또는 처음부터)
                eventIndex = currentEventIndex
            }

            // 절대시간 기준점 설정(현재 tick 기준)
            resetScheduleBase(currentTick)
            
            // 현재 위치부터 재생
            var localResetSeq = scheduleResetSeq
            var localBaseRealtimeMs = baseRealtimeMs
            var localBaseStartTick = baseStartTick
            var lastTickForSchedule = localBaseStartTick
            var tickTimeOffsetMs = 0.0
            var tempoIdx = tempoIndexAt(lastTickForSchedule)
            var currentTempoBpm = if (tempoIdx >= 0) tempoPoints.getOrNull(tempoIdx)?.bpm ?: baseTempoBpm else baseTempoBpm
            var nextTempoTick = if (tempoIdx >= 0) tempoPoints.getOrNull(tempoIdx + 1)?.tick ?: Long.MAX_VALUE else Long.MAX_VALUE
            var scale = if (baseTempoBpm > 0.0) (bpm / baseTempoBpm) else 1.0

            while (eventIndex < allEvents.size && isPlaying) {
                // Seek 중단 처리
                val seekTick = seekToTick
                if (seekTick != null) {
                    eventIndex = allEvents.indexOfFirst { it.tick >= seekTick }
                        .takeIf { it >= 0 } ?: allEvents.size
                    currentTick = seekTick
                    currentEventIndex = eventIndex
                    seekToTick = null
                    // seek 후 기준점 재설정 (오차 누적 방지)
                    resetScheduleBase(currentTick)

                    // local 스케줄 상태도 리셋
                    localResetSeq = scheduleResetSeq
                    localBaseRealtimeMs = baseRealtimeMs
                    localBaseStartTick = baseStartTick
                    lastTickForSchedule = localBaseStartTick
                    tickTimeOffsetMs = 0.0
                    tempoIdx = tempoIndexAt(lastTickForSchedule)
                    currentTempoBpm = if (tempoIdx >= 0) tempoPoints.getOrNull(tempoIdx)?.bpm ?: baseTempoBpm else baseTempoBpm
                    nextTempoTick = if (tempoIdx >= 0) tempoPoints.getOrNull(tempoIdx + 1)?.tick ?: Long.MAX_VALUE else Long.MAX_VALUE
                    scale = if (baseTempoBpm > 0.0) (bpm / baseTempoBpm) else 1.0
                    // continue 대신 while 루프의 조건으로 처리
                    continue
                }
                
                val event = allEvents[eventIndex]
                // BPM 변경(setBPM)으로 기준점이 바뀐 경우 local 상태 동기화
                val curSeq = scheduleResetSeq
                if (curSeq != localResetSeq) {
                    localResetSeq = curSeq
                    localBaseRealtimeMs = baseRealtimeMs
                    localBaseStartTick = baseStartTick
                    lastTickForSchedule = localBaseStartTick
                    tickTimeOffsetMs = 0.0
                    tempoIdx = tempoIndexAt(lastTickForSchedule)
                    currentTempoBpm = if (tempoIdx >= 0) tempoPoints.getOrNull(tempoIdx)?.bpm ?: baseTempoBpm else baseTempoBpm
                    nextTempoTick = if (tempoIdx >= 0) tempoPoints.getOrNull(tempoIdx + 1)?.tick ?: Long.MAX_VALUE else Long.MAX_VALUE
                    scale = if (baseTempoBpm > 0.0) (bpm / baseTempoBpm) else 1.0
                }

                // tick -> time(누적) 적분: tempo map이 있으면 구간별로, 없으면 상수 bpm
                if (tempoPoints.isEmpty()) {
                    val msPerTick = 60000.0 / (bpm * ticksPerQuarter)
                    tickTimeOffsetMs = ((event.tick - localBaseStartTick) * msPerTick)
                    lastTickForSchedule = event.tick
                } else {
                    // 마지막 tick -> event.tick까지 누적 증가
                    while (nextTempoTick <= event.tick) {
                        val msPerTick = 60000.0 / ((currentTempoBpm * scale) * ticksPerQuarter)
                        tickTimeOffsetMs += (nextTempoTick - lastTickForSchedule) * msPerTick
                        lastTickForSchedule = nextTempoTick
                        tempoIdx++
                        currentTempoBpm = tempoPoints.getOrNull(tempoIdx)?.bpm ?: currentTempoBpm
                        nextTempoTick = tempoPoints.getOrNull(tempoIdx + 1)?.tick ?: Long.MAX_VALUE
                    }
                    val msPerTick = 60000.0 / ((currentTempoBpm * scale) * ticksPerQuarter)
                    tickTimeOffsetMs += (event.tick - lastTickForSchedule) * msPerTick
                    lastTickForSchedule = event.tick
                }

                val targetTimeMs = localBaseRealtimeMs + tickTimeOffsetMs.toLong()
                var sleepTime = targetTimeMs - SystemClock.elapsedRealtime()
                
                // Thread.sleep 중에 interrupt를 받을 수 있도록 처리
                if (sleepTime > 0) {
                    try {
                        // 짧은 간격으로 나누어 sleep하여 즉시 중단 가능하도록
                        var remaining = sleepTime
                        while (remaining > 0 && isPlaying) {
                            val sleepChunk = minOf(remaining, 50) // 50ms 단위로 sleep
                            Thread.sleep(sleepChunk)
                            remaining -= sleepChunk
                        }
                        // sleep 중에 stopAll이 호출되면 즉시 중단
                        if (!isPlaying) break
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
                
                // 재생 중단 확인
                if (!isPlaying) break
                
                currentTick = event.tick
                currentEventIndex = eventIndex

                val isMuted = muteTracks[event.trackIndex] == true
                if (!isMuted) {
                    if (event.isNoteOn) {
                        synth.noteOn(event.note, event.velocity)
                        activeNotes.add(event.note) // 재생 중인 노트 추가
                    } else {
                        synth.noteOff(event.note)
                        activeNotes.remove(event.note) // 노트 종료 시 제거
                    }
                }
                
                eventIndex++
            }
            isPlaying = false
        }
    }

    /** 전체 정지 */
    fun stopAll() {
        isPlaying = false
        
        // 재생 중인 모든 노트 끄기
        activeNotes.forEach { note ->
            synth.noteOff(note)
        }
        activeNotes.clear()
        
        // 스레드 중단
        playThread?.interrupt() // Thread.sleep 중단
        playThread?.join()
        playThread = null
    }
    
    /** 재생 위치 초기화 */
    fun reset() {
        currentTick = 0L
        currentEventIndex = 0
        seekToTick = null
    }

    /** BPM 설정 */
    fun setBPM(newBPM: Double) {
        bpm = newBPM
        // 재생 중 BPM 변경 시 기준점 재설정(절대시간 스케줄링 드리프트/점프 방지)
        if (isPlaying) {
            resetScheduleBase(currentTick)
        }
    }

    /** 특정 트랙 mute 설정 */
    fun setMute(trackIndex: Int, mute: Boolean) {
        muteTracks[trackIndex] = mute
    }
}
