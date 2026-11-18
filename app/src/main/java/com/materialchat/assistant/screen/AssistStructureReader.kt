package com.materialchat.assistant.screen

import android.app.assist.AssistStructure
import android.text.InputType
import android.view.View

/**
 * Adapts the platform's `AssistStructure` to [ScreenNode] so [ScreenTextFlattener] can read it.
 * Every accessor is wrapped: the structure is parcelled from another process and a malformed
 * node must cost at most its own subtree, never the capture.
 */
object AssistStructureReader {
    fun flatten(structure: AssistStructure): String = ScreenTextFlattener.flatten(roots(structure))

    fun roots(structure: AssistStructure): List<ScreenNode> {
        val count = runCatching { structure.windowNodeCount }.getOrDefault(0)
        return (0 until count).mapNotNull { index ->
            runCatching { structure.getWindowNodeAt(index).rootViewNode }.getOrNull()?.let(::ViewNodeAdapter)
        }
    }

    /** The foreground activity's package, if the structure carries it. */
    fun sourcePackage(structure: AssistStructure): String? =
        runCatching { structure.activityComponent?.packageName }.getOrNull()

    /** Password fields of any class: their text is never part of the screen context. */
    internal fun isPasswordInput(inputType: Int): Boolean {
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return when (inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_TEXT -> variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }

    private class ViewNodeAdapter(private val node: AssistStructure.ViewNode) : ScreenNode {
        override val visible: Boolean
            get() = runCatching { node.visibility == View.VISIBLE }.getOrDefault(false)
        override val text: CharSequence?
            get() = runCatching { node.text }.getOrNull()
        override val contentDescription: CharSequence?
            get() = runCatching { node.contentDescription }.getOrNull()
        override val sensitive: Boolean
            get() = runCatching { node.isAssistBlocked || isPasswordInput(node.inputType) }.getOrDefault(true)
        override val childCount: Int
            get() = runCatching { node.childCount }.getOrDefault(0)

        override fun childAt(index: Int): ScreenNode = ViewNodeAdapter(node.getChildAt(index))
    }
}
