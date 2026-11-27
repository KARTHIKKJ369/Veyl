package com.audiophile.player.engine

import uniffi.audiophile_core.TrackInfo
import java.io.File

/**
 * Parses standard .cue sheets paired with monolithic album images (FLAC/WAV/APE/WV)
 * and splits them into virtual TrackInfo entries with #cue=<start>-<end> URIs.
 */
object CueSheetParser {

    data class CueRange(
        val basePath: String,
        val startSec: Double,
        val endSec: Double
    )

    fun parseCueUri(uri: String): CueRange? {
        val idx = uri.indexOf("#cue=")
        if (idx < 0) return null
        val basePath = uri.substring(0, idx)
        val rangePart = uri.substring(idx + 5)
        val parts = rangePart.split("-")
        val start = parts.getOrNull(0)?.toDoubleOrNull() ?: return null
        val end = parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0
        return CueRange(basePath, start, end)
    }

    fun splitIfCuePresent(track: TrackInfo): List<TrackInfo> {
        val audioFile = File(track.uri)
        val parent = audioFile.parentFile ?: return listOf(track)
        if (!parent.exists() || !parent.isDirectory) return listOf(track)

        val matchingCue = findMatchingCueFile(parent, audioFile) ?: return listOf(track)
        val parsed = parseCueFile(matchingCue, track)
        return if (parsed.size >= 2) parsed else listOf(track)
    }

    private fun findMatchingCueFile(dir: File, audioFile: File): File? {
        val exact = File(dir, "${audioFile.nameWithoutExtension}.cue")
        if (exact.exists() && exact.isFile) return exact

        val cueFiles = dir.listFiles { f -> f.isFile && f.extension.equals("cue", ignoreCase = true) }
            ?: return null
        val targetName = audioFile.name
        return cueFiles.firstOrNull { cue ->
            runCatching {
                cue.readLines().any { line ->
                    val trimmed = line.trim()
                    trimmed.startsWith("FILE ", ignoreCase = true) &&
                        trimmed.contains(targetName, ignoreCase = true)
                }
            }.getOrDefault(false)
        }
    }

    private data class RawCueTrack(
        val trackNumber: UInt,
        var title: String? = null,
        var performer: String? = null,
        var index01Sec: Double? = null
    )

    private fun parseCueFile(cueFile: File, baseTrack: TrackInfo): List<TrackInfo> {
        val lines = runCatching { cueFile.readLines() }.getOrNull() ?: return emptyList()

        var albumTitle = baseTrack.album
        var albumArtist = baseTrack.albumArtist ?: baseTrack.artist
        var year = baseTrack.year
        var currentFileMatches = false
        val rawTracks = mutableListOf<RawCueTrack>()
        var currentTrack: RawCueTrack? = null
        val targetFileName = File(baseTrack.uri).name

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            when {
                line.startsWith("REM DATE ", ignoreCase = true) && currentTrack == null -> {
                    val y = line.substring(9).trim().trim('"').take(4).toUIntOrNull()
                    if (y != null && y in 1900u..2100u) year = y
                }
                line.startsWith("TITLE ", ignoreCase = true) -> {
                    val value = extractQuotedOrRest(line.substring(6))
                    if (currentTrack == null) {
                        if (value.isNotBlank()) albumTitle = value
                    } else {
                        currentTrack.title = value
                    }
                }
                line.startsWith("PERFORMER ", ignoreCase = true) -> {
                    val value = extractQuotedOrRest(line.substring(10))
                    if (currentTrack == null) {
                        if (value.isNotBlank()) albumArtist = value
                    } else {
                        currentTrack.performer = value
                    }
                }
                line.startsWith("FILE ", ignoreCase = true) -> {
                    val fileName = extractQuotedOrRest(line.substring(5))
                    currentFileMatches = fileName.equals(targetFileName, ignoreCase = true) ||
                        File(fileName).name.equals(targetFileName, ignoreCase = true)
                }
                line.startsWith("TRACK ", ignoreCase = true) -> {
                    val parts = line.split(Regex("\\s+"))
                    val num = parts.getOrNull(1)?.toUIntOrNull()
                    val isAudio = parts.getOrNull(2)?.equals("AUDIO", ignoreCase = true) == true
                    if (num != null && isAudio && (currentFileMatches || lines.count { it.trim().startsWith("FILE ", true) } <= 1)) {
                        currentTrack = RawCueTrack(trackNumber = num)
                        rawTracks.add(currentTrack)
                    } else {
                        currentTrack = null
                    }
                }
                line.startsWith("INDEX 01 ", ignoreCase = true) && currentTrack != null -> {
                    val timeStr = line.substring(9).trim().split(Regex("\\s+")).firstOrNull()
                    val sec = timeStr?.let { parseCueTimestamp(it) }
                    if (sec != null) currentTrack.index01Sec = sec
                }
            }
        }

        val valid = rawTracks.filter { it.index01Sec != null }.sortedBy { it.index01Sec!! }
        if (valid.size < 2) return emptyList()

        val totalDuration = baseTrack.durationSeconds.coerceAtLeast(valid.last().index01Sec!! + 1.0)
        return valid.mapIndexed { i, ct ->
            val start = ct.index01Sec!!
            val end = if (i + 1 < valid.size) valid[i + 1].index01Sec!! else totalDuration
            val dur = (end - start).coerceAtLeast(1.0)
            baseTrack.copy(
                uri = "${baseTrack.uri}#cue=${"%.3f".format(java.util.Locale.US, start)}-${"%.3f".format(java.util.Locale.US, end)}",
                title = ct.title?.takeIf { it.isNotBlank() } ?: "Track ${ct.trackNumber}",
                artist = ct.performer?.takeIf { it.isNotBlank() } ?: albumArtist,
                album = albumTitle,
                albumArtist = albumArtist,
                year = year,
                trackNumber = ct.trackNumber,
                durationSeconds = dur
            )
        }
    }

    private fun extractQuotedOrRest(s: String): String {
        val trimmed = s.trim()
        if (trimmed.startsWith("\"")) {
            val endQuote = trimmed.indexOf('"', startIndex = 1)
            if (endQuote > 1) return trimmed.substring(1, endQuote).trim()
        }
        return trimmed.trim('"')
    }

    private fun parseCueTimestamp(ts: String): Double? {
        val parts = ts.split(":")
        if (parts.size != 3) return null
        val mm = parts[0].toLongOrNull() ?: return null
        val ss = parts[1].toLongOrNull() ?: return null
        val ff = parts[2].toLongOrNull() ?: return null
        return mm * 60.0 + ss + (ff / 75.0)
    }
}
