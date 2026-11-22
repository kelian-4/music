package com.music.bitchord.notify

import org.json.JSONArray
import org.json.JSONObject

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

    fun encode(seen: Map<String, Set<String>>): String {
        val root = JSONObject()
        seen.forEach { (artist, ids) -> root.put(artist, JSONArray(ids.toList())) }
        return root.toString()
    }

    fun decode(stored: String?): Map<String, Set<String>> {
        if (stored.isNullOrBlank()) return emptyMap()
        return runCatching {
            val root = JSONObject(stored)
            root.keys().asSequence().associateWith { key ->
                val array = root.getJSONArray(key)
                (0 until array.length()).mapTo(LinkedHashSet()) { array.getString(it) }
            }
        }.getOrDefault(emptyMap())
    }
}
