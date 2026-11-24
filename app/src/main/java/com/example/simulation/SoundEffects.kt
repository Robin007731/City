package com.example.simulation

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

class SoundEffects {
    @Volatile
    var muted: Boolean = false

    private val executor = Executors.newSingleThreadExecutor()
    private val sampleRate = 22050

    @Volatile private var lastThunderMs = 0L
    @Volatile private var lastCrashMs = 0L
    @Volatile private var lastHonkMs = 0L
    @Volatile private var lastZapMs = 0L
    @Volatile private var lastTrainMs = 0L
    @Volatile private var lastBellMs = 0L

    fun playBuild() = playNotes(doubleArrayOf(523.25, 659.25), 0.065, 0.14)
    fun playRoad() = playNotes(doubleArrayOf(392.0), 0.045, 0.10)
    fun playDemolish() = playNotes(doubleArrayOf(196.0, 146.83), 0.08, 0.14)
    fun playError() = playNotes(doubleArrayOf(220.0, 185.0), 0.07, 0.11)
    fun playExpand() = playNotes(doubleArrayOf(440.0, 554.37, 659.25), 0.09, 0.16)
    fun playEraFanfare() = playNotes(doubleArrayOf(523.25, 659.25, 783.99, 1046.50), 0.12, 0.18)

    fun playBell() {
        val now = System.currentTimeMillis()
        if (muted || now - lastBellMs < 4000L) return
        lastBellMs = now
        playNotes(doubleArrayOf(523.25, 659.25, 523.25), 0.24, 0.12)
    }

    fun playTrainWhistle() {
        val now = System.currentTimeMillis()
        if (muted || now - lastTrainMs < 1500L) return
        lastTrainMs = now
        playNotes(doubleArrayOf(440.0, 554.37), 0.18, 0.12)
    }

    fun playHonk() {
        val now = System.currentTimeMillis()
        if (muted || now - lastHonkMs < 850L) return
        lastHonkMs = now
        playNotes(doubleArrayOf(349.23, 440.0), 0.085, 0.09)
    }

    fun playZap() {
        val now = System.currentTimeMillis()
        if (muted || now - lastZapMs < 250L) return
        lastZapMs = now
        playCustom(0.28, 0.22) { t ->
            val f = 900.0 * exp(-t * 9.0) + 120.0
            (sin(2.0 * PI * f * t) + 0.4 * (Random.nextDouble() * 2.0 - 1.0)) * exp(-t * 10.0)
        }
    }

    fun playCrash() {
        val now = System.currentTimeMillis()
        if (muted || now - lastCrashMs < 1200L) return
        lastCrashMs = now
        playCustom(0.32, 0.20) { t ->
            val noise = Random.nextDouble() * 2.0 - 1.0
            val thud = sin(2.0 * PI * (120.0 * exp(-t * 8.0) + 40.0) * t)
            (noise * 0.65 + thud * 0.55) * exp(-t * 9.5)
        }
    }

    fun playThunder() {
        val now = System.currentTimeMillis()
        if (muted || now - lastThunderMs < 2500L) return
        lastThunderMs = now
        playCustom(0.65, 0.18) { t ->
            val rumble = sin(2.0 * PI * 54.0 * t) + 0.5 * sin(2.0 * PI * 79.0 * t)
            val noise = Random.nextDouble() * 2.0 - 1.0
            (rumble * 0.55 + noise * 0.45) * exp(-t * 3.2)
        }
    }

    private fun playNotes(freqs: DoubleArray, noteDurSec: Double, volume: Double) {
        if (muted) return
        executor.execute {
            try {
                val samplesPerNote = (sampleRate * noteDurSec).toInt()
                val totalSamples = samplesPerNote * freqs.size
                val pcm = ShortArray(totalSamples)
                for (n in freqs.indices) {
                    val f = freqs[n]
                    val offset = n * samplesPerNote
                    for (i in 0 until samplesPerNote) {
                        val t = i.toDouble() / sampleRate
                        val env = exp(-t * 14.0)
                        val wave = sin(2.0 * PI * f * t) + 0.35 * sin(4.0 * PI * f * t)
                        val sample = (wave * env * volume * Short.MAX_VALUE).toInt()
                            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        pcm[offset + i] = sample.toShort()
                    }
                }
                dispatchPcm(pcm, (noteDurSec * freqs.size * 1000).toLong() + 60L)
            } catch (_: Throwable) {
            }
        }
    }

    private fun playCustom(durSec: Double, volume: Double, generator: (Double) -> Double) {
        if (muted) return
        executor.execute {
            try {
                val total = (sampleRate * durSec).toInt().coerceAtLeast(1)
                val pcm = ShortArray(total)
                for (i in 0 until total) {
                    val t = i.toDouble() / sampleRate
                    val v = generator(t).coerceIn(-1.0, 1.0)
                    pcm[i] = (v * volume * Short.MAX_VALUE).toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        .toShort()
                }
                dispatchPcm(pcm, (durSec * 1000).toLong() + 60L)
            } catch (_: Throwable) {
            }
        }
    }

    private fun dispatchPcm(pcm: ShortArray, sleepMs: Long) {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(pcm, 0, pcm.size)
        track.play()
        Thread.sleep(sleepMs)
        track.release()
    }

    fun release() {
        executor.shutdownNow()
    }
}
