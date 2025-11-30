package com.talobin.music.parser.model

import com.tickaroo.tikxml.annotation.Attribute
import com.tickaroo.tikxml.annotation.Element
import com.tickaroo.tikxml.annotation.PropertyElement
import com.tickaroo.tikxml.annotation.Xml

@Xml
data class Print(
    @Attribute(name = "new-system")
    val newSystem: String?,
    
    @Attribute(name = "new-page")
    val newPage: String?,
    
    @Element(name = "system-layout")
    val systemLayout: SystemLayout?
)

@Xml
data class SystemLayout(
    @Element(name = "system-margins")
    val systemMargins: SystemMargins?,
    
    @PropertyElement(name = "top-system-distance")
    val topSystemDistance: String?,
    
    @PropertyElement(name = "system-distance")
    val systemDistance: String?
)

@Xml
data class SystemMargins(
    @PropertyElement(name = "left-margin")
    val leftMargin: String?,
    
    @PropertyElement(name = "right-margin")
    val rightMargin: String?
)

