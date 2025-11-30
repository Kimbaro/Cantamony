package com.robsonmartins.androidmidisynth.util

import android.util.Log
import com.talobin.music.Parser
import com.talobin.music.parser.model.*

/**
 * MusicXML-Android 라이브러리의 ScorePartWise를 
 * MeasureData로 변환하는 어댑터
 * 
 * 순수 파싱 작업만 수행하며, duration 계산은 기존 로직을 유지합니다.
 */
object MusicXmlAdapter {
    private const val TAG = "MusicXmlAdapter"
    
    /**
     * fifths 값을 조표 문자열로 변환
     */
    private fun convertFifthsToKey(fifths: Int): String {
        val majorKeys = mapOf(
            -7 to "Cb", -6 to "Gb", -5 to "Db", -4 to "Ab", -3 to "Eb", -2 to "Bb", -1 to "F",
            0 to "C",
            1 to "G", 2 to "D", 3 to "A", 4 to "E", 5 to "B", 6 to "F#", 7 to "C#"
        )
        return majorKeys[fifths] ?: "C"
    }
    
    /**
     * MusicXML 문자열을 파싱하여 MeasureData 리스트로 변환
     * @param xmlContent MusicXML XML 문자열
     * @return 파싱된 MeasureData 리스트
     */
    fun parseToMeasureData(xmlContent: String): List<MusicXmlToVexFlowConverter.MeasureData> {
        return try {
            Log.d(TAG, "Parsing MusicXML with MusicXML-Android library, length: ${xmlContent.length}")
            
            // MusicXML-Android 라이브러리로 파싱
            val scorePartWise = Parser.parseString(xmlContent)
            
            if (scorePartWise == null) {
                Log.w(TAG, "Failed to parse MusicXML: returned null")
                return emptyList()
            }
            
            // ScorePartWise를 MeasureData 리스트로 변환
            convertScorePartWiseToMeasureData(scorePartWise)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse MusicXML to MeasureData", e)
            e.printStackTrace()
            emptyList()
        }
    }
    
    /**
     * ScorePartWise를 MeasureData 리스트로 변환
     * 외부에서도 사용 가능하도록 public으로 변경
     */
    fun convertScorePartWiseToMeasureData(
        scorePartWise: ScorePartWise
    ): List<MusicXmlToVexFlowConverter.MeasureData> {
        val measuresMap = mutableMapOf<String, MusicXmlToVexFlowConverter.MeasureData>()
        
        // 첫 번째 마디의 조표를 저장 (모든 마디에 적용)
        var globalKeySignature = "C"
        var firstImplicitFound = false  // 첫 번째 implicit 마디 추적
        
        // 각 Part 처리
        scorePartWise.parts?.forEach { part ->
            val partId = part.id
            Log.d(TAG, "Processing part: $partId")
            
            // 각 Measure 처리
            part.measureList?.forEach { measure ->
                // implicit="yes" 마디는 못갖춤마디로 처리
                val isImplicit = measure.implicit == "yes"
                val originalMeasureNumber = measure.number ?: ""
                
                // implicit 마디 처리: 첫 번째 implicit 마디만 "0"으로 변환, 나머지는 원본 유지
                val measureNumber = if (isImplicit) {
                    if (!firstImplicitFound) {
                        // 첫 번째 implicit 마디만 "0"으로 변환
                        firstImplicitFound = true
                        "0"
                    } else {
                        // 나머지 implicit 마디는 원본 번호 유지 (중복 방지)
                        originalMeasureNumber
                    }
                } else {
                    originalMeasureNumber
                }
                
                if (isImplicit) {
                    Log.d(TAG, "Implicit measure detected: original=$originalMeasureNumber, converted=$measureNumber, isFirst=$firstImplicitFound")
                }
                
                // MusicXML의 width 값 추출 (tenths 단위)
                val measureWidth = measure.width?.toFloatOrNull()
                if (measureWidth != null) {
                    Log.d(TAG, "Measure $measureNumber (original=$originalMeasureNumber, implicit=$isImplicit) width: $measureWidth (tenths)")
                } else if (isImplicit) {
                    Log.d(TAG, "Implicit measure $measureNumber (original=$originalMeasureNumber) has no width attribute")
                }
                
                // Attributes에서 divisions, time signature, key signature 추출
                var divisions = 1
                var timeSignature = Pair(4, 4)
                var keySignature = globalKeySignature // 기본값으로 이전 조표 사용
                
                measure.attributesList?.firstOrNull()?.let { attrs ->
                    // Divisions
                    attrs.divisions?.toIntOrNull()?.let { divisions = it }
                    
                    // Time signature
                    attrs.time?.let { time ->
                        val beats = time.beats?.toIntOrNull() ?: 4
                        val beatType = time.beatType?.toIntOrNull() ?: 4
                        timeSignature = Pair(beats, beatType)
                    }
                    
                    // Key signature (fifths)
                    attrs.key?.let { key ->
                        val fifths = key.fifths?.toIntOrNull() ?: 0
                        keySignature = convertFifthsToKey(fifths)
                        // 첫 번째 마디의 조표를 전역 변수에 저장 (모든 마디에 적용)
                        if (globalKeySignature == "C" || measureNumber == "1") {
                            globalKeySignature = keySignature
                        }
                        Log.d(TAG, "Extracted key signature: fifths=$fifths, key=$keySignature (measure=$measureNumber)")
                    }
                }
                
                // 조표가 없으면 전역 조표 사용
                if (keySignature == "C" && globalKeySignature != "C") {
                    keySignature = globalKeySignature
                }
                
                // 노트 처리 (화음 처리를 위해 리스트로 수집 후 처리)
                val allNotes = mutableListOf<Pair<Note, Int>>() // Note와 staff 번호
                measure.noteList?.forEach { note ->
                    val staffNumber = note.staff?.toIntOrNull() ?: 1
                    allNotes.add(Pair(note, staffNumber))
                }
                
                // 화음 처리를 위해 노트를 그룹화
                val trebleNotes = mutableListOf<MusicXmlToVexFlowConverter.VexFlowNote>()
                val bassNotes = mutableListOf<MusicXmlToVexFlowConverter.VexFlowNote>()
                
                var currentTrebleChordKeys = mutableListOf<String>()
                var currentBassChordKeys = mutableListOf<String>()
                var currentTrebleNote: MusicXmlToVexFlowConverter.VexFlowNote? = null
                var currentBassNote: MusicXmlToVexFlowConverter.VexFlowNote? = null
                
                allNotes.forEach { (note, staffNumber) ->
                    val noteData = convertNoteToVexFlowNote(note, divisions)
                    if (noteData != null) {
                        val isChord = note.chord != null
                        
                        if (staffNumber == 1) {
                            // Treble 처리
                            if (isChord && currentTrebleNote != null) {
                                // 화음: 이전 노트에 키 추가 및 기호 정보 병합
                                val updatedKeys = currentTrebleNote!!.keys + noteData.keys
                                val mergedArticulations = (currentTrebleNote!!.articulations + noteData.articulations).distinct()
                                // 화음의 경우 첫 번째 노트의 기호 정보를 우선 사용
                                currentTrebleNote = currentTrebleNote!!.copy(
                                    keys = updatedKeys,
                                    isChord = true,
                                    articulations = mergedArticulations
                                )
                            } else {
                                // 새 노트: 이전 노트 저장하고 새로 시작
                                if (currentTrebleNote != null) {
                                    trebleNotes.add(currentTrebleNote!!)
                                }
                                currentTrebleNote = noteData
                            }
                        } else {
                            // Bass 처리
                            if (isChord && currentBassNote != null) {
                                // 화음: 이전 노트에 키 추가 및 기호 정보 병합
                                val updatedKeys = currentBassNote!!.keys + noteData.keys
                                val mergedArticulations = (currentBassNote!!.articulations + noteData.articulations).distinct()
                                // 화음의 경우 첫 번째 노트의 기호 정보를 우선 사용
                                currentBassNote = currentBassNote!!.copy(
                                    keys = updatedKeys,
                                    isChord = true,
                                    articulations = mergedArticulations
                                )
                            } else {
                                // 새 노트: 이전 노트 저장하고 새로 시작
                                if (currentBassNote != null) {
                                    bassNotes.add(currentBassNote!!)
                                }
                                currentBassNote = noteData
                            }
                        }
                    }
                }
                
                // 마지막 노트 추가
                if (currentTrebleNote != null) {
                    trebleNotes.add(currentTrebleNote!!)
                }
                if (currentBassNote != null) {
                    bassNotes.add(currentBassNote!!)
                }
                
                // Direction 파싱 (셈여림 기호, Segno, Coda 등)
                val dynamicsList = mutableListOf<MusicXmlToVexFlowConverter.DynamicsData>()
                var segno: MusicXmlToVexFlowConverter.SegnoCodaData? = null
                var coda: MusicXmlToVexFlowConverter.SegnoCodaData? = null
                var dacapo = false
                var dalsegno = false
                var tocoda = false
                
                measure.directionList?.forEach { direction ->
                    // Dynamics 파싱
                    direction.dynamics?.getDynamicsText()?.let { dynamicsText ->
                        dynamicsList.add(
                            MusicXmlToVexFlowConverter.DynamicsData(
                                text = dynamicsText,
                                placement = direction.placement
                            )
                        )
                    }
                    
                    // DirectionType에서 Dynamics, Segno, Coda 파싱
                    direction.directionTypeList?.forEach { directionType ->
                        directionType.dynamics?.getDynamicsText()?.let { dynamicsText ->
                            dynamicsList.add(
                                MusicXmlToVexFlowConverter.DynamicsData(
                                    text = dynamicsText,
                                    placement = direction.placement
                                )
                            )
                        }
                        
                        // Segno 파싱
                        if (directionType.segno != null) {
                            segno = MusicXmlToVexFlowConverter.SegnoCodaData(
                                type = "segno",
                                location = direction.placement
                            )
                        }
                        
                        // Coda 파싱
                        if (directionType.coda != null) {
                            coda = MusicXmlToVexFlowConverter.SegnoCodaData(
                                type = "coda",
                                location = direction.placement
                            )
                        }
                        
                        // Words에서 D.C., D.S. 파싱
                        directionType.words?.value?.let { words ->
                            val wordsUpper = words.uppercase()
                            when {
                                wordsUpper.contains("D.C.") || wordsUpper.contains("DA CAPO") -> dacapo = true
                                wordsUpper.contains("D.S.") || wordsUpper.contains("DAL SEGNO") -> dalsegno = true
                                wordsUpper.contains("TO CODA") -> tocoda = true
                            }
                        }
                    }
                    
                    // Sound에서 D.C., D.S., Coda 파싱
                    direction.sound?.let { sound ->
                        if (sound.dacapo != null) dacapo = true
                        if (sound.dalsegno != null) dalsegno = true
                        if (sound.coda != null) coda = MusicXmlToVexFlowConverter.SegnoCodaData(
                            type = "coda",
                            location = direction.placement
                        )
                        if (sound.tocoda != null) tocoda = true
                        if (sound.segno != null) segno = MusicXmlToVexFlowConverter.SegnoCodaData(
                            type = "segno",
                            location = direction.placement
                        )
                    }
                }
                
                // Barline 파싱 (반복 기호)
                val repeatBars = mutableListOf<MusicXmlToVexFlowConverter.RepeatBarData>()
                measure.barlineList?.forEach { barline ->
                    barline.repeat?.let { repeat ->
                        val repeatType = when (repeat.direction) {
                            "forward" -> "forward"  // ||:
                            "backward" -> "backward"  // :||
                            else -> "both"  // ||: :||
                        }
                        repeatBars.add(
                            MusicXmlToVexFlowConverter.RepeatBarData(
                                type = repeatType,
                                location = barline.location
                            )
                        )
                    }
                    
                    // Barline에서 Segno, Coda 파싱
                    barline.segno?.let {
                        segno = MusicXmlToVexFlowConverter.SegnoCodaData(
                            type = "segno",
                            location = barline.location
                        )
                    }
                    
                    barline.coda?.let {
                        coda = MusicXmlToVexFlowConverter.SegnoCodaData(
                            type = "coda",
                            location = barline.location
                        )
                    }
                }
                
                // 기존 마디가 있으면 업데이트, 없으면 추가
                if (measuresMap.containsKey(measureNumber)) {
                    val existing = measuresMap[measureNumber]!!
                    
                    // width 값 처리: 첫 번째 implicit 마디("0")의 width는 우선 보존
                    // implicit 마디의 width는 항상 우선, 그 외에는 새로운 값이 있으면 사용
                    val finalWidth = when {
                        // 첫 번째 implicit 마디("0")의 width는 항상 보존 (다른 Part에서 덮어쓰지 않음)
                        measureNumber == "0" && existing.width != null -> {
                            if (isImplicit && measureWidth != null) {
                                // 현재 마디도 implicit이고 width가 있으면 사용 (같은 Part 내에서 업데이트)
                                measureWidth
                            } else {
                                // 다른 Part의 일반 마디가 오면 기존 width 보존
                                existing.width
                            }
                        }
                        // implicit 마디의 width는 우선 보존
                        isImplicit && existing.width != null -> existing.width
                        // 새로운 width 값이 있으면 사용
                        measureWidth != null -> measureWidth
                        // 기존 값 유지
                        else -> existing.width
                    }
                    
                    if (measureNumber == "0" && finalWidth != null) {
                        Log.d(TAG, "Pickup measure width preserved: $finalWidth (tenths)")
                    }
                    
                    measuresMap[measureNumber] = existing.copy(
                        notes = existing.notes + trebleNotes,
                        notesBass = existing.notesBass + bassNotes,
                        timeSignature = timeSignature,
                        keySignature = keySignature,
                        dynamics = existing.dynamics + dynamicsList,
                        repeatBars = existing.repeatBars + repeatBars,
                        segno = segno ?: existing.segno,
                        coda = coda ?: existing.coda,
                        dacapo = dacapo || existing.dacapo,
                        dalsegno = dalsegno || existing.dalsegno,
                        tocoda = tocoda || existing.tocoda,
                        width = finalWidth  // 개선된 width 로직
                    )
                } else {
                    measuresMap[measureNumber] = MusicXmlToVexFlowConverter.MeasureData(
                        measureNumber = measureNumber,
                        notes = trebleNotes,
                        notesBass = bassNotes,
                        timeSignature = timeSignature,
                        keySignature = keySignature,
                        dynamics = dynamicsList,
                        repeatBars = repeatBars,
                        segno = segno,
                        coda = coda,
                        dacapo = dacapo,
                        dalsegno = dalsegno,
                        tocoda = tocoda,
                        width = measureWidth  // MusicXML의 width 값 추가
                    )
                }
            }
        }
        
        // 마디 번호를 정렬 (원본 순서 유지)
        // "0" (첫 번째 implicit) -> 숫자 마디 -> "X"로 시작하는 마디 순서
        return measuresMap.values.sortedWith(compareBy { measure ->
            val num = measure.measureNumber
            when {
                num == "0" -> 0  // 첫 번째 implicit 마디는 맨 앞
                num.startsWith("X") -> {
                    // "X8", "X20" 등은 숫자 부분 추출하여 정렬
                    val xNum = num.substring(1).toIntOrNull() ?: Int.MAX_VALUE
                    1000000 + xNum  // 숫자 마디 뒤에 배치
                }
                else -> {
                    // 일반 숫자 마디
                    num.toIntOrNull() ?: Int.MAX_VALUE
                }
            }
        })
    }
    
    /**
     * Note를 VexFlowNote로 변환
     * MusicXML의 원본 값을 그대로 사용하며, 모든 기호 정보를 포함합니다.
     */
    private fun convertNoteToVexFlowNote(
        note: Note,
        divisions: Int
    ): MusicXmlToVexFlowConverter.VexFlowNote? {
        // 쉼표 처리
        if (note.rest != null) {
            // MusicXML의 원본 type 값을 그대로 사용
            val duration = convertTypeToDuration(note.type)
            return MusicXmlToVexFlowConverter.VexFlowNote(
                keys = listOf("b/4"), // 쉼표는 임의의 키 사용
                duration = duration,
                isRest = true,
                isChord = false,
                articulations = emptyList(),
                tieType = extractTieType(note),
                slurType = extractSlurType(note),
                beamType = extractBeamType(note),
                finger = null,
                pedalType = null,
                noteId = "rest_${duration}_${System.currentTimeMillis()}"
            )
        }
        
        // Pitch 처리
        val pitch = note.pitch ?: return null
        val step = pitch.step ?: return null
        val octave = pitch.octave?.toIntOrNull() ?: 4
        
        // alter 값을 그대로 사용 (원본 값 유지)
        val alter = pitch.alter?.toIntOrNull() ?: 0
        
        // accidental 필드도 확인 (우선순위: accidental > alter)
        val accidental = note.accidental?.lowercase()
        val finalAlter = when {
            accidental != null -> {
                // accidental 필드가 있으면 이를 우선 사용
                when (accidental) {
                    "sharp", "#" -> 1
                    "flat", "b" -> -1
                    "natural", "n" -> 0
                    "double-sharp", "x" -> 2
                    "double-flat", "bb" -> -2
                    else -> alter
                }
            }
            else -> alter
        }
        
        // 음이름 변환 (finalAlter에 따라 #, b 추가)
        val noteName = when (finalAlter) {
            2 -> "${step}##"
            -2 -> "${step}bb"
            1 -> "${step}#"
            -1 -> "${step}b"
            else -> step
        }.lowercase()
        
        val key = "$noteName/$octave"
        
        // MusicXML의 원본 type 값을 그대로 사용
        val duration = convertTypeToDuration(note.type)
        
        // 기호 정보 추출
        val articulations = extractArticulations(note)
        val tieType = extractTieType(note)
        val slurType = extractSlurType(note)
        val beamType = extractBeamType(note)
        val isChord = note.chord != null
        val finger = extractFinger(note)
        val pedalType = extractPedalType(note)
        
        // 음표 식별자 생성 (하이라이트용): measureNumber_key_duration_index 형식
        // 실제 measureNumber는 나중에 설정되므로 임시로 사용
        val noteId = "${key}_${duration}_${System.currentTimeMillis()}"
        
        return MusicXmlToVexFlowConverter.VexFlowNote(
            keys = listOf(key),
            duration = duration,
            isRest = false,
            isChord = isChord,
            articulations = articulations,
            tieType = tieType,
            slurType = slurType,
            beamType = beamType,
            finger = finger,
            pedalType = pedalType,
            noteId = noteId
        )
    }
    
    /**
     * 연주기호 추출
     */
    private fun extractArticulations(note: Note): List<String> {
        val articulations = mutableListOf<String>()
        
        note.notations?.articulations?.let { arts ->
            // Staccato
            if (arts.staccato != null) {
                articulations.add("staccato")
            }
            // TODO: 다른 연주기호 추가 (accent, tenuto, fermata, trill 등)
            // 현재 Articulations 모델에는 staccato만 있음
        }
        
        return articulations
    }
    
    /**
     * 붙임줄 타입 추출
     */
    private fun extractTieType(note: Note): String? {
        return note.notations?.tiedList?.firstOrNull()?.type
    }
    
    /**
     * 슬러 타입 추출
     */
    private fun extractSlurType(note: Note): String? {
        return note.notations?.slurList?.firstOrNull()?.type
    }
    
    /**
     * 음표 연결 타입 추출
     */
    private fun extractBeamType(note: Note): String? {
        return note.beamList?.firstOrNull()?.description
    }
    
    /**
     * 손가락 번호 추출
     * TODO: MusicXML 모델에 technical/fingering 요소가 추가되면 구현
     */
    private fun extractFinger(note: Note): String? {
        // 현재 모델에는 finger 정보가 없음
        // 나중에 Notation에 technical/fingering이 추가되면 구현
        return null
    }
    
    /**
     * 페달 타입 추출
     * TODO: MusicXML 모델에 pedal 요소가 추가되면 구현
     */
    private fun extractPedalType(note: Note): String? {
        // 현재 모델에는 pedal 정보가 없음
        // 나중에 Notation에 pedal이 추가되면 구현
        return null
    }
    
    /**
     * MusicXML의 원본 type 값을 VexFlow duration 형식으로 변환
     * MusicXML의 type 값을 그대로 사용하여 변환합니다.
     */
    private fun convertTypeToDuration(type: String?): String {
        if (type == null) return "q" // 기본값: quarter note
        
        // MusicXML type 값을 소문자로 변환하여 비교
        return when (type.lowercase()) {
            "whole" -> "w"
            "half" -> "h"
            "quarter" -> "q"
            "eighth" -> "8"
            "16th", "sixteenth" -> "16"
            "32nd", "thirty-second" -> "32"
            "64th", "sixty-fourth" -> "64"
            "breve" -> "w"  // breve는 whole note로 처리
            "long" -> "w"   // long은 whole note로 처리
            else -> {
                // 알 수 없는 타입은 기본값 사용
                Log.w(TAG, "Unknown note type: $type, using default 'q'")
                "q"
            }
        }
    }
}

