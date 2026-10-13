package com.materialchat.ui

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.materialchat.MainActivity
import com.materialchat.data.local.preferences.AppPreferences
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Shipped activity + persisted settings + real accessibility clicks; animations stay enabled. */
@RunWith(AndroidJUnit4::class)
class SeaGlassAppearanceTest {
    @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)
    private val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation
    private lateinit var prefs: AppPreferences
    private lateinit var folder: File

    @Test fun glassWorksWithDynamicColorsAndSurvivesLifecycle() {
        shell("input keyevent 224")
        shell("wm dismiss-keyguard")
        activityRule.scenario.onActivity {
            prefs = it.appPreferences
            folder = File(it.getExternalFilesDir(null), "seaglass-evidence").apply { mkdirs() }
        }
        // Let first-launch seeding finish before overwriting its onboarding preference.
        val readyDeadline = SystemClock.uptimeMillis() + 30_000
        while (find("Welcome to Material Chat") == null && find("Settings") == null && SystemClock.uptimeMillis() < readyDeadline) {
            SystemClock.sleep(200)
        }
        if (find("Welcome to Material Chat") != null) {
            click(waitNode("Continue with Local Ollama"))
            waitNode("Settings")
        }
        runBlocking {
            prefs.setFirstLaunchComplete()
            prefs.setOnboardingComplete()
            prefs.setThemeMode(AppPreferences.ThemeMode.LIGHT)
            prefs.setDynamicColorEnabled(true)
            prefs.setThemePalette(AppPreferences.ThemePalette.VIOLET)
        }
        click(waitNode("Settings"))
        waitNode("AI Providers")
        // Scroll the real settings list until the appearance controls are on screen.
        repeat(32) {
            if (find("Material palette & appearance") == null) shell("input swipe 360 1150 360 900 250")
        }
        waitNode("Material palette & appearance")
        // Scroll the real palette carousel horizontally, then click SeaGlass with dynamic on.
        repeat(5) {
            if (find("SeaGlass") == null) {
                val label = waitNode("Material palette & appearance")
                val bounds = android.graphics.Rect().also { label.getBoundsInScreen(it) }
                val card = label.parent
                val carousel = (0 until (card?.childCount ?: 0)).mapNotNull { card?.getChild(it) }
                    .firstOrNull { it.isScrollable }
                val carouselBounds = android.graphics.Rect().also { carousel?.getBoundsInScreen(it) }
                val y = if (!carouselBounds.isEmpty) carouselBounds.centerY() else bounds.bottom + 90
                shell("input swipe 650 $y 80 $y 400")
            }
        }
        val plain = capture("settings-dynamic-material")
        click(waitNode("SeaGlass"))
        SystemClock.sleep(1500) // bounded settling time; does not require continuous hero animations to stop
        waitNode("Material palette & appearance") // Switching appearance must not reset navigation.
        val glass = capture("settings-dynamic-seaglass")
        assertVisibleDifference(plain, glass)
        assertTrue(runBlocking { prefs.themePalette.first() } == AppPreferences.ThemePalette.SEA_GLASS)
        assertTrue(runBlocking { prefs.dynamicColorEnabled.first() })
        activityRule.scenario.recreate()
        waitNode("Settings")
        capture("seaglass-after-recreate")
        runBlocking { prefs.setThemeMode(AppPreferences.ThemeMode.DARK) }
        SystemClock.sleep(1000)
        capture("seaglass-dark")
        activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        waitNode("Settings")
        capture("seaglass-resumed")
        runBlocking {
            prefs.setThemeMode(AppPreferences.ThemeMode.LIGHT)
            prefs.setThemePalette(AppPreferences.ThemePalette.VIOLET)
        }
        SystemClock.sleep(1000)
        capture("material-restored")
    }

    private fun find(label: String): AccessibilityNodeInfo? {
        fun visit(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.isVisibleToUser && (node.text?.toString() == label || node.contentDescription?.toString() == label)) return node
            for (i in 0 until node.childCount) visit(node.getChild(i))?.let { return it }
            return null
        }
        return visit(automation.rootInActiveWindow)
    }

    private fun waitNode(label: String): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 30_000
        while (SystemClock.uptimeMillis() < deadline) {
            find(label)?.let { return it }
            SystemClock.sleep(200)
        }
        capture("failure-${label.replace(' ', '-')}")
        error("Missing visible app control: $label")
    }

    private fun click(node: AccessibilityNodeInfo) {
        var target: AccessibilityNodeInfo? = node
        while (target != null && !target.isClickable) target = target.parent
        assertTrue("Control must be clickable", target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
        SystemClock.sleep(500)
    }

    private fun shell(command: String) {
        automation.executeShellCommand(command).use { descriptor ->
            java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
        }
        SystemClock.sleep(500)
    }

    private fun capture(name: String): Bitmap {
        val bitmap = requireNotNull(automation.takeScreenshot())
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }

    private fun assertVisibleDifference(before: Bitmap, after: Bitmap) {
        val width = minOf(before.width, after.width)
        val height = minOf(before.height, after.height)
        var changed = 0
        var total = 0
        for (y in 0 until height step 4) for (x in 0 until width step 4) {
            val a = before.getPixel(x, y)
            val b = after.getPixel(x, y)
            val delta = kotlin.math.abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)) +
                kotlin.math.abs(android.graphics.Color.green(a) - android.graphics.Color.green(b)) +
                kotlin.math.abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b))
            if (delta >= 18) changed++
            total++
        }
        assertTrue("Glass must visibly change >=5% of samples with identical dynamic colors: $changed/$total",
            changed.toDouble() / total >= 0.05)
    }
}
