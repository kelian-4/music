package com.music.bitchord.data.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class PlaylistFolder(
    val id: String,
    val name: String,
    val playlistIds: List<String> = emptyList(),
)

object PlaylistFoldersPolicy {
    const val MAX_NAME_LENGTH = 40
    const val MAX_FOLDERS = 50

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(PlaylistFolder.serializer())

    fun cleanName(raw: String): String? =
        raw.trim().replace(Regex("\\s+"), " ").take(MAX_NAME_LENGTH).trim().ifEmpty { null }

    private fun nameTaken(folders: List<PlaylistFolder>, name: String, exceptId: String? = null): Boolean =
        folders.any { it.id != exceptId && it.name.equals(name, ignoreCase = true) }

    fun create(folders: List<PlaylistFolder>, rawName: String, id: String): List<PlaylistFolder>? {
        val name = cleanName(rawName) ?: return null
        if (folders.size >= MAX_FOLDERS || nameTaken(folders, name) || folders.any { it.id == id }) return null
        return folders + PlaylistFolder(id, name)
    }

    fun rename(folders: List<PlaylistFolder>, id: String, rawName: String): List<PlaylistFolder>? {
        val name = cleanName(rawName) ?: return null
        if (folders.none { it.id == id } || nameTaken(folders, name, exceptId = id)) return null
        return folders.map { if (it.id == id) it.copy(name = name) else it }
    }

    fun delete(folders: List<PlaylistFolder>, id: String): List<PlaylistFolder> =
        folders.filterNot { it.id == id }

    fun assign(folders: List<PlaylistFolder>, playlistId: String, folderId: String?): List<PlaylistFolder> =
        folders.map { folder ->
            val without = folder.playlistIds.filterNot { it == playlistId }
            if (folder.id == folderId) folder.copy(playlistIds = without + playlistId) else folder.copy(playlistIds = without)
        }

    fun folderOf(folders: List<PlaylistFolder>, playlistId: String): String? =
        folders.firstOrNull { playlistId in it.playlistIds }?.id

    fun <T> visible(items: List<T>, idOf: (T) -> String?, folders: List<PlaylistFolder>, selectedId: String?): List<T> {
        val folder = folders.firstOrNull { it.id == selectedId } ?: return items
        val ids = folder.playlistIds.toSet()
        return items.filter { item -> idOf(item)?.let { it in ids } == true }
    }

    fun encode(folders: List<PlaylistFolder>): String = json.encodeToString(serializer, folders)

    fun decode(stored: String?): List<PlaylistFolder> {
        val raw = runCatching { json.decodeFromString(serializer, stored ?: "[]") }.getOrDefault(emptyList())
        val seen = HashSet<String>()
        val claimed = HashSet<String>()
        return raw.mapNotNull { folder ->
            val name = cleanName(folder.name)
            if (name == null || folder.id.isBlank() || !seen.add(folder.id)) return@mapNotNull null

            folder.copy(name = name, playlistIds = folder.playlistIds.filter { it.isNotBlank() && claimed.add(it) }.distinct())
        }.take(MAX_FOLDERS)
    }
}
