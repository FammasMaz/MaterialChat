package com.materialchat.assistant.screen

import android.graphics.Bitmap
import android.net.Uri

/**
 * What the platform told the assistant about the screen it was opened over, for the current
 * invocation only.
 *
 * The two halves arrive separately (`onHandleAssist` brings the view tree and the content,
 * `onHandleScreenshot` the image) and only when the user enabled "Use text from screen" and
 * "Use screenshot" for the digital assistant app; a secure window or the lock screen delivers
 * neither. The session merges the deliveries into one value and drops it when it hides.
 *
 * @property capturedAt When the first half arrived (epoch millis).
 * @property screenshot Already downscaled (see [AssistScreenshots]); the full-size original was
 * released. Never written to disk unless it is attached to a message.
 * @property text The visible text of the screen in reading order, bounded (see
 * [ScreenTextFlattener]); null when no structure arrived.
 * @property webUri The page the foreground app reported through `AssistContent`, if any.
 * @property sourcePackage The foreground activity's package, if the structure named it.
 */
data class ScreenContext(
    val capturedAt: Long,
    val screenshot: Bitmap? = null,
    val text: String? = null,
    val webUri: Uri? = null,
    val sourcePackage: String? = null
) {
    /** Whether anything usable arrived at all. */
    val isEmpty: Boolean
        get() = screenshot == null && text.isNullOrBlank() && webUri == null

    /** The screenshot and the text were both withheld: the system settings are off or the screen is secure. */
    val isBlank: Boolean get() = isEmpty

    /**
     * Releases the screenshot. Serialised on the bitmap itself so a file write in progress
     * ([AssistScreenshots.writeAttachment]) never sees a recycled bitmap.
     */
    fun release() {
        val bitmap = screenshot ?: return
        synchronized(bitmap) { if (!bitmap.isRecycled) bitmap.recycle() }
    }
}
