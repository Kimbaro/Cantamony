package com.robsonmartins.androidmidisynth.dto

data class MidiEvent(
    val tick: Long,
    val note: Int,
    val velocity: Int,
    val isNoteOn: Boolean,
    var trackIndex: Int
)
