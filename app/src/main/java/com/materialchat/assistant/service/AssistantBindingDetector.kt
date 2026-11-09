package com.materialchat.assistant.service

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.service.voice.VoiceInteractionService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads whether Android is actually routing assistant invocations to MaterialChat.
 *
 * See [AssistantBindingStatus] for why this can be false even though MaterialChat
 * is still the selected assistant.
 */
@Singleton
class AssistantBindingDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val serviceComponent = ComponentName(
        context.packageName,
        MaterialChatVoiceInteractionService::class.java.name
    )

    fun currentStatus(): AssistantBindingStatus = AssistantBindingStatus.resolve(
        isSelectedAssistant = isSelectedAssistant(),
        isServiceActive = isServiceActive()
    )

    /**
     * Whether MaterialChat is the assistant Android has on record. Read from the
     * same secure setting the platform consults, so it works on every supported
     * API level rather than only where [android.app.role.RoleManager] exists.
     */
    fun isSelectedAssistant(): Boolean {
        val selected = runCatching {
            Settings.Secure.getString(context.contentResolver, SELECTED_ASSISTANT_SETTING)
        }.getOrNull()
        if (selected.isNullOrEmpty()) return false
        val component = runCatching { ComponentName.unflattenFromString(selected) }.getOrNull()
            ?: return false
        return component.packageName == context.packageName
    }

    /**
     * Whether the platform currently holds a live binding to our assistant service.
     *
     * This is the check that detects the post-update breakage: the setting still
     * names MaterialChat, but the service is not active.
     */
    fun isServiceActive(): Boolean = runCatching {
        VoiceInteractionService.isActiveService(context, serviceComponent)
    }.getOrDefault(false)

    /**
     * Opens the system screen where the user can re-select the assistant. Re-asserting
     * the selection is what makes the platform rebuild its binding.
     */
    fun reconnectIntent(): android.content.Intent {
        val intent = android.content.Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_ASSISTANT)) {
                return roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_ASSISTANT)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        return intent
    }

    private companion object {
        const val SELECTED_ASSISTANT_SETTING = "voice_interaction_service"
    }
}
