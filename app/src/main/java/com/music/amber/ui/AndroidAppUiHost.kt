package com.music.amber.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.amber.data.AppleCatalogue
import com.music.amber.data.YtMusicRepository
import com.music.amber.data.canvas.AppleArtistArtRepository
import com.music.amber.data.canvas.CanvasArtwork
import com.music.amber.data.canvas.CanvasRepository
import com.music.amber.data.settings.AppSettings
import com.music.amber.download.Downloads

/** The shared pages' settings and downloads, answered from the phone's own stores. */
object AndroidAppUiHost : AppUiHost {
    override val swipeToPlayNext = AppSettings.swipeToPlayNext
    override val pinnedPlaylists = AppSettings.pinnedPlaylists
    override val pinnedItems = AppSettings.pinnedShelfItems
    override val librarySort = AppSettings.librarySort
    override val playlistFolders = AppSettings.playlistFolders
    override val supportsPlaylistFolders = true

    override fun createPlaylistFolder(name: String) = AppSettings.createPlaylistFolder(name)

    override fun renamePlaylistFolder(id: String, name: String) = AppSettings.renamePlaylistFolder(id, name)

    override fun deletePlaylistFolder(id: String) = AppSettings.deletePlaylistFolder(id)

    @Composable
    override fun downloadedIds(): Set<String> {
        val saved by Downloads.saved.collectAsStateWithLifecycle()
        return saved.keys
    }

    override suspend fun releaseMotion(album: String, artist: String): CanvasArtwork? {
        if (!AppSettings.animatedCanvas.value || album.isBlank() || artist.isBlank()) return null
        return CanvasRepository.canvasForAlbum(album, artist)
    }

    /**
     * The clip the artist page plays, off the same proven Apple lookup — so
     * opening the artist from the card costs nothing more, and a card never
     * moves with somebody else's face. The YouTube page it is proved against is
     * fetched only when that lookup hasn't settled already.
     */
    override suspend fun artistMotion(browseId: String, artistName: String): CanvasArtwork? {
        if (!AppSettings.animatedCanvas.value) return null
        val art = AppleArtistArtRepository.artFor(browseId, artistName) {
            YtMusicRepository.artistLanding(browseId).getOrNull()?.let(AppleCatalogue::titlesOf).orEmpty()
        }
        return art?.videoUrl?.let { CanvasArtwork(url = it) }
    }
}
