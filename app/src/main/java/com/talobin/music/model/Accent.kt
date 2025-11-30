package com.talobin.music.parser.model

import com.tickaroo.tikxml.annotation.Attribute
import com.tickaroo.tikxml.annotation.Xml

@Xml
data class Accent(
    @Attribute
    val placement: String?  // "above" or "below"
)

