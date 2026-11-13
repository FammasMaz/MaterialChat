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
}
