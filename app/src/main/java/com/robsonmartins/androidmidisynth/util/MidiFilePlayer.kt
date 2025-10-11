package com.robsonmartins.androidmidisynth.util

import com.robsonmartins.androidmidisynth.SynthManager
import java.io.InputStream
import kotlin.concurrent.thread

/**
 * 각 MIDI 파일별 재생 및 볼륨(on/off) 제어 담당
 */
class MidiFilePlayer(private val synth: SynthManager) {

    private var isPlaying = false
    private var playThread: Thread? = null
    private var mute: Boolean = false
    private var midiData: ByteArray = byteArrayOf()
    private var bpm: Double = 120.0

    fun getIsPlaying(): Boolean = isPlaying

    fun load(inputStream: InputStream, initialBPM: Double) {
        midiData = inputStream.readBytes()
        bpm = initialBPM
    }

    fun play() {
        if (isPlaying) return
        isPlaying = true

        playThread = thread {
            // TODO: 실제 MIDI 파싱 후 noteOn/noteOff 호출 필요
            while (isPlaying) {
                if (!mute) {
                    // 임시: 모든 채널 볼륨 설정
                    for (ch in 0 until 16) synth.sendCC(7, 127)
                }
                Thread.sleep(10) // 루프 간격
            }
        }
    }

    fun stop() {
        isPlaying = false
        playThread?.join()
    }

    fun setBPM(newBPM: Double) {
        bpm = newBPM
    }

    fun setMute(on: Boolean) {
        mute = on
    }
}
