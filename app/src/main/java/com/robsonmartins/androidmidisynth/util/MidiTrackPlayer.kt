package com.robsonmartins.androidmidisynth.util

import com.robsonmartins.androidmidisynth.SynthManager
import com.robsonmartins.androidmidisynth.dto.MidiEvent
import kotlinx.coroutines.*

class MidiTrackPlayer(
    private val events: List<MidiEvent>,
    private val synth: SynthManager,
    private var bpm: Double
) {
    private var job: Job? = null

    fun start() {
        job = CoroutineScope(Dispatchers.Default).launch {
            val startTime = System.currentTimeMillis()
            for (event in events) {
                val msPerTick = 60000.0 / (bpm * 480) // 480 ticks per quarter note
                val eventTime = startTime + (event.tick * msPerTick).toLong()
                val delayTime = eventTime - System.currentTimeMillis()
                if (delayTime > 0) delay(delayTime)

                if (event.isNoteOn) synth.noteOn(event.note, event.velocity)
                else synth.noteOff(event.note)
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    fun setBPM(newBpm: Double) {
        bpm = newBpm
    }
}
