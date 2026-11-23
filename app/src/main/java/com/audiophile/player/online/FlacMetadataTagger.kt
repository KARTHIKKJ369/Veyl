package com.audiophile.player.online

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer

/**
 * Embeds standard FLAC metadata (Vorbis Comments for Title, Artist, Album, Year,
 * and FLAC Picture Block for front cover art) into .flac audio files.
 * Preserves the exact lossless FLAC audio bitstream bit-for-bit without transcoding.
 */
object FlacMetadataTagger {
    private const val TAG = "FlacMetadataTagger"

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
        year: UInt? = null,
        coverBytes: ByteArray? = null
    ): Boolean {
        if (!file.exists() || file.length() < 100) return false

        try {
            val fileBytes = file.readBytes()
            if (fileBytes.size < 4 ||
                fileBytes[0] != 'f'.code.toByte() ||
                fileBytes[1] != 'L'.code.toByte() ||
                fileBytes[2] != 'a'.code.toByte() ||
                fileBytes[3] != 'C'.code.toByte()
            ) {
                logE("Not a valid FLAC file: ${file.name}")
                return false
            }

            var offset = 4
            var streamInfoBlock: ByteArray? = null
            val preservedBlocks = mutableListOf<Pair<Int, ByteArray>>() // (blockType, blockPayload)
            var audioStartOffset = -1

            while (offset + 4 <= fileBytes.size) {
                val headerByte = fileBytes[offset].toInt() and 0xFF
                val isLast = (headerByte and 0x80) != 0
                val blockType = headerByte and 0x7F

                val length = ((fileBytes[offset + 1].toInt() and 0xFF) shl 16) or
                             ((fileBytes[offset + 2].toInt() and 0xFF) shl 8) or
                             (fileBytes[offset + 3].toInt() and 0xFF)

                offset += 4
                if (offset + length > fileBytes.size) break

                val payload = fileBytes.copyOfRange(offset, offset + length)
                offset += length

                when (blockType) {
                    0 -> { // STREAMINFO
                        streamInfoBlock = payload
                    }
                    4, 6 -> {
                        // Skip existing VORBIS_COMMENT and PICTURE blocks to replace with fresh ones
                    }
                    else -> {
                        // Keep SEEKTABLE, CUESHEET, APPLICATION, etc. (ignore PADDING block type 1)
                        if (blockType != 1) {
                            preservedBlocks.add(Pair(blockType, payload))
                        }
                    }
                }

                if (isLast) {
                    audioStartOffset = offset
                    break
                }
            }

            if (streamInfoBlock == null || audioStartOffset == -1 || audioStartOffset >= fileBytes.size) {
                logE("FLAC streaminfo or audio offset invalid in ${file.name}")
                return false
            }

            // Build new Vorbis Comment Block
            val vorbisStream = ByteArrayOutputStream()
            val vendorBytes = "Veyl Audiophile Core".toByteArray(Charsets.UTF_8)
            vorbisStream.write(ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(vendorBytes.size).array())
            vorbisStream.write(vendorBytes)

            val comments = mutableListOf<String>()
            if (title.isNotBlank()) comments.add("TITLE=$title")
            if (artist.isNotBlank()) comments.add("ARTIST=$artist")
            if (album.isNotBlank()) comments.add("ALBUM=$album")
            if (year != null && year > 0u) comments.add("DATE=$year")

            vorbisStream.write(ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(comments.size).array())
            for (comment in comments) {
                val cBytes = comment.toByteArray(Charsets.UTF_8)
                vorbisStream.write(ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(cBytes.size).array())
                vorbisStream.write(cBytes)
            }
            val vorbisBlockPayload = vorbisStream.toByteArray()

            // Build new Picture Block if artwork is provided
            var pictureBlockPayload: ByteArray? = null
            if (coverBytes != null && coverBytes.isNotEmpty()) {
                val picStream = ByteArrayOutputStream()
                val isPng = coverBytes.size > 8 &&
                            coverBytes[0] == 0x89.toByte() &&
                            coverBytes[1] == 0x50.toByte() &&
                            coverBytes[2] == 0x4E.toByte()
                val mime = if (isPng) "image/png" else "image/jpeg"
                val mimeBytes = mime.toByteArray(Charsets.US_ASCII)

                picStream.write(ByteBuffer.allocate(4).putInt(3).array()) // 3 = Cover (front)
                picStream.write(ByteBuffer.allocate(4).putInt(mimeBytes.size).array())
                picStream.write(mimeBytes)
                picStream.write(ByteBuffer.allocate(4).putInt(0).array()) // description length = 0
                picStream.write(ByteBuffer.allocate(4).putInt(0).array()) // width
                picStream.write(ByteBuffer.allocate(4).putInt(0).array()) // height
                picStream.write(ByteBuffer.allocate(4).putInt(0).array()) // color depth
                picStream.write(ByteBuffer.allocate(4).putInt(0).array()) // indexed colors
                picStream.write(ByteBuffer.allocate(4).putInt(coverBytes.size).array())
                picStream.write(coverBytes)

                pictureBlockPayload = picStream.toByteArray()
            }

            // Assemble all blocks in order: STREAMINFO, preserved blocks, VORBIS_COMMENT, PICTURE
            val allBlocks = mutableListOf<Pair<Int, ByteArray>>()
            allBlocks.add(Pair(0, streamInfoBlock))
            allBlocks.addAll(preservedBlocks)
            allBlocks.add(Pair(4, vorbisBlockPayload))
            if (pictureBlockPayload != null) {
                allBlocks.add(Pair(6, pictureBlockPayload))
            }

            val out = ByteArrayOutputStream()
            out.write("fLaC".toByteArray(Charsets.US_ASCII))

            for (i in 0 until allBlocks.size) {
                val (type, payload) = allBlocks[i]
                val isLast = (i == allBlocks.size - 1)
                val headerByte = (if (isLast) 0x80 else 0x00) or (type and 0x7F)
                val length = payload.size
                out.write(headerByte)
                out.write((length shr 16) and 0xFF)
                out.write((length shr 8) and 0xFF)
                out.write(length and 0xFF)
                out.write(payload)
            }

            // Append FLAC audio frames
            out.write(fileBytes, audioStartOffset, fileBytes.size - audioStartOffset)

            val newBytes = out.toByteArray()
            val tempFile = File(file.parentFile, "${file.name}.tmp")
            tempFile.writeBytes(newBytes)
            if (!tempFile.renameTo(file)) {
                file.writeBytes(newBytes)
                tempFile.delete()
            }

            logD("Successfully embedded FLAC metadata into ${file.name} (title=$title, artist=$artist)")
            return true
        } catch (e: Exception) {
            logE("Failed to embed FLAC metadata into ${file.name}", e)
            return false
        }
    }
}
