package com.talobin.music.parser.model

import com.tickaroo.tikxml.annotation.Attribute
import com.tickaroo.tikxml.annotation.Element
import com.tickaroo.tikxml.annotation.PropertyElement
import com.tickaroo.tikxml.annotation.Xml

@Xml
data class Direction(
    @Attribute
    val placement: String?,  // "above" or "below"
    
    @Element
    val dynamics: Dynamics?,
    
    @Element(name = "direction-type")
    val directionTypeList: List<DirectionType>?,
    
    @Element
    val sound: Sound?,
    
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
    @PropertyElement
    val beatUnit: String?,
    
    @PropertyElement
    val perMinute: String?
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

