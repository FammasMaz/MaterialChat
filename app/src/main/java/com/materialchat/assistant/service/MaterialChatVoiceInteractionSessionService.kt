package com.materialchat.assistant.service

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Session service that creates VoiceInteractionSession instances.
 *
 * This is the factory for MaterialChatVoiceInteractionSession.
 * Each assistant invocation creates a new session.
 */
@AndroidEntryPoint
class MaterialChatVoiceInteractionSessionService : VoiceInteractionSessionService() {

    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return MaterialChatVoiceInteractionSession(this)
    }
}
