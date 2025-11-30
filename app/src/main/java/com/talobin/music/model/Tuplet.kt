package com.talobin.music.parser.model

import com.tickaroo.tikxml.annotation.Attribute
import com.tickaroo.tikxml.annotation.Element
import com.tickaroo.tikxml.annotation.PropertyElement
import com.tickaroo.tikxml.annotation.Xml

@Xml
data class TupletActual(
    @PropertyElement(name = "tuplet-number") val tupletNumber: String?,
    @PropertyElement(name = "tuplet-type") val tupletType: String?
)

@Xml
data class TupletNormal(
    @PropertyElement(name = "tuplet-number") val tupletNumber: String?,
    @PropertyElement(name = "tuplet-type") val tupletType: String?
)

@Xml
data class Tuplet(
    @Attribute val type: String?,  // "start" or "stop"
    @Attribute val bracket: String?,  // "yes" or "no"
    @Attribute val number: String?,
    @Element(name = "tuplet-actual") val tupletActual: TupletActual?,
    @Element(name = "tuplet-normal") val tupletNormal: TupletNormal?
)

