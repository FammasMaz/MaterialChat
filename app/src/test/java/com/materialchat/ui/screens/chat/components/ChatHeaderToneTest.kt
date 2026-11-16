package com.materialchat.ui.screens.chat.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The chat header keeps ONE container tone across rest, scrolled-under and folded states so
 * the list scrolling never shifts the header colour or reveals its bottom corners.
 */
class ChatHeaderToneTest {
    private val states = listOf(false to false, true to false, false to true, true to true)

    @Test
    fun standardToneDoesNotChangeWithScrollOrFold() {
        states.forEach { (scrolledUnder, collapsed) ->
            assertEquals(
                "scrolledUnder=$scrolledUnder collapsed=$collapsed",
                ChatHeaderTone.Standard,
                resolveChatHeaderTone(
                    isEphemeral = false,
                    contentScrolledUnder = scrolledUnder,
                    isCollapsed = collapsed
                )
            )
        }
    }

    @Test
    fun ephemeralToneDoesNotChangeWithScrollOrFold() {
        states.forEach { (scrolledUnder, collapsed) ->
            assertEquals(
                "scrolledUnder=$scrolledUnder collapsed=$collapsed",
                ChatHeaderTone.Ephemeral,
                resolveChatHeaderTone(
                    isEphemeral = true,
                    contentScrolledUnder = scrolledUnder,
                    isCollapsed = collapsed
                )
            )
        }
    }
}
