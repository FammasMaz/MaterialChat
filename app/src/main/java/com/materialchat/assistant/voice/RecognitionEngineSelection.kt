package com.materialchat.assistant.voice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/** A service returned by the installed recognition-service query, not an arbitrary component. */
internal data class RecognitionServiceCandidate(
    val component: String,
    val packageName: String,
    val enabled: Boolean,
    val exported: Boolean,
    val permission: String?,
    val systemApp: Boolean
)

/**
 * Ranks recognizer engines by transcription quality, never alphabetically. A usable external
 * configured default wins (except Android System Intelligence's on-device model).
 *
 * While MaterialChat is the default assistant, Android points `voice_recognition_service` at the
 * assistant's own declared recognitionService (a component inside this package), so the
 * configured default is unusable and must not drive the choice. The Google app's recognizer is
 * the server-backed engine Gemini and Google Assistant use; it is preferred over Speech Services
 * by Google and over Android System Intelligence's small on-device model, which an alphabetical
 * pick ("com.google.android.as" < "com.google.android.googlequicksearchbox") silently chose.
 */
internal fun selectRecognitionService(
    configuredComponent: String?,
    ownPackage: String,
    candidates: List<RecognitionServiceCandidate>,
    preferredComponent: String? = null
): RecognitionServiceCandidate? {
    // No platform bind permission exists for recognition services (only BIND_VOICE_INTERACTION /
    // BIND_MUSIC_RECOGNITION_SERVICE do); engines declare none or their own guard, enforced at bind.
    val ranked = rankRecognitionServices(configuredComponent, ownPackage, candidates)
    return ranked.firstOrNull { preferredComponent != null && it.component == preferredComponent }
        ?: ranked.firstOrNull()
}

/** Usable external engines, best transcription quality first. */
internal fun rankRecognitionServices(
    configuredComponent: String?,
    ownPackage: String,
    candidates: List<RecognitionServiceCandidate>
): List<RecognitionServiceCandidate> = candidates
    .filter { it.packageName != ownPackage && it.enabled && it.exported }
    .sortedWith(
        compareBy<RecognitionServiceCandidate> { recognitionQualityRank(it, configuredComponent) }
            .thenBy { it.component }
    )

internal const val GOOGLE_APP_PACKAGE = "com.google.android.googlequicksearchbox"
internal const val GOOGLE_SPEECH_SERVICES_PACKAGE = "com.google.android.tts"
internal const val ANDROID_SYSTEM_INTELLIGENCE_PACKAGE = "com.google.android.as"

private fun recognitionQualityRank(candidate: RecognitionServiceCandidate, configured: String?): Int = when {
    // A usable external default is an explicit system/user choice. While MaterialChat is the
    // assistant the default points into our own package and is already filtered out.
    candidate.component == configured && candidate.packageName != ANDROID_SYSTEM_INTELLIGENCE_PACKAGE -> 0
    candidate.packageName == GOOGLE_APP_PACKAGE -> 1
    candidate.packageName == GOOGLE_SPEECH_SERVICES_PACKAGE -> 2
    candidate.packageName == ANDROID_SYSTEM_INTELLIGENCE_PACKAGE -> 5
    candidate.systemApp -> 3
    else -> 4
}

/** Human label for a recognizer component, for settings and diagnostics. */
fun recognitionEngineLabel(component: String): String {
    val pkg = component.substringBefore('/')
    return when (pkg) {
        GOOGLE_APP_PACKAGE -> "Google app (same engine as Gemini)"
        GOOGLE_SPEECH_SERVICES_PACKAGE -> "Speech Services by Google"
        ANDROID_SYSTEM_INTELLIGENCE_PACKAGE -> "Android System Intelligence (on-device)"
        else -> component
    }
}

private data class RecognitionServices(
    val configured: String?,
    val candidates: List<RecognitionServiceCandidate>,
    val selected: RecognitionServiceCandidate?
)

private fun installedRecognitionServices(context: Context, preferred: String? = null): RecognitionServices {
    val manager = context.packageManager
    val configured = runCatching {
        Settings.Secure.getString(context.contentResolver, "voice_recognition_service")
            ?.let(ComponentName::unflattenFromString)?.flattenToString()
    }.getOrNull()
    val candidates = manager.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
        .mapNotNull { result ->
            val service = result.serviceInfo ?: return@mapNotNull null
            val component = ComponentName(service.packageName, service.name)
            val componentState = manager.getComponentEnabledSetting(component)
            val componentEnabled = when (componentState) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> service.enabled
                else -> false
            }
            RecognitionServiceCandidate(
                component = component.flattenToString(),
                packageName = service.packageName,
                enabled = componentEnabled && service.applicationInfo.enabled,
                exported = service.exported,
                permission = service.permission,
                systemApp = service.applicationInfo.flags and
                    (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            )
        }
    return RecognitionServices(configured, candidates,
        selectRecognitionService(configured, context.packageName, candidates, preferred))
}

private fun onDeviceRecognitionAvailable(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)

/** Selection diagnostics, not a claim that an engine has already transcribed successfully. */
data class RecognitionEngineReport(
    val defaultComponent: String?,
    val visibleServiceCount: Int,
    val usesOnDeviceFallback: Boolean,
    val selectedComponent: String? = null,
    /** Every usable engine, best first; lets the user override the automatic choice. */
    val availableComponents: List<String> = emptyList()
)

fun recognitionEngineReport(context: Context, preferredComponent: String? = null): RecognitionEngineReport {
    val services = runCatching { installedRecognitionServices(context, preferredComponent) }.getOrNull()
    return RecognitionEngineReport(
        defaultComponent = services?.configured,
        visibleServiceCount = services?.candidates?.size ?: 0,
        usesOnDeviceFallback = services?.selected == null && onDeviceRecognitionAvailable(context),
        selectedComponent = services?.selected?.component,
        availableComponents = services?.let {
            rankRecognitionServices(it.configured, context.packageName, it.candidates)
        }.orEmpty().map { it.component }
    )
}

/** Explicit selection bypasses a stale or nonexistent configured default without changing it. */
internal class AndroidRecognitionPlatform(context: Context) : RecognitionPlatform {
    private val context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var primaryWasOnDevice = false

    override fun isAvailable(): Boolean =
        runCatching { installedRecognitionServices(context).selected != null }.getOrDefault(false) ||
            onDeviceRecognitionAvailable(context)

    override fun createRecognizer(preferredComponent: String?): SpeechRecognizer {
        primaryWasOnDevice = false
        val service = installedRecognitionServices(context, preferredComponent).selected
        android.util.Log.i("MaterialChatSpeech", "recognizer=${service?.component ?: "on-device"}")
        if (service != null) {
            return SpeechRecognizer.createSpeechRecognizer(context,
                requireNotNull(ComponentName.unflattenFromString(service.component)))
        }
        check(onDeviceRecognitionAvailable(context)) { "Speech recognition not available" }
        primaryWasOnDevice = true
        return SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
    }

    override fun createFallbackRecognizer(): SpeechRecognizer? {
        if (primaryWasOnDevice || !onDeviceRecognitionAvailable(context)) return null
        return SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
    }

    override fun dispatch(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else post(action)
    }
    override fun post(action: () -> Unit) { handler.post { action() } }
    override fun postDelayed(action: Runnable, delayMillis: Long) { handler.postDelayed(action, delayMillis) }
    override fun removeCallbacks(action: Runnable) { handler.removeCallbacks(action) }
}
