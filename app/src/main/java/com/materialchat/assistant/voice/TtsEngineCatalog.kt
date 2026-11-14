package com.materialchat.assistant.voice

/** An installed Android text-to-speech engine. */
data class TtsEngineOption(val packageName: String, val label: String, val isSystemDefault: Boolean)

/**
 * Installed TTS engines, best first: the system default, then engines that are not the
 * stock AOSP/Google engine, then the rest. Neural engines installed by the user (Piper,
 * Kokoro, Supertonic based apps) sound far better than the stock voice, so the picker
 * must surface them rather than hide them behind the default.
 */
fun rankTtsEngines(engines: List<TtsEngineOption>): List<TtsEngineOption> = engines
    .distinctBy { it.packageName }
    .sortedWith(
        compareBy<TtsEngineOption> { if (it.isSystemDefault) 0 else 1 }
            .thenBy { if (it.packageName in STOCK_ENGINES) 1 else 0 }
            .thenBy { it.label.lowercase() }
    )

/** Engine used when the user has not chosen one: the system default, else the first ranked. */
fun selectTtsEngine(engines: List<TtsEngineOption>, preferredPackage: String?): TtsEngineOption? {
    val ranked = rankTtsEngines(engines)
    return ranked.firstOrNull { preferredPackage != null && it.packageName == preferredPackage }
        ?: ranked.firstOrNull()
}

private val STOCK_ENGINES = setOf("com.google.android.tts", "com.android.tts")

/** Installed TTS engines for the settings picker; empty when the platform cannot enumerate. */
fun installedTtsEngines(context: android.content.Context): List<TtsEngineOption> = runCatching {
    val packageManager = context.packageManager
    val defaultPackage = android.provider.Settings.Secure.getString(
        context.contentResolver,
        "tts_default_synth"
    )
    packageManager.queryIntentServices(
        android.content.Intent(android.speech.tts.TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE),
        0
    ).mapNotNull { resolved ->
        val service = resolved.serviceInfo ?: return@mapNotNull null
        val label = runCatching {
            service.applicationInfo.loadLabel(packageManager).toString()
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: service.packageName
        TtsEngineOption(
            packageName = service.packageName,
            label = label,
            isSystemDefault = service.packageName == defaultPackage
        )
    }
}.getOrDefault(emptyList())
