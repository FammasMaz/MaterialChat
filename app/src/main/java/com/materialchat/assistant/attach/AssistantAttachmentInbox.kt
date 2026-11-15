package com.materialchat.assistant.attach

import com.materialchat.domain.model.Attachment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update

/** Which picker the assistant asks [AssistantAttachActivity] to open. */
enum class AssistantAttachMode { Camera, Gallery }

/**
 * Process-wide hand-off of photos from [AssistantAttachActivity] to the assistant.
 *
 * The assistant overlay lives in a `VoiceInteractionSession`, which cannot receive an activity
 * result, so the picker and camera run in a separate transparent activity. That activity may
 * outlive the session that started it (the system can destroy a hidden session and create a
 * new one for the next invocation), so the delivery is not tied to any session or ViewModel:
 * whatever is published waits here until some [com.materialchat.assistant.ui.AssistantViewModel]
 * drains it. [drain] is atomic, so two ViewModels alive at once cannot both take the same photo.
 *
 * [shared] is the production instance; tests construct their own.
 */
class AssistantAttachmentInbox {
    private val _pending = MutableStateFlow<List<Attachment>>(emptyList())

    /** Photos waiting to be taken. Observers call [drain] when this is non-empty. */
    val pending: StateFlow<List<Attachment>> = _pending.asStateFlow()

    /** Appends delivered photos, in order. Publishing nothing is a no-op. */
    fun publish(attachments: List<Attachment>) {
        if (attachments.isEmpty()) return
        _pending.update { it + attachments }
    }

    /** Takes everything waiting, exactly once: a second call returns an empty list. */
    fun drain(): List<Attachment> = _pending.getAndUpdate { emptyList() }

    companion object {
        val shared = AssistantAttachmentInbox()
    }
}
