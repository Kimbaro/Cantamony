package com.talobin.music.parser.model

import com.tickaroo.tikxml.annotation.Attribute
import com.tickaroo.tikxml.annotation.Element
import com.tickaroo.tikxml.annotation.Xml

@Xml(name = "score-partwise")
data class ScorePartWise(
    @Attribute
        var version: String?,

    @Element
        var identification: Identification?,

    @Element(name = "part-list")
        var partList: PartList?,

    @Element(name = "part")
        var parts: List<Part>?
)
