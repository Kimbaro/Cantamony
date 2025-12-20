package com.robsonmartins.androidmidisynth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.robsonmartins.androidmidisynth.dto.CantamonyAlbum

class RoomActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ROOM_ID = "ROOM_ID"
        const val EXTRA_ROOM_TITLE = "ROOM_TITLE"
    }

    private val chatItems = mutableListOf(
        ChatMessage(author = "시스템", body = "방에 입장했습니다."),
    )

    private lateinit var chatAdapter: ChatAdapter
    private lateinit var selectionStore: UserAlbumSelectionStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_room)

        val roomId = intent.getStringExtra(EXTRA_ROOM_ID) ?: "room_default"
        val userId = "user_local" // TODO: 로그인 도입 시 실제 userId로 치환
        selectionStore = PrefsUserAlbumSelectionStore(this)

        val roomTitle = intent.getStringExtra(EXTRA_ROOM_TITLE) ?: "소규모 그룹방"
        findViewById<TextView>(R.id.tvRoomTitle).text = roomTitle

        val albums = demoAlbums()
        val selectedAlbumName = selectionStore.getSelectedAlbumName(roomId, userId)

        // 1) 상단: 그룹방 참여자 리스트(가로)
        val participants = demoParticipants()
        val rvParticipants = findViewById<RecyclerView>(R.id.rvParticipants)
        rvParticipants.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvParticipants.adapter = ParticipantAdapter(participants)

        // 2) 하단: 앨범 가로 슬라이드 (스냅)
        val rvAlbumGrid = findViewById<RecyclerView>(R.id.rvAlbumGrid)
        rvAlbumGrid.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        PagerSnapHelper().attachToRecyclerView(rvAlbumGrid)
        rvAlbumGrid.adapter = AlbumGridAdapter(albums, selectedAlbumName) { selected ->
            // 앨범 선택 -> 악보/재생 화면으로 이동
            selectionStore.setSelectedAlbumName(roomId, userId, selected.albumName)
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra(MainActivity.EXTRA_SELECTED_ALBUM, selected)

            // MainActivity는 기본적으로 EXTRA_MUSICXML_FILE_PATH(EXTRA_MXL_FILE_PATH)를 통해 악보 렌더링을 시작한다.
            // RoomActivity에서도 SelectMidiActivity와 동일하게 파일명을 전달해 무한 로딩을 방지한다.
            val mxlFileName = selected.albumAsset["mxl"]
            val musicxmlFileName = selected.albumAsset["musicxml"]
            if (mxlFileName != null) {
                intent.putExtra(MainActivity.EXTRA_MXL_FILE_PATH, mxlFileName)
                Log.d("RoomActivity", "MXL file name passed to MainActivity: $mxlFileName")
            } else if (musicxmlFileName != null) {
                intent.putExtra(MainActivity.EXTRA_MUSICXML_FILE_PATH, musicxmlFileName)
                Log.d("RoomActivity", "MusicXML file name passed to MainActivity: $musicxmlFileName")
            } else {
                Log.w("RoomActivity", "No musicxml/mxl asset found in selected album: ${selected.albumName}")
            }

            startActivity(intent)
        }

        // 3) 하단: 채팅 리스트 + 입력창
        val rvChat = findViewById<RecyclerView>(R.id.rvChat)
        rvChat.layoutManager = LinearLayoutManager(this)
        chatAdapter = ChatAdapter(chatItems)
        rvChat.adapter = chatAdapter

        val etMessage = findViewById<EditText>(R.id.etMessage)
        val btnSend = findViewById<Button>(R.id.btnSend)
        btnSend.setOnClickListener {
            val text = etMessage.text?.toString()?.trim().orEmpty()
            if (text.isNotEmpty()) {
                chatItems.add(ChatMessage(author = "나", body = text))
                chatAdapter.notifyItemInserted(chatItems.lastIndex)
                rvChat.scrollToPosition(chatItems.lastIndex)
                etMessage.setText("")
            }
        }
    }

    private fun demoAlbums(): List<CantamonyAlbum> {
        return listOf(
            CantamonyAlbum(
                "Dorico 학습앨범 5",
                mapOf(
                    "musicxml" to "Minuet 도리코 최종 - 01_피아노 - 01 Minuet.musicxml",
                    "mid" to "j - Full score - Flow 1.mid",
                )
            ),
            CantamonyAlbum(
                "ORieding-Op35",
                mapOf(
                    "musicxml" to "ORieding-Op35 - 01_Part 1 - 01 ORieding-Op35.musicxml",
                    "mid" to "ORieding-Op35 - Full score - ORieding-Op35.mid",
                )
            ),
            CantamonyAlbum(
                "test",
                mapOf(
                    "musicxml" to "ORieding-Op35 - 01_Part 1 - 01 ORieding-Op35.musicxml",
                    "mid" to "ORieding-Op35 - Full score - ORieding-Op35.mid",
                )
            ),
        )
    }

    private fun demoParticipants(): List<Participant> {
        return listOf(
            Participant("Person 1", isActive = true),
            Participant("Person 2", isActive = true),
            Participant("Person 3", isActive = true),
            Participant("Person 4", isActive = true),
            Participant("Person 5", isActive = true),
            Participant("Person 6", isActive = true),
        )
    }
}


