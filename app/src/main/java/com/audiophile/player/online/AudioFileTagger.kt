package com.audiophile.player.online

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.audiophile.player.engine.ArtworkCache
import java.io.File

/**
 * Universal Audio File Metadata Tagger and Storage Manager.
 * Embeds tags (Title, Artist, Album, Year, Cover Art) directly into the actual file:
 * - MP4 / M4A / AAC via iTunes atoms
 * - MP3 via ID3v2.3 + ID3v1
 * - FLAC via Vorbis Comments + Picture block
 * Also renames the physical file on disk to clean "Title - Artist.ext",
 * writes companion cover.jpg image, and synchronizes with Android MediaStore.
 */
object AudioFileTagger {
    private const val TAG = "AudioFileTagger"

    fun canWriteFile(file: File): Boolean {
        if (!file.exists()) return false
        if (file.canWrite()) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager()
        }
        return false
    }

    fun embedMetadata(
        context: Context,
        file: File,
        title: String,
        artist: String,
        album: String,
        year: UInt? = null,
        coverBytes: ByteArray? = null
    ): Boolean {
        if (!file.exists()) {
            Log.w(TAG, "File does not exist: ${file.absolutePath}")
            return false
        }

        if (!canWriteFile(file)) {
            Log.w(TAG, "Storage permission missing to write to file: ${file.absolutePath}")
            return false
        }

        val ext = file.extension.lowercase()
        val success = when (ext) {
            "m4a", "mp4", "aac" -> {
                Mp4MetadataTagger.embedMetadata(file, title, artist, album, year, coverBytes)
            }
            "mp3" -> {
                Mp3MetadataTagger.embedMetadata(file, title, artist, album, year, coverBytes)
            }
            "flac" -> {
                FlacMetadataTagger.embedMetadata(file, title, artist, album, year, coverBytes)
            }
            else -> {
                val magic = try {
                    file.inputStream().use { stream ->
                        val buf = ByteArray(12)
                        val read = stream.read(buf)
                        if (read > 0) buf.copyOf(read) else byteArrayOf()
                    }
                } catch (_: Exception) { byteArrayOf() }

                when {
                    magic.size >= 4 && magic[0] == 'f'.code.toByte() && magic[1] == 'L'.code.toByte() && magic[2] == 'a'.code.toByte() && magic[3] == 'C'.code.toByte() -> {
                        FlacMetadataTagger.embedMetadata(file, title, artist, album, year, coverBytes)
                    }
                    magic.size >= 3 && magic[0] == 'I'.code.toByte() && magic[1] == 'D'.code.toByte() && magic[2] == '3'.code.toByte() -> {
                        Mp3MetadataTagger.embedMetadata(file, title, artist, album, year, coverBytes)
                    }
                    magic.size >= 8 && magic[4] == 'f'.code.toByte() && magic[5] == 't'.code.toByte() && magic[6] == 'y'.code.toByte() && magic[7] == 'p'.code.toByte() -> {
                        Mp4MetadataTagger.embedMetadata(file, title, artist, album, year, coverBytes)
                    }
                    else -> {
                        Log.w(TAG, "Unsupported format for tagging: ${file.name}")
                        false
                    }
                }
            }
        }

        return success
    }

    /**
     * Physically renames the audio file on storage to "Title - Artist.ext".
     * Sanitizes illegal filesystem characters. Returns the new File handle.
     */
    fun renameFile(oldFile: File, newTitle: String, newArtist: String): File {
        if (!oldFile.exists()) return oldFile

        val cleanTitle = newTitle
            .replace(Regex("[\\\\/:*?\"<>|]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        val cleanArtist = newArtist
            .replace(Regex("[\\\\/:*?\"<>|]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        val ext = oldFile.extension.lowercase()
        val targetName = if (cleanArtist.isNotBlank() && !cleanTitle.contains(cleanArtist, ignoreCase = true)) {
            "$cleanTitle - $cleanArtist.$ext"
        } else {
            "$cleanTitle.$ext"
        }

        if (oldFile.name == targetName) {
            return oldFile
        }

        val parent = oldFile.parentFile ?: return oldFile
        var targetFile = File(parent, targetName)

        // Avoid collision with an existing different file
        if (targetFile.exists() && targetFile.absolutePath != oldFile.absolutePath) {
            targetFile = File(parent, "$cleanTitle - $cleanArtist (${System.currentTimeMillis() % 10000}).$ext")
        }

        val renamed = try {
            oldFile.renameTo(targetFile)
        } catch (e: Exception) {
            Log.w(TAG, "Failed renaming file on disk", e)
            false
        }

        return if (renamed) {
            Log.i(TAG, "Renamed physical file: ${oldFile.name} -> ${targetFile.name}")
            targetFile
        } else {
            oldFile
        }
    }

    /**
     * Synchronizes Android MediaStore ContentResolver and MediaScanner
     * when a file is tagged and renamed.
     */
    fun updateMediaStore(
        context: Context,
        oldFile: File,
        newFile: File
    ) {
        // If file was renamed, delete old MediaStore record
        if (oldFile.absolutePath != newFile.absolutePath) {
            try {
                context.contentResolver.delete(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    "${MediaStore.Audio.Media.DATA} = ?",
                    arrayOf(oldFile.absolutePath)
                )
            } catch (e: Exception) {
                Log.w(TAG, "Error removing old MediaStore path: ${oldFile.absolutePath}", e)
            }
        }

        val filesToScan = arrayOf(newFile.absolutePath)

        try {
            MediaScannerConnection.scanFile(
                context,
                filesToScan,
                null
            ) { path, uri ->
                Log.i(TAG, "MediaScanner registered file: $path -> $uri")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error scanning with MediaScanner", e)
        }
    }
}
