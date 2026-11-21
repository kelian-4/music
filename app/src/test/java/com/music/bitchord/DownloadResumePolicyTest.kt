package com.music.bitchord

import com.music.bitchord.data.model.Song
import com.music.bitchord.download.DownloadQueueCodec
import com.music.bitchord.download.DownloadResumePolicy
import com.music.bitchord.download.QueuedDownload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.CancellationException
import org.junit.Test

class DownloadResumePolicyTest {
    @Test
    fun `no failures means no wait`() {
        assertEquals(0L, DownloadResumePolicy.backoffMs(0))
        assertEquals(0L, DownloadResumePolicy.backoffMs(-3))
    }

    @Test
    fun `the wait triples each time`() {
        assertEquals(5_000L, DownloadResumePolicy.backoffMs(1))
        assertEquals(15_000L, DownloadResumePolicy.backoffMs(2))
        assertEquals(45_000L, DownloadResumePolicy.backoffMs(3))
        assertEquals(135_000L, DownloadResumePolicy.backoffMs(4))
        assertEquals(405_000L, DownloadResumePolicy.backoffMs(5))
    }

    @Test
    fun `the wait never passes ten minutes`() {
        assertEquals(600_000L, DownloadResumePolicy.backoffMs(6))
        assertEquals(600_000L, DownloadResumePolicy.backoffMs(40))
        assertEquals(600_000L, DownloadResumePolicy.backoffMs(Int.MAX_VALUE.coerceAtMost(1_000)))
    }

    @Test
    fun `a track is retried until the attempts run out`() {
        assertTrue(DownloadResumePolicy.shouldRetry(0))
        assertTrue(DownloadResumePolicy.shouldRetry(DownloadResumePolicy.MAX_ATTEMPTS - 1))
        assertFalse(DownloadResumePolicy.shouldRetry(DownloadResumePolicy.MAX_ATTEMPTS))
    }

    @Test
    fun `network faults are transient`() {
        assertTrue(DownloadResumePolicy.isTransient(IOException("reset")))
        assertTrue(DownloadResumePolicy.isTransient(SocketTimeoutException("timeout")))
        assertTrue(DownloadResumePolicy.isTransient(UnknownHostException("no host")))
        assertTrue(DownloadResumePolicy.isTransient(RuntimeException("boom")))
    }

    @Test
    fun `server errors and stalled ranges are transient`() {
        assertTrue(DownloadResumePolicy.isTransient(IllegalStateException("Download failed (HTTP 503)")))
        assertTrue(DownloadResumePolicy.isTransient(IllegalStateException("Download failed (HTTP 429)")))
        assertTrue(DownloadResumePolicy.isTransient(IllegalStateException("Download stalled at 1024B")))
        assertTrue(DownloadResumePolicy.isTransient(IllegalStateException("Download refused after 2048B (HTTP 403)")))
        assertTrue(DownloadResumePolicy.isTransient(IllegalStateException("The stream changed mid-download — try again")))
    }

    @Test
    fun `answers written for the listener are final`() {
        assertFalse(DownloadResumePolicy.isTransient(IllegalStateException("Nothing to download")))
        assertFalse(DownloadResumePolicy.isTransient(IllegalStateException("Download failed (HTTP 404)")))
        assertFalse(DownloadResumePolicy.isTransient(IllegalStateException("Track unavailable: no length to fetch")))
        assertFalse(DownloadResumePolicy.isTransient(IllegalArgumentException("Unsupported MIME type audio/webm")))
    }

    @Test
    fun `cancellation is never retried`() {
        assertFalse(DownloadResumePolicy.isTransient(CancellationException("stopped")))
    }

    @Test
    fun `a shorter partial of a known stream is resumed where it ends`() {
        assertEquals(1_000L, DownloadResumePolicy.resumeOffset(partialBytes = 1_000L, recordedTotal = 4_000L))
        assertEquals(3_999L, DownloadResumePolicy.resumeOffset(partialBytes = 3_999L, recordedTotal = 4_000L))
    }

    @Test
    fun `anything else starts over`() {
        assertEquals(0L, DownloadResumePolicy.resumeOffset(0L, 4_000L))
        assertEquals(0L, DownloadResumePolicy.resumeOffset(-5L, 4_000L))
        assertEquals(0L, DownloadResumePolicy.resumeOffset(4_000L, 4_000L))
        assertEquals(0L, DownloadResumePolicy.resumeOffset(5_000L, 4_000L))
        assertEquals(0L, DownloadResumePolicy.resumeOffset(1_000L, null))
        assertEquals(0L, DownloadResumePolicy.resumeOffset(1_000L, 0L))
    }

    private fun song(id: String) = Song(
        videoId = id,
        title = "Title $id",
        artist = "Artist",
        thumbnailUrl = "https://example.test/$id.jpg",
        durationText = "3:21",
        artistId = "UC$id",
        albumId = "MPRE$id",
        albumName = "Album",
        isVideo = false,
    )

    @Test
    fun `the queue survives being written and read back in order`() {
        val entries = listOf(
            QueuedDownload.of(song("a"), from = "MPRElist").copy(failures = 2, partialName = "a.m4a", partialTotal = 4_000_000L),
            QueuedDownload.of(song("b"), from = null),
            QueuedDownload.of(song("c"), from = null),
        )
        val back = DownloadQueueCodec.decode(DownloadQueueCodec.encode(entries))
        assertEquals(entries, back)
        assertEquals(listOf("a", "b", "c"), back.map { it.videoId })
    }

    @Test
    fun `a stored track comes back as the song it was`() {
        val original = song("x")
        val back = QueuedDownload.of(original, from = null).toSong()
        assertEquals(original.videoId, back.videoId)
        assertEquals(original.title, back.title)
        assertEquals(original.artist, back.artist)
        assertEquals(original.thumbnailUrl, back.thumbnailUrl)
        assertEquals(original.albumId, back.albumId)
        assertEquals(original.albumName, back.albumName)
        assertEquals(original.isVideo, back.isVideo)
    }

    @Test
    fun `an unreadable queue is an empty one`() {
        assertTrue(DownloadQueueCodec.decode(null).isEmpty())
        assertTrue(DownloadQueueCodec.decode("").isEmpty())
        assertTrue(DownloadQueueCodec.decode("{not json").isEmpty())
        assertTrue(DownloadQueueCodec.decode("[{\"videoId\":").isEmpty())
    }

    @Test
    fun `fields a later version adds do not break an older reader`() {
        val json = """[{"videoId":"a","title":"T","artist":"A","someFutureField":true}]"""
        assertEquals(listOf("a"), DownloadQueueCodec.decode(json).map { it.videoId })
    }
}
