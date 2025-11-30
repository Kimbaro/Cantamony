package com.talobin.music.parser.model

import com.tickaroo.tikxml.annotation.Element
import com.tickaroo.tikxml.annotation.Xml

@Xml
data class Articulations(
    @Element
    val staccato: Staccato?,
    
    @Element
    val accent: Accent?,
    
    @Element
    val tenuto: Tenuto?,
    
    @Element
    val marcato: Marcato?,
    
    @Element
    val staccatissimo: Staccatissimo?
)