package com.robsonmartins.androidmidisynth

import android.os.Build
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import android.Manifest
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import android.media.midi.MidiDeviceInfo
import android.view.WindowManager
import android.widget.SeekBar
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.robsonmartins.androidmidisynth.util.MidiMultiPlayer

class MainViewModel : androidx.lifecycle.ViewModel() {
    var textContent: String = ""
}

class MainActivity : AppCompatActivity() {

    companion object {
        init {
            System.loadLibrary("synth-lib")
        }
    }

    private val viewModel: MainViewModel by viewModels()

    // MIDI 외부 장치용 (나중에 블루투스 연결 시 사용)
    private lateinit var midiManager: MidiManager

    private lateinit var txtLog: TextView
    private lateinit var scrollView: ScrollView

    private var useInternalSynth: Boolean = false

    private lateinit var synth: SynthManager
    private lateinit var multiPlayer: MidiMultiPlayer

    private lateinit var txtBPM: TextView
    private lateinit var seekBarBPM: SeekBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /* 권한 요청 */
        requestPermissions()

        /* UI 초기화 */
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainLayout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        txtBPM = findViewById(R.id.txtBPM)
        seekBarBPM = findViewById(R.id.seekBarBPM)

        /* SynthManager 초기화 */
        synth = SynthManager(this)
        synth.loadSoundFont("KawaiStereoGrand.sf3")

        /* MidiMultiPlayer 초기화 */
        multiPlayer = MidiMultiPlayer(synth)

        /* 초기 BPM 설정 */
        val initialBPM = 120
        seekBarBPM.progress = initialBPM
        txtBPM.text = "BPM: $initialBPM"

        /* MID 파일 로드 */
        assets.open("05_ Concerto in a minor, 3rd Movement, Op. 3, No.6.mid")
            .use { multiPlayer.loadMidi(it, initialBPM.toDouble()) }
        assets.open("09_Gavotte from _mignon_.mid")
            .use { multiPlayer.loadMidi(it, initialBPM.toDouble()) }

        /* MID 재생 시작 */
        multiPlayer.startAll()

        /* BPM SeekBar 이벤트 */
        seekBarBPM.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val bpm = progress.coerceAtLeast(30) // 최소 30 BPM
                multiPlayer.setBPM(bpm.toDouble())
                txtBPM.text = "BPM: $bpm"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    override fun onDestroy() {
        /* 안전 종료 */
        multiPlayer.stopAll()
        synth.release()
        synth.finalize()
        super.onDestroy()
    }

    private fun onMidiMessageReceived(message: String) {
        runOnUiThread { txtLog.append("$message\n") }

        if (useInternalSynth) {
            // TODO: MIDI Note/Velocity 파싱 후 SynthManager 호출
            // synth.fluidsynthNoteOn(note, velocity)
        }
    }

    /* 나중에 외부 MIDI 장치 연결 시 사용 */
    private fun checkMidiDevicesAndStart() {
        val devices = (getSystemService(MIDI_SERVICE) as android.media.midi.MidiManager).devices
        if (devices.isEmpty()) {
            onMidiMessageReceived("No external MIDI devices detected. Using internal Synth.")
            useInternalSynth = true
        } else {
            devices.forEach { deviceInfo ->
                openMidiDeviceOrUseSynth(deviceInfo)
            }
        }
    }

    private fun openMidiDeviceOrUseSynth(deviceInfo: MidiDeviceInfo) {
        val productName = deviceInfo.properties.getString("product") ?: "Unknown"
        if (productName.lowercase() == "fluidsynth") return

        val outputPort =
            deviceInfo.ports.firstOrNull { it.type == MidiDeviceInfo.PortInfo.TYPE_OUTPUT }
        if (outputPort == null) {
            onMidiMessageReceived("No valid output port for device $productName. Using internal Synth.")
            useInternalSynth = true
            return
        }

        midiManager.openMidiDevice(deviceInfo)
    }

    private fun requestPermissions() {
        val permissions = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            else -> emptyArray()
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions, 100)
        }
    }
}
