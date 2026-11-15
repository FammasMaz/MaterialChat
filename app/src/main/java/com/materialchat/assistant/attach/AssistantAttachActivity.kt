package com.materialchat.assistant.attach

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.materialchat.assistant.service.MaterialChatVoiceInteractionService
import com.materialchat.util.AttachmentLimits
import com.materialchat.util.CameraCapture
import kotlinx.coroutines.launch
import java.io.File

/**
 * Invisible activity that runs the photo picker or the camera on the assistant's behalf.
 *
 * The assistant overlay is a `VoiceInteractionSession` and cannot receive an activity result,
 * so the session starts this activity instead. It launches the matching contract, imports what
 * came back into app-owned storage off the main thread, publishes the attachments to
 * [AssistantAttachmentInbox], asks the voice-interaction service to show the assistant again,
 * and finishes. A cancelled picker or camera publishes nothing and leaves no file behind.
 *
 * It is deliberately not `noHistory`: Android finishes a no-history activity as soon as the
 * camera covers it, which would drop the result. It is excluded from recents and has no task
 * affinity, so it never pulls the main app's task to the front.
 */
class AssistantAttachActivity : ComponentActivity() {
    private var captureFile: File? = null
    private var room = AttachmentLimits.MAX_PER_MESSAGE

    private val pickOne = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        deliver(listOfNotNull(uri))
    }

    private val pickMany = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(AttachmentLimits.MAX_PER_MESSAGE)
    ) { uris -> deliver(uris) }

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        val file = captureFile
        captureFile = null
        if (!captured || file == null) {
            CameraCapture.discard(file)
            close()
            return@registerForActivityResult
        }
        lifecycleScope.launch {
            // The importer writes its own downscaled copy; the raw capture is not needed after it.
            val outcome = AssistantAttachmentDelivery.deliver(applicationContext, listOf(Uri.fromFile(file)), room)
            CameraCapture.discard(file)
            close(outcome.notice)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        room = intent.getIntExtra(EXTRA_ROOM, AttachmentLimits.MAX_PER_MESSAGE)
            .coerceIn(0, AttachmentLimits.MAX_PER_MESSAGE)
        savedInstanceState?.getString(STATE_CAPTURE_FILE)?.let { captureFile = File(it) }
        // After a recreation the contract already launched is still in flight and reports to
        // the callbacks re-registered above; launching again would open a second picker.
        if (savedInstanceState != null) return
        if (room <= 0) {
            close(AttachmentLimits.limitNotice())
            return
        }
        when (intent.getStringExtra(EXTRA_MODE)) {
            MODE_CAMERA -> {
                val capture = CameraCapture.prepare(this)
                if (capture == null) {
                    close("No camera app available")
                } else {
                    captureFile = capture.file
                    takePicture.launch(capture.uri)
                }
            }
            MODE_GALLERY -> {
                val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                if (room == 1) pickOne.launch(request) else pickMany.launch(request)
            }
            else -> close()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        captureFile?.let { outState.putString(STATE_CAPTURE_FILE, it.path) }
    }

    private fun deliver(uris: List<Uri>) {
        if (uris.isEmpty()) {
            close()
            return
        }
        lifecycleScope.launch {
            val outcome = AssistantAttachmentDelivery.deliver(applicationContext, uris, room)
            close(outcome.notice)
        }
    }

    /**
     * Brings the assistant back (the session hid when this activity came up) and goes away.
     * The resume flag tells the session this is the same turn, so it does not start a new chat.
     */
    private fun close(notice: String? = null) {
        if (notice != null) Toast.makeText(this, notice, Toast.LENGTH_SHORT).show()
        val reopened = MaterialChatVoiceInteractionService.reopenSession(
            Bundle().apply { putBoolean(EXTRA_RESUME_AFTER_ATTACH, true) }
        )
        if (!reopened) Log.i(TAG, "assistant service unavailable; attachments wait in the inbox")
        finish()
    }

    companion object {
        private const val TAG = "MaterialChatAssistant"
        const val EXTRA_MODE = "com.materialchat.assistant.attach.MODE"
        const val EXTRA_ROOM = "com.materialchat.assistant.attach.ROOM"
        /** Show-session argument: the assistant is resuming the turn it left to add a photo. */
        const val EXTRA_RESUME_AFTER_ATTACH = "com.materialchat.assistant.attach.RESUME"
        const val MODE_CAMERA = "camera"
        const val MODE_GALLERY = "gallery"
        private const val STATE_CAPTURE_FILE = "capture_file"

        /** The intent the session starts; [room] is how many more images the message can take. */
        fun intent(context: Context, mode: AssistantAttachMode, room: Int): Intent =
            Intent(context, AssistantAttachActivity::class.java).apply {
                putExtra(EXTRA_MODE, if (mode == AssistantAttachMode.Camera) MODE_CAMERA else MODE_GALLERY)
                putExtra(EXTRA_ROOM, room)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
    }
}
