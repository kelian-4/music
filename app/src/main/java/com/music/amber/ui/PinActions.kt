package com.music.amber.ui

import android.content.Context
import android.widget.Toast
import com.music.amber.R
import com.music.amber.data.settings.AppSettings
import com.music.amber.data.settings.PinChange
import com.music.amber.data.settings.PinnedItem

internal fun togglePinWithNotice(context: Context, item: PinnedItem) {
    if (AppSettings.togglePinned(item) == PinChange.LIMIT) {
        Toast.makeText(
            context,
            context.getString(R.string.pinned_limit, AppSettings.MAX_PINNED_PLAYLISTS),
            Toast.LENGTH_SHORT,
        ).show()
    }
}
