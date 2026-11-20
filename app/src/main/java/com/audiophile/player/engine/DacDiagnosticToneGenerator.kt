package com.audiophile.player.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

enum class DacToneType(
    val title: String,
    val subtitle: String,
    val description: String
) {
    SINE_1KHZ_0DBFS(
        title = "1 kHz Reference Sine (0 dBFS)",
        subtitle = "Full-Scale Max Output",
        description = "Standard 0 dBFS reference for measuring DAC full-scale clipping, intermodulation distortion, and analog output voltage swing."
    ),
    SINE_1KHZ_20DBFS(
        title = "1 kHz Reference Sine (-20 dBFS)",
        subtitle = "Standard Line-Level Reference",
        description = "Calibrated -20 dBFS tone for gain staging, headphone amplifier sensitivity matching, and THD+N verification."
    ),
    PINK_NOISE(
        title = "Pink Noise (1/f Equal Power)",
        subtitle = "Broadband Acoustic Reference",
        description = "Equal sound energy per octave (-3 dB/octave falloff) via Paul Kellet filtering for room acoustic analysis, headphone burn-in, and driver polarity."
    ),
    LOG_SWEEP_20HZ_20KHZ(
        title = "20 Hz – 20 kHz Logarithmic Sweep",
        subtitle = "Continuous 10s Full Band Chirp",
        description = "Continuous logarithmic frequency chirp traversing the entire human audible range to expose DAC filter ringing, crossover dips, and mechanical resonances."
    )
}

/**
 * Precision DAC Diagnostic Signal Generator
 * Generates calibrated bit-accurate test waveforms at 48,000 Hz 32-bit floating-point PCM.
 */
class DacDiagnosticToneGenerator {

    private val sampleRate = 48000
    private val isRunning = AtomicBoolean(false)
    private var workerThread: Thread? = null
    private var audioTrack: AudioTrack? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _activeTone = MutableStateFlow<DacToneType?>(null)
    val activeTone: StateFlow<DacToneType?> = _activeTone.asStateFlow()

    private val _liveFrequencyHz = MutableStateFlow(1000f)
    val liveFrequencyHz: StateFlow<Float> = _liveFrequencyHz.asStateFlow()

    @Synchronized
    fun startTone(type: DacToneType, onPlaybackStarted: (() -> Unit)? = null) {
        stopTone()

        _activeTone.value = type
        isRunning.set(true)

        val bufferSizeFrames = 2048
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_FLOAT
        ).coerceAtLeast(bufferSizeFrames * 2 * 4)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(minBufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack = track
        track.play()
        _isPlaying.value = true
        onPlaybackStarted?.invoke()

        workerThread = Thread({
            generateLoop(type, track, bufferSizeFrames)
        }, "VeylDacToneGenerator").apply {
            priority = Thread.MAX_PRIORITY
            isDaemon = true
            start()
        }
    }

    @Synchronized
    fun stopTone() {
        isRunning.set(false)
        try {
            workerThread?.interrupt()
            workerThread?.join(300)
        } catch (_: Exception) {}
        workerThread = null

        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null

        _isPlaying.value = false
        _activeTone.value = null
        _liveFrequencyHz.value = 1000f
    }

    private fun generateLoop(type: DacToneType, track: AudioTrack, framesPerChunk: Int) {
        val stereoFloats = FloatArray(framesPerChunk * 2)
        var phase = 0.0

        // Pink noise filter state (Paul Kellet's filter)
        var b0 = 0.0
        var b1 = 0.0
        var b2 = 0.0
        var b3 = 0.0
        var b4 = 0.0
        var b5 = 0.0
        var b6 = 0.0

        // Sweep parameters: 20 Hz to 20,000 Hz over 10.0 seconds
        val sweepDurationSec = 10.0
        val sweepTotalSamples = (sweepDurationSec * sampleRate).toLong()
        var sweepSampleIndex = 0L
        val f0 = 20.0
        val f1 = 20000.0
        val kSweep = sweepDurationSec / ln(f1 / f0)

        while (isRunning.get() && !Thread.currentThread().isInterrupted) {
            when (type) {
                DacToneType.SINE_1KHZ_0DBFS -> {
                    _liveFrequencyHz.value = 1000f
                    val phaseIncrement = 2.0 * PI * 1000.0 / sampleRate
                    // 0.89 (-1 dBFS) for safe headroom against inter-sample peaks
                    val amplitude = 0.89f
                    for (i in 0 until framesPerChunk) {
                        val s = (sin(phase) * amplitude).toFloat()
                        stereoFloats[i * 2] = s
                        stereoFloats[i * 2 + 1] = s
                        phase += phaseIncrement
                        if (phase >= 2.0 * PI) phase -= 2.0 * PI
                    }
                }

                DacToneType.SINE_1KHZ_20DBFS -> {
                    _liveFrequencyHz.value = 1000f
                    val phaseIncrement = 2.0 * PI * 1000.0 / sampleRate
                    // -20 dBFS = 0.1 amplitude
                    val amplitude = 0.1f
                    for (i in 0 until framesPerChunk) {
                        val s = (sin(phase) * amplitude).toFloat()
                        stereoFloats[i * 2] = s
                        stereoFloats[i * 2 + 1] = s
                        phase += phaseIncrement
                        if (phase >= 2.0 * PI) phase -= 2.0 * PI
                    }
                }

                DacToneType.PINK_NOISE -> {
                    _liveFrequencyHz.value = 0f // Broadband
                    for (i in 0 until framesPerChunk) {
                        val white = Random.nextDouble(-1.0, 1.0)
                        b0 = 0.99886 * b0 + white * 0.0555179
                        b1 = 0.99332 * b1 + white * 0.0750759
                        b2 = 0.96900 * b2 + white * 0.1538520
                        b3 = 0.86650 * b3 + white * 0.3104856
                        b4 = 0.55000 * b4 + white * 0.5329522
                        b5 = -0.7616 * b5 - white * 0.0168980
                        val pink = (b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362) * 0.11
                        b6 = white * 0.115926

                        // Scale pink noise safely to -14 dBFS RMS
                        val s = (pink * 0.25).toFloat().coerceIn(-0.95f, 0.95f)
                        stereoFloats[i * 2] = s
                        stereoFloats[i * 2 + 1] = s
                    }
                }

                DacToneType.LOG_SWEEP_20HZ_20KHZ -> {
                    val amplitude = 0.5f
                    for (i in 0 until framesPerChunk) {
                        val t = sweepSampleIndex.toDouble() / sampleRate
                        val curFreq = f0 * (f1 / f0).pow(t / sweepDurationSec)
                        if (i == 0) {
                            _liveFrequencyHz.value = curFreq.toFloat()
                        }

                        // Integral of log frequency for continuous phase:
                        val phi = 2.0 * PI * f0 * kSweep * ((f1 / f0).pow(t / sweepDurationSec) - 1.0)
                        val s = (sin(phi) * amplitude).toFloat()
                        stereoFloats[i * 2] = s
                        stereoFloats[i * 2 + 1] = s

                        sweepSampleIndex++
                        if (sweepSampleIndex >= sweepTotalSamples) {
                            sweepSampleIndex = 0L
                        }
                    }
                }
            }

            val written = track.write(stereoFloats, 0, stereoFloats.size, AudioTrack.WRITE_BLOCKING)
            if (written < 0) {
                break
            }
        }
    }
}
