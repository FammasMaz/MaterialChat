package com.materialchat.assistant.voice

import android.content.ComponentName
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileInputStream
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Uses real Android Intent/Bundle serialization and the production explicit-component binder. */
@RunWith(AndroidJUnit4::class)
class RecognitionContractTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun requestUsesAndroidSupportedExtraTypes() {
        val normal = recognitionIntent(RecognitionOptions(languageTag = "en-US"))
        assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, normal.action)
        assertEquals("en-US", normal.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
        assertEquals(true, normal.getBooleanExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false))
        assertEquals(1, normal.getIntExtra(RecognizerIntent.EXTRA_MAX_RESULTS, -1))
        assertFalse(normal.hasExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH))
        assertFalse(normal.hasExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS))
        val multilingual = recognitionIntent(RecognitionOptions(allowLanguageSwitch = true))
        assertEquals(RecognizerIntent.LANGUAGE_SWITCH_BALANCED,
            multilingual.getStringExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH))
        assertTrue(multilingual.getBooleanExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, false))
    }

    @Test fun productionManagerBindsValidatedExternalServiceAndDeliversFinalTranscript() = runBlocking {
        val context = instrumentation.targetContext
        val component = ComponentName(instrumentation.context.packageName,
            ContractRecognitionService::class.java.name).flattenToString()
        val original = shell("settings get secure voice_recognition_service").trim()
        try {
            shell("settings put secure voice_recognition_service $component")
            // pm grant is unreliable on some emulator images; the UiAutomation binder call is synchronous.
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName, android.Manifest.permission.RECORD_AUDIO
            )
            val report = recognitionEngineReport(context)
            assertEquals("Production resolution must select the installed external fixture", component, report.selectedComponent)
            val manager = SpeechRecognitionManager(context)
            assertTrue(manager.isAvailable())
            val events = withTimeout(20_000) {
                manager.startListening(RecognitionOptions(languageTag = "en-US")).toList()
            }
            assertTrue("Expected ready callback through the Android binder: $events", events.contains(SpeechRecognitionResult.Ready))
            assertEquals(SpeechRecognitionResult.FinalResult("verified Android recognition request"), events.last())
            assertFalse(manager.isListening.value)
        } finally {
            if (original.isEmpty() || original == "null") shell("settings delete secure voice_recognition_service")
            else shell("settings put secure voice_recognition_service $original")
            // Do not revoke a runtime permission in-process: Android kills the target UID.
            // The hosted runner uninstalls this disposable test target after instrumentation.
        }
    }

    private fun shell(command: String): String = instrumentation.uiAutomation
        .executeShellCommand(command).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText() }
        }
}
