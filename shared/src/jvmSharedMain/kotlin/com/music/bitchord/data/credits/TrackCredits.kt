package com.music.bitchord.data.credits

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs

enum class CreditRole { PERFORMER, VOCALS, INSTRUMENT, COMPOSER, LYRICIST, WRITER, ARRANGER, PRODUCER, MIX, ENGINEER, RECORDING, MASTERING }

data class Credit(val name: String, val role: CreditRole, val detail: String? = null)

data class TrackCredits(val recordingId: String, val credits: List<Credit>) {
    val isEmpty: Boolean get() = credits.isEmpty()
}

object CreditsParser {
    private val json = Json { ignoreUnknownKeys = true }

    const val MIN_SEARCH_SCORE = 90
    const val DURATION_TOLERANCE_MS = 5_000L

    private val roles = mapOf(
        "performer" to CreditRole.PERFORMER,
        "vocal" to CreditRole.VOCALS,
        "instrument" to CreditRole.INSTRUMENT,
        "composer" to CreditRole.COMPOSER,
        "lyricist" to CreditRole.LYRICIST,
        "writer" to CreditRole.WRITER,
        "arranger" to CreditRole.ARRANGER,
        "orchestrator" to CreditRole.ARRANGER,
        "producer" to CreditRole.PRODUCER,
        "mix" to CreditRole.MIX,
        "engineer" to CreditRole.ENGINEER,
        "sound" to CreditRole.ENGINEER,
        "recording" to CreditRole.RECORDING,
        "mastering" to CreditRole.MASTERING,
    )

    fun bestRecordingId(searchBody: String, durationMs: Long?): String? {
        val recordings = runCatching { json.parseToJsonElement(searchBody).jsonObject["recordings"]?.jsonArray }
            .getOrNull() ?: return null
        return recordings.mapNotNull { it as? JsonObject }
            .filter { (it["score"]?.jsonPrimitive?.intOrNull ?: 0) >= MIN_SEARCH_SCORE }
            .filter { hit ->
                val length = hit["length"]?.jsonPrimitive?.intOrNull?.toLong()
                durationMs == null || length == null || abs(length - durationMs) <= DURATION_TOLERANCE_MS
            }
            .firstNotNullOfOrNull { it["id"]?.jsonPrimitive?.contentOrNull }
    }

    fun parseCredits(lookupBody: String): TrackCredits? {
        val root = runCatching { json.parseToJsonElement(lookupBody).jsonObject }.getOrNull() ?: return null
        val id = root["id"]?.jsonPrimitive?.contentOrNull ?: return null
        val found = LinkedHashSet<Credit>()
        collect(root["relations"], found)
        (root["relations"] as? JsonArray)?.forEach { relation ->
            val work = (relation as? JsonObject)?.get("work") as? JsonObject
            collect(work?.get("relations"), found)
        }
        return TrackCredits(id, found.toList().sortedBy { it.role.ordinal })
    }

    private fun collect(relations: JsonElement?, into: MutableSet<Credit>) {
        (relations as? JsonArray)?.forEach { element ->
            val relation = element as? JsonObject ?: return@forEach
            val role = roles[relation["type"]?.jsonPrimitive?.contentOrNull?.lowercase()] ?: return@forEach
            val name = (relation["artist"] as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull
                ?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
            val detail = (relation["attributes"] as? JsonArray)
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                ?.filter { it.isNotBlank() }
                ?.joinToString(", ")
                ?.takeIf { it.isNotEmpty() && (role == CreditRole.INSTRUMENT || role == CreditRole.VOCALS || role == CreditRole.PERFORMER) }
            into += Credit(name, role, detail)
        }
    }

    fun searchQuery(title: String, artist: String): String {
        fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")
        return "recording:\"${esc(title)}\" AND artist:\"${esc(artist)}\""
    }
}
