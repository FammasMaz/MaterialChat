package com.materialchat.assistant.voice

import android.speech.RecognizerIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** Checks the public recognizer request contract, without assuming keyboard feature parity. */
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

    @Test fun `installed recognizer owns silence timing`() {
        val extras = recognitionExtras(RecognitionOptions(), deviceLanguageTag = "en-US")
        assertNull(extras[RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS])
        assertNull(extras[RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS])
        assertNull(extras[RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS])
    }

    @Test fun `optional language switching uses the documented sensitivity string`() {
        val automatic = recognitionExtras(RecognitionOptions(allowLanguageSwitch = true), deviceLanguageTag = "en-US")
        assertEquals(true, automatic[RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION])
        assertEquals(RecognizerIntent.LANGUAGE_SWITCH_BALANCED, automatic[RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH])

        val pinned = recognitionExtras(RecognitionOptions(languageTag = "en-US", allowLanguageSwitch = true), deviceLanguageTag = "en-US")
        assertNull(pinned[RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION])
        assertNull(pinned[RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH])
    }

    @Test fun `switching is not required for normal speech input`() {
        val extras = recognitionExtras(RecognitionOptions(), deviceLanguageTag = "en-US")
        assertNull(extras[RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH])
        assertFalse(extras.containsKey(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION))
    }
}
