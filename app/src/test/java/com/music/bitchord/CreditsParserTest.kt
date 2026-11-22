package com.music.bitchord

import com.music.bitchord.data.credits.Credit
import com.music.bitchord.data.credits.CreditRole
import com.music.bitchord.data.credits.CreditsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CreditsParserTest {
    private val search = """
        {"recordings":[
          {"id":"low","score":60,"length":200000},
          {"id":"wrong-length","score":100,"length":400000},
          {"id":"good","score":100,"length":201500},
          {"id":"second","score":95,"length":200000}
        ]}
    """.trimIndent()

    @Test
    fun `the best hit is the first one scoring high enough with a matching length`() {
        assertEquals("good", CreditsParser.bestRecordingId(search, 200_000L))
    }

    @Test
    fun `without a known duration the length is not used`() {
        assertEquals("wrong-length", CreditsParser.bestRecordingId(search, null))
    }

    @Test
    fun `nothing scores high enough means no match`() {
        assertNull(CreditsParser.bestRecordingId("""{"recordings":[{"id":"x","score":89,"length":1}]}""", 1L))
        assertNull(CreditsParser.bestRecordingId("""{"recordings":[]}""", null))
    }

    @Test
    fun `an unreadable answer is no match rather than a crash`() {
        assertNull(CreditsParser.bestRecordingId("<html>", null))
        assertNull(CreditsParser.bestRecordingId("""{"error":"rate"}""", null))
    }

    private val lookup = """
        {"id":"rec1","title":"Song","relations":[
          {"type":"producer","target-type":"artist","artist":{"name":"Pat Producer"}},
          {"type":"instrument","attributes":["guitar","bass guitar"],"artist":{"name":"Gus Guitar"}},
          {"type":"vocal","attributes":["lead vocals"],"artist":{"name":"Vera Voice"}},
          {"type":"mix","artist":{"name":"Mia Mixer"}},
          {"type":"performance","target-type":"work","work":{"relations":[
            {"type":"composer","artist":{"name":"Cora Composer"}},
            {"type":"lyricist","artist":{"name":"Lee Lyrics"}},
            {"type":"composer","artist":{"name":"Cora Composer"}}
          ]}},
          {"type":"video","artist":{"name":"Ignored"}},
          {"type":"producer","artist":{"name":"  "}}
        ]}
    """.trimIndent()

    @Test
    fun `credits come from the recording and from its work`() {
        val credits = CreditsParser.parseCredits(lookup)!!
        assertEquals("rec1", credits.recordingId)
        assertTrue(Credit("Cora Composer", CreditRole.COMPOSER) in credits.credits)
        assertTrue(Credit("Lee Lyrics", CreditRole.LYRICIST) in credits.credits)
        assertTrue(Credit("Pat Producer", CreditRole.PRODUCER) in credits.credits)
        assertTrue(Credit("Mia Mixer", CreditRole.MIX) in credits.credits)
    }

    @Test
    fun `instruments and vocals keep what they play`() {
        val credits = CreditsParser.parseCredits(lookup)!!.credits
        assertEquals("guitar, bass guitar", credits.first { it.name == "Gus Guitar" }.detail)
        assertEquals("lead vocals", credits.first { it.name == "Vera Voice" }.detail)
    }

    @Test
    fun `a person credited twice for the same thing appears once`() {
        assertEquals(1, CreditsParser.parseCredits(lookup)!!.credits.count { it.name == "Cora Composer" })
    }

    @Test
    fun `unknown relation types and blank names are dropped`() {
        val names = CreditsParser.parseCredits(lookup)!!.credits.map { it.name }
        assertTrue("Ignored" !in names)
        assertTrue(names.none { it.isBlank() })
    }

    @Test
    fun `credits are grouped by role in a fixed order`() {
        val roles = CreditsParser.parseCredits(lookup)!!.credits.map { it.role }
        assertEquals(roles.sortedBy { it.ordinal }, roles)
    }

    @Test
    fun `a recording with no relations has empty credits`() {
        assertTrue(CreditsParser.parseCredits("""{"id":"r"}""")!!.isEmpty)
    }

    @Test
    fun `garbage is not credits`() {
        assertNull(CreditsParser.parseCredits("nope"))
        assertNull(CreditsParser.parseCredits("""{"relations":[]}"""))
    }

    @Test
    fun `the search query escapes quotes and backslashes`() {
        assertEquals(
            "recording:\"Say \\\"Hi\\\"\" AND artist:\"A\\\\B\"",
            CreditsParser.searchQuery("Say \"Hi\"", "A\\B"),
        )
    }
}
