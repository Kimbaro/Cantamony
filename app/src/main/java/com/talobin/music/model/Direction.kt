package com.talobin.music.parser.model

import com.tickaroo.tikxml.annotation.Attribute
import com.tickaroo.tikxml.annotation.Element
import com.tickaroo.tikxml.annotation.PropertyElement
import com.tickaroo.tikxml.annotation.Xml

@Xml
data class Direction(
    @Attribute
    val placement: String?,  // "above" or "below"
    
    @Attribute
    val directive: String?,  // "yes" or "no"
    
    @Attribute
    val system: String?,  // "only-top", "only-bottom", etc.
    
    @Element
    val dynamics: Dynamics?,
    
    @Element(name = "direction-type")
    val directionTypeList: List<DirectionType>?,
    
    @Element
    val sound: Sound?,
    
    @Element(name = "staff")
    val staff: Staff?,
    
    @PropertyElement
    val offset: String?
)

@Xml
data class DirectionType(
    @Element
    val dynamics: Dynamics?,
    
    @Element
    val words: Words?,
    
    @Element
    val metronome: Metronome?,
    
    @Element
    val wedge: Wedge?,
    
    @Element
    val segno: Segno?,
    
    @Element
    val coda: Coda?,
    
    @Element
    val rehearsal: Rehearsal?
)

@Xml
data class Words(
    @PropertyElement
    val value: String?
)

@Xml
data class Metronome(
    @Attribute(name = "font-family")
    val fontFamily: String?,
    
    @Attribute(name = "font-style")
    val fontStyle: String?,
    
    @Attribute(name = "font-weight")
    val fontWeight: String?,
    
    @Attribute(name = "font-size")
    val fontSize: String?,
    
    @Attribute(name = "default-y")
    val defaultY: String?,
    
    @PropertyElement(name = "beat-unit")
    val beatUnit: String?,
    
    @Element(name = "per-minute")
    val perMinute: PerMinute?
)

@Xml
data class PerMinute(
    @Attribute(name = "font-family")
    val fontFamily: String?,
    
    @Attribute(name = "font-style")
    val fontStyle: String?,
    
    @Attribute(name = "font-weight")
    val fontWeight: String?,
    
    @Attribute(name = "font-size")
    val fontSize: String?,
    
    @PropertyElement
    val value: String?
)

@Xml
data class Staff(
    @PropertyElement
    val value: String?
)

@Xml
data class Segno(
    // Segno는 빈 요소이지만 TikXML을 위해 최소 하나의 속성 필요
    // MusicXML에서 segno는 보통 빈 요소이지만, 위치 속성을 가질 수 있음
    @Attribute(name = "default-x")
    val defaultX: String?,
    
    @Attribute(name = "default-y")
    val defaultY: String?
)

@Xml
data class Coda(
    // Coda는 빈 요소이지만 TikXML을 위해 최소 하나의 속성 필요
    // MusicXML에서 coda는 보통 빈 요소이지만, 위치 속성을 가질 수 있음
    @Attribute(name = "default-x")
    val defaultX: String?,
    
    @Attribute(name = "default-y")
    val defaultY: String?
)

@Xml
data class Rehearsal(
    @PropertyElement
    val value: String?
)

@Xml
data class Sound(
    @PropertyElement
    val tempo: String?,
    
    @PropertyElement
    val dynamics: String?,
    
    @PropertyElement
    val dacapo: String?,
    
    @PropertyElement
    val segno: String?,
    
    @PropertyElement
    val dalsegno: String?,
    
    @PropertyElement
    val coda: String?,
    
    @PropertyElement
    val tocoda: String?,
    
    @PropertyElement
    val divisions: String?
)

