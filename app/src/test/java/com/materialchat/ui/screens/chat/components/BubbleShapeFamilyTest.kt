package com.materialchat.ui.screens.chat.components

import com.materialchat.data.local.preferences.AppPreferences.ChatBubbleStyle
import com.materialchat.ui.screens.chat.MessageGroupPosition
import com.materialchat.ui.theme.ExpressiveShapes
import com.materialchat.ui.theme.MessageBubbleShapes
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * `bubbleShapeFor` is the family function and the theme owns the radii (W7 chat part 4, W8
 * chat part 2): for every style x role x group position the shape returned is the theme's
 * token instance, so the settings preview (which reads the same tokens) and the bubble cannot
 * drift apart.
 */
class BubbleShapeFamilyTest {

    private fun user(style: ChatBubbleStyle, position: MessageGroupPosition = MessageGroupPosition.Single) =
        bubbleShapeFor(style, isUser = true, isAssistant = false, groupPosition = position)

    private fun assistant(style: ChatBubbleStyle, position: MessageGroupPosition = MessageGroupPosition.Single) =
        bubbleShapeFor(style, isUser = false, isAssistant = true, groupPosition = position)

    private fun system(style: ChatBubbleStyle, position: MessageGroupPosition = MessageGroupPosition.Single) =
        bubbleShapeFor(style, isUser = false, isAssistant = false, groupPosition = position)

    @Test
    fun `rounded compact and geometric read the theme bubble tokens for every role and position`() {
        for (position in MessageGroupPosition.values()) {
            assertSame(ExpressiveShapes.Bubble.Rounded, user(ChatBubbleStyle.ROUNDED, position))
            assertSame(ExpressiveShapes.Bubble.Rounded, assistant(ChatBubbleStyle.ROUNDED, position))
            assertSame(MessageBubbleShapes.SystemBubble, system(ChatBubbleStyle.ROUNDED, position))

            assertSame(ExpressiveShapes.Bubble.Compact.User, user(ChatBubbleStyle.COMPACT, position))
            assertSame(ExpressiveShapes.Bubble.Compact.Assistant, assistant(ChatBubbleStyle.COMPACT, position))
            assertSame(ExpressiveShapes.Bubble.Compact.System, system(ChatBubbleStyle.COMPACT, position))

            assertSame(ExpressiveShapes.Bubble.Geometric.User, user(ChatBubbleStyle.GEOMETRIC, position))
            assertSame(ExpressiveShapes.Bubble.Geometric.Assistant, assistant(ChatBubbleStyle.GEOMETRIC, position))
            assertSame(ExpressiveShapes.Bubble.Geometric.System, system(ChatBubbleStyle.GEOMETRIC, position))
        }
    }

    @Test
    fun `expressive reads the grouped message bubble shapes by position`() {
        assertSame(MessageBubbleShapes.UserBubble, user(ChatBubbleStyle.EXPRESSIVE, MessageGroupPosition.Single))
        assertSame(MessageBubbleShapes.Grouped.UserFirst, user(ChatBubbleStyle.EXPRESSIVE, MessageGroupPosition.First))
        assertSame(MessageBubbleShapes.Grouped.UserMiddle, user(ChatBubbleStyle.EXPRESSIVE, MessageGroupPosition.Middle))
        assertSame(MessageBubbleShapes.Grouped.UserLast, user(ChatBubbleStyle.EXPRESSIVE, MessageGroupPosition.Last))
        assertSame(MessageBubbleShapes.AssistantBubble, assistant(ChatBubbleStyle.EXPRESSIVE, MessageGroupPosition.Single))
        assertSame(MessageBubbleShapes.Grouped.AssistantFirst, assistant(ChatBubbleStyle.EXPRESSIVE, MessageGroupPosition.First))
        assertSame(MessageBubbleShapes.Grouped.AssistantMiddle, assistant(ChatBubbleStyle.EXPRESSIVE, MessageGroupPosition.Middle))
        assertSame(MessageBubbleShapes.Grouped.AssistantLast, assistant(ChatBubbleStyle.EXPRESSIVE, MessageGroupPosition.Last))
        for (position in MessageGroupPosition.values()) {
            assertSame(MessageBubbleShapes.SystemBubble, system(ChatBubbleStyle.EXPRESSIVE, position))
        }
    }
}
