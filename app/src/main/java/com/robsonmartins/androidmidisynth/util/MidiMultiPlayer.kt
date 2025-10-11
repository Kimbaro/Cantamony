package com.robsonmartins.androidmidisynth.util

import com.leff.midi.MidiFile
import com.leff.midi.event.NoteOff
import com.leff.midi.event.NoteOn
import com.robsonmartins.androidmidisynth.SynthManager
import com.robsonmartins.androidmidisynth.dto.MidiEvent
import java.io.InputStream

class MidiMultiPlayer(private val synth: SynthManager) {
    private val trackPlayers = mutableListOf<MidiTrackPlayer>()

    fun loadMidi(inputStream: InputStream, bpm: Double) {
        val tracks = parseMidiFile(inputStream)
        tracks.forEach { events ->
            trackPlayers.add(MidiTrackPlayer(events, synth, bpm))
        }
    }

    fun startAll() {
        trackPlayers.forEach { it.start() }
    }

    fun stopAll() {
        trackPlayers.forEach { it.stop() }
    }

    fun setBPM(bpm: Double) {
        trackPlayers.forEach { it.setBPM(bpm) }
    }

    private fun parseMidiFile(inputStream: InputStream): List<List<MidiEvent>> {
        val midiFile = MidiFile(inputStream) // midi-parser 라이브러리
        val tracksEvents = mutableListOf<List<MidiEvent>>()
        for (track in midiFile.tracks) {
            val events = mutableListOf<MidiEvent>()
            for (event in track.events) {
                when (event) {
                    is NoteOn -> events.add(MidiEvent(event.tick, event.noteValue, event.velocity, true))
                    is NoteOff -> events.add(MidiEvent(event.tick, event.noteValue, event.velocity, false))
                    else -> {}
                }
            }
            tracksEvents.add(events)
        }
        return tracksEvents
    }
}
