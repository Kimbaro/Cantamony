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

    fun getMuteTracks():MutableMap<Int,Boolean> = muteTracks

    /** 트랙 이벤트 추가 (trackIndex 기준) */
    fun addTrack(events: List<MidiEvent>, trackIndex: Int) {
        events.forEach { it.trackIndex = trackIndex } // 이벤트에 트랙 정보 추가
        allEvents.addAll(events)
        muteTracks[trackIndex] = false
        allEvents.sortBy { it.tick } // tick 기준 정렬
    }

    /** 전체 재생 시작 */
    fun startAll() {
        if (isPlaying) return
        isPlaying = true
        playThread = thread {
            var lastTick = 0L
            for (event in allEvents) {
                if (!isPlaying) break
                val deltaTick = event.tick - lastTick
                val msPerTick = 60000.0 / (bpm * 480) // 480 ticks per beat
                val sleepTime = (deltaTick * msPerTick).toLong()
                Thread.sleep(sleepTime)
                lastTick = event.tick

                val isMuted = muteTracks[event.trackIndex] == true
                if (!isMuted) {
                    if (event.isNoteOn) synth.noteOn(event.note, event.velocity)
                    else synth.noteOff(event.note)
                }
            }
            isPlaying = false
        }
    }

    /** 전체 정지 */
    fun stopAll() {
        isPlaying = false
        playThread?.join()
        playThread = null
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
