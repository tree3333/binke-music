package com.binke.music.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.binke.music.data.model.Playlist
import com.binke.music.data.model.Song
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "music_data")

class MusicRepository(private val context: Context) {

    private val favoritesKey = stringPreferencesKey("favorites")
    private val historyKey = stringPreferencesKey("history")
    private val searchHistoryKey = stringPreferencesKey("search_history")
    private val customPlaylistsKey = stringPreferencesKey("custom_playlists")

    // 收藏
    suspend fun getFavorites(): List<Song> {
        val json = context.dataStore.data.map { it[favoritesKey] ?: "[]" }.first()
        return parseSongList(json)
    }

    suspend fun addFavorite(song: Song) {
        context.dataStore.edit { prefs ->
            val current = parseSongList(prefs[favoritesKey] ?: "[]").toMutableList()
            if (current.none { it.id == song.id }) {
                current.add(0, song)
                prefs[favoritesKey] = serializeSongList(current)
            }
        }
    }

    suspend fun removeFavorite(songId: String) {
        context.dataStore.edit { prefs ->
            val current = parseSongList(prefs[favoritesKey] ?: "[]").toMutableList()
            current.removeAll { it.id == songId }
            prefs[favoritesKey] = serializeSongList(current)
        }
    }

    suspend fun isFavorite(songId: String): Boolean {
        return getFavorites().any { it.id == songId }
    }

    // 历史
    suspend fun getHistory(): List<Song> {
        val json = context.dataStore.data.map { it[historyKey] ?: "[]" }.first()
        return parseSongList(json)
    }

    suspend fun addToHistory(song: Song) {
        context.dataStore.edit { prefs ->
            val current = parseSongList(prefs[historyKey] ?: "[]").toMutableList()
            current.removeAll { it.id == song.id }
            current.add(0, song)
            if (current.size > 100) current.removeAt(current.lastIndex)
            prefs[historyKey] = serializeSongList(current)
        }
    }

    suspend fun removeFromHistory(songId: String) {
        context.dataStore.edit { prefs ->
            val current = parseSongList(prefs[historyKey] ?: "[]").toMutableList()
            current.removeAll { it.id == songId }
            prefs[historyKey] = serializeSongList(current)
        }
    }

    // 搜索历史
    suspend fun getSearchHistory(): List<String> {
        val json = context.dataStore.data.map { it[searchHistoryKey] ?: "[]" }.first()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun addSearchHistory(query: String) {
        val current = getSearchHistory().toMutableList()
        current.remove(query)
        current.add(0, query)
        if (current.size > 20) current.removeAt(current.lastIndex)
        context.dataStore.edit { prefs ->
            prefs[searchHistoryKey] = JSONArray(current).toString()
        }
    }

    suspend fun clearSearchHistory() {
        context.dataStore.edit { it[searchHistoryKey] = "[]" }
    }

    // 自定义歌单
    suspend fun getCustomPlaylists(): List<Playlist> {
        val json = context.dataStore.data.map { it[customPlaylistsKey] ?: "[]" }.first()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val songs = parseSongList(obj.optString("songs", "[]"))
                Playlist(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    img = obj.optString("img", ""),
                    total = if (obj.has("total")) obj.optInt("total", songs.size) else songs.size,
                    description = obj.optString("description", ""),
                    listenCount = obj.optString("listenCount", ""),
                    creator = obj.optString("creator", ""),
                    musicList = songs
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun createPlaylist(name: String) {
        val current = getCustomPlaylists().toMutableList()
        val newPlaylist = Playlist(
            id = System.currentTimeMillis().toString(),
            name = name,
            img = "",
            total = 0
        )
        current.add(newPlaylist)
        context.dataStore.edit { prefs ->
            prefs[customPlaylistsKey] = serializePlaylists(current)
        }
    }

    suspend fun renamePlaylist(playlistId: String, newName: String) {
        val current = getCustomPlaylists().toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index >= 0) {
            current[index] = current[index].copy(name = newName)
            context.dataStore.edit { prefs ->
                prefs[customPlaylistsKey] = serializePlaylists(current)
            }
        }
    }

    suspend fun deletePlaylist(playlistId: String) {
        val current = getCustomPlaylists().toMutableList()
        current.removeAll { it.id == playlistId }
        context.dataStore.edit { prefs ->
            prefs[customPlaylistsKey] = serializePlaylists(current)
        }
    }

    suspend fun addSongToPlaylist(playlistId: String, song: Song) {
        val current = getCustomPlaylists().toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index >= 0) {
            val songs = current[index].musicList.toMutableList()
            songs.removeAll { it.id == song.id }
            songs.add(0, song)
            current[index] = current[index].copy(musicList = songs, total = songs.size)
            context.dataStore.edit { prefs ->
                prefs[customPlaylistsKey] = serializePlaylists(current)
            }
        }
    }

    suspend fun removeSongFromPlaylist(playlistId: String, songId: String) {
        val current = getCustomPlaylists().toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index >= 0) {
            val songs = current[index].musicList.filterNot { it.id == songId }
            current[index] = current[index].copy(musicList = songs, total = songs.size)
            context.dataStore.edit { prefs ->
                prefs[customPlaylistsKey] = serializePlaylists(current)
            }
        }
    }

    suspend fun getAllPlaylists(): List<Playlist> {
        return getCustomPlaylists()
    }

    private fun parseSongList(json: String): List<Song> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Song(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    artist = obj.optString("artist", "未知歌手"),
                    album = obj.optString("album", ""),
                    duration = obj.optInt("duration", 0),
                    durationText = obj.optString("durationText", "00:00"),
                    pic = obj.optString("pic", ""),
                    musicRid = obj.optString("musicRid", ""),
                    rid = obj.optInt("rid", 0),
                    quality = obj.optString("quality", "标准"),
                    favoriteCount = obj.optString("favoriteCount", "--"),
                    albumId = obj.optString("albumId", ""),
                    playUrl = obj.optString("playUrl", null)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun serializeSongList(songs: List<Song>): String {
        val arr = JSONArray()
        songs.forEach { song ->
            val obj = JSONObject().apply {
                put("id", song.id)
                put("name", song.name)
                put("artist", song.artist)
                put("album", song.album)
                put("duration", song.duration)
                put("durationText", song.durationText)
                put("pic", song.pic)
                put("musicRid", song.musicRid)
                put("rid", song.rid)
                put("quality", song.quality)
                put("favoriteCount", song.favoriteCount)
                put("albumId", song.albumId)
                put("playUrl", song.playUrl)
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun serializePlaylists(playlists: List<Playlist>): String {
        val arr = JSONArray()
        playlists.forEach { pl ->
            val obj = JSONObject().apply {
                put("id", pl.id)
                put("name", pl.name)
                put("img", pl.img)
                put("total", pl.musicList.size.takeIf { it > 0 } ?: pl.total)
                put("description", pl.description)
                put("listenCount", pl.listenCount)
                put("creator", pl.creator)
                put("songs", serializeSongList(pl.musicList))
            }
            arr.put(obj)
        }
        return arr.toString()
    }
}
