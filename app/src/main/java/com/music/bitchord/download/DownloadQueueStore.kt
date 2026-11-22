package com.music.bitchord.download

import android.content.Context
import android.content.SharedPreferences
import com.music.bitchord.data.model.Song
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class QueuedDownload(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String? = null,
    val durationText: String? = null,
    val artistId: String? = null,
    val albumId: String? = null,
    val albumName: String? = null,
    val isVideo: Boolean = false,

    val from: String? = null,

    val failures: Int = 0,

    val partialName: String? = null,

    val partialTotal: Long? = null,
) {
    fun toSong(): Song = Song(
        videoId = videoId,
        title = title,
        artist = artist,
        thumbnailUrl = thumbnailUrl,
        durationText = durationText,
        artistId = artistId,
        albumId = albumId,
        albumName = albumName,
        isVideo = isVideo,
    )

    companion object {
        fun of(song: Song, from: String?): QueuedDownload = QueuedDownload(
            videoId = song.videoId,
            title = song.title,
            artist = song.artist,
            thumbnailUrl = song.thumbnailUrl,
            durationText = song.durationText,
            artistId = song.artistId,
            albumId = song.albumId,
            albumName = song.albumName,
            isVideo = song.isVideo,
            from = from,
        )
    }
}

internal object DownloadQueueCodec {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(QueuedDownload.serializer())

    fun encode(entries: Collection<QueuedDownload>): String =
        json.encodeToString(serializer, entries.toList())

    fun decode(stored: String?): List<QueuedDownload> =
        runCatching { json.decodeFromString(serializer, stored ?: "[]") }.getOrDefault(emptyList())
}

object DownloadQueueStore {
    private const val KEY = "download_queue"

    private val lock = Any()
    private var prefs: SharedPreferences? = null
    private val entries = LinkedHashMap<String, QueuedDownload>()

    fun init(context: Context) {
        val opened = context.getSharedPreferences("bitchord_settings", Context.MODE_PRIVATE)
        synchronized(lock) {
            prefs = opened
            entries.clear()
            DownloadQueueCodec.decode(opened.getString(KEY, null)).forEach { entries[it.videoId] = it }
        }
    }

    fun contains(videoId: String): Boolean = synchronized(lock) { videoId in entries }

    fun get(videoId: String): QueuedDownload? = synchronized(lock) { entries[videoId] }

    fun all(): List<QueuedDownload> = synchronized(lock) { entries.values.toList() }

    fun add(song: Song, from: String?) = update { if (song.videoId !in it) it[song.videoId] = QueuedDownload.of(song, from) }

    fun remove(videoId: String) = update { it.remove(videoId) }

    fun recordFailure(videoId: String): Int {
        var count = 0
        update { map ->
            val entry = map[videoId] ?: return@update
            count = entry.failures + 1
            map[videoId] = entry.copy(failures = count)
        }
        return count
    }

    fun notePartial(videoId: String, name: String, total: Long) = update { map ->
        val entry = map[videoId] ?: return@update
        if (entry.partialName != name || entry.partialTotal != total) {
            map[videoId] = entry.copy(partialName = name, partialTotal = total)
        }
    }

    fun clearPartial(videoId: String) = update { map ->
        val entry = map[videoId] ?: return@update
        if (entry.partialName != null || entry.partialTotal != null) {
            map[videoId] = entry.copy(partialName = null, partialTotal = null)
        }
    }

    private fun update(change: (MutableMap<String, QueuedDownload>) -> Unit) {
        synchronized(lock) {
            val before = entries.toMap()
            change(entries)
            if (entries != before) {
                prefs?.edit()?.putString(KEY, DownloadQueueCodec.encode(entries.values))?.apply()
            }
        }
    }
}
