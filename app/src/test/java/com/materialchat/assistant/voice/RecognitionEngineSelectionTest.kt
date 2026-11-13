package com.materialchat.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecognitionEngineSelectionTest {
    private val google = candidate("com.google.speech", system = true)
    private val other = candidate("com.example.speech")
    private fun candidate(pkg: String, system: Boolean = false) = RecognitionServiceCandidate(
        "$pkg/$pkg.RecognitionService", pkg, true, true, null, system
    )
    private fun select(default: String?, vararg candidates: RecognitionServiceCandidate) =
        selectRecognitionService(default, "com.materialchat", candidates.toList())

    @Test fun validatedConfiguredServiceIsPreferred() {
        assertEquals(other, select(other.component, google, other))
    }
    @Test fun missingDefaultDoesNotPreventSelectingInstalledEngine() {
        assertEquals(google, select("com.materialchat/android.speech.RecognitionService", other, google))
    }
    @Test fun nullDefaultUsesStableSystemFirstSelection() {
        assertEquals(google, select(null, other, google))
        assertEquals(google, select(null, google, other))
    }
    @Test fun unsafeDefaultIsNotUsed() {
        listOf(other.copy(enabled = false), other.copy(exported = false))
            .forEach { unsafe -> assertEquals(google, select(unsafe.component, unsafe, google)) }
    }
    @Test fun bindPermissionGuardDoesNotDisqualifyService() {
        // No platform bind permission exists for recognition services; an engine's own guard
        // is enforced at bind time and must not reject it during selection.
        val guarded = other.copy(permission = "com.example.vendor.BIND_MY_ENGINE")
        assertEquals(guarded, select(guarded.component, guarded, google))
    }
    @Test fun ownPackageIsNeverSelectedAsExternalRecognizer() {
        val own = candidate("com.materialchat", system = true)
        assertEquals(other, select(own.component, own, other))
    }
    @Test fun noValidServiceReturnsNoExternalEngine() {
        assertNull(select(null))
        assertNull(select(other.component, other.copy(enabled = false)))
    }

    private val googleApp = RecognitionServiceCandidate(
        "com.google.android.googlequicksearchbox/com.google.android.voicesearch.serviceapi.GoogleRecognitionService",
        "com.google.android.googlequicksearchbox", true, true, null, true
    )
    private val speechServices = RecognitionServiceCandidate(
        "com.google.android.tts/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService",
        "com.google.android.tts", true, true, null, true
    )
    private val systemIntelligence = RecognitionServiceCandidate(
        "com.google.android.as/com.google.android.apps.miphone.aiai.speech.AiAiRecognitionService",
        "com.google.android.as", true, true, null, true
    )
    private val ownDeclaredDefault = "com.materialchat/android.speech.RecognitionService"

    @Test fun whileMaterialChatIsAssistantTheGoogleAppEngineWinsNotAlphabeticalOnDevice() {
        // Regression: the configured default points into our own package while we are the
        // assistant, and alphabetical order picked Android System Intelligence's on-device model.
        assertEquals(googleApp, select(ownDeclaredDefault, systemIntelligence, speechServices, googleApp))
    }
    @Test fun withoutGoogleAppSpeechServicesBeatsOnDeviceSystemIntelligence() {
        assertEquals(speechServices, select(ownDeclaredDefault, systemIntelligence, speechServices))
    }
    @Test fun googleAppBeatsAThirdPartyConfiguredDefault() {
        assertEquals(googleApp, select(other.component, other, googleApp))
    }
    @Test fun userChosenEngineOverridesRanking() {
        assertEquals(speechServices, selectRecognitionService(
            ownDeclaredDefault, "com.materialchat", listOf(googleApp, speechServices), speechServices.component))
    }
    @Test fun unusableUserChoiceFallsBackToBestEngine() {
        assertEquals(googleApp, selectRecognitionService(
            ownDeclaredDefault, "com.materialchat",
            listOf(googleApp, speechServices.copy(enabled = false)), speechServices.component))
    }
    @Test fun rankingListsBestFirstAndExcludesUnusable() {
        assertEquals(
            listOf(googleApp, speechServices, systemIntelligence).map { it.component },
            rankRecognitionServices(ownDeclaredDefault, "com.materialchat",
                listOf(systemIntelligence, speechServices, googleApp, other.copy(exported = false)))
                .map { it.component }
        )
    }
}
