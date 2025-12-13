package com.talobin.music.parser.model

import com.tickaroo.tikxml.annotation.Attribute
import com.tickaroo.tikxml.annotation.PropertyElement
import com.tickaroo.tikxml.annotation.Xml

@Xml
data class Rest(
    @PropertyElement(name = "display-step")
    val displayStep: String?,
    
    @PropertyElement(name = "display-octave")
    val displayOctave: String?,
    
    @Attribute
    val measure: String?  // "yes" for whole measure rests
)


