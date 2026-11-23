package com.music.amber.data.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DataSaverPolicyTest {

    private fun values(quality: AudioQuality, wifiOnly: Boolean, canvas: Boolean) =
        DataSaverValues(quality, wifiOnly, canvas)

    @Test
    fun highCeilingIsLoweredAndLowIsKept() {
        AudioQuality.entries.filter { it != AudioQuality.LOW }.forEach {
            assertEquals(DataSaverPolicy.CELLULAR_QUALITY_CAP, DataSaverPolicy.applied(values(it, false, true)).cellularQuality)
        }
        assertEquals(AudioQuality.LOW, DataSaverPolicy.applied(values(AudioQuality.LOW, true, false)).cellularQuality)
    }

    @Test
    fun downloadsGoWifiOnlyAndCellularCanvasGoesOff() {
        val applied = DataSaverPolicy.applied(values(AudioQuality.LOSSLESS, false, true))
        assertTrue(applied.wifiOnlyDownloads)
        assertFalse(applied.canvasOverCellular)
    }

    @Test
    fun applyingTwiceChangesNothingMore() {
        val once = DataSaverPolicy.applied(values(AudioQuality.HIGH, false, true))
        assertEquals(once, DataSaverPolicy.applied(once))
    }

    @Test
    fun everyCombinationSurvivesTheSnapshot() {
        for (quality in AudioQuality.entries) for (wifi in listOf(true, false)) for (canvas in listOf(true, false)) {
            val before = values(quality, wifi, canvas)
            assertEquals(before, DataSaverPolicy.decode(DataSaverPolicy.encode(before)))
        }
    }

    @Test
    fun damagedSnapshotDecodesToNothing() {
        assertNull(DataSaverPolicy.decode(null))
        assertNull(DataSaverPolicy.decode(""))
        assertNull(DataSaverPolicy.decode("LOW|true"))
        assertNull(DataSaverPolicy.decode("ULTRA|true|false"))
        assertNull(DataSaverPolicy.decode("LOW|yes|false"))
    }
}
