/*
 * Region backdrop blur behind the capsule, the mechanism essentials' island uses
 * (island/ui/IslandBackdropBlur.kt): `ViewRootImpl.createBackgroundBlurDrawable()` returns a
 * drawable whose bounds are blurred by SurfaceFlinger from whatever is behind the WINDOW, with
 * its own corner radius, so only the pill's own footprint is blurred, not the whole screen.
 *
 * Finding: both `View.getViewRootImpl()` and `createBackgroundBlurDrawable()` are non-SDK
 * (@hide) members. essentials reaches them only because it ships
 * org.lsposed.hiddenapibypass and calls `HiddenApiBypass.setHiddenApiExemptions("")` at app
 * start (EssentialsApp.kt). MaterialChat has no such exemption, so on API 28+ the reflective
 * lookup is expected to throw and the probe resolves to [Status.ApiUnavailable]; the public
 * alternatives (`WindowManager.LayoutParams.blurBehindRadius` / `Window.setBackgroundBlurRadius`,
 * API 31+) blur behind the ENTIRE session window, which for a full-screen scrim window means the
 * whole app behind the assistant, not the pill's backdrop. This probe therefore attempts the
 * region blur, logs the resolved status once (`NeuralParticles: backdrop=...`), and otherwise
 * draws nothing. The pill's soft edge never depends on it.
 */
package com.materialchat.ui.theme.particles

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt

internal object CapsuleBackdropBlurSupport {
    enum class Status { Available, UnsupportedOs, WindowBlurDisabled, ApiUnavailable }

    val available: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    private val apiPresent: Boolean by lazy {
        try {
            Class.forName("android.view.ViewRootImpl").getMethod("createBackgroundBlurDrawable")
            View::class.java.getMethod("getViewRootImpl")
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun windowBlurEnabled(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            runCatching { context.getSystemService(WindowManager::class.java).isCrossWindowBlurEnabled }
                .getOrDefault(true)

    fun status(sdkInt: Int, apiPresent: Boolean, windowBlurEnabled: Boolean): Status = when {
        sdkInt < Build.VERSION_CODES.S -> Status.UnsupportedOs
        !apiPresent -> Status.ApiUnavailable
        !windowBlurEnabled -> Status.WindowBlurDisabled
        else -> Status.Available
    }

    fun status(context: Context): Status = status(Build.VERSION.SDK_INT, apiPresent, windowBlurEnabled(context))

    private var logged: Status? = null

    fun log(status: Status) {
        if (logged == status) return
        logged = status
        Log.i(NeuralParticleTag, "backdrop=${status.name.lowercase()}")
    }
}

private class CapsuleBackdropBlurView(context: Context) : View(context) {
    private var blur: Drawable? = null
    private var failed = false
    private val setRadius by lazy { blur?.javaClass?.getMethod("setBlurRadius", Int::class.javaPrimitiveType) }
    private val setCorner by lazy { blur?.javaClass?.getMethod("setCornerRadius", Float::class.javaPrimitiveType) }

    init {
        setWillNotDraw(false)
    }

    private fun ensureBlur(): Drawable? {
        if (blur != null || failed) return blur
        blur = try {
            val root = View::class.java.getMethod("getViewRootImpl").invoke(this) ?: return null
            root.javaClass.getMethod("createBackgroundBlurDrawable").invoke(root) as? Drawable
        } catch (_: Throwable) {
            failed = true
            CapsuleBackdropBlurSupport.log(CapsuleBackdropBlurSupport.Status.ApiUnavailable)
            null
        }
        if (blur != null) CapsuleBackdropBlurSupport.log(CapsuleBackdropBlurSupport.Status.Available)
        return blur
    }

    fun update(rect: Rect?, cornerRadius: Float, radiusPx: Float) {
        val d = ensureBlur() ?: return
        val visible = rect != null && rect.width > 0f && rect.height > 0f && radiusPx > 0f
        try {
            setRadius?.invoke(d, if (visible) radiusPx.roundToInt() else 0)
            setCorner?.invoke(d, cornerRadius)
        } catch (_: Throwable) {
        }
        d.alpha = if (visible) 255 else 0
        if (rect != null) {
            d.setBounds(rect.left.roundToInt(), rect.top.roundToInt(), rect.right.roundToInt(), rect.bottom.roundToInt())
        }
        invalidate()
    }

    override fun onDetachedFromWindow() {
        blur?.let {
            try {
                setRadius?.invoke(it, 0)
            } catch (_: Throwable) {
            }
        }
        blur = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        blur?.draw(canvas)
    }
}

/**
 * Blurs what is behind the window within [rect] (in this node's coordinates) with [cornerRadius]
 * corners, when the platform exposes the region blur; draws nothing otherwise. Place it behind
 * the capsule content.
 */
@Composable
internal fun CapsuleBackdropBlur(
    rect: () -> Rect?,
    cornerRadius: Float,
    radiusPx: Float,
    modifier: Modifier = Modifier,
) {
    if (!CapsuleBackdropBlurSupport.available) return
    val holder = remember { arrayOfNulls<CapsuleBackdropBlurView>(1) }
    AndroidView(
        factory = { context ->
            CapsuleBackdropBlurSupport.log(CapsuleBackdropBlurSupport.status(context))
            CapsuleBackdropBlurView(context).also { holder[0] = it }
        },
        modifier = modifier.fillMaxSize().drawBehind { holder[0]?.update(rect(), cornerRadius, radiusPx) }
    )
}
