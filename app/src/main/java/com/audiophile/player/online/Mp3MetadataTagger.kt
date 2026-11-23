package com.audiophile.player.online

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer

/**
 * Embeds standard ID3v2.3 tags (Title, Artist, Album, Year, and APIC Cover Art)
 * into .mp3 audio files and updates the 128-byte ID3v1 footer.
 * Preserves the exact underlying MPEG audio bitstream bit-for-bit without transcoding.
 */
object Mp3MetadataTagger {
    private const val TAG = "Mp3MetadataTagger"

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
        if (!file.exists() || file.length() < 128) return false

        try {
            val fileBytes = file.readBytes()
            var audioStartOffset = 0

            // 1. Detect existing ID3v2 tag at the start of the file
            if (fileBytes.size >= 10 &&
                fileBytes[0] == 'I'.code.toByte() &&
                fileBytes[1] == 'D'.code.toByte() &&
                fileBytes[2] == '3'.code.toByte()
            ) {
                val tagPayloadSize = ((fileBytes[6].toInt() and 0x7F) shl 21) or
                                    ((fileBytes[7].toInt() and 0x7F) shl 14) or
                                    ((fileBytes[8].toInt() and 0x7F) shl 7) or
                                    (fileBytes[9].toInt() and 0x7F)
                val flags = fileBytes[5].toInt()
                val hasFooter = (flags and 0x10) != 0
                val totalOldTagSize = 10 + tagPayloadSize + (if (hasFooter) 10 else 0)
                audioStartOffset = totalOldTagSize.coerceAtMost(fileBytes.size)
            }

            // 2. Detect existing ID3v1 tag at the end of the file (128 bytes)
            var audioEndOffset = fileBytes.size
            if (fileBytes.size >= audioStartOffset + 128) {
                val checkOffset = fileBytes.size - 128
                if (fileBytes[checkOffset] == 'T'.code.toByte() &&
                    fileBytes[checkOffset + 1] == 'A'.code.toByte() &&
                    fileBytes[checkOffset + 2] == 'G'.code.toByte()
                ) {
                    audioEndOffset = checkOffset
                }
            }

            if (audioStartOffset >= audioEndOffset) {
                logE("Audio stream boundaries invalid in ${file.name}")
                return false
            }

            // 3. Build new ID3v2.3 tag
            val framesStream = ByteArrayOutputStream()

            fun writeTextFrame(id: String, text: String) {
                if (text.isBlank()) return
                // ID3v2.3: 0x01 = Unicode with BOM (0xFF, 0xFE = UTF-16LE BOM)
                val bom = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
                val textBytes = text.toByteArray(Charsets.UTF_16LE)
                val payload = byteArrayOf(0x01) + bom + textBytes

                val frameHeader = ByteArrayOutputStream().apply {
                    write(id.toByteArray(Charsets.US_ASCII)) // 4 bytes ID
                    write(ByteBuffer.allocate(4).putInt(payload.size).array()) // 4 bytes size
                    write(byteArrayOf(0, 0)) // 2 bytes flags
                }.toByteArray()

                framesStream.write(frameHeader)
                framesStream.write(payload)
            }

            writeTextFrame("TIT2", title)
            writeTextFrame("TPE1", artist)
            writeTextFrame("TALB", album)
            if (year != null && year > 0u) {
                writeTextFrame("TYER", year.toString())
            }

            // APIC frame for embedded cover art
            if (coverBytes != null && coverBytes.isNotEmpty()) {
                val isPng = coverBytes.size > 8 &&
                            coverBytes[0] == 0x89.toByte() &&
                            coverBytes[1] == 0x50.toByte() &&
                            coverBytes[2] == 0x4E.toByte()
                val mime = (if (isPng) "image/png" else "image/jpeg").toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0)
                val picType = byteArrayOf(3) // 0x03 = Front cover
                val desc = byteArrayOf(0) // empty description null-terminated
                val apicPayload = byteArrayOf(0) + mime + picType + desc + coverBytes

                val apicHeader = ByteArrayOutputStream().apply {
                    write("APIC".toByteArray(Charsets.US_ASCII))
                    write(ByteBuffer.allocate(4).putInt(apicPayload.size).array())
                    write(byteArrayOf(0, 0))
                }.toByteArray()

                framesStream.write(apicHeader)
                framesStream.write(apicPayload)
            }

            val framesBytes = framesStream.toByteArray()
            val synchsafeSize = byteArrayOf(
                ((framesBytes.size shr 21) and 0x7F).toByte(),
                ((framesBytes.size shr 14) and 0x7F).toByte(),
                ((framesBytes.size shr 7) and 0x7F).toByte(),
                (framesBytes.size and 0x7F).toByte()
            )

            val id3v2Header = byteArrayOf(
                'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(),
                0x03, 0x00, // ID3v2.3
                0x00 // flags
            ) + synchsafeSize

            // 4. Build ID3v1.1 tag (128 bytes)
            val id3v1Buf = ByteArray(128)
            id3v1Buf[0] = 'T'.code.toByte()
            id3v1Buf[1] = 'A'.code.toByte()
            id3v1Buf[2] = 'G'.code.toByte()

            fun putPaddedString(str: String, startOffset: Int, maxLen: Int) {
                val b = str.toByteArray(Charsets.ISO_8859_1)
                val len = minOf(b.size, maxLen)
                System.arraycopy(b, 0, id3v1Buf, startOffset, len)
            }

            putPaddedString(title, 3, 30)
            putPaddedString(artist, 33, 30)
            putPaddedString(album, 63, 30)
            if (year != null && year > 0u) {
                putPaddedString(year.toString(), 93, 4)
            }
            id3v1Buf[125] = 0 // ID3v1.1 track number separator
            id3v1Buf[126] = 1 // track 1
            id3v1Buf[127] = 255.toByte() // Genre unknown

            // 5. Assemble new file
            val audioLength = audioEndOffset - audioStartOffset
            val totalSize = id3v2Header.size + framesBytes.size + audioLength + 128
            val out = ByteArrayOutputStream(totalSize)

            out.write(id3v2Header)
            out.write(framesBytes)
            out.write(fileBytes, audioStartOffset, audioLength)
            out.write(id3v1Buf)

            val newBytes = out.toByteArray()
            val tempFile = File(file.parentFile, "${file.name}.tmp")
            tempFile.writeBytes(newBytes)
            if (!tempFile.renameTo(file)) {
                file.writeBytes(newBytes)
                tempFile.delete()
            }

            logD("Successfully embedded ID3v2.3 metadata into ${file.name} (title=$title, artist=$artist)")
            return true
        } catch (e: Exception) {
            logE("Failed to embed ID3v2.3 metadata into ${file.name}", e)
            return false
        }
    }
}
