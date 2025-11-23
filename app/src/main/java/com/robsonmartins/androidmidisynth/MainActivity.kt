package com.robsonmartins.androidmidisynth

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
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
import java.io.InputStream

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

    private var initialBPM = 120
    
    // 마디 정보
    private var totalMeasures = 0
    private var ticksPerMeasure = 0L
    private var midiResolution = 480
    private var timeSignature: TimeSignature? = null
    
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
                val measure = seekBar?.progress ?: 0
                val targetTick = measureToTick(measure + 1)
                multiPlayer.seekTo(targetTick)
                
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
        multiPlayer.stopAll()
        synth.release()
        super.onDestroy()
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
                        MidiEvent(event.tick.toLong(), event.noteValue, event.velocity, true, trackIndex)
                    )
                    is NoteOff -> events.add(
                        MidiEvent(event.tick.toLong(), event.noteValue, event.velocity, false, trackIndex)
                    )
                }
            }
            tracksEvents.add(events)
        }

        return tracksEvents
    }

}
