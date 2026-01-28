package com.materialchat.ui.screens.conversations

/**
 * UI representation of a conversation group in the conversations list.
 *
 * Groups a parent conversation with its branches for expandable display.
 * Root conversations without branches are still wrapped in this structure
 * for consistent rendering.
 *
 * @property parent The parent conversation (root of the group)
 * @property branches List of branch conversations under this parent
 * @property isExpanded Whether the branch list is currently expanded
 */
data class ConversationGroupUiItem(
    val parent: ConversationUiItem,
    val branches: List<ConversationUiItem>,
    val isExpanded: Boolean = false
) {
    /**
     * Whether this group has any branches.
     */
    val hasBranches: Boolean get() = branches.isNotEmpty()

    /**
     * The number of branches in this group.
     */
    val branchCount: Int get() = branches.size
}
