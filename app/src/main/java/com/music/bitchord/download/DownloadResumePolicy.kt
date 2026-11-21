package com.music.bitchord.download

import java.io.IOException
import java.util.concurrent.CancellationException

object DownloadResumePolicy {
    const val MAX_ATTEMPTS = 6

    private const val BASE_BACKOFF_MS = 5_000L
    private const val BACKOFF_FACTOR = 3L
    private const val MAX_BACKOFF_MS = 10L * 60_000L

    fun backoffMs(failures: Int): Long {
        if (failures <= 0) return 0L
        var wait = BASE_BACKOFF_MS
        repeat(failures - 1) { wait = (wait * BACKOFF_FACTOR).coerceAtMost(MAX_BACKOFF_MS) }
        return wait.coerceAtMost(MAX_BACKOFF_MS)
    }

    fun shouldRetry(failures: Int): Boolean = failures < MAX_ATTEMPTS

    fun isTransient(error: Throwable): Boolean {
        if (error is CancellationException) return false
        if (error is IOException) return true
        if (error is IllegalStateException || error is IllegalArgumentException) {
            val message = error.message.orEmpty()
            return TRANSIENT_PREFIXES.any { message.startsWith(it) } || RETRYABLE_HTTP.containsMatchIn(message)
        }
        return true
    }

    fun resumeOffset(partialBytes: Long, recordedTotal: Long?): Long {
        if (recordedTotal == null || recordedTotal <= 0L) return 0L
        if (partialBytes <= 0L || partialBytes >= recordedTotal) return 0L
        return partialBytes
    }

    private val TRANSIENT_PREFIXES = listOf(
        "Download stalled",
        "Download refused",
        "The stream changed",
    )

    private val RETRYABLE_HTTP = Regex("""HTTP (408|425|429|5\d\d)\)""")
}
