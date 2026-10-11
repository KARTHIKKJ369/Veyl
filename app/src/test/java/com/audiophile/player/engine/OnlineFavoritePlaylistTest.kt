package com.audiophile.player.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.audiophile_core.TrackInfo

class OnlineFavoritePlaylistTest {

    @Test
    fun testTrackInfoUpdateUri() {
        val original = TrackInfo(
            uri = "/data/user/0/com.audiophile.player/cache/veyl_online_stream_123.m4a",
            title = "Blinding Lights",
            artist = "The Weeknd",
            album = "After Hours",
            albumArtist = "The Weeknd",
            genre = "Online Stream",
            year = 2020u,
            trackNumber = 9u,
            discNumber = 1u,
            durationSeconds = 200.0,
            sampleRate = 44100u,
            bitDepth = 16u.toUByte(),
            bitrate = 320000u,
            channels = 2u.toUByte(),
            formatName = "AAC 320k (Studio Master)",
            hasArtwork = true
        )

        val updatedUri = "/storage/emulated/0/Music/Veyl/Blinding Lights - The Weeknd.m4a"
        val migrated = original.copy(uri = updatedUri)

        assertEquals(updatedUri, migrated.uri)
        assertEquals(original.title, migrated.title)
        assertEquals(original.artist, migrated.artist)
        assertEquals(original.album, migrated.album)
        assertEquals(original.durationSeconds, migrated.durationSeconds, 0.01)
        assertEquals(original.sampleRate, migrated.sampleRate)
        assertEquals(original.bitDepth, migrated.bitDepth)
        assertEquals(original.channels, migrated.channels)
    }

    @Test
    fun testSanitizedStorageFilename() {
        val title = "Song / With : Bad? * Characters|"
        val artist = "Artist <feat> Test"
        val sanitized = "$title - $artist".replace(Regex("[^a-zA-Z0-9.\\- ]"), "_").trim()
        val expected = "Song _ With _ Bad_ _ Characters_ - Artist _feat_ Test"
        assertEquals(expected, sanitized)
        assertTrue(sanitized.endsWith(".m4a") == false)
    }
}
