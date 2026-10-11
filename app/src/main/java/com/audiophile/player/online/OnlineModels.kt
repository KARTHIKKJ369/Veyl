package com.audiophile.player.online

/**
 * Model representing an official online music track.
 */
data class OnlineSongItem(
    val id: String = "",
    val videoId: String = id,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val durationSeconds: Double = 0.0,
    val durationText: String = "",
    val thumbnailUrl: String = "",
    val streamUrl: String = ""
)
