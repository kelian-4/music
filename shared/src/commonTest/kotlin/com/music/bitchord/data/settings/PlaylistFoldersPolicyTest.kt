package com.music.bitchord.data.settings

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

class PlaylistFoldersPolicyTest {
    private val P = PlaylistFoldersPolicy

    private val gym = PlaylistFolder("f1", "Gym", listOf("a", "b"))
    private val night = PlaylistFolder("f2", "Night", listOf("c"))

    @Test
    fun namesAreTrimmedAndCollapsedAndBlankOnesRefused() {
        assertEquals("Road trip", P.cleanName("  Road   trip \n"))
        assertNull(P.cleanName("   "))
        assertEquals(P.MAX_NAME_LENGTH, P.cleanName("x".repeat(100))!!.length)
    }

    @Test
    fun aNameAlreadyUsedIsRefusedWhateverItsCase() {
        assertNull(P.create(listOf(gym), "gym", "f9"))
        assertNull(P.create(listOf(gym), " GYM ", "f9"))
        assertEquals(2, P.create(listOf(gym), "Study", "f9")!!.size)
    }

    @Test
    fun theFolderCapIsEnforced() {
        val full = (1..P.MAX_FOLDERS).map { PlaylistFolder("id$it", "F$it") }
        assertNull(P.create(full, "One more", "new"))
    }

    @Test
    fun aDuplicateIdIsRefused() {
        assertNull(P.create(listOf(gym), "Other", "f1"))
    }

    @Test
    fun renamingKeepsPlaylistsAndRefusesAnotherFoldersName() {
        val renamed = P.rename(listOf(gym, night), "f1", "Workout")!!
        assertEquals(listOf("a", "b"), renamed.first { it.id == "f1" }.playlistIds)
        assertNull(P.rename(listOf(gym, night), "f1", "night"))
        assertNull(P.rename(listOf(gym, night), "nope", "Anything"))

        assertEquals("GYM", P.rename(listOf(gym), "f1", "GYM")!!.single().name)
    }

    @Test
    fun deletingAFolderDropsOnlyTheFolder() {
        assertEquals(listOf(night), P.delete(listOf(gym, night), "f1"))
        assertEquals(listOf(gym, night), P.delete(listOf(gym, night), "missing"))
    }

    @Test
    fun aPlaylistIsInOneFolderAtATime() {
        val moved = P.assign(listOf(gym, night), "a", "f2")
        assertEquals(listOf("b"), moved.first { it.id == "f1" }.playlistIds)
        assertEquals(listOf("c", "a"), moved.first { it.id == "f2" }.playlistIds)
        assertEquals("f2", P.folderOf(moved, "a"))
    }

    @Test
    fun noFolderOrAnUnknownOneOnlyTakesThePlaylistOut() {
        assertNull(P.folderOf(P.assign(listOf(gym, night), "a", null), "a"))
        assertNull(P.folderOf(P.assign(listOf(gym, night), "a", "ghost"), "a"))
    }

    @Test
    fun assigningToTheFolderItIsAlreadyInChangesNothing() {
        assertEquals(listOf("a", "b").toSet(), P.assign(listOf(gym), "a", "f1").single().playlistIds.toSet())
        assertEquals(2, P.assign(listOf(gym), "a", "f1").single().playlistIds.size)
    }

    @Test
    fun theShelfKeepsItsOwnOrderInsideAFolder() {
        val shelf = listOf("c", "b", "a", "z")
        assertEquals(listOf("b", "a"), P.visible(shelf, { it }, listOf(gym, night), "f1"))
    }

    @Test
    fun noSelectionOrAVanishedFolderShowsTheWholeShelf() {
        val shelf = listOf("a", "z")
        assertEquals(shelf, P.visible(shelf, { it }, listOf(gym), null))
        assertEquals(shelf, P.visible(shelf, { it }, listOf(gym), "deleted"))
    }

    @Test
    fun anItemWithoutAnIdIsNeverInAFolder() {
        assertEquals(listOf("a"), P.visible(listOf("a", "?"), { if (it == "?") null else it }, listOf(gym), "f1"))
    }

    @Test
    fun foldersSurviveTheStoredFormExactly() {
        val folders = listOf(gym, night, PlaylistFolder("f3", "Empty"))
        assertEquals(folders, P.decode(P.encode(folders)))
    }

    @Test
    fun nothingOrGarbageStoredMeansNoFolders() {
        assertEquals(emptyList<PlaylistFolder>(), P.decode(null))
        assertEquals(emptyList<PlaylistFolder>(), P.decode("{broken"))
    }

    @Test
    fun aDamagedStoreIsCleanedOnRead() {
        val json = """[{"id":"f1","name":"A","playlistIds":["x","x","y"]},
            {"id":"f1","name":"Dup"},{"id":"","name":"NoId"},{"id":"f3","name":"  "},
            {"id":"f4","name":"B","playlistIds":["y","z"]}]"""
        val folders = P.decode(json)
        assertEquals(listOf("f1", "f4"), folders.map { it.id })
        assertEquals(listOf("x", "y"), folders[0].playlistIds)
        assertEquals(listOf("z"), folders[1].playlistIds)
    }
}
