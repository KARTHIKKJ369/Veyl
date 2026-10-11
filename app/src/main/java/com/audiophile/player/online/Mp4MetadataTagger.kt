package com.audiophile.player.online

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer

/**
 * Injects standard iTunes/MP4 metadata tags (Title, Artist, Album, and Cover Art)
 * into .m4a / .mp4 audio files and adjusts MP4 chunk offsets (stco/co64)
 * so Android MediaStore, pure-Rust lofty, and external audiophile players
 * recognize all metadata and embedded album art natively.
 */
object Mp4MetadataTagger {
    private const val TAG = "Mp4MetadataTagger"

    private fun logD(msg: String) {
        try { Log.d(TAG, msg) } catch (_: Throwable) { println("[$TAG] $msg") }
    }

    private fun logE(msg: String, e: Throwable? = null) {
        try { Log.e(TAG, msg, e) } catch (_: Throwable) { println("[$TAG] ERROR: $msg ${e?.message}") }
    }

    fun embedMetadata(
        file: File,
        title: String,
        artist: String,
        album: String,
        coverBytes: ByteArray? = null
    ): Boolean {
        if (!file.exists() || file.length() < 100) return false

        try {
            val fileBytes = file.readBytes()
            var offset = 0
            var moovOffset = -1
            var moovSize = 0

            while (offset + 8 <= fileBytes.size) {
                val size = ByteBuffer.wrap(fileBytes, offset, 4).int
                val type = String(fileBytes, offset + 4, 4, Charsets.ISO_8859_1)
                if (type == "moov") {
                    moovOffset = offset
                    moovSize = size
                    break
                }
                if (size <= 0) break
                offset += size
            }

            if (moovOffset == -1 || moovSize <= 8) {
                logE("No moov atom found in ${file.name}")
                return false
            }

            // Extract existing moov children, excluding existing udta
            val moovEnd = moovOffset + moovSize
            var childOffset = moovOffset + 8
            val otherMoovChildren = ByteArrayOutputStream()

            while (childOffset + 8 <= moovEnd) {
                val childSize = ByteBuffer.wrap(fileBytes, childOffset, 4).int
                if (childSize <= 0 || childOffset + childSize > moovEnd) break
                val childType = String(fileBytes, childOffset + 4, 4, Charsets.ISO_8859_1)
                if (childType != "udta") {
                    otherMoovChildren.write(fileBytes, childOffset, childSize)
                }
                childOffset += childSize
            }

            // Build new udta atom
            val newUdta = buildUdtaAtom(title, artist, album, coverBytes)

            // New moov content = otherMoovChildren + newUdta
            val newMoovContent = otherMoovChildren.toByteArray() + newUdta
            val newMoovSize = newMoovContent.size + 8
            val delta = newMoovSize - moovSize

            // Update chunk offsets (stco/co64) in trak if delta != 0
            val updatedMoovContent = if (delta != 0) {
                adjustChunkOffsets(newMoovContent, delta)
            } else {
                newMoovContent
            }

            val newMoov = buildBox("moov", updatedMoovContent)

            // Reconstruct file: [before moov] + [new moov] + [after old moov]
            val out = ByteArrayOutputStream(fileBytes.size + delta)
            out.write(fileBytes, 0, moovOffset)
            out.write(newMoov)
            val afterMoovOffset = moovOffset + moovSize
            if (afterMoovOffset < fileBytes.size) {
                out.write(fileBytes, afterMoovOffset, fileBytes.size - afterMoovOffset)
            }

            file.writeBytes(out.toByteArray())
            logD("Successfully embedded MP4 metadata into ${file.name} (title=$title, artist=$artist)")
            return true
        } catch (e: Exception) {
            logE("Failed to embed MP4 metadata into ${file.name}", e)
            return false
        }
    }

    private fun adjustChunkOffsets(bytes: ByteArray, delta: Int): ByteArray {
        val result = bytes.clone()
        var offset = 0
        while (offset + 8 <= result.size) {
            val size = ByteBuffer.wrap(result, offset, 4).int
            if (size <= 0 || offset + size > result.size) break
            val type = String(result, offset + 4, 4, Charsets.ISO_8859_1)
            if (type == "trak") {
                findAndAdjustStco(result, offset + 8, offset + size, delta)
            }
            offset += size
        }
        return result
    }

    private fun findAndAdjustStco(bytes: ByteArray, start: Int, end: Int, delta: Int) {
        var offset = start
        while (offset + 8 <= end) {
            val size = ByteBuffer.wrap(bytes, offset, 4).int
            if (size <= 0 || offset + size > end) break
            val type = String(bytes, offset + 4, 4, Charsets.ISO_8859_1)
            when (type) {
                "mdia", "minf", "stbl" -> {
                    findAndAdjustStco(bytes, offset + 8, offset + size, delta)
                }
                "stco" -> {
                    if (offset + 16 <= end) {
                        val count = ByteBuffer.wrap(bytes, offset + 12, 4).int
                        var entryOffset = offset + 16
                        for (i in 0 until count) {
                            if (entryOffset + 4 > end) break
                            val oldChunkOffset = ByteBuffer.wrap(bytes, entryOffset, 4).int
                            val newChunkOffset = oldChunkOffset + delta
                            ByteBuffer.wrap(bytes, entryOffset, 4).putInt(newChunkOffset)
                            entryOffset += 4
                        }
                    }
                }
                "co64" -> {
                    if (offset + 16 <= end) {
                        val count = ByteBuffer.wrap(bytes, offset + 12, 4).int
                        var entryOffset = offset + 16
                        for (i in 0 until count) {
                            if (entryOffset + 8 > end) break
                            val oldChunkOffset = ByteBuffer.wrap(bytes, entryOffset, 8).long
                            val newChunkOffset = oldChunkOffset + delta
                            ByteBuffer.wrap(bytes, entryOffset, 8).putLong(newChunkOffset)
                            entryOffset += 8
                        }
                    }
                }
            }
            offset += size
        }
    }

    private fun buildUdtaAtom(title: String, artist: String, album: String, coverBytes: ByteArray?): ByteArray {
        val ilstContent = ByteArrayOutputStream()
        if (title.isNotBlank()) {
            ilstContent.write(buildTextAtom("\u00A9nam", title))
        }
        if (artist.isNotBlank()) {
            ilstContent.write(buildTextAtom("\u00A9ART", artist))
            ilstContent.write(buildTextAtom("aART", artist))
        }
        if (album.isNotBlank()) {
            ilstContent.write(buildTextAtom("\u00A9alb", album))
        }
        if (coverBytes != null && coverBytes.isNotEmpty()) {
            ilstContent.write(buildCoverAtom(coverBytes))
        }

        val ilstAtom = buildBox("ilst", ilstContent.toByteArray())
        val hdlrAtom = buildHdlrAtom()

        val metaContent = ByteArrayOutputStream().apply {
            write(byteArrayOf(0, 0, 0, 0)) // 4 bytes version/flags
            write(hdlrAtom)
            write(ilstAtom)
        }.toByteArray()

        val metaAtom = buildBox("meta", metaContent)
        return buildBox("udta", metaAtom)
    }

    private fun buildTextAtom(fourcc: String, text: String): ByteArray {
        val textBytes = text.toByteArray(Charsets.UTF_8)
        val dataContent = ByteArrayOutputStream().apply {
            write(byteArrayOf(0, 0, 0, 1)) // Type 1 = UTF-8 text
            write(byteArrayOf(0, 0, 0, 0)) // Locale = 0
            write(textBytes)
        }.toByteArray()
        val dataAtom = buildBox("data", dataContent)
        return buildBox(fourcc, dataAtom)
    }

    private fun buildCoverAtom(imageBytes: ByteArray): ByteArray {
        val isPng = imageBytes.size >= 8 &&
                imageBytes[0] == 0x89.toByte() &&
                imageBytes[1] == 0x50.toByte() &&
                imageBytes[2] == 0x4E.toByte() &&
                imageBytes[3] == 0x47.toByte()

        val typeFlag = if (isPng) 14 else 13 // 13 = JPEG, 14 = PNG
        val dataContent = ByteArrayOutputStream().apply {
            write(byteArrayOf(0, 0, 0, typeFlag.toByte()))
            write(byteArrayOf(0, 0, 0, 0)) // Locale = 0
            write(imageBytes)
        }.toByteArray()
        val dataAtom = buildBox("data", dataContent)
        return buildBox("covr", dataAtom)
    }

    private fun buildHdlrAtom(): ByteArray {
        val hdlrContent = ByteArrayOutputStream().apply {
            write(byteArrayOf(0, 0, 0, 0)) // Version & flags
            write(byteArrayOf(0, 0, 0, 0)) // pre_defined
            write("mdir".toByteArray(Charsets.ISO_8859_1)) // handler type
            write(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)) // 12 reserved bytes
            write(0) // name (null terminated string)
        }.toByteArray()
        return buildBox("hdlr", hdlrContent)
    }

    private fun buildBox(type: String, content: ByteArray): ByteArray {
        val size = content.size + 8
        val out = ByteArrayOutputStream(size)
        val header = ByteBuffer.allocate(8).apply {
            putInt(size)
            put(type.toByteArray(Charsets.ISO_8859_1))
        }.array()
        out.write(header)
        out.write(content)
        return out.toByteArray()
    }
}
