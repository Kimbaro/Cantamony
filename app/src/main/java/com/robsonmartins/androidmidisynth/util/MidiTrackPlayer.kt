package com.robsonmartins.androidmidisynth.util

import com.robsonmartins.androidmidisynth.SynthManager
import com.robsonmartins.androidmidisynth.dto.MidiEvent
import kotlin.concurrent.thread

class MidiTrackPlayer(private val synth: SynthManager, private val events: List<MidiEvent>) {

    private var isPlaying = false
    private var playThread: Thread? = null
    private var mute = false
    private var bpm = 120.0

    fun start() {
        if (isPlaying) return
        isPlaying = true
        playThread = thread {
            var lastTick = 0L
            for (event in events) {
                if (!isPlaying) break

                val tickDiff = event.tick - lastTick
                lastTick = event.tick

                // MIDI tick → 실제 시간 변환
                val ms = (tickDiff * 500.0 / bpm).toLong()
                Thread.sleep(ms)

                if (!mute) {
                    if (event.isNoteOn) synth.noteOn(event.note, event.velocity)
                    else synth.noteOff(event.note)
                }
            }
            isPlaying = false
        }
    }

    fun isMuted() = mute

    fun stop() {
        isPlaying = false
        playThread?.join()
    }

    fun setMute(on: Boolean) {
        mute = on
    }

    fun setBPM(newBPM: Double) {
        bpm = newBPM
    }

    fun getIsPlaying(): Boolean = isPlaying
}
