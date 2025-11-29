package com.robsonmartins.androidmidisynth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.robsonmartins.androidmidisynth.dto.CantamonyAlbum

class SelectMidiActivity : AppCompatActivity() {

    private val albums = listOf(
        CantamonyAlbum(
            "Minuet 학습앨범 1",
            mapOf(
                "mxl" to "12_Minuet.mxl",
                "mid" to "12_Minuet.mid"
            )
        ),
        CantamonyAlbum(
            "Minuet 학습앨범 2",
            mapOf(
                "mxl" to "J._S._Bach_-_Air_on_the_G_String_Piano_arrangement.mxl",
                "mid" to "12_Minuet.mid"
            )
        ),
        CantamonyAlbum(
            "Minuet 학습앨범 3",
            mapOf(
                "musicxml" to "12_Minuet.musicxml",
                "mid" to "12_Minuet.mid"
            )
        ),
        CantamonyAlbum(
            "Minuet 학습앨범 4",
            mapOf(
                "musicxml" to "Minuet.musicxml",
                "mid" to "12_Minuet.mid"
            )
        ),
        CantamonyAlbum(
            "J 학습앨범 5",
            mapOf(
                "musicxml" to "j - 01_Violin - 01 Flow 1.musicxml",
                "mid" to "j - Full score - Flow 1.mid"
            )
        ),
                CantamonyAlbum(
                "Dorico 학습앨범 5",
        mapOf(
            "musicxml" to "Minuet 도리코 최종 - 01_피아노 - 01 Minuet.musicxml",
            "mid" to "j - Full score - Flow 1.mid"
        )
    )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_select_midi)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewAlbums)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = AlbumAdapter(albums) { selectedAlbum ->
            // MXL 파일을 MusicXML 문자열로 변환 후 MainActivity로 이동
            handleAlbumSelection(selectedAlbum)
        }
    }

    /**
     * 앨범 선택 시 MXL 파일 이름을 Intent에 담아 MainActivity로 전달
     * MusicXML 변환 작업은 MainActivity에서 진행
     */
    private fun handleAlbumSelection(selectedAlbum: CantamonyAlbum) {
        val mxlFileName = selectedAlbum.albumAsset["mxl"]
        val musicxmlFileName = selectedAlbum.albumAsset["musicxml"]
        val intent = Intent(this@SelectMidiActivity, MainActivity::class.java)

        // SELECTED_ALBUM 전달 (MIDI 파일 경로용)
        intent.putExtra("SELECTED_ALBUM", selectedAlbum)

        if (mxlFileName != null) {
            // MXL 파일 이름 전달 (MainActivity에서 MusicXML 변환 작업 진행)
            intent.putExtra("MXL_FILE_PATH", mxlFileName)
            Log.d("SelectMidiActivity", "MXL file name passed to MainActivity: $mxlFileName")
        } else if (musicxmlFileName != null) {
            // MusicXML 파일 이름 전달 (MainActivity에서 MusicXML 읽기 작업 진행)
            intent.putExtra("MUSICXML_FILE_PATH", musicxmlFileName)
            Log.d("SelectMidiActivity", "MusicXML file name passed to MainActivity: $musicxmlFileName")
        }

        // MainActivity로 이동
        startActivity(intent)
    }

}
