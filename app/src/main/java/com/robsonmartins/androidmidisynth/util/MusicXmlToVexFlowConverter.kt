package com.robsonmartins.androidmidisynth.util

import android.util.Log

/**
 * MusicXML 데이터를 VexFlow 형식으로 변환하는 유틸리티
 * 
 * MeasureData와 VexFlowNote 데이터 클래스를 제공합니다.
 */
object MusicXmlToVexFlowConverter {
    private const val TAG = "MusicXmlToVexFlow"

    /**
     * VexFlow 노트 데이터 클래스
     */
    data class VexFlowNote(
        val keys: List<String>,      // 예: ["c/4", "e/4", "g/4"] (화음)
        val duration: String,         // 예: "w", "h", "q", "8", "16", "32"
        val isRest: Boolean = false,  // 쉼표 여부
        val isChord: Boolean = false,  // 화음 여부 (chord 태그)
        val articulations: List<String> = emptyList(),  // 연주기호: ["staccato", "accent", "tenuto", "fermata", "trill"]
        val tieType: String? = null,  // 붙임줄: "start", "stop", "continue"
        val slurType: String? = null, // 슬러: "start", "stop", "continue"
        val beamType: String? = null,  // 음표 연결: "begin", "continue", "end"
        val finger: String? = null,  // 손가락 번호: "1", "2", "3", "4", "5"
        val pedalType: String? = null,  // 페달: "start", "stop", "change"
        val noteId: String? = null  // 음표 식별자 (하이라이트용)
    )

    /**
     * 셈여림 기호 데이터 클래스
     */
    data class DynamicsData(
        val text: String,  // "ppp", "pp", "p", "mp", "mf", "f", "ff", "fff", "fp", "sf", "sfz", "rf", "rfz" 등
        val placement: String? = null,  // "above" or "below"
        val x: Float = 0f  // x 위치 (노트와의 상대 위치)
    )
    
    /**
     * 반복 기호 데이터 클래스
     */
    data class RepeatBarData(
        val type: String,  // "forward" (||:), "backward" (:||), "both" (||: :||)
        val location: String? = null  // "left" or "right"
    )
    
    /**
     * 도돌이표 데이터 클래스
     */
    data class SegnoCodaData(
        val type: String,  // "segno", "coda", "dacapo", "dalsegno", "tocoda"
        val location: String? = null  // "left" or "right"
    )
    
    /**
     * 마디 데이터 클래스
     * 피아노 악보를 위해 상단/하단 오선보를 모두 지원
     */
    data class MeasureData(
        val measureNumber: String,  // MusicXML 원본 값을 그대로 사용 (조정 없음)
        val notes: List<VexFlowNote>,  // 상단 오선보 (treble clef)
        val notesBass: List<VexFlowNote> = emptyList(),  // 하단 오선보 (bass clef)
        val timeSignature: Pair<Int, Int> = Pair(4, 4),  // numerator, denominator
        val keySignature: String = "C",
        val dynamics: List<DynamicsData> = emptyList(),  // 셈여림 기호
        val repeatBars: List<RepeatBarData> = emptyList(),  // 반복 기호
        val segno: SegnoCodaData? = null,  // Segno 기호
        val coda: SegnoCodaData? = null,  // Coda 기호
        val dacapo: Boolean = false,  // D.C. (Da Capo)
        val dalsegno: Boolean = false,  // D.S. (Dal Segno)
        val tocoda: Boolean = false,  // To Coda
        val width: Float? = null  // MusicXML의 width 값 (tenths 단위)
    )

    /**
     * VexFlow 노트를 JavaScript 코드 문자열로 변환
     * @param clef 'treble' 또는 'bass' (기본값: 'treble')
     */
    fun noteToJsString(note: VexFlowNote, clef: String = "treble"): String {
        val keysStr = note.keys.joinToString(", ") { "'$it'" }
        val duration = if (note.isRest) {
            when (note.duration) {
                "w" -> "wr"
                "h" -> "hr"
                "q" -> "qr"
                "8" -> "8r"
                "16" -> "16r"
                "32" -> "32r"
                else -> "qr"
            }
        } else {
            note.duration
        }
        
        // 기호 정보를 JavaScript 객체에 포함
        val noteObj = buildString {
            append("new VF.StaveNote({ clef: '$clef', keys: [$keysStr], duration: '$duration'")
            
            // 기호 정보 추가
            if (note.articulations.isNotEmpty()) {
                val articulationsStr = note.articulations.joinToString(", ") { "'$it'" }
                append(", articulations: [$articulationsStr]")
            }
            if (note.tieType != null) {
                append(", tieType: '${note.tieType}'")
            }
            if (note.slurType != null) {
                append(", slurType: '${note.slurType}'")
            }
            if (note.beamType != null) {
                append(", beamType: '${note.beamType}'")
            }
            if (note.isChord) {
                append(", isChord: true")
            }
            if (note.finger != null) {
                append(", finger: '${note.finger}'")
            }
            if (note.pedalType != null) {
                append(", pedalType: '${note.pedalType}'")
            }
            if (note.noteId != null) {
                append(", noteId: '${note.noteId}'")
            }
            
            append(" })")
        }
        
        return noteObj
    }

    /**
     * 마디 데이터를 VexFlow JSON 문자열로 변환 (상단 오선보만)
     */
    fun measureToJson(measure: MeasureData): String {
        if (measure.notes.isEmpty()) {
            return ""
        }
        
        return measure.notes.joinToString(",\n                ") { note ->
            noteToJsString(note)
        }
    }
    
    /**
     * 마디 데이터의 하단 오선보를 VexFlow JSON 문자열로 변환
     */
    fun measureBassToJson(measure: MeasureData): String {
        if (measure.notesBass.isEmpty()) {
            return ""
        }
        
        return measure.notesBass.joinToString(",\n                ") { note ->
            noteToJsString(note, "bass")
        }
    }

    /**
     * 마디의 총 박자 수 계산
     */
    fun calculateTotalBeats(measure: MeasureData): Double {
        return measure.notes.sumOf { note ->
            when (note.duration) {
                "w", "wr" -> 4.0
                "h", "hr" -> 2.0
                "q", "qr" -> 1.0
                "8", "8r" -> 0.5
                "16", "16r" -> 0.25
                "32", "32r" -> 0.125
                else -> 1.0
            }
        }
    }
}

