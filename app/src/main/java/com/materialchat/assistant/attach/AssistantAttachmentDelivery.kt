package com.materialchat.assistant.attach

import android.content.Context
import android.net.Uri
import com.materialchat.domain.model.Attachment
import com.materialchat.util.AttachmentImporter
import com.materialchat.util.AttachmentLimits

/**
 * Imports picked or captured image URIs into app-owned storage and publishes the results to an
 * [AssistantAttachmentInbox]. Factored out of [AssistantAttachActivity] so the publish path can
 * be exercised with plain files, without a camera or the system picker.
 */
object AssistantAttachmentDelivery {
    /** How many images were published, and a short message for the user when not all were. */
    data class Outcome(val delivered: Int, val notice: String?)

    /**
     * Imports at most [room] of [uris] (the assistant already holds the rest of the per-message
     * limit) and publishes the successful imports in one batch. Images beyond [room] are never
     * imported, so nothing is written for them; a failed import is reported, not published.
     */
    suspend fun deliver(
        context: Context,
        uris: List<Uri>,
        room: Int,
        inbox: AssistantAttachmentInbox = AssistantAttachmentInbox.shared
    ): Outcome {
        val accepted = uris.take(room.coerceAtLeast(0))
        val imported = mutableListOf<Attachment>()
        var firstFailure: String? = null
        for (uri in accepted) {
            when (val result = AttachmentImporter.import(context, uri)) {
                is AttachmentImporter.ImportResult.Success -> imported += result.attachment
                is AttachmentImporter.ImportResult.Failure -> if (firstFailure == null) firstFailure = result.reason
            }
        }
        inbox.publish(imported)
        val dropped = uris.size - accepted.size
        val notice = when {
            firstFailure != null -> firstFailure
            dropped > 0 -> AttachmentLimits.skippedNotice(dropped)
            else -> null
        }
        return Outcome(imported.size, notice)
    }
}
