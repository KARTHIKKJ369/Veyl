package com.audiophile.player.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsManagerTest {

    @Test
    fun testParseStandardLrc() {
        val lrcContent = """
            [ti:Test Title]
            [ar:Test Artist]
            [al:Test Album]
            [00:05.00]First line of the song
            [00:12.50]Second line after chorus
            [00:25.00]Third line outro
        """.trimIndent()

        val lines = LyricsManager.parseLrc(lrcContent)
        val parsed = TrackLyrics(uri = "test_uri", lines = lines, source = "test")

        assertEquals(3, parsed.lines.size)
        assertEquals(5000L, parsed.lines[0].timestampMs)
        assertEquals("First line of the song", parsed.lines[0].text)
        assertEquals(12500L, parsed.lines[1].timestampMs)
        assertEquals("Second line after chorus", parsed.lines[1].text)
        assertEquals(25000L, parsed.lines[2].timestampMs)
        assertEquals("Third line outro", parsed.lines[2].text)
        assertFalse(parsed.hasWordTiming)
    }

    @Test
    fun testParseLrcWithOffsetHeader() {
        val lrcContent = """
            [offset:500]
            [00:10.00]Line with 500ms offset
        """.trimIndent()

        val lines = LyricsManager.parseLrc(lrcContent)
        val parsed = TrackLyrics(uri = "test_uri", lines = lines, source = "test")

        assertEquals(1, parsed.lines.size)
        // 10000ms + 500ms offset = 10500ms
        assertEquals(10500L, parsed.lines[0].timestampMs)
    }

    @Test
    fun testBinarySearchFindActiveIndex() {
        val lines = listOf(
            LyricLine(timestampMs = 5000L, text = "Line 1"),
            LyricLine(timestampMs = 10000L, text = "Line 2"),
            LyricLine(timestampMs = 20000L, text = "Line 3"),
            LyricLine(timestampMs = 30000L, text = "Line 4")
        )
        val trackLyrics = TrackLyrics(uri = "test_uri", lines = lines, source = "test")

        // Before any lyrics start -> returns -1
        assertEquals(-1, trackLyrics.findActiveIndex(2.0))
        assertEquals(-1, trackLyrics.findActiveIndex(4.99))

        // Exact hits
        assertEquals(0, trackLyrics.findActiveIndex(5.0))
        assertEquals(1, trackLyrics.findActiveIndex(10.0))
        assertEquals(2, trackLyrics.findActiveIndex(20.0))
        assertEquals(3, trackLyrics.findActiveIndex(30.0))

        // In between lines
        assertEquals(0, trackLyrics.findActiveIndex(7.5))
        assertEquals(1, trackLyrics.findActiveIndex(15.0))
        assertEquals(2, trackLyrics.findActiveIndex(25.0))

        // Past last line -> returns last line
        assertEquals(3, trackLyrics.findActiveIndex(35.0))
        assertEquals(3, trackLyrics.findActiveIndex(120.0))
    }

    @Test
    fun testFindActiveIndexWithCalibrationOffset() {
        val lines = listOf(
            LyricLine(timestampMs = 10000L, text = "Line 1")
        )
        val trackLyrics = TrackLyrics(uri = "test_uri", lines = lines, source = "test")

        // At 9.9s without offset -> not yet active (-1)
        assertEquals(-1, trackLyrics.findActiveIndex(9.9, offsetMs = 0L))

        // With +200ms user offset -> 9900ms + 200ms = 10100ms >= 10000ms -> active (0)
        assertEquals(0, trackLyrics.findActiveIndex(9.9, offsetMs = 200L))

        // At 10.0s with -200ms user offset -> 10000ms - 200ms = 9800ms < 10000ms -> not yet active (-1)
        assertEquals(-1, trackLyrics.findActiveIndex(10.0, offsetMs = -200L))
    }

    @Test
    fun testParseWordByWordTiming() {
        val lrcContent = """
            [00:04.00]<00:04.00>Never <00:04.50>gonna <00:05.00>give <00:05.50>you <00:06.00>up
        """.trimIndent()

        val lines = LyricsManager.parseLrc(lrcContent)
        val parsed = TrackLyrics(uri = "test_uri", lines = lines, source = "word_test")

        assertTrue(parsed.hasWordTiming)
        assertEquals(1, parsed.lines.size)

        val line = parsed.lines[0]
        assertTrue(line.words.isNotEmpty())
        assertEquals("Never", line.words[0].text)
        assertEquals(4000L, line.words[0].startMs)
        assertEquals("gonna", line.words[1].text)
        assertEquals(4500L, line.words[1].startMs)
        assertEquals("give", line.words[2].text)
        assertEquals(5000L, line.words[2].startMs)
        assertEquals("you", line.words[3].text)
        assertEquals(5500L, line.words[3].startMs)
        assertEquals("up", line.words[4].text)
        assertEquals(6000L, line.words[4].startMs)

        // Test active word indexing
        assertEquals(-1, line.findActiveWordIndex(3000L))
        assertEquals(0, line.findActiveWordIndex(4000L))
        assertEquals(0, line.findActiveWordIndex(4200L))
        assertEquals(1, line.findActiveWordIndex(4500L))
        assertEquals(2, line.findActiveWordIndex(5100L))
    }

    @Test
    fun testCleanTitleAndArtist() {
        assertEquals("Bohemian Rhapsody", LyricsManager.cleanTitle("Bohemian Rhapsody (2011 Remaster)"))
        assertEquals("Hotel California", LyricsManager.cleanTitle("Hotel California [Remastered] (Live)"))
        assertEquals("Stairway To Heaven", LyricsManager.cleanTitle("Stairway To Heaven - Remastered 2024"))
        assertEquals("Daft Punk", LyricsManager.cleanArtist("Daft Punk feat. Pharrell Williams"))
        assertEquals("Queen", LyricsManager.cleanArtist("Queen ft. David Bowie"))
    }
}
