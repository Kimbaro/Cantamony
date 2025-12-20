package com.robsonmartins.androidmidisynth

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ProfileActivity : AppCompatActivity() {

    private lateinit var feedAdapter: FeedAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_profile)

        setupStatusBar()
        applySelectedUserIfAny()
        setupRecycler()
        setupMenuButtons()
    }

    private fun applySelectedUserIfAny() {
        val name = intent.getStringExtra(NewActivity.EXTRA_USER_NAME)
        val handle = intent.getStringExtra(NewActivity.EXTRA_USER_HANDLE)
        if (name.isNullOrBlank() && handle.isNullOrBlank()) return

        name?.let {
            findViewById<TextView>(R.id.tvName)?.text = it
        }
        handle?.let {
            // tvRole을 "핸들"처럼 보여주고 싶을 때가 많아 우선 여기에 바인딩
            findViewById<TextView>(R.id.tvRole)?.text = it
        }
    }

    private fun setupRecycler() {
        val recyclerView = findViewById<RecyclerView>(R.id.rvArticles)
        recyclerView.layoutManager = LinearLayoutManager(this)

        feedAdapter = FeedAdapter(
            onRoomClicked = { roomId, title ->
                val intent = Intent(this, RoomActivity::class.java)
                intent.putExtra(RoomActivity.EXTRA_ROOM_ID, roomId)
                intent.putExtra(RoomActivity.EXTRA_ROOM_TITLE, title)
                startActivity(intent)
            },
        )
        recyclerView.adapter = feedAdapter

        renderFeed()
    }

    private fun renderFeed() {
        val cards = buildList<FeedCard> {
            add(
                RoomEntryCard(
                    cardId = "room_1",
                    roomId = "room_default",
                    title = "소규모 그룹방 A",
                    category = "커뮤니티 · 음악",
                )
            )
            add(
                RoomEntryCard(
                    cardId = "room_2",
                    roomId = "room_default_2",
                    title = "소규모 그룹방 B",
                    category = "커뮤니티 · 음악",
                )
            )
            add(
                RoomEntryCard(
                    cardId = "room_3",
                    roomId = "room_default_3",
                    title = "소규모 그룹방 C",
                    category = "커뮤니티 · 음악",
                )
            )
        }

        feedAdapter.submitList(cards)
    }

    private fun setupMenuButtons() {
        // TODO: 필요 시 클릭 액션 연결
        findViewById<View>(R.id.btnMenu1).setOnClickListener { /* no-op */ }
        findViewById<View>(R.id.btnMenu2).setOnClickListener { /* no-op */ }
        findViewById<View>(R.id.btnMenu3).setOnClickListener { /* no-op */ }
    }

    private fun setupStatusBar() {
        // 이미지처럼 밝은 배경 위에 어두운 아이콘을 의도.
        val statusBarColor = Color.parseColor("#6E748B")
        window.statusBarColor = statusBarColor

        WindowCompat.setDecorFitsSystemWindows(window, true)

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = false

        // Android 15+ 대비: 시스템 바 컨트롤러가 null일 경우도 대비
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // no-op: WindowInsetsControllerCompat가 내부 처리
        }
    }
}


