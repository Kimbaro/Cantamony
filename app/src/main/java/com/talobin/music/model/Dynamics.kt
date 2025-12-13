package com.talobin.music.parser.model

import com.tickaroo.tikxml.annotation.PropertyElement
import com.tickaroo.tikxml.annotation.Xml

@Xml
data class Dynamics(
    @PropertyElement
    val ppp: String?,
    
    @PropertyElement
    val pp: String?,
    
    @PropertyElement
    val p: String?,
    
    @PropertyElement
    val mp: String?,
    
    @PropertyElement
    val mf: String?,
    
    @PropertyElement
    val f: String?,
    
    @PropertyElement
    val ff: String?,
    
    @PropertyElement
    val fff: String?,
    
    @PropertyElement
    val fp: String?,
    
    @PropertyElement
    val sf: String?,
    
    @PropertyElement
    val sfz: String?,
    
    @PropertyElement
    val rf: String?,
    
    @PropertyElement
    val rfz: String?,
    
    @PropertyElement
    val otherDynamics: String?
) {
    /**
     * 동적 기호 문자열 반환 (우선순위: ppp > pp > p > mp > mf > f > ff > fff > fp > sf > sfz > rf > rfz > other)
     */
    fun getDynamicsText(): String? {
        return when {
            ppp != null -> "ppp"
            pp != null -> "pp"
            p != null -> "p"
            mp != null -> "mp"
            mf != null -> "mf"
            f != null -> "f"
            ff != null -> "ff"
            fff != null -> "fff"
            fp != null -> "fp"
            sf != null -> "sf"
            sfz != null -> "sfz"
            rf != null -> "rf"
            rfz != null -> "rfz"
            otherDynamics != null -> otherDynamics
            else -> null
        }
    }
}


