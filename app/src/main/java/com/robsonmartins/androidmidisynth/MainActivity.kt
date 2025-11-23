package com.robsonmartins.androidmidisynth

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
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
import com.leff.midi.event.meta.TimeSignature
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

    private var initialBPM = 120

    // 마디 정보
    private var totalMeasures = 0
    private var ticksPerMeasure = 0L
    private var midiResolution = 480
    private var timeSignature: TimeSignature? = null

    // 모든 MIDI 이벤트 저장 (악보 렌더링용)
    private val allMidiEvents = mutableListOf<MidiEvent>()

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
            handler.postDelayed(this, 100) // 100ms마다 업데이트
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // View 초기화
        txtAlbumName = findViewById(R.id.txtAlbumName)
        buttonContainer = findViewById(R.id.buttonContainer)
        btnPlayAll = findViewById(R.id.btnPlayAll)
        btnStopAll = findViewById(R.id.btnStopAll)
        txtBPM = findViewById(R.id.txtBPM)
        seekBarBPM = findViewById(R.id.seekBarBPM)
        txtMeasure = findViewById(R.id.txtMeasure)
        seekBarMeasure = findViewById(R.id.seekBarMeasure)
        btnPrevMeasure = findViewById(R.id.btnPrevMeasure)
        btnNextMeasure = findViewById(R.id.btnNextMeasure)
        imgSheetMusic = findViewById(R.id.imgSheetMusic)

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
        for ((index, path) in midiFiles.withIndex()) {
            assets.open(path).use { inputStream ->
                val midiFile = MidiFile(inputStream)

                // 첫 번째 파일에서만 TimeSignature와 Resolution 추출
                if (index == 0) {
                    timeSignature = extractTimeSignature(midiFile)
                    midiResolution = midiFile.getResolution()

                    val numerator = timeSignature?.getNumerator() ?: 4
                    ticksPerMeasure = (midiResolution * numerator).toLong()
                }

                // MidiFile 객체에서 직접 이벤트 파싱
                val trackEvents = parseMidiFileFromMidiFile(midiFile)
                trackEvents.forEach { events ->
                    multiPlayer.addTrack(events, index)
                    allMidiEvents.addAll(events) // 모든 이벤트 저장
                    events.maxOfOrNull { it.tick }?.let { tick ->
                        if (tick > maxTick) maxTick = tick
                    }
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

        // 트랙별 ON/OFF 버튼 생성
        midiFiles.forEachIndexed { index, path ->
            val btn = Button(this).apply {
                text = "ON ${path}"
                setOnClickListener {
                    val currentlyMuted = multiPlayer.getMuteTracks()[index] ?: false
                    multiPlayer.setMute(index, !currentlyMuted)
                    text = if (!currentlyMuted) "OFF ${path}" else "ON ${path}"
                }
            }
            buttonContainer.addView(btn)
        }

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
        Log.d("SheetMusic", "onMeasureChanged called for measure $measure, lastUploaded: $lastUploadedMeasure, isRendering: $isRendering")
        
        // 마디가 변경되지 않았으면 무시
        if (measure == lastUploadedMeasure) {
            Log.d("SheetMusic", "Skipping render for measure $measure (same as last uploaded)")
            return
        }
        
        // 진행 중이면 이전 작업 취소하고 새 마디로 즉시 렌더링 시작
        if (isRendering) {
            Log.d("SheetMusic", "Cancelling previous render job and starting new render for measure $measure")
            currentRenderJob?.cancel()
            isRendering = false // 플래그 리셋
        }

        // 즉시 실행
        Log.d("SheetMusic", "Starting render for measure $measure (different from last: $lastUploadedMeasure)")
        pendingMeasure = -1
        renderAndUploadSheetMusic(measure)
    }

    /** 악보 렌더링 및 서버 업로드 */
    private fun renderAndUploadSheetMusic(measure: Int) {
        // 진행 중 플래그 설정
        isRendering = true

        currentRenderJob = uploadScope.launch {
            try {
                val measureEvents = getEventsByMeasure(measure)
                Log.d("SheetMusic", "Rendering measure $measure with ${measureEvents.size} events")

                // 렌더링 완료 대기용 Deferred
                val renderComplete = CompletableDeferred<Boolean>()

                // WebView 로드 완료 대기
                val loadComplete = CompletableDeferred<Boolean>()

                // WebView 생성 및 설정을 메인 스레드에서 수행
                val webView = withContext(Dispatchers.Main) {
                    // WebView 생성 (메인 스레드에서만 가능)
                    val webView = createVexFlowWebView(measureEvents, measure, renderComplete)

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
                            android.widget.FrameLayout.LayoutParams(800, 400)
                        } else {
                            android.view.ViewGroup.LayoutParams(800, 400)
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
                            400,
                            android.view.View.MeasureSpec.EXACTLY
                        )
                    )
                    webView.layout(0, 0, 800, 400)
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
                val base64Image = bitmapToBase64(bitmap)
                Log.d("SheetMusic", "Base64 image size: ${base64Image.length} bytes")

                // 화면에 악보 이미지 표시
                withContext(Dispatchers.Main) {
                    imgSheetMusic.setImageBitmap(bitmap)
                    Log.d("SheetMusic", "Image set to ImageView for measure $measure")
                }

                if (false) { //현재는 disable
                    // 서버에 업로드
                    uploadSheetMusicToServer(base64Image, measure)
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
        events: List<MidiEvent>,
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

        val html = generateVexFlowHTML(events, measure)
        webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null)

        return webView
    }

    /** VexFlow HTML 생성 */
    private fun generateVexFlowHTML(events: List<MidiEvent>, measure: Int): String {
        val noteOnEvents = events.filter { it.isNoteOn }
        Log.d("SheetMusic", "Found ${noteOnEvents.size} NoteOn events for measure $measure")
        
        // 같은 시간(tick)에 연주되는 노트들을 그룹화하여 화음으로 만들기
        val notesByTick = noteOnEvents.groupBy { it.tick }.toSortedMap()
        
        // 시간 서명 정보 가져오기
        val numerator = timeSignature?.getNumerator() ?: 4
        val denominator = timeSignature?.getRealDenominator() ?: 4
        
        // 각 시간 그룹에서 노트들을 화음으로 변환하고 duration 계산
        val chordNotes = mutableListOf<Map<String, Any>>()
        var previousTick: Long? = null
        
        notesByTick.forEach { (tick, tickEvents) ->
            // 바이올린 음역대: G3 (MIDI 55) ~ E7 (MIDI 100)
            // 트레블 클레프에 적합한 범위로 필터링
            // 범위를 약간 넓혀서 C3 (48) ~ E7 (100)로 설정 (바이올린의 확장 범위 포함)
            val validNotes = tickEvents
                .filter { it.note in 48..100 } // 바이올린 확장 음역대 (C3 ~ E7)
                .map { midiNoteToVexFlowNote(it.note) }
                .sorted() // 정렬하여 일관성 유지
            
            Log.d("SheetMusic", "Tick $tick: ${tickEvents.size} events, ${validNotes.size} valid notes after filtering")
            
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
                        octave * 12 + when(noteName.lowercase()) {
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
                        octave * 12 + when(noteName.lowercase()) {
                            "c" -> 0; "c#" -> 1; "d" -> 2; "d#" -> 3; "e" -> 4
                            "f" -> 5; "f#" -> 6; "g" -> 7; "g#" -> 8; "a" -> 9; "a#" -> 10; "b" -> 11
                            else -> 0
                        }
                    } ?: validNotes.first())
                }
                
                if (finalNotes.isNotEmpty()) {
                    // duration 계산: 이전 tick과의 차이를 기반으로
                    val prevTick = previousTick
                    val duration = if (prevTick != null && tick > prevTick) {
                        val tickDiff = tick - prevTick
                        // tick을 duration으로 변환 (480 ticks = quarter note, midiResolution 기준)
                        val resolution = midiResolution.toLong()
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
                    
                    Log.d("SheetMusic", "Tick: $tick, prevTick: $prevTick, duration: $duration")
                    
                    // 첫 번째 노트만 사용 (단일 노트로 표시)
                    chordNotes.add(mapOf(
                        "keys" to listOf(finalNotes.first()),
                        "duration" to duration,
                        "tick" to tick
                    ))
                    
                    previousTick = tick
                }
            }
        }
        
        Log.d("SheetMusic", "Grouped into ${chordNotes.size} chords from ${noteOnEvents.size} events")
        if (chordNotes.isEmpty()) {
            Log.w("SheetMusic", "No valid notes found after filtering! Check note range.")
        }

        val notesJson = if (chordNotes.isEmpty()) {
            Log.w("SheetMusic", "No valid notes found for measure $measure, using placeholder")
            "new VF.StaveNote({ clef: 'treble', keys: ['b/4'], duration: 'w' })"
        } else {
            // 마디 내의 노트들을 시간 순서대로 정렬하여 표시
            // 계산된 duration 값을 사용하여 다양한 음표 길이 표시
            // 최대 8개 노트만 표시 (한 마디에 적합한 수)
            val notesToRender = chordNotes.take(8)
            notesToRender.joinToString(",\n                ") { chord ->
                val keysStr = chord["keys"] as List<String>
                val duration = chord["duration"] as String  // 계산된 duration 사용
                // 첫 번째 노트만 사용 (화음이 아닌 단일 노트로)
                val firstKey = keysStr.firstOrNull() ?: "c/4"
                "new VF.StaveNote({ clef: 'treble', keys: ['$firstKey'], duration: '$duration' })"
            }
        }
        
        // 렌더링할 노트들의 총 박자 수 계산 (각 노트의 duration을 합산)
        val notesToRender = chordNotes.take(8)
        val totalBeats = notesToRender.sumOf { chord ->
            when (chord["duration"] as String) {
                "w" -> 4.0
                "h" -> 2.0
                "q" -> 1.0
                "8" -> 0.5
                "16" -> 0.25
                "32" -> 0.125
                else -> 1.0
            }
        }
        
        Log.d("SheetMusic", "Total beats calculated: $totalBeats, time signature: $numerator/$denominator, notes to render: ${notesToRender.size}")
        Log.d("SheetMusic", "Generated notes JSON (first 500 chars): ${notesJson.take(500)}")
        
        // HTML에 totalBeats와 time signature 정보 전달
        return generateVexFlowHTMLWithBeats(notesJson, totalBeats, numerator, denominator)
    }
    
    /** VexFlow HTML 생성 (박자 정보 포함) */
    private fun generateVexFlowHTMLWithBeats(notesJson: String, totalBeats: Double, numerator: Int, denominator: Int): String {
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
                        stave.addClef("treble").addTimeSignature("$numerator/$denominator");
                        stave.setContext(context).draw();
                        log('Stave drawn with time signature: $numerator/$denominator at (10, 50)');
                        
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
                                'h': 1/2,    // half note = 1/2
                                'q': 1/4,    // quarter note = 1/4
                                '8': 1/8,    // eighth note = 1/8
                                '16': 1/16,  // sixteenth note = 1/16
                                '32': 1/32   // thirty-second note = 1/32
                            };
                            
                            notes.forEach(function(note) {
                                try {
                                    var duration = note.getDuration();
                                    var fraction = durationFractionMap[duration] || 1/4; // 기본값: quarter note
                                    beatAccumulator += fraction;
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
        
        Log.d("SheetMusic", "Getting events for measure $measure: tick range [$startTick, $endTick)")
        Log.d("SheetMusic", "Total events in allMidiEvents: ${allMidiEvents.size}")

        val filtered = allMidiEvents.filter { event ->
            event.tick >= startTick && event.tick < endTick
        }.sortedBy { it.tick }
        
        Log.d("SheetMusic", "Filtered events count: ${filtered.size}")
        filtered.forEach { event ->
            Log.d("SheetMusic", "  Event: tick=${event.tick}, note=${event.note}, isNoteOn=${event.isNoteOn}")
        }
        
        return filtered
    }

    /** 이전 마디로 이동 */
    private fun moveToPreviousMeasure() {
        val currentMeasure = tickToMeasure(multiPlayer.getCurrentTick())
        if (currentMeasure > 1) {
            val targetMeasure = currentMeasure - 1
            val targetTick = measureToTick(targetMeasure)
            multiPlayer.seekTo(targetTick)
            updateMeasureDisplay(targetMeasure)
            seekBarMeasure.progress = (targetMeasure - 1).coerceAtLeast(0)

            // 마디 변경 이벤트 발생
            onMeasureChanged(targetMeasure)

            // 재생 중이면 seek 후 계속 재생
            val wasPlaying = multiPlayer.isPlaying()
            if (wasPlaying) {
                multiPlayer.stopAll()
            }
            multiPlayer.startAll()
            if (wasPlaying) {
                handler.post(updateRunnable)
            }
        }
    }

    /** 다음 마디로 이동 */
    private fun moveToNextMeasure() {
        val currentMeasure = tickToMeasure(multiPlayer.getCurrentTick())
        if (currentMeasure < totalMeasures) {
            val targetMeasure = currentMeasure + 1
            val targetTick = measureToTick(targetMeasure)
            multiPlayer.seekTo(targetTick)
            updateMeasureDisplay(targetMeasure)
            seekBarMeasure.progress = targetMeasure - 1

            // 마디 변경 이벤트 발생
            onMeasureChanged(targetMeasure)

            // 재생 중이면 seek 후 계속 재생
            val wasPlaying = multiPlayer.isPlaying()
            if (wasPlaying) {
                multiPlayer.stopAll()
            }
            multiPlayer.startAll()
            if (wasPlaying) {
                handler.post(updateRunnable)
            }
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

}
