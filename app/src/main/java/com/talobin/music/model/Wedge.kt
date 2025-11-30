package com.talobin.music.parser.model

import com.tickaroo.tikxml.annotation.Attribute
import com.tickaroo.tikxml.annotation.Xml

@Xml
data class Wedge(
    @Attribute
    val type: String?,  // "crescendo" or "diminuendo"
    
    @Attribute
    val number: String?,
    
    @Attribute(name = "default-x")
    val defaultX: String?,
    
    @Attribute(name = "default-y")
    val defaultY: String?,
    
    @Attribute(name = "relative-x")
    val relativeX: String?,
    
    @Attribute(name = "spread")
    val spread: String?  // wedge의 확장 정도
)

