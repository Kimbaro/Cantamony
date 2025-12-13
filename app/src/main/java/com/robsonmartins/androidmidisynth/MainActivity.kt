package com.robsonmartins.androidmidisynth

import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_main)

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

        // MusicXML 렌더링
        testMusicXmlRendering()
        
        // 확대/축소 버튼 설정
        setupZoomControls()
        
        // MIDI 로드 및 재생 준비
        loadAndPlayMidi()
    }
    
    /**
     * 확대/축소 버튼 설정
     */
    private fun setupZoomControls() {
        val webView = findViewById<WebView>(R.id.webViewSheetMusic)
        
        findViewById<Button>(R.id.btnZoomIn)?.setOnClickListener {
            webView.evaluateJavascript("if (typeof osmd !== 'undefined') { osmd.zoom = (osmd.zoom || 1) * 1.2; osmd.render(); }", null)
        }
        
        findViewById<Button>(R.id.btnZoomOut)?.setOnClickListener {
            webView.evaluateJavascript("if (typeof osmd !== 'undefined') { osmd.zoom = (osmd.zoom || 1) * 0.8; osmd.render(); }", null)
        }
        
        findViewById<Button>(R.id.btnZoomReset)?.setOnClickListener {
            webView.evaluateJavascript("if (typeof osmd !== 'undefined') { osmd.zoom = 1; osmd.render(); }", null)
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
        
        // MusicXML 파일 읽기 (SelectMidiActivity에서 Intent로 전달받은 파일명 사용)
        musicxmlFileName?.let { fileName ->
            Log.d("MainActivity", "Loading MusicXML file from Intent: $fileName")
            try {
                val musicXmlContent = assets.open(fileName).bufferedReader().use { it.readText() }
                Log.d("MainActivity", "MusicXML content loaded, length: ${musicXmlContent.length}")
                
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
    }

    /** 로딩 화면 숨기기 */
    private fun hideLoadingScreen() {
        loadingLayout.visibility = View.GONE
        loadingProgressBar.visibility = View.GONE
        loadingText.visibility = View.GONE
        Log.d("Loading", "Loading screen hidden")
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
                
                // 3. 템포 추출
                val tempoChanges = extractTempoChanges(midiFile!!)
                val initialBPM = tempoChanges.firstOrNull()?.bpm ?: 120.0
                Log.d("MainActivity", "Initial BPM: $initialBPM")
                
                // 4. MidiEvent로 변환
                val trackEventsMap = parseMidiFileToEvents(midiFile!!)
                Log.d("MainActivity", "Parsed ${trackEventsMap.size} tracks")
                
                // 5. SynthManager 및 MidiMultiPlayer 초기화
                synthManager = SynthManager(this)
                // Synth 초기화
                synthManager.loadSoundFont("KawaiStereoGrand.sf3")
                midiPlayer = MidiMultiPlayer(synthManager)
                
                // 6. 트랙 추가
                trackEventsMap.forEach { (trackIndex, events) ->
                    midiPlayer.addTrack(events, trackIndex)
                    Log.d("MainActivity", "Track $trackIndex: ${events.size} events")
                }
                
                // 7. BPM 설정
                midiPlayer.setBPM(initialBPM)
                
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

    /**
     * 재생 컨트롤 버튼 연동
     */
    private fun setupPlaybackControls() {
        val btnPlay = findViewById<Button>(R.id.btnPlayAll)
        val btnStop = findViewById<Button>(R.id.btnStopAll)
        val seekBar = findViewById<SeekBar>(R.id.seekBarMeasure)
        
        btnPlay?.setOnClickListener {
            if (::midiPlayer.isInitialized) {
                if (midiPlayer.isPlaying()) {
                    midiPlayer.stopAll()
                    btnPlay.text = "▶"
                } else {
                    midiPlayer.startAll()
                    btnPlay.text = "⏸"
                    // TODO: 재생 위치 추적 시작
                }
            }
        }
        
        btnStop?.setOnClickListener {
            if (::midiPlayer.isInitialized) {
                midiPlayer.stopAll()
                midiPlayer.reset()
                btnPlay?.text = "▶"
            }
        }
        
        // BPM 조절
        val seekBarBPM = findViewById<SeekBar>(R.id.seekBarBPM)
        val txtBPM = findViewById<TextView>(R.id.txtBPM)
        
        seekBarBPM?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && ::midiPlayer.isInitialized) {
                    val bpm = progress.toDouble()
                    midiPlayer.setBPM(bpm)
                    txtBPM?.text = "BPM: $progress"
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
            }
        }
    }
}

