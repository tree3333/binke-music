package com.binke.music.data.api

import android.util.Base64
import android.util.Log
import com.binke.music.data.model.LrcLine
import com.binke.music.data.model.Playlist
import com.binke.music.data.model.Song
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/**
 * getPlayUrl 返回结果，包含地址和调试信息
 */
data class PlayUrlResult(
    val url: String?,
    val debugInfo: String
)

/**
 * 酷我 kwDES 加解密
 * 完整移植自 ~/doc/kuwo_des.py (yeyuchen198/kuwo_des)
 * 密钥: ylzsxkwm
 */
class kwDES {
    companion object {
        private val KEY = byteArrayOf(121, 108, 122, 115, 120, 107, 119, 109)
        private val MASK = LongArray(64) { 1L shl it }
        private val IP = intArrayOf(
            57, 49, 41, 33, 25, 17, 9, 1, 59, 51, 43, 35, 27, 19, 11, 3,
            61, 53, 45, 37, 29, 21, 13, 5, 63, 55, 47, 39, 31, 23, 15, 7,
            56, 48, 40, 32, 24, 16, 8, 0, 58, 50, 42, 34, 26, 18, 10, 2,
            60, 52, 44, 36, 28, 20, 12, 4, 62, 54, 46, 38, 30, 22, 14, 6
        )
        private val E = intArrayOf(
            31, 0, 1, 2, 3, 4, -1, -1, 3, 4, 5, 6, 7, 8, -1, -1,
            7, 8, 9, 10, 11, 12, -1, -1, 11, 12, 13, 14, 15, 16, -1, -1,
            15, 16, 17, 18, 19, 20, -1, -1, 19, 20, 21, 22, 23, 24, -1, -1,
            23, 24, 25, 26, 27, 28, -1, -1, 27, 28, 29, 30, 31, 30, -1, -1
        )
        private val S0 = intArrayOf(14,4,3,15,2,13,5,3,13,14,6,9,11,2,0,5,4,1,10,12,15,6,9,10,1,8,12,7,8,11,7,0,0,15,10,5,14,4,9,10,7,8,12,3,13,1,3,6,15,12,6,11,2,9,5,0,4,2,11,14,1,7,8,13)
        private val S1 = intArrayOf(15,0,9,5,6,10,12,9,8,7,2,12,3,13,5,2,1,14,7,8,11,4,0,3,14,11,13,6,4,1,10,15,3,13,12,11,15,3,6,0,4,10,1,7,8,4,11,14,13,8,0,6,2,15,9,5,7,1,10,12,14,2,5,9)
        private val S2 = intArrayOf(10,13,1,11,6,8,11,5,9,4,12,2,15,3,2,14,0,6,13,1,3,15,4,10,14,9,7,12,5,0,8,7,13,1,2,4,3,6,12,11,0,13,5,14,6,8,15,2,7,10,8,15,4,9,11,5,9,0,14,3,10,7,1,12)
        private val S3 = intArrayOf(7,10,1,15,0,12,11,5,14,9,8,3,9,7,4,8,13,6,2,1,6,11,12,2,3,0,5,14,10,13,15,4,13,3,4,9,6,10,1,12,11,0,2,5,0,13,14,2,8,15,7,4,15,1,10,7,5,6,12,11,3,8,9,14)
        private val S4 = intArrayOf(2,4,8,15,7,10,13,6,4,1,3,12,11,7,14,0,12,2,5,9,10,13,0,3,1,11,15,5,6,8,9,14,14,11,5,6,4,1,3,10,2,12,15,0,13,2,8,5,11,8,0,15,7,14,9,4,12,7,10,9,1,13,6,3)
        private val S5 = intArrayOf(12,9,0,7,9,2,14,1,10,15,3,4,6,12,5,11,1,14,13,0,2,8,7,13,15,5,4,10,8,3,11,6,10,4,6,11,7,9,0,6,4,2,13,1,9,15,3,8,15,3,1,14,12,5,11,0,2,12,14,7,5,10,8,13)
        private val S6 = intArrayOf(4,1,3,10,15,12,5,0,2,11,9,6,8,7,6,9,11,4,12,15,0,3,10,5,14,13,7,8,13,14,1,2,13,6,14,9,4,1,2,14,11,13,5,0,1,10,8,3,0,11,3,5,9,4,15,2,7,8,12,15,10,7,6,12)
        private val S7 = intArrayOf(13,7,10,0,6,9,5,15,8,4,3,10,11,14,12,5,2,11,9,6,15,12,0,3,4,1,14,13,1,2,7,8,1,2,12,15,10,4,0,3,13,14,6,9,7,8,9,6,15,1,5,12,3,10,14,5,8,7,11,0,4,13,2,11)
        private val S_BOXES = arrayOf(S0, S1, S2, S3, S4, S5, S6, S7)
        private val P = intArrayOf(15, 6, 19, 20, 28, 11, 27, 16, 0, 14, 22, 25, 4, 17, 30, 9, 1, 7, 23, 13, 31, 26, 2, 8, 18, 12, 29, 5, 21, 10, 3, 24)
        private val IP1 = intArrayOf(
            39, 7, 47, 15, 55, 23, 63, 31, 38, 6, 46, 14, 54, 22, 62, 30,
            37, 5, 45, 13, 53, 21, 61, 29, 36, 4, 44, 12, 52, 20, 60, 28,
            35, 3, 43, 11, 51, 19, 59, 27, 34, 2, 42, 10, 50, 18, 58, 26,
            33, 1, 41, 9, 49, 17, 57, 25, 32, 0, 40, 8, 48, 16, 56, 24
        )
        private val PC1 = intArrayOf(
            56, 48, 40, 32, 24, 16, 8, 0, 57, 49, 41, 33, 25, 17, 9, 1,
            58, 50, 42, 34, 26, 18, 10, 2, 59, 51, 43, 35, 62, 54, 46, 38,
            30, 22, 14, 6, 61, 53, 45, 37, 29, 21, 13, 5, 60, 52, 44, 36,
            28, 20, 12, 4, 27, 19, 11, 3
        )
        private val PC2 = intArrayOf(
            13, 16, 10, 23, 0, 4, -1, -1, 2, 27, 14, 5, 20, 9, -1, -1,
            22, 18, 11, 3, 25, 7, -1, -1, 15, 6, 26, 19, 12, 1, -1, -1,
            40, 51, 30, 36, 46, 54, -1, -1, 29, 39, 50, 44, 32, 47, -1, -1,
            43, 48, 38, 55, 33, 52, -1, -1, 45, 41, 49, 35, 28, 31, -1, -1
        )
        private val LS = intArrayOf(1, 1, 2, 2, 2, 2, 2, 2, 1, 2, 2, 2, 2, 2, 2, 1)
        private val LS_MASK = longArrayOf(0x0L, 0x100001L, 0x300003L)
        private const val MASK32 = 0xFFFFFFFFL

        private fun bitTransform(array: IntArray, length: Int, source: Long): Long {
            var dest = 0L
            for (i in 0 until length) {
                if (array[i] >= 0 && (source and MASK[array[i]]) != 0L) {
                    dest = dest or MASK[i]
                }
            }
            return dest
        }

        private fun desSubKeys(key: Long, k: LongArray, decrypt: Boolean) {
            var temp = bitTransform(PC1, 56, key)
            for (j in 0..15) {
                val lsVal = LS[j]
                val mask = LS_MASK[lsVal]
                val left = (temp and mask) shl (28 - lsVal)
                val right = ((temp and mask.inv()) and ((1L shl 56) - 1)) ushr lsVal
                temp = left or right
                k[j] = bitTransform(PC2, 64, temp)
            }
            if (decrypt) {
                for (j in 0..7) {
                    val tmp = k[j]
                    k[j] = k[15 - j]
                    k[15 - j] = tmp
                }
            }
        }

        private fun des64(subKeys: LongArray, data: Long): Long {
            var out = bitTransform(IP, 64, data)
            var left = out and MASK32
            var right = (out ushr 32) and MASK32
            for (i in 0..15) {
                val origRight = right
                var r = bitTransform(E, 64, origRight)
                r = r xor subKeys[i]
                val pR = LongArray(8) { k -> (r ushr (k * 8)) and 0xFF }
                var sOut = 0L
                for (sbi in 7 downTo 0) {
                    sOut = (sOut shl 4) or S_BOXES[sbi][pR[sbi].toInt()].toLong()
                }
                r = bitTransform(P, 32, sOut)
                val newRight = left xor r
                left = origRight
                right = newRight
            }
            val tmp = left
            left = right
            right = tmp
            val combined = ((right and MASK32) shl 32) or (left and MASK32)
            return bitTransform(IP1, 64, combined)
        }

        private fun bytesToBlock(data: ByteArray, offset: Int): Long {
            var block = 0L
            for (j in 0..7) {
                block = block or ((data[offset + j].toLong() and 0xFFL) shl (j * 8))
            }
            return block
        }

        private fun blockToBytes(block: Long, out: ByteArray, offset: Int) {
            for (j in 0..7) {
                out[offset + j] = ((block ushr (j * 8)) and 0xFFL).toByte()
            }
        }

        fun encryptBytes(plaintext: ByteArray): ByteArray {
            val padLen = if (plaintext.size % 8 != 0) 8 - (plaintext.size % 8) else 0
            val padded = if (padLen > 0) plaintext + ByteArray(padLen) else plaintext
            val num = padded.size / 8
            var keyLong = 0L
            for (i in 0..7) keyLong = keyLong or ((KEY[i].toLong() and 0xFFL) shl (i * 8))
            val subKey = LongArray(16)
            desSubKeys(keyLong, subKey, decrypt = false)
            val encrypted = ByteArray(num * 8)
            for (i in 0 until num) {
                val block = bytesToBlock(padded, i * 8)
                val cipher = des64(subKey, block)
                blockToBytes(cipher, encrypted, i * 8)
            }
            return encrypted
        }

        fun decryptBytes(ciphertext: ByteArray): ByteArray {
            val num = ciphertext.size / 8
            var keyLong = 0L
            for (i in 0..7) keyLong = keyLong or ((KEY[i].toLong() and 0xFFL) shl (i * 8))
            val subKey = LongArray(16)
            desSubKeys(keyLong, subKey, decrypt = true)
            val decrypted = ByteArray(num * 8)
            for (i in 0 until num) {
                val block = bytesToBlock(ciphertext, i * 8)
                val plain = des64(subKey, block)
                blockToBytes(plain, decrypted, i * 8)
            }
            return decrypted
        }

        fun encrypt(plaintext: String): String {
            return Base64.encodeToString(encryptBytes(plaintext.toByteArray(Charsets.UTF_8)), Base64.DEFAULT)
        }

        fun decrypt(ciphertextB64: String): String {
            val raw = Base64.decode(ciphertextB64, Base64.NO_WRAP)
            val decrypted = decryptBytes(raw)
            var end = decrypted.size - 1
            while (end >= 0 && decrypted[end] == 0.toByte()) end--
            return String(decrypted, 0, end + 1, Charsets.UTF_8)
        }
    }
}

class KuwoApiService {

    companion object {
        private const val REFERER = "http://www.kuwo.cn/"
        private const val KUWO_UA = "kwplayerhd_ar_4.3.0.8_tianbao_T1A_qirui"
        private const val BROWSER_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"

        init {
            val params = "user=0&android_id=0&prod=kwplayerhd_ar_4.3.0.8&corp=kuwo&vipver=4.3.0.8&source=kwplayerhd_ar_4.3.0.8_tianbao_T1A_qirui.apk&notrace=0&type=convert_url2&br=320&format=mp3&sig=0&rid=29530546&priority=bitrate&loginUid=0&network=WIFI&loginSid=0&mode=down"
            val encrypted = kwDES.encrypt(params)
            val decrypted = kwDES.decrypt(encrypted)
            Log.d("KuwoApi", "kwDES decrypt roundtrip OK: ${params == decrypted}")
        }
    }

    private val browserClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            val body = response.body ?: return@addInterceptor response
            val contentEncoding = response.header("Content-Encoding")
            if (contentEncoding.equals("gzip", ignoreCase = true)) {
                val source = body.source()
                source.request(Long.MAX_VALUE)
                val buffer = source.buffer
                val gzipBytes = buffer.clone().readByteArray()
                try {
                    val decompressed = GZIPInputStream(gzipBytes.inputStream()).readBytes()
                    val decompressedBody = okhttp3.ResponseBody.create(body.contentType(), decompressed)
                    return@addInterceptor response.newBuilder().body(decompressedBody)
                        .removeHeader("Content-Encoding").build()
                } catch (e: Exception) {
                    Log.e("KuwoApi", "gzip decompress failed", e)
                    return@addInterceptor response
                }
            }
            return@addInterceptor response
        }
        .build()

    private val playerClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("User-Agent", KUWO_UA)
                .addHeader("Referer", REFERER)
                .build()
            chain.proceed(request)
        }
        .build()

    private fun get(urlStr: String): String {
        val request = Request.Builder()
            .url(urlStr)
            .addHeader("User-Agent", BROWSER_UA)
            .addHeader("Referer", REFERER)
            .build()
        return browserClient.newCall(request).execute().body?.string() ?: ""
    }

    /**
     * 获取推荐歌单
     */
    fun getRecommendPlaylists(pn: Int = 1, rn: Int = 20): List<Playlist> {
        return try {
            val url = "http://wapi.kuwo.cn/api/www/rcm/index/playlist?id=0&pn=$pn&rn=$rn"
            val response = get(url)
            val json = JSONObject(response)
            val data = json.optJSONObject("data") ?: return emptyList()
            val list = data.optJSONArray("list") ?: return emptyList()
            (0 until list.length()).mapNotNull { i ->
                Playlist.fromJson(parseJsonObject(list.getJSONObject(i)))
            }
        } catch (e: Exception) {
            Log.e("KuwoApi", "getRecommendPlaylists error", e)
            emptyList()
        }
    }

    /**
     * 获取歌单详情
     */
    fun getPlaylistDetail(pid: String, pn: Int = 1, rn: Int = 50): Playlist? {
        return try {
            val url = "http://wapi.kuwo.cn/api/www/playlist/playListInfo?pid=$pid&pn=$pn&rn=$rn"
            val response = get(url)
            val json = JSONObject(response)
            val data = json.optJSONObject("data") ?: return null
            val playlist = Playlist.fromJson(parseJsonObject(data)) ?: return null
            val musicListArray = data.optJSONArray("musicList") ?: return playlist
            val songs = (0 until musicListArray.length()).mapNotNull { i ->
                Song.fromPlaylistJson(parseJsonObject(musicListArray.getJSONObject(i)))
            }
            playlist.copy(musicList = songs, total = songs.size)
        } catch (e: Exception) {
            Log.e("KuwoApi", "getPlaylistDetail error", e)
            null
        }
    }

    /**
     * 搜索歌曲
     */
    fun searchSongs(keyword: String, pn: Int = 0, rn: Int = 30): List<Song> {
        return try {
            val encoded = URLEncoder.encode(keyword, "UTF-8")
            val url = "https://www.kuwo.cn/search/searchMusicBykeyWord?all=$encoded&vipver=1&client=kt&ft=music&cluster=0&strategy=2012&encoding=utf8&rformat=json&mobi=1&issubtitle=1&show_copyright_off=1&pn=$pn&rn=$rn"
            val response = getWithCookie(url)
            val json = JSONObject(response)
            val absList = json.optJSONArray("abslist") ?: JSONArray()
            (0 until absList.length()).mapNotNull { i ->
                Song.fromSearchJson(parseJsonObject(absList.getJSONObject(i)))
            }
        } catch (e: Exception) {
            Log.e("KuwoApi", "searchSongs error", e)
            emptyList()
        }
    }

    private fun getWithCookie(urlStr: String): String {
        val request = Request.Builder()
            .url(urlStr)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.110 Safari/537.36")
            .addHeader("Referer", "https://www.kuwo.cn/")
            .addHeader("Cookie", "kw_token=RWQBXNPIE1K")
            .addHeader("csrf", "RWQBXNPIE1K")
            .build()
        return browserClient.newCall(request).execute().use { response ->
            val body = response.body ?: return ""
            val contentEncoding = response.header("Content-Encoding") ?: ""
            if (contentEncoding.equals("gzip", ignoreCase = true)) {
                try {
                    String(GZIPInputStream(body.byteStream()).readBytes(), Charset.forName("UTF-8"))
                } catch (e: Exception) {
                    body.string()
                }
            } else {
                body.string()
            }
        }
    }

    /**
     * 获取搜索联想
     */
    fun getSearchSuggestions(keyword: String): List<String> {
        return try {
            val encoded = URLEncoder.encode(keyword, "UTF-8")
            val url = "http://wapi.kuwo.cn/api/www/search/searchKey?key=$encoded&httpsStatus=1&pn=1&rn=12"
            val response = get(url)
            val json = JSONObject(response)
            val data = json.optJSONArray("data") ?: return emptyList()
            (0 until data.length()).mapNotNull { i ->
                val raw = data.optString(i)
                raw.lineSequence()
                    .firstOrNull { it.startsWith("RELWORD=") }
                    ?.substringAfter("RELWORD=")
                    ?.takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            Log.e("KuwoApi", "getSearchSuggestions error", e)
            emptyList()
        }
    }

    /**
     * 获取播放地址（自动重试，最多重试3次）
     */
    fun getPlayUrl(rid: String, br: Int = 320, type: String = "mp3"): PlayUrlResult {
        val maxRetries = 3
        var lastException: Exception? = null

        for (attempt in 1..maxRetries) {
            try {
                val musicId = rid.removePrefix("MUSIC_")
                val qParams = "user=0&android_id=0&prod=kwplayerhd_ar_4.3.0.8&corp=kuwo&vipver=4.3.0.8&source=kwplayerhd_ar_4.3.0.8_tianbao_T1A_qirui.apk&notrace=0&type=convert_url2&br=$br&format=$type&sig=0&rid=$musicId&priority=bitrate&loginUid=0&network=WIFI&loginSid=0&mode=down"
                val encrypted = kwDES.encrypt(qParams)
                val encoded = URLEncoder.encode(encrypted, "UTF-8")
                val url = "https://nmobi.kuwo.cn/mobi.s?f=kuwo&q=$encoded"
                Log.d("KuwoApi", "getPlayUrl attempt=$attempt url=$url")
                val request = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", KUWO_UA)
                    .addHeader("Accept", "*/*")
                    .addHeader("Accept-Language", "zh-CN,zh;q=0.9")
                    .addHeader("Host", "nmobi.kuwo.cn")
                    .addHeader("Connection", "Keep-Alive")
                    .build()
                val response = playerClient.newCall(request).execute().body?.string() ?: ""

                val kvUrl = response.lineSequence()
                    .map { it.trim() }
                    .firstOrNull { it.startsWith("url=", ignoreCase = true) }
                    ?.substringAfter("url=")
                    ?.trim()
                    ?.takeIf { it.startsWith("http", ignoreCase = true) }

                val regexUrl = Regex("url=(https?://[^\\s]+)", RegexOption.IGNORE_CASE)
                    .find(response)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.trim()

                val afterSig = response.substringAfter("sig=", "").take(20)
                val finalUrl = when {
                    !kvUrl.isNullOrBlank() -> kvUrl
                    !regexUrl.isNullOrBlank() -> regexUrl
                    response.trim().startsWith("http") -> response.trim()
                    else -> null
                }?.substringBefore("sig=")

                val debugInfo = buildString {
                    append("attempt=$attempt\n")
                    append("原始响应前200字符:\n${response.take(200)}\n\n")
                    append("kvUrl=$kvUrl\n")
                    append("regexUrl=$regexUrl\n")
                    append("response以http开头=${response.trim().startsWith("http")}\n")
                    append("finalUrl=$finalUrl\n")
                    append("sig=后前20字符: $afterSig")
                }
                Log.d("KuwoApi", "getPlayUrl attempt=$attempt finalUrl=$finalUrl")

                // 如果拿到有效地址立即返回
                if (!finalUrl.isNullOrBlank()) {
                    return PlayUrlResult(finalUrl, debugInfo)
                }

                // 无效地址也算失败，需要重试
                Log.w("KuwoApi", "getPlayUrl attempt=$attempt 返回地址为空，将重试")

            } catch (e: Exception) {
                lastException = e
                Log.w("KuwoApi", "getPlayUrl attempt=$attempt 异常: ${e.message}，将重试")
            }

            // 不是最后一次，等待后重试
            if (attempt < maxRetries) {
                try {
                    Thread.sleep(1000)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }

        Log.e("KuwoApi", "getPlayUrl 重试${maxRetries}次后仍失败")
        return PlayUrlResult(null, "重试${maxRetries}次失败：${lastException?.message ?: "地址为空"}")
    }

    /**
     * 获取歌词
     * @return Result.success(list) 或 Result.failure(exception)
     */
    fun getLyrics(musicId: String): Result<List<LrcLine>> {
        return try {
            val url = "http://m.kuwo.cn/newh5/singles/songinfoandlrc?musicId=$musicId&httpsStatus=1"
            val response = get(url)
            val json = JSONObject(response)
            if (json.optInt("status") != 200) return Result.failure(Exception("酷我歌词接口状态码非200"))
            val data = json.optJSONObject("data") ?: return Result.success(emptyList())
            val lrclist = data.optJSONArray("lrclist") ?: return Result.success(emptyList())
            val lines = (0 until lrclist.length()).mapNotNull { i ->
                val item = lrclist.getJSONObject(i)
                val time = item.optString("time", "0").toFloatOrNull() ?: 0f
                val text = item.optString("lineLyric", "").trim()
                if (text.isNotEmpty()) LrcLine(time, text) else null
            }
            Result.success(lines)
        } catch (e: Exception) {
            Log.e("KuwoApi", "getLyrics error", e)
            Result.failure(e)
        }
    }

    /**
     * 网易云歌词搜索（备用）
     * @return Result.success(list) 或 Result.failure(exception)
     */
    fun searchLyricsNetEase(name: String, artist: String): Result<List<LrcLine>> {
        return try {
            val searchName = "${name.trim()} ${artist.trim()}"
            val encoded = URLEncoder.encode(searchName, "UTF-8")
            val searchUrl = "https://music.163.com/api/search/get"
            val body = "s=$encoded&type=1&limit=1&offset=0".toRequestBody("application/x-www-form-urlencoded".toMediaType())
            val request = Request.Builder()
                .url(searchUrl)
                .post(body)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .addHeader("Referer", "https://music.163.com/")
                .build()
            val respStr = browserClient.newCall(request).execute().use { it.body?.string() ?: return Result.failure(Exception("网易云搜索请求为空")) }
            val searchJson = JSONObject(respStr)
            val songs = searchJson.optJSONObject("result")?.optJSONArray("songs") ?: return Result.success(emptyList())
            if (songs.length() == 0) return Result.success(emptyList())
            val songId = songs.getJSONObject(0).optString("id") ?: return Result.success(emptyList())

            val lrcUrl = "https://music.163.com/api/song/lyric?id=$songId&lv=1&kv=1&tv=-1"
            val lrcRequest = Request.Builder()
                .url(lrcUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .addHeader("Referer", "https://music.163.com/")
                .build()
            val lrcStr = browserClient.newCall(lrcRequest).execute().use { it.body?.string() ?: return Result.failure(Exception("网易云歌词请求为空")) }
            val lrcJson = JSONObject(lrcStr)
            val lrcText = lrcJson.optJSONObject("lrc")?.optString("lyric") ?: return Result.success(emptyList())

            Result.success(parseLrcText(lrcText))
        } catch (e: Exception) {
            Log.e("KuwoApi", "searchLyricsNetEase error", e)
            Result.failure(e)
        }
    }

    /**
     * QQ音乐歌词搜索（备用）
     * @return Result.success(list) 或 Result.failure(exception)
     */
    fun searchLyricsQQ(name: String, artist: String): Result<List<LrcLine>> {
        return try {
            val encoded = URLEncoder.encode(name.trim(), "UTF-8")
            val searchUrl = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?w=$encoded&format=json&p=1&n=5"
            val request = Request.Builder()
                .url(searchUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .addHeader("Referer", "https://y.qq.com/")
                .build()
            val respStr = browserClient.newCall(request).execute().use { it.body?.string() ?: return Result.failure(Exception("QQ音乐搜索请求为空")) }
            val json = JSONObject(respStr)
            val songs = json.optJSONObject("data")?.optJSONObject("song")?.optJSONArray("list") ?: return Result.success(emptyList())

            var songmid: String? = null
            for (i in 0 until songs.length()) {
                val s = songs.getJSONObject(i)
                val singers = s.optJSONArray("singer")
                val singerNames = (0 until (singers?.length() ?: 0)).mapNotNull { singers?.getJSONObject(it)?.optString("name") }
                if (singerNames.any { it.contains(artist) || artist.contains(it) } || artist.isBlank()) {
                    songmid = s.optString("songmid")
                    break
                }
            }
            if (songmid == null && songs.length() > 0) {
                songmid = songs.getJSONObject(0).optString("songmid")
            }
            if (songmid == null) return Result.success(emptyList())

            val lrcUrl = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid=$songmid&format=json&nobase64=1"
            val lrcRequest = Request.Builder()
                .url(lrcUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .addHeader("Referer", "https://y.qq.com/portal/player.html")
                .build()
            val lrcResp = browserClient.newCall(lrcRequest).execute().use { it.body?.string() ?: return Result.failure(Exception("QQ音乐歌词请求为空")) }

            val lrcJsonStr = lrcResp.trim().let {
                val start = it.indexOf('{')
                val end = it.lastIndexOf('}') + 1
                if (start >= 0 && end > start) it.substring(start, end) else it
            }
            val lrcJson = JSONObject(lrcJsonStr)
            val lrcText = lrcJson.optString("lyric", "").takeIf { it.isNotBlank() }
                ?: lrcJson.optJSONObject("lyric")?.optString("lyric", "")?.takeIf { it.isNotBlank() }
                ?: return Result.success(emptyList())

            Result.success(parseLrcText(lrcText))
        } catch (e: Exception) {
            Log.e("KuwoApi", "searchLyricsQQ error", e)
            Result.failure(e)
        }
    }

    private fun parseLrcText(lrcText: String): List<LrcLine> {
        return lrcText.lineSequence()
            .mapNotNull { line ->
                val trimmed = line.trim()
                if (!trimmed.startsWith("[")) return@mapNotNull null
                val endIdx = trimmed.indexOf(']')
                if (endIdx < 1) return@mapNotNull null
                val timeStr = trimmed.substring(1, endIdx)
                val text = trimmed.substring(endIdx + 1).trim()
                if (text.isEmpty()) return@mapNotNull null
                val time = parseLrcTime(timeStr)
                if (time != null) LrcLine(time, text) else null
            }
            .toList()
    }

    private fun parseLrcTime(timeStr: String): Float? {
        // 支持 [mm:ss.xxx] 或 [mm:ss.xx] 或 [mm:ss]
        return try {
            val parts = timeStr.split(":")
            if (parts.size != 2) return null
            val min = parts[0].toIntOrNull() ?: return null
            val secPart = parts[1]
            val secParts = secPart.split(".")
            val sec = secParts[0].toIntOrNull() ?: return null
            val ms = if (secParts.size > 1) {
                val msStr = secParts[1].padEnd(3, '0').take(3)
                msStr.toIntOrNull() ?: 0
            } else 0
            min * 60f + sec + ms / 1000f
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 从 iTunes 搜索高清封面，失败返回空字符串
     */
    fun getCoverFromItunes(artist: String, name: String): String {
        return try {
            val query = URLEncoder.encode("${artist.trim()} ${name.trim()}", "UTF-8")
            val url = "https://itunes.apple.com/search?term=$query&entity=song&limit=1&country=CN"
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", BROWSER_UA)
                .build()
            val response = browserClient.newCall(request).execute().use { it.body?.string() ?: "" }
            val json = JSONObject(response)
            val results = json.optJSONArray("results") ?: return ""
            if (results.length() == 0) return ""
            val artwork = results.getJSONObject(0).optString("artworkUrl100", "")
            if (artwork.isBlank()) return ""
            // 把 100x100 换成 1000x1000 拿高清
            artwork.replace("100x100bb", "1000x1000bb")
        } catch (e: Exception) {
            Log.e("KuwoApi", "getCoverFromItunes error", e)
            ""
        }
    }
    /**
     * 获取排行榜
     */
    fun getBangMenu(): List<Playlist> {
        return try {
            val url = "http://wapi.kuwo.cn/api/www/bang/bang/bangMenu"
            val response = get(url)
            val json = JSONObject(response)
            val data = json.optJSONArray("data") ?: return emptyList()
            val playlists = mutableListOf<Playlist>()
            for (i in 0 until data.length()) {
                val category = data.getJSONObject(i)
                val list = category.optJSONArray("list") ?: continue
                for (j in 0 until list.length()) {
                    Playlist.fromJson(parseJsonObject(list.getJSONObject(j)))?.let { playlists.add(it) }
                }
            }
            playlists
        } catch (e: Exception) {
            Log.e("KuwoApi", "getBangMenu error", e)
            emptyList()
        }
    }

    private fun parseJsonObject(json: JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        val iterator = json.keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            val value = json.opt(key)
            map[key] = when (value) {
                is JSONObject -> parseJsonObject(value)
                is JSONArray -> value.toString()
                JSONObject.NULL -> null
                else -> value
            }
        }
        return map
    }

    @Suppress("unused")
    private fun decodeIfBroken(text: String): String {
        return try {
            val repaired = String(text.toByteArray(Charset.forName("ISO-8859-1")), Charsets.UTF_8)
            if (repaired.contains('�')) text else repaired
        } catch (_: Exception) {
            text
        }
    }
}
