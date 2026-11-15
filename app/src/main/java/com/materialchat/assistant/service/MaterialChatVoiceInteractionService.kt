package com.materialchat.assistant.service

import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.util.Log

/**
 * MaterialChat's VoiceInteractionService implementation.
 *
 * This service allows MaterialChat to be set as the default digital assistant
 * on Android devices, replacing Google Gemini or other assistants.
 *
 * Users can activate MaterialChat via:
 * - Long-pressing the power button (Pixel 6+)
 * - Corner swipe gesture (Pixel 4+)
 * - "Hey Google" (requires Google integration, not implemented)
 *
 * This service is registered in AndroidManifest.xml with the
 * android.permission.BIND_VOICE_INTERACTION permission.
 */
class MaterialChatVoiceInteractionService : VoiceInteractionService() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onReady() {
        super.onReady()
        // Service is ready to handle voice interactions
    }

    override fun onLaunchVoiceAssistFromKeyguard() {
        super.onLaunchVoiceAssistFromKeyguard()
        showSession(null, VoiceInteractionSession.SHOW_SOURCE_ASSIST_GESTURE)
    }

    override fun onShutdown() {
        super.onShutdown()
        // Clean up any resources
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MaterialChatAssistant"

        @Volatile private var instance: MaterialChatVoiceInteractionService? = null

        /**
         * Shows the assistant session again after a hand-off (the photo picker or camera hid
         * it). Returns false when the service is not alive or is no longer the system's
         * assistant, in which case the platform throws and the user re-invokes it by hand.
         */
        fun reopenSession(args: Bundle?): Boolean {
            val service = instance ?: return false
            return runCatching { service.showSession(args, 0) }
                .onFailure { Log.w(TAG, "Could not reopen the assistant session", it) }
                .isSuccess
        }
    }
}
