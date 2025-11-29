package com.robsonmartins.androidmidisynth.util

import android.util.Log

/**
 * MusicXML 파서 (MusicXML-Android 라이브러리 기반)
 * 
 * 이 클래스는 MusicXML-Android 라이브러리를 사용하여 파싱을 수행합니다.
 * 호환성을 위해 인터페이스를 유지하며, 내부적으로 MusicXmlAdapter를 사용합니다.
 */
object MusicXmlParser {
    private const val TAG = "MusicXmlParser"
    
    /**
     * MusicXML 문자열을 직접 MeasureData 리스트로 파싱
     * MusicXML-Android 라이브러리를 사용하여 파싱
     * 
     * @param xmlContent MusicXML XML 문자열
     * @return 파싱된 MeasureData 리스트
     */
    fun parseToMeasureData(xmlContent: String): List<MusicXmlToVexFlowConverter.MeasureData> {
        // MusicXML-Android 라이브러리 기반 어댑터 사용
        return MusicXmlAdapter.parseToMeasureData(xmlContent)
    }
}
