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

/**
 * Model representing an artist in discovery and recommendations.
 */
data class OnlineArtistItem(
    val id: String = "",
    val name: String = "",
    val role: String = "Artist",
    val imageUrl: String = "",
    val language: String = ""
)
