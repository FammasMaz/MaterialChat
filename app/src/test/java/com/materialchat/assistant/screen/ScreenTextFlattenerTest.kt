package com.materialchat.assistant.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The view-tree flattener on a fake tree (the platform's AssistStructure cannot be built in a JVM test). */
class ScreenTextFlattenerTest {
    private class FakeNode(
        override val text: CharSequence? = null,
        override val contentDescription: CharSequence? = null,
        override val visible: Boolean = true,
        override val sensitive: Boolean = false,
        private val children: List<ScreenNode> = emptyList()
    ) : ScreenNode {
        override val childCount: Int get() = children.size
        override fun childAt(index: Int): ScreenNode = children[index]
    }

    private fun node(
        text: String? = null,
        description: String? = null,
        visible: Boolean = true,
        sensitive: Boolean = false,
        vararg children: ScreenNode
    ): ScreenNode = FakeNode(text, description, visible, sensitive, children.toList())

    @Test fun readsVisibleTextInDepthFirstLayoutOrder() {
        val tree = node(
            null, null, true, false,
            node("Header"),
            node(null, null, true, false, node("First tweet"), node("12 likes")),
            node("Footer")
        )
        assertEquals("Header\nFirst tweet\n12 likes\nFooter", ScreenTextFlattener.flatten(listOf(tree)))
    }

    @Test fun contentDescriptionIsReadOnceAndNotWhenItRepeatsTheText() {
        val tree = node(
            null, null, true, false,
            node("Like", "Like"),
            node(null, "Profile picture"),
            node("Reply", "Reply to this post")
        )
        assertEquals("Like\nProfile picture\nReply\nReply to this post", ScreenTextFlattener.flatten(listOf(tree)))
    }

    @Test fun invisibleAndSensitiveSubtreesAreSkippedWhole() {
        val tree = node(
            null, null, true, false,
            node("Shown"),
            node("Hidden", null, false, false, node("Hidden child")),
            node("Password", null, true, true, node("hunter2"))
        )
        assertEquals("Shown", ScreenTextFlattener.flatten(listOf(tree)))
    }

    @Test fun duplicatesAndWhitespaceAreCollapsed() {
        val tree = node(null, null, true, false, node("  a   b \n c "), node("a b c"), node("   "))
        assertEquals("a b c", ScreenTextFlattener.flatten(listOf(tree)))
    }

    @Test fun windowsAreReadInDeliveryOrder() {
        assertEquals("one\ntwo", ScreenTextFlattener.flatten(listOf(node("one"), node("two"))))
    }

    @Test fun outputIsCappedWithAMarkerAndNeverExceedsTheLimit() {
        val lines = (1..500).map { node("line number $it with some padding text") }
        val tree = node(null, null, true, false, *lines.toTypedArray())
        val small = ScreenTextFlattener.flatten(listOf(tree), maxChars = 200)
        assertTrue(small, small.length <= 200)
        assertTrue(small, small.endsWith("\n" + ScreenTextFlattener.TRUNCATION_MARKER))
        assertTrue(small, small.startsWith("line number 1 with"))
        val full = ScreenTextFlattener.flatten(listOf(tree))
        assertTrue(full.length <= ScreenTextFlattener.MAX_CHARS)
        assertTrue(full.endsWith(ScreenTextFlattener.TRUNCATION_MARKER))
    }

    @Test fun shortContentIsNotMarkedTruncated() {
        val out = ScreenTextFlattener.flatten(listOf(node("hello")))
        assertEquals("hello", out)
        assertFalse(out.contains(ScreenTextFlattener.TRUNCATION_MARKER))
    }

    @Test fun aHugeTreeStopsAtTheNodeCap() {
        val children = (1..ScreenTextFlattener.MAX_NODES + 10).map { node("n$it") }
        val out = ScreenTextFlattener.flatten(
            listOf(node(null, null, true, false, *children.toTypedArray())),
            maxChars = 1_000_000
        )
        assertTrue(out.endsWith(ScreenTextFlattener.TRUNCATION_MARKER))
        assertTrue(out.contains("\nn10\n"))
        assertFalse(out.contains("n${ScreenTextFlattener.MAX_NODES + 5}"))
    }

    @Test fun emptyTreesGiveEmptyText() {
        assertEquals("", ScreenTextFlattener.flatten(emptyList()))
        assertEquals("", ScreenTextFlattener.flatten(listOf(node(null, null, false, false, node("x")))))
    }
}
