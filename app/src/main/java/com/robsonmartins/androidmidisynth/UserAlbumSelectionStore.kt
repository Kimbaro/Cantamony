package com.robsonmartins.androidmidisynth

import android.content.Context

/**
 * 개인별 앨범 선택 상태 저장소.
 *
 * - 현재는 SharedPreferences 구현
 * - 추후 Room/서버로 대체 가능하도록 인터페이스로 분리
 */
interface UserAlbumSelectionStore {
    fun getSelectedAlbumName(roomId: String, userId: String): String?
    fun setSelectedAlbumName(roomId: String, userId: String, albumName: String)
}

class PrefsUserAlbumSelectionStore(
    context: Context,
) : UserAlbumSelectionStore {

    private val prefs = context.getSharedPreferences("room_state", Context.MODE_PRIVATE)

    override fun getSelectedAlbumName(roomId: String, userId: String): String? {
        return prefs.getString(key(roomId, userId), null)
    }

    override fun setSelectedAlbumName(roomId: String, userId: String, albumName: String) {
        prefs.edit().putString(key(roomId, userId), albumName).apply()
    }

    private fun key(roomId: String, userId: String): String = "selected_album_name::$roomId::$userId"
}





