package com.music.bitchord

import com.music.bitchord.data.settings.PlaylistFolder
import com.music.bitchord.data.settings.PlaylistFoldersPolicy as P
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistFoldersPolicyTest {
    private val gym = PlaylistFolder("f1", "Gym", listOf("a", "b"))
    private val night = PlaylistFolder("f2", "Night", listOf("c"))

    @Test
    fun `names are trimmed and collapsed and blank ones refused`() {
        assertEquals("Road trip", P.cleanName("  Road   trip \n"))
        assertNull(P.cleanName("   "))
        assertEquals(P.MAX_NAME_LENGTH, P.cleanName("x".repeat(100))!!.length)
    }

    @Test
    fun `a name already used is refused whatever its case`() {
        assertNull(P.create(listOf(gym), "gym", "f9"))
        assertNull(P.create(listOf(gym), " GYM ", "f9"))
        assertEquals(2, P.create(listOf(gym), "Study", "f9")!!.size)
    }

    @Test
    fun `the folder cap is enforced`() {
        val full = (1..P.MAX_FOLDERS).map { PlaylistFolder("id$it", "F$it") }
        assertNull(P.create(full, "One more", "new"))
    }

    @Test
    fun `a duplicate id is refused`() {
        assertNull(P.create(listOf(gym), "Other", "f1"))
    }

    @Test
    fun `renaming keeps playlists and refuses another folder's name`() {
        val renamed = P.rename(listOf(gym, night), "f1", "Workout")!!
        assertEquals(listOf("a", "b"), renamed.first { it.id == "f1" }.playlistIds)
        assertNull(P.rename(listOf(gym, night), "f1", "night"))
        assertNull(P.rename(listOf(gym, night), "nope", "Anything"))

        assertEquals("GYM", P.rename(listOf(gym), "f1", "GYM")!!.single().name)
    }

    @Test
    fun `deleting a folder drops only the folder`() {
        assertEquals(listOf(night), P.delete(listOf(gym, night), "f1"))
        assertEquals(listOf(gym, night), P.delete(listOf(gym, night), "missing"))
    }

    @Test
    fun `a playlist is in one folder at a time`() {
        val moved = P.assign(listOf(gym, night), "a", "f2")
        assertEquals(listOf("b"), moved.first { it.id == "f1" }.playlistIds)
        assertEquals(listOf("c", "a"), moved.first { it.id == "f2" }.playlistIds)
        assertEquals("f2", P.folderOf(moved, "a"))
    }

    @Test
    fun `no folder or an unknown one only takes the playlist out`() {
        assertNull(P.folderOf(P.assign(listOf(gym, night), "a", null), "a"))
        assertNull(P.folderOf(P.assign(listOf(gym, night), "a", "ghost"), "a"))
    }

    @Test
    fun `assigning to the folder it is already in changes nothing`() {
        assertEquals(listOf("a", "b").toSet(), P.assign(listOf(gym), "a", "f1").single().playlistIds.toSet())
        assertEquals(2, P.assign(listOf(gym), "a", "f1").single().playlistIds.size)
    }

    @Test
    fun `the shelf keeps its own order inside a folder`() {
        val shelf = listOf("c", "b", "a", "z")
        assertEquals(listOf("b", "a"), P.visible(shelf, { it }, listOf(gym, night), "f1"))
    }

    @Test
    fun `no selection or a vanished folder shows the whole shelf`() {
        val shelf = listOf("a", "z")
        assertEquals(shelf, P.visible(shelf, { it }, listOf(gym), null))
        assertEquals(shelf, P.visible(shelf, { it }, listOf(gym), "deleted"))
    }

    @Test
    fun `an item without an id is never in a folder`() {
        assertEquals(listOf("a"), P.visible(listOf("a", "?"), { if (it == "?") null else it }, listOf(gym), "f1"))
    }

    @Test
    fun `folders survive the stored form exactly`() {
        val folders = listOf(gym, night, PlaylistFolder("f3", "Empty"))
        assertEquals(folders, P.decode(P.encode(folders)))
    }

    @Test
    fun `nothing or garbage stored means no folders`() {
        assertEquals(emptyList<PlaylistFolder>(), P.decode(null))
        assertEquals(emptyList<PlaylistFolder>(), P.decode("{broken"))
    }

    @Test
    fun `a damaged store is cleaned on read`() {
        val json = """[{"id":"f1","name":"A","playlistIds":["x","x","y"]},
            {"id":"f1","name":"Dup"},{"id":"","name":"NoId"},{"id":"f3","name":"  "},
            {"id":"f4","name":"B","playlistIds":["y","z"]}]"""
        val folders = P.decode(json)
        assertEquals(listOf("f1", "f4"), folders.map { it.id })
        assertEquals(listOf("x", "y"), folders[0].playlistIds)
        assertEquals(listOf("z"), folders[1].playlistIds)
    }
}
