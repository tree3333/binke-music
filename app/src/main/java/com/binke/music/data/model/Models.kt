package com.binke.music.data.model

/**
 * 歌曲数据模型
 */
data class Song(
    val id: String,
    val name: String,
    val artist: String,
    val album: String,
    val duration: Int,
    val durationText: String,
    val pic: String,
    val musicRid: String,
    val rid: Int,
    val quality: String = "标准",
    val favoriteCount: String = "--",
    val albumId: String = "",
    var playUrl: String? = null
) {
    companion object {
        fun fromPlaylistJson(json: Map<String, Any?>): Song? {
            val rid = json["rid"]?.toString()?.toIntOrNull() ?: return null
            val name = json["name"]?.toString() ?: return null
            val hasLossless = json["hasLossless"]?.toString()?.toBooleanStrictOrNull() == true
            val artist = json["artist"]?.toString() ?: "未知歌手"
            return Song(
                id = rid.toString(),
                name = name,
                artist = artist,
                album = json["album"]?.toString() ?: "",
                duration = json["duration"]?.toString()?.toIntOrNull() ?: 0,
                durationText = json["songTimeMinutes"]?.toString() ?: formatDuration(json["duration"]?.toString()?.toIntOrNull() ?: 0),
                pic = json["pic"]?.toString() ?: json["albumpic"]?.toString() ?: "",
                musicRid = json["musicrid"]?.toString() ?: "MUSIC_$rid",
                rid = rid,
                quality = when {
                    hasLossless -> "无损"
                    (json["payInfo"] as? Map<*, *>)?.get("down")?.toString()?.contains("1111") == true -> "高品"
                    else -> "标准"
                },
                favoriteCount = json["score100"]?.toString() ?: "--",
                albumId = json["albumid"]?.toString() ?: ""
            )
        }

        fun fromSearchJson(json: Map<String, Any?>): Song? {
            val musicRid = json["MUSICRID"]?.toString() ?: json["musicrid"]?.toString() ?: return null
            val rid = musicRid.removePrefix("MUSIC_").toIntOrNull() ?: return null
            val name = json["NAME"]?.toString() ?: json["SONGNAME"]?.toString() ?: return null
            val artist = json["ARTIST"]?.toString() ?: "未知歌手"
            val duration = json["DURATION"]?.toString()?.toIntOrNull() ?: 0
            // 图片：album > artist pic
            val albumPic = json["web_albumpic_short"]?.toString() ?: ""
            val artistPic = json["web_artistpic_short"]?.toString() ?: ""
            val pic = when {
                albumPic.isNotBlank() -> "https://img2.kuwo.cn/star/albumcover/$albumPic"
                artistPic.isNotBlank() -> "https://img2.kuwo.cn/star/starheads/$artistPic"
                else -> ""
            }
            // 从MINFO判断音质：flac→无损，320→高品
            val minfo = json["MINFO"]?.toString() ?: ""
            val hasFlac = minfo.contains("format:flac", ignoreCase = true)
            val has320 = minfo.contains("bitrate:320", ignoreCase = true)
            // payInfo是嵌套对象，检查play字段
            val payInfoPlay = (json["payInfo"] as? Map<*, *>)?.get("play")?.toString() ?: ""
            val isVipOnly = payInfoPlay.startsWith("00")
            val quality = when {
                isVipOnly -> "VIP"
                hasFlac -> "无损"
                has320 -> "高品"
                else -> "标准"
            }
            return Song(
                id = rid.toString(),
                name = name,
                artist = artist,
                album = json["ALBUM"]?.toString() ?: "",
                duration = duration,
                durationText = formatDuration(duration),
                pic = pic,
                musicRid = musicRid,
                rid = rid,
                quality = quality,
                favoriteCount = json["score100"]?.toString() ?: "--",
                albumId = json["ALBUMID"]?.toString() ?: ""
            )
        }

        private fun decodeBrokenText(text: String): String {
            if (text.isBlank()) return text
            return try {
                val repaired = String(text.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
                if (repaired.contains('�')) text else repaired
            } catch (_: Exception) {
                text
            }
        }

        fun formatDuration(seconds: Int): String {
            val min = seconds / 60
            val sec = seconds % 60
            return String.format("%02d:%02d", min, sec)
        }
    }
}

/**
 * 歌单数据模型
 */
data class Playlist(
    val id: String,
    val name: String,
    val img: String,
    val total: Int,
    val description: String = "",
    val listenCount: String = "",
    val creator: String = "",
    val musicList: List<Song> = emptyList()
) {
    companion object {
        fun fromJson(json: Map<String, Any?>): Playlist? {
            val id = json["id"]?.toString() ?: json["rid"]?.toString() ?: return null
            val name = json["name"]?.toString() ?: return null
            val imgUrl = json["img700"]?.toString()
                ?: json["img500"]?.toString()
                ?: json["pic"]?.toString()
                ?: json["img"]?.toString()
                ?: ""
            val total = json["total"]?.toString()?.toIntOrNull() ?: 0
            return Playlist(
                id = id,
                name = name,
                img = imgUrl,
                total = total,
                description = json["info"]?.toString()?.ifBlank { json["desc"]?.toString() ?: "" }
                    ?: json["desc"]?.toString().orEmpty(),
                listenCount = json["listencnt"]?.toString() ?: "",
                creator = json["userName"]?.toString() ?: json["uname"]?.toString() ?: ""
            )
        }
    }
}

/**
 * 歌词行
 */
data class LrcLine(val time: Float, val text: String)

/**
 * 播放模式
 */
enum class PlayMode { LIST_LOOP, SINGLE_LOOP, SHUFFLE }

/**
 * 页面
 */
enum class Page { HOME, MUSIC, MINE, SEARCH, PLAYLIST_DETAIL }
