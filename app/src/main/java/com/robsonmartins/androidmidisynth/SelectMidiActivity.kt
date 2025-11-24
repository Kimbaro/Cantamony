package com.robsonmartins.androidmidisynth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.robsonmartins.androidmidisynth.dto.MidiAlbum

class SelectMidiActivity : AppCompatActivity() {

    private val albums = listOf(
        MidiAlbum("안토니오 비발디", listOf(
            "05_ Concerto in a minor, 3rd Movement, Op. 3, No.6.mid"
        )),
        MidiAlbum("앙브루아즈 토마", listOf(
            "09_Gavotte from _mignon_.mid"
        ))
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_midi)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewAlbums)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = AlbumAdapter(albums) { selectedAlbum ->
            val intent = Intent(this, MainActivity::class.java)
            intent.putStringArrayListExtra("MID_FILES", ArrayList(selectedAlbum.midFiles))
            intent.putExtra("ALBUM_NAME", selectedAlbum.albumName)
            startActivity(intent)
        }
    }
}
