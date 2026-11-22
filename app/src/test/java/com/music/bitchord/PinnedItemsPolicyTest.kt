package com.music.bitchord

import com.music.bitchord.data.settings.PinChange
import com.music.bitchord.data.settings.PinKind
import com.music.bitchord.data.settings.PinnedItem
import com.music.bitchord.data.settings.PinnedItemsPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinnedItemsPolicyTest {
    private fun pin(kind: PinKind, id: String) = PinnedItem(kind, id, title = "T $id")

    @Test
    fun `pinning appends in order`() {
        var row = emptyList<PinnedItem>()
        row = PinnedItemsPolicy.toggle(row, pin(PinKind.ALBUM, "a")).items
        row = PinnedItemsPolicy.toggle(row, pin(PinKind.SONG, "s")).items
        row = PinnedItemsPolicy.toggle(row, pin(PinKind.ARTIST, "r")).items
        assertEquals(listOf("a", "s", "r"), row.map { it.id })
    }

    @Test
    fun `toggling a pinned item unpins it and keeps the others in order`() {
        val row = listOf(pin(PinKind.ALBUM, "a"), pin(PinKind.SONG, "s"), pin(PinKind.ARTIST, "r"))
        val result = PinnedItemsPolicy.toggle(row, pin(PinKind.SONG, "s"))
        assertEquals(PinChange.UNPINNED, result.change)
        assertEquals(listOf("a", "r"), result.items.map { it.id })
    }

    @Test
    fun `unpinning needs no title or artwork`() {
        val row = listOf(PinnedItem(PinKind.ALBUM, "a", "Kept title", "Sub", "https://x/y.jpg"))
        val result = PinnedItemsPolicy.toggle(row, PinnedItem(PinKind.ALBUM, "a"))
        assertEquals(PinChange.UNPINNED, result.change)
        assertTrue(result.items.isEmpty())
    }

    @Test
    fun `the seventh pin is refused and nothing is evicted`() {
        val row = (1..PinnedItemsPolicy.MAX_PINNED).map { pin(PinKind.SONG, "s$it") }
        val result = PinnedItemsPolicy.toggle(row, pin(PinKind.ALBUM, "extra"))
        assertEquals(PinChange.LIMIT, result.change)
        assertEquals(row, result.items)
    }

    @Test
    fun `a full row can still be unpinned from`() {
        val row = (1..PinnedItemsPolicy.MAX_PINNED).map { pin(PinKind.SONG, "s$it") }
        val result = PinnedItemsPolicy.toggle(row, pin(PinKind.SONG, "s3"))
        assertEquals(PinChange.UNPINNED, result.change)
        assertEquals(PinnedItemsPolicy.MAX_PINNED - 1, result.items.size)
    }

    @Test
    fun `the limit is six, as in Apple Music`() {
        assertEquals(6, PinnedItemsPolicy.MAX_PINNED)
    }

    @Test
    fun `identity is kind and id together`() {
        val row = listOf(pin(PinKind.ALBUM, "same"))
        assertFalse(PinnedItemsPolicy.isPinned(row, PinKind.SONG, "same"))
        assertTrue(PinnedItemsPolicy.isPinned(row, PinKind.ALBUM, "same"))
        val result = PinnedItemsPolicy.toggle(row, pin(PinKind.SONG, "same"))
        assertEquals(PinChange.PINNED, result.change)
        assertEquals(2, result.items.size)
    }

    @Test
    fun `moving to the front keeps everything else in order`() {
        val row = listOf(pin(PinKind.ALBUM, "a"), pin(PinKind.SONG, "s"), pin(PinKind.ARTIST, "r"))
        assertEquals(listOf("r", "a", "s"), PinnedItemsPolicy.moveToFront(row, PinKind.ARTIST, "r").map { it.id })
        assertEquals(row, PinnedItemsPolicy.moveToFront(row, PinKind.ALBUM, "a"))
        assertEquals(row, PinnedItemsPolicy.moveToFront(row, PinKind.ALBUM, "missing"))
    }

    @Test
    fun `playlist ids are the playlists among the pins in row order`() {
        val row = listOf(
            pin(PinKind.PLAYLIST, "p1"), pin(PinKind.ALBUM, "a"), pin(PinKind.PLAYLIST, "p2"), pin(PinKind.SONG, "s"),
        )
        assertEquals(listOf("p1", "p2"), PinnedItemsPolicy.playlistIds(row))
    }

    @Test
    fun `old playlist-only pins migrate in order`() {
        val migrated = PinnedItemsPolicy.migrate(listOf("VLa", "VLb", "", "VLa", "VLc"))
        assertEquals(listOf("VLa", "VLb", "VLc"), migrated.map { it.id })
        assertTrue(migrated.all { it.kind == PinKind.PLAYLIST })
    }

    @Test
    fun `a migration never exceeds the limit`() {
        val many = (1..20).map { "VL$it" }
        assertEquals(PinnedItemsPolicy.MAX_PINNED, PinnedItemsPolicy.migrate(many).size)
        assertEquals(listOf("VL1"), PinnedItemsPolicy.migrate(many).take(1).map { it.id })
    }

    @Test
    fun `the stored row round-trips exactly`() {
        val row = listOf(
            PinnedItem(PinKind.ALBUM, "a", "Title", "Sub", "https://x/a.jpg"),
            PinnedItem(PinKind.SONG, "s", "Song", "Artist", null),
            PinnedItem(PinKind.PLAYLIST, "VLp"),
        )
        assertEquals(row, PinnedItemsPolicy.decode(PinnedItemsPolicy.encode(row)))
    }

    @Test
    fun `nothing stored is not the same as an empty row`() {
        assertNull(PinnedItemsPolicy.decode(null))
        assertNull(PinnedItemsPolicy.decode("{broken"))
        assertEquals(emptyList<PinnedItem>(), PinnedItemsPolicy.decode("[]"))
    }

    @Test
    fun `a damaged store drops blank and repeated entries and caps the row`() {
        val json = """[{"kind":"SONG","id":""},{"kind":"SONG","id":"x"},{"kind":"SONG","id":"x"}]"""
        assertEquals(listOf("x"), PinnedItemsPolicy.decode(json)!!.map { it.id })
    }
}
