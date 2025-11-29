package com.talobin.music.parser.model

import com.tickaroo.tikxml.annotation.Attribute
import com.tickaroo.tikxml.annotation.Element
import com.tickaroo.tikxml.annotation.Xml

@Xml
data class Measure(
    @Attribute
        val number: String?,
    @Attribute
        val width: String?,
    @Attribute
        val implicit: String?,

    @Element(name = "note")
        val noteList: List<Note>?,

    @Element(name = "attributes")
        val attributesList: List<Attributes>?,
    
    @Element(name = "direction")
        val directionList: List<Direction>?,
    
    @Element(name = "barline")
        val barlineList: List<Barline>?
)

