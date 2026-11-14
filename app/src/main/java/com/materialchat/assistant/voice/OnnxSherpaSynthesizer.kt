package com.materialchat.assistant.voice

import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig
import java.io.File

/** Builds sherpa-onnx [OfflineTts] instances from an installed model directory. */
class OnnxSherpaSynthesizerFactory : SherpaSynthesizerFactory {
    override fun load(model: SherpaTtsModel, dir: File): SherpaSynthesizer {
        fun path(name: String): String {
            val file = File(dir, name)
            check(file.isFile) { "Missing model file $name" }
            return file.absolutePath
        }
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val modelConfig = when (model.family) {
            SherpaModelFamily.SUPERTONIC -> OfflineTtsModelConfig(
                supertonic = OfflineTtsSupertonicModelConfig(
                    durationPredictor = path("duration_predictor.int8.onnx"),
                    textEncoder = path("text_encoder.int8.onnx"),
                    vectorEstimator = path("vector_estimator.int8.onnx"),
                    vocoder = path("vocoder.int8.onnx"),
                    ttsJson = path("tts.json"),
                    unicodeIndexer = path("unicode_indexer.bin"),
                    voiceStyle = path("voice.bin")
                ),
                numThreads = minOf(2, cores)
            )
            SherpaModelFamily.KOKORO -> OfflineTtsModelConfig(
                kokoro = OfflineTtsKokoroModelConfig(
                    model = path("model.int8.onnx"),
                    voices = path("voices.bin"),
                    tokens = path("tokens.txt"),
                    lexicon = path("lexicon-en-us.txt")
                ),
                numThreads = minOf(4, cores)
            )
        }
        // One sentence per callback so playback starts after the first sentence, not the whole text.
        val tts = OfflineTts(config = OfflineTtsConfig(model = modelConfig, maxNumSentences = 1))
        return object : SherpaSynthesizer {
            override val sampleRate: Int = tts.sampleRate()
            override fun synthesize(text: String, speed: Float, language: String, onSamples: (FloatArray) -> Boolean) {
                val config = GenerationConfig(speed = speed)
                if (model.family == SherpaModelFamily.SUPERTONIC) config.extra = mapOf("lang" to language)
                // Must be SherpaSampleCallback, never a lambda: see its KDoc (native crash otherwise).
                tts.generateWithConfigAndCallback(text, config, SherpaSampleCallback { onSamples(it) })
            }
            override fun close() = tts.release()
        }
    }
}
