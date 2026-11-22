package com.music.bitchord.data.settings

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

class PinnedItemsPolicyTest {
    private fun pin(kind: PinKind, id: String) = PinnedItem(kind, id, title = "T $id")

    @Test
    fun pinningAppendsInOrder() {
        var row = emptyList<PinnedItem>()
        row = PinnedItemsPolicy.toggle(row, pin(PinKind.ALBUM, "a")).items
        row = PinnedItemsPolicy.toggle(row, pin(PinKind.SONG, "s")).items
        row = PinnedItemsPolicy.toggle(row, pin(PinKind.ARTIST, "r")).items
        assertEquals(listOf("a", "s", "r"), row.map { it.id })
    }

    @Test
    fun togglingAPinnedItemUnpinsItAndKeepsTheOthersInOrder() {
        val row = listOf(pin(PinKind.ALBUM, "a"), pin(PinKind.SONG, "s"), pin(PinKind.ARTIST, "r"))
        val result = PinnedItemsPolicy.toggle(row, pin(PinKind.SONG, "s"))
        assertEquals(PinChange.UNPINNED, result.change)
        assertEquals(listOf("a", "r"), result.items.map { it.id })
    }

    @Test
    fun unpinningNeedsNoTitleOrArtwork() {
        val row = listOf(PinnedItem(PinKind.ALBUM, "a", "Kept title", "Sub", "https://x/y.jpg"))
        val result = PinnedItemsPolicy.toggle(row, PinnedItem(PinKind.ALBUM, "a"))
        assertEquals(PinChange.UNPINNED, result.change)
        assertTrue(result.items.isEmpty())
    }

    @Test
    fun theSeventhPinIsRefusedAndNothingIsEvicted() {
        val row = (1..PinnedItemsPolicy.MAX_PINNED).map { pin(PinKind.SONG, "s$it") }
        val result = PinnedItemsPolicy.toggle(row, pin(PinKind.ALBUM, "extra"))
        assertEquals(PinChange.LIMIT, result.change)
        assertEquals(row, result.items)
    }

    @Test
    fun aFullRowCanStillBeUnpinnedFrom() {
        val row = (1..PinnedItemsPolicy.MAX_PINNED).map { pin(PinKind.SONG, "s$it") }
        val result = PinnedItemsPolicy.toggle(row, pin(PinKind.SONG, "s3"))
        assertEquals(PinChange.UNPINNED, result.change)
        assertEquals(PinnedItemsPolicy.MAX_PINNED - 1, result.items.size)
    }

    @Test
    fun theLimitIsSixAsInAppleMusic() {
        assertEquals(6, PinnedItemsPolicy.MAX_PINNED)
    }

    @Test
    fun identityIsKindAndIdTogether() {
        val row = listOf(pin(PinKind.ALBUM, "same"))
        assertFalse(PinnedItemsPolicy.isPinned(row, PinKind.SONG, "same"))
        assertTrue(PinnedItemsPolicy.isPinned(row, PinKind.ALBUM, "same"))
        val result = PinnedItemsPolicy.toggle(row, pin(PinKind.SONG, "same"))
        assertEquals(PinChange.PINNED, result.change)
        assertEquals(2, result.items.size)
    }

    @Test
    fun movingToTheFrontKeepsEverythingElseInOrder() {
        val row = listOf(pin(PinKind.ALBUM, "a"), pin(PinKind.SONG, "s"), pin(PinKind.ARTIST, "r"))
        assertEquals(listOf("r", "a", "s"), PinnedItemsPolicy.moveToFront(row, PinKind.ARTIST, "r").map { it.id })
        assertEquals(row, PinnedItemsPolicy.moveToFront(row, PinKind.ALBUM, "a"))
        assertEquals(row, PinnedItemsPolicy.moveToFront(row, PinKind.ALBUM, "missing"))
    }

    @Test
    fun playlistIdsAreThePlaylistsAmongThePinsInRowOrder() {
        val row = listOf(
            pin(PinKind.PLAYLIST, "p1"), pin(PinKind.ALBUM, "a"), pin(PinKind.PLAYLIST, "p2"), pin(PinKind.SONG, "s"),
        )
        assertEquals(listOf("p1", "p2"), PinnedItemsPolicy.playlistIds(row))
    }

    @Test
    fun oldPlaylistonlyPinsMigrateInOrder() {
        val migrated = PinnedItemsPolicy.migrate(listOf("VLa", "VLb", "", "VLa", "VLc"))
        assertEquals(listOf("VLa", "VLb", "VLc"), migrated.map { it.id })
        assertTrue(migrated.all { it.kind == PinKind.PLAYLIST })
    }

    @Test
    fun aMigrationNeverExceedsTheLimit() {
        val many = (1..20).map { "VL$it" }
        assertEquals(PinnedItemsPolicy.MAX_PINNED, PinnedItemsPolicy.migrate(many).size)
        assertEquals(listOf("VL1"), PinnedItemsPolicy.migrate(many).take(1).map { it.id })
    }

    @Test
    fun theStoredRowRoundtripsExactly() {
        val row = listOf(
            PinnedItem(PinKind.ALBUM, "a", "Title", "Sub", "https://x/a.jpg"),
            PinnedItem(PinKind.SONG, "s", "Song", "Artist", null),
            PinnedItem(PinKind.PLAYLIST, "VLp"),
        )
        assertEquals(row, PinnedItemsPolicy.decode(PinnedItemsPolicy.encode(row)))
    }

    @Test
    fun nothingStoredIsNotTheSameAsAnEmptyRow() {
        assertNull(PinnedItemsPolicy.decode(null))
        assertNull(PinnedItemsPolicy.decode("{broken"))
        assertEquals(emptyList<PinnedItem>(), PinnedItemsPolicy.decode("[]"))
    }

    @Test
    fun aDamagedStoreDropsBlankAndRepeatedEntriesAndCapsTheRow() {
        val json = """[{"kind":"SONG","id":""},{"kind":"SONG","id":"x"},{"kind":"SONG","id":"x"}]"""
        assertEquals(listOf("x"), PinnedItemsPolicy.decode(json)!!.map { it.id })
    }
}
