package com.materialchat.assistant.service

import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises real session content and callbacks, not the privileged system show/token path. */
@RunWith(AndroidJUnit4::class)
class MaterialChatVoiceInteractionSessionTest {
    @Test fun contentCreationAndRepeatedShowHidePreserveOwnersAndImePolicy() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val session = MaterialChatVoiceInteractionSession(instrumentation.targetContext)
            session.onCreate()
            assertEquals(Lifecycle.State.CREATED, session.lifecycle.currentState)
            try {
                val content = session.onCreateContentView()
                assertTrue(content is ComposeView)
                assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, content.layoutParams.height)
                assertSame(session, content.findViewTreeLifecycleOwner())
                assertSame(session, content.findViewTreeSavedStateRegistryOwner())
                assertEquals(Lifecycle.State.CREATED, session.lifecycle.currentState)
                val window = requireNotNull(session.window.window)
                assertEquals(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
                    window.attributes.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST
                )
                // Keep the framework's full-screen session layout; Activity edge-to-edge
                // helpers must not take over this window during content creation.
                assertTrue(window.attributes.flags and WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN != 0)
                repeat(3) {
                    session.onShow(null, 0)
                    assertEquals(Lifecycle.State.RESUMED, session.lifecycle.currentState)
                    session.onShow(null, 0) // Android may show an already-visible session.
                    assertEquals(Lifecycle.State.RESUMED, session.lifecycle.currentState)
                    session.onHide()
                    assertEquals(Lifecycle.State.CREATED, session.lifecycle.currentState)
                }
                assertSame(session, content.findViewTreeLifecycleOwner())
            } finally {
                session.onDestroy()
            }
            assertEquals(Lifecycle.State.DESTROYED, session.lifecycle.currentState)
        }
    }
}
