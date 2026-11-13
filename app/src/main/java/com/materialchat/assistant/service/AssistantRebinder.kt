package com.materialchat.assistant.service

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay

/** Outcome of an attempt to repair the platform's assistant binding. */
enum class RebindResult {
    /** MaterialChat is not the selected assistant; nothing to repair. */
    NotSelected,

    /** Android was already routing to us. */
    AlreadyConnected,

    /** The binding was rebuilt. */
    Reconnected,

    /** The toggle did not restore the binding; the user has to re-select the assistant. */
    StillDisconnected
}

/**
 * Repairs the assistant binding that Android tears down when the app is updated.
 *
 * The platform keeps its own binding to the selected assistant's
 * [MaterialChatVoiceInteractionService]. An update replaces the package but leaves the
 * component name identical, so the platform's "has the selected component changed?" check
 * reports no change and the dead binding survives. Every assistant gesture then does
 * nothing until the user re-selects an assistant, which is exactly the "switch to Google
 * and back" workaround.
 *
 * Re-asserting the component's enabled state changes what the platform has on record for
 * that component, so it re-reads the service and binds again - without any user action.
 */
@Singleton
class AssistantRebinder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val detector: AssistantBindingDetector
) {

    suspend fun rebindIfNeeded(settleMillis: Long = SETTLE_MILLIS): RebindResult {
        if (!shouldRebind(detector.isSelectedAssistant(), detector.isServiceActive())) {
            return if (detector.isSelectedAssistant()) {
                RebindResult.AlreadyConnected
            } else {
                RebindResult.NotSelected
            }
        }
        if (!reassertServiceComponent()) return RebindResult.StillDisconnected
        // The platform rebinds asynchronously after the package change.
        delay(settleMillis)
        return if (detector.isServiceActive()) {
            RebindResult.Reconnected
        } else {
            RebindResult.StillDisconnected
        }
    }

    private fun reassertServiceComponent(): Boolean = runCatching {
        val manager = context.packageManager
        val component = ComponentName(context, MaterialChatVoiceInteractionService::class.java)
        manager.setComponentEnabledSetting(
            component,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
        manager.setComponentEnabledSetting(
            component,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
        true
    }.getOrDefault(false)

    companion object {
        /** Time the platform gets to rebuild its binding after the component change. */
        const val SETTLE_MILLIS = 1_500L

        /** Pure decision so the repair policy is testable without a device. */
        fun shouldRebind(isSelectedAssistant: Boolean, isServiceActive: Boolean): Boolean =
            isSelectedAssistant && !isServiceActive
    }
}
