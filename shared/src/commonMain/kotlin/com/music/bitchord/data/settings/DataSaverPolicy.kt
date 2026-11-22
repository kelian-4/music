package com.music.bitchord.data.settings

data class DataSaverValues(
    val cellularQuality: AudioQuality,
    val wifiOnlyDownloads: Boolean,
    val canvasOverCellular: Boolean,
)

object DataSaverPolicy {

    val CELLULAR_QUALITY_CAP: AudioQuality = AudioQuality.LOW

    fun applied(current: DataSaverValues): DataSaverValues = DataSaverValues(
        cellularQuality = if (current.cellularQuality.ordinal > CELLULAR_QUALITY_CAP.ordinal) {
            CELLULAR_QUALITY_CAP
        } else {
            current.cellularQuality
        },
        wifiOnlyDownloads = true,
        canvasOverCellular = false,
    )

    fun encode(values: DataSaverValues): String =
        "${values.cellularQuality.name}|${values.wifiOnlyDownloads}|${values.canvasOverCellular}"

    fun decode(stored: String?): DataSaverValues? {
        val parts = stored?.split('|') ?: return null
        if (parts.size != 3) return null
        val quality = AudioQuality.entries.firstOrNull { it.name == parts[0] } ?: return null
        val wifiOnly = parts[1].toBooleanStrictOrNull() ?: return null
        val canvas = parts[2].toBooleanStrictOrNull() ?: return null
        return DataSaverValues(quality, wifiOnly, canvas)
    }
}
