package com.robsonmartins.androidmidisynth.util

import android.content.res.AssetManager
import android.util.Log
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * MusicXML 관련 유틸리티 함수
 */
object MusicXmlUtils {
    private const val TAG = "MusicXmlUtils"

    /**
     * .mxl 파일 (ZIP 압축된 MusicXML)에서 XML 내용 추출
     * @param assets AssetManager
     * @param mxlPath assets 폴더 내 .mxl 파일 경로
     * @return MusicXML XML 문자열
     */
    fun extractMxlFile(assets: AssetManager, mxlPath: String): String {
        return try {
            assets.open(mxlPath).use { inputStream ->
                extractMxlFromInputStream(inputStream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract MXL file: $mxlPath", e)
            throw IllegalArgumentException("Failed to extract MXL file: $mxlPath", e)
        }
    }

    /**
     * InputStream에서 .mxl 파일 내용 추출
     * MXL 파일은 ZIP 압축 파일이며, META-INF/container.xml에 실제 MusicXML 파일 경로가 지정되어 있습니다.
     * @param inputStream .mxl 파일의 InputStream
     * @return MusicXML XML 문자열
     */
    private fun extractMxlFromInputStream(inputStream: InputStream): String {
        ZipInputStream(inputStream).use { zipInputStream ->
            var entry = zipInputStream.nextEntry
            var containerXml: String? = null
            var rootFilePath: String? = null
            val xmlFiles = mutableMapOf<String, String>() // 파일 경로 -> 내용
            
            // 첫 번째 패스: container.xml 찾기 및 모든 XML 파일 수집
            while (entry != null) {
                val entryName = entry.name
                Log.d(TAG, "Processing entry in MXL: $entryName")
                
                when {
                    // container.xml 찾기
                    entryName == "META-INF/container.xml" || entryName.endsWith("/container.xml") -> {
                        containerXml = zipInputStream.readBytes().toString(Charsets.UTF_8)
                        Log.d(TAG, "Extracted container.xml, size: ${containerXml.length}")
                        
                        // container.xml에서 rootfile 경로 추출
                        try {
                            val rootfileRegex = """<rootfile[^>]*full-path=["']([^"']+)["']""".toRegex()
                            val match = rootfileRegex.find(containerXml)
                            if (match != null) {
                                rootFilePath = match.groupValues[1]
                                Log.d(TAG, "Found rootfile path in container.xml: $rootFilePath")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing container.xml", e)
                        }
                    }
                    
                    // 모든 XML 파일 저장
                    entryName.endsWith(".xml") || entryName.endsWith(".musicxml") -> {
                        val xmlContent = zipInputStream.readBytes().toString(Charsets.UTF_8)
                        xmlFiles[entryName] = xmlContent
                        Log.d(TAG, "Found XML file: $entryName, size: ${xmlContent.length}")
                    }
                    
                    else -> {
                        // 다른 파일은 건너뛰기
                        zipInputStream.readBytes() // 엔트리 내용 읽기 (다음 엔트리로 이동하기 위해)
                    }
                }
                
                entry = zipInputStream.nextEntry
            }
            
            // container.xml에서 찾은 경로와 일치하는 파일 찾기
            val matchedFile = if (rootFilePath != null) {
                // 정확한 경로 매칭
                xmlFiles[rootFilePath] 
                    ?: xmlFiles.entries.find { it.key == rootFilePath || it.key.endsWith("/$rootFilePath") }?.value
            } else {
                null
            }
            
            // score.xml 찾기 (fallback)
            val scoreXml = matchedFile ?: (xmlFiles["score.xml"] 
                ?: xmlFiles.entries.find { it.key.endsWith("/score.xml") }?.value)
            
            // 첫 번째 XML 파일 사용 (최후의 fallback)
            val finalXml = scoreXml ?: xmlFiles.values.firstOrNull()
            
            if (finalXml != null) {
                // XML 헤더 확인 및 수정
                var xmlContent = finalXml.trim()
                
                // XML 주석 제거 (TikXML이 주석을 처리하지 못함)
                // 주석 패턴: <!-- ... --> (중첩 주석도 처리)
                xmlContent = xmlContent.replace(Regex("""<!--[\s\S]*?-->"""), "")
                
                // DOCTYPE 선언 제거 (TikXML이 DOCTYPE을 처리하지 못할 수 있음)
                xmlContent = xmlContent.replace(Regex("""<!DOCTYPE[\s\S]*?>"""), "")
                
                Log.d(TAG, "Removed XML comments and DOCTYPE from extracted content")
                
                // XML 선언 확인 및 수정
                val xmlDeclarationRegex = """<\?xml[^>]*>""".toRegex()
                val existingDeclaration = xmlDeclarationRegex.find(xmlContent)?.value
                
                if (existingDeclaration != null) {
                    // XML 선언이 있으면 standalone 속성 확인
                    if (!existingDeclaration.contains("standalone")) {
                        // standalone 속성이 없으면 추가
                        val newDeclaration = existingDeclaration.replace(
                            "?>",
                            """ standalone="no"?>"""
                        )
                        xmlContent = xmlContent.replace(existingDeclaration, newDeclaration)
                        Log.d(TAG, "Added standalone='no' to XML declaration")
                    } else {
                        // standalone이 있지만 "no"가 아니면 수정
                        if (!existingDeclaration.contains("standalone=\"no\"")) {
                            val newDeclaration = existingDeclaration.replace(
                                """standalone=["'][^"']*["']""".toRegex(),
                                """standalone="no""""
                            )
                            xmlContent = xmlContent.replace(existingDeclaration, newDeclaration)
                            Log.d(TAG, "Updated standalone to 'no' in XML declaration")
                        }
                    }
                } else {
                    // XML 선언이 없으면 추가
                    xmlContent = """<?xml version="1.0" encoding="UTF-8" standalone="no"?>""" + "\n" + xmlContent
                    Log.d(TAG, "Added XML declaration with standalone='no'")
                }
                
                // 최종 XML 선언 확인
                val finalDeclaration = xmlDeclarationRegex.find(xmlContent)?.value
                Log.d(TAG, "Extracted MusicXML, size: ${xmlContent.length}")
                Log.d(TAG, "Final XML declaration: $finalDeclaration")
                
                return xmlContent
            } else {
                if (rootFilePath != null) {
                    Log.w(TAG, "Rootfile path '$rootFilePath' not found in extracted XML files")
                }
            }
        }
        
        throw IllegalArgumentException("No XML file found in MXL archive")
    }

    /**
     * MusicXML 파일이 .mxl (압축) 형식인지 확인
     */
    fun isMxlFile(filePath: String): Boolean {
        return filePath.endsWith(".mxl", ignoreCase = true)
    }

    /**
     * MusicXML 파일이 .xml (비압축) 형식인지 확인
     */
    fun isXmlFile(filePath: String): Boolean {
        return filePath.endsWith(".xml", ignoreCase = true) && 
               !filePath.endsWith(".mxl", ignoreCase = true)
    }

    /**
     * .musicxml 파일 (비압축 MusicXML)에서 XML 내용 읽기
     * @param assets AssetManager
     * @param musicxmlPath assets 폴더 내 .musicxml 파일 경로
     * @return MusicXML XML 문자열
     */
    fun readMusicXmlFile(assets: AssetManager, musicxmlPath: String): String {
        return try {
            assets.open(musicxmlPath).use { inputStream ->
                var xmlContent = inputStream.readBytes().toString(Charsets.UTF_8).trim()
                
                // XML 주석 제거 (TikXML이 주석을 처리하지 못함)
                xmlContent = xmlContent.replace(Regex("""<!--[\s\S]*?-->"""), "")
                
                // DOCTYPE 선언 제거 (TikXML이 DOCTYPE을 처리하지 못할 수 있음)
                xmlContent = xmlContent.replace(Regex("""<!DOCTYPE[\s\S]*?>"""), "")
                
                Log.d(TAG, "Removed XML comments and DOCTYPE from MusicXML file")
                
                // XML 선언 확인 및 수정
                val xmlDeclarationRegex = """<\?xml[^>]*>""".toRegex()
                val existingDeclaration = xmlDeclarationRegex.find(xmlContent)?.value
                
                if (existingDeclaration != null) {
                    // XML 선언이 있으면 standalone 속성 확인
                    if (!existingDeclaration.contains("standalone")) {
                        // standalone 속성이 없으면 추가
                        val newDeclaration = existingDeclaration.replace(
                            "?>",
                            """ standalone="no"?>"""
                        )
                        xmlContent = xmlContent.replace(existingDeclaration, newDeclaration)
                        Log.d(TAG, "Added standalone='no' to XML declaration")
                    } else {
                        // standalone이 있지만 "no"가 아니면 수정
                        if (!existingDeclaration.contains("standalone=\"no\"")) {
                            val newDeclaration = existingDeclaration.replace(
                                """standalone=["'][^"']*["']""".toRegex(),
                                """standalone="no""""
                            )
                            xmlContent = xmlContent.replace(existingDeclaration, newDeclaration)
                            Log.d(TAG, "Updated standalone to 'no' in XML declaration")
                        }
                    }
                } else {
                    // XML 선언이 없으면 추가
                    xmlContent = """<?xml version="1.0" encoding="UTF-8" standalone="no"?>""" + "\n" + xmlContent
                    Log.d(TAG, "Added XML declaration with standalone='no'")
                }
                
                // 최종 XML 선언 확인
                val finalDeclaration = xmlDeclarationRegex.find(xmlContent)?.value
                Log.d(TAG, "Read MusicXML file: $musicxmlPath, size: ${xmlContent.length}")
                Log.d(TAG, "Final XML declaration: $finalDeclaration")
                
                xmlContent
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read MusicXML file: $musicxmlPath", e)
            throw IllegalArgumentException("Failed to read MusicXML file: $musicxmlPath", e)
        }
    }
}
