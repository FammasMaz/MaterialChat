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

internal const val RECOGNITION_BIND_PERMISSION = "android.permission.BIND_SPEECH_RECOGNITION"

/** A service returned by the installed recognition-service query, not an arbitrary component. */
internal data class RecognitionServiceCandidate(
    val component: String,
    val packageName: String,
    val enabled: Boolean,
    val exported: Boolean,
    val permission: String?,
    val systemApp: Boolean
)

/** Availability of some service does not establish that Android's configured default is valid. */
internal fun selectRecognitionService(
    configuredComponent: String?,
    ownPackage: String,
    candidates: List<RecognitionServiceCandidate>
): RecognitionServiceCandidate? {
    val valid = candidates.filter {
        it.packageName != ownPackage && it.enabled && it.exported &&
            it.permission == RECOGNITION_BIND_PERMISSION
    }
    return valid.firstOrNull { it.component == configuredComponent }
        ?: valid.sortedWith(compareByDescending<RecognitionServiceCandidate> { it.systemApp }
            .thenBy { it.component }).firstOrNull()
}

private data class RecognitionServices(
    val configured: String?,
    val candidates: List<RecognitionServiceCandidate>,
    val selected: RecognitionServiceCandidate?
)

private fun installedRecognitionServices(context: Context): RecognitionServices {
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
        selectRecognitionService(configured, context.packageName, candidates))
}

private fun onDeviceRecognitionAvailable(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)

/** Selection diagnostics, not a claim that an engine has already transcribed successfully. */
data class RecognitionEngineReport(
    val defaultComponent: String?,
    val visibleServiceCount: Int,
    val usesOnDeviceFallback: Boolean,
    val selectedComponent: String? = null
)

fun recognitionEngineReport(context: Context): RecognitionEngineReport {
    val services = runCatching { installedRecognitionServices(context) }.getOrNull()
    return RecognitionEngineReport(
        defaultComponent = services?.configured,
        visibleServiceCount = services?.candidates?.size ?: 0,
        usesOnDeviceFallback = services?.selected == null && onDeviceRecognitionAvailable(context),
        selectedComponent = services?.selected?.component
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

    override fun createRecognizer(): SpeechRecognizer {
        primaryWasOnDevice = false
        val service = installedRecognitionServices(context).selected
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
