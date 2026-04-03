package com.fitapp.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.fitapp.data.model.ExercisePhase
import kotlinx.coroutines.*
import kotlin.math.*

/**
 * Metronome engine backed by AudioTrack with programmatically generated PCM click sounds.
 * Runs on a dedicated coroutine dispatcher (not the main thread).
 *
 * Two sounds are produced:
 *   • Click — short high-pitched tick used for regular beat ticks.
 *   • Transition — slightly longer, lower-pitched tone that signals exercise/phase changes.
 */
class MetronomeEngine {

    private val sampleRate = 44100
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var metronomJob: Job? = null
    private var audioTrack: AudioTrack? = null

    // Prebuilt PCM buffers
    private val clickBuffer: ShortArray by lazy { buildClickBuffer(frequencyHz = 1800.0, durationMs = 18) }
    private val transitionBuffer: ShortArray by lazy { buildClickBuffer(frequencyHz = 880.0, durationMs = 80) }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Start a simple periodic metronome at [bpm] beats per minute.
     * @param playImmediately If true the first tick fires right away; otherwise after one beat interval.
     */
    fun startSimple(bpm: Int, playImmediately: Boolean = true) {
        if (bpm <= 0) return
        stop()
        ensureTrack()
        metronomJob = scope.launch {
            if (playImmediately) tick()
            val intervalNs = (60_000_000_000L / bpm)
            var nextTickNs = System.nanoTime() + intervalNs
            while (isActive) {
                val nowNs = System.nanoTime()
                val delayMs = ((nextTickNs - nowNs) / 1_000_000L).coerceAtLeast(0)
                delay(delayMs)
                tick()
                nextTickNs += intervalNs
            }
        }
    }

    /**
     * Start a phase-aware metronome. The metronome cycles through [phases] in order,
     * ticking at [bpm] BPM within each phase and emitting a [transitionTick] at phase
     * boundaries. The whole phase sequence repeats until [stop] is called.
     */
    fun startPhased(bpm: Int, phases: List<ExercisePhase>, onPhaseChange: (Int) -> Unit = {}) {
        if (phases.isEmpty()) { if (bpm > 0) startSimple(bpm); return }
        stop()
        ensureTrack()
        metronomJob = scope.launch {
            val intervalNs = if (bpm > 0) 60_000_000_000L / bpm else Long.MAX_VALUE
            while (isActive) {
                phases.forEachIndexed { phaseIndex, phase ->
                    if (!isActive) return@forEachIndexed
                    onPhaseChange(phaseIndex)
                    transitionTick()
                    val phaseEndNs = System.nanoTime() + phase.durationSeconds * 1_000_000_000L
                    var nextTickNs = System.nanoTime() + intervalNs
                    while (isActive && System.nanoTime() < phaseEndNs) {
                        val nowNs = System.nanoTime()
                        val delayMs = ((nextTickNs - nowNs) / 1_000_000L).coerceAtLeast(0)
                        delay(delayMs)
                        if (System.nanoTime() < phaseEndNs) tick()
                        nextTickNs += intervalNs
                    }
                }
            }
        }
    }

    /** Play the transition/exercise-change tone and optionally restart the metronome. */
    fun playTransitionCue() {
        scope.launch { transitionTick() }
    }

    fun stop() {
        metronomJob?.cancel()
        metronomJob = null
    }

    fun release() {
        stop()
        scope.cancel()
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private fun ensureTrack() {
        if (audioTrack?.state == AudioTrack.STATE_INITIALIZED) return
        audioTrack?.release()
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(
                AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ) * 2
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        audioTrack?.play()
    }

    private fun tick() = writeBuffer(clickBuffer)
    private fun transitionTick() = writeBuffer(transitionBuffer)

    private fun writeBuffer(buffer: ShortArray) {
        audioTrack?.write(buffer, 0, buffer.size)
    }

    /**
     * Generates a sine-wave click with an exponential decay envelope.
     * [frequencyHz] sets pitch; [durationMs] sets length in milliseconds.
     */
    private fun buildClickBuffer(frequencyHz: Double, durationMs: Int): ShortArray {
        val numSamples = (sampleRate * durationMs / 1000)
        val buffer = ShortArray(numSamples)
        val twoPiF = 2.0 * PI * frequencyHz / sampleRate
        for (i in 0 until numSamples) {
            val envelope = exp(-6.0 * i / numSamples)       // fast exponential decay
            val sample = sin(twoPiF * i) * envelope
            buffer[i] = (sample * Short.MAX_VALUE).toInt().toShort()
        }
        return buffer
    }
}
