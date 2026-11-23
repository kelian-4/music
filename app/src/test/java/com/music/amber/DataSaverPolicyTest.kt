package com.music.amber

import com.music.amber.data.settings.AudioQuality
import com.music.amber.data.settings.DataSaverPolicy
import com.music.amber.data.settings.DataSaverValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataSaverPolicyTest {
    private fun values(
        quality: AudioQuality,
        wifiOnly: Boolean,
        canvas: Boolean,
    ) = DataSaverValues(quality, wifiOnly, canvas)

    @Test
    fun `a high mobile ceiling is lowered to the cap`() {
        AudioQuality.entries.filter { it != AudioQuality.LOW }.forEach { quality ->
            val applied = DataSaverPolicy.applied(values(quality, wifiOnly = false, canvas = true))
            assertEquals(quality.name, DataSaverPolicy.CELLULAR_QUALITY_CAP, applied.cellularQuality)
        }
    }

    @Test
    fun `a ceiling already at the cap is kept and never raised`() {
        val applied = DataSaverPolicy.applied(values(AudioQuality.LOW, wifiOnly = true, canvas = false))
        assertEquals(AudioQuality.LOW, applied.cellularQuality)
    }

    @Test
    fun `downloads go Wi-Fi only and the cellular canvas goes off`() {
        val applied = DataSaverPolicy.applied(values(AudioQuality.LOSSLESS, wifiOnly = false, canvas = true))
        assertTrue(applied.wifiOnlyDownloads)
        assertFalse(applied.canvasOverCellular)
    }

    @Test
    fun `applying twice changes nothing more`() {
        val once = DataSaverPolicy.applied(values(AudioQuality.HIGH, wifiOnly = false, canvas = true))
        assertEquals(once, DataSaverPolicy.applied(once))
    }

    @Test
    fun `every combination survives the stored snapshot exactly`() {
        for (quality in AudioQuality.entries) {
            for (wifiOnly in listOf(true, false)) {
                for (canvas in listOf(true, false)) {
                    val before = values(quality, wifiOnly, canvas)
                    assertEquals(before, DataSaverPolicy.decode(DataSaverPolicy.encode(before)))
                }
            }
        }
    }

    @Test
    fun `a missing or damaged snapshot decodes to nothing`() {
        assertNull(DataSaverPolicy.decode(null))
        assertNull(DataSaverPolicy.decode(""))
        assertNull(DataSaverPolicy.decode("LOW|true"))
        assertNull(DataSaverPolicy.decode("LOW|true|false|extra"))
        assertNull(DataSaverPolicy.decode("ULTRA|true|false"))
        assertNull(DataSaverPolicy.decode("LOW|yes|false"))
    }

    @Test
    fun `read-ahead shrinks only while saving on a metered connection`() {
        assertEquals(DataSaverPolicy.PRELOAD_BYTES_SAVER, DataSaverPolicy.preloadBytes(true, true))
        assertEquals(DataSaverPolicy.PRELOAD_BYTES_NORMAL, DataSaverPolicy.preloadBytes(true, false))
        assertEquals(DataSaverPolicy.PRELOAD_BYTES_NORMAL, DataSaverPolicy.preloadBytes(true, null))
        assertEquals(DataSaverPolicy.PRELOAD_BYTES_NORMAL, DataSaverPolicy.preloadBytes(false, true))
        assertTrue(DataSaverPolicy.PRELOAD_BYTES_SAVER < DataSaverPolicy.PRELOAD_BYTES_NORMAL)
    }

    @Test
    fun `the whole next track is fetched unless saving on mobile data`() {
        assertFalse(DataSaverPolicy.prefetchesWholeTrack(dataSaverOn = true, metered = true))
        assertTrue(DataSaverPolicy.prefetchesWholeTrack(dataSaverOn = true, metered = false))
        assertTrue(DataSaverPolicy.prefetchesWholeTrack(dataSaverOn = true, metered = null))
        assertTrue(DataSaverPolicy.prefetchesWholeTrack(dataSaverOn = false, metered = true))
    }

    @Test
    fun `music videos become audio on mobile data under Data saver without touching the choice`() {
        assertTrue(DataSaverPolicy.musicOnlyNow(preferMusicOnly = false, dataSaverOn = true, metered = true))

        assertFalse(DataSaverPolicy.musicOnlyNow(preferMusicOnly = false, dataSaverOn = true, metered = false))

        assertFalse(DataSaverPolicy.musicOnlyNow(preferMusicOnly = false, dataSaverOn = false, metered = true))
        assertTrue(DataSaverPolicy.musicOnlyNow(preferMusicOnly = true, dataSaverOn = false, metered = false))

        assertTrue(DataSaverPolicy.musicOnlyNow(preferMusicOnly = true, dataSaverOn = true, metered = false))
    }
}
