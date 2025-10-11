package com.robsonmartins.androidmidisynth.dto

data class MidiAlbum(
    val albumName: String,
    val midFiles: List<String> // assets 폴더에 있는 파일명
)