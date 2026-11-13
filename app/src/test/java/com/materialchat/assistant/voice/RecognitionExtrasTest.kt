package com.materialchat.assistant.voice

import android.speech.RecognizerIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The recognition request is what separates a usable transcript from a poor one: without an
 * explicit language the service falls back to its own default, and without formatting and silence
 * thresholds the keyboard-grade engine still returns unpunctuated, truncated text.
 */
class RecognitionExtrasTest {
    @Test fun `language is always sent explicitly`() {
        val extras = recognitionExtras(RecognitionOptions(languageTag = "en-US"), deviceLanguageTag = "fr-FR")
        assertEquals("en-US", extras[RecognizerIntent.EXTRA_LANGUAGE])
    }

    @Test fun `blank language follows the device language`() {
        val extras = recognitionExtras(RecognitionOptions(languageTag = "   "), deviceLanguageTag = "de-DE")
        assertEquals("de-DE", extras[RecognizerIntent.EXTRA_LANGUAGE])
    }

    @Test fun `null language follows the device language`() {
        val extras = recognitionExtras(RecognitionOptions(), deviceLanguageTag = "pt-BR")
        assertEquals("pt-BR", extras[RecognizerIntent.EXTRA_LANGUAGE])
    }

    @Test fun `formatting and partial results are requested`() {
        val extras = recognitionExtras(RecognitionOptions(), deviceLanguageTag = "en-US")
        assertEquals(RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY, extras[RecognizerIntent.EXTRA_ENABLE_FORMATTING])
        assertEquals(true, extras[RecognizerIntent.EXTRA_PARTIAL_RESULTS])
        assertEquals(1, extras[RecognizerIntent.EXTRA_MAX_RESULTS])
        assertEquals(false, extras[RecognizerIntent.EXTRA_PREFER_OFFLINE])
    }

    @Test fun `silence thresholds do not cut a sentence short`() {
        val extras = recognitionExtras(RecognitionOptions(), deviceLanguageTag = "en-US")
        val complete = extras[RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS] as Int
        val possiblyComplete = extras[RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS] as Int
        assertTrue("complete silence threshold must be long enough to survive a pause", complete >= 1_500)
        assertTrue(possiblyComplete < complete)
    }

    @Test fun `language switching is requested only while the language is not pinned`() {
        val automatic = recognitionExtras(RecognitionOptions(), deviceLanguageTag = "en-US")
        assertEquals(true, automatic[RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION])
        assertEquals(true, automatic[RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH])

        val pinned = recognitionExtras(RecognitionOptions(languageTag = "en-US"), deviceLanguageTag = "en-US")
        assertNull(pinned[RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION])
        assertNull(pinned[RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH])
    }

    @Test fun `switching can be disabled explicitly`() {
        val extras = recognitionExtras(
            RecognitionOptions(allowLanguageSwitch = false),
            deviceLanguageTag = "en-US"
        )
        assertNull(extras[RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH])
        assertFalse(extras.containsKey(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION))
    }
}
