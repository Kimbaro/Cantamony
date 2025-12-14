package com.robsonmartins.androidmidisynth

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.leff.midi.MidiFile
import com.leff.midi.event.NoteOff
import com.leff.midi.event.NoteOn
import com.leff.midi.event.meta.Tempo
import com.leff.midi.event.meta.TimeSignature
import com.robsonmartins.androidmidisynth.dto.CantamonyAlbum
import com.robsonmartins.androidmidisynth.dto.MidiEvent
import com.robsonmartins.androidmidisynth.SynthManager
import com.robsonmartins.androidmidisynth.util.MidiMultiPlayer
import com.robsonmartins.androidmidisynth.util.MusicXmlParser
import com.robsonmartins.androidmidisynth.util.MusicXmlToVexFlowConverter
import com.robsonmartins.androidmidisynth.util.MusicXmlToVexFlowConverter.calculateTotalBeats
import kotlin.math.max

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SELECTED_ALBUM = "SELECTED_ALBUM"
        const val EXTRA_MXL_FILE_PATH = "MXL_FILE_PATH"
        const val EXTRA_MUSICXML_FILE_PATH = "MUSICXML_FILE_PATH"
    }

    private var selectedAlbum: CantamonyAlbum? = null
    private var mxlFileName: String? = null
    private var musicxmlFileName: String? = null
    private var midiFileName: String? = null

    // 로딩 화면
    private lateinit var loadingLayout: View
    private lateinit var loadingProgressBar: ProgressBar
    private lateinit var loadingText: TextView
    
    // MIDI 재생 관련
    private lateinit var synthManager: SynthManager
    private lateinit var midiPlayer: MidiMultiPlayer
    private var midiFile: com.leff.midi.MidiFile? = null

    private val isDebuggableApp: Boolean by lazy {
        (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    // === Phase 3 (B안): tick -> 마디 index 매핑 + OSMD 하이라이트 ===
    private var webViewSheet: WebView? = null
    private var measureDataList: List<MusicXmlToVexFlowConverter.MeasureData> = emptyList()
    private var measureTickRanges: List<MeasureTickRange> = emptyList()
    private var currentMeasureIndex: Int = -1
    private var playbackHandler: Handler? = null
    private var osmdIndexedMeasureCount: Int = -1
    private var didLogHighlightBootstrap: Boolean = false
    private var playbackStartRealtimeMs: Long? = null
    private var playbackStartTick: Long = 0L
    private var currentPlaybackBpm: Double = 120.0

    // === Track group toggles ===
    // 사용자 규칙: trackIndex=1 은 반주, 그 외는 멜로디
    private val accompanimentTrackIndex: Int = 1
    // 단일 버튼: ON = 반주+멜로디, OFF = 반주만
    private var mixAllEnabled: Boolean = true
    private var trackControlsVisible: Boolean = false

    private data class MeasureTickRange(
        val index: Int,
        val measureNumber: String,
        val startTick: Long,
        val endTick: Long,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_main)

        // WebView 원격 디버깅(Chrome DevTools) 활성화: 디버그 빌드에서만
        if (isDebuggableApp) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        // 로딩 화면 초기화
        loadingLayout = findViewById(R.id.loadingLayout)
        loadingProgressBar = findViewById(R.id.loadingProgressBar)
        loadingText = findViewById(R.id.loadingText)
        loadingText.text = "악보를 그리는중 .... 0/0"

        // 로딩 화면 표시
        showLoadingScreen()

        // Intent에서 인자 받기
        receiveIntentData()

        // UI 초기화 및 핵심 기능 구현
        initializeViews()
    }

    /**
     * Intent에서 전달받은 데이터 파싱
     */
    private fun receiveIntentData() {
        selectedAlbum = intent.getSerializableExtra(EXTRA_SELECTED_ALBUM) as? CantamonyAlbum
        mxlFileName = intent.getStringExtra(EXTRA_MXL_FILE_PATH)
        musicxmlFileName = intent.getStringExtra(EXTRA_MUSICXML_FILE_PATH)
        
        // MIDI 파일명 추출
        midiFileName = selectedAlbum?.albumAsset?.get("mid")

        // 로그 출력
        selectedAlbum?.let {
            Log.d("MainActivity", "Selected Album: ${it.albumName}")
            Log.d("MainActivity", "Album Assets: ${it.albumAsset}")
        }
        
        // MIDI 파일 체크
        midiFileName?.let { fileName ->
            Log.d("MainActivity", "MIDI file name found in Intent: $fileName")
            
            // MIDI 파일 존재 여부 확인
            try {
                val inputStream = assets.open(fileName)
                val fileSize = inputStream.available()
                inputStream.close()
                Log.d("MainActivity", "MIDI file exists, size: $fileSize bytes")
            } catch (e: Exception) {
                Log.e("MainActivity", "MIDI file not found or cannot be opened: $fileName", e)
            }
        } ?: run {
            Log.w("MainActivity", "MIDI file name is null in albumAsset")
        }
        
        mxlFileName?.let {
            Log.d("MainActivity", "MXL file name: $it")
        }
        
        musicxmlFileName?.let {
            Log.d("MainActivity", "MusicXML file name received from Intent: $it")
        } ?: run {
            Log.w("MainActivity", "MusicXML file name is null - Intent may not contain EXTRA_MUSICXML_FILE_PATH")
        }
    }

    /**
     * UI 초기화 및 핵심 기능 구현
     */
    private fun initializeViews() {
        // 앨범명 표시
        selectedAlbum?.let { album ->
            findViewById<android.widget.TextView>(R.id.txtAlbumName)?.text = album.albumName
        }

        webViewSheet = findViewById(R.id.webViewSheetMusic)

        // MusicXML 렌더링
        testMusicXmlRendering()
        
        // 확대/축소 버튼 설정
        setupZoomControls()

        // 트랙 그룹 토글(반주/멜로디) UI 설정
        setupTrackGroupToggles()
        
        // MIDI 로드 및 재생 준비
        loadAndPlayMidi()
    }

    private fun setupTrackGroupToggles() {
        val btnTrackList = findViewById<Button>(R.id.btnTrackList)

        val topContainer = findViewById<View>(R.id.trackQuickTogglesTop)
        val sheetZoomRemote = findViewById<View>(R.id.sheetZoomRemote)

        val btnMixTop = findViewById<Button>(R.id.btnMixToggleTop)

        fun setContainersVisible(visible: Boolean) {
            topContainer?.visibility = if (visible) View.VISIBLE else View.GONE
            // sheetZoomRemote(줌 리모컨)는 초기에는 숨김, btnTrackList 선택 시에만 노출
            sheetZoomRemote?.visibility = if (visible) View.VISIBLE else View.GONE
        }

        fun syncUiFromState() {
            val txt = if (mixAllEnabled) "ON" else "OFF"
            val color = if (mixAllEnabled) 0xFF1DB954.toInt() else 0xFF535353.toInt()
            btnMixTop?.text = txt
            btnMixTop?.setBackgroundColor(color)
        }

        val onToggle = View.OnClickListener {
            mixAllEnabled = !mixAllEnabled
            syncUiFromState()
            applyTrackGroupMute()
        }
        btnMixTop?.setOnClickListener(onToggle)

        btnTrackList?.setOnClickListener {
            trackControlsVisible = !trackControlsVisible
            setContainersVisible(trackControlsVisible)
            syncUiFromState()
        }

        // 초기 상태
        setContainersVisible(false)
        syncUiFromState()
    }

    /**
     * 사용자 규칙:
     * - 반주: trackIndex=1
     * - 멜로디: trackIndex!=1 전체
     */
    private fun applyTrackGroupMute() {
        if (!::midiPlayer.isInitialized) return
        val trackCount = midiFile?.trackCount ?: return
        if (trackCount <= 0) return

        // 반주(track 1): 항상 ON
        if (accompanimentTrackIndex in 0 until trackCount) {
            midiPlayer.setMute(accompanimentTrackIndex, mute = false)
        }

        // 멜로디(track 1 제외 전부): mixAllEnabled=false 이면 모두 mute
        val muteMelody = !mixAllEnabled
        for (i in 0 until trackCount) {
            if (i == accompanimentTrackIndex) continue
            midiPlayer.setMute(i, mute = muteMelody)
        }
    }
    
    /**
     * 확대/축소 버튼 설정
     */
    private fun setupZoomControls() {
        val webView = findViewById<WebView>(R.id.webViewSheetMusic)
        
        findViewById<Button>(R.id.btnZoomIn)?.setOnClickListener {
            webView.evaluateJavascript(
                "if (typeof osmd !== 'undefined') {" +
                    "if (typeof cantamonyBeforeRender === 'function') { cantamonyBeforeRender(); }" +
                    "osmd.zoom = (osmd.zoom || 1) * 1.2; osmd.render();" +
                    "setTimeout(function(){ if (typeof cantamonyAfterRender === 'function') { cantamonyAfterRender(); } }, 0);" +
                "}",
                null
            )
        }
        
        findViewById<Button>(R.id.btnZoomOut)?.setOnClickListener {
            webView.evaluateJavascript(
                "if (typeof osmd !== 'undefined') {" +
                    "if (typeof cantamonyBeforeRender === 'function') { cantamonyBeforeRender(); }" +
                    "osmd.zoom = (osmd.zoom || 1) * 0.8; osmd.render();" +
                    "setTimeout(function(){ if (typeof cantamonyAfterRender === 'function') { cantamonyAfterRender(); } }, 0);" +
                "}",
                null
            )
        }
        
        findViewById<Button>(R.id.btnZoomReset)?.setOnClickListener {
            webView.evaluateJavascript(
                "if (typeof osmd !== 'undefined') {" +
                    "if (typeof cantamonyBeforeRender === 'function') { cantamonyBeforeRender(); }" +
                    "osmd.zoom = 1; osmd.render();" +
                    "setTimeout(function(){ if (typeof cantamonyAfterRender === 'function') { cantamonyAfterRender(); } }, 0);" +
                "}",
                null
            )
        }
    }

    /**
     * MusicXML 로드 및 OSMD 렌더링 테스트
     */
    private fun testMusicXmlRendering() {
        val webView = findViewById<WebView>(R.id.webViewSheetMusic)
        
        // WebView 설정
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
        }

        // WebView가 실제로 화면에 렌더링/로드되는지 확인하기 위한 진단 훅
        setupWebViewDiagnostics(webView)
        
        // MusicXML 파일 읽기 (SelectMidiActivity에서 Intent로 전달받은 파일명 사용)
        musicxmlFileName?.let { fileName ->
            Log.d("MainActivity", "Loading MusicXML file from Intent: $fileName")
            try {
                val musicXmlContent = assets.open(fileName).bufferedReader().use { it.readText() }
                Log.d("MainActivity", "MusicXML content loaded, length: ${musicXmlContent.length}")

                // Phase 3 준비: MeasureData 확보
                measureDataList = MusicXmlParser.parseToMeasureData(musicXmlContent)
                Log.d("Phase3", "Parsed MeasureData count=${measureDataList.size}")
                tryBuildMeasureTickRanges()
                
                // JavaScript Interface 생성 및 MusicXML 내용 설정
                val osmdInterface = OSMDInterface(this@MainActivity)
                osmdInterface.setMusicXmlContent(musicXmlContent)
                
                // JavaScript Interface 추가
                webView.addJavascriptInterface(osmdInterface, "OSMDAndroid")
                
                // HTML 파일 로드
                webView.loadUrl("file:///android_asset/osmd_template.html")
                
                Log.d("MainActivity", "OSMD template loaded, waiting for JavaScript to load MusicXML")
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to load MusicXML file: $fileName", e)
            }
        } ?: run {
            Log.w("MainActivity", "MusicXML file name is null - check if Intent contains EXTRA_MUSICXML_FILE_PATH")
        }
    }

    /** 로딩 화면 표시 */
    private fun showLoadingScreen() {
        loadingLayout.visibility = View.VISIBLE
        loadingProgressBar.visibility = View.VISIBLE
        loadingText.visibility = View.VISIBLE
        Log.d("Loading", "Loading screen shown")

        // 디버그 빌드에서: 로딩 화면이 덮고 있어 WebView 렌더링 여부를 못 보는 경우를 대비
        if (isDebuggableApp) {
            loadingLayout.isLongClickable = true
            loadingLayout.setOnLongClickListener {
                Log.w("Loading", "Loading overlay dismissed manually (DEBUG)")
                hideLoadingScreen()
                true
            }
            loadingText.text = "악보를 그리는중... (디버그: 길게 눌러 로딩 화면 숨김)"
        }

        // 일정 시간 이상 로딩이 지속되면 상태를 갱신해 원인 파악을 돕는다
        Handler(Looper.getMainLooper()).postDelayed({
            if (loadingLayout.visibility == View.VISIBLE) {
                val hint = if (isDebuggableApp) {
                    "로딩이 지연됩니다. Logcat에서 WebViewDiag 확인 / (디버그: 길게 눌러 로딩 화면 숨김)"
                } else {
                    "로딩이 지연됩니다. 잠시 후에도 계속되면 다시 시도해주세요."
                }
                loadingText.text = hint
                Log.w("Loading", "Loading overlay still visible after timeout")
            }
        }, 6000)
    }

    /** 로딩 화면 숨기기 */
    private fun hideLoadingScreen() {
        loadingLayout.visibility = View.GONE
        loadingProgressBar.visibility = View.GONE
        loadingText.visibility = View.GONE
        Log.d("Loading", "Loading screen hidden")
    }

    private fun setupWebViewDiagnostics(webView: WebView) {
        // WebView 자체가 레이아웃 상에서 실제 크기를 갖는지(0x0인지)와 로드/에러를 확인
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                Log.d("WebViewDiag", "onPageStarted url=$url")
                if (loadingLayout.visibility == View.VISIBLE) {
                    loadingText.text = "WebView 로딩 시작...\n$url"
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                Log.d("WebViewDiag", "onPageFinished url=$url")
                webView.post {
                    val w = webView.width
                    val h = webView.height
                    Log.d("WebViewDiag", "WebView size=${w}x${h} shown=${webView.isShown} alpha=${webView.alpha}")
                    if (loadingLayout.visibility == View.VISIBLE) {
                        loadingText.text = "WebView 로드 완료\nsize=${w}x${h}\n$url"
                    }
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                val url = request?.url?.toString()
                val desc = error?.description?.toString()
                Log.e("WebViewDiag", "onReceivedError url=$url error=$desc")
                if (loadingLayout.visibility == View.VISIBLE) {
                    loadingText.text = "WebView 에러\n$url\n$desc"
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                val msg = "${consoleMessage.messageLevel()}: ${consoleMessage.message()} " +
                    "(${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})"
                Log.d("WebViewConsole", msg)
                // 치명적인 에러가 보이면 로딩 화면에 표시
                if (loadingLayout.visibility == View.VISIBLE &&
                    (consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.ERROR ||
                        consoleMessage.message().contains("OSMD load error", ignoreCase = true))
                ) {
                    loadingText.text = "WebView 콘솔 에러\n${consoleMessage.message()}"
                }
                return super.onConsoleMessage(consoleMessage)
            }
        }
    }
    
    /**
     * MIDI 로드 및 재생 준비
     */
    private fun loadAndPlayMidi() {
        midiFileName?.let { fileName ->
            Log.d("MainActivity", "Loading MIDI file: $fileName")
            
            try {
                // 1. MIDI 파일 로드
                val inputStream = assets.open(fileName)
                midiFile = com.leff.midi.MidiFile(inputStream)
                
                // 2. MIDI 파일 정보 출력
                Log.d("MainActivity", "MIDI Resolution: ${midiFile?.resolution}")
                Log.d("MainActivity", "MIDI Track Count: ${midiFile?.trackCount}")
                Log.d("MainActivity", "MIDI Total Ticks: ${midiFile?.lengthInTicks}")

                // Phase 3 준비: MIDI가 준비되면 매핑 생성
                tryBuildMeasureTickRanges()
                
                // 3. 템포 추출
                val tempoChanges = extractTempoChanges(midiFile!!)
                val initialBPM = tempoChanges.firstOrNull { it.tick == 0L }?.bpm ?: 120.0
                run {
                    val count = tempoChanges.size
                    val hasTick0 = tempoChanges.any { it.tick == 0L }
                    val distinctCount = tempoChanges
                        .map { it.tick to it.bpm }
                        .distinct()
                        .size
                    val hasNonZeroTick = tempoChanges.any { it.tick > 0L }
                    val sample = tempoChanges.take(5).joinToString { "(${it.tick},${it.bpm})" }
                    Log.d(
                        "TempoMap",
                        "tempoChanges count=$count distinct=$distinctCount hasTick0=$hasTick0 hasNonZeroTick=$hasNonZeroTick sample=$sample"
                    )
                }
                Log.d("MainActivity", "Initial BPM: $initialBPM")
                
                // 4. MidiEvent로 변환
                val trackEventsMap = parseMidiFileToEvents(midiFile!!)
                Log.d("MainActivity", "Parsed ${trackEventsMap.size} tracks")
                
                // 5. SynthManager 및 MidiMultiPlayer 초기화
                synthManager = SynthManager(this)
                // Synth 초기화
                synthManager.loadSoundFont("KawaiStereoGrand.sf3")
                midiPlayer = MidiMultiPlayer(synthManager)
                // PPQ(resolution) 주입: 480 하드코딩 제거
                midiPlayer.setTicksPerQuarter(midiFile?.resolution ?: 480)
                // Tempo map 주입: 파일 템포 변화 + 사용자 BPM(scale) 기반 스케줄링/SyncCheck에 사용
                midiPlayer.setTempoPoints(
                    tempoChanges.map { MidiMultiPlayer.TempoPoint(it.tick, it.bpm) },
                    baseBpmFallback = 120.0
                )
                
                // 6. 트랙 추가
                trackEventsMap.forEach { (trackIndex, events) ->
                    midiPlayer.addTrack(events, trackIndex)
                    Log.d("MainActivity", "Track $trackIndex: ${events.size} events")
                }
                
                // 7. BPM 설정
                midiPlayer.setBPM(initialBPM)
                currentPlaybackBpm = initialBPM

                // 7.1 트랙 그룹 mute 상태 반영(토글이 열려있든 아니든 현재 상태 적용)
                applyTrackGroupMute()
                
                // 8. 재생 버튼 연동
                setupPlaybackControls()
                
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to load MIDI file: $fileName", e)
            }
        } ?: run {
            Log.w("MainActivity", "MIDI file name is null - skipping MIDI load")
        }
    }
    
    /**
     * MIDI 파일을 앱의 MidiEvent 리스트로 변환
     */
    private fun parseMidiFileToEvents(midiFile: com.leff.midi.MidiFile): Map<Int, List<MidiEvent>> {
        val trackEventsMap = mutableMapOf<Int, MutableList<MidiEvent>>()
        
        midiFile.tracks.forEachIndexed { trackIndex, track ->
            val events = mutableListOf<MidiEvent>()
            
            track.events.forEach { midiEvent ->
                when (midiEvent) {
                    is NoteOn -> {
                        // velocity > 0 이면 Note On
                        if (midiEvent.velocity > 0) {
                            events.add(
                                MidiEvent(
                                    tick = midiEvent.tick,
                                    note = midiEvent.noteValue,
                                    velocity = midiEvent.velocity,
                                    isNoteOn = true,
                                    trackIndex = trackIndex
                                )
                            )
                        } else {
                            // velocity = 0 은 Note Off로 취급
                            events.add(
                                MidiEvent(
                                    tick = midiEvent.tick,
                                    note = midiEvent.noteValue,
                                    velocity = 0,
                                    isNoteOn = false,
                                    trackIndex = trackIndex
                                )
                            )
                        }
                    }
                    is NoteOff -> {
                        events.add(
                            MidiEvent(
                                tick = midiEvent.tick,
                                note = midiEvent.noteValue,
                                velocity = 0,
                                isNoteOn = false,
                                trackIndex = trackIndex
                            )
                        )
                    }
                }
            }
            
            trackEventsMap[trackIndex] = events
        }
        
        return trackEventsMap
    }
    
    /**
     * 템포 변경 데이터 클래스
     */
    data class TempoChange(
        val tick: Long,
        val bpm: Double
    )
    
    /**
     * MIDI 파일에서 템포 이벤트 추출
     */
    private fun extractTempoChanges(midiFile: MidiFile): List<TempoChange> {
        val tempoChanges = mutableListOf<TempoChange>()
        
        midiFile.tracks.forEach { track ->
            track.events.forEach { event ->
                if (event is Tempo) {
                    tempoChanges.add(
                        TempoChange(
                            tick = event.tick,
                            bpm = event.bpm.toDouble()
                        )
                    )
                }
            }
        }
        
        return tempoChanges.sortedBy { it.tick }
    }

    private fun tryBuildMeasureTickRanges() {
        val mf = midiFile ?: return
        if (measureDataList.isEmpty()) {
            // MusicXML 파싱이 실패/미구현이어도 B안 진행을 위해 OSMD 인덱싱 개수 기반으로 fallback 매핑을 만든다.
            val osmdCount = osmdIndexedMeasureCount
            if (osmdCount > 0) {
                buildMeasureTickRangesFallback(osmdCount)
            } else {
                Log.w("Phase3", "MeasureData is empty and OSMD count not ready yet (skip ranges)")
            }
            return
        }

        val ticksPerQuarter = mf.resolution.coerceAtLeast(1)

        fun durationToQuarters(duration: String): Double = when (duration) {
            "w", "wr" -> 4.0
            "h", "hr" -> 2.0
            "q", "qr" -> 1.0
            "8", "8r" -> 0.5
            "16", "16r" -> 0.25
            "32", "32r" -> 0.125
            else -> 1.0
        }

        fun notesQuarters(measure: MusicXmlToVexFlowConverter.MeasureData, bass: Boolean): Double {
            val notes = if (bass) measure.notesBass else measure.notes
            return notes.sumOf { durationToQuarters(it.duration) }
        }

        fun nominalMeasureQuarters(measure: MusicXmlToVexFlowConverter.MeasureData): Double {
            val (num, den) = measure.timeSignature
            return num.toDouble() * (4.0 / den.toDouble())
        }

        var curTick = 0L
        val ranges = mutableListOf<MeasureTickRange>()

        measureDataList.forEachIndexed { idx, m ->
            val nominal = nominalMeasureQuarters(m)
            val treble = notesQuarters(m, bass = false)
            val bass = notesQuarters(m, bass = true)
            val actual = max(treble, bass)

            // 픽업/불완전 마디는 실제 길이 기반이 더 안전
            val quarters = if (m.measureNumber == "0") {
                // MusicXML 파싱 성공 시엔 실제 노트 합을 우선(박자와 다를 수 있음)
                val beats = runCatching { calculateTotalBeats(m) }.getOrNull()
                val quartersFromBeats = beats ?: actual
                if (quartersFromBeats > 0.0) quartersFromBeats else nominal
            } else {
                nominal
            }

            val lenTicks = (quarters * ticksPerQuarter).toLong().coerceAtLeast(1L)
            val start = curTick
            val end = curTick + lenTicks
            ranges += MeasureTickRange(idx, m.measureNumber, start, end)
            curTick = end
        }

        measureTickRanges = ranges
        Log.d("Phase3", "MeasureTickRanges built size=${measureTickRanges.size} (ticksPerQuarter=$ticksPerQuarter)")

        // SeekBar를 “마디 index” 기반으로 쓰는 게 구현이 단순
        findViewById<SeekBar>(R.id.seekBarMeasure)?.apply {
            max = (measureTickRanges.size - 1).coerceAtLeast(0)
        }

        // 초기 화면 표기: 재생 전에도 "현재/전체"가 보이도록
        val total = measureTickRanges.size
        findViewById<TextView>(R.id.txtMeasure)?.text =
            if (total > 0) "마디: 1 / $total" else "마디: 0 / 0"
    }

    private fun startPlaybackTracking() {
        playbackHandler?.removeCallbacksAndMessages(null)
        playbackHandler = Handler(Looper.getMainLooper())

        val r = object : Runnable {
            override fun run() {
                if (!::midiPlayer.isInitialized || measureTickRanges.isEmpty()) {
                    playbackHandler?.postDelayed(this, 100)
                    return
                }

                if (midiPlayer.isPlaying()) {
                    // 재생 시작 기준(벽시계) 기록: 싱크 드리프트 산출용
                    if (playbackStartRealtimeMs == null) {
                        playbackStartRealtimeMs = SystemClock.elapsedRealtime()
                        playbackStartTick = midiPlayer.getCurrentTick()
                    }
                    val tick = midiPlayer.getCurrentTick()
                    val idx = measureTickRanges.indexOfFirst { tick >= it.startTick && tick < it.endTick }
                    if (idx >= 0 && idx != currentMeasureIndex) {
                        currentMeasureIndex = idx

                        // UI
                        val displayIndex = idx + 1
                        findViewById<TextView>(R.id.txtMeasure)?.text =
                            "마디: $displayIndex / ${measureTickRanges.size}"
                        findViewById<SeekBar>(R.id.seekBarMeasure)?.progress = idx

                        // WebView(OSMD) 하이라이트 (B안 핵심)
                        highlightMeasureIndexInWebView(idx)

                        // === 싱크 검증 로그 ===
                        val startMs = playbackStartRealtimeMs ?: SystemClock.elapsedRealtime()
                        val elapsedMs = SystemClock.elapsedRealtime() - startMs
                        val ppq = (midiFile?.resolution ?: 480).coerceAtLeast(1)
                        // 현재 플레이어는 bpm을 고정 사용(템포 변경 미반영). 따라서 "플레이어 기준 expected"로 드리프트를 확인
                        val expectedMs = if (::midiPlayer.isInitialized) {
                            midiPlayer.ticksToMs(playbackStartTick, tick)
                        } else {
                            (((tick - playbackStartTick).toDouble()) * (60000.0 / (currentPlaybackBpm * ppq))).toLong()
                        }
                        val driftMs = elapsedMs - expectedMs
                        val playerBpmAtTick = if (::midiPlayer.isInitialized) midiPlayer.getEffectiveBpmAtTick(tick) else currentPlaybackBpm
                        Log.d(
                            "SyncCheck",
                            "measureIndex=$idx measureNo=${measureTickRanges[idx].measureNumber} tick=$tick " +
                                "uiBpm=$currentPlaybackBpm playerBpm=$playerBpmAtTick baseTempoBpm=${if (::midiPlayer.isInitialized) midiPlayer.getBaseTempoBpm() else -1.0} " +
                                "hasTempoMap=${if (::midiPlayer.isInitialized) midiPlayer.hasTempoMap() else false} " +
                                "ppq=$ppq elapsedMs=$elapsedMs expectedMs=$expectedMs driftMs=$driftMs"
                        )
                    }
                    playbackHandler?.postDelayed(this, 50)
                } else {
                    // 정지 상태가 되면 다음 재생을 위해 초기화
                    playbackStartRealtimeMs = null
                    playbackHandler?.postDelayed(this, 100)
                }
            }
        }
        playbackHandler?.post(r)
    }

    // NOTE: expectedMs 계산은 반드시 실제 재생에 적용된 BPM(currentPlaybackBpm)을 사용해야 의미가 있습니다.

    private fun buildMeasureTickRangesFallback(osmdCount: Int) {
        val mf = midiFile ?: return
        // 가능한 한 MIDI 길이를 기준으로 균등 분할 (정밀도는 낮지만 Phase3 검증/하이라이트 루프는 가능)
        val totalTicks = runCatching { mf.lengthInTicks }.getOrNull()?.toLong()
            ?: runCatching { midiPlayer.getTotalTicks() }.getOrNull()
            ?: 0L

        if (totalTicks <= 0L) {
            Log.w("Phase3", "Fallback ranges skipped: totalTicks=$totalTicks")
            return
        }

        val count = osmdCount.coerceAtLeast(1)
        val ranges = mutableListOf<MeasureTickRange>()
        for (i in 0 until count) {
            val start = (totalTicks * i) / count
            val end = (totalTicks * (i + 1)) / count
            ranges += MeasureTickRange(
                index = i,
                measureNumber = (i + 1).toString(),
                startTick = start,
                endTick = maxOf(end, start + 1)
            )
        }

        measureTickRanges = ranges
        Log.w("Phase3", "MeasureTickRanges FALLBACK built size=${measureTickRanges.size} totalTicks=$totalTicks (osmdCount=$osmdCount)")

        findViewById<SeekBar>(R.id.seekBarMeasure)?.apply {
            max = (measureTickRanges.size - 1).coerceAtLeast(0)
        }

        // 초기 화면 표기: 재생 전에도 "현재/전체"가 보이도록
        val total = measureTickRanges.size
        findViewById<TextView>(R.id.txtMeasure)?.text =
            if (total > 0) "마디: 1 / $total" else "마디: 0 / 0"
    }

    private fun highlightMeasureIndexInWebView(index: Int) {
        val webView = webViewSheet ?: return
        val count = osmdIndexedMeasureCount
        val safeIndex = if (count > 0) index.coerceIn(0, count - 1) else index
        if (count > 0 && safeIndex != index) {
            Log.w("Phase3", "Clamp highlight index $index -> $safeIndex (osmdCount=$count)")
        }
        if (isDebuggableApp && !didLogHighlightBootstrap) {
            didLogHighlightBootstrap = true
            webView.evaluateJavascript("typeof highlightMeasureIndex", null)
        }
        // 요구사항: 마디 단위가 아니라 "한 줄(staffline) 단위" 하이라이트
        webView.evaluateJavascript("if (typeof highlightLineOfMeasureIndex === 'function') { highlightLineOfMeasureIndex($safeIndex); } else { highlightMeasureIndex($safeIndex); }", null)
    }

    /**
     * 재생 컨트롤 버튼 연동
     */
    private fun setupPlaybackControls() {
        val btnPlay = findViewById<Button>(R.id.btnPlayAll)
        val btnStop = findViewById<Button>(R.id.btnStopAll)
        val seekBar = findViewById<SeekBar>(R.id.seekBarMeasure)
        val btnPrev = findViewById<Button>(R.id.btnPrevMeasure)
        val btnNext = findViewById<Button>(R.id.btnNextMeasure)
        
        btnPlay?.setOnClickListener {
            if (::midiPlayer.isInitialized) {
                if (midiPlayer.isPlaying()) {
                    midiPlayer.stopAll()
                    btnPlay.text = "▶"
                    playbackHandler?.removeCallbacksAndMessages(null)
                    // 싱크 검증 기준점 초기화 (stop 시 handler가 더 이상 돌지 않아 자동 초기화가 안 될 수 있음)
                    playbackStartRealtimeMs = null
                    playbackStartTick = 0L
                } else {
                    // 새 재생 시작 시 기준점 초기화 (이전 재생의 값이 남아 driftMs가 상수로 커지는 현상 방지)
                    playbackStartRealtimeMs = null
                    playbackStartTick = 0L
                    midiPlayer.startAll()
                    btnPlay.text = "⏸"
                    startPlaybackTracking() // Phase 3
                }
            }
        }
        
        btnStop?.setOnClickListener {
            if (::midiPlayer.isInitialized) {
                midiPlayer.stopAll()
                midiPlayer.reset()
                btnPlay?.text = "▶"
                playbackHandler?.removeCallbacksAndMessages(null)
                // 싱크 검증 기준점 초기화
                playbackStartRealtimeMs = null
                playbackStartTick = 0L
            }
        }

        // SeekBar를 마디 index로 사용
        seekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                if (!::midiPlayer.isInitialized || measureTickRanges.isEmpty()) return
                val target = measureTickRanges.getOrNull(progress) ?: return
                midiPlayer.seekTo(target.startTick)
                highlightMeasureIndexInWebView(target.index)
                findViewById<TextView>(R.id.txtMeasure)?.text =
                    "마디: ${progress + 1} / ${measureTickRanges.size}"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        btnPrev?.setOnClickListener {
            if (!::midiPlayer.isInitialized || measureTickRanges.isEmpty()) return@setOnClickListener
            val nextIndex = (seekBar?.progress ?: 0) - 1
            val target = measureTickRanges.getOrNull(nextIndex) ?: return@setOnClickListener
            seekBar?.progress = target.index
            midiPlayer.seekTo(target.startTick)
            highlightMeasureIndexInWebView(target.index)
        }

        btnNext?.setOnClickListener {
            if (!::midiPlayer.isInitialized || measureTickRanges.isEmpty()) return@setOnClickListener
            val nextIndex = (seekBar?.progress ?: 0) + 1
            val target = measureTickRanges.getOrNull(nextIndex) ?: return@setOnClickListener
            seekBar?.progress = target.index
            midiPlayer.seekTo(target.startTick)
            highlightMeasureIndexInWebView(target.index)
        }
        
        // BPM 조절
        val seekBarBPM = findViewById<SeekBar>(R.id.seekBarBPM)
        val txtBPM = findViewById<TextView>(R.id.txtBPM)
        
        seekBarBPM?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && ::midiPlayer.isInitialized) {
                    val bpm = progress.toDouble()
                    midiPlayer.setBPM(bpm)
                    currentPlaybackBpm = bpm
                    txtBPM?.text = "BPM: $progress"

                    // SyncCheck 기준점도 BPM 변경 시점으로 재설정 (expected/elapsed 일치)
                    playbackStartRealtimeMs = SystemClock.elapsedRealtime()
                    playbackStartTick = midiPlayer.getCurrentTick()
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    /**
     * JavaScript Interface for OSMD
     */
    class OSMDInterface(private val mainActivity: MainActivity) {
        private var musicXmlContent: String? = null
        
        fun setMusicXmlContent(content: String) {
            musicXmlContent = content
        }
        
        @JavascriptInterface
        fun getMusicXmlContent(): String {
            return musicXmlContent ?: ""
        }
        
        @JavascriptInterface
        fun onOSMDReady() {
            // OSMD 렌더링 완료 시 호출
            Log.d("MainActivity", "OSMD ready")
            
            // 메인 스레드에서 로딩 화면 숨기기
            mainActivity.runOnUiThread {
                mainActivity.hideLoadingScreen()

                // OSMD가 인덱싱한 마디 개수 읽기 + 초기 하이라이트 테스트
                mainActivity.webViewSheet?.evaluateJavascript(
                    "typeof getCantamonyMeasureCount === 'function' ? getCantamonyMeasureCount() : -1;"
                ) { value ->
                    val count = value?.trim()?.trim('"')?.toIntOrNull() ?: -1
                    mainActivity.osmdIndexedMeasureCount = count
                    Log.d("Phase3", "OSMD indexed measure count=$count")
                    // OSMD count가 확보되면, MusicXML 파싱이 0이어도 fallback 매핑을 만들 수 있다.
                    mainActivity.tryBuildMeasureTickRanges()
                    if (count > 0) {
                        mainActivity.highlightMeasureIndexInWebView(0)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        playbackHandler?.removeCallbacksAndMessages(null)
        playbackHandler = null
        super.onDestroy()
    }
}

