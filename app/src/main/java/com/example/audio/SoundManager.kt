package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

/**
 * Low-latency SoundPool-backed manager for UNO Chess tactile sound effects.
 * Synthesizes and caches lightweight WAV buffers on a background thread and plays
 * overlapping effects cleanly with subtle natural pitch variation on moves/draws.
 */
class SoundManager(
    context: Context
) : SoundPlayer {

    private companion object {
        const val CACHE_DIR_NAME = "uno_chess_sfx_v1"
        const val SEQUENCE_STAGGER_MS = 115L
        const val MAX_STREAMS = 6
    }

    private val appContext = context.applicationContext
    private val initialized = AtomicBoolean(false)
    private val released = AtomicBoolean(false)
    private val soundIds = ConcurrentHashMap<SoundEffect, Int>()
    private val readyIds = ConcurrentHashMap.newKeySet<Int>()

    private var soundPool: SoundPool? = null
    private var scheduler: ScheduledExecutorService? = null

    @Volatile
    override var isEnabled: Boolean = true

    fun initialize() {
        if (!initialized.compareAndSet(false, true)) return
        if (released.get()) return

        runCatching {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val pool = SoundPool.Builder()
                .setMaxStreams(MAX_STREAMS)
                .setAudioAttributes(attrs)
                .build()

            pool.setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0) {
                    readyIds.add(sampleId)
                }
            }
            soundPool = pool

            scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
                Thread(runnable, "UnoChessSoundScheduler").apply { isDaemon = true }
            }

            // Synthesize & cache WAV files off the main thread
            Thread({
                loadAllEffects(pool)
            }, "UnoChessSoundInit").apply {
                isDaemon = true
                start()
            }
        }
    }

    private fun loadAllEffects(pool: SoundPool) {
        if (released.get()) return
        val cacheDir = File(appContext.cacheDir, CACHE_DIR_NAME).apply { mkdirs() }
        for (effect in SoundEffect.entries) {
            if (released.get()) return
            runCatching {
                val wavFile = File(cacheDir, effect.fileName)
                if (!wavFile.exists() || wavFile.length() < 44L) {
                    wavFile.writeBytes(SoundSynthesizer.generateWavBytes(effect))
                }
                val id = pool.load(wavFile.absolutePath, 1)
                if (id > 0) {
                    soundIds[effect] = id
                }
            }
        }
    }

    override fun play(effect: SoundEffect) {
        if (!isEnabled || released.get()) return
        val pool = soundPool ?: return
        val id = soundIds[effect] ?: return
        if (id !in readyIds) return

        val rate = if (effect.pitchJitter > 0f) {
            val delta = (Random.nextFloat() * 2f - 1f) * effect.pitchJitter
            (1.0f + delta).coerceIn(0.85f, 1.2f)
        } else {
            1.0f
        }
        val priority = when (effect) {
            SoundEffect.CHECKMATE, SoundEffect.FORFEIT, SoundEffect.CHECK -> 2
            else -> 1
        }
        runCatching {
            pool.play(id, effect.volume, effect.volume, priority, 0, rate)
        }
    }

    override fun playSequence(effects: List<SoundEffect>) {
        if (!isEnabled || released.get() || effects.isEmpty()) return
        if (effects.size == 1) {
            play(effects[0])
            return
        }
        play(effects[0])
        val exec = scheduler ?: return
        for (idx in 1 until effects.size) {
            val effect = effects[idx]
            runCatching {
                exec.schedule(
                    { play(effect) },
                    idx * SEQUENCE_STAGGER_MS,
                    TimeUnit.MILLISECONDS
                )
            }
        }
    }

    override fun release() {
        if (!released.compareAndSet(false, true)) return
        runCatching { scheduler?.shutdownNow() }
        scheduler = null
        runCatching { soundPool?.release() }
        soundPool = null
        soundIds.clear()
        readyIds.clear()
    }
}
