package com.materialchat.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The attachment notices are shared copy: the chat composer snackbar and the assistant pill
 * both show them, so the sentence form is pinned here once.
 */
class AttachmentLimitsTest {

    @Test
    fun `limit notice is a sentence that names the limit and the way out`() {
        assertEquals(
            "You can attach up to 4 images per message. Remove one to add another.",
            AttachmentLimits.limitNotice()
        )
    }

    @Test
    fun `skipped notice counts the dropped images in sentence form`() {
        assertEquals(
            "Only the first 4 images were attached; 1 was skipped.",
            AttachmentLimits.skippedNotice(1)
        )
        assertEquals(
            "Only the first 4 images were attached; 3 were skipped.",
            AttachmentLimits.skippedNotice(3)
        )
    }

    @Test
    fun `notices follow the assistant copy rules`() {
        listOf(AttachmentLimits.limitNotice(), AttachmentLimits.skippedNotice(2)).forEach { notice ->
            assertTrue("ends with a period: $notice", notice.endsWith("."))
            assertTrue("sentence case: $notice", notice.first().isUpperCase())
            assertTrue("no ASCII ellipsis: $notice", !notice.contains("..."))
        }
    }
}
