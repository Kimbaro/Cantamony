package com.robsonmartins.androidmidisynth

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.leff.midi.MidiFile
import com.leff.midi.event.NoteOff
import com.leff.midi.event.NoteOn
import com.leff.midi.event.meta.KeySignature
import com.leff.midi.event.meta.TimeSignature
import com.leff.midi.event.meta.TrackName
import com.robsonmartins.androidmidisynth.dto.MidiEvent
import com.robsonmartins.androidmidisynth.util.MidiMultiPlayer
import kotlinx.coroutines.*
import java.io.InputStream
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.util.DisplayMetrics
import com.robsonmartins.androidmidisynth.dto.CantamonyAlbum
import com.robsonmartins.androidmidisynth.util.MusicXmlAdapter
import com.robsonmartins.androidmidisynth.util.MusicXmlParser
import com.robsonmartins.androidmidisynth.util.MusicXmlToVexFlowConverter
import com.robsonmartins.androidmidisynth.util.MusicXmlUtils
import com.talobin.music.Parser
import com.talobin.music.parser.model.ScorePartWise
import kotlin.coroutines.coroutineContext

class MainActivity : AppCompatActivity() {

    companion object {
        init {
            System.loadLibrary("synth-lib")
        }
    }

    // 두 개의 오선보 데이터를 위한 data class
    private data class MeasureDataWithBass(
        val measureNumber: String,  // MusicXML 원본 값을 그대로 사용
        val trebleNotes: String,
        val bassNotes: String,
        val totalBeats: Double,
        val dynamics: List<MusicXmlToVexFlowConverter.DynamicsData> = emptyList(),
        val repeatBars: List<MusicXmlToVexFlowConverter.RepeatBarData> = emptyList(),
        val segno: MusicXmlToVexFlowConverter.SegnoCodaData? = null,
        val coda: MusicXmlToVexFlowConverter.SegnoCodaData? = null,
        val dacapo: Boolean = false,
        val width: Float? = null,  // MusicXML의 width 값 (tenths 단위)
        val dalsegno: Boolean = false,
        val tocoda: Boolean = false
    )

    private lateinit var synth: SynthManager
    private lateinit var multiPlayer: MidiMultiPlayer

    private lateinit var txtAlbumName: TextView
    private lateinit var trackCheckboxContainer: LinearLayout
    private lateinit var btnSelectAll: Button
    private lateinit var btnDeselectAll: Button
    private lateinit var btnPlayAll: Button
    private lateinit var btnStopAll: Button
    private lateinit var btnTrackList: Button
    private lateinit var btnCloseTopSheet: Button
    private lateinit var txtBPM: TextView
    private lateinit var seekBarBPM: SeekBar

    // 마디 관련 UI
    private lateinit var txtMeasure: TextView
    private lateinit var seekBarMeasure: SeekBar
    private lateinit var btnPrevMeasure: Button
    private lateinit var btnNextMeasure: Button

    // 악보 WebView 표시
    private lateinit var webViewSheetMusic: WebView

    // 확대/축소 관련
    private lateinit var sheetZoomRemote: LinearLayout
    private lateinit var btnZoomIn: Button
    private lateinit var btnZoomOut: Button
    private lateinit var btnZoomReset: Button
    private var currentZoomLevel = 1.0f

    // 메트로놈 표시
    private lateinit var metronomeIndicator: View
    private lateinit var metronomeBeat: View
    private lateinit var txtMetronome: TextView
    private var metronomeHandler: Handler? = null
    private var metronomeRunnable: Runnable? = null

    // 워터마크
    private lateinit var watermarkView: WatermarkView

    // 로딩 화면
    private lateinit var loadingLayout: View
    private lateinit var loadingProgressBar: ProgressBar
    private lateinit var loadingText: TextView

    // Top Sheet
    private lateinit var topSheet: View
    private lateinit var swipeArea: View
    private var isTopSheetVisible = false

    private var initialBPM = 120

    // 마디 정보
    private var totalMeasures = 0
    private var ticksPerMeasure = 0L
    private var midiResolution = 480
    private var timeSignature: TimeSignature? = null
    private var keySignature: KeySignature? = null

    // MusicXML 데이터
    private var musicXmlMeasures: List<MusicXmlToVexFlowConverter.MeasureData> = emptyList()
    private var hasMusicXml = false
    private var scorePartWise: ScorePartWise? = null  // ScorePartWise 직접 저장

    // 모든 MIDI 이벤트 저장 (악보 렌더링용)
    private val allMidiEvents = mutableListOf<MidiEvent>()

    // 트랙 정보 저장
    data class TrackInfo(
        val globalTrackIndex: Int,  // 전역 트랙 인덱스 (고유)
        val fileIndex: Int,          // 파일 인덱스
        val trackIndex: Int,         // 파일 내 트랙 인덱스
        val trackName: String,       // 트랙 이름
        val fileName: String         // 파일 이름
    )

    private val trackInfoList = mutableListOf<TrackInfo>()

    // 렌더링 관련
    private val renderScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var lastRenderedMeasure = -1 // 중복 렌더링 방지
    private var lastDisplayedMeasure = -1 // 마지막으로 표시된 마디 (재생 중 마디 변경 감지용)
    private var isRendering = false // 진행 중 플래그
    private var currentRenderJob: Job? = null // 현재 렌더링 작업
    private var pendingMeasure = -1 // 대기 중인 마디
    private var preloadRenderJob: Job? = null // 예측적 렌더링 작업
    private var preloadedMeasure = -1 // 미리 렌더링된 마디
    private var preloadedBitmap: Bitmap? = null // 미리 렌더링된 Bitmap

    // 렌더링 우선순위
    private enum class RenderPriority {
        HIGH,    // 현재 마디 (즉시 렌더링)
        LOW      // 다음 마디 (백그라운드 렌더링)
    }

    // SeekBar 업데이트용
    private val handler = Handler(Looper.getMainLooper())
    private var isUserSeeking = false

    // 스크롤 디바운싱을 위한 변수
    private var lastScrollMeasure = -1
    private var lastScrollTime = 0L

    // 마디 무한반복을 위한 변수
    private var loopMeasure: Int? = null // null이면 반복 안함, 숫자면 해당 마디 반복

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (!isUserSeeking && multiPlayer.isPlaying()) {
                val currentTick = multiPlayer.getCurrentTick()
                val currentMeasure = tickToMeasure(currentTick)

                // 무한반복 모드 체크
                if (loopMeasure != null) {
                    val loopMeasureNum = loopMeasure!!
                    val loopStartTick = measureToTick(loopMeasureNum)
                    val loopEndTick = measureToTick(loopMeasureNum + 1)

                    // 마디 끝에 도달하면 시작으로 이동
                    if (currentTick >= loopEndTick) {
                        multiPlayer.seekTo(loopStartTick)
                        Log.d("Loop", "Looping back to measure $loopMeasureNum")
                        handler.postDelayed(this, 30)
                        return
                    }
                }

                seekBarMeasure.progress = (currentMeasure - 1).coerceAtLeast(0)
                updateMeasureDisplay(currentMeasure)

                // 마디가 실제로 변경되었을 때만 화살표 업데이트 및 스크롤
                if (currentMeasure != lastDisplayedMeasure) {
                    Log.d(
                        "SheetMusic",
                        "Measure changed from $lastDisplayedMeasure to $currentMeasure"
                    )
                    lastDisplayedMeasure = currentMeasure

                    // 재생 중이면 스크롤 이동, 아니면 화살표만 업데이트
                    if (multiPlayer.isPlaying()) {
                        val currentTime = System.currentTimeMillis()
                        // 같은 마디로 연속 스크롤 방지 (최소 100ms 간격)
                        if (lastScrollMeasure != currentMeasure || currentTime - lastScrollTime > 100) {
                            lastScrollMeasure = currentMeasure
                            lastScrollTime = currentTime
                            webViewSheetMusic.evaluateJavascript(
                                "javascript:updateArrowAndScroll($currentMeasure);",
                                null
                            )
                            // 음표 단위 하이라이트 업데이트
                            highlightCurrentNote(currentTick)
                        } else {
                            // 스크롤은 스킵하고 화살표만 업데이트
                            webViewSheetMusic.evaluateJavascript(
                                "javascript:updateArrow($currentMeasure);",
                                null
                            )
                            // 음표 단위 하이라이트 업데이트
                            highlightCurrentNote(currentTick)
                        }
                    } else {
                        webViewSheetMusic.evaluateJavascript(
                            "javascript:updateArrow($currentMeasure);",
                            null
                        )
                    }
                }
            }
            handler.postDelayed(this, 30) // 50ms → 30ms로 단축 (Phase 1 적용)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 앱바 제거
        supportActionBar?.hide()

        // View 초기화
        txtAlbumName = findViewById(R.id.txtAlbumName)
        swipeArea = findViewById(R.id.swipeArea)
        topSheet = findViewById(R.id.topSheet)
        btnPlayAll = findViewById(R.id.btnPlayAll)
        btnStopAll = findViewById(R.id.btnStopAll)
        txtBPM = findViewById(R.id.txtBPM)
        seekBarBPM = findViewById(R.id.seekBarBPM)
        txtMeasure = findViewById(R.id.txtMeasure)
        seekBarMeasure = findViewById(R.id.seekBarMeasure)
        btnPrevMeasure = findViewById(R.id.btnPrevMeasure)
        btnNextMeasure = findViewById(R.id.btnNextMeasure)

        // 확대/축소 버튼
        sheetZoomRemote = findViewById(R.id.sheetZoomRemote)
        btnZoomIn = findViewById(R.id.btnZoomIn)
        btnZoomOut = findViewById(R.id.btnZoomOut)
        btnZoomReset = findViewById(R.id.btnZoomReset)

        // 메트로놈 표시
        metronomeIndicator = findViewById(R.id.metronomeIndicator)
        metronomeBeat = findViewById(R.id.metronomeBeat)
        txtMetronome = findViewById(R.id.txtMetronome)
        btnTrackList = findViewById(R.id.btnTrackList)
        btnCloseTopSheet = findViewById(R.id.btnCloseTopSheet)
        webViewSheetMusic = findViewById(R.id.webViewSheetMusic)
        watermarkView = findViewById(R.id.watermarkView)
        loadingLayout = findViewById<View>(R.id.loadingLayout)
        loadingProgressBar = findViewById<ProgressBar>(R.id.loadingProgressBar)
        loadingText = findViewById<TextView>(R.id.loadingText)

        // 초기 로딩 화면 표시 (렌더링 시작 전까지)
        loadingLayout.visibility = View.VISIBLE
        loadingLayout.bringToFront() // 다른 뷰 위에 표시
        loadingProgressBar.visibility = View.VISIBLE
        loadingText.visibility = View.VISIBLE
        loadingText.text = "악보를 그리는중 .... 0/0"
        Log.d("Loading", "Loading screen initialized and shown")

        // WebView 설정
        webViewSheetMusic.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            loadWithOverviewMode = true
            useWideViewPort = true
        }

        // WebView 스크롤 활성화 (악보 포커싱을 위해)
        webViewSheetMusic.isVerticalScrollBarEnabled = true
        webViewSheetMusic.isHorizontalScrollBarEnabled = false

        // WebView가 보이도록 초기 설정
        webViewSheetMusic.visibility = android.view.View.VISIBLE
        webViewSheetMusic.alpha = 1f
        webViewSheetMusic.setBackgroundColor(0xFFFFFFFF.toInt()) // 흰색 배경

        // 워터마크 설정
        watermarkView.watermarkText = "CANTAMONY"
        watermarkView.textColor = 0x80808080.toInt() // 더 진한 회색 (알파 50%)
        watermarkView.textSize = 48f
        watermarkView.rotationAngle = -45f

        txtBPM.text = "BPM: $initialBPM"
        seekBarBPM.progress = initialBPM

        // Synth 초기화
        synth = SynthManager(this)
        synth.loadSoundFont("KawaiStereoGrand.sf3")
        multiPlayer = MidiMultiPlayer(synth)

        // 앨범 정보 수신
        val selectedAlbum = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("SELECTED_ALBUM", CantamonyAlbum::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("SELECTED_ALBUM") as? CantamonyAlbum
        }

        // 앨범 정보 검증
        if (selectedAlbum == null) {
            Log.e("MainActivity", "Selected album is null")
            hideLoadingScreen()
            return
        }

        val albumName = selectedAlbum.albumName
        txtAlbumName.text = "앨범: $albumName"

        // 파일 경로 추출
        val midFile = selectedAlbum.albumAsset["mid"]

        if (midFile == null) {
            Log.e("MainActivity", "MIDI file not found in album asset")
            hideLoadingScreen()
            return
        }

        // MusicXML 파일 경로 수신 (Intent 크기 제한으로 직접 assets에서 읽음)
        val mxlFilePath = intent.getStringExtra("MXL_FILE_PATH")
        val musicxmlFilePath = intent.getStringExtra("MUSICXML_FILE_PATH")


        if (mxlFilePath != null) {
            Log.d("MainActivity", "MXL file path received: $mxlFilePath")

            try {
                // assets에서 직접 MusicXML 추출
                val musicXmlString = if (MusicXmlUtils.isMxlFile(mxlFilePath)) {
                    MusicXmlUtils.extractMxlFile(assets, mxlFilePath)
                } else if (MusicXmlUtils.isXmlFile(mxlFilePath)) {
                    assets.open(mxlFilePath).use { it.readBytes().toString(Charsets.UTF_8) }
                } else {
                    null
                }

                if (musicXmlString != null) {
                    Log.d(
                        "MainActivity",
                        "MusicXML content extracted, length: ${musicXmlString.length}"
                    )

                    // ScorePartWise로 직접 파싱
                    val parsedScorePartWise = Parser.parseString(musicXmlString)

                    if (parsedScorePartWise != null) {
                        scorePartWise = parsedScorePartWise
                        hasMusicXml = true

                        // 마디 수 계산
                        val parts = parsedScorePartWise.parts
                        if (parts != null && parts.isNotEmpty()) {
                            var maxMeasures = 0
                            parts.forEach { part ->
                                val measureCount = part.measureList?.size ?: 0
                                if (measureCount > maxMeasures) {
                                    maxMeasures = measureCount
                                }
                            }
                            totalMeasures = maxMeasures
                            Log.d(
                                "MainActivity",
                                "ScorePartWise parsed successfully: ${parts.size} parts, $totalMeasures measures"
                            )
                        } else {
                            Log.w("MainActivity", "ScorePartWise parsed but no parts found")
                            hasMusicXml = false
                        }
                    } else {
                        Log.w("MainActivity", "Failed to parse MusicXML: returned null")
                        // Fallback: 기존 방식으로 파싱 시도
                        musicXmlMeasures = MusicXmlParser.parseToMeasureData(musicXmlString)
                        hasMusicXml = musicXmlMeasures.isNotEmpty()
                        if (hasMusicXml) {
                            totalMeasures = musicXmlMeasures.size
                            Log.d(
                                "MainActivity",
                                "Fallback: MusicXML parsed via adapter: $totalMeasures measures"
                            )
                        }
                    }
                } else {
                    Log.w("MainActivity", "Failed to extract MusicXML content from: $mxlFilePath")
                }

            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to parse MusicXML", e)
                e.printStackTrace()
                // MusicXML 파싱 실패해도 MIDI 파일로 계속 진행
            }
        } else if (musicxmlFilePath != null) {
            Log.d("MainActivity", "MusicXML file path received: $musicxmlFilePath")

            try {
                // assets에서 직접 MusicXML 파일 읽기 (.musicxml는 압축되지 않음)
                val musicXmlString = MusicXmlUtils.readMusicXmlFile(assets, musicxmlFilePath)

                if (musicXmlString != null && musicXmlString.isNotEmpty()) {
                    Log.d(
                        "MainActivity",
                        "MusicXML content read, length: ${musicXmlString.length}"
                    )

                    // ScorePartWise로 직접 파싱
                    val parsedScorePartWise = Parser.parseString(musicXmlString)

                    if (parsedScorePartWise != null) {
                        scorePartWise = parsedScorePartWise
                        hasMusicXml = true

                        // 마디 수 계산
                        val parts = parsedScorePartWise.parts
                        if (parts != null && parts.isNotEmpty()) {
                            var maxMeasures = 0
                            parts.forEach { part ->
                                val measureList = part.measureList
                                if (measureList != null && measureList.size > maxMeasures) {
                                    maxMeasures = measureList.size
                                }
                            }
                            totalMeasures = maxMeasures
                            Log.d(
                                "MainActivity",
                                "ScorePartWise parsed successfully: ${parts.size} parts, $totalMeasures measures"
                            )
                        } else {
                            Log.w("MainActivity", "ScorePartWise parsed but no parts found")
                        }

                        // MusicXmlAdapter를 사용하여 MeasureData로 변환
                        musicXmlMeasures =
                            MusicXmlAdapter.convertScorePartWiseToMeasureData(parsedScorePartWise)
                        if (hasMusicXml) {
                            totalMeasures = musicXmlMeasures.size
                            Log.d(
                                "MainActivity",
                                "MusicXML parsed via adapter: $totalMeasures measures"
                            )
                        }
                    } else {
                        Log.w("MainActivity", "Failed to parse MusicXML file: $musicxmlFilePath")
                    }
                } else {
                    Log.w("MainActivity", "Failed to read MusicXML content from: $musicxmlFilePath")
                }

            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to parse MusicXML file: $musicxmlFilePath", e)
                e.printStackTrace()
                // MusicXML 파싱 실패해도 MIDI 파일로 계속 진행
            }
        } else {
            Log.d("MainActivity", "No MXL or MusicXML file path received, using MIDI only")
        }

        // MIDI 파일 처리
        val midiFiles = listOf(midFile)

        // MIDI 파일 정보 파싱 및 마디 정보 계산
        var maxTick = 0L
        var globalTrackIndex = 0
        for ((fileIndex, path) in midiFiles.withIndex()) {
            assets.open(path).use { inputStream ->
                val midiFile = MidiFile(inputStream)

                // 첫 번째 파일에서만 TimeSignature, KeySignature와 Resolution 추출
                if (fileIndex == 0) {
                    timeSignature = extractTimeSignature(midiFile)
                    keySignature = extractKeySignature(midiFile)
                    midiResolution = midiFile.getResolution()

                    val numerator = timeSignature?.getNumerator() ?: 4
                    ticksPerMeasure = (midiResolution * numerator).toLong()
                }

                // MidiFile 객체에서 직접 이벤트 파싱 (트랙 정보 포함)
                val trackDataList = parseMidiFileWithTrackInfo(midiFile, fileIndex, path)
                trackDataList.forEach { (events, trackIndex, trackName) ->
                    // 전역 트랙 인덱스로 트랙 추가
                    multiPlayer.addTrack(events, globalTrackIndex)

                    // 트랙 정보 저장
                    trackInfoList.add(
                        TrackInfo(
                            globalTrackIndex = globalTrackIndex,
                            fileIndex = fileIndex,
                            trackIndex = trackIndex,
                            trackName = trackName,
                            fileName = path
                        )
                    )

                    allMidiEvents.addAll(events) // 모든 이벤트 저장
                    events.maxOfOrNull { it.tick }?.let { tick ->
                        if (tick > maxTick) maxTick = tick
                    }
                    globalTrackIndex++
                }
            }
        }

        // 전체 마디 수 계산
        // MusicXML 데이터가 있으면 우선 사용, 없으면 MIDI 기반 계산
        if (!hasMusicXml || musicXmlMeasures.isEmpty()) {
            totalMeasures = if (ticksPerMeasure > 0) {
                ((maxTick / ticksPerMeasure) + 1).toInt()
            } else {
                1
            }
        } else {
            // MusicXML에서 이미 totalMeasures가 설정됨
            totalMeasures = musicXmlMeasures.size
        }

        // 마디 SeekBar 설정
        seekBarMeasure.max = (totalMeasures - 1).coerceAtLeast(0)
        seekBarMeasure.progress = 0
        updateMeasureDisplay(1)

        // MIDI 파일 로드 및 이벤트 확인
        Log.d(
            "SheetMusic",
            "MIDI files loaded: ${midiFiles.size}, total events: ${allMidiEvents.size}, total measures: $totalMeasures"
        )

        // 모든 마디를 한 번에 렌더링 (MainActivity 진입 시 자동 렌더링)
        // MusicXML 또는 MIDI 데이터가 있으면 렌더링
        val canRender = (hasMusicXml && musicXmlMeasures.isNotEmpty()) ||
                (allMidiEvents.isNotEmpty() && totalMeasures > 0)

        if (canRender && totalMeasures > 0) {
            Log.d("SheetMusic", "Starting automatic sheet music rendering on activity creation")
            Log.d(
                "SheetMusic",
                "MusicXML: $hasMusicXml, MIDI events: ${allMidiEvents.size}, measures: $totalMeasures"
            )

            // 로딩 화면 표시
            loadingLayout.visibility = View.VISIBLE
            loadingLayout.bringToFront() // 다른 뷰 위에 표시
            loadingProgressBar.visibility = View.VISIBLE
            loadingText.visibility = View.VISIBLE
            loadingText.text = "악보를 그리는중 .... 0/$totalMeasures"
            Log.d("Loading", "Loading screen shown for $totalMeasures measures")
            renderAllMeasures()
        } else {
            Log.w(
                "SheetMusic",
                "Cannot render sheet music: MusicXML=$hasMusicXml, events=${allMidiEvents.size}, measures=$totalMeasures"
            )
            // 렌더링할 수 없으면 로딩 화면 숨기기
            hideLoadingScreen()
        }

        // Bottom Sheet 초기화
        setupBottomSheet()

        // 트랙 목록 버튼 클릭 리스너
        btnTrackList.setOnClickListener {
            showTopSheet()
        }

        // Top Sheet 닫기 버튼 클릭 리스너
        btnCloseTopSheet.setOnClickListener {
            hideTopSheet()
        }

        // PLAY ALL
        btnPlayAll.setOnClickListener {
            multiPlayer.startAll()
            handler.post(updateRunnable) // 업데이트 시작
            startMetronome(initialBPM) // 메트로놈 시작
        }

        // STOP ALL
        btnStopAll.setOnClickListener {
            multiPlayer.stopAll()
            handler.removeCallbacks(updateRunnable) // 업데이트 중지
            stopMetronome() // 메트로놈 중지
        }

        // BPM 변경
        seekBarBPM.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val bpm = progress.coerceAtLeast(30)
                txtBPM.text = "BPM: $bpm"
                multiPlayer.setBPM(bpm.toDouble())

                // 메트로놈 업데이트
                if (multiPlayer.isPlaying()) {
                    stopMetronome()
                    startMetronome(bpm)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 이전 마디 버튼
        btnPrevMeasure.setOnClickListener {
            moveToPreviousMeasure()
        }

        // 다음 마디 버튼
        btnNextMeasure.setOnClickListener {
            moveToNextMeasure()
        }

        // 확대/축소 버튼 이벤트
        btnZoomIn.setOnClickListener {
            currentZoomLevel = (currentZoomLevel + 0.1f).coerceAtMost(3.0f)
            applyZoom()
        }

        btnZoomOut.setOnClickListener {
            currentZoomLevel = (currentZoomLevel - 0.1f).coerceAtLeast(0.5f)
            applyZoom()
        }

        btnZoomReset.setOnClickListener {
            currentZoomLevel = 1.0f
            applyZoom()
        }

        // WebView 핀치 줌 설정
        webViewSheetMusic.settings.builtInZoomControls = false
        webViewSheetMusic.settings.displayZoomControls = false
        webViewSheetMusic.settings.setSupportZoom(true)

        // 확대/축소 버튼 이벤트
        btnZoomIn.setOnClickListener {
            currentZoomLevel = (currentZoomLevel + 0.1f).coerceAtMost(3.0f)
            applyZoom()
        }

        btnZoomOut.setOnClickListener {
            currentZoomLevel = (currentZoomLevel - 0.1f).coerceAtLeast(0.5f)
            applyZoom()
        }

        btnZoomReset.setOnClickListener {
            currentZoomLevel = 1.0f
            applyZoom()
        }

        // WebView 핀치 줌 설정
        webViewSheetMusic.settings.builtInZoomControls = false
        webViewSheetMusic.settings.displayZoomControls = false
        webViewSheetMusic.settings.setSupportZoom(true)

        // 마디 SeekBar 리스너
        seekBarMeasure.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val measure = progress + 1
                    updateMeasureDisplay(measure)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = false
                val measure = (seekBar?.progress ?: 0) + 1
                val targetTick = measureToTick(measure)
                multiPlayer.seekTo(targetTick)

                // 마디 변경 이벤트 발생
                onMeasureChanged(measure, RenderPriority.HIGH)

                // 재생 중이면 seek 후 계속 재생
                if (multiPlayer.isPlaying()) {
                    multiPlayer.stopAll()
                    multiPlayer.startAll()
                }
            }
        })
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateRunnable)
        renderScope.cancel() // 렌더링 코루틴 취소
        currentRenderJob?.cancel() // 진행 중인 렌더링 취소
        multiPlayer.stopAll()
        synth.release()
        super.onDestroy()
    }

    /** 모든 마디를 한 번에 렌더링 */
    private fun renderAllMeasures() {
        if (isRendering) {
            Log.d("SheetMusic", "Already rendering all measures, skipping")
            return
        }

        // 렌더링 전 조건 확인
        if (totalMeasures <= 0) {
            Log.w("SheetMusic", "Cannot render: totalMeasures is $totalMeasures")
            return
        }

        // MusicXML 또는 MIDI 데이터 확인
        val hasData = hasMusicXml && musicXmlMeasures.isNotEmpty() || allMidiEvents.isNotEmpty()
        if (!hasData) {
            Log.w(
                "SheetMusic",
                "Cannot render: no music data available (MusicXML: $hasMusicXml, MIDI: ${allMidiEvents.size})"
            )
            return
        }

        isRendering = true
        currentRenderJob = renderScope.launch {
            try {
                val dataSource = if (hasMusicXml && musicXmlMeasures.isNotEmpty()) {
                    "MusicXML (${musicXmlMeasures.size} measures)"
                } else {
                    "MIDI (${allMidiEvents.size} events)"
                }
                Log.d(
                    "SheetMusic",
                    "Starting full sheet music rendering for all $totalMeasures measures - Source: $dataSource"
                )

                // 모든 마디의 이벤트 가져오기 (MIDI 기반, MusicXML이 있으면 사용 안 함)
                val allMeasuresEvents = if (hasMusicXml && musicXmlMeasures.isNotEmpty()) {
                    // MusicXML 사용 시 빈 리스트 (generateFullSheetMusicHTML에서 MusicXML 데이터 사용)
                    emptyList<List<MidiEvent>>()
                } else {
                    mutableListOf<List<MidiEvent>>().apply {
                        for (measure in 1..totalMeasures) {
                            add(getEventsByMeasure(measure))
                        }
                    }
                }

                // 렌더링 완료 대기용 Deferred
                val renderComplete = CompletableDeferred<Boolean>()

                // WebView 로드 완료 대기
                val loadComplete = CompletableDeferred<Boolean>()

                // WebView 생성 및 설정을 메인 스레드에서 수행
                withContext(Dispatchers.Main) {
                    // WebView가 보이도록 설정
                    webViewSheetMusic.visibility = android.view.View.VISIBLE
                    webViewSheetMusic.alpha = 1f

                    val html = generateFullSheetMusicHTML(allMeasuresEvents)
                    Log.d("SheetMusic", "Generated HTML length: ${html.length}")

                    webViewSheetMusic.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            Log.d("SheetMusic", "WebView page finished for all measures")
                            // 페이지 로드 후 WebView가 보이도록 확인
                            webViewSheetMusic.visibility = android.view.View.VISIBLE

                            // 초기 스크롤을 맨 위로 이동
                            webViewSheetMusic.post {
                                webViewSheetMusic.scrollTo(0, 0)
                                Log.d("SheetMusic", "Initial scroll to top in onPageFinished")
                            }

                            loadComplete.complete(true)
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: android.webkit.WebResourceRequest?,
                            error: android.webkit.WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            Log.e("SheetMusic", "WebView error: ${error?.description}")
                        }
                    }

                    // JavaScript 인터페이스 추가
                    webViewSheetMusic.addJavascriptInterface(object {
                        @android.webkit.JavascriptInterface
                        fun onRenderComplete() {
                            Log.d(
                                "SheetMusic",
                                "JavaScript: Full render complete callback received - VexFlow rendering finished"
                            )
                            renderComplete.complete(true)
                            // 로딩 화면 숨기기 (VexFlow 렌더링 완료 후)
                            runOnUiThread {
                                hideLoadingScreen()
                            }
                        }

                        @android.webkit.JavascriptInterface
                        fun updateWebViewHeight(height: Int) {
                            Log.d(
                                "SheetMusic",
                                "JavaScript: Updating WebView height to $height (ignored, using match_parent for unlimited scroll)"
                            )
                            // WebView는 match_parent로 유지하여 무제한 스크롤 가능
                            // 실제 스크롤은 SVG 내부 콘텐츠 높이에 따라 결정됨
                            runOnUiThread {
                                webViewSheetMusic.visibility = android.view.View.VISIBLE
                            }
                        }

                        @android.webkit.JavascriptInterface
                        fun smoothScrollToPosition(scrollY: Int) {
                            Log.d(
                                "SheetMusic",
                                "JavaScript: Requesting smooth scroll to position $scrollY"
                            )
                            runOnUiThread {
                                // WebView에는 smoothScrollTo가 없으므로 ObjectAnimator로 부드러운 스크롤 구현
                                val currentScrollY = webViewSheetMusic.scrollY
                                if (kotlin.math.abs(currentScrollY - scrollY) > 10) { // 10px 이상 차이날 때만 애니메이션
                                    val animator = android.animation.ObjectAnimator.ofInt(
                                        webViewSheetMusic,
                                        "scrollY",
                                        currentScrollY,
                                        scrollY
                                    )
                                    animator.duration = 300 // 300ms 애니메이션
                                    animator.interpolator =
                                        android.view.animation.DecelerateInterpolator()
                                    animator.start()
                                } else {
                                    // 차이가 작으면 즉시 스크롤
                                    webViewSheetMusic.scrollTo(0, scrollY)
                                }
                            }
                        }

                        @android.webkit.JavascriptInterface
                        fun scrollToPosition(scrollY: Int) {
                            Log.d(
                                "SheetMusic",
                                "JavaScript: Requesting scroll to position $scrollY"
                            )
                            runOnUiThread {
                                // WebView 내부 스크롤을 직접 제어
                                webViewSheetMusic.scrollTo(0, scrollY)
                            }
                        }

                        @android.webkit.JavascriptInterface
                        fun log(message: String) {
                            Log.d("SheetMusic", "JS: $message")
                        }

                        @android.webkit.JavascriptInterface
                        fun logError(message: String) {
                            Log.e("SheetMusic", "JS Error: $message")
                        }

                        @android.webkit.JavascriptInterface
                        fun onMeasureSelected(measure: Int) {
                            Log.d("MeasureSelect", "Measure $measure selected from JavaScript")
                            runOnUiThread {
                                handleMeasureSelection(measure)
                            }
                        }

                        @android.webkit.JavascriptInterface
                        fun onRenderProgress(current: Int, total: Int) {
                            Log.d("SheetMusic", "Rendering progress: $current/$total")
                            runOnUiThread {
                                if (::loadingText.isInitialized) {
                                    loadingText.text = "악보를 그리는중 .... $current/$total"
                                }
                            }
                        }
                    }, "AndroidInterface")

                    Log.d(
                        "SheetMusic",
                        "Loading HTML into WebView, baseURL: file:///android_asset/"
                    )
                    webViewSheetMusic.loadDataWithBaseURL(
                        "file:///android_asset/",
                        html,
                        "text/html",
                        "UTF-8",
                        null
                    )

                    // WebView 콘솔 로그 확인을 위한 설정
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                        android.webkit.WebView.setWebContentsDebuggingEnabled(true)
                    }
                }

                // 페이지 로드 완료 대기
                try {
                    withTimeout(5000) {
                        loadComplete.await()
                    }
                    Log.d("SheetMusic", "Page loaded, waiting for VexFlow rendering...")
                } catch (e: TimeoutCancellationException) {
                    Log.w("SheetMusic", "Page load timeout, continuing anyway")
                }

                // VexFlow 렌더링 완료 대기 (로딩 화면은 onRenderComplete에서 숨김)
                try {
                    withTimeout(15000) { // 타임아웃을 15초로 증가
                        renderComplete.await()
                    }
                    Log.d("SheetMusic", "Full VexFlow rendering complete")
                } catch (e: TimeoutCancellationException) {
                    Log.w("SheetMusic", "VexFlow render timeout, hiding loading screen anyway")
                    // 타임아웃 시에도 로딩 화면 숨기기
                    withContext(Dispatchers.Main) {
                        hideLoadingScreen()
                    }
                    delay(1000)
                }

                // 초기 마디로 화살표 설정 및 스크롤을 맨 위로
                webViewSheetMusic.evaluateJavascript(
                    "javascript:updateArrow(1);",
                    null
                )

                // WebView 스크롤을 맨 위로 이동
                runOnUiThread {
                    webViewSheetMusic.scrollTo(0, 0)
                    Log.d("SheetMusic", "Scrolled WebView to top (0, 0)")
                }

                lastRenderedMeasure = 1
                lastDisplayedMeasure = 1
                isRendering = false

                Log.d("SheetMusic", "Full sheet music rendering completed")

            } catch (e: Exception) {
                Log.e("SheetMusic", "Full render error", e)
                isRendering = false
                // 에러 발생 시에도 로딩 화면 숨기기
                withContext(Dispatchers.Main) {
                    hideLoadingScreen()
                }
            } catch (e: CancellationException) {
                Log.d("SheetMusic", "Render cancelled")
                isRendering = false
                // 취소 시에도 로딩 화면 숨기기
                withContext(Dispatchers.Main) {
                    hideLoadingScreen()
                }
                throw e
            }
        }
    }

    /** 로딩 화면 숨기기 */
    private fun hideLoadingScreen() {
        if (::loadingLayout.isInitialized) {
            loadingLayout.visibility = View.GONE
            loadingProgressBar.visibility = View.GONE
            loadingText.visibility = View.GONE
            Log.d("Loading", "Loading screen hidden")
        }
    }

    /** 마디 변경 이벤트 핸들러 - 화살표 업데이트 및 스크롤 (전체 렌더링이므로) */
    private fun onMeasureChanged(measure: Int, priority: RenderPriority = RenderPriority.HIGH) {
        // 마디가 변경되지 않았으면 무시
        if (measure == lastDisplayedMeasure) {
            return
        }

        // 재생 중일 때만 스크롤 이동
        if (multiPlayer.isPlaying()) {
            // 전체 렌더링이므로 화살표 업데이트 및 스크롤
            webViewSheetMusic.evaluateJavascript(
                "javascript:updateArrowAndScroll($measure);",
                null
            )
        } else {
            // 정지 상태에서는 화살표만 업데이트
            webViewSheetMusic.evaluateJavascript(
                "javascript:updateArrow($measure);",
                null
            )
        }
        lastDisplayedMeasure = measure
    }

    /** VexFlow WebView 생성 */
    @Deprecated(
        "Use generateFullSheetMusicHTMLFromMusicXml instead",
        ReplaceWith("generateFullSheetMusicHTMLFromMusicXml")
    )
    private fun createVexFlowWebView(
        currentEvents: List<MidiEvent>,
        nextEvents: List<MidiEvent>,
        thirdEvents: List<MidiEvent>,
        measure: Int,
        renderComplete: CompletableDeferred<Boolean>
    ): WebView {
        val webView = WebView(this)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            loadWithOverviewMode = true
            useWideViewPort = true
        }

        // JavaScript 인터페이스 추가 (렌더링 완료 감지용)
        webView.addJavascriptInterface(object {
            @android.webkit.JavascriptInterface
            fun onRenderComplete() {
                Log.d("SheetMusic", "JavaScript: Render complete callback received")
                renderComplete.complete(true)
            }

            @android.webkit.JavascriptInterface
            fun log(message: String) {
                Log.d("SheetMusic", "JS: $message")
            }

            @android.webkit.JavascriptInterface
            fun logError(message: String) {
                Log.e("SheetMusic", "JS Error: $message")
            }
        }, "AndroidInterface")

        // TODO: 이 함수는 deprecated되었으므로 사용하지 않음
        // val html = generateVexFlowHTML(currentEvents, nextEvents, thirdEvents, measure)
        val html = "<html><body>Deprecated function</body></html>"
        webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null)

        return webView
    }

    // TODO: MusicXML 파싱 후 VexFlow 노트 생성 함수로 대체 예정
    // 기존 MIDI 이벤트 기반 변환 함수는 삭제됨

    /** 모든 마디를 한 번에 렌더링하는 HTML 생성 */
    private fun generateFullSheetMusicHTML(allMeasuresEvents: List<List<MidiEvent>>): String {
        // ScorePartWise가 있으면 직접 사용 (우선순위 1)
        if (hasMusicXml && scorePartWise != null) {
            return generateFullSheetMusicHTMLFromScorePartWise(scorePartWise!!)
        }

        // MusicXML MeasureData가 있으면 사용 (우선순위 2)
        if (hasMusicXml && musicXmlMeasures.isNotEmpty()) {
            return generateFullSheetMusicHTMLFromMusicXml(musicXmlMeasures)
        }

        // MIDI 이벤트 기반으로 생성 (하위 호환성)
        // 시간 서명 정보 가져오기
        val numerator = timeSignature?.getNumerator() ?: 4
        val denominator = timeSignature?.getRealDenominator() ?: 4
        val keySigStr = keySignatureToVexFlow(keySignature)

        // MIDI 이벤트를 VexFlow 형식으로 변환 (임시 - MusicXML 우선)
        val measuresData = emptyList<Triple<Int, String, Double>>()

        return generateFullSheetMusicHTMLWithAllStaves(
            measuresData,
            numerator,
            denominator,
            keySigStr
        )
    }

    /** ScorePartWise로부터 직접 전체 악보 HTML 생성 */
    private fun generateFullSheetMusicHTMLFromScorePartWise(
        scorePartWise: ScorePartWise
    ): String {
        // ScorePartWise를 MeasureData로 변환 (MusicXmlAdapter 로직 재사용)
        val measures = MusicXmlAdapter.convertScorePartWiseToMeasureData(scorePartWise)

        if (measures.isEmpty()) {
            return generateEmptySheetMusicHTML()
        }

        // 첫 번째 마디에서 시간 서명과 조표 가져오기
        val firstMeasure = measures.first()
        val numerator = firstMeasure.timeSignature.first
        val denominator = firstMeasure.timeSignature.second
        val keySigStr = firstMeasure.keySignature

        // 각 마디의 노트 JSON 생성 (상단/하단 오선보 모두 포함)
        val measuresData = measures.map { measure ->
            val notesJson = MusicXmlToVexFlowConverter.measureToJson(measure)
            val notesBassJson = MusicXmlToVexFlowConverter.measureBassToJson(measure)
            val totalBeats = MusicXmlToVexFlowConverter.calculateTotalBeats(measure)
            MeasureDataWithBass(
                measure.measureNumber,
                notesJson,
                notesBassJson,
                totalBeats,
                measure.dynamics,
                measure.repeatBars,
                measure.segno,
                measure.coda,
                measure.dacapo,
                measure.width,  // MusicXML의 width 값 전달
                measure.dalsegno,
                measure.tocoda
            )
        }

        return generateFullSheetMusicHTMLWithTwoStaves(
            measuresData,
            numerator,
            denominator,
            keySigStr
        )
    }

    /** MusicXML 데이터로부터 전체 악보 HTML 생성 */
    private fun generateFullSheetMusicHTMLFromMusicXml(
        measures: List<MusicXmlToVexFlowConverter.MeasureData>
    ): String {
        if (measures.isEmpty()) {
            return generateEmptySheetMusicHTML()
        }

        // 첫 번째 마디에서 시간 서명과 조표 가져오기
        val firstMeasure = measures.first()
        val numerator = firstMeasure.timeSignature.first
        val denominator = firstMeasure.timeSignature.second
        val keySigStr = firstMeasure.keySignature

        // 각 마디의 노트 JSON 생성 (상단/하단 오선보 모두 포함)
        val measuresData = measures.map { measure ->
            val notesJson = MusicXmlToVexFlowConverter.measureToJson(measure)
            val notesBassJson = MusicXmlToVexFlowConverter.measureBassToJson(measure)
            val totalBeats = MusicXmlToVexFlowConverter.calculateTotalBeats(measure)
            MeasureDataWithBass(
                measure.measureNumber,
                notesJson,
                notesBassJson,
                totalBeats,
                measure.dynamics,
                measure.repeatBars,
                measure.segno,
                measure.coda,
                measure.dacapo,
                measure.width,  // MusicXML의 width 값 전달
                measure.dalsegno,
                measure.tocoda
            )
        }

        return generateFullSheetMusicHTMLWithTwoStaves(
            measuresData,
            numerator,
            denominator,
            keySigStr
        )
    }

    /** 공통 JavaScript 유틸리티 함수 */
    private fun getCommonJavaScriptUtils(): String {
        return """
        <script>
            // 로그 함수
            function log(msg) {
                if (typeof AndroidInterface !== 'undefined' && AndroidInterface.log) {
                    AndroidInterface.log(msg);
                } else {
                    console.log(msg);
                }
            }
            
            function logError(msg) {
                if (typeof AndroidInterface !== 'undefined' && AndroidInterface.logError) {
                    AndroidInterface.logError(msg);
                } else {
                    console.error(msg);
                }
            }
            
            // VexFlow 로드 함수
            function loadVexFlow() {
                return new Promise(function(resolve, reject) {
                    if (typeof Vex !== 'undefined' && typeof Vex.Flow !== 'undefined') {
                        resolve(Vex.Flow);
                        return;
                    }
                    var script = document.createElement('script');
                    script.src = 'vexflow/vexflow-min.js';
                    script.onload = function() {
                        if (typeof Vex !== 'undefined' && typeof Vex.Flow !== 'undefined') {
                            log('VexFlow loaded from local assets');
                            resolve(Vex.Flow);
                        } else {
                            loadVexFlowFromCDN().then(resolve).catch(reject);
                        }
                    };
                    script.onerror = function() {
                        log('Local VexFlow load failed, trying CDN...');
                        loadVexFlowFromCDN().then(resolve).catch(reject);
                    };
                    document.head.appendChild(script);
                });
            }
            
            function loadVexFlowFromCDN() {
                return new Promise(function(resolve, reject) {
                    var script = document.createElement('script');
                    script.src = 'https://cdn.jsdelivr.net/npm/vexflow@4.2.5/releases/vexflow-min.js';
                    script.onload = function() {
                        if (typeof Vex !== 'undefined' && typeof Vex.Flow !== 'undefined') {
                            log('VexFlow loaded from CDN');
                            resolve(Vex.Flow);
                        } else {
                            reject(new Error('VexFlow not available after CDN load'));
                        }
                    };
                    script.onerror = function() {
                        reject(new Error('Failed to load VexFlow from CDN'));
                    };
                    document.head.appendChild(script);
                });
            }
            
            // 점음표 헬퍼 함수
            function dotted(staveNote, noteIndex) {
                var VF = window.VF || (typeof VF !== 'undefined' ? VF : null);
                if (!VF) return staveNote;
                if (noteIndex === undefined || noteIndex < 0) {
                    VF.Dot.buildAndAttach([staveNote], { all: true });
                } else {
                    VF.Dot.buildAndAttach([staveNote], { index: noteIndex });
                }
                return staveNote;
            }
            
            // Duration을 beats로 변환하는 맵
            var durationFractionMap = {
                'w': 4, 'wr': 4, 'h': 2, 'hr': 2,
                'q': 1, 'qr': 1, '8': 0.5, '8r': 0.5,
                '16': 0.25, '16r': 0.25, '32': 0.125, '32r': 0.125
            };
            
            // 노트에 Accidental 추가
            function addAccidentalsToNote(note) {
                var VF = window.VF || (typeof VF !== 'undefined' ? VF : null);
                if (!VF || !(note instanceof VF.StaveNote)) return;
                var keys = note.getKeys();
                if (!keys || keys.length === 0) return;
                
                keys.forEach(function(key, keyIndex) {
                    try {
                        var accidental = null;
                        if (key.includes('##')) {
                            accidental = new VF.Accidental('##');
                        } else if (key.includes('bb')) {
                            accidental = new VF.Accidental('bb');
                        } else if (key.includes('#')) {
                            accidental = new VF.Accidental('#');
                        } else if (key.includes('b') && !key.includes('bb')) {
                            var noteName = key.split('/')[0].toLowerCase();
                            if (noteName.endsWith('b') && !noteName.endsWith('bb')) {
                                accidental = new VF.Accidental('b');
                            }
                        }
                        if (accidental) {
                            note.addModifier(accidental, keyIndex);
                        }
                    } catch (e) {
                        logError('Error adding accidental: ' + e.message);
                    }
                });
            }
            
            // 노트에 Articulation 추가
            function addArticulationsToNote(note) {
                var VF = window.VF || (typeof VF !== 'undefined' ? VF : null);
                if (!VF || !(note instanceof VF.StaveNote)) return;
                if (!note.articulations || !Array.isArray(note.articulations)) return;
                
                note.articulations.forEach(function(articulationData, index) {
                    try {
                        var type = null;
                        var placement = null;
                        
                        if (typeof articulationData === 'string') {
                            type = articulationData;
                        } else if (articulationData && articulationData.type) {
                            type = articulationData.type;
                            placement = articulationData.placement;
                        } else {
                            return;
                        }
                        
                        var articulation = null;
                        switch(type) {
                            case 'staccato': articulation = new VF.Articulation('a.'); break;
                            case 'accent': articulation = new VF.Articulation('a>'); break;
                            case 'tenuto': articulation = new VF.Articulation('a-'); break;
                            case 'fermata': articulation = new VF.Articulation('am'); break;
                            case 'marcato': articulation = new VF.Articulation('a^'); break;
                            case 'staccatissimo': articulation = new VF.Articulation('av'); break;
                        }
                        
                        if (articulation) {
                            var position = (placement === 'below') ? VF.Annotation.Position.BELOW : VF.Annotation.Position.ABOVE;
                           
                            // 🔑 악센트 전용 충돌 우회 로직 - addArticulation 이후에 호출
                            if (type === 'accent') {
                                // addArticulation 이후에 setYShift 호출
                                if (position === VF.Annotation.Position.ABOVE) {
                                    articulation.setYShift(-5);
                                    log('Applied setYShift to accent above');
                                } else if (position === VF.Annotation.Position.BELOW) {
                                    articulation.setYShift(5);
                                    log('Applied setYShift to accent below');
                                }
                            }
                            // 먼저 position 설정
                            articulation.setPosition(position);
                            
                            // addArticulation을 먼저 호출
                            note.addArticulation(index, articulation);
                        }
                    } catch (e) {
                        logError('Error adding articulation: ' + e.message);
                    }
                });
            }
            
            // 노트에 Dot 추가
            function addDotsToNote(note) {
                var VF = window.VF || (typeof VF !== 'undefined' ? VF : null);
                if (!VF || !(note instanceof VF.StaveNote)) return;
                try {
                    var duration = note.getDuration();
                    if (duration && duration.includes('d')) {
                        dotted(note);
                    }
                } catch (e) {
                    logError('Error adding dot: ' + e.message);
                }
            }
            
            // 노트 처리 (Accidental, Dot, Articulation 모두 적용)
            function processNote(note) {
                log('loopKDT -> processNote called, note type: ' + typeof note);
                
                // window.VF 또는 VF 사용 (전역 변수로 설정된 VF)
                var VF = window.VF || (typeof VF !== 'undefined' ? VF : null);
                
                // VF가 로드되지 않았거나 note가 아직 생성되지 않은 경우
                if (!VF || typeof VF.StaveNote === 'undefined') {
                    logError('loopKDT -> VF or VF.StaveNote is not defined');
                    return;
                }
                if (!note) {
                    logError('loopKDT -> note is null or undefined');
                    return;
                }
                if (!(note instanceof VF.StaveNote)) {
                    logError('loopKDT -> note is not VF.StaveNote instance. note type: ' + typeof note + ', constructor: ' + (note.constructor ? note.constructor.name : 'null'));
                    return;
                }
                log('loopKDT -> addAccidentalsToNote');
                addAccidentalsToNote(note);
                log('loopKDT -> addDotsToNote');
                addDotsToNote(note);
                log('loopKDT -> addArticulationsToNote');
                addArticulationsToNote(note);
            }
        </script>
        """.trimIndent()
    }

    /** 빈 악보 HTML 생성 */
    private fun generateEmptySheetMusicHTML(): String {
        return """
        <!DOCTYPE html>
        <html>
        <head>
            <style>
                body { margin: 0; padding: 0; background: white; }
                #sheet { width: 100%; height: 100%; }
            </style>
            ${getCommonJavaScriptUtils()}
        </head>
        <body>
            <div id="sheet"></div>
            <script>
                loadVexFlow().then(function(VF) {
                    var div = document.getElementById("sheet");
                    if (div) {
                        var renderer = new VF.Renderer(div, VF.Renderer.Backends.SVG);
                        renderer.resize(800, 200);
                        var context = renderer.getContext();
                        var stave = new VF.Stave(10, 50, 750);
                        stave.addClef("treble");
                        stave.setContext(context).draw();
                    }
                    if (typeof AndroidInterface !== 'undefined') {
                        AndroidInterface.onRenderComplete();
                    }
                }).catch(function(error) {
                    log('Failed to load VexFlow: ' + error.message);
                    if (typeof AndroidInterface !== 'undefined') {
                        AndroidInterface.onRenderComplete();
                    }
                });
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    /** 두 개의 오선보(상단/하단)를 렌더링하는 VexFlow HTML 생성 */
    private fun generateFullSheetMusicHTMLWithTwoStaves(
        measuresData: List<MeasureDataWithBass>,
        numerator: Int,
        denominator: Int,
        keySignature: String = "C"
    ): String {
        // 4마디 단위로 표시: 한 줄에 4마디를 하나의 연속된 Stave로
        val measuresPerLine = 4
        val measureWidth = 180  // 각 마디의 너비
        val staveWidth = (measuresPerLine * measureWidth) - 10  // 한 줄 Stave의 전체 너비 (4마디)
        val measureHeight = 120  // 각 줄의 높이
        val lineSpacing = 20  // 줄 간격
        val totalLines = (measuresData.size + measuresPerLine - 1) / measuresPerLine  // 올림 계산
        val totalHeight = totalLines * (measureHeight + lineSpacing) + 100
        val canvasWidth = (measuresPerLine * measureWidth)  // 4마디 * 180px (여백 없음)

        // 마디별 노트 데이터를 JavaScript 배열로 변환
        val measuresJson =
            measuresData.joinToString(",\n            ") { measure ->
                val dynamicsJson = measure.dynamics.joinToString(", ") { dyn ->
                    "{text: '${dyn.text}', placement: '${dyn.placement ?: "above"}'}"
                }
                val repeatBarsJson = measure.repeatBars.joinToString(", ") { rb ->
                    "{type: '${rb.type}', location: '${rb.location ?: "right"}'}"
                }
                val segnoJson =
                    measure.segno?.let { "type: '${it.type}', location: '${it.location ?: "right"}'" }
                        ?: "null"
                val codaJson =
                    measure.coda?.let { "type: '${it.type}', location: '${it.location ?: "right"}'" }
                        ?: "null"

                """
            {
                measure: "${measure.measureNumber}",
                trebleNotes: ${if (measure.trebleNotes.isBlank()) "[]" else "[${measure.trebleNotes}]"},
                bassNotes: ${if (measure.bassNotes.isBlank()) "[]" else "[${measure.bassNotes}]"},
                totalBeats: ${measure.totalBeats},
                width: ${measure.width ?: "null"},
                dynamics: [${dynamicsJson}],
                repeatBars: [${repeatBarsJson}],
                segno: ${if (measure.segno != null) "{$segnoJson}" else "null"},
                coda: ${if (measure.coda != null) "{$codaJson}" else "null"},
                dacapo: ${measure.dacapo},
                dalsegno: ${measure.dalsegno},
                tocoda: ${measure.tocoda}
            }
            """.trimIndent()
            }

        return """
        <!DOCTYPE html>
        <html>
        <head>
            <style>
                html {
                    height: auto;
                    min-height: ${totalHeight}px;
                }
                body { 
                    margin: 0; 
                    padding: 0; 
                    background: white; 
                    overflow-y: auto;
                    overflow-x: hidden;
                    height: auto;
                    min-height: ${totalHeight}px;
                }
                #sheet { 
                    width: 100%; 
                    min-height: ${totalHeight}px;
                    height: auto;
                    position: relative;
                    display: flex;
                    justify-content: center;
                    align-items: flex-start;
                }
                svg { display: block; }
                .measure-container {
                    position: relative;
                    margin-bottom: 20px;
                }
                .measure-arrow {
                    position: absolute;
                    left: -30px;
                    top: 50%;
                    transform: translateY(-50%);
                    width: 0;
                    height: 0;
                    border-left: 15px solid #1db954;
                    border-top: 10px solid transparent;
                    border-bottom: 10px solid transparent;
                    display: none;
                }
                .measure-arrow.active {
                    display: block;
                }
            </style>
            ${getCommonJavaScriptUtils()}
        </head>
        <body>
            <div id="sheet"></div>
            <script>
                function startRendering() {
                    var div = document.getElementById("sheet");
                    if (!div) {
                        logError('Sheet div not found');
                        return;
                    }
                    
                    // VexFlow 초기화 및 렌더링
                    loadVexFlow().then(function(VF) {
                        log('VexFlow initialized successfully');
                        renderSheetMusic(VF, div);
                    }).catch(function(error) {
                        logError('Failed to initialize VexFlow: ' + error.message);
                        if (typeof AndroidInterface !== 'undefined' && AndroidInterface.onRenderComplete) {
                            AndroidInterface.onRenderComplete();
                        }
                    });
                }
                
                function renderSheetMusic(VF, div) {
                    // VF를 전역 변수로 설정하여 processNote 등에서 접근 가능하도록 함
                    window.VF = VF;
                    
                    try {
                        log('Starting VexFlow rendering for two staves...');
                        
                        var renderer = new VF.Renderer(div, VF.Renderer.Backends.SVG);
                        // 4마디 단위 배치: 한 줄에 4마디
                        var canvasWidth = $canvasWidth;  // 4마디 너비에 맞춤
                        log('Canvas size: ' + canvasWidth + ' x ' + $totalHeight);
                        renderer.resize(canvasWidth, $totalHeight);
                        var context = renderer.getContext();
                        
                        var svg = div.querySelector('svg');
                        if (svg) {
                            // 초기 SVG 설정 (나중에 조정됨)
                            svg.setAttribute('style', 'display: block; width: ' + canvasWidth + 'px; height: ' + $totalHeight + 'px; min-height: ' + $totalHeight + 'px; overflow: visible;');
                            svg.setAttribute('width', canvasWidth);
                            svg.setAttribute('height', $totalHeight);
                        }
                        
                        var measuresData = [$measuresJson];
                        log('Total measures to render: ' + measuresData.length);
                        
                        var numerator = $numerator;
                        var denominator = $denominator;
                        var keySignatureValue = "$keySignature";
                        var beatDuration = 4;
                        var measuresPerLine = 4;  // 한 줄에 4마디
                        var measureWidth = 180;  // 각 마디의 너비
                        var staveWidth = (measuresPerLine * measureWidth) - 10;  // 한 줄 Stave의 전체 너비 (4마디)
                        var measureHeight = 120;  // 각 줄의 높이
                        var lineSpacing = 20;  // 줄 간격
                        
                        // 조표 값 확인 및 로그
                        log('Key signature value received: "' + keySignatureValue + '" (length: ' + keySignatureValue.length + ')');
                        log('Key signature type: ' + typeof keySignatureValue);
                        log('Measures per line: ' + measuresPerLine + ', measure width: ' + measureWidth + ', stave width: ' + staveWidth);
                        
                        var measureYPositions = {};
                        
                        // 4마디 단위로 그룹화하여 한 줄에 하나의 연속된 Stave로 렌더링 (임시 비활성화)
                        /*
                        for (var lineIndex = 0; lineIndex < Math.ceil(measuresData.length / measuresPerLine); lineIndex++) {
                            var lineMeasures = measuresData.slice(lineIndex * measuresPerLine, (lineIndex + 1) * measuresPerLine);
                            var yPos = 50 + (lineIndex * (measureHeight + lineSpacing));
                            
                            log('Rendering line ' + lineIndex + ' with ' + lineMeasures.length + ' measures at y=' + yPos);
                            
                            // 한 줄에 하나의 긴 Stave 생성
                            var trebleStave = new VF.Stave(10, yPos, staveWidth);
                            trebleStave.addClef("treble");
                            
                            // 조표 추가 (각 줄의 첫 번째 마디에만 표시)
                            if (keySignatureValue && keySignatureValue !== "" && keySignatureValue.trim() !== "") {
                                try {
                                    trebleStave.addKeySignature(keySignatureValue.trim());
                                    log('Added key signature: "' + keySignatureValue.trim() + '" to line ' + lineIndex);
                                } catch (e) {
                                    logError('Error adding key signature: ' + e.message);
                                }
                            }
                            
                            trebleStave.addTimeSignature(numerator + "/" + denominator);
                            trebleStave.setContext(context).draw();
                            
                            // 한 줄의 모든 마디 노트를 수집
                            var allTrebleNotes = [];
                            var measureBoundaries = []; // 각 마디의 노트 인덱스 경계
                            
                            lineMeasures.forEach(function(measureData, measureIndexInLine) {
                                var measureNum = measureData.measure;
                                var trebleNotesJson = measureData.trebleNotes;
                                var measureStartIndex = allTrebleNotes.length;
                                
                                measureYPositions[measureNum] = yPos;
                                
                                // 마디 번호 표시
                                var measureXPos = 0 + (measureIndexInLine * measureWidth);
                                context.setFont("Arial", 10, "normal");
                                context.setFillStyle("#666666");
                                context.fillText("M" + measureNum, measureXPos + 2, yPos - 5);
                                
                                // 마디 노트 파싱
                                var trebleNotes = [];
                                log('Parsing notes for measure ' + measureNum + ', trebleNotesJson type: ' + typeof trebleNotesJson);
                                if (trebleNotesJson && typeof trebleNotesJson === 'string' && trebleNotesJson.trim() !== '') {
                                    try {
                                        var jsonStr = trebleNotesJson.trim();
                                        log('Parsing JSON string for measure ' + measureNum + ' (first 200 chars): ' + jsonStr.substring(0, Math.min(200, jsonStr.length)));
                                        var notesArray = new Function('return [' + jsonStr + '];')();
                                        trebleNotes = notesArray;
                                        log('Parsed ' + trebleNotes.length + ' notes for measure ' + measureNum);
                                    } catch (e) {
                                        logError('Error parsing treble notes for measure ' + measureNum + ': ' + e.message);
                                        logError('Stack: ' + e.stack);
                                    }
                                } else if (Array.isArray(trebleNotesJson)) {
                                    trebleNotes = trebleNotesJson;
                                    log('Using array directly for measure ' + measureNum + ': ' + trebleNotes.length + ' notes');
                                } else {
                                    log('No treble notes for measure ' + measureNum + ' (trebleNotesJson is null or empty)');
                                }
                                
                                // 노트 기호 정보 적용
                                trebleNotes.forEach(function(note) {
                                    try {
                                        // 변음표, 점음표, 연주기호 등 적용 (기존 로직과 동일)
                                        if (note instanceof VF.StaveNote) {
                                            var keys = note.getKeys();
                                            if (keys && keys.length > 0) {
                                                keys.forEach(function(key, keyIndex) {
                                                    try {
                                                        var accidental = null;
                                                        if (key.includes('##')) {
                                                            accidental = new VF.Accidental('##');
                                                        } else if (key.includes('bb')) {
                                                            accidental = new VF.Accidental('bb');
                                                        } else if (key.includes('#')) {
                                                            accidental = new VF.Accidental('#');
                                                        } else if (key.includes('b') && !key.includes('bb')) {
                                                            var noteName = key.split('/')[0].toLowerCase();
                                                            if (noteName.endsWith('b') && !noteName.endsWith('bb')) {
                                                                accidental = new VF.Accidental('b');
                                                            }
                                                        }
                                                        if (accidental) {
                                                            note.addModifier(accidental, keyIndex);
                                                        }
                                                    } catch (e) {
                                                        logError('Error adding accidental: ' + e.message);
                                                    }
                                                });
                                            }
                                            
                                            var duration = note.getDuration();
                                            if (duration && duration.includes('d')) {
                                                try {
                                                    dotted(note);
                                                } catch (e) {
                                                    logError('Error adding dot: ' + e.message);
                                                }
                                            }
                                            
                                            if (note.finger) {
                                                try {
                                                    var fingerAnnotation = new VF.Annotation(note.finger);
                                                    fingerAnnotation.setVerticalJustification(VF.Annotation.VerticalJustify.BOTTOM);
                                                    fingerAnnotation.setFont('Arial', 12, 'bold');
                                                    note.addAnnotation(0, fingerAnnotation);
                                                } catch (e) {
                                                    logError('Error adding finger: ' + e.message);
                                                }
                                            }
                                            
                                             // 연주기호 적용
                                            if (note.articulations && Array.isArray(note.articulations)) {
                                                note.articulations.forEach(function(articulationData,index) {
                                                    try {
                                                        var articulation = null;
                                                        var type = null;
                                                        var placement = null;
                                                        
                                                        // ArticulationData 객체 또는 문자열 처리
                                                        if (typeof articulationData === 'string') {
                                                            // 기존 문자열 형식 지원 (하위 호환성)
                                                            type = articulationData;
                                                        } else if (articulationData && articulationData.type) {
                                                            // 새로운 객체 형식
                                                            type = articulationData.type;
                                                            placement = articulationData.placement;
                                                        } else {
                                                            log('loopKDT: Invalid articulation data: ' + JSON.stringify(articulationData));
                                                            return;
                                                        }
                                                        
                                                        log('loopKDT: type=' + type + ', placement=' + (placement || 'null'));
                                                        
                                                        switch(type) {
                                                            case 'staccato':
                                                                articulation = new VF.Articulation('a.');
                                                                break;
                                                            case 'accent':
                                                                articulation = new VF.Articulation('a>');
                                                                break;
                                                            case 'tenuto':
                                                                articulation = new VF.Articulation('a-');
                                                                break;
                                                            case 'fermata':
                                                                articulation = new VF.Articulation('am');
                                                                break;
                                                            case 'marcato':
                                                                articulation = new VF.Articulation('a^');
                                                                break;
                                                            case 'staccatissimo':
                                                                articulation = new VF.Articulation('av');
                                                                break;
                                                        }
                                                        if (articulation) {
                                                            // placement에 따라 위치 설정 (above: 3, below: 4)
                                                             var position = (placement === 'below') ? VF.Annotation.Position.BELOW : VF.Annotation.Position.ABOVE;
                                                          
                                                            note.addArticulation(index, articulation);
                                                        } else {
                                                            log('loopKDT: Failed to create articulation for: ' + type);
                                                        }
                                                    } catch (e) {
                                                        logError('loopKDT: Error adding articulation: ' + e.message);
                                                    }
                                                });
                                            } else {
                                                if (note.articulations) {
                                                    log('loopKDT: Note.articulations exists but is not an array: ' + typeof note.articulations);
                                                } else {
                                                    log('loopKDT: Note.articulations is undefined or null');
                                                }
                                            }
                                    } catch (e) {
                                        logError('Error processing note: ' + e.message);
                                    }
                                });
                                
                                // StaveNote만 필터링하여 추가
                                var validNotes = trebleNotes.filter(function(note) {
                                    return note instanceof VF.StaveNote;
                                });
                                log('Measure ' + measureNum + ': ' + validNotes.length + ' valid StaveNotes out of ' + trebleNotes.length + ' total notes');
                                
                                allTrebleNotes = allTrebleNotes.concat(validNotes);
                                measureBoundaries.push({
                                    measureNum: measureNum,
                                    startIndex: measureStartIndex,
                                    endIndex: allTrebleNotes.length,
                                    xPos: measureXPos
                                });
                            });
                            
                            log('Line ' + lineIndex + ': Collected ' + allTrebleNotes.length + ' total notes from ' + lineMeasures.length + ' measures');
                            
                            // 한 줄의 모든 노트를 하나의 Voice로 렌더링
                            if (allTrebleNotes.length > 0) {
                                try {
                                    // 모든 노트의 총 duration 계산
                                    var totalDuration = 0;
                                    allTrebleNotes.forEach(function(note) {
                                        try {
                                            var duration = note.getDuration();
                                            var fraction = durationFractionMap[duration] || 1/4;
                                            totalDuration += fraction;
                                        } catch (e) {
                                            logError('Error getting duration: ' + e.message);
                                            totalDuration += 1/4;
                                        }
                                    });
                                    
                                    // 첫 번째 줄의 첫 번째 마디는 무조건 못갖춘마디로 처리
                                    var isFirstLineFirstMeasure = (lineIndex === 0 && lineMeasures.length > 0 && (lineMeasures[0].measure === "1" || lineMeasures[0].measure === 1));
                                    
                                    // 필요한 beats 계산
                                    var requiredBeats;
                                    if (isFirstLineFirstMeasure) {
                                        // 첫 번째 줄의 첫 번째 마디는 못갖춘마디: 실제 duration에 맞춤 (최소 1)
                                        requiredBeats = Math.max(1, Math.ceil(totalDuration));
                                        log('First line first measure (anacrusis): total duration=' + totalDuration + ', required beats=' + requiredBeats);
                                    } else {
                                        // 일반 마디: 4마디 * numerator
                                        requiredBeats = lineMeasures.length * numerator;
                                    }
                                    
                                    log('Total duration: ' + totalDuration + ', required beats: ' + requiredBeats + ', notes: ' + allTrebleNotes.length + ', isPickup: ' + isFirstLineFirstMeasure);
                                    
                                    // duration이 부족하면 rest 추가 (못갖춘마디가 아닌 경우만)
                                    if (!isFirstLineFirstMeasure && totalDuration < requiredBeats) {
                                        var missingDuration = requiredBeats - totalDuration;
                                        log('Adding rest for missing duration: ' + missingDuration);
                                        
                                        var restDuration = 'qr'; // quarter rest
                                        if (missingDuration >= 2) {
                                            restDuration = 'hr';
                                        } else if (missingDuration >= 1) {
                                            restDuration = 'qr';
                                        } else if (missingDuration >= 0.5) {
                                            restDuration = '8r';
                                        } else {
                                            restDuration = '16r';
                                        }
                                        
                                        var restNote = new VF.StaveNote({ 
                                            clef: 'treble',
                                            keys: ['b/4'], 
                                            duration: restDuration 
                                        });
                                        allTrebleNotes.push(restNote);
                                        totalDuration = requiredBeats;
                                        log('Added rest note: ' + restDuration);
                                    }
                                    
                                    log('Creating Voice with ' + requiredBeats + ' beats for ' + allTrebleNotes.length + ' notes');
                                    var trebleVoice = new VF.Voice({ num_beats: requiredBeats, beat_value: denominator });
                                    trebleVoice.addTickables(allTrebleNotes);
                                    
                                    // Formatter로 4마디 너비에 맞게 배치
                                    log('Formatting notes with width: ' + (staveWidth - 20));
                                    var formatter = new VF.Formatter().joinVoices([trebleVoice]).format([trebleVoice], staveWidth - 20);
                                    log('Drawing ' + allTrebleNotes.length + ' notes on stave');
                                    trebleVoice.draw(context, trebleStave);
                                    log('Successfully drew notes');
                                    
                                    // Beam 생성
                                    try {
                                        var trebleBeams = VF.Beam.generateBeams(allTrebleNotes);
                                        trebleBeams.forEach(function(beam) {
                                            try {
                                                beam.setContext(context).draw();
                                            } catch (e) {
                                                logError('Error drawing beam: ' + e.message);
                                            }
                                        });
                                    } catch (e) {
                                        logError('Error generating beams: ' + e.message);
                                    }
                                    
                                    // 붙임줄 및 슬러 처리
                                    for (var i = 0; i < allTrebleNotes.length - 1; i++) {
                                        var note = allTrebleNotes[i];
                                        var nextNote = allTrebleNotes[i + 1];
                                        
                                        if (note.tieType === 'start' && nextNote && nextNote.tieType === 'stop') {
                                            try {
                                                var tie = new VF.StaveTie({
                                                    first_note: note,
                                                    last_note: nextNote
                                                });
                                                tie.setContext(context).draw();
                                            } catch (e) {
                                                logError('Error drawing tie: ' + e.message);
                                            }
                                        }
                                        
                                        if (note.slurType === 'start' && nextNote && nextNote.slurType === 'stop') {
                                            try {
                                                var slur = new VF.Curve({
                                                    from: note,
                                                    to: nextNote
                                                });
                                                slur.setContext(context).draw();
                                            } catch (e) {
                                                logError('Error drawing slur: ' + e.message);
                                            }
                                        }
                                    }
                                    
                                    // data-note 속성 추가
                                    var svg = div.querySelector('svg');
                                    if (svg) {
                                        var paths = svg.querySelectorAll('path');
                                        var noteIndex = 0;
                                        paths.forEach(function(path) {
                                            if (noteIndex < allTrebleNotes.length) {
                                                var note = allTrebleNotes[noteIndex];
                                                if (note && note.getKeys) {
                                                    var keys = note.getKeys();
                                                    if (keys && keys.length > 0) {
                                                        var keysStr = keys.join(',');
                                                        path.setAttribute('data-note', keysStr);
                                                        path.setAttribute('data-measure', lineMeasures[Math.floor(noteIndex / (allTrebleNotes.length / lineMeasures.length))].measure);
                                                        path.setAttribute('data-staff', 'treble');
                                                    }
                                                }
                                                noteIndex++;
                                            }
                                        });
                                    }
                                } catch (e) {
                                    logError('Error rendering treble notes: ' + e.message);
                                    logError('Stack: ' + e.stack);
                                }
                            }
                            
                            // 마디선 그리기 (각 마디 사이)
                            measureBoundaries.forEach(function(boundary, idx) {
                                if (idx > 0) { // 첫 번째 마디 앞에는 마디선 없음
                                    try {
                                        var barlineX = 10 + (idx * measureWidth);
                                        var staveY = trebleStave.getY();
                                        var staveHeight = trebleStave.getHeight();
                                        
                                        context.beginPath();
                                        context.setStrokeStyle("#000000");
                                        context.setLineWidth(1);
                                        context.moveTo(barlineX, staveY);
                                        context.lineTo(barlineX, staveY + staveHeight);
                                        context.stroke();
                                    } catch (e) {
                                        logError('Error drawing barline: ' + e.message);
                                    }
                                }
                            });
                            
                            // 마지막 마디 끝에 마디선 그리기
                            try {
                                var lastBarlineX = 10 + (lineMeasures.length * measureWidth);
                                var staveY = trebleStave.getY();
                                var staveHeight = trebleStave.getHeight();
                                
                                context.beginPath();
                                context.setStrokeStyle("#000000");
                                context.setLineWidth(1);
                                context.moveTo(lastBarlineX, staveY);
                                context.lineTo(lastBarlineX, staveY + staveHeight);
                                context.stroke();
                            } catch (e) {
                                logError('Error drawing final barline: ' + e.message);
                            }
                        }
                        */
                        
                        // 기존 개별 마디 렌더링 코드 (4마디 단위로 한 줄에 배치)
                        // 각 마디를 개별적으로 렌더링하되, 4마디 단위로 한 줄에 배치
                        measuresData.forEach(function(measureData, index) {
                            var measureNum = measureData.measure;
                            var trebleNotesJson = measureData.trebleNotes;
                            
                            // 4마디 단위로 줄 계산
                            var lineIndex = Math.floor(index / measuresPerLine);
                            var measureIndexInLine = index % measuresPerLine;
                            
                            // 각 마디의 위치 계산 (4마디 단위로 한 줄에 배치, 간격 없이 연속)
                            // MusicXML의 width 값 사용 (tenths 단위를 픽셀로 변환)
                            // tenths를 픽셀로 변환: 일반적으로 40 tenths = 약 1cm, 1cm = 약 37.8px (96 DPI 기준)
                            // 또는 더 간단하게: tenths * 0.945 = px (40 tenths = 37.8px)
                            var actualMeasureWidth = measureWidth; // 기본값
                            if (measureData.width != null && measureData.width > 0) {
                                // tenths를 픽셀로 변환 (스케일 조정 가능)
                                // 기본 스케일: 1 tenth = 약 0.945px (40 tenths = 37.8px)
                                actualMeasureWidth = measureData.width * 0.945;
                                log('Using MusicXML width for measure ' + measureNum + ': ' + measureData.width + ' tenths = ' + actualMeasureWidth + 'px');
                            }
                            
                            // 첫 번째 마디는 x=0에서 시작 (clef/key/time signature가 자동으로 왼쪽 공간 할당)
                            // 이후 마디는 이전 마디의 끝에서 시작 (간격 없음)
                            // 동적 너비를 고려하여 위치 계산
                            var xPos = 0;
                            if (measureIndexInLine > 0) {
                                // 이전 마디들의 너비 합산
                                for (var i = 0; i < measureIndexInLine; i++) {
                                    var prevIndex = lineIndex * measuresPerLine + i;
                                    if (prevIndex < measuresData.length) {
                                        var prevMeasure = measuresData[prevIndex];
                                        var prevWidth = measureWidth; // 기본값
                                        if (prevMeasure.width != null && prevMeasure.width > 0) {
                                            prevWidth = prevMeasure.width * 0.945;
                                        }
                                        xPos += prevWidth;
                                    }
                                }
                            }
                            
                            // 첫 번째 줄의 첫 번째 마디는 clef/key/time signature가 추가되므로
                            // VexFlow가 자동으로 왼쪽 공간을 할당하므로 xPos는 0에서 시작
                            var yPos = 50 + (lineIndex * (measureHeight + lineSpacing));
                            
                            measureYPositions[measureNum] = yPos;
                            log('Rendering measure ' + measureNum + ' at (x=' + xPos + ', y=' + yPos + '), line=' + lineIndex + ', position=' + measureIndexInLine + '/4');
                            
                            // 반복 기호 타입 결정
                            var repeatBeginType = null;
                            var repeatEndType = null;
                            if (measureData.repeatBars && measureData.repeatBars.length > 0) {
                                measureData.repeatBars.forEach(function(repeatBar) {
                                    var repeatType = null;
                                    if (repeatBar.type === 'forward') {
                                        repeatType = VF.Barline.type.REPEAT_BEGIN;
                                    } else if (repeatBar.type === 'backward') {
                                        repeatType = VF.Barline.type.REPEAT_END;
                                    } else if (repeatBar.type === 'both') {
                                        repeatType = VF.Barline.type.REPEAT_BOTH;
                                    }
                                    
                                    if (repeatType) {
                                        if (repeatBar.location === 'left') {
                                            repeatBeginType = repeatType;
                                        } else {
                                            repeatEndType = repeatType;
                                        }
                                    }
                                });
                            }
                            
                            // 상단 오선보 (Treble Clef) - 4마디 단위로 배치 (간격 없이 연속)
                            // Stave 너비를 실제 마디 너비로 설정 (MusicXML width 값 사용)
                            var staveWidth = actualMeasureWidth;
                            var trebleStave = new VF.Stave(xPos, yPos, staveWidth);
                            
                            // 높은음 자리표와 박자표는 맨 처음 한 줄(첫 번째 줄의 첫 번째 마디)에만 추가
                            // implicit 마디("0")는 이미 첫 번째 위치에 있으므로 별도 체크 불필요
                            if (lineIndex === 0 && measureIndexInLine === 0) {
                                trebleStave.addClef("treble");
                                
                                // 박자표는 맨 처음 한 줄에만 추가
                                trebleStave.addTimeSignature(numerator + "/" + denominator);
                            }
                            
                            // 조표는 첫 번째 마디(measureNumber === "1" 또는 "0")에만 추가
                            // 단, 첫 번째 줄의 첫 번째 마디일 때만 (중복 방지)
                            if ((lineIndex === 0 && measureIndexInLine === 0) && 
                                (measureNum === "1" || measureNum === 1 || (typeof measureNum === 'string' && measureNum.trim() === "1") ||
                                 measureNum === "0" || measureNum === 0 || (typeof measureNum === 'string' && measureNum.trim() === "0"))) {
                                // VexFlow는 "C", "G", "D", "A", "E", "B", "F#", "C#", "F", "Bb", "Eb", "Ab", "Db", "Gb", "Cb" 형식 지원
                                log('Processing key signature for first measure (measureNum=' + measureNum + ', index=' + index + ', line=' + lineIndex + '): "' + keySignatureValue + '"');
                                if (keySignatureValue && keySignatureValue !== "" && keySignatureValue.trim() !== "") {
                                    try {
                                        // VexFlow addKeySignature는 clef 다음, time signature 전에 호출해야 함
                                        trebleStave.addKeySignature(keySignatureValue.trim());
                                        log('Successfully added key signature: "' + keySignatureValue.trim() + '" to first measure (measureNum=' + measureNum + ')');
                                    } catch (e) {
                                        logError('Error adding key signature "' + keySignatureValue + '": ' + e.message);
                                        logError('Stack: ' + e.stack);
                                        // 조표 추가 실패 시에도 계속 진행
                                    }
                                } else {
                                    log('Key signature is empty or C major (no sharps/flats) for measure ' + measureNum);
                                }
                            }
                            if (repeatBeginType) {
                                trebleStave.setBegBarType(repeatBeginType);
                            }
                            if (repeatEndType) {
                                trebleStave.setEndBarType(repeatEndType);
                            }
                            trebleStave.setContext(context).draw();
                            
                            // 마디 번호 표시
                            context.setFont("Arial", 10, "normal");
                            context.setFillStyle("#666666");
                            context.fillText("M" + measureNum, xPos + 2, yPos - 5);
                            
                            // 마디선(Barline) 표시 - 각 마디 끝에 세로선 그리기 (마지막 마디가 아닌 경우만)
                            // 마지막 마디가 아니거나, 줄의 마지막 마디인 경우에만 마디선 그리기
                            var isLastMeasureInLine = (measureIndexInLine === measuresPerLine - 1) || (index === measuresData.length - 1);
                            if (!isLastMeasureInLine) {
                                try {
                                    // 동적 너비를 고려하여 마디 끝 위치 계산
                                    var staveEndX = trebleStave.getX() + trebleStave.getWidth();
                                    var staveY = trebleStave.getY();
                                    var staveHeight = trebleStave.getHeight();
                                    
                                    // 마디 끝에 세로선 그리기
                                    context.beginPath();
                                    context.setStrokeStyle("#000000");
                                    context.setLineWidth(1);
                                    context.moveTo(staveEndX, staveY);
                                    context.lineTo(staveEndX, staveY + staveHeight);
                                    context.stroke();
                                    log('Drew barline at end of measure ' + measureNum + ' at x=' + staveEndX + ' (width=' + actualMeasureWidth + ')');
                                } catch (e) {
                                    logError('Error drawing barline: ' + e.message);
                                }
                            }
                            
                            // 상단 오선보 노트 렌더링
                            var trebleNotes = [];
                            log('Measure ' + measureNum + ' trebleNotesJson type: ' + typeof trebleNotesJson + ', length: ' + (trebleNotesJson ? trebleNotesJson.length : 0));
                            if (trebleNotesJson && typeof trebleNotesJson === 'string' && trebleNotesJson.trim() !== '') {
                                try {
                                    var jsonStr = trebleNotesJson.trim();
                                    log('Parsing treble notes for measure ' + measureNum + ' (first 200 chars): ' + jsonStr.substring(0, Math.min(200, jsonStr.length)));
                                    // eval 대신 Function 생성자 사용 (더 안전)
                                    var notesArray = new Function('return [' + jsonStr + '];')();
                                    trebleNotes = notesArray;
                                    log('Parsed ' + trebleNotes.length + ' treble notes');
                                    // 각 노트 확인
                                    trebleNotes.forEach(function(note, idx) {
                                        try {
                                            log('Note ' + idx + ' type: ' + typeof note + ', is StaveNote: ' + (note instanceof VF.StaveNote) + ', constructor: ' + (note && note.constructor ? note.constructor.name : 'null'));
                                            if (note && typeof note.getDuration === 'function') {
                                                log('Treble note ' + idx + ': duration=' + note.getDuration());
                                            } else {
                                                log('Treble note ' + idx + ': getDuration is not a function');
                                            }
                                        } catch (e) {
                                            logError('Error checking treble note ' + idx + ': ' + e.message);
                                        }
                                    });
                                } catch (e) {
                                    logError('Error parsing treble notes for measure ' + measureNum + ': ' + e.message);
                                    logError('Stack: ' + e.stack);
                                    logError('Treble notes JSON (first 500 chars): ' + (trebleNotesJson ? trebleNotesJson.substring(0, Math.min(500, trebleNotesJson.length)) : 'null'));
                                }
                            } else if (Array.isArray(trebleNotesJson)) {
                                trebleNotes = trebleNotesJson;
                                log('Using treble notes from array: ' + trebleNotes.length);
                            } else {
                                log('No treble notes for measure ' + measureNum + ' (empty or invalid)');
                            }
                            
                            if (trebleNotes.length > 0) {
                                try {
                                            // 기호 정보 적용 (공통 함수 사용)
                                    trebleNotes.forEach(function(note, index) {
                                        try {
                                            // 공통 함수로 노트 처리 (Accidental, Dot, Articulation)
                                            processNote(note);
                                            
                                            // 손가락 번호 표시
                                            if (note.finger) {
                                                try {
                                                    var fingerAnnotation = new VF.Annotation(note.finger);
                                                    fingerAnnotation.setVerticalJustification(VF.Annotation.VerticalJustify.BOTTOM);
                                                    fingerAnnotation.setFont('Arial', 12, 'bold');
                                                    note.addAnnotation(0, fingerAnnotation);
                                                } catch (e) {
                                                    logError('Error adding finger: ' + e.message);
                                                }
                                            }
                                            
                                            // 페달 표시
                                            if (note.pedalType) {
                                                try {
                                                    var pedalMark = note.pedalType === 'start' ? 'Ped.' : (note.pedalType === 'stop' ? '*' : '');
                                                    if (pedalMark) {
                                                        var pedalAnnotation = new VF.Annotation(pedalMark);
                                                        pedalAnnotation.setVerticalJustification(VF.Annotation.VerticalJustify.BOTTOM);
                                                        pedalAnnotation.setFont('Arial', 10, 'italic');
                                                        note.addAnnotation(0, pedalAnnotation);
                                                    }
                                                } catch (e) {
                                                    logError('Error adding pedal: ' + e.message);
                                                }
                                            }
                                        } catch (e) {
                                            logError('Error processing note: ' + e.message);
                                        }
                                    });
                                    
                                    // 노트들의 총 duration 계산
                                    var totalDuration = 0;
                                    trebleNotes.forEach(function(note) {
                                        try {
                                            var duration = note.getDuration();
                                            var fraction = durationFractionMap[duration] || 1/4;
                                            totalDuration += fraction;
                                        } catch (e) {
                                            logError('Error getting duration: ' + e.message);
                                            totalDuration += 1/4;
                                        }
                                    });
                                    
                                    // 못갖춘마디(첫 번째 마디) 확인: index === 0 또는 measureNum === "0" 또는 measureNum === "1" 또는 "X"로 시작
                                    var isPickupMeasure = (index === 0) || (measureNum === "0") || (measureNum === "1") || 
                                                          (typeof measureNum === 'string' && measureNum.startsWith('X'));
                                    
                                    // num_beats를 실제 duration 합에 맞춤
                                    // 못갖춘마디가 아닌 경우: VexFlow는 정확히 num_beats만큼의 duration이 필요
                                    // 못갖춘마디인 경우: totalDuration에 맞춰서 requiredBeats 설정
                                    var requiredBeats;
                                    if (isPickupMeasure) {
                                        // 못갖춘마디: totalDuration에 맞춤 (최소 1)
                                        requiredBeats = Math.max(1, Math.ceil(totalDuration));
                                        log('Pickup measure ' + measureNum + ' treble: ' + trebleNotes.length + ' notes, total duration: ' + totalDuration + ', required beats: ' + requiredBeats + ' (anacrusis)');
                                    } else {
                                        // 일반 마디: numerator와 totalDuration 중 큰 값 사용
                                        requiredBeats = Math.max(numerator, Math.max(1, Math.ceil(totalDuration)));
                                        log('Measure ' + measureNum + ' treble: ' + trebleNotes.length + ' notes, total duration: ' + totalDuration + ', required beats: ' + requiredBeats);
                                        
                                        // totalDuration이 numerator보다 작으면 쉼표 추가 필요 (못갖춘마디가 아닌 경우만)
                                        if (totalDuration < numerator) {
                                            log('Warning: Measure ' + measureNum + ' treble has only ' + totalDuration + ' beats, need ' + numerator + ' beats');
                                            // 부족한 duration만큼 쉼표 추가
                                            var missingDuration = numerator - totalDuration;
                                            var restDuration = 'qr'; // quarter rest
                                            if (missingDuration >= 2) {
                                                restDuration = 'hr';
                                            } else if (missingDuration >= 1) {
                                                restDuration = 'qr';
                                            } else if (missingDuration >= 0.5) {
                                                restDuration = '8r';
                                            } else {
                                                restDuration = '16r';
                                            }
                                            // StaveNote 쉼표 생성
                                            var restNote = new VF.StaveNote({ 
                                                clef: 'treble',
                                                keys: ['b/4'], 
                                                duration: restDuration 
                                            });
                                            trebleNotes.push(restNote);
                                            totalDuration = numerator;
                                            requiredBeats = numerator;
                                            log('Added rest note to fill measure: ' + restDuration);
                                        }
                                    }
                                    
                                    // 붙임줄 및 슬러 처리 (변환 전에 인덱스 저장)
                                    var tiePairIndices = [];
                                    var slurPairIndices = [];
                                    for (var i = 0; i < trebleNotes.length; i++) {
                                        var note = trebleNotes[i];
                                        if (note.tieType === 'start' && i < trebleNotes.length - 1) {
                                            var nextNote = trebleNotes[i + 1];
                                            if (nextNote && nextNote.tieType === 'stop') {
                                                tiePairIndices.push([i, i + 1]);
                                            }
                                        }
                                        if (note.slurType === 'start' && i < trebleNotes.length - 1) {
                                            var nextNote = trebleNotes[i + 1];
                                            if (nextNote && nextNote.slurType === 'stop') {
                                                slurPairIndices.push([i, i + 1]);
                                            }
                                        }
                                    }
                                    
                                    // StaveNote를 그대로 사용 (변환 불필요)
                                    var trebleStaveNotes = trebleNotes.filter(function(note) {
                                        return note instanceof VF.StaveNote;
                                    });
                                    
                                    // Beam 자동 생성 (규칙 파일 참조)
                                    var trebleBeams = [];
                                    try {
                                        trebleBeams = VF.Beam.generateBeams(trebleStaveNotes);
                                        log('Generated ' + trebleBeams.length + ' beams for treble notes');
                                    } catch (e) {
                                        logError('Error generating beams: ' + e.message);
                                    }
                                    
                                    // VexFlow는 정확히 num_beats만큼의 duration이 필요하므로, 정확히 맞춤
                                    var trebleVoice = new VF.Voice({ num_beats: requiredBeats, beat_value: denominator });
                                    trebleVoice.addTickables(trebleStaveNotes);
                                    try {
                                        // 4마디 단위 배치에 맞춰 formatter 너비 조정 (간격 없이 연속)
                                        // Formatter 너비는 실제 마디 너비에서 여백 제외 (MusicXML width 값 사용)
                                        var formatterWidth = Math.max(actualMeasureWidth - 20, 100);  // 최소 100px 보장
                                        
                                        // implicit 마디("0")의 경우 clef/key/time signature가 있으므로 더 작은 formatter width 사용
                                        if (measureNum === "0" || measureNum === 0 || (typeof measureNum === 'string' && measureNum.trim() === "0")) {
                                            // implicit 마디는 clef/key/time signature가 있으므로 더 작은 formatter width 사용 (공간을 2배로 늘림)
                                            formatterWidth = Math.max(actualMeasureWidth - 120, 80); // clef/key/time signature 공간 고려 (60px -> 120px로 2배 증가)
                                            log('Pickup measure formatter width adjusted: ' + formatterWidth + 'px (staveWidth: ' + actualMeasureWidth + 'px)');
                                        }
                                        
                                        var formatter = new VF.Formatter().joinVoices([trebleVoice]).format([trebleVoice], formatterWidth);
                                        
                                        // 렌더링 전 path 개수 저장
                                        var svg = div.querySelector('svg');
                                        var pathCountBefore = svg ? svg.querySelectorAll('path').length : 0;
                                        
                                        trebleVoice.draw(context, trebleStave);
                                        
                                        // Beam 그리기 (규칙 파일 참조)
                                        trebleBeams.forEach(function(beam) {
                                            try {
                                                beam.setContext(context).draw();
                                            } catch (e) {
                                                logError('Error drawing beam: ' + e.message);
                                            }
                                        });
                                        
                                        // 렌더링 후 새로 추가된 path 요소에 속성 추가 (StaveNote용)
                                        if (svg) {
                                            var paths = svg.querySelectorAll('path');
                                            
                                            // 각 StaveNote에 대해 해당하는 path 찾기
                                            trebleStaveNotes.forEach(function(note, index) {
                                                try {
                                                    // StaveNote의 keys를 문자열로 변환하여 저장
                                                    var keys = note.getKeys();
                                                    if (keys && keys.length > 0) {
                                                        var keysStr = keys.join(',');
                                                        
                                                        // 노트의 bounding box를 사용하여 해당 path 찾기
                                                        var noteBBox = note.getBoundingBox();
                                                        if (noteBBox) {
                                                            // bounding box와 가장 가까운 path 찾기
                                                            var closestPath = null;
                                                            var minDistance = Infinity;
                                                            
                                                            for (var i = pathCountBefore; i < paths.length; i++) {
                                                                var path = paths[i];
                                                                try {
                                                                    var pathBBox = path.getBBox();
                                                                    if (pathBBox) {
                                                                        // 중심점 거리 계산
                                                                        var noteCenterX = noteBBox.x + noteBBox.width / 2;
                                                                        var noteCenterY = noteBBox.y + noteBBox.height / 2;
                                                                        var pathCenterX = pathBBox.x + pathBBox.width / 2;
                                                                        var pathCenterY = pathBBox.y + pathBBox.height / 2;
                                                                        
                                                                        var distance = Math.sqrt(
                                                                            Math.pow(noteCenterX - pathCenterX, 2) + 
                                                                            Math.pow(noteCenterY - pathCenterY, 2)
                                                                        );
                                                                        
                                                                        // 거리가 가까우면 선택 (50 픽셀 이내)
                                                                        if (distance < 50 && distance < minDistance) {
                                                                            minDistance = distance;
                                                                            closestPath = path;
                                                                        }
                                                                    }
                                                                } catch (e) {
                                                                    // getBBox 실패 시 건너뛰기
                                                                }
                                                            }
                                                            
                                                            // 가장 가까운 path에 속성 추가
                                                            if (closestPath) {
                                                                closestPath.setAttribute('data-note', keysStr);
                                                                closestPath.setAttribute('data-measure', measureNum);
                                                                closestPath.setAttribute('data-staff', 'treble');
                                                                closestPath.setAttribute('data-note-index', index.toString());
                                                            }
                                                        }
                                                    }
                                                } catch (e) {
                                                    logError('Error adding data-note attribute to StaveNote: ' + e.message);
                                                }
                                            });
                                        }
                                        
                                        // 붙임줄 그리기 (StaveNote용 - 인덱스로 매핑)
                                        tiePairIndices.forEach(function(indices) {
                                            try {
                                                var firstIdx = indices[0];
                                                var lastIdx = indices[1];
                                                
                                                if (firstIdx < trebleStaveNotes.length && lastIdx < trebleStaveNotes.length) {
                                                    var firstNote = trebleStaveNotes[firstIdx];
                                                    var lastNote = trebleStaveNotes[lastIdx];
                                                    
                                                    if (firstNote && lastNote) {
                                                        var tie = new VF.StaveTie({
                                                            first_note: firstNote,
                                                            last_note: lastNote,
                                                            first_indices: [0],
                                                            last_indices: [0]
                                                        });
                                                        tie.setContext(context);
                                                        tie.draw();
                                                        log('Drew tie between treble StaveNotes');
                                                    }
                                                }
                                            } catch (e) {
                                                logError('Error drawing tie for StaveNote: ' + e.message);
                                            }
                                        });
                                        
                                        // 슬러 그리기 (StaveNote용 - 인덱스로 매핑)
                                        slurPairIndices.forEach(function(indices) {
                                            try {
                                                var firstIdx = indices[0];
                                                var lastIdx = indices[1];
                                                
                                                if (firstIdx < trebleStaveNotes.length && lastIdx < trebleStaveNotes.length) {
                                                    var firstNote = trebleStaveNotes[firstIdx];
                                                    var lastNote = trebleStaveNotes[lastIdx];
                                                    
                                                    if (firstNote && lastNote) {
                                                        var slur = new VF.Curve({
                                                            from: firstNote,
                                                            to: lastNote
                                                        });
                                                        slur.setContext(context);
                                                        slur.draw();
                                                        log('Drew slur between treble StaveNotes');
                                                    }
                                                }
                                            } catch (e) {
                                                logError('Error drawing slur for StaveNote: ' + e.message);
                                            }
                                        });
                                        
                                        log('Successfully rendered ' + trebleStaveNotes.length + ' treble StaveNotes for measure ' + measureNum);
                                        
                                        // 셈여림 기호 렌더링
                                        if (measureData.dynamics && measureData.dynamics.length > 0) {
                                            measureData.dynamics.forEach(function(dyn) {
                                                try {
                                                    var dynamics = new VF.Dynamics({
                                                        text: dyn.text,
                                                        x: trebleStave.getX() + 50, // 노트 시작 위치
                                                        y: dyn.placement === 'below' ? (yPos + 80) : (yPos - 20)
                                                    });
                                                    dynamics.setContext(context);
                                                    dynamics.draw();
                                                    log('Drew dynamics: ' + dyn.text);
                                                } catch (e) {
                                                    logError('Error drawing dynamics: ' + e.message);
                                                }
                                            });
                                        }
                                        
                                        // Segno 렌더링
                                        if (measureData.segno) {
                                            try {
                                                var segno = new VF.Segno({
                                                    x: trebleStave.getX() - 10,
                                                    y: yPos + 20
                                                });
                                                segno.setContext(context);
                                                segno.draw();
                                                log('Drew segno');
                                            } catch (e) {
                                                logError('Error drawing segno: ' + e.message);
                                            }
                                        }
                                        
                                        // Coda 렌더링
                                        if (measureData.coda) {
                                            try {
                                                var coda = new VF.Coda({
                                                    x: trebleStave.getX() - 10,
                                                    y: yPos + 20
                                                });
                                                coda.setContext(context);
                                                coda.draw();
                                                log('Drew coda');
                                            } catch (e) {
                                                logError('Error drawing coda: ' + e.message);
                                            }
                                        }
                                        
                                        // D.C., D.S., To Coda 텍스트 렌더링
                                        if (measureData.dacapo || measureData.dalsegno || measureData.tocoda) {
                                            try {
                                                var text = '';
                                                if (measureData.dacapo) text = 'D.C.';
                                                else if (measureData.dalsegno) text = 'D.S.';
                                                else if (measureData.tocoda) text = 'To Coda';
                                                
                                                if (text) {
                                                    var textNote = new VF.TextNote({
                                                        text: text,
                                                        x: trebleStave.getX() + 10,
                                                        y: yPos - 30
                                                    });
                                                    textNote.setContext(context);
                                                    textNote.draw();
                                                    log('Drew text: ' + text);
                                                }
                                            } catch (e) {
                                                logError('Error drawing text note: ' + e.message);
                                            }
                                        }
                                        
                                    } catch (e) {
                                        logError('Error in formatter/draw for treble notes: ' + e.message);
                                        // 에러가 발생해도 계속 진행
                                    }
                                } catch (e) {
                                    logError('Error rendering treble notes for measure ' + measureNum + ': ' + e.message);
                                    logError('Stack: ' + e.stack);
                                }
                            }
                            
                        });
                        
                        // SVG 좌우 여백 조정: 실제 콘텐츠 경계 계산하여 viewBox 조정
                        if (svg) {
                            try {
                                // 모든 렌더링된 요소의 bounding box 계산
                                var allElements = svg.querySelectorAll('*');
                                var minX = Infinity;
                                var maxX = -Infinity;
                                var minY = Infinity;
                                var maxY = -Infinity;
                                
                                allElements.forEach(function(element) {
                                    try {
                                        var bbox = element.getBBox();
                                        if (bbox && bbox.width > 0 && bbox.height > 0) {
                                            minX = Math.min(minX, bbox.x);
                                            maxX = Math.max(maxX, bbox.x + bbox.width);
                                            minY = Math.min(minY, bbox.y);
                                            maxY = Math.max(maxY, bbox.y + bbox.height);
                                        }
                                    } catch (e) {
                                        // getBBox가 실패할 수 있음 (일부 요소는 bbox가 없음)
                                    }
                                });
                                
                                // 좌우 여백 약간만 추가 (각 10px)
                                var horizontalPadding = 10;
                                var verticalPadding = 10;
                                
                                if (minX !== Infinity && maxX !== -Infinity) {
                                    // 실제 콘텐츠 경계에 여백 추가
                                    var viewBoxX = Math.max(0, minX - horizontalPadding);
                                    var viewBoxWidth = maxX - viewBoxX + horizontalPadding;
                                    var viewBoxY = Math.max(0, minY - verticalPadding);
                                    var viewBoxHeight = maxY - viewBoxY + verticalPadding;
                                    
                                    // SVG 너비와 viewBox 설정
                                    svg.setAttribute('width', viewBoxWidth);
                                    svg.setAttribute('viewBox', viewBoxX + ' ' + viewBoxY + ' ' + viewBoxWidth + ' ' + viewBoxHeight);
                                    svg.setAttribute('preserveAspectRatio', 'none');
                                    
                                    // SVG 스타일 업데이트 (100% 너비로 설정하여 꽉 차게)
                                    svg.setAttribute('style', 'display: block; width: 100%; height: ' + $totalHeight + 'px; min-height: ' + $totalHeight + 'px; overflow: visible; margin: 0 auto;');
                                    
                                    log('SVG viewBox adjusted: x=' + viewBoxX + ', width=' + viewBoxWidth + ', content range: ' + minX + ' to ' + maxX);
                                } else {
                                    // bbox 계산 실패 시 동적 너비를 고려하여 계산
                                    var lastMeasureIndex = measuresData.length - 1;
                                    var lastLineIndex = Math.floor(lastMeasureIndex / measuresPerLine);
                                    var lastMeasureIndexInLine = lastMeasureIndex % measuresPerLine;
                                    
                                    // 마지막 줄의 모든 마디 너비 합산
                                    var totalLineWidth = 0;
                                    for (var i = 0; i <= lastMeasureIndexInLine; i++) {
                                        var measureIdx = lastLineIndex * measuresPerLine + i;
                                        if (measureIdx < measuresData.length) {
                                            var m = measuresData[measureIdx];
                                            var mWidth = measureWidth; // 기본값
                                            if (m.width != null && m.width > 0) {
                                                mWidth = m.width * 0.945;
                                            }
                                            totalLineWidth += mWidth;
                                        }
                                    }
                                    
                                    var actualWidth = totalLineWidth + (horizontalPadding * 2);
                                    
                                    svg.setAttribute('width', actualWidth);
                                    svg.setAttribute('style', 'display: block; width: 100%; height: ' + $totalHeight + 'px; min-height: ' + $totalHeight + 'px; overflow: visible; margin: 0 auto;');
                                    
                                    log('SVG width adjusted (fallback with dynamic widths): ' + actualWidth + 'px');
                                }
                            } catch (e) {
                                logError('Error adjusting SVG viewBox: ' + e.message);
                            }
                        }
                        
                        log('Rendering complete - individual measures');
                        if (typeof AndroidInterface !== 'undefined' && AndroidInterface.onRenderComplete) {
                            AndroidInterface.onRenderComplete();
                        }
                    } catch (e) {
                        logError('Error during rendering: ' + e.message);
                        logError(e.stack);
                        if (typeof AndroidInterface !== 'undefined' && AndroidInterface.onRenderComplete) {
                            AndroidInterface.onRenderComplete();
                        }
                    }
                }
                
                // DOM 로드 후 VexFlow 초기화 및 렌더링 시작
                if (document.readyState === 'loading') {
                    document.addEventListener('DOMContentLoaded', function() {
                        log('DOM loaded, initializing VexFlow...');
                        startRendering();
                    });
                } else {
                    log('DOM already loaded, initializing VexFlow...');
                    startRendering();
                }
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    /** 모든 마디를 한 번에 렌더링하는 VexFlow HTML 생성 */
    private fun generateFullSheetMusicHTMLWithAllStaves(
        measuresData: List<Triple<Int, String, Double>>,
        numerator: Int,
        denominator: Int,
        keySignature: String = "C"
    ): String {
        // 각 마디의 높이 (약 200px)
        val measureHeight = 200
        val totalHeight = measuresData.size * measureHeight + 100

        // 마디별 노트 데이터를 JavaScript 배열로 변환
        val measuresJson =
            measuresData.joinToString(",\n            ") { (measureNum, notesJson, totalBeats) ->
                """
            {
                measure: "$measureNum",
                notes: ${if (notesJson.isBlank()) "[]" else "[$notesJson]"},
                totalBeats: $totalBeats
            }
            """.trimIndent()
            }

        return """
        <!DOCTYPE html>
        <html>
        <head>
            <style>
                html {
                    height: auto;
                    min-height: ${totalHeight}px;
                }
                body { 
                    margin: 0; 
                    padding: 0; 
                    background: white; 
                    overflow-y: auto;
                    overflow-x: hidden;
                    height: auto;
                    min-height: ${totalHeight}px;
                }
                #sheet { 
                    width: 100%; 
                    min-height: ${totalHeight}px;
                    height: auto;
                    position: relative;
                    display: flex;
                    justify-content: center;
                    align-items: flex-start;
                }
                svg { display: block; }
                .measure-container {
                    position: relative;
                    margin-bottom: 20px;
                }
                .measure-arrow {
                    position: absolute;
                    left: -30px;
                    top: 50%;
                    transform: translateY(-50%);
                    width: 0;
                    height: 0;
                    border-left: 15px solid #1db954;
                    border-top: 10px solid transparent;
                    border-bottom: 10px solid transparent;
                    display: none;
                }
                .measure-arrow.active {
                    display: block;
                }
            </style>
            ${getCommonJavaScriptUtils()}
        </head>
        <body>
            <div id="sheet"></div>
            <script>
                function startRendering() {
                    var div = document.getElementById("sheet");
                    if (!div) {
                        logError('Sheet div not found');
                        return;
                    }
                    
                    // VexFlow 초기화 및 렌더링
                    loadVexFlow().then(function(VF) {
                        log('VexFlow initialized successfully');
                        renderSheetMusic(VF, div);
                    }).catch(function(error) {
                        logError('Failed to initialize VexFlow: ' + error.message);
                        if (typeof AndroidInterface !== 'undefined' && AndroidInterface.onRenderComplete) {
                            AndroidInterface.onRenderComplete();
                        }
                    });
                }
                
                function renderSheetMusic(VF, div) {
                    // VF를 전역 변수로 설정하여 processNote 등에서 접근 가능하도록 함
                    window.VF = VF;
                    
                    try {
                        log('Starting VexFlow rendering for all measures...');
                        
                        var renderer = new VF.Renderer(div, VF.Renderer.Backends.SVG);
                        // 렌더러 크기를 충분히 크게 설정 (무제한 스크롤을 위해)
                        renderer.resize(800, $totalHeight);
                        var context = renderer.getContext();
                        
                        // SVG 높이를 실제 콘텐츠 높이로 설정 (무제한 스크롤)
                        var svg = div.querySelector('svg');
                        if (svg) {
                            // SVG 높이를 실제 콘텐츠 높이로 설정하여 무제한 스크롤 가능
                            svg.setAttribute('style', 'display: block; width: 100%; height: ' + $totalHeight + 'px; min-height: ' + $totalHeight + 'px;');
                            svg.setAttribute('height', $totalHeight);
                        }
                        
                        var measuresData = [$measuresJson];
                        log('Total measures to render: ' + measuresData.length);
                        
                        var numerator = $numerator;
                        var denominator = $denominator;
                        var keySignature = "$keySignature";
                        var beatDuration = 4;
                        var measureHeight = $measureHeight;
                        var currentMeasure = 1;
                        
                        // 마디별 Y 위치 저장
                        var measurePositions = {};
                        var measureYPositions = {};
                        
                        // 각 마디 렌더링
                        measuresData.forEach(function(measureData, index) {
                            var measureNum = measureData.measure;
                            var notesJson = measureData.notes;
                            var yPos = 50 + (index * measureHeight);
                            
                            // 렌더링 시점의 yPos 저장
                            measureYPositions[measureNum] = yPos;
                            measurePositions[measureNum] = yPos;
                            
                            log('Rendering measure ' + measureNum + ' at y=' + yPos);
                            
                            // Stave 생성
                            var stave = new VF.Stave(10, yPos, 750);
                            stave.addClef("treble");
                            stave.addKeySignature(keySignature);
                            stave.addTimeSignature(numerator + "/" + denominator);
                            stave.setContext(context).draw();
                            
                            // 마디 번호 표시
                            context.setFont("Arial", 12, "normal");
                            context.setFillStyle("#666666");
                            context.fillText("M" + measureNum, 5, yPos - 10);
                            
                            // notes 파싱
                            var notes = [];
                            if (notesJson) {
                                try {
                                    // notesJson이 문자열인 경우
                                    if (typeof notesJson === 'string' && notesJson.trim() !== '') {
                                        eval('notes = [' + notesJson + '];');
                                        log('Measure ' + measureNum + ': parsed ' + notes.length + ' notes from string');
                                    } 
                                    // notesJson이 이미 배열인 경우
                                    else if (Array.isArray(notesJson)) {
                                        notes = notesJson;
                                        log('Measure ' + measureNum + ': using ' + notes.length + ' notes from array');
                                    }
                                    // notesJson이 객체인 경우 (단일 노트)
                                    else if (typeof notesJson === 'object') {
                                        notes = [notesJson];
                                        log('Measure ' + measureNum + ': using single note object');
                                    }
                                } catch (e) {
                                    logError('Error parsing notes for measure ' + measureNum + ': ' + e.message);
                                }
                            }
                            
                            if (notes.length > 0) {
                                // 박자 계산
                                var beatAccumulator = 0;
                                var beamableNotes = [];
                                notes.forEach(function(note) {
                                    try {
                                        var duration = note.getDuration();
                                        var fraction = durationFractionMap[duration] || 1/4;
                                        beatAccumulator += fraction;
                                        if (duration === '8' || duration === '16' || duration === '32') {
                                            beamableNotes.push(note);
                                        }
                                    } catch (e) {
                                        beatAccumulator += 1/4;
                                    }
                                });
                                
                                var totalBeats = beatAccumulator / (1 / beatDuration);
                                var voiceBeats = Math.max(totalBeats, numerator);
                                
                                var voice = new VF.Voice({ num_beats: voiceBeats, beat_value: beatDuration });
                                voice.addTickables(notes);
                                
                                try {
                                    var formatter = new VF.Formatter().joinVoices([voice]).format([voice], 600);
                                    voice.draw(context, stave);
                                    
                                    // 베벨 추가
                                    if (beamableNotes.length > 1) {
                                        var beamGroups = [];
                                        var currentGroup = [];
                                        for (var i = 0; i < beamableNotes.length; i++) {
                                            var note = beamableNotes[i];
                                            var duration = note.getDuration();
                                            if (currentGroup.length === 0 || currentGroup[0].getDuration() === duration) {
                                                currentGroup.push(note);
                                            } else {
                                                if (currentGroup.length > 1) beamGroups.push(currentGroup);
                                                currentGroup = [note];
                                            }
                                        }
                                        if (currentGroup.length > 1) beamGroups.push(currentGroup);
                                        beamGroups.forEach(function(group) {
                                            try {
                                                var beam = new VF.Beam(group);
                                                beam.setContext(context);
                                                beam.draw();
                                            } catch (e) {
                                                logError('Error adding beam: ' + e.message);
                                            }
                                        });
                                    }
                                } catch (e) {
                                    logError('Error rendering measure ' + measureNum + ': ' + e.message);
                                }
                            }
                            
                            // 마디 강조 배경 및 클릭 이벤트 추가
                            var svg = div.querySelector('svg');
                            if (svg) {
                                // 강조 배경 사각형 (기본 숨김)
                                var highlightRect = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
                                highlightRect.setAttribute('x', '5');
                                highlightRect.setAttribute('y', (yPos - 20).toString());
                                highlightRect.setAttribute('width', '760');
                                highlightRect.setAttribute('height', (measureHeight - 10).toString());
                                highlightRect.setAttribute('fill', '#1db954');
                                highlightRect.setAttribute('opacity', '0.15');
                                highlightRect.setAttribute('id', 'highlight-' + measureNum);
                                highlightRect.setAttribute('style', 'cursor: pointer; display: none;');
                                highlightRect.setAttribute('data-measure', measureNum);
                                
                                // 클릭 이벤트 추가
                                highlightRect.addEventListener('click', function(e) {
                                    e.stopPropagation();
                                    var clickedMeasure = parseInt(this.getAttribute('data-measure'));
                                    log('Measure ' + clickedMeasure + ' clicked');
                                    
                                    // Android에 마디 선택 알림
                                    if (typeof AndroidInterface !== 'undefined' && AndroidInterface.onMeasureSelected) {
                                        AndroidInterface.onMeasureSelected(clickedMeasure);
                                    }
                                });
                                
                                svg.appendChild(highlightRect);
                                
                                // 화살표를 Stave 왼쪽에 배치 (yPos는 Stave의 상단 위치)
                                var arrowY = yPos + 30; // Stave 중앙 높이
                                var arrow = document.createElementNS('http://www.w3.org/2000/svg', 'polygon');
                                // 더 큰 화살표: 왼쪽에서 오른쪽을 가리키는 삼각형
                                arrow.setAttribute('points', 
                                    '0,' + (arrowY - 8) + ' ' + 
                                    '20,' + arrowY + ' ' + 
                                    '0,' + (arrowY + 8));
                                arrow.setAttribute('fill', '#1db954');
                                arrow.setAttribute('stroke', '#ffffff');
                                arrow.setAttribute('stroke-width', '2');
                                arrow.setAttribute('id', 'arrow-' + measureNum);
                                arrow.setAttribute('opacity', '0');
                                arrow.setAttribute('style', 'cursor: pointer;');
                                svg.appendChild(arrow);
                            }
                            
                            // 마디 렌더링 완료 후 진행 상황 업데이트
                            if (typeof AndroidInterface !== 'undefined' && AndroidInterface.onRenderProgress) {
                                AndroidInterface.onRenderProgress(measureNum, measuresData.length);
                            }
                        });
                        
                        // 화살표 업데이트 함수
                        function updateArrow(measureNum) {
                            log('Updating arrow for measure ' + measureNum);
                            var svg = div.querySelector('svg');
                            if (!svg) {
                                logError('SVG not found for arrow update');
                                return;
                            }
                            
                            // 이전 화살표 숨기기
                            var prevArrow = svg.querySelector('#arrow-' + currentMeasure);
                            if (prevArrow) {
                                prevArrow.setAttribute('opacity', '0');
                                prevArrow.setAttribute('fill', '#1db954'); // 기본 색상으로 복원
                            }
                            
                            // 새 화살표 표시
                            var arrow = svg.querySelector('#arrow-' + measureNum);
                            if (arrow) {
                                arrow.setAttribute('opacity', '1');
                                arrow.setAttribute('fill', '#1db954'); // 활성 색상
                                log('Arrow shown for measure ' + measureNum);
                            } else {
                                logError('Arrow not found for measure ' + measureNum + '. Available arrows: ' + 
                                    Array.from(svg.querySelectorAll('[id^="arrow-"]')).map(function(a) { return a.id; }).join(', '));
                            }
                            
                            currentMeasure = measureNum;
                        }
                        
                        function scrollToMeasure(measureNum) {
                            var measureY = measurePositions[measureNum];
                            if (measureY !== undefined) {
                                var viewportHeight = window.innerHeight || document.documentElement.clientHeight || 400;
                                var svg = div.querySelector('svg');
                                var svgOffset = 0;
                                if (svg) {
                                    var svgRect = svg.getBoundingClientRect();
                                    var bodyRect = document.body.getBoundingClientRect();
                                    svgOffset = svgRect.top - bodyRect.top;
                                }
                                // 현재 마디가 화면 중앙에 오도록 계산
                                var scrollY = Math.max(0, svgOffset + measureY - viewportHeight / 2);
                                
                                log('Scrolling to measure ' + measureNum + ': measureY=' + measureY + ', scrollY=' + scrollY);
                                
                                // 부드러운 스크롤을 위해 Android 인터페이스 사용
                                if (typeof AndroidInterface !== 'undefined' && AndroidInterface.smoothScrollToPosition) {
                                    AndroidInterface.smoothScrollToPosition(Math.round(scrollY));
                                } else if (typeof AndroidInterface !== 'undefined' && AndroidInterface.scrollToPosition) {
                                    AndroidInterface.scrollToPosition(Math.round(scrollY));
                                }
                                
                                // JavaScript에서도 부드러운 스크롤 시도
                                try {
                                    // smooth scroll 지원
                                    if (window.scrollTo && typeof window.scrollTo === 'function') {
                                        if (window.scrollTo.length > 1) {
                                            // options 객체 지원
                                            window.scrollTo({
                                                top: scrollY,
                                                left: 0,
                                                behavior: 'smooth'
                                            });
                                        } else {
                                            // 즉시 스크롤
                                            window.scrollTo(0, scrollY);
                                        }
                                    }
                                    
                                    // 폴백: 즉시 스크롤
                                    if (document.documentElement) {
                                        try {
                                            document.documentElement.scrollTo({
                                                top: scrollY,
                                                behavior: 'smooth'
                                            });
                                        } catch (e) {
                                            document.documentElement.scrollTop = scrollY;
                                        }
                                    }
                                    if (document.body) {
                                        try {
                                            document.body.scrollTo({
                                                top: scrollY,
                                                behavior: 'smooth'
                                            });
                                        } catch (e) {
                                            document.body.scrollTop = scrollY;
                                        }
                                    }
                                } catch (e) {
                                    // 최종 폴백: 즉시 스크롤
                                    try {
                                        if (window.scrollTo) window.scrollTo(0, scrollY);
                                        if (document.documentElement) document.documentElement.scrollTop = scrollY;
                                        if (document.body) document.body.scrollTop = scrollY;
                                    } catch (e2) {
                                        logError('Error in JavaScript scroll: ' + e2.message);
                                    }
                                }
                            } else {
                                logError('Measure position not found for measure ' + measureNum);
                            }
                        }
                        
                        function updateArrowAndScroll(measureNum) {
                            updateArrow(measureNum);
                            scrollToMeasure(measureNum);
                            // 페이지 자동 넘김 체크
                            checkPageTurn(measureNum);
                        }
                        
                        // 페이지 자동 넘김 체크
                        function checkPageTurn(measureNum) {
                            try {
                                var measureY = measurePositions[measureNum];
                                if (measureY === undefined) return;
                                
                                var viewportHeight = window.innerHeight || document.documentElement.clientHeight || 400;
                                var svg = div.querySelector('svg');
                                if (!svg) return;
                                
                                var svgRect = svg.getBoundingClientRect();
                                var bodyRect = document.body.getBoundingClientRect();
                                var svgOffset = svgRect.top - bodyRect.top;
                                var currentScrollY = window.scrollY || document.documentElement.scrollTop || 0;
                                var measureScreenY = svgOffset + measureY - currentScrollY;
                                
                                // 현재 마디가 화면 하단 20% 영역에 있으면 다음 페이지로 스크롤
                                if (measureScreenY > viewportHeight * 0.8) {
                                    var nextPageY = Math.max(0, svgOffset + measureY - viewportHeight * 0.3);
                                    window.scrollTo({
                                        top: nextPageY,
                                        behavior: 'smooth'
                                    });
                                }
                                // 현재 마디가 화면 상단 20% 영역에 있으면 이전 페이지로 스크롤
                                else if (measureScreenY < viewportHeight * 0.2) {
                                    var prevPageY = Math.max(0, svgOffset + measureY - viewportHeight * 0.7);
                                    window.scrollTo({
                                        top: prevPageY,
                                        behavior: 'smooth'
                                    });
                                }
                            } catch (e) {
                                logError('Error in page turn: ' + e.message);
                            }
                        }
                        
                        // 반복 구간 표시 함수
                        function showRepeatSection(startMeasure, endMeasure) {
                            try {
                                var svg = div.querySelector('svg');
                                if (!svg) return;
                                
                                // 반복 구간 배경 표시
                                var startY = measurePositions[startMeasure];
                                var endY = measurePositions[endMeasure];
                                if (startY !== undefined && endY !== undefined) {
                                    var repeatRect = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
                                    repeatRect.setAttribute('id', 'repeat-section');
                                    repeatRect.setAttribute('x', '0');
                                    repeatRect.setAttribute('y', (startY - 20).toString());
                                    repeatRect.setAttribute('width', '800');
                                    repeatRect.setAttribute('height', (endY - startY + 40).toString());
                                    repeatRect.setAttribute('fill', 'rgba(29, 185, 84, 0.1)');
                                    repeatRect.setAttribute('stroke', '#1db954');
                                    repeatRect.setAttribute('stroke-width', '2');
                                    repeatRect.setAttribute('stroke-dasharray', '5,5');
                                    svg.appendChild(repeatRect);
                                    
                                    // 반복 표시 텍스트
                                    var repeatText = document.createElementNS('http://www.w3.org/2000/svg', 'text');
                                    repeatText.setAttribute('x', '10');
                                    repeatText.setAttribute('y', (startY - 5).toString());
                                    repeatText.setAttribute('fill', '#1db954');
                                    repeatText.setAttribute('font-size', '14');
                                    repeatText.setAttribute('font-weight', 'bold');
                                    repeatText.textContent = 'REPEAT';
                                    svg.appendChild(repeatText);
                                }
                            } catch (e) {
                                logError('Error showing repeat section: ' + e.message);
                            }
                        }
                        
                        // 음표 하이라이트 함수 (개선된 버전)
                        function highlightNote(noteName) {
                            try {
                                // 이전 하이라이트 제거
                                var svg = div.querySelector('svg');
                                if (!svg) return;
                                
                                var prevHighlights = svg.querySelectorAll('.note-highlight');
                                prevHighlights.forEach(function(el) {
                                    el.remove();
                                });
                                
                                // data-note 속성을 가진 모든 요소 찾기
                                var noteElements = svg.querySelectorAll('[data-note]');
                                var found = false;
                                
                                noteElements.forEach(function(noteEl) {
                                    var dataNote = noteEl.getAttribute('data-note');
                                    // 노트 이름 매칭 (예: "c/4" 또는 "c4")
                                    if (dataNote && (dataNote === noteName || dataNote.replace('/', '') === noteName.replace('/', ''))) {
                                        found = true;
                                        try {
                                            var bbox = noteEl.getBBox();
                                            if (bbox) {
                                                var highlight = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
                                                highlight.setAttribute('class', 'note-highlight');
                                                highlight.setAttribute('x', (bbox.x - 3).toString());
                                                highlight.setAttribute('y', (bbox.y - 3).toString());
                                                highlight.setAttribute('width', (bbox.width + 6).toString());
                                                highlight.setAttribute('height', (bbox.height + 6).toString());
                                                highlight.setAttribute('fill', 'rgba(29, 185, 84, 0.4)');
                                                highlight.setAttribute('stroke', '#1db954');
                                                highlight.setAttribute('stroke-width', '3');
                                                highlight.setAttribute('rx', '3');
                                                highlight.setAttribute('style', 'pointer-events: none;');
                                                svg.appendChild(highlight);
                                                log('Highlighted note: ' + noteName);
                                            }
                                        } catch (e) {
                                            // getBBox 실패 시 대체 방법
                                            var rect = noteEl.getBoundingClientRect();
                                            var svgRect = svg.getBoundingClientRect();
                                            if (rect && svgRect) {
                                                var highlight = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
                                                highlight.setAttribute('class', 'note-highlight');
                                                highlight.setAttribute('x', (rect.left - svgRect.left - 3).toString());
                                                highlight.setAttribute('y', (rect.top - svgRect.top - 3).toString());
                                                highlight.setAttribute('width', (rect.width + 6).toString());
                                                highlight.setAttribute('height', (rect.height + 6).toString());
                                                highlight.setAttribute('fill', 'rgba(29, 185, 84, 0.4)');
                                                highlight.setAttribute('stroke', '#1db954');
                                                highlight.setAttribute('stroke-width', '3');
                                                highlight.setAttribute('rx', '3');
                                                highlight.setAttribute('style', 'pointer-events: none;');
                                                svg.appendChild(highlight);
                                            }
                                        }
                                    }
                                });
                                
                                if (!found) {
                                    log('Note not found for highlighting: ' + noteName);
                                }
                            } catch (e) {
                                logError('Error highlighting note: ' + e.message);
                            }
                        }
                        
                        // 여러 음표 동시 하이라이트 (화음용)
                        function highlightNotes(noteNames) {
                            try {
                                // 이전 하이라이트 제거
                                clearNoteHighlights();
                                
                                if (!noteNames || (Array.isArray(noteNames) && noteNames.length === 0)) {
                                    return;
                                }
                                
                                if (!Array.isArray(noteNames)) {
                                    highlightNote(noteNames);
                                    return;
                                }
                                
                                // 각 음표 하이라이트
                                noteNames.forEach(function(noteName) {
                                    highlightNote(noteName);
                                });
                            } catch (e) {
                                logError('Error highlighting multiple notes: ' + e.message);
                            }
                        }
                        
                        // 하이라이트 제거 함수
                        function clearNoteHighlights() {
                            try {
                                var svg = div.querySelector('svg');
                                if (!svg) return;
                                
                                var prevHighlights = svg.querySelectorAll('.note-highlight');
                                prevHighlights.forEach(function(el) {
                                    el.remove();
                                });
                            } catch (e) {
                                logError('Error clearing note highlights: ' + e.message);
                            }
                        }
                        
                        // 마디 강조 표시 함수
                        function highlightMeasure(measureNum, highlight) {
                            var svg = div.querySelector('svg');
                            if (!svg) {
                                logError('SVG not found for highlight');
                                return;
                            }
                            
                            var highlightRect = svg.querySelector('#highlight-' + measureNum);
                            if (highlightRect) {
                                if (highlight) {
                                    highlightRect.setAttribute('display', 'block');
                                    highlightRect.setAttribute('opacity', '0.15');
                                    log('Measure ' + measureNum + ' highlighted');
                                } else {
                                    highlightRect.setAttribute('display', 'none');
                                    log('Measure ' + measureNum + ' highlight removed');
                                }
                            } else {
                                logError('Highlight rect not found for measure ' + measureNum);
                            }
                        }
                        
                        window.updateArrow = updateArrow;
                        window.updateArrowAndScroll = updateArrowAndScroll;
                        window.scrollToMeasure = scrollToMeasure;
                        window.highlightMeasure = highlightMeasure;
                        
                        // 렌더링 완료 알림 및 초기 스크롤 위치 설정
                        setTimeout(function() {
                            var svg = div.querySelector('svg');
                            if (svg && svg.parentElement) {
                                // SVG의 실제 높이 확인
                                var svgHeight = svg.getBoundingClientRect().height || $totalHeight;
                                var actualHeight = Math.max(svgHeight, $totalHeight);
                                
                                // SVG 높이를 실제 콘텐츠 높이로 확실히 설정
                                svg.setAttribute('height', actualHeight);
                                svg.style.height = actualHeight + 'px';
                                
                                // body와 html 높이도 설정하여 무제한 스크롤 보장
                                document.body.style.minHeight = actualHeight + 'px';
                                document.documentElement.style.minHeight = actualHeight + 'px';
                                
                                log('SVG height set to: ' + actualHeight + 'px for unlimited scrolling');
                                
                                if (typeof AndroidInterface !== 'undefined' && AndroidInterface.updateWebViewHeight) {
                                    AndroidInterface.updateWebViewHeight(Math.ceil(actualHeight));
                                }
                            }
                            
                            // 초기 스크롤을 맨 위로 이동
                            log('Scrolling to top (measure 1)');
                            if (typeof AndroidInterface !== 'undefined' && AndroidInterface.scrollToPosition) {
                                AndroidInterface.scrollToPosition(0);
                            }
                            try {
                                if (window.scrollTo) window.scrollTo(0, 0);
                                if (document.documentElement) document.documentElement.scrollTop = 0;
                                if (document.body) document.body.scrollTop = 0;
                            } catch (e) {
                                logError('Error scrolling to top: ' + e.message);
                            }
                            
                            // 초기 화살표를 1번 마디로 설정
                            updateArrow(1);
                            
                            if (typeof AndroidInterface !== 'undefined' && AndroidInterface.onRenderComplete) {
                                AndroidInterface.onRenderComplete();
                            }
                        }, 200);
                    } catch (e) {
                        logError('Rendering error: ' + e.message);
                        logError('Stack: ' + e.stack);
                    }
                }
                
                // DOM 로드 후 VexFlow 초기화 및 렌더링 시작
                if (document.readyState === 'loading') {
                    document.addEventListener('DOMContentLoaded', function() {
                        log('DOM loaded, initializing VexFlow...');
                        startRendering();
                    });
                } else {
                    log('DOM already loaded, initializing VexFlow...');
                    startRendering();
                }
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    // TODO: MusicXML 기반 VexFlow HTML 생성 함수로 대체 예정
    // 기존 MIDI 이벤트 기반 HTML 생성 함수는 삭제됨

    // TODO: MusicXML 기반 VexFlow HTML 생성 함수로 대체 예정
    // 기존 MIDI 이벤트 기반 HTML 생성 함수는 삭제됨
    /*
    private fun generateVexFlowHTMLWithThreeStaves(
        currentNotesJson: String,
        currentTotalBeats: Double,
        nextNotesJson: String,
        nextTotalBeats: Double,
        thirdNotesJson: String,
        thirdTotalBeats: Double,
        numerator: Int,
        denominator: Int,
        keySignature: String = "C"
    ): String {
        val currentNumBeats = currentTotalBeats.coerceAtLeast(numerator.toDouble()).toInt()
        val nextNumBeats = if (nextTotalBeats > 0) {
            nextTotalBeats.coerceAtLeast(numerator.toDouble()).toInt()
        } else {
            0
        }
        val thirdNumBeats = if (thirdTotalBeats > 0) {
            thirdTotalBeats.coerceAtLeast(numerator.toDouble()).toInt()
        } else {
            0
        }

        return """
        <!DOCTYPE html>
        <html>
        <head>
            <style>
                body { margin: 0; padding: 0; background: white; overflow: visible; }
                #sheet { width: 100%; height: 100%; overflow: visible; }
                svg { display: block; }
                #nextMeasure { opacity: 0.5; }
                #thirdMeasure { opacity: 0.5; }
            </style>
        </head>
        <body>
            <div id="sheet"></div>
            <script>
                // 로그 함수
                function log(msg) {
                    if (typeof AndroidInterface !== 'undefined' && AndroidInterface.log) {
                        AndroidInterface.log(msg);
                    } else {
                        console.log(msg);
                    }
                }
                
                function logError(msg) {
                    if (typeof AndroidInterface !== 'undefined' && AndroidInterface.logError) {
                        AndroidInterface.logError(msg);
                    } else {
                        console.error(msg);
                    }
                }
                
                function startRendering() {
                    var div = document.getElementById("sheet");
                    if (!div) {
                        logError('Sheet div not found');
                        return;
                    }
                    
                    // VexFlow 초기화 및 렌더링
                    loadVexFlow().then(function(VF) {
                        log('VexFlow initialized successfully');
                        renderSheetMusic(VF, div);
                    }).catch(function(error) {
                        logError('Failed to initialize VexFlow: ' + error.message);
                        if (typeof AndroidInterface !== 'undefined' && AndroidInterface.onRenderComplete) {
                            AndroidInterface.onRenderComplete();
                        }
                    });
                }
                
                function renderSheetMusic(VF, div) {
                    // VF를 전역 변수로 설정하여 processNote 등에서 접근 가능하도록 함
                    window.VF = VF;
                    
                    try {
                        log('Starting VexFlow rendering with three staves...');
                        
                        var renderer = new VF.Renderer(div, VF.Renderer.Backends.SVG);
                        renderer.resize(800, 750); // 높이 증가 (세 마디용)
                        var context = renderer.getContext();
                        
                        // 현재 마디 Stave (위쪽)
                        var stave1 = new VF.Stave(10, 50, 750);
                        stave1.addClef("treble");
                        stave1.addKeySignature("$keySignature");
                        stave1.addTimeSignature("$numerator/$denominator");
                        stave1.setContext(context).draw();
                        log('Current measure stave drawn at (10, 50)');
                        
                        var currentNotes = ${if (currentNotesJson.isBlank()) "[]" else "[$currentNotesJson]"}
                        log('Current measure notes: ' + currentNotes.length);
                        
                        if (currentNotes.length > 0) {
                            // 현재 마디 노트 렌더링
                            var numerator = $numerator;
                            var denominator = $denominator;
                            
                            var beatAccumulator = 0;
                            // VexFlow duration을 beats로 변환 (4/4 time 기준)
                            var durationFractionMap = {
                                'w': 4, 'wr': 4, 'h': 2, 'hr': 2,
                                'q': 1, 'qr': 1, '8': 0.5, '8r': 0.5,
                                '16': 0.25, '16r': 0.25, '32': 0.125, '32r': 0.125
                            };
                            
                            var beamableNotes = [];
                            currentNotes.forEach(function(note) {
                                try {
                                    var duration = note.getDuration();
                                    var fraction = durationFractionMap[duration] || 1/4;
                                    beatAccumulator += fraction;
                                    if (duration === '8' || duration === '16' || duration === '32') {
                                        beamableNotes.push(note);
                                    }
                                } catch (e) {
                                    logError('Error getting duration: ' + e.message);
                                    beatAccumulator += 1/4;
                                }
                            });
                            
                            var beatDuration = 4;
                            var totalBeats = beatAccumulator / (1 / beatDuration);
                            var voiceBeats = Math.max(totalBeats, numerator);
                            
                            var voice1 = new VF.Voice({ num_beats: voiceBeats, beat_value: beatDuration });
                            voice1.addTickables(currentNotes);
                            
                            try {
                                var formatter1 = new VF.Formatter().joinVoices([voice1]).format([voice1], 600);
                                voice1.draw(context, stave1);
                                
                                // 베벨 추가
                                if (beamableNotes.length > 1) {
                                    var beamGroups = [];
                                    var currentGroup = [];
                                    for (var i = 0; i < beamableNotes.length; i++) {
                                        var note = beamableNotes[i];
                                        var duration = note.getDuration();
                                        if (currentGroup.length === 0 || currentGroup[0].getDuration() === duration) {
                                            currentGroup.push(note);
                                        } else {
                                            if (currentGroup.length > 1) beamGroups.push(currentGroup);
                                            currentGroup = [note];
                                        }
                                    }
                                    if (currentGroup.length > 1) beamGroups.push(currentGroup);
                                    beamGroups.forEach(function(group) {
                                        try {
                                            var beam = new VF.Beam(group);
                                            beam.setContext(context);
                                            beam.draw();
                                        } catch (e) {
                                            logError('Error adding beam: ' + e.message);
                                        }
                                    });
                                }
                                
                                // 다이나믹 마크, 타이, 아티큘레이션 등 추가 (기존 로직)
                                // ... (기존 코드와 동일)
                                
                            } catch (e) {
                                logError('Error rendering current measure: ' + e.message);
                            }
                        }
                        
                        // 다음 마디 Stave (중간, 회색)
                        var nextNotes = ${if (nextNotesJson.isBlank()) "[]" else "[$nextNotesJson]"}
                        log('Next measure notes: ' + nextNotes.length);
                        
                        if (nextNotes.length > 0) {
                            // SVG 그룹 시작 (회색 적용용)
                            context.openGroup("nextMeasure", { id: "nextMeasure" });
                            
                            var stave2 = new VF.Stave(10, 250, 750); // y 좌표를 중간으로 이동
                            stave2.addClef("treble");
                            stave2.addKeySignature("$keySignature");
                            stave2.addTimeSignature("$numerator/$denominator");
                            stave2.setContext(context);
                            
                            // 회색으로 그리기
                            context.setFillStyle("#999999");
                            context.setStrokeStyle("#999999");
                            stave2.draw();
                            
                            // 다음 마디 노트 렌더링
                            var beatAccumulator2 = 0;
                            var beamableNotes2 = [];
                            nextNotes.forEach(function(note) {
                                try {
                                    var duration = note.getDuration();
                                    var fraction = durationFractionMap[duration] || 1/4;
                                    beatAccumulator2 += fraction;
                                    if (duration === '8' || duration === '16' || duration === '32') {
                                        beamableNotes2.push(note);
                                    }
                                } catch (e) {
                                    beatAccumulator2 += 1/4;
                                }
                            });
                            
                            var totalBeats2 = beatAccumulator2 / (1 / beatDuration);
                            var voiceBeats2 = Math.max(totalBeats2, numerator);
                            
                            var voice2 = new VF.Voice({ num_beats: voiceBeats2, beat_value: beatDuration });
                            voice2.addTickables(nextNotes);
                            
                            try {
                                var formatter2 = new VF.Formatter().joinVoices([voice2]).format([voice2], 600);
                                voice2.draw(context, stave2);
                                
                                // 베벨 추가
                                if (beamableNotes2.length > 1) {
                                    var beamGroups2 = [];
                                    var currentGroup2 = [];
                                    for (var i = 0; i < beamableNotes2.length; i++) {
                                        var note = beamableNotes2[i];
                                        var duration = note.getDuration();
                                        if (currentGroup2.length === 0 || currentGroup2[0].getDuration() === duration) {
                                            currentGroup2.push(note);
                                        } else {
                                            if (currentGroup2.length > 1) beamGroups2.push(currentGroup2);
                                            currentGroup2 = [note];
                                        }
                                    }
                                    if (currentGroup2.length > 1) beamGroups2.push(currentGroup2);
                                    beamGroups2.forEach(function(group) {
                                        try {
                                            var beam = new VF.Beam(group);
                                            beam.setContext(context);
                                            beam.draw();
                                        } catch (e) {
                                            logError('Error adding beam: ' + e.message);
                                        }
                                    });
                                }
                            } catch (e) {
                                logError('Error rendering next measure: ' + e.message);
                            }
                            
                            context.closeGroup();
                        }
                        
                        // 세 번째 마디 Stave (아래쪽, 회색)
                        var thirdNotes = ${if (thirdNotesJson.isBlank()) "[]" else "[$thirdNotesJson]"}
                        log('Third measure notes: ' + thirdNotes.length);
                        
                        if (thirdNotes.length > 0) {
                            // SVG 그룹 시작 (회색 적용용)
                            context.openGroup("thirdMeasure", { id: "thirdMeasure" });
                            
                            var stave3 = new VF.Stave(10, 450, 750); // y 좌표를 아래로 이동
                            stave3.addClef("treble");
                            stave3.addKeySignature("$keySignature");
                            stave3.addTimeSignature("$numerator/$denominator");
                            stave3.setContext(context);
                            
                            // 회색으로 그리기
                            context.setFillStyle("#999999");
                            context.setStrokeStyle("#999999");
                            stave3.draw();
                            
                            // 세 번째 마디 노트 렌더링
                            var beatAccumulator3 = 0;
                            var beamableNotes3 = [];
                            thirdNotes.forEach(function(note) {
                                try {
                                    var duration = note.getDuration();
                                    var fraction = durationFractionMap[duration] || 1/4;
                                    beatAccumulator3 += fraction;
                                    if (duration === '8' || duration === '16' || duration === '32') {
                                        beamableNotes3.push(note);
                                    }
                                } catch (e) {
                                    beatAccumulator3 += 1/4;
                                }
                            });
                            
                            var totalBeats3 = beatAccumulator3 / (1 / beatDuration);
                            var voiceBeats3 = Math.max(totalBeats3, numerator);
                            
                            var voice3 = new VF.Voice({ num_beats: voiceBeats3, beat_value: beatDuration });
                            voice3.addTickables(thirdNotes);
                            
                            try {
                                var formatter3 = new VF.Formatter().joinVoices([voice3]).format([voice3], 600);
                                voice3.draw(context, stave3);
                                
                                // 베벨 추가
                                if (beamableNotes3.length > 1) {
                                    var beamGroups3 = [];
                                    var currentGroup3 = [];
                                    for (var i = 0; i < beamableNotes3.length; i++) {
                                        var note = beamableNotes3[i];
                                        var duration = note.getDuration();
                                        if (currentGroup3.length === 0 || currentGroup3[0].getDuration() === duration) {
                                            currentGroup3.push(note);
                                        } else {
                                            if (currentGroup3.length > 1) beamGroups3.push(currentGroup3);
                                            currentGroup3 = [note];
                                        }
                                    }
                                    if (currentGroup3.length > 1) beamGroups3.push(currentGroup3);
                                    beamGroups3.forEach(function(group) {
                                        try {
                                            var beam = new VF.Beam(group);
                                            beam.setContext(context);
                                            beam.draw();
                                        } catch (e) {
                                            logError('Error adding beam: ' + e.message);
                                        }
                                    });
                                }
                            } catch (e) {
                                logError('Error rendering third measure: ' + e.message);
                            }
                            
                            context.closeGroup();
                        }
                        
                        // SVG 요소에 opacity 적용
                        setTimeout(function() {
                            var svg = div.querySelector('svg');
                            if (svg) {
                                var nextMeasureGroup = svg.querySelector('#nextMeasure');
                                if (nextMeasureGroup) {
                                    nextMeasureGroup.setAttribute('opacity', '0.5');
                                    var elements = nextMeasureGroup.querySelectorAll('path, circle, ellipse, line, text');
                                    elements.forEach(function(el) {
                                        el.setAttribute('stroke', '#999999');
                                        el.setAttribute('fill', '#999999');
                                    });
                                }
                                var thirdMeasureGroup = svg.querySelector('#thirdMeasure');
                                if (thirdMeasureGroup) {
                                    thirdMeasureGroup.setAttribute('opacity', '0.5');
                                    var elements = thirdMeasureGroup.querySelectorAll('path, circle, ellipse, line, text');
                                    elements.forEach(function(el) {
                                        el.setAttribute('stroke', '#999999');
                                        el.setAttribute('fill', '#999999');
                                    });
                                }
                            }
                            AndroidInterface.onRenderComplete();
                        }, 100);
                        
                    } catch (e) {
                        logError('Rendering error: ' + e.message);
                        logError('Stack: ' + e.stack);
                        if (typeof AndroidInterface !== 'undefined' && AndroidInterface.onRenderComplete) {
                            AndroidInterface.onRenderComplete();
                        }
                    }
                }
                
                // DOM 로드 후 VexFlow 초기화 및 렌더링 시작
                if (document.readyState === 'loading') {
                    document.addEventListener('DOMContentLoaded', function() {
                        log('DOM loaded, initializing VexFlow...');
                        startRendering();
                    });
                } else {
                    log('DOM already loaded, initializing VexFlow...');
                    startRendering();
                }
            </script>
        </body>
        </html>
        """
    }
    */

    // TODO: MusicXML 기반 VexFlow HTML 생성 함수로 대체 예정
    // 기존 MIDI 이벤트 기반 HTML 생성 함수는 삭제됨
    /*
    private fun generateVexFlowHTMLWithBeats(
        notesJson: String,
        totalBeats: Double,
        numerator: Int,
        denominator: Int,
        keySignature: String = "C"
    ): String {
        // 박자 수를 정수로 올림 (최소 마디의 박자 수)
        val numBeats = totalBeats.coerceAtLeast(numerator.toDouble()).toInt()

        return """
        <!DOCTYPE html>
        <html>
        <head>
            <style>
                body { margin: 0; padding: 0; background: white; overflow: visible; }
                #sheet { width: 100%; height: 100%; overflow: visible; }
                svg { display: block; }
            </style>
            <script>
                // VexFlow 로드 함수 (로컬 우선, 실패 시 CDN)
                function loadVexFlow() {
                    return new Promise(function(resolve, reject) {
                        // 이미 로드되어 있는지 확인
                        if (typeof Vex !== 'undefined' && typeof Vex.Flow !== 'undefined') {
                            resolve(Vex.Flow);
                            return;
                        }
                        
                        // 로컬 assets에서 먼저 시도
                        var script = document.createElement('script');
                        script.src = 'vexflow/vexflow-min.js';
                        script.onload = function() {
                            if (typeof Vex !== 'undefined' && typeof Vex.Flow !== 'undefined') {
                                log('VexFlow loaded from local assets');
                                resolve(Vex.Flow);
                            } else {
                                // 로컬 로드 실패 시 CDN 사용
                                loadVexFlowFromCDN().then(resolve).catch(reject);
                            }
                        };
                        script.onerror = function() {
                            // 로컬 로드 실패 시 CDN 사용
                            log('Local VexFlow load failed, trying CDN...');
                            loadVexFlowFromCDN().then(resolve).catch(reject);
                        };
                        document.head.appendChild(script);
                    });
                }
                
                function loadVexFlowFromCDN() {
                    return new Promise(function(resolve, reject) {
                        var script = document.createElement('script');
                        script.src = 'https://cdn.jsdelivr.net/npm/vexflow@4.2.5/releases/vexflow-min.js';
                        script.onload = function() {
                            if (typeof Vex !== 'undefined' && typeof Vex.Flow !== 'undefined') {
                                log('VexFlow loaded from CDN');
                                resolve(Vex.Flow);
                            } else {
                                reject(new Error('VexFlow not available after CDN load'));
                            }
                        };
                        script.onerror = function() {
                            reject(new Error('Failed to load VexFlow from CDN'));
                        };
                        document.head.appendChild(script);
                    });
                }
            </script>
        </head>
        <body>
            <div id="sheet"></div>
            <script>
                // 로그 함수
                function log(msg) {
                    if (typeof AndroidInterface !== 'undefined' && AndroidInterface.log) {
                        AndroidInterface.log(msg);
                    } else {
                        console.log(msg);
                    }
                }
                
                function logError(msg) {
                    if (typeof AndroidInterface !== 'undefined' && AndroidInterface.logError) {
                        AndroidInterface.logError(msg);
                    } else {
                        console.error(msg);
                    }
                }
                
                function startRendering() {
                    var div = document.getElementById("sheet");
                    if (!div) {
                        logError('Sheet div not found');
                        return;
                    }
                    
                    // VexFlow 초기화 및 렌더링
                    loadVexFlow().then(function(VF) {
                        log('VexFlow initialized successfully');
                        renderSheetMusic(VF, div);
                    }).catch(function(error) {
                        logError('Failed to initialize VexFlow: ' + error.message);
                        if (typeof AndroidInterface !== 'undefined' && AndroidInterface.onRenderComplete) {
                            AndroidInterface.onRenderComplete();
                        }
                    });
                }
                
                function renderSheetMusic(VF, div) {
                    // VF를 전역 변수로 설정하여 processNote 등에서 접근 가능하도록 함
                    window.VF = VF;
                    
                    try {
                        log('Starting VexFlow rendering...');
                        
                        var renderer = new VF.Renderer(div, VF.Renderer.Backends.SVG);
                        renderer.resize(800, 300);
                        var context = renderer.getContext();
                        
                        // Stave 위치 조정 (더 넓은 공간 확보)
                        var stave = new VF.Stave(10, 50, 750);
                        stave.addClef("treble");
                        stave.addKeySignature("$keySignature");
                        stave.addTimeSignature("$numerator/$denominator");
                        stave.setContext(context).draw();
                        log('Stave drawn with key signature: $keySignature, time signature: $numerator/$denominator at (10, 50)');
                        
                        var notes = ${if (notesJson.isBlank()) "[]" else "[$notesJson]"}
                        
                        log('Total notes to render: ' + notes.length);
                        
                        if (notes.length > 0) {
                            // 시간 서명 정보
                            var numerator = $numerator;
                            var denominator = $denominator;
                            
                            // 첫 번째 노트 확인
                            try {
                                var firstNote = notes[0];
                                log('First note created: ' + (firstNote ? 'yes' : 'no'));
                                if (firstNote && firstNote.getKeys) {
                                    var keys = firstNote.getKeys();
                                    log('First note keys: ' + JSON.stringify(keys));
                                }
                            } catch (e) {
                                logError('Error checking first note: ' + e.message);
                            }
                            
                            // 각 노트의 duration을 분수로 변환하여 계산
                            // VexFlow는 beat_value (denominator) 기준으로 계산해야 함
                            var beatAccumulator = 0;
                            var durationFractionMap = {
                                'w': 1,      // whole note = 1
                                'wr': 1,     // whole rest = 1
                                'h': 1/2,    // half note = 1/2
                                'hr': 1/2,   // half rest = 1/2
                                'q': 1/4,    // quarter note = 1/4
                                'qr': 1/4,   // quarter rest = 1/4
                                '8': 1/8,    // eighth note = 1/8
                                '8r': 1/8,   // eighth rest = 1/8
                                '16': 1/16,  // sixteenth note = 1/16
                                '16r': 1/16, // sixteenth rest = 1/16
                                '32': 1/32,  // thirty-second note = 1/32
                                '32r': 1/32  // thirty-second rest = 1/32
                            };
                            
                            // 베벨을 위한 노트 그룹 (8분음표, 16분음표 등)
                            var beamableNotes = [];
                            
                            notes.forEach(function(note, index) {
                                try {
                                    var duration = note.getDuration();
                                    var fraction = durationFractionMap[duration] || 1/4; // 기본값: quarter note
                                    beatAccumulator += fraction;
                                    
                                    // 베벨 가능한 노트 수집 (8분음표, 16분음표 등)
                                    if (duration === '8' || duration === '16' || duration === '32') {
                                        beamableNotes.push(note);
                                    }
                                } catch (e) {
                                    logError('Error getting duration: ' + e.message);
                                    beatAccumulator += 1/4; // 기본값: quarter note
                                }
                            });
                            
                            // beat_value (denominator) 기준으로 totalBeats 계산
                            // 링크의 코드: totalBeats = beatAccumulator / (1 / beatDuration) = beatAccumulator * beatDuration
                            // beatDuration은 4 (quarter note 기준)로 고정
                            var beatDuration = 4; // quarter note 기준으로 고정 (링크의 코드와 동일)
                            var totalBeats = beatAccumulator / (1 / beatDuration);
                            
                            // Voice의 박자 수를 정확히 설정
                            // 링크의 코드에서는 totalBeats를 그대로 사용
                            // VexFlow는 노트들의 duration 합이 정확히 num_beats와 일치해야 함
                            // totalBeats를 그대로 사용 (링크의 코드와 동일)
                            var voiceBeats = totalBeats;
                            
                            // 최소값은 시간 서명의 numerator
                            voiceBeats = Math.max(voiceBeats, numerator);
                            
                            log('Notes count: ' + notes.length + ', Beat accumulator: ' + beatAccumulator + ', Total beats: ' + totalBeats + ', Voice beats: ' + voiceBeats + ', time signature: ' + numerator + '/' + denominator);
                            
                            // beat_value는 4로 고정 (링크의 코드와 동일)
                            // totalBeats를 그대로 사용 (링크의 코드와 동일)
                            var voice = new VF.Voice({ num_beats: voiceBeats, beat_value: beatDuration });
                            voice.addTickables(notes);
                            
                            log('Voice created with ' + notes.length + ' notes');
                            
                            // Formatter 설정: 마디 너비에 맞게 조정
                            var formatterSuccess = false;
                            try {
                                var formatter = new VF.Formatter().joinVoices([voice]).format([voice], 600);
                                log('Formatter applied with width: 600');
                                formatterSuccess = true;
                            } catch (e) {
                                logError('Formatter error: ' + e.message);
                                logError('Stack: ' + e.stack);
                                // Formatter 실패 시에도 계속 진행
                            }
                            
                            // 노트 그리기
                            log('Drawing ' + notes.length + ' notes on stave');
                            try {
                                if (formatterSuccess) {
                                    voice.draw(context, stave);
                                    log('Notes drawn successfully');
                                    
                                    // 베벨 추가 (8분음표, 16분음표 등)
                                    if (beamableNotes.length > 1) {
                                        try {
                                            // 같은 duration의 연속된 노트들을 그룹화
                                            var beamGroups = [];
                                            var currentGroup = [];
                                            
                                            for (var i = 0; i < beamableNotes.length; i++) {
                                                var note = beamableNotes[i];
                                                var duration = note.getDuration();
                                                
                                                if (currentGroup.length === 0 || 
                                                    currentGroup[0].getDuration() === duration) {
                                                    currentGroup.push(note);
                                                } else {
                                                    if (currentGroup.length > 1) {
                                                        beamGroups.push(currentGroup);
                                                    }
                                                    currentGroup = [note];
                                                }
                                            }
                                            
                                            if (currentGroup.length > 1) {
                                                beamGroups.push(currentGroup);
                                            }
                                            
                                            // 각 그룹에 베벨 추가
                                            beamGroups.forEach(function(group) {
                                                try {
                                                    var beam = new VF.Beam(group);
                                                    beam.setContext(context);
                                                    beam.draw();
                                                    log('Added beam for ' + group.length + ' notes');
                                                } catch (e) {
                                                    logError('Error adding beam: ' + e.message);
                                                }
                                            });
                                        } catch (e) {
                                            logError('Error processing beams: ' + e.message);
                                        }
                                    }
                                    
                                    // 타이 추가 (needsTie가 true인 노트들)
                                    var tiePairs = [];
                                    for (var i = 1; i < notes.length; i++) {
                                        try {
                                            var currentNote = notes[i];
                                            var previousNote = notes[i - 1];
                                            
                                            if (currentNote.needsTie === true) {
                                                // 같은 음표인지 확인
                                                var currentKeys = currentNote.getKeys();
                                                var previousKeys = previousNote.getKeys();
                                                
                                                if (currentKeys.length > 0 && previousKeys.length > 0 &&
                                                    currentKeys[0] === previousKeys[0]) {
                                                    tiePairs.push([previousNote, currentNote]);
                                                    log('Added tie pair for note: ' + currentKeys[0]);
                                                }
                                            }
                                        } catch (e) {
                                            logError('Error processing tie: ' + e.message);
                                        }
                                    }
                                    
                                    // 타이 그리기
                                    tiePairs.forEach(function(pair) {
                                        try {
                                            var tie = new VF.StaveTie({
                                                first_note: pair[0],
                                                last_note: pair[1],
                                                first_indices: [0],
                                                last_indices: [0]
                                            });
                                            tie.setContext(context);
                                            tie.draw();
                                            log('Drew tie between notes');
                                        } catch (e) {
                                            logError('Error drawing tie: ' + e.message);
                                        }
                                    });
                                    
                                    // 아티큘레이션 추가
                                    notes.forEach(function(note, index) {
                                        try {
                                            if (note.articulation !== undefined && note.articulation !== 'normal') {
                                                try {
                                                    var articulationType = note.articulation;
                                                    if (articulationType === 'staccato') {
                                                        // 스타카토: 점 추가
                                                        var dot = new VF.Articulation('a.');
                                                        dot.setPosition(3); // 노트 위
                                                        note.addArticulation(0, dot);
                                                        log('Added staccato articulation');
                                                    } else if (articulationType === 'legato') {
                                                        // 레가토: 슬러로 표시 (나중에 슬러 섹션에서 처리)
                                                        log('Legato articulation detected (will be handled by slur)');
                                                    }
                                                } catch (e) {
                                                    logError('Error adding articulation: ' + e.message);
                                                }
                                            }
                                        } catch (e) {
                                            logError('Error processing note for articulation: ' + e.message);
                                        }
                                    });
                                    
                                    // 슬러 추가 (레가토 패턴 또는 연속된 노트)
                                    var slurGroups = [];
                                    var currentSlurGroup = [];
                                    
                                    for (var i = 0; i < notes.length; i++) {
                                        var note = notes[i];
                                        var isLegato = note.articulation === 'legato';
                                        var isShortGap = false;
                                        
                                        // 이전 노트와의 간격 확인
                                        if (i > 0) {
                                            try {
                                                var prevNote = notes[i - 1];
                                                var prevKeys = prevNote.getKeys();
                                                var currentKeys = note.getKeys();
                                                
                                                // 같은 음표가 아니고, 간격이 짧으면 슬러 그룹에 추가
                                                if (prevKeys.length > 0 && currentKeys.length > 0 &&
                                                    prevKeys[0] !== currentKeys[0]) {
                                                    isShortGap = true;
                                                }
                                            } catch (e) {
                                                logError('Error checking note gap: ' + e.message);
                                            }
                                        }
                                        
                                        if (isLegato || isShortGap) {
                                            if (currentSlurGroup.length === 0 && i > 0) {
                                                currentSlurGroup.push(notes[i - 1]);
                                            }
                                            currentSlurGroup.push(note);
                                        } else {
                                            if (currentSlurGroup.length > 1) {
                                                slurGroups.push(currentSlurGroup);
                                            }
                                            currentSlurGroup = [];
                                        }
                                    }
                                    
                                    if (currentSlurGroup.length > 1) {
                                        slurGroups.push(currentSlurGroup);
                                    }
                                    
                                    // 슬러 그리기
                                    slurGroups.forEach(function(group) {
                                        try {
                                            if (group.length >= 2) {
                                                var slur = new VF.Curve({
                                                    from: group[0],
                                                    to: group[group.length - 1],
                                                    cps: [{x: 0, y: 10}, {x: 0, y: 10}]
                                                });
                                                slur.setContext(context);
                                                slur.draw();
                                                log('Added slur for ' + group.length + ' notes');
                                            }
                                        } catch (e) {
                                            logError('Error drawing slur: ' + e.message);
                                        }
                                    });
                                    
                                    // 다이나믹 마크 추가 (velocity 기반)
                                    notes.forEach(function(note, index) {
                                        try {
                                            // velocity 정보가 있으면 다이나믹 마크 추가
                                            if (note.velocity !== undefined) {
                                                var velocity = note.velocity;
                                                var dynamicMark = '';
                                                if (velocity < 40) {
                                                    dynamicMark = 'pp'; // pianissimo
                                                } else if (velocity < 60) {
                                                    dynamicMark = 'p'; // piano
                                                } else if (velocity < 80) {
                                                    dynamicMark = 'mp'; // mezzo-piano
                                                } else if (velocity < 100) {
                                                    dynamicMark = 'mf'; // mezzo-forte
                                                } else if (velocity < 120) {
                                                    dynamicMark = 'f'; // forte
                                                } else {
                                                    dynamicMark = 'ff'; // fortissimo
                                                }
                                                
                                                // Annotation을 사용하여 다이나믹 마크 추가
                                                try {
                                                    var annotation = new VF.Annotation(dynamicMark);
                                                    annotation.setVerticalJustification(VF.Annotation.VerticalJustify.BOTTOM);
                                                    note.addAnnotation(annotation);
                                                    log('Added dynamic mark: ' + dynamicMark + ' at velocity: ' + velocity);
                                                } catch (e) {
                                                    logError('Error adding dynamic mark: ' + e.message);
                                                }
                                            }
                                        } catch (e) {
                                            logError('Error processing note for dynamics: ' + e.message);
                                        }
                                    });
                                    
                                    // 다이나믹 마크가 추가된 노트 다시 그리기
                                    notes.forEach(function(note) {
                                        try {
                                            note.setContext(context);
                                            note.setStave(stave);
                                            note.draw();
                                        } catch (e) {
                                            logError('Error redrawing note with annotation: ' + e.message);
                                        }
                                    });
                                } else {
                                    logError('Cannot draw without formatter - skipping');
                                }
                            } catch (e) {
                                logError('Draw error: ' + e.message);
                                logError('Stack: ' + e.stack);
                            }
                            
                            // 실제로 그려진 노트 확인
                            setTimeout(function() {
                                var svgElement = div.querySelector('svg');
                                if (svgElement) {
                                    var noteHeads = svgElement.querySelectorAll('circle, ellipse');
                                    log('Found ' + noteHeads.length + ' note heads in SVG');
                                    
                                    // 모든 path 요소 확인
                                    var paths = svgElement.querySelectorAll('path');
                                    log('Found ' + paths.length + ' path elements in SVG');
                                    
                                    // 노트 스템 확인
                                    var stems = svgElement.querySelectorAll('line[stroke]');
                                    log('Found ' + stems.length + ' stems in SVG');
                                }
                            }, 200);
                        } else {
                            logError('No notes to render!');
                        }
                    } catch (e) {
                        logError('Rendering error: ' + e.message);
                        logError('Stack: ' + e.stack);
                    }
                    
                    // SVG 내용 확인 (디버깅용)
                    setTimeout(function() {
                        var svgElement = div.querySelector('svg');
                        if (svgElement) {
                            log('SVG element found, width: ' + svgElement.width.baseVal.value + ', height: ' + svgElement.height.baseVal.value);
                            var notesInSvg = svgElement.querySelectorAll('path, circle, rect, ellipse');
                            log('Found ' + notesInSvg.length + ' drawing elements in SVG');
                            
                            // 노트 관련 요소만 찾기
                            var noteElements = svgElement.querySelectorAll('g[class*="note"], path[class*="note"], circle[class*="note"]');
                            log('Found ' + noteElements.length + ' note-related elements');
                        } else {
                            logError('SVG element not found!');
                        }
                    }, 300);
                    
                    // 렌더링 완료 알림
                    setTimeout(function() {
                        if (typeof AndroidInterface !== 'undefined' && AndroidInterface.onRenderComplete) {
                            AndroidInterface.onRenderComplete();
                            log('Render complete callback sent');
                        } else {
                            logError('AndroidInterface not found');
                        }
                    }, 500);
                    } catch (e) {
                        logError('Rendering error: ' + e.message);
                        logError('Stack: ' + e.stack);
                        if (typeof AndroidInterface !== 'undefined' && AndroidInterface.onRenderComplete) {
                            AndroidInterface.onRenderComplete();
                        }
                    }
                }
                
                // DOM 로드 후 VexFlow 초기화 및 렌더링 시작
                if (document.readyState === 'loading') {
                    document.addEventListener('DOMContentLoaded', function() {
                        log('DOM loaded, initializing VexFlow...');
                        startRendering();
                    });
                } else {
                    log('DOM already loaded, initializing VexFlow...');
                    startRendering();
                }
            </script>
        </body>
        </html>
        """.trimIndent()
    }
    */

    // TODO: MusicXML 파싱 후 VexFlow 노트 이름 변환 함수로 대체 예정
    // 기존 MIDI 노트 기반 변환 함수는 삭제됨

    /** WebView를 Bitmap으로 변환 */
    private fun webViewToBitmap(webView: WebView): Bitmap {
        // WebView의 실제 크기 사용
        val width = webView.width.coerceAtLeast(800)
        val height = webView.height.coerceAtLeast(750) // 세 마디용 높이

        Log.d("SheetMusic", "Creating bitmap: ${width}x${height}")

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 배경을 흰색으로
        canvas.drawColor(android.graphics.Color.WHITE)

        // WebView 그리기
        webView.draw(canvas)

        Log.d("SheetMusic", "Bitmap created successfully")
        return bitmap
    }


    /** 특정 마디의 이벤트 가져오기 */
    private fun getEventsByMeasure(measure: Int): List<MidiEvent> {
        val startTick = measureToTick(measure)
        val endTick = measureToTick(measure + 1)

        Log.d(
            "SheetMusic",
            "Getting events for measure $measure: tick range [$startTick, $endTick)"
        )
        Log.d("SheetMusic", "Total events in allMidiEvents: ${allMidiEvents.size}")

        val filtered = allMidiEvents.filter { event ->
            event.tick >= startTick && event.tick < endTick
        }.sortedBy { it.tick }

        Log.d("SheetMusic", "Filtered events count: ${filtered.size}")
        filtered.forEach { event ->
            Log.d(
                "SheetMusic",
                "  Event: tick=${event.tick}, note=${event.note}, isNoteOn=${event.isNoteOn}"
            )
        }

        return filtered
    }

    /** 현재 마디와 다음 2마디의 이벤트 가져오기 (총 3마디) */
    private fun getEventsForThreeMeasures(currentMeasure: Int): Triple<List<MidiEvent>, List<MidiEvent>, List<MidiEvent>> {
        val currentEvents = getEventsByMeasure(currentMeasure)
        val nextMeasure = currentMeasure + 1
        val nextEvents = if (nextMeasure <= totalMeasures) {
            getEventsByMeasure(nextMeasure)
        } else {
            emptyList()
        }
        val thirdMeasure = currentMeasure + 2
        val thirdEvents = if (thirdMeasure <= totalMeasures) {
            getEventsByMeasure(thirdMeasure)
        } else {
            emptyList()
        }
        Log.d(
            "SheetMusic",
            "Three measures: current=$currentMeasure (${currentEvents.size} events), next=$nextMeasure (${nextEvents.size} events), third=$thirdMeasure (${thirdEvents.size} events)"
        )
        return Triple(currentEvents, nextEvents, thirdEvents)
    }

    /** 이전 마디로 이동 */
    private fun moveToPreviousMeasure() {
        val currentTick = multiPlayer.getCurrentTick()

        if (ticksPerMeasure > 0 && currentTick > 0) {
            // 현재 마디 계산
            val currentMeasure = tickToMeasure(currentTick)

            // 이전 마디로 이동 (최소 1마디)
            val targetMeasure = (currentMeasure - 1).coerceAtLeast(1)
            val targetTick = measureToTick(targetMeasure)

            Log.d(
                "MeasureNav",
                "Previous: currentTick=$currentTick, currentMeasure=$currentMeasure, targetMeasure=$targetMeasure, targetTick=$targetTick"
            )

            // 재생 상태 확인
            val wasPlaying = multiPlayer.isPlaying()

            // 마디 이동 (seek)
            multiPlayer.seekTo(targetTick)
            updateMeasureDisplay(targetMeasure)
            seekBarMeasure.progress = (targetMeasure - 1).coerceAtLeast(0)

            // 마디 변경 이벤트 발생
            lastDisplayedMeasure = -1 // 강제로 업데이트하도록 리셋
            // 전체 렌더링이므로 화살표만 업데이트
            webViewSheetMusic.evaluateJavascript(
                "javascript:updateArrow($targetMeasure);",
                null
            )
            lastDisplayedMeasure = targetMeasure

            // 재생 중이었으면 seek 후 계속 재생, 아니면 재생하지 않음
            if (wasPlaying) {
                // 재생 중이었으면 seek 후 재생 계속
                multiPlayer.stopAll()
                multiPlayer.startAll()
                handler.post(updateRunnable)
            }
            // 재생 중이 아니었으면 재생하지 않음 (마디만 이동)
        }
    }

    /** 다음 마디로 이동 */
    private fun moveToNextMeasure() {
        val currentMeasure = tickToMeasure(multiPlayer.getCurrentTick())
        if (currentMeasure < totalMeasures) {
            val targetMeasure = currentMeasure + 1
            val targetTick = measureToTick(targetMeasure)

            // 재생 상태 확인
            val wasPlaying = multiPlayer.isPlaying()

            // 마디 이동 (seek)
            multiPlayer.seekTo(targetTick)
            updateMeasureDisplay(targetMeasure)
            seekBarMeasure.progress = targetMeasure - 1

            // 마디 변경 이벤트 발생
            lastDisplayedMeasure = -1 // 강제로 업데이트하도록 리셋
            // 전체 렌더링이므로 화살표만 업데이트
            webViewSheetMusic.evaluateJavascript(
                "javascript:updateArrow($targetMeasure);",
                null
            )
            lastDisplayedMeasure = targetMeasure

            // 재생 중이었으면 seek 후 계속 재생, 아니면 재생하지 않음
            if (wasPlaying) {
                // 재생 중이었으면 seek 후 재생 계속
                multiPlayer.stopAll()
                multiPlayer.startAll()
                handler.post(updateRunnable)
            }
            // 재생 중이 아니었으면 재생하지 않음 (마디만 이동)
        }
    }

    /** TimeSignature 추출 */
    private fun extractTimeSignature(midiFile: MidiFile): TimeSignature? {
        for (track in midiFile.tracks) {
            for (event in track.events) {
                if (event is TimeSignature) {
                    return event
                }
            }
        }
        return null
    }

    private fun extractKeySignature(midiFile: MidiFile): KeySignature? {
        for (track in midiFile.tracks) {
            for (event in track.events) {
                if (event is KeySignature) {
                    return event
                }
            }
        }
        return null
    }

    /** KeySignature를 VexFlow 형식으로 변환 */
    private fun keySignatureToVexFlow(keySignature: KeySignature?): String {
        if (keySignature == null) {
            return "C" // 기본값: C major
        }

        val key = keySignature.key
        val scale = keySignature.scale

        // Major/Minor에 따른 조표 문자열 생성
        val majorKeys = mapOf(
            -7 to "Cb", -6 to "Gb", -5 to "Db", -4 to "Ab", -3 to "Eb", -2 to "Bb", -1 to "F",
            0 to "C", 1 to "G", 2 to "D", 3 to "A", 4 to "E", 5 to "B", 6 to "F#", 7 to "C#"
        )

        val minorKeys = mapOf(
            -7 to "Abm",
            -6 to "Ebm",
            -5 to "Bbm",
            -4 to "Fm",
            -3 to "Cm",
            -2 to "Gm",
            -1 to "Dm",
            0 to "Am",
            1 to "Em",
            2 to "Bm",
            3 to "F#m",
            4 to "C#m",
            5 to "G#m",
            6 to "D#m",
            7 to "A#m"
        )

        return if (scale == KeySignature.SCALE_MINOR) {
            minorKeys[key] ?: "Am"
        } else {
            majorKeys[key] ?: "C"
        }
    }

    /** Tick을 마디로 변환 */
    private fun tickToMeasure(tick: Long): Int {
        return if (ticksPerMeasure > 0) {
            ((tick / ticksPerMeasure) + 1).toInt()
        } else {
            1
        }
    }

    /** 마디를 Tick으로 변환 */
    private fun measureToTick(measure: Int): Long {
        return if (ticksPerMeasure > 0) {
            ((measure - 1) * ticksPerMeasure).toLong()
        } else {
            0L
        }
    }

    /** 마디 표시 업데이트 */
    private fun updateMeasureDisplay(measure: Int) {
        val loopText = if (loopMeasure != null) " [반복: ${loopMeasure}]" else ""
        txtMeasure.text = "마디: $measure / $totalMeasures$loopText"
    }

    /** 마디 선택 처리 (클릭 시 호출) */
    private fun handleMeasureSelection(measure: Int) {
        if (loopMeasure == measure) {
            // 같은 마디 재선택 시 반복 취소
            clearLoopMeasure()
        } else {
            // 새 마디 선택 시 반복 시작
            setLoopMeasure(measure)
        }
    }

    /** 마디 반복 시작 */
    private fun setLoopMeasure(measure: Int) {
        loopMeasure = measure
        val startTick = measureToTick(measure)
        multiPlayer.seekTo(startTick)

        // 마디 강조 표시
        webViewSheetMusic.evaluateJavascript(
            "javascript:highlightMeasure($measure, true);",
            null
        )

        // 재생 중이 아니면 시작
        if (!multiPlayer.isPlaying()) {
            multiPlayer.startAll()
            handler.post(updateRunnable)
        }

        // 마디 표시 업데이트
        val currentMeasure = tickToMeasure(multiPlayer.getCurrentTick())
        updateMeasureDisplay(currentMeasure)

        Log.d("Loop", "Loop measure $measure started")
    }

    /** 반복 취소 */
    private fun clearLoopMeasure() {
        val previousMeasure = loopMeasure
        loopMeasure = null

        // 강조 표시 제거
        if (previousMeasure != null) {
            webViewSheetMusic.evaluateJavascript(
                "javascript:highlightMeasure($previousMeasure, false);",
                null
            )
        }

        // 마디 표시 업데이트
        val currentMeasure = tickToMeasure(multiPlayer.getCurrentTick())
        updateMeasureDisplay(currentMeasure)

        Log.d("Loop", "Loop cancelled")
    }

    /** MIDI InputStream → 트랙 단위 MidiEvent 리스트 반환 */
    private fun parseMidiFile(inputStream: InputStream): List<List<MidiEvent>> {
        val midiFile = MidiFile(inputStream)
        return parseMidiFileFromMidiFile(midiFile)
    }

    /** MidiFile 객체에서 트랙 단위 MidiEvent 리스트 반환 */
    private fun parseMidiFileFromMidiFile(midiFile: MidiFile): List<List<MidiEvent>> {
        val tracksEvents = mutableListOf<List<MidiEvent>>()

        for ((trackIndex, track) in midiFile.tracks.withIndex()) {
            val events = mutableListOf<MidiEvent>()
            for (event in track.events) {
                when (event) {
                    is NoteOn -> events.add(
                        MidiEvent(
                            event.tick.toLong(),
                            event.noteValue,
                            event.velocity,
                            true,
                            trackIndex
                        )
                    )

                    is NoteOff -> events.add(
                        MidiEvent(
                            event.tick.toLong(),
                            event.noteValue,
                            event.velocity,
                            false,
                            trackIndex
                        )
                    )
                }
            }
            tracksEvents.add(events)
        }

        return tracksEvents
    }

    /** MidiFile 객체에서 트랙 정보와 함께 이벤트 리스트 반환 */
    private fun parseMidiFileWithTrackInfo(
        midiFile: MidiFile,
        fileIndex: Int,
        fileName: String
    ): List<Triple<List<MidiEvent>, Int, String>> {
        val tracksData = mutableListOf<Triple<List<MidiEvent>, Int, String>>()

        for ((trackIndex, track) in midiFile.tracks.withIndex()) {
            val events = mutableListOf<MidiEvent>()
            var trackName = "트랙 ${trackIndex + 1}"

            // 트랙 이름 추출
            for (event in track.events) {
                if (event is TrackName) {
                    trackName = event.trackName
                    break
                }
            }

            // NoteOn/NoteOff 이벤트 추출
            for (event in track.events) {
                when (event) {
                    is NoteOn -> events.add(
                        MidiEvent(
                            event.tick.toLong(),
                            event.noteValue,
                            event.velocity,
                            true,
                            trackIndex
                        )
                    )

                    is NoteOff -> events.add(
                        MidiEvent(
                            event.tick.toLong(),
                            event.noteValue,
                            event.velocity,
                            false,
                            trackIndex
                        )
                    )
                }
            }

            // 이벤트가 있는 트랙만 추가
            if (events.isNotEmpty()) {
                tracksData.add(Triple(events, trackIndex, trackName))
            }
        }

        return tracksData
    }

    /** 트랙 체크박스 리스트 생성 */
    private fun createTrackCheckboxes() {
        trackCheckboxContainer.removeAllViews()

        trackInfoList.forEachIndexed { index, trackInfo ->
            // 트랙 아이템 컨테이너 (음악 앱 스타일)
            val container = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(16, 16, 16, 16)
                setBackgroundColor(if (index % 2 == 0) 0xFF1a1a1a.toInt() else 0xFF121212.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            // 트랙 번호 표시
            val trackNumber = TextView(this).apply {
                text = "${index + 1}"
                textSize = 14f
                setTextColor(0xFFb3b3b3.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = 16
                    width = 40
                }
                gravity = android.view.Gravity.CENTER
            }

            // 트랙 정보 텍스트
            val trackInfoText = TextView(this).apply {
                text = trackInfo.trackName
                textSize = 16f
                setTextColor(0xFFffffff.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            val fileNameText = TextView(this).apply {
                text = trackInfo.fileName
                textSize = 12f
                setTextColor(0xFFb3b3b3.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val textContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }
            textContainer.addView(trackInfoText)
            textContainer.addView(fileNameText)

            // 체크박스 (음악 앱 스타일)
            val checkbox = android.widget.CheckBox(this).apply {
                isChecked = true // 기본적으로 모두 선택
                buttonTintList = android.content.res.ColorStateList.valueOf(0xFF1db954.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = 16
                }

                setOnCheckedChangeListener { _, isChecked ->
                    multiPlayer.setMute(trackInfo.globalTrackIndex, !isChecked)
                    // 체크 상태에 따라 텍스트 색상 변경
                    trackInfoText.setTextColor(if (isChecked) 0xFFffffff.toInt() else 0xFF535353.toInt())
                    fileNameText.setTextColor(if (isChecked) 0xFFb3b3b3.toInt() else 0xFF404040.toInt())
                }
            }

            container.addView(trackNumber)
            container.addView(textContainer)
            container.addView(checkbox)
            trackCheckboxContainer.addView(container)
        }
    }

    /** 전체선택/전체해제 */
    private fun selectAllTracks(select: Boolean) {
        for (i in 0 until trackCheckboxContainer.childCount) {
            val container = trackCheckboxContainer.getChildAt(i) as? LinearLayout
            // 체크박스는 마지막 자식 요소
            val checkbox =
                container?.getChildAt(container.childCount - 1) as? android.widget.CheckBox
            checkbox?.isChecked = select
        }
    }

    /** Top Sheet 설정 */
    private fun setupBottomSheet() {
        // Top Sheet의 컨테이너와 버튼 참조
        trackCheckboxContainer = findViewById(R.id.trackCheckboxContainer)
        btnSelectAll = findViewById(R.id.btnSelectAll)
        btnDeselectAll = findViewById(R.id.btnDeselectAll)

        // 트랙 체크박스 리스트 생성
        createTrackCheckboxes()

        // 전체선택/전체해제 버튼 리스너
        btnSelectAll.setOnClickListener {
            selectAllTracks(true)
        }
        btnDeselectAll.setOnClickListener {
            selectAllTracks(false)
        }

        // 스와이프 제스처 제거됨 (버튼으로만 열기)
    }

    /** Top Sheet 표시 */
    private fun showTopSheet() {
        if (isTopSheetVisible) return

        isTopSheetVisible = true
        topSheet.visibility = View.VISIBLE

        // View가 측정되기를 기다린 후 애니메이션 시작
        topSheet.post {
            // 화면 크기 가져오기
            val displayMetrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            val screenHeight = displayMetrics.heightPixels
            val maxSheetHeight = (screenHeight * 0.8).toInt() // 화면 높이의 80%

            // Top Sheet의 최대 높이 제한
            val layoutParams = topSheet.layoutParams
            if (layoutParams.height > maxSheetHeight || layoutParams.height == android.view.ViewGroup.LayoutParams.WRAP_CONTENT) {
                layoutParams.height = maxSheetHeight
                topSheet.layoutParams = layoutParams
            }

            val height = topSheet.height
            if (height > 0) {
                topSheet.translationY = -height.toFloat()
                // 위에서 아래로 슬라이드 애니메이션
                val animator = android.animation.ObjectAnimator.ofFloat(
                    topSheet,
                    "translationY",
                    -height.toFloat(),
                    0f
                )
                animator.duration = 300
                animator.start()
            } else {
                // 높이가 측정되지 않은 경우 화면 높이의 80% 사용
                val defaultHeight = maxSheetHeight
                topSheet.translationY = -defaultHeight.toFloat()
                val animator = android.animation.ObjectAnimator.ofFloat(
                    topSheet,
                    "translationY",
                    -defaultHeight.toFloat(),
                    0f
                )
                animator.duration = 300
                animator.start()
            }
        }
    }

    /** Top Sheet 숨김 */
    private fun hideTopSheet() {
        if (!isTopSheetVisible) return

        val currentY = topSheet.translationY
        val targetY = -topSheet.height.toFloat()

        // 아래에서 위로 슬라이드 애니메이션
        val animator = android.animation.ObjectAnimator.ofFloat(
            topSheet,
            "translationY",
            currentY,
            if (targetY < -500) -1000f else targetY
        )
        animator.duration = 300
        animator.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                topSheet.visibility = View.GONE
                isTopSheetVisible = false
            }
        })
        animator.start()
    }

    /**
     * 확대/축소 적용
     */
    private fun applyZoom() {
        webViewSheetMusic.evaluateJavascript(
            "javascript:document.body.style.zoom = $currentZoomLevel;",
            null
        )
        Log.d("Zoom", "Zoom level set to: $currentZoomLevel")
    }

    /**
     * 메트로놈 표시 시작
     */
    private fun startMetronome(bpm: Int) {
        metronomeIndicator.visibility = View.VISIBLE
        txtMetronome.text = "♩ = $bpm"

        val beatInterval = (60000.0 / bpm).toLong()

        metronomeRunnable = object : Runnable {
            override fun run() {
                if (multiPlayer.isPlaying()) {
                    // 비트 표시 애니메이션
                    metronomeBeat.animate()
                        .scaleX(1.5f)
                        .scaleY(1.5f)
                        .alpha(1.0f)
                        .setDuration(50)
                        .withEndAction {
                            metronomeBeat.animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .alpha(0.7f)
                                .setDuration(beatInterval - 50)
                                .start()
                        }
                        .start()

                    metronomeHandler?.postDelayed(this, beatInterval)
                } else {
                    stopMetronome()
                }
            }
        }

        metronomeHandler = Handler(Looper.getMainLooper())
        metronomeHandler?.post(metronomeRunnable!!)
    }

    /**
     * 메트로놈 표시 중지
     */
    private fun stopMetronome() {
        metronomeHandler?.removeCallbacks(metronomeRunnable ?: return)
        metronomeRunnable = null
        metronomeIndicator.visibility = View.GONE
    }

    /**
     * 현재 재생 중인 음표 하이라이트 (개선된 버전)
     */
    private fun highlightCurrentNote(currentTick: Long) {
        // 현재 틱에 재생 중인 모든 음표 찾기 (화음 지원)
        val activeNotes = allMidiEvents.filter { event ->
            event.isNoteOn && event.tick <= currentTick &&
                    // NoteOff 이벤트가 아직 발생하지 않은 노트
                    allMidiEvents.none {
                        !it.isNoteOn &&
                                it.note == event.note &&
                                it.tick > currentTick
                    }
        }

        if (activeNotes.isNotEmpty()) {
            // 모든 활성 음표를 하이라이트 (화음 지원)
            val noteNames = activeNotes.map { getNoteName(it.note) }
            val noteNamesJson = noteNames.joinToString(", ") { "'$it'" }
            webViewSheetMusic.evaluateJavascript(
                "javascript:highlightNotes([$noteNamesJson]);",
                null
            )
        } else {
            // 활성 음표가 없으면 하이라이트 제거
            webViewSheetMusic.evaluateJavascript(
                "javascript:highlightNotes([]);",
                null
            )
        }
    }

    /**
     * MIDI 노트 번호를 음표 이름으로 변환 (VexFlow 형식: c/4, d/4 등)
     */
    private fun getNoteName(midiNote: Int): String {
        val noteNames = arrayOf("c", "c#", "d", "d#", "e", "f", "f#", "g", "g#", "a", "a#", "b")
        val octave = (midiNote / 12) - 1
        val note = noteNames[midiNote % 12]
        return "$note/$octave"
    }

}
