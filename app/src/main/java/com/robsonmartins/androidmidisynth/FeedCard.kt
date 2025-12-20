package com.robsonmartins.androidmidisynth

sealed interface FeedCard {
    val cardId: String
}

data class RoomEntryCard(
    override val cardId: String,
    val roomId: String,
    val title: String,
    val category: String?,
) : FeedCard


