package com.music.bitchord.data.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

enum class PinKind { PLAYLIST, ALBUM, ARTIST, SONG }

@Serializable
data class PinnedItem(
    val kind: PinKind,

    val id: String,
    val title: String = "",
    val subtitle: String = "",
    val thumbnailUrl: String? = null,
)

enum class PinChange { PINNED, UNPINNED, LIMIT }

class PinResult(val items: List<PinnedItem>, val change: PinChange)

object PinnedItemsPolicy {
    const val MAX_PINNED = 6

    fun toggle(current: List<PinnedItem>, item: PinnedItem): PinResult {
        val index = current.indexOfFirst { it.kind == item.kind && it.id == item.id }
        return when {
            index >= 0 -> PinResult(current.filterIndexed { i, _ -> i != index }, PinChange.UNPINNED)
            current.size >= MAX_PINNED -> PinResult(current, PinChange.LIMIT)
            else -> PinResult(current + item, PinChange.PINNED)
        }
    }

    fun isPinned(current: List<PinnedItem>, kind: PinKind, id: String): Boolean =
        current.any { it.kind == kind && it.id == id }

    fun moveToFront(current: List<PinnedItem>, kind: PinKind, id: String): List<PinnedItem> {
        val index = current.indexOfFirst { it.kind == kind && it.id == id }
        if (index <= 0) return current
        return listOf(current[index]) + current.filterIndexed { i, _ -> i != index }
    }

    fun playlistIds(current: List<PinnedItem>): List<String> =
        current.filter { it.kind == PinKind.PLAYLIST }.map { it.id }

    fun migrate(legacyPlaylistIds: List<String>): List<PinnedItem> =
        legacyPlaylistIds.filter { it.isNotBlank() }.distinct().take(MAX_PINNED)
            .map { PinnedItem(PinKind.PLAYLIST, it) }

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(PinnedItem.serializer())

    fun encode(items: List<PinnedItem>): String = json.encodeToString(serializer, items)

    fun decode(stored: String?): List<PinnedItem>? {
        if (stored == null) return null
        return runCatching { json.decodeFromString(serializer, stored) }.getOrNull()
            ?.filter { it.id.isNotBlank() }
            ?.distinctBy { it.kind to it.id }
            ?.take(MAX_PINNED)
    }
}
