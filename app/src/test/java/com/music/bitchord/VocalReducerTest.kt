package com.music.bitchord

import com.music.bitchord.playback.audio.AudioBlock
import com.music.bitchord.playback.audio.VocalReducer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

class VocalReducerTest {
    private val rate = 44_100
    private val frames = 44_100

    private fun reducer(on: Boolean = true) = VocalReducer().apply {
        configure(rate, 2)
        enabled = on
    }

    private fun tone(hz: Double, left: Float, right: Float): AudioBlock {
        val block = AudioBlock(channelCount = 2, capacityFrames = frames)
        for (n in 0 until frames) {
            val s = sin(2.0 * PI * hz * n / rate).toFloat()
            block.samples[2 * n] = s * left
            block.samples[2 * n + 1] = s * right
        }
        block.setFrameCount(frames)
        return block
    }

    private fun rms(block: AudioBlock, channel: Int, from: Int = frames / 2): Double {
        var sum = 0.0
        var count = 0
        for (n in from until block.frameCount) {
            val v = block.samples[2 * n + channel].toDouble()
            sum += v * v
            count++
        }
        return sqrt(sum / count)
    }

    private fun db(ratio: Double) = 20 * log10(ratio)

    @Test
    fun `off leaves every sample exactly as it was`() {
        val block = tone(1000.0, 0.7f, 0.5f)
        val before = block.samples.copyOf(block.sampleCount)
        reducer(on = false).process(block)
        assertTrue(before.contentEquals(block.samples.copyOf(block.sampleCount)))
    }

    @Test
    fun `a centre-panned voice is taken down by at least 15 dB`() {
        val block = tone(1000.0, 0.5f, 0.5f)
        val before = rms(block, 0)
        reducer().process(block)
        assertTrue(db(rms(block, 0) / before) < -15.0)
    }

    @Test
    fun `bass in the centre keeps its level`() {
        val block = tone(60.0, 0.5f, 0.5f)
        val before = rms(block, 0)
        reducer().process(block)
        assertEquals(0.0, db(rms(block, 0) / before), 1.5)
    }

    @Test
    fun `a sound that is only in one side is not touched`() {
        val block = tone(1000.0, 0.5f, -0.5f)
        val before = rms(block, 0)
        reducer().process(block)
        assertEquals(0.0, db(rms(block, 0) / before), 0.1)
    }

    @Test
    fun `a hard-panned instrument loses only its centre share`() {
        val block = tone(1000.0, 0.5f, 0.0f)
        val before = rms(block, 0)
        reducer().process(block)
        val after = rms(block, 0)
        assertTrue(after < before)
        assertTrue(db(after / before) > -6.5)
    }

    @Test
    fun `mono blocks and unconfigured processors pass through`() {
        val mono = AudioBlock(channelCount = 1, capacityFrames = 8)
        mono.samples.fill(0.3f)
        mono.setFrameCount(8)
        reducer().process(mono)
        assertEquals(0.3f, mono.samples[3], 0f)

        val block = tone(1000.0, 0.5f, 0.5f)
        val before = block.samples.copyOf(block.sampleCount)
        VocalReducer().apply { enabled = true }.process(block)
        assertTrue(before.contentEquals(block.samples.copyOf(block.sampleCount)))
    }

    @Test
    fun `output stays finite and never louder than the input`() {
        val block = tone(3000.0, 0.9f, 0.9f)
        reducer().process(block)
        for (n in 0 until block.sampleCount) {
            assertTrue(block.samples[n].isFinite())
            assertTrue(Math.abs(block.samples[n]) <= 1.0f)
        }
    }

    @Test
    fun `flush forgets the filter history`() {
        val a = tone(200.0, 0.5f, 0.5f)
        val b = tone(200.0, 0.5f, 0.5f)
        val warm = reducer()
        warm.process(tone(90.0, 0.8f, 0.8f))
        warm.flush()
        warm.process(a)
        reducer().process(b)
        assertTrue(a.samples.copyOf(a.sampleCount).contentEquals(b.samples.copyOf(b.sampleCount)))
    }
}
