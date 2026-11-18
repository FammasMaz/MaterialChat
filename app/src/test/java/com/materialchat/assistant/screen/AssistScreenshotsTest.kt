package com.materialchat.assistant.screen

import android.text.InputType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pure parts of the capture: the downscale geometry and the password-field filter. */
class AssistScreenshotsTest {
    @Test fun smallImagesKeepTheirSize() {
        assertEquals(800 to 600, AssistScreenshots.targetSize(800, 600))
        assertEquals(1280 to 1280, AssistScreenshots.targetSize(1280, 1280))
    }

    @Test fun longEdgeIsCappedKeepingTheAspectRatio() {
        assertEquals(1280 to 720, AssistScreenshots.targetSize(2560, 1440))
        assertEquals(576 to 1280, AssistScreenshots.targetSize(1080, 2400))
    }

    @Test fun degenerateSizesStayPositive() {
        assertEquals(1 to 1, AssistScreenshots.targetSize(0, 0))
        assertEquals(1 to 1280, AssistScreenshots.targetSize(1, 100_000))
    }

    @Test fun passwordFieldsOfEveryClassAreSensitive() {
        assertTrue(AssistStructureReader.isPasswordInput(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD))
        assertTrue(AssistStructureReader.isPasswordInput(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD))
        assertTrue(AssistStructureReader.isPasswordInput(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
        assertTrue(AssistStructureReader.isPasswordInput(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD))
        assertFalse(AssistStructureReader.isPasswordInput(InputType.TYPE_CLASS_TEXT))
        assertFalse(AssistStructureReader.isPasswordInput(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS))
        assertFalse(AssistStructureReader.isPasswordInput(InputType.TYPE_CLASS_NUMBER))
        assertFalse(AssistStructureReader.isPasswordInput(InputType.TYPE_NULL))
    }
}
