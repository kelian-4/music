package com.music.bitchord.data.credits

import com.music.bitchord.data.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

object MusicBrainz {
    private const val BASE = "https://musicbrainz.org/ws/2"
    private const val AGENT = "BitChord (https://github.com/kelian-4/music)"
    private const val GAP_MS = 1_100L
    private const val CACHE_SIZE = 64

    private val gate = Mutex()
    private var lastCall = 0L
    private val cache = object : LinkedHashMap<String, TrackCredits?>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TrackCredits?>?) = size > CACHE_SIZE
    }

    suspend fun credits(title: String, artist: String, durationMs: Long?): Result<TrackCredits?> =
        withContext(Dispatchers.IO) {
            val key = "${title.lowercase()}|${artist.lowercase()}"
            synchronized(cache) { if (cache.containsKey(key)) return@withContext Result.success(cache[key]) }
            runCatching {
                val search = "$BASE/recording".toHttpUrl().newBuilder()
                    .addQueryParameter("query", CreditsParser.searchQuery(title, artist))
                    .addQueryParameter("fmt", "json")
                    .addQueryParameter("limit", "5")
                    .build()
                val id = CreditsParser.bestRecordingId(fetch(search.toString()), durationMs)
                val result = id?.let {
                    val lookup = "$BASE/recording/$it".toHttpUrl().newBuilder()
                        .addQueryParameter("inc", "artist-rels+work-rels+work-level-rels")
                        .addQueryParameter("fmt", "json")
                        .build()
                    CreditsParser.parseCredits(fetch(lookup.toString()))
                }?.takeUnless { it.isEmpty }
                synchronized(cache) { cache[key] = result }
                result
            }
        }

    private suspend fun fetch(url: String): String = gate.withLock {
        val wait = lastCall + GAP_MS - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        try {
            val request = Request.Builder().url(url).header("User-Agent", AGENT).header("Accept", "application/json").build()
            Http.client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "MusicBrainz HTTP ${response.code}" }
                response.body?.string() ?: error("MusicBrainz sent an empty answer")
            }
        } finally {
            lastCall = System.currentTimeMillis()
        }
    }
}
