package com.audiophile.player.online

/**
 * Model representing an official online music track retrieved from YouTube Music.
 */
data class OnlineSongItem(
    val videoId: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationSeconds: Double,
    val durationText: String,
    val thumbnailUrl: String
)
