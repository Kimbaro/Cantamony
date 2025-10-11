package com.robsonmartins.androidmidisynth.dto

import com.robsonmartins.androidmidisynth.SynthManager

data class MidiInstance(
    val synth: SynthManager,
    val name: String,
    var isOn: Boolean = true,
    var bpm: Double = 120.0
)