package com.materialchat.assistant.service

/**
 * Whether Android is currently routing assistant invocations to MaterialChat.
 *
 * The platform keeps its own binding to the selected assistant's
 * `VoiceInteractionService`. An app update replaces the package and the system
 * tears that binding down; because the component name is unchanged, the
 * platform's "has the selected component changed?" check does not notice, so
 * the binding can stay dead until the user re-selects an assistant. Until then
 * every assistant gesture does nothing at all, which looks like the assistant
 * vanished.
 *
 * [resolve] is deliberately pure so the decision can be tested without a
 * device: the caller supplies what the system reports.
 */
enum class AssistantBindingStatus {
    /** MaterialChat is not the selected assistant; nothing to repair. */
    NotSelected,

    /** MaterialChat is selected and Android is routing to it. */
    Connected,

    /**
     * MaterialChat is selected but Android is not routing to it. The user has
     * to re-assert the selection for the platform to rebuild its binding.
     */
    NeedsReconnect;

    companion object {
        fun resolve(isSelectedAssistant: Boolean, isServiceActive: Boolean): AssistantBindingStatus =
            when {
                !isSelectedAssistant -> NotSelected
                isServiceActive -> Connected
                else -> NeedsReconnect
            }
    }
}
