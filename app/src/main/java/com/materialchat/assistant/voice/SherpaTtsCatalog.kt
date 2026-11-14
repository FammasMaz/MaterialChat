package com.materialchat.assistant.voice

/** One downloadable file of a neural voice model. Checksums were computed from the published assets. */
data class SherpaArtifact(
    val url: String,
    val sizeBytes: Long,
    val sha256: String,
    /** tar.bz2 archives are unpacked; plain files are copied into the install directory as [fileName]. */
    val archive: Boolean,
    val fileName: String
)

enum class SherpaModelFamily { SUPERTONIC, KOKORO }

/**
 * Single source of truth for the on-device neural voices (sherpa-onnx model zoo):
 * download URLs, sizes, SHA-256 checksums and the files each model needs on disk.
 * Models are never bundled in the APK; they are downloaded into `filesDir/tts/<id>/`.
 */
enum class SherpaTtsModel(
    val id: String,
    val label: String,
    val description: String,
    val family: SherpaModelFamily,
    val artifacts: List<SherpaArtifact>,
    /** Top-level directory inside the archive; it is stripped on install. */
    val archiveDir: String,
    /** Files that must exist in the install directory (paths relative to it). */
    val files: Set<String>,
    val slowerThanRealTime: Boolean
) {
    SUPERTONIC(
        id = "supertonic-int8-2026-03-06",
        label = "Supertonic (fast)",
        description = "66M-parameter voice tuned for phone CPUs. Synthesises far faster than real time, " +
            "so each sentence starts playing almost immediately. English, Korean, Spanish, Portuguese, French.",
        family = SherpaModelFamily.SUPERTONIC,
        artifacts = listOf(
            SherpaArtifact(
                url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/sherpa-onnx-supertonic-tts-int8-2026-03-06.tar.bz2",
                sizeBytes = 84_692_981L,
                sha256 = "8c74359f63edd5045d47747f65331f0f6dbcbc91d7e898dd756d631295fe3259",
                archive = true,
                fileName = "sherpa-onnx-supertonic-tts-int8-2026-03-06.tar.bz2"
            )
        ),
        archiveDir = "sherpa-onnx-supertonic-tts-int8-2026-03-06",
        files = setOf(
            "duration_predictor.int8.onnx", "text_encoder.int8.onnx", "vector_estimator.int8.onnx",
            "vocoder.int8.onnx", "tts.json", "unicode_indexer.bin", "voice.bin"
        ),
        slowerThanRealTime = false
    ),
    KOKORO(
        id = "kokoro-int8-en-v0_19",
        label = "Kokoro-82M (best quality)",
        description = "Higher-quality English voice (int8). Can be slower than real time on a phone, " +
            "so sentences may pause before they play.",
        family = SherpaModelFamily.KOKORO,
        artifacts = listOf(
            SherpaArtifact(
                url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-int8-en-v0_19.tar.bz2",
                sizeBytes = 103_248_205L,
                sha256 = "c9f0dd393615805b0bab050c340834d5e684e732aec91c0e860cd30e982c08bd",
                archive = true,
                fileName = "kokoro-int8-en-v0_19.tar.bz2"
            ),
            // sherpa-onnx 2.x ignores the bundled espeak-ng-data; English text needs this lexicon instead.
            SherpaArtifact(
                url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/lexicon-en-us.txt",
                sizeBytes = 6_305_773L,
                sha256 = "8b67ae549a465eee78f92ecb74644ad90c3c58ce65f19c68baf85878a10ef73a",
                archive = false,
                fileName = "lexicon-en-us.txt"
            )
        ),
        archiveDir = "kokoro-int8-en-v0_19",
        files = setOf("model.int8.onnx", "voices.bin", "tokens.txt", "lexicon-en-us.txt"),
        slowerThanRealTime = true
    );

    val downloadBytes: Long get() = artifacts.sumOf { it.sizeBytes }

    /** Written after a verified install; a different value means the catalog changed and a re-download is needed. */
    val manifest: String get() = artifacts.joinToString("\n") { "${it.sha256} ${it.fileName}" }

    companion object {
        val DEFAULT = SUPERTONIC
        private val OPTIONAL_FILES = setOf("LICENSE", "README.md")

        fun fromId(id: String?): SherpaTtsModel = entries.firstOrNull { it.id == id?.trim() } ?: DEFAULT

        /**
         * Maps an archive entry to its install-relative path, or null when the entry is not part of the
         * model (unused data such as espeak-ng dictionaries) or looks like a path traversal.
         */
        fun installPath(model: SherpaTtsModel, entryName: String): String? {
            val name = entryName.removePrefix("./")
            val prefix = model.archiveDir + "/"
            if (!name.startsWith(prefix)) return null
            val relative = name.removePrefix(prefix)
            if (relative.isEmpty() || relative.startsWith("/") || relative.split('/').any { it == ".." }) return null
            return relative.takeIf { it in model.files || it in OPTIONAL_FILES }
        }
    }
}

/** Human-readable size for the settings UI, e.g. "85 MB". */
fun formatModelBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000L -> "${(bytes + 500_000L) / 1_000_000L} MB"
    bytes >= 1_000L -> "${(bytes + 500L) / 1_000L} kB"
    else -> "$bytes B"
}
