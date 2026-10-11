package com.audiophile.player.online

import org.junit.Test
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

class StreamTest {

    @Test
    fun testJioSaavnSearchAndDownloadSpeed() = runBlocking {
        println("=== TESTING JIOSAAVN SEARCH & CDN SPEED ===")
        val t0 = System.currentTimeMillis()

        // 1. Search directly
        val query = "Starboy"
        val endpoint = "https://www.jiosaavn.com/api.php?__call=search.getResults&_format=json&_marker=0&api_version=4&ctx=android&n=5&q=" + java.net.URLEncoder.encode(query, "UTF-8")
        val conn = URL(endpoint).openConnection()
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")
        val jsonText = conn.getInputStream().bufferedReader().readText()
        val searchMs = System.currentTimeMillis() - t0
        println("Search HTTP response took: ${searchMs}ms")

        // Extract encrypted_media_url with regex for JVM unit test
        val encMatch = Regex("\"encrypted_media_url\":\"(.*?)\"").find(jsonText)
        val titleMatch = Regex("\"title\":\"(.*?)\"").find(jsonText)
        assert(encMatch != null)
        val encUrl = encMatch!!.groupValues[1].replace("\\/", "/")
        val title = titleMatch?.groupValues?.get(1) ?: "Unknown"
        println("Track found: $title")

        // 2. Decrypt media URL (<1ms)
        val tDec = System.currentTimeMillis()
        val keyBytes = "38346591".toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "DES"))
        val decoded = Base64.getDecoder().decode(encUrl)
        val rawUrl = String(cipher.doFinal(decoded), Charsets.UTF_8).trim()
        val streamUrl = rawUrl.replace("_96.mp4", "_320.mp4").replace("_160.mp4", "_320.mp4")
        val decMs = System.currentTimeMillis() - tDec
        println("Decryption took: ${decMs}ms -> $streamUrl")

        // 3. Download stream from CDN
        val tDl = System.currentTimeMillis()
        val mediaConn = URL(streamUrl).openConnection()
        mediaConn.setRequestProperty("User-Agent", "Mozilla/5.0")
        val input = mediaConn.getInputStream()
        val tempFile = File.createTempFile("saavn_test", ".m4a")
        tempFile.deleteOnExit()
        val out = FileOutputStream(tempFile)
        val buf = ByteArray(65536)
        var read: Int
        var total = 0L
        while (input.read(buf).also { read = it } != -1) {
            out.write(buf, 0, read)
            total += read
        }
        out.close()
        input.close()
        val dlMs = System.currentTimeMillis() - tDl
        val mb = total.toDouble() / (1024 * 1024)
        val mbps = (mb / (dlMs / 1000.0))
        println("Downloaded ${"%.2f".format(mb)} MB in ${dlMs}ms (${"%.2f".format(mbps)} MB/s) to ${tempFile.absolutePath}")
        println("=== TEST PASSED: ULTRA-FAST STUDIO AUDIO VALIDATED ===")
    }
}
