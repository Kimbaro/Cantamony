package com.robsonmartins.androidmidisynth

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.leff.midi.MidiFile
import com.leff.midi.event.NoteOff
import com.leff.midi.event.NoteOn
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

    private var initialBPM = 120

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

        // MIDI 파일 트랙별로 파싱 후 multiPlayer에 추가
        for ((index, path) in midiFiles.withIndex()) {
            assets.open(path).use { inputStream ->
                val trackEvents = parseMidiFile(inputStream) // track 단위 이벤트 리스트
                trackEvents.forEach { events ->
                    multiPlayer.addTrack(events, index)
                }
            }
        }

        // 트랙별 ON/OFF 버튼 생성
        midiFiles.forEachIndexed { index, _ ->
            val btn = Button(this).apply {
                text = "ON ${index + 1}"
                setOnClickListener {
                    val currentlyMuted = multiPlayer.getMuteTracks()[index] ?: false
                    multiPlayer.setMute(index, !currentlyMuted)
                    text = if (!currentlyMuted) "OFF ${index + 1}" else "ON ${index + 1}"
                }
            }
            buttonContainer.addView(btn)
        }

        // PLAY ALL
        btnPlayAll.setOnClickListener { multiPlayer.startAll() }

        // STOP ALL
        btnStopAll.setOnClickListener { multiPlayer.stopAll() }

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
    }

    override fun onDestroy() {
        multiPlayer.stopAll()
        synth.release()
        super.onDestroy()
    }

    /** MIDI InputStream → 트랙 단위 MidiEvent 리스트 반환 */
    private fun parseMidiFile(inputStream: InputStream): List<List<MidiEvent>> {
        val midiFile = MidiFile(inputStream)
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
