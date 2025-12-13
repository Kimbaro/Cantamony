package com.talobin.music.parser.model

import com.tickaroo.tikxml.annotation.Attribute
import com.tickaroo.tikxml.annotation.Element
import com.tickaroo.tikxml.annotation.PropertyElement
import com.tickaroo.tikxml.annotation.Xml

@Xml
data class Barline(
    @Attribute
    val location: String?,  // "left", "right", "middle"
    
    @Attribute
    val barStyle: String?,  // "regular", "dotted", "dashed", "heavy", "light-light", "light-heavy", "heavy-light", "heavy-heavy", "tick", "short", "none"
    
    @Element
    val ending: Ending?,
    
    @Element
    val repeat: Repeat?,
    
    @Element
    val segno: Segno?,
    
    @Element
    val coda: Coda?,
    
    @Element
    val fermata: Fermata?
)

@Xml
data class Ending(
    @Attribute
    val number: String?,
    
    @Attribute
    val type: String?,  // "start" or "stop"
    
    @Attribute
    val printObject: String?
)

@Xml
data class Repeat(
    @Attribute
    val direction: String?,  // "forward" or "backward"
    
    @Attribute
    val times: String?,
    
    @Attribute
    val winged: String?  // "none", "straight", "curved"
)

@Xml
data class Fermata(
    @Attribute
    val type: String?,  // "upright" or "inverted"
    
    @Attribute
    val shape: String?  // "normal" or "square"
)


