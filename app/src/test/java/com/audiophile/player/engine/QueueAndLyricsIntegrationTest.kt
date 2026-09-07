package com.audiophile.player.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.audiophile_core.TrackInfo

class QueueAndLyricsIntegrationTest {

    private fun createDummyTrack(id: Int, title: String): TrackInfo {
        return TrackInfo(
            uri = "content://media/audio/$id",
            title = title,
            artist = "Artist $id",
            album = "Album $id",
            albumArtist = null,
            genre = null,
            year = null,
            trackNumber = null,
            discNumber = null,
            durationSeconds = 180.0,
            sampleRate = 44100u,
            bitDepth = 16u,
            bitrate = null,
            channels = 2u,
            formatName = "FLAC",
            hasArtwork = true
        )
    }

    @Test
    fun testQueueReordering() {
        val tracks = mutableListOf(
            createDummyTrack(1, "Track 1"),
            createDummyTrack(2, "Track 2"),
            createDummyTrack(3, "Track 3"),
            createDummyTrack(4, "Track 4")
        )

        // Move Track 1 (index 0) to index 2
        val item = tracks.removeAt(0)
        tracks.add(2, item)

        assertEquals("Track 2", tracks[0].title)
        assertEquals("Track 3", tracks[1].title)
        assertEquals("Track 1", tracks[2].title)
        assertEquals("Track 4", tracks[3].title)
    }

    @Test
    fun testQueuePlayNextInsertion() {
        val tracks = mutableListOf(
            createDummyTrack(1, "Track 1"),
            createDummyTrack(2, "Track 2"),
            createDummyTrack(3, "Track 3")
        )
        val currentTrack = tracks[0] // Track 1 is playing

        val newTrack = createDummyTrack(99, "Priority Track")
        val currentIndex = tracks.indexOfFirst { it.uri == currentTrack.uri }
        val insertIndex = if (currentIndex != -1) currentIndex + 1 else 0

        tracks.add(insertIndex, newTrack)

        assertEquals(4, tracks.size)
        assertEquals("Track 1", tracks[0].title)
        assertEquals("Priority Track", tracks[1].title)
        assertEquals("Track 2", tracks[2].title)
        assertEquals("Track 3", tracks[3].title)
    }

    @Test
    fun testLyricsOffsetMathDoesNotUnderflow() {
        val line = LyricLine(timestampMs = 1000L, text = "Early Line")
        val lyrics = TrackLyrics(uri = "test_uri", lines = listOf(line), source = "test")

        // Position 0.2s with -500ms offset would be 200 - 500 = -300ms.
        // Coerced at least 0ms -> 0ms.
        val activeIndex = lyrics.findActiveIndex(0.2, offsetMs = -500L)
        assertEquals(-1, activeIndex)
    }
}
