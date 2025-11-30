package com.talobin.music.parser.model

import com.tickaroo.tikxml.annotation.Attribute
import com.tickaroo.tikxml.annotation.Element
import com.tickaroo.tikxml.annotation.PropertyElement
import com.tickaroo.tikxml.annotation.Xml

@Xml
data class Note(
    @Attribute(name = "default-x")
        val defaultX: String?,

    @Element
        val pitch: Pitch?,

    @PropertyElement
        val duration: String?,

    @PropertyElement
        val voice: String?,

    @PropertyElement
        val type: String?,
    
    @PropertyElement
        val stem: String?,

    @PropertyElement
        val accidental: String?,

    @PropertyElement
        val unpitched: String?,

    @PropertyElement
        val staff: String?,

    @PropertyElement
        val chord: String?,

    @Element
        val rest: Rest?,

    @Element(name = "notations")
        val notations: Notations?,

    @Element(name = "beam")
        val beamList: List<Beam>?,
    
    @Element(name = "dot")
        val dot: Dot?,
    
    @Element(name = "time-modification")
        val timeModification: TimeModification?
)
