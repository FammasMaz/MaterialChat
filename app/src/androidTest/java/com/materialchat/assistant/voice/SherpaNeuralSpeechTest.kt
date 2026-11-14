package com.materialchat.assistant.voice

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.materialchat.data.local.preferences.AssistantSpeechBackend
import com.materialchat.data.local.preferences.AssistantVoiceSettings
import android.os.SystemClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/**
 * Runs the production neural speech path on a real Android runtime: the real downloader
 * fetches and verifies each model, the real sherpa-onnx JNI loads it and synthesises, and
 * the real AudioTrack player plays the result. 2.29.39 crashed the process on the first
 * synthesis (native callback lookup); this test would have died with it.
 */
@RunWith(AndroidJUnit4::class)
class SherpaNeuralSpeechTest {

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private lateinit var store: SherpaModelStore

        @JvmStatic @BeforeClass fun downloadModels() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            store = SherpaModelStore(File(context.filesDir, "tts-test"), OkHttpSherpaArtifactFetcher(), scope)
            runBlocking {
                for (model in SherpaTtsModel.entries) {
                    if (!store.state(model).isInstalled) store.download(model)
                    val settled = withTimeout(15 * 60_000L) {
                        store.states.first { states ->
                            val state = states[model]
                            state is SherpaModelState.Installed || state is SherpaModelState.Failed
                        }[model]
                    }
                    assertTrue("$model failed to install: $settled", settled is SherpaModelState.Installed)
                }
            }
        }

        @JvmStatic @AfterClass fun cleanup() {
            SherpaTtsModel.entries.forEach { store.delete(it) }
            scope.cancel()
        }
    }

    private fun synthesize(model: SherpaTtsModel, text: String, stopAfterFirst: Boolean = false): Pair<Int, FloatArray> {
        val dir = requireNotNull(store.installedDir(model))
        val synth = OnnxSherpaSynthesizerFactory().load(model, dir)
        try {
            val chunks = mutableListOf<FloatArray>()
            synth.synthesize(text, 1f, "en") { samples ->
                chunks += samples.copyOf()
                !stopAfterFirst
            }
            val all = FloatArray(chunks.sumOf { it.size })
            var offset = 0
            chunks.forEach { it.copyInto(all, offset); offset += it.size }
            return synth.sampleRate to all
        } finally {
            synth.close()
        }
    }

    private fun assertAudible(model: SherpaTtsModel, sampleRate: Int, audio: FloatArray) {
        assertTrue("$model sample rate $sampleRate", sampleRate in 8_000..48_000)
        val seconds = audio.size.toFloat() / sampleRate
        assertTrue("$model produced ${seconds}s of audio", seconds > 0.5f)
        val peak = audio.maxOf { abs(it) }
        assertTrue("$model audio is silent (peak $peak)", peak > 0.01f)
        assertTrue("$model audio has non-finite samples", audio.all { it.isFinite() })
    }

    @Test fun supertonicSynthesisesAudibleSpeechThroughJni() {
        val (rate, audio) = synthesize(SherpaTtsModel.SUPERTONIC, "Hello, this is MaterialChat speaking.")
        assertAudible(SherpaTtsModel.SUPERTONIC, rate, audio)
    }

    @Test fun kokoroSynthesisesAudibleSpeechThroughJni() {
        val (rate, audio) = synthesize(SherpaTtsModel.KOKORO, "Hello, this is MaterialChat speaking.")
        assertAudible(SherpaTtsModel.KOKORO, rate, audio)
    }

    /**
     * Stop/barge-in cancels the speak flow. Supertonic returns a whole utterance in one native
     * callback, so stopping must come from the playback side: cancelling mid-playback has to
     * silence the AudioTrack promptly rather than finish the sentence.
     */
    @Test fun cancellingSpeechStopsPlaybackPromptly() = runBlocking {
        val backend = SherpaTtsBackend(store, OnnxSherpaSynthesizerFactory(), AudioTrackSherpaPlayer())
        val settings = AssistantVoiceSettings(backend = AssistantSpeechBackend.SHERPA, languageTag = "en-US")
        val text = "This sentence is deliberately long so that playback lasts several seconds, " +
            "which leaves plenty of time to interrupt it in the middle of speaking."
        val started = CompletableDeferred<Unit>()
        val events = mutableListOf<TtsEvent>()
        val job = launch(Dispatchers.Default) {
            backend.speak(text, settings, SherpaTtsModel.SUPERTONIC).collect {
                events += it
                if (it is TtsEvent.Started) started.complete(Unit)
            }
        }
        withTimeout(120_000) { started.await() }
        delay(300)
        val before = SystemClock.elapsedRealtime()
        withTimeout(10_000) { job.cancelAndJoin() }
        val stopMillis = SystemClock.elapsedRealtime() - before
        assertTrue("stop took ${stopMillis}ms", stopMillis < 1_000)
        assertTrue("speech completed instead of stopping: $events", TtsEvent.Completed !in events)
        backend.release()
    }

    @Test fun productionBackendSpeaksThroughAudioTrackWithoutCrashing() = runBlocking {
        val backend = SherpaTtsBackend(store, OnnxSherpaSynthesizerFactory(), AudioTrackSherpaPlayer())
        val settings = AssistantVoiceSettings(backend = AssistantSpeechBackend.SHERPA, languageTag = "en-US")
        for (model in SherpaTtsModel.entries) {
            val events = withTimeout(120_000) { backend.speak("Testing the neural voice.", settings, model).toList() }
            assertEquals("$model events: $events", listOf(TtsEvent.Started, TtsEvent.Completed), events)
        }
        backend.release()
    }
}
