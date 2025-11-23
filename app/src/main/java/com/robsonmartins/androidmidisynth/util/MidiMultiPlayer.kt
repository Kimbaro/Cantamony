package com.robsonmartins.androidmidisynth.util

import com.robsonmartins.androidmidisynth.SynthManager
import com.robsonmartins.androidmidisynth.dto.MidiEvent
import kotlin.concurrent.thread

class MidiMultiPlayer(private val synth: SynthManager) {

    private val allEvents = mutableListOf<MidiEvent>()
    private var playThread: Thread? = null
    private var isPlaying = false
    private var bpm = 120.0
    private var muteTracks = mutableMapOf<Int, Boolean>() // 트랙별 mute 상태
    
    // 재생 위치 추적
    @Volatile
    private var currentTick: Long = 0L
    private var totalTicks: Long = 0L
    private var currentEventIndex: Int = 0
    
    // Seek 기능을 위한 플래그
    @Volatile
    private var seekToTick: Long? = null
    
    // 재생 중인 노트 추적 (stopAll 시 모두 끄기 위해)
    private val activeNotes = mutableSetOf<Int>()

    fun getMuteTracks(): MutableMap<Int, Boolean> = muteTracks
    
    fun isPlaying(): Boolean = isPlaying
    
    fun getCurrentTick(): Long = currentTick
    fun getTotalTicks(): Long = totalTicks

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
            var lastTick: Long
            var eventIndex: Int
            
            // Seek 처리: 현재 위치보다 앞으로 이동한 경우
            val initialSeek = seekToTick
            if (initialSeek != null) {
                val seekTick = initialSeek
                // seekTick 이후의 첫 이벤트 찾기
                eventIndex = allEvents.indexOfFirst { it.tick >= seekTick }
                    .takeIf { it >= 0 } ?: allEvents.size
                lastTick = if (eventIndex > 0) {
                    allEvents[eventIndex - 1].tick
                } else {
                    seekTick
                }
                currentTick = seekTick
                currentEventIndex = eventIndex
                seekToTick = null
            } else {
                // Seek가 없으면 현재 위치에서 시작 (또는 처음부터)
                lastTick = currentTick
                eventIndex = currentEventIndex
                // 처음 시작하는 경우 0으로 초기화
                if (eventIndex == 0 && currentTick == 0L) {
                    lastTick = 0L
                }
            }
            
            // 현재 위치부터 재생
            while (eventIndex < allEvents.size && isPlaying) {
                // Seek 중단 처리
                val seekTick = seekToTick
                if (seekTick != null) {
                    eventIndex = allEvents.indexOfFirst { it.tick >= seekTick }
                        .takeIf { it >= 0 } ?: allEvents.size
                    lastTick = if (eventIndex > 0) {
                        allEvents[eventIndex - 1].tick
                    } else {
                        seekTick
                    }
                    currentTick = seekTick
                    currentEventIndex = eventIndex
                    seekToTick = null
                    // continue 대신 while 루프의 조건으로 처리
                    continue
                }
                
                val event = allEvents[eventIndex]
                val deltaTick = event.tick - lastTick
                val msPerTick = 60000.0 / (bpm * 480)
                val sleepTime = (deltaTick * msPerTick).toLong()
                
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
                
                lastTick = event.tick
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
    }

    /** 특정 트랙 mute 설정 */
    fun setMute(trackIndex: Int, mute: Boolean) {
        muteTracks[trackIndex] = mute
    }
}
