package com.music.amber.playback.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object SingModeState {
    @Volatile
    var enabled: Boolean = false
}

class VocalReducer : FloatAudioProcessor {
    @Volatile
    var enabled: Boolean = false

    private var b0 = 0.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0
    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0
    private var configured = false

    override fun configure(sampleRate: Int, channelCount: Int) {
        configured = sampleRate > 0 && channelCount == 2
        if (!configured) return
        val w0 = 2.0 * PI * BASS_CUTOFF_HZ / sampleRate
        val alpha = sin(w0) / (2.0 * (1.0 / sqrt(2.0)))
        val c = cos(w0)
        val a0 = 1.0 + alpha
        b0 = (1.0 - c) / 2.0 / a0
        b1 = (1.0 - c) / a0
        b2 = b0
        a1 = -2.0 * c / a0
        a2 = (1.0 - alpha) / a0
        flush()
    }

    override fun process(block: AudioBlock) {
        if (!enabled || !configured || block.channelCount != 2 || block.frameCount == 0) return
        val samples = block.samples
        val total = block.frameCount * 2
        var i = 0
        var px1 = x1
        var px2 = x2
        var py1 = y1
        var py2 = y2
        while (i < total) {
            val left = samples[i]
            val right = samples[i + 1]
            val mid = (left + right) * 0.5
            val side = (left - right) * 0.5
            val low = b0 * mid + b1 * px1 + b2 * px2 - a1 * py1 - a2 * py2
            px2 = px1
            px1 = mid
            py2 = py1
            py1 = low
            val reducedMid = low + (mid - low) * (1.0 - ATTENUATION)
            samples[i] = (reducedMid + side).toFloat()
            samples[i + 1] = (reducedMid - side).toFloat()
            i += 2
        }
        x1 = px1
        x2 = px2
        y1 = py1
        y2 = py2
    }

    override fun flush() {
        x1 = 0.0
        x2 = 0.0
        y1 = 0.0
        y2 = 0.0
    }

    override fun reset() = flush()

    companion object {
        const val BASS_CUTOFF_HZ = 120.0
        const val ATTENUATION = 0.9
    }
}
