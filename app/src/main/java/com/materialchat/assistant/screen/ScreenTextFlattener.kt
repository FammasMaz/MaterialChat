package com.materialchat.assistant.screen

/**
 * One node of the screen's view tree, as much of `AssistStructure.ViewNode` as the flattener
 * needs. `AssistStructure` cannot be constructed outside the framework, so the flattener works
 * on this interface and the unit tests feed it a fake tree ([AssistStructureReader] adapts the
 * real one).
 */
interface ScreenNode {
    /** `View.VISIBLE`; an invisible node hides its whole subtree. */
    val visible: Boolean
    val text: CharSequence?
    val contentDescription: CharSequence?
    /** A password field or a subtree the app blocked from assist: never read, never descended. */
    val sensitive: Boolean
    val childCount: Int
    fun childAt(index: Int): ScreenNode
}

/**
 * Turns the view tree into the compact text block the model receives: visible nodes' text and
 * content descriptions in reading order (depth-first, children in layout order, windows in
 * delivery order), one line each, duplicates dropped, whitespace collapsed, and the whole thing
 * capped so a long list or a chat log cannot flood the prompt.
 */
object ScreenTextFlattener {
    /** The cap on the flattened text, in characters, including the truncation marker. */
    const val MAX_CHARS = 6000

    /** A tree deeper or wider than this is a web page's DOM mirror, not something to read whole. */
    const val MAX_NODES = 5000

    const val TRUNCATION_MARKER = "\u2026"

    private val WHITESPACE = Regex("\\s+")

    fun flatten(roots: List<ScreenNode>, maxChars: Int = MAX_CHARS): String {
        require(maxChars > TRUNCATION_MARKER.length + 1) { "maxChars too small" }
        val out = StringBuilder()
        val seen = HashSet<String>()
        var truncated = false
        var visited = 0

        fun add(raw: CharSequence?): Boolean {
            val line = raw?.toString()?.replace(WHITESPACE, " ")?.trim().orEmpty()
            if (line.isEmpty() || !seen.add(line)) return true
            val needed = line.length + if (out.isEmpty()) 0 else 1
            if (out.length + needed > maxChars) {
                truncated = true
                return false
            }
            if (out.isNotEmpty()) out.append('\n')
            out.append(line)
            return true
        }

        fun walk(node: ScreenNode): Boolean {
            if (++visited > MAX_NODES) {
                truncated = true
                return false
            }
            if (!node.visible || node.sensitive) return true
            if (!add(node.text)) return false
            val description = node.contentDescription
            if (description != null && description.toString() != node.text?.toString() && !add(description)) return false
            for (index in 0 until node.childCount) {
                if (!walk(node.childAt(index))) return false
            }
            return true
        }

        for (root in roots) if (!walk(root)) break
        if (truncated && out.isNotEmpty()) {
            val room = maxChars - TRUNCATION_MARKER.length - 1
            if (out.length > room) out.setLength(room)
            out.append('\n').append(TRUNCATION_MARKER)
        }
        return out.toString()
    }
}
