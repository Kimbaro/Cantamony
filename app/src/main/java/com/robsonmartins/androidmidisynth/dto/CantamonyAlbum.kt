package com.robsonmartins.androidmidisynth.dto

import java.io.Serializable

data class CantamonyAlbum(
    val albumName: String,
    val albumAsset: Map<String, String> // assets 폴더에 있는 파일명
) : Serializable