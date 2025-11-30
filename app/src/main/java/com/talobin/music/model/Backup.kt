package com.talobin.music.parser.model

import com.tickaroo.tikxml.annotation.PropertyElement
import com.tickaroo.tikxml.annotation.Xml

@Xml
data class Backup(
    @PropertyElement
    val duration: String?
)

