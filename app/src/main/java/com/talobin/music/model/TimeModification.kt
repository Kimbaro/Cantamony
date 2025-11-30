package com.talobin.music.parser.model

import com.tickaroo.tikxml.annotation.PropertyElement
import com.tickaroo.tikxml.annotation.Xml

@Xml
data class TimeModification(
    @PropertyElement(name = "actual-notes") val actualNotes: String?,
    @PropertyElement(name = "normal-notes") val normalNotes: String?,
    @PropertyElement(name = "normal-type") val normalType: String?
)

