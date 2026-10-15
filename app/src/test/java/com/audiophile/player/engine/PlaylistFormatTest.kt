package com.audiophile.player.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.audiophile_core.TrackInfo
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class PlaylistFormatTest {

    @Test
    fun testExportM3u8Format() {
        val tracks = listOf(
            TrackInfo(
                uri = "/storage/emulated/0/Music/SongA.flac",
                title = "Song A",
                artist = "Artist One",
                album = "Album Alpha",
                albumArtist = null,
                genre = "Jazz",
                year = null,
                trackNumber = null,
                discNumber = null,
                durationSeconds = 180.0,
                sampleRate = 96000u,
                bitDepth = 24u.toUByte(),
                bitrate = 2500000u,
                channels = 2u.toUByte(),
                formatName = "FLAC",
                hasArtwork = true
            ),
            TrackInfo(
                uri = "https://example.com/stream/track.m4a",
                title = "Online Song",
                artist = "Artist Two",
                album = "Cloud Album",
                albumArtist = null,
                genre = "Online Stream",
                year = null,
                trackNumber = null,
                discNumber = null,
                durationSeconds = 215.5,
                sampleRate = 44100u,
                bitDepth = 16u.toUByte(),
                bitrate = 320000u,
                channels = 2u.toUByte(),
                formatName = "AAC",
                hasArtwork = true
            )
        )

        val out = ByteArrayOutputStream()
        out.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("#EXTM3U\n")
            writer.write("#PLAYLIST:My Test Playlist\n")
            for (track in tracks) {
                val dur = if (track.durationSeconds > 0) track.durationSeconds.toInt() else -1
                val artist = if (track.artist.isNotBlank()) track.artist else "Unknown Artist"
                val title = if (track.title.isNotBlank()) track.title else "Unknown Title"
                writer.write("#EXTINF:$dur,$artist - $title\n")
                if (!track.album.isNullOrBlank()) {
                    writer.write("#EXTALB:${track.album}\n")
                }
                writer.write("${track.uri}\n")
            }
            writer.flush()
        }

        val text = out.toString(Charsets.UTF_8.name())
        assertTrue(text.contains("#EXTM3U"))
        assertTrue(text.contains("#PLAYLIST:My Test Playlist"))
        assertTrue(text.contains("#EXTINF:180,Artist One - Song A"))
        assertTrue(text.contains("#EXTALB:Album Alpha"))
        assertTrue(text.contains("/storage/emulated/0/Music/SongA.flac"))
        assertTrue(text.contains("#EXTINF:215,Artist Two - Online Song"))
        assertTrue(text.contains("https://example.com/stream/track.m4a"))
    }

    @Test
    fun testParseM3u8Lines() {
        val m3uContent = """
            #EXTM3U
            #PLAYLIST:Rock Classics
            #EXTINF:240,Queen - Bohemian Rhapsody
            #EXTALB:A Night at the Opera
            /storage/emulated/0/Music/Queen/Bohemian Rhapsody.mp3
            #EXTINF:195,AC/DC - Back In Black
            /storage/emulated/0/Music/ACDC/Back In Black.flac
        """.trimIndent()

        val lines = m3uContent.lines()
        var playlistName = "Default"
        val uris = mutableListOf<String>()
        val parsedTitles = mutableListOf<String>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#PLAYLIST:")) {
                playlistName = trimmed.substring("#PLAYLIST:".length).trim()
            } else if (trimmed.startsWith("#EXTINF:")) {
                val info = trimmed.substring("#EXTINF:".length).trim()
                val comma = info.indexOf(',')
                if (comma != -1) {
                    val titleAndArtist = info.substring(comma + 1).trim()
                    parsedTitles.add(titleAndArtist)
                }
            } else if (!trimmed.startsWith("#") && trimmed.isNotEmpty()) {
                uris.add(trimmed)
            }
        }

        assertEquals("Rock Classics", playlistName)
        assertEquals(2, uris.size)
        assertEquals("/storage/emulated/0/Music/Queen/Bohemian Rhapsody.mp3", uris[0])
        assertEquals("/storage/emulated/0/Music/ACDC/Back In Black.flac", uris[1])
        assertEquals("Queen - Bohemian Rhapsody", parsedTitles[0])
        assertEquals("AC/DC - Back In Black", parsedTitles[1])
    }
}
