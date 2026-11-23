package com.music.amber

import com.music.amber.notify.NewReleasePolicy
import com.music.amber.notify.ReleaseRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewReleasePolicyTest {
    private fun r(id: String) = ReleaseRef(id, "Title $id")

    @Test
    fun `an artist seen for the first time is recorded and never announced`() {
        val diff = NewReleasePolicy.diff(null, listOf(r("MPREb_1"), r("MPREb_2")))
        assertTrue(diff.fresh.isEmpty())
        assertEquals(setOf("MPREb_1", "MPREb_2"), diff.seenAfter)
    }

    @Test
    fun `only releases not remembered are new`() {
        val diff = NewReleasePolicy.diff(setOf("MPREb_1"), listOf(r("MPREb_3"), r("MPREb_1")))
        assertEquals(listOf("MPREb_3"), diff.fresh.map { it.id })
        assertEquals(setOf("MPREb_1", "MPREb_3"), diff.seenAfter)
    }

    @Test
    fun `an unchanged page announces nothing`() {
        val diff = NewReleasePolicy.diff(setOf("a", "b"), listOf(r("a"), r("b")))
        assertTrue(diff.fresh.isEmpty())
    }

    @Test
    fun `a release that drops off the page is still remembered`() {
        val diff = NewReleasePolicy.diff(setOf("a", "b"), listOf(r("a")))
        assertEquals(setOf("a", "b"), diff.seenAfter)
    }

    @Test
    fun `the same release listed twice is announced once`() {
        val diff = NewReleasePolicy.diff(setOf("a"), listOf(r("n"), r("n")))
        assertEquals(1, diff.fresh.size)
    }

    @Test
    fun `an empty page for a known artist announces nothing and forgets nothing`() {
        val diff = NewReleasePolicy.diff(setOf("a"), emptyList())
        assertTrue(diff.fresh.isEmpty())
        assertEquals(setOf("a"), diff.seenAfter)
    }

    @Test
    fun `the memory per artist is bounded and keeps the current releases`() {
        val many = (1..NewReleasePolicy.MAX_SEEN_PER_ARTIST + 50).map { r("old$it") }
        val old = many.mapTo(LinkedHashSet()) { it.id }
        val diff = NewReleasePolicy.diff(old, listOf(r("brand-new")) + many)
        assertEquals(NewReleasePolicy.MAX_SEEN_PER_ARTIST, diff.seenAfter.size)
        assertTrue("brand-new" in diff.seenAfter)
    }

    @Test
    fun `only album single and EP ids count as releases`() {
        assertTrue(NewReleasePolicy.isRelease("MPREb_abc"))
        assertFalse(NewReleasePolicy.isRelease("UCabc"))
        assertFalse(NewReleasePolicy.isRelease("VLPLabc"))
        assertFalse(NewReleasePolicy.isRelease(null))
    }

    @Test
    fun `unfollowed artists are forgotten but an empty list never wipes the memory`() {
        val seen = mapOf("A" to setOf("1"), "B" to setOf("2"))
        assertEquals(mapOf("A" to setOf("1")), NewReleasePolicy.forgetUnfollowed(seen, listOf("A")))
        assertEquals(seen, NewReleasePolicy.forgetUnfollowed(seen, emptyList()))
    }

    @Test
    fun `one check announces a bounded number of artists`() {
        val findings = (1..20).map { "artist$it" to listOf(r("x$it")) } + ("quiet" to emptyList())
        val announced = NewReleasePolicy.toAnnounce(findings)
        assertEquals(NewReleasePolicy.MAX_NOTIFICATIONS_PER_CHECK, announced.size)
        assertTrue(announced.none { it.second.isEmpty() })
    }

    @Test
    fun `the memory survives its stored form and garbage reads as nothing`() {
        val seen = mapOf("A" to setOf("1", "2"), "B" to emptySet())
        assertEquals(seen, NewReleasePolicy.decode(NewReleasePolicy.encode(seen)))
        assertEquals(emptyMap<String, Set<String>>(), NewReleasePolicy.decode("{nope"))
        assertEquals(emptyMap<String, Set<String>>(), NewReleasePolicy.decode(null))
    }
}
