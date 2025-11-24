package com.robsonmartins.androidmidisynth

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
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
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import android.graphics.Bitmap
import android.graphics.Canvas

class MainActivity : AppCompatActivity() {

    companion object {
        init {
            System.loadLibrary("synth-lib")
        }
    }

    private lateinit var synth: SynthManager
    private lateinit var multiPlayer: MidiMultiPlayer

    private lateinit var txtAlbumName: TextView
    private lateinit var buttonContainer: LinearLayout
    private lateinit var trackCheckboxContainer: LinearLayout
    private lateinit var btnSelectAll: Button
    private lateinit var btnDeselectAll: Button
    private lateinit var btnPlayAll: Button
    private lateinit var btnStopAll: Button
    private lateinit var txtBPM: TextView
    private lateinit var seekBarBPM: SeekBar

    // 마디 관련 UI
    private lateinit var txtMeasure: TextView
    private lateinit var seekBarMeasure: SeekBar
    private lateinit var btnPrevMeasure: Button
    private lateinit var btnNextMeasure: Button

    // 악보 이미지 표시
    private lateinit var imgSheetMusic: ImageView

    // 워터마크
    private lateinit var watermarkView: WatermarkView

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

    // 서버 업로드 관련
    private val serverUrl = "https://your-server.com/api/upload-sheet-music" // TODO: 실제 서버 URL로 변경
    private val uploadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastUploadedMeasure = -1 // 중복 업로드 방지
    private var isRendering = false // 진행 중 플래그
    private var currentRenderJob: Job? = null // 현재 렌더링 작업
    private var pendingMeasure = -1 // 대기 중인 마디

    // SeekBar 업데이트용
    private val handler = Handler(Looper.getMainLooper())
    private var isUserSeeking = false
    private val updateRunnable = object : Runnable {
        override fun run() {
            if (!isUserSeeking && multiPlayer.isPlaying()) {
                val currentTick = multiPlayer.getCurrentTick()
                val currentMeasure = tickToMeasure(currentTick)
                seekBarMeasure.progress = (currentMeasure - 1).coerceAtLeast(0)
                updateMeasureDisplay(currentMeasure)
                // 재생 중 마디 변경 시 서버 업로드
                onMeasureChanged(currentMeasure)
            }
            handler.postDelayed(this, 50) // ms 마다 업데이트
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // 앱바 제거
        supportActionBar?.hide()

        // View 초기화
        txtAlbumName = findViewById(R.id.txtAlbumName)
        buttonContainer = findViewById(R.id.buttonContainer)
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
        imgSheetMusic = findViewById(R.id.imgSheetMusic)
        watermarkView = findViewById(R.id.watermarkView)

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

        // Intent로 전달받은 MIDI 파일 로드
        val midiFiles = intent.getStringArrayListExtra("MID_FILES") ?: arrayListOf()
        val albumName = intent.getStringExtra("ALBUM_NAME") ?: "앨범"
        txtAlbumName.text = "앨범: $albumName"

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
        totalMeasures = if (ticksPerMeasure > 0) {
            ((maxTick / ticksPerMeasure) + 1).toInt()
        } else {
            1
        }

        // 마디 SeekBar 설정
        seekBarMeasure.max = (totalMeasures - 1).coerceAtLeast(0)
        seekBarMeasure.progress = 0
        updateMeasureDisplay(1)

        // 초기 마디 서버 업로드
        onMeasureChanged(1)

        // Bottom Sheet 초기화
        setupBottomSheet()

        // 스와이프 제스처 설정 (상단 헤더 + 악보 영역)
        setupSwipeGesture()

        // PLAY ALL
        btnPlayAll.setOnClickListener {
            multiPlayer.startAll()
            handler.post(updateRunnable) // 업데이트 시작
        }

        // STOP ALL
        btnStopAll.setOnClickListener {
            multiPlayer.stopAll()
            handler.removeCallbacks(updateRunnable) // 업데이트 중지
        }

        // BPM 변경
        seekBarBPM.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val bpm = progress.coerceAtLeast(30)
                txtBPM.text = "BPM: $bpm"
                multiPlayer.setBPM(bpm.toDouble())
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
                onMeasureChanged(measure)

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
        uploadScope.cancel() // 업로드 코루틴 취소
        currentRenderJob?.cancel() // 진행 중인 렌더링 취소
        multiPlayer.stopAll()
        synth.release()
        super.onDestroy()
    }

    /** 마디 변경 이벤트 핸들러 - 모든 마디 변경 지점에서 호출 */
    private fun onMeasureChanged(measure: Int) {
        Log.d(
            "SheetMusic",
            "onMeasureChanged called for measure $measure, lastUploaded: $lastUploadedMeasure, isRendering: $isRendering"
        )

        // 마디가 변경되지 않았으면 무시
        if (measure == lastUploadedMeasure) {
            Log.d("SheetMusic", "Skipping render for measure $measure (same as last uploaded)")
            return
        }

        // 진행 중이면 이전 작업 취소하고 새 마디로 즉시 렌더링 시작
        if (isRendering) {
            Log.d(
                "SheetMusic",
                "Cancelling previous render job and starting new render for measure $measure"
            )
            currentRenderJob?.cancel()
            isRendering = false // 플래그 리셋
        }

        // 즉시 실행
        Log.d(
            "SheetMusic",
            "Starting render for measure $measure (different from last: $lastUploadedMeasure)"
        )
        pendingMeasure = -1
        renderAndUploadSheetMusic(measure)
    }

    /** 악보 렌더링 및 서버 업로드 */
    private fun renderAndUploadSheetMusic(measure: Int) {
        // 진행 중 플래그 설정
        isRendering = true

        currentRenderJob = uploadScope.launch {
            try {
                // 현재 마디와 다음 마디 이벤트 가져오기
                val (currentEvents, nextEvents) = getEventsForTwoMeasures(measure)
                Log.d("SheetMusic", "Rendering measure $measure with ${currentEvents.size} current events, ${nextEvents.size} next events")

                // 렌더링 완료 대기용 Deferred
                val renderComplete = CompletableDeferred<Boolean>()

                // WebView 로드 완료 대기
                val loadComplete = CompletableDeferred<Boolean>()

                // WebView 생성 및 설정을 메인 스레드에서 수행
                val webView = withContext(Dispatchers.Main) {
                    // WebView 생성 (메인 스레드에서만 가능)
                    val webView = createVexFlowWebView(currentEvents, nextEvents, measure, renderComplete)

                    // WebViewClient 설정
                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            Log.d("SheetMusic", "WebView page finished for measure $measure")
                            loadComplete.complete(true)
                        }
                    }

                    // WebView를 부모에 추가 (렌더링을 위해)
                    val parent = window.decorView.rootView as? android.view.ViewGroup
                    parent?.let {
                        // FrameLayout의 경우 MarginLayoutParams 사용
                        val layoutParams = if (it is android.widget.FrameLayout) {
                            android.widget.FrameLayout.LayoutParams(800, 500) // 높이 증가 (두 마디용)
                        } else {
                            android.view.ViewGroup.LayoutParams(800, 500)
                        }
                        webView.layoutParams = layoutParams
                        it.addView(webView)
                    }
                    // WebView를 화면 밖에 배치 (보이지 않게)
                    webView.x = -10000f
                    webView.y = -10000f
                    webView.visibility = android.view.View.VISIBLE
                    // 레이아웃 강제 실행
                    webView.measure(
                        android.view.View.MeasureSpec.makeMeasureSpec(
                            800,
                            android.view.View.MeasureSpec.EXACTLY
                        ),
                        android.view.View.MeasureSpec.makeMeasureSpec(
                            500, // 높이 증가
                            android.view.View.MeasureSpec.EXACTLY
                        )
                    )
                    webView.layout(0, 0, 800, 500)
                    Log.d(
                        "SheetMusic",
                        "WebView added to parent, size: ${webView.width}x${webView.height}"
                    )

                    webView
                }

                // 페이지 로드 완료 대기 (최대 5초)
                try {
                    withTimeout(5000) {
                        loadComplete.await()
                    }
                    Log.d("SheetMusic", "Page loaded, waiting for VexFlow rendering...")
                } catch (e: TimeoutCancellationException) {
                    Log.w("SheetMusic", "Page load timeout, continuing anyway")
                }

                // VexFlow 렌더링 완료 대기 (최대 5초)
                try {
                    withTimeout(5000) {
                        renderComplete.await()
                    }
                    Log.d("SheetMusic", "VexFlow rendering complete")
                } catch (e: TimeoutCancellationException) {
                    Log.w("SheetMusic", "VexFlow render timeout, continuing anyway")
                    delay(1000) // 최소 대기 시간
                }

                // 취소 확인
                if (!isActive) {
                    withContext(Dispatchers.Main) {
                        val parent = webView.parent as? android.view.ViewGroup
                        parent?.removeView(webView)
                    }
                    return@launch
                }

                // 이미지 캡처
                val bitmap = withContext(Dispatchers.Main) {
                    try {
                        val captured = webViewToBitmap(webView)
                        Log.d("SheetMusic", "Bitmap captured: ${captured.width}x${captured.height}")
                        captured
                    } catch (e: Exception) {
                        Log.e("SheetMusic", "Bitmap capture error", e)
                        null
                    }
                }

                // WebView 제거
                withContext(Dispatchers.Main) {
                    val parent = webView.parent as? android.view.ViewGroup
                    parent?.removeView(webView)
                }

                if (bitmap == null) {
                    Log.e("SheetMusic", "Failed to capture bitmap for measure $measure")
                    return@launch
                }

                // Base64 인코딩
                var sendImageSwitch = false;
                if (sendImageSwitch) {
                    val base64Image = bitmapToBase64(bitmap)
                    Log.d("SheetMusic", "Base64 image size: ${base64Image.length} bytes")

                    uploadSheetMusicToServer(base64Image, measure)
                }

                // 화면에 악보 이미지 표시
                withContext(Dispatchers.Main) {
                    imgSheetMusic.setImageBitmap(bitmap)
                    Log.d("SheetMusic", "Image set to ImageView for measure $measure")
                }
                // 업로드 완료
                lastUploadedMeasure = measure
                Log.d("SheetMusic", "Render and upload completed for measure $measure")

            } catch (e: CancellationException) {
                // 취소된 경우 정리
                Log.d("SheetMusic", "Render cancelled for measure $measure")
                throw e
            } catch (e: Exception) {
                Log.e("SheetMusic", "Render and upload error", e)
                e.printStackTrace()
            } finally {
                // 진행 중 플래그 해제
                isRendering = false

                // 대기 중인 마디가 있으면 처리
                if (pendingMeasure != -1) {
                    val nextMeasure = pendingMeasure
                    pendingMeasure = -1
                    renderAndUploadSheetMusic(nextMeasure)
                }
            }
        }
    }

    /** VexFlow WebView 생성 */
    private fun createVexFlowWebView(
        currentEvents: List<MidiEvent>,
        nextEvents: List<MidiEvent>,
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

        val html = generateVexFlowHTML(currentEvents, nextEvents, measure)
        webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null)

        return webView
    }

    /** 이벤트 리스트에서 노트 JSON과 총 박자 수 생성 */
    private fun generateNotesFromEvents(events: List<MidiEvent>): Pair<String, Double> {
        val noteOnEvents = events.filter { it.isNoteOn }
        val noteOffEvents = events.filter { !it.isNoteOn }
        Log.d(
            "SheetMusic",
            "Found ${noteOnEvents.size} NoteOn events, ${noteOffEvents.size} NoteOff events"
        )

        // 같은 시간(tick)에 연주되는 노트들을 그룹화하여 화음으로 만들기
        val notesByTick = noteOnEvents.groupBy { it.tick }.toSortedMap()

        // NoteOff 이벤트를 노트별로 매핑 (노트 길이 계산용)
        val noteOffByNote = noteOffEvents.groupBy { it.note }

        // 시간 서명 정보 가져오기
        val numerator = timeSignature?.getNumerator() ?: 4
        val denominator = timeSignature?.getRealDenominator() ?: 4

        // 각 시간 그룹에서 노트들을 화음으로 변환하고 duration 계산
        // 쉼표를 포함한 모든 음표/쉼표 리스트
        val allElements = mutableListOf<Map<String, Any>>()
        var previousTick: Long? = null
        var previousNoteKey: String? = null // 타이 계산용

        notesByTick.forEach { (tick, tickEvents) ->
            // 바이올린 음역대: G3 (MIDI 55) ~ E7 (MIDI 100)
            // 트레블 클레프에 적합한 범위로 필터링
            // 범위를 약간 넓혀서 C3 (48) ~ E7 (100)로 설정 (바이올린의 확장 범위 포함)
            val validNotes = tickEvents
                .filter { it.note in 48..100 } // 바이올린 확장 음역대 (C3 ~ E7)
                .map { midiNoteToVexFlowNote(it.note) }
                .sorted() // 정렬하여 일관성 유지

            Log.d(
                "SheetMusic",
                "Tick $tick: ${tickEvents.size} events, ${validNotes.size} valid notes after filtering"
            )

            if (validNotes.isNotEmpty()) {
                // 바이올린은 단선 악기이므로 가장 높은 멜로디 라인 선택
                // 옥타브 4 이상의 노트를 우선 사용 (바이올린의 일반적인 연주 범위)
                val trebleNotes = validNotes.filter {
                    val octave = it.split("/").getOrNull(1)?.toIntOrNull() ?: 0
                    octave >= 4
                }

                // 옥타브 4 이상 노트가 있으면 그것을 사용, 없으면 가장 높은 노트 선택
                val finalNotes = if (trebleNotes.isNotEmpty()) {
                    // 가장 높은 노트 선택 (멜로디 라인)
                    listOf(trebleNotes.maxByOrNull { note ->
                        val parts = note.split("/")
                        val octave = parts.getOrNull(1)?.toIntOrNull() ?: 0
                        val noteName = parts.firstOrNull() ?: ""
                        octave * 12 + when (noteName.lowercase()) {
                            "c" -> 0; "c#" -> 1; "d" -> 2; "d#" -> 3; "e" -> 4
                            "f" -> 5; "f#" -> 6; "g" -> 7; "g#" -> 8; "a" -> 9; "a#" -> 10; "b" -> 11
                            else -> 0
                        }
                    } ?: trebleNotes.first())
                } else {
                    // 옥타브 3 노트 중 가장 높은 것 선택
                    listOf(validNotes.maxByOrNull { note ->
                        val parts = note.split("/")
                        val octave = parts.getOrNull(1)?.toIntOrNull() ?: 0
                        val noteName = parts.firstOrNull() ?: ""
                        octave * 12 + when (noteName.lowercase()) {
                            "c" -> 0; "c#" -> 1; "d" -> 2; "d#" -> 3; "e" -> 4
                            "f" -> 5; "f#" -> 6; "g" -> 7; "g#" -> 8; "a" -> 9; "a#" -> 10; "b" -> 11
                            else -> 0
                        }
                    } ?: validNotes.first())
                }

                if (finalNotes.isNotEmpty()) {
                    // 이전 요소와의 간격 확인 (쉼표 추가용)
                    val prevTick = previousTick
                    if (prevTick != null && tick > prevTick) {
                        val tickDiff = tick - prevTick
                        val resolution = midiResolution.toLong()

                        // 간격이 충분히 크면 쉼표 추가
                        if (tickDiff >= resolution / 8) { // 32분음표 이상의 간격
                            val restDuration = when {
                                tickDiff >= resolution * 2 -> "hr" // half rest
                                tickDiff >= resolution -> "qr" // quarter rest
                                tickDiff >= resolution / 2 -> "8r" // eighth rest
                                tickDiff >= resolution / 4 -> "16r" // sixteenth rest
                                tickDiff >= resolution / 8 -> "32r" // thirty-second rest
                                else -> "8r" // 기본값
                            }

                            Log.d(
                                "SheetMusic",
                                "Adding rest: tick=$tick, prevTick=$prevTick, restDuration=$restDuration"
                            )
                            allElements.add(
                                mapOf(
                                    "type" to "rest",
                                    "duration" to restDuration,
                                    "tick" to prevTick
                                )
                            )
                        }
                    }

                    // 노트의 실제 길이 계산 (NoteOff 이벤트 기반)
                    val selectedNote = tickEvents.firstOrNull {
                        val noteName = midiNoteToVexFlowNote(it.note)
                        finalNotes.contains(noteName)
                    }

                    // NoteOff 이벤트 찾기 (노트 길이 계산용)
                    val noteOffTick = noteOffEvents
                        .filter { it.note == selectedNote?.note }
                        .minOfOrNull { it.tick } ?: (tick + midiResolution.toLong())

                    val noteDuration = noteOffTick - tick
                    val resolution = midiResolution.toLong()

                    // duration 계산: 이전 tick과의 차이를 기반으로
                    val duration = if (prevTick != null && tick > prevTick) {
                        val tickDiff = tick - prevTick
                        when {
                            tickDiff >= resolution * 2 -> "h" // half note (2 beats)
                            tickDiff >= resolution -> "q" // quarter note (1 beat)
                            tickDiff >= resolution / 2 -> "8" // eighth note (0.5 beat)
                            tickDiff >= resolution / 4 -> "16" // sixteenth note (0.25 beat)
                            tickDiff >= resolution / 8 -> "32" // thirty-second note (0.125 beat)
                            else -> "8" // 기본값 (너무 짧으면 eighth note)
                        }
                    } else {
                        "q" // 첫 번째 노트는 기본 quarter note
                    }

                    // velocity 정보도 저장 (다이나믹 마크용)
                    val velocity = selectedNote?.velocity ?: 64

                    // 타이 확인: 이전 노트와 같은 음표인지 확인
                    val currentNoteKey = finalNotes.first()
                    val needsTie = previousNoteKey != null && previousNoteKey == currentNoteKey

                    // 아티큘레이션 계산 (NoteOn/NoteOff 간격 기반)
                    // 노트 길이 대비 실제 연주 길이 비율로 추정
                    val articulation = when {
                        noteDuration < resolution * 0.3 -> "staccato" // 스타카토: 30% 미만
                        noteDuration >= resolution * 0.9 -> "legato" // 레가토: 90% 이상
                        else -> "normal" // 일반
                    }

                    Log.d(
                        "SheetMusic",
                        "Tick: $tick, prevTick: $prevTick, duration: $duration, velocity: $velocity, needsTie: $needsTie, articulation: $articulation"
                    )

                    // 첫 번째 노트만 사용 (단일 노트로 표시)
                    allElements.add(
                        mapOf(
                            "type" to "note",
                            "keys" to listOf(currentNoteKey),
                            "duration" to duration,
                            "tick" to tick,
                            "velocity" to velocity,
                            "needsTie" to needsTie,
                            "articulation" to articulation
                        )
                    )

                    previousTick = tick
                    previousNoteKey = currentNoteKey
                }
            }
        }

        Log.d(
            "SheetMusic",
            "Grouped into ${allElements.size} elements (notes + rests) from ${noteOnEvents.size} events"
        )
        if (allElements.isEmpty()) {
            Log.w("SheetMusic", "No valid notes found after filtering! Check note range.")
        }

        val notesJson = if (allElements.isEmpty()) {
            Log.w("SheetMusic", "No valid notes found, using placeholder")
            "new VF.StaveNote({ clef: 'treble', keys: ['b/4'], duration: 'w' })"
        } else {
            // 마디 내의 모든 요소(노트 + 쉼표)를 시간 순서대로 정렬하여 표시
            // 최대 16개 요소만 표시 (한 마디에 적합한 수)
            val elementsToRender = allElements.take(16)
            elementsToRender.joinToString(",\n                ") { element ->
                val elementType = element["type"] as String
                val duration = element["duration"] as String

                when (elementType) {
                    "rest" -> {
                        // 쉼표 생성 (VexFlow는 duration에 'r'을 붙여서 쉼표로 표시)
                        // 쉼표는 keys를 빈 배열로 하거나 특정 키를 사용할 수 있음
                        // VexFlow는 쉼표를 자동으로 처리하므로 keys는 임의로 설정 가능
                        "new VF.StaveNote({ clef: 'treble', keys: ['b/4'], duration: '$duration' })"
                    }

                    "note" -> {
                        // 노트 생성 (velocity 정보 포함)
                        val keysStr = element["keys"] as List<String>
                        val firstKey = keysStr.firstOrNull() ?: "c/4"
                        val velocity = element["velocity"] as? Int ?: 64
                        val needsTie = element["needsTie"] as? Boolean ?: false
                        val articulation = element["articulation"] as? String ?: "normal"
                        // velocity는 나중에 다이나믹 마크로 추가
                        // needsTie는 나중에 타이로 추가
                        // articulation은 나중에 아티큘레이션으로 추가
                        "new VF.StaveNote({ clef: 'treble', keys: ['$firstKey'], duration: '$duration', velocity: $velocity, needsTie: $needsTie, articulation: '$articulation' })"
                    }

                    else -> {
                        // 기본값
                        "new VF.StaveNote({ clef: 'treble', keys: ['c/4'], duration: 'q' })"
                    }
                }
            }
        }

        // 렌더링할 요소들의 총 박자 수 계산 (각 요소의 duration을 합산)
        val elementsToRender = allElements.take(16)
        val totalBeats = elementsToRender.sumOf { element ->
            val duration = element["duration"] as String
            when {
                duration.startsWith("w") -> 4.0
                duration.startsWith("h") -> 2.0
                duration.startsWith("q") -> 1.0
                duration.startsWith("8") -> 0.5
                duration.startsWith("16") -> 0.25
                duration.startsWith("32") -> 0.125
                else -> 1.0
            }
        }

        Log.d(
            "SheetMusic",
            "Total beats calculated: $totalBeats, time signature: $numerator/$denominator, elements to render: ${elementsToRender.size}"
        )
        Log.d("SheetMusic", "Generated notes JSON (first 500 chars): ${notesJson.take(500)}")

        return Pair(notesJson, totalBeats)
    }

    /** VexFlow HTML 생성 (현재 마디 + 다음 마디) */
    private fun generateVexFlowHTML(
        currentEvents: List<MidiEvent>,
        nextEvents: List<MidiEvent>,
        measure: Int
    ): String {
        // 현재 마디 노트 생성
        val (currentNotesJson, currentTotalBeats) = generateNotesFromEvents(currentEvents)
        
        // 다음 마디 노트 생성
        val (nextNotesJson, nextTotalBeats) = if (nextEvents.isNotEmpty()) {
            generateNotesFromEvents(nextEvents)
        } else {
            Pair("", 0.0)
        }

        // 시간 서명 정보 가져오기
        val numerator = timeSignature?.getNumerator() ?: 4
        val denominator = timeSignature?.getRealDenominator() ?: 4
        val keySigStr = keySignatureToVexFlow(keySignature)
        
        return generateVexFlowHTMLWithTwoStaves(
            currentNotesJson, currentTotalBeats,
            nextNotesJson, nextTotalBeats,
            numerator, denominator, keySigStr
        )
    }

    /** VexFlow HTML 생성 (두 개의 Stave - 현재 마디 + 다음 마디) */
    private fun generateVexFlowHTMLWithTwoStaves(
        currentNotesJson: String,
        currentTotalBeats: Double,
        nextNotesJson: String,
        nextTotalBeats: Double,
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

        return """
        <!DOCTYPE html>
        <html>
        <head>
            <script src="vexflow/vexflow-min.js"></script>
            <style>
                body { margin: 0; padding: 10px; background: white; overflow: visible; }
                #sheet { width: 100%; height: 100%; overflow: visible; }
                svg { display: block; }
                #nextMeasure { opacity: 0.5; }
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
                
                var div = document.getElementById("sheet");
                if (!div) {
                    logError('Sheet div not found');
                } else {
                    try {
                        log('Starting VexFlow rendering with two staves...');
                        var VF = Vex.Flow;
                        log('VexFlow loaded: ' + (typeof VF !== 'undefined'));
                        
                        var renderer = new VF.Renderer(div, VF.Renderer.Backends.SVG);
                        renderer.resize(800, 500); // 높이 증가 (두 마디용)
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
                            var durationFractionMap = {
                                'w': 1, 'wr': 1, 'h': 1/2, 'hr': 1/2,
                                'q': 1/4, 'qr': 1/4, '8': 1/8, '8r': 1/8,
                                '16': 1/16, '16r': 1/16, '32': 1/32, '32r': 1/32
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
                        
                        // 다음 마디 Stave (아래쪽, 회색)
                        var nextNotes = ${if (nextNotesJson.isBlank()) "[]" else "[$nextNotesJson]"}
                        log('Next measure notes: ' + nextNotes.length);
                        
                        if (nextNotes.length > 0) {
                            // SVG 그룹 시작 (회색 적용용)
                            context.openGroup("nextMeasure", { id: "nextMeasure" });
                            
                            var stave2 = new VF.Stave(10, 250, 750); // y 좌표를 아래로 이동
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
                            
                            // SVG 요소에 opacity 적용
                            setTimeout(function() {
                                var svg = div.querySelector('svg');
                                if (svg) {
                                    var nextMeasureGroup = svg.querySelector('#nextMeasure');
                                    if (nextMeasureGroup) {
                                        nextMeasureGroup.setAttribute('opacity', '0.5');
                                        // 모든 path, circle, ellipse, line 요소를 회색으로 변경
                                        var elements = nextMeasureGroup.querySelectorAll('path, circle, ellipse, line, text');
                                        elements.forEach(function(el) {
                                            el.setAttribute('stroke', '#999999');
                                            el.setAttribute('fill', '#999999');
                                        });
                                    }
                                }
                                AndroidInterface.onRenderComplete();
                            }, 100);
                        } else {
                            AndroidInterface.onRenderComplete();
                        }
                        
                    } catch (e) {
                        logError('Rendering error: ' + e.message);
                        logError('Stack: ' + e.stack);
                        AndroidInterface.onRenderComplete();
                    }
                }
            </script>
        </body>
        </html>
        """
    }

    /** VexFlow HTML 생성 (박자 정보 포함) - 단일 마디용 (하위 호환성) */
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
            <script src="vexflow/vexflow-min.js"></script>
            <style>
                body { margin: 0; padding: 10px; background: white; overflow: visible; }
                #sheet { width: 100%; height: 100%; overflow: visible; }
                svg { display: block; }
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
                
                var div = document.getElementById("sheet");
                if (!div) {
                    logError('Sheet div not found');
                } else {
                    try {
                        log('Starting VexFlow rendering...');
                        var VF = Vex.Flow;
                        log('VexFlow loaded: ' + (typeof VF !== 'undefined'));
                        
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
                }
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    /** MIDI 노트를 VexFlow 노트 이름으로 변환 */
    private fun midiNoteToVexFlowNote(note: Int): String {
        // MIDI 노트 번호: 0 = C-1, 60 = C4 (중앙 C)
        // VexFlow 형식: "c/4" (C4), "c#/4" (C#4), "d/4" (D4) 등
        val noteNames = arrayOf("c", "c#", "d", "d#", "e", "f", "f#", "g", "g#", "a", "a#", "b")
        val octave = (note / 12) - 1
        val noteIndex = note % 12
        val noteName = noteNames[noteIndex]
        // VexFlow 형식: "c/4" (C4 음표)
        return "$noteName/$octave"
    }

    /** WebView를 Bitmap으로 변환 */
    private fun webViewToBitmap(webView: WebView): Bitmap {
        // WebView의 실제 크기 사용
        val width = webView.width.coerceAtLeast(800)
        val height = webView.height.coerceAtLeast(400)

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

    /** Bitmap을 Base64로 변환 */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    /** 서버에 악보 이미지 업로드 */
    private suspend fun uploadSheetMusicToServer(base64Image: String, measure: Int) {
        withContext(Dispatchers.IO) {
            try {
                val url = URL(serverUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val deviceId = android.provider.Settings.Secure.getString(
                    contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                ) ?: "unknown"

                val jsonData = JSONObject().apply {
                    put("measure", measure)
                    put("image", base64Image)
                    put("timestamp", System.currentTimeMillis())
                    put("deviceId", deviceId)
                    put("totalMeasures", totalMeasures)
                }

                connection.outputStream.use {
                    it.write(jsonData.toString().toByteArray(Charsets.UTF_8))
                }

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    Log.d("SheetMusic", "Upload successful: $response")
                } else {
                    Log.e("SheetMusic", "Upload failed: $responseCode")
                }
            } catch (e: Exception) {
                Log.e("SheetMusic", "Upload error", e)
            }
        }
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

    /** 현재 마디와 다음 마디의 이벤트 가져오기 */
    private fun getEventsForTwoMeasures(currentMeasure: Int): Pair<List<MidiEvent>, List<MidiEvent>> {
        val currentEvents = getEventsByMeasure(currentMeasure)
        val nextMeasure = currentMeasure + 1
        val nextEvents = if (nextMeasure <= totalMeasures) {
            getEventsByMeasure(nextMeasure)
        } else {
            emptyList()
        }
        Log.d("SheetMusic", "Two measures: current=$currentMeasure (${currentEvents.size} events), next=$nextMeasure (${nextEvents.size} events)")
        return Pair(currentEvents, nextEvents)
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
            onMeasureChanged(targetMeasure)

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
            onMeasureChanged(targetMeasure)

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
        txtMeasure.text = "마디: $measure / $totalMeasures"
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
            val checkbox = container?.getChildAt(container.childCount - 1) as? android.widget.CheckBox
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

        // 헤더 영역에서 위로 스와이프 감지 (스크롤뷰와의 충돌 방지)
        val headerLayout = findViewById<LinearLayout>(R.id.topSheetHeader)
        setupHeaderSwipeGesture(headerLayout)
        
        // Top Sheet 전체에서도 위로 스와이프 감지
        setupTopSheetSwipeGesture()
    }
    
    /** 헤더 영역에서 위로 스와이프하여 닫기 */
    private fun setupHeaderSwipeGesture(header: View) {
        var startY = 0f
        var initialTranslationY = 0f
        var isSwiping = false
        
        header.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.y
                    initialTranslationY = topSheet.translationY
                    isSwiping = false
                    // 터치 이벤트를 캡처
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = event.y - startY
                    
                    // 위로 스와이프 감지 (음수 deltaY = 위로)
                    if (deltaY < -20) {
                        if (!isSwiping) {
                            isSwiping = true
                            Log.d("TopSheet", "Swipe started: deltaY=$deltaY")
                        }
                        
                        // Top Sheet를 위로 이동
                        val newY = initialTranslationY + deltaY
                        if (newY <= 0) {
                            topSheet.translationY = newY.coerceAtLeast(-topSheet.height.toFloat())
                        }
                        // 터치 이벤트를 계속 캡처
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                        true
                    } else {
                        // 아래로 스와이프는 무시
                        false
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    
                    if (isSwiping) {
                        val deltaY = event.y - startY
                        val currentY = topSheet.translationY
                        val threshold = -topSheet.height * 0.25f
                        
                        Log.d("TopSheet", "Swipe ended: deltaY=$deltaY, currentY=$currentY, threshold=$threshold")
                        
                        // 위로 충분히 스와이프했으면 닫기
                        if (deltaY < -50 || currentY < threshold) {
                            Log.d("TopSheet", "Closing Top Sheet")
                            hideTopSheet()
                        } else {
                            // 원래 위치로 복귀
                            Log.d("TopSheet", "Returning to original position")
                            val animator = android.animation.ObjectAnimator.ofFloat(
                                topSheet,
                                "translationY",
                                currentY,
                                0f
                            )
                            animator.duration = 200
                            animator.start()
                        }
                        isSwiping = false
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }
    }

    /** 스와이프 제스처 설정 (상단 헤더 + 악보 영역에서 아래로 스와이프하여 Top Sheet 열기) */
    private fun setupSwipeGesture() {
        // 상단 헤더 영역 스와이프 감지
        setupSwipeAreaGesture(swipeArea, true)
        
        // 악보 영역 스와이프 감지
        val sheetMusicArea = findViewById<LinearLayout>(R.id.sheetMusicArea)
        val sheetMusicScrollView = findViewById<android.widget.ScrollView>(R.id.sheetMusicScrollView)
        setupSwipeAreaGesture(sheetMusicArea, false)
        sheetMusicScrollView?.let { setupSwipeAreaGestureForScrollView(it) }
    }
    
    /** 특정 영역에서 아래로 스와이프하여 Top Sheet 열기 */
    private fun setupSwipeAreaGesture(view: View, allowClick: Boolean) {
        var startY = 0f
        
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.y
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // 클릭으로도 Top Sheet 열기 (헤더 영역만)
                    if (allowClick) {
                        showTopSheet()
                        true
                    } else {
                        false
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = event.y - startY
                    // 아래로 스와이프 (위에서 시작하여 아래로)
                    if (deltaY > 100) {
                        showTopSheet()
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }
    }
    
    /** ScrollView 영역에서 아래로 스와이프하여 Top Sheet 열기 (스크롤과 충돌 방지) */
    private fun setupSwipeAreaGestureForScrollView(scrollView: android.widget.ScrollView) {
        var startY = 0f
        
        scrollView.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.y
                    false // ScrollView가 터치 이벤트를 처리하도록
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = event.y - startY
                    // ScrollView가 최상단에 있고, 아래로 스와이프할 때만 Top Sheet 열기
                    if (scrollView.scrollY == 0 && deltaY > 100) {
                        showTopSheet()
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }
    }

    /** Top Sheet에서 위로 스와이프하여 닫기 (스크롤뷰 영역 포함) */
    private fun setupTopSheetSwipeGesture() {
        // 스크롤뷰를 찾아서 스와이프 감지 추가
        val scrollView = topSheet.findViewById<androidx.core.widget.NestedScrollView>(R.id.trackScrollView)
        
        scrollView?.let { sv ->
            var startY = 0f
            var initialTranslationY = 0f
            var isSwiping = false
            
            sv.setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // 스크롤뷰가 최상단에 있을 때만 스와이프 감지
                        if (sv.scrollY == 0) {
                            startY = event.y
                            initialTranslationY = topSheet.translationY
                            isSwiping = false
                            true
                        } else {
                            false
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (sv.scrollY == 0 && startY > 0) {
                            val deltaY = event.y - startY
                            
                            // 위로 스와이프 감지 (음수 deltaY = 위로)
                            if (deltaY < -20) {
                                if (!isSwiping) {
                                    isSwiping = true
                                    // 스크롤뷰 스크롤 방지
                                    view.parent?.requestDisallowInterceptTouchEvent(true)
                                }
                                
                                // Top Sheet를 위로 이동
                                val newY = initialTranslationY + deltaY
                                if (newY <= 0) {
                                    topSheet.translationY = newY.coerceAtLeast(-topSheet.height.toFloat())
                                }
                                true
                            } else {
                                false
                            }
                        } else {
                            false
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                        
                        if (isSwiping) {
                            val deltaY = event.y - startY
                            val currentY = topSheet.translationY
                            val threshold = -topSheet.height * 0.25f
                            
                            // 위로 충분히 스와이프했으면 닫기
                            if (deltaY < -50 || currentY < threshold) {
                                hideTopSheet()
                            } else {
                                // 원래 위치로 복귀
                                val animator = android.animation.ObjectAnimator.ofFloat(
                                    topSheet,
                                    "translationY",
                                    currentY,
                                    0f
                                )
                                animator.duration = 200
                                animator.start()
                            }
                            isSwiping = false
                            startY = 0f
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
        }
    }

    /** Top Sheet 표시 */
    private fun showTopSheet() {
        if (isTopSheetVisible) return
        
        isTopSheetVisible = true
        topSheet.visibility = View.VISIBLE
        
        // View가 측정되기를 기다린 후 애니메이션 시작
        topSheet.post {
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
                // 높이가 측정되지 않은 경우 기본값 사용
                topSheet.translationY = -1000f
                val animator = android.animation.ObjectAnimator.ofFloat(
                    topSheet,
                    "translationY",
                    -1000f,
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

}
