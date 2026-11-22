package com.music.bitchord.notify

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

data class ReleaseRef(val id: String, val title: String)

data class ReleaseDiff(val fresh: List<ReleaseRef>, val seenAfter: Set<String>)

object NewReleasePolicy {
    const val MAX_SEEN_PER_ARTIST = 300

    const val MAX_NOTIFICATIONS_PER_CHECK = 6

    private const val RELEASE_PREFIX = "MPRE"

    fun isRelease(browseId: String?): Boolean = browseId?.startsWith(RELEASE_PREFIX) == true

    fun diff(seen: Set<String>?, current: List<ReleaseRef>): ReleaseDiff {
        val distinct = current.distinctBy { it.id }
        if (seen == null) return ReleaseDiff(emptyList(), distinct.mapTo(LinkedHashSet()) { it.id }.trimmed())
        val fresh = distinct.filter { it.id !in seen }
        val after = LinkedHashSet<String>().apply {
            distinct.forEach { add(it.id) }
            addAll(seen)
        }
        return ReleaseDiff(fresh, after.trimmed())
    }

    private fun Set<String>.trimmed(): Set<String> =
        if (size <= MAX_SEEN_PER_ARTIST) this else take(MAX_SEEN_PER_ARTIST).toCollection(LinkedHashSet())

    fun forgetUnfollowed(seen: Map<String, Set<String>>, followed: Collection<String>): Map<String, Set<String>> {
        if (followed.isEmpty()) return seen
        val keep = followed.toSet()
        return seen.filterKeys { it in keep }
    }

    fun toAnnounce(findings: List<Pair<String, List<ReleaseRef>>>): List<Pair<String, List<ReleaseRef>>> =
        findings.filter { it.second.isNotEmpty() }.take(MAX_NOTIFICATIONS_PER_CHECK)

    private val serializer = MapSerializer(String.serializer(), ListSerializer(String.serializer()))

    fun encode(seen: Map<String, Set<String>>): String =
        Json.encodeToString(serializer, seen.mapValues { it.value.toList() })

    fun decode(stored: String?): Map<String, Set<String>> {
        if (stored.isNullOrBlank()) return emptyMap()
        return runCatching {
            Json.decodeFromString(serializer, stored).mapValues { it.value.toCollection(LinkedHashSet()) }
        }.getOrDefault(emptyMap())
    }
}
