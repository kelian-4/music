package com.music.bitchord.data.settings

data class DataSaverValues(
    val cellularQuality: AudioQuality,
    val wifiOnlyDownloads: Boolean,
    val canvasOverCellular: Boolean,
)

object DataSaverPolicy {
    val CELLULAR_QUALITY_CAP: AudioQuality = AudioQuality.LOW

    const val PRELOAD_BYTES_NORMAL: Long = 1L * 1024 * 1024

    const val PRELOAD_BYTES_SAVER: Long = 256L * 1024

    fun applied(current: DataSaverValues): DataSaverValues = DataSaverValues(
        cellularQuality = if (current.cellularQuality.ordinal > CELLULAR_QUALITY_CAP.ordinal) {
            CELLULAR_QUALITY_CAP
        } else {
            current.cellularQuality
        },
        wifiOnlyDownloads = true,
        canvasOverCellular = false,
    )

    fun savingNow(dataSaverOn: Boolean, metered: Boolean?): Boolean = dataSaverOn && metered == true

    fun preloadBytes(dataSaverOn: Boolean, metered: Boolean?): Long =
        if (savingNow(dataSaverOn, metered)) PRELOAD_BYTES_SAVER else PRELOAD_BYTES_NORMAL

    fun prefetchesWholeTrack(dataSaverOn: Boolean, metered: Boolean?): Boolean =
        !savingNow(dataSaverOn, metered)

    fun musicOnlyNow(preferMusicOnly: Boolean, dataSaverOn: Boolean, metered: Boolean?): Boolean =
        preferMusicOnly || savingNow(dataSaverOn, metered)

    fun encode(values: DataSaverValues): String =
        "${values.cellularQuality.name}|${values.wifiOnlyDownloads}|${values.canvasOverCellular}"

    fun decode(stored: String?): DataSaverValues? {
        val parts = stored?.split('|') ?: return null
        if (parts.size != 3) return null
        val quality = runCatching { AudioQuality.valueOf(parts[0]) }.getOrNull() ?: return null
        val wifiOnly = parts[1].toBooleanStrictOrNull() ?: return null
        val canvas = parts[2].toBooleanStrictOrNull() ?: return null
        return DataSaverValues(quality, wifiOnly, canvas)
    }
}
