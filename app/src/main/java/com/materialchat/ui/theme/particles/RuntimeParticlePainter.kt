/*
 * Ported from sameerasw/essentials (MIT) — island/plugins/media/particles/RuntimeParticlePainter.kt
 * API 33+ draws every particle in one `drawVertices` call through an AGSL runtime shader;
 * older releases fall back to one batched `Path` per opacity bucket.
 */
package com.materialchat.ui.theme.particles

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

enum class NeuralParticlePainterKind { Runtime, Static }

internal interface ParticlePainter {
    val kind: NeuralParticlePainterKind
    val animated: Boolean
    fun DrawScope.paint(
        geometry: CapsuleParticleGeometry,
        time: Float,
        alpha: Float,
        color: Color,
        style: ParticleFieldPixels,
        screenOrigin: Offset,
        tile: PoissonTile
    )
}

internal class StaticParticlePainter : ParticlePainter {
    override val kind = NeuralParticlePainterKind.Static
    override val animated = false
    private var key: Triple<CapsuleParticleGeometry, ParticleFieldPixels, Offset>? = null
    private val paths = Array(256) { Path() }
    private var lastTile: PoissonTile? = null

    override fun DrawScope.paint(
        geometry: CapsuleParticleGeometry,
        time: Float,
        alpha: Float,
        color: Color,
        style: ParticleFieldPixels,
        screenOrigin: Offset,
        tile: PoissonTile
    ) {
        val next = Triple(geometry, style, screenOrigin)
        if (key != next || lastTile !== tile) {
            paths.forEach { it.rewind() }
            visitParticles(tile, geometry, style, screenOrigin, 0f) { x, y, radius, opacity ->
                val path = paths[(opacity * 255f).roundToInt().coerceIn(0, 255)]
                if (style.shape == ParticleShape.Circle) {
                    path.addOval(Rect(x - radius, y - radius, x + radius, y + radius))
                } else {
                    val extent = radius * 1.25f
                    path.moveTo(x, y - extent)
                    path.lineTo(x + extent, y)
                    path.lineTo(x, y + extent)
                    path.lineTo(x - extent, y)
                    path.close()
                }
            }
            key = next
            lastTile = tile
        }
        for (i in 1..255) if (!paths[i].isEmpty) {
            drawPath(paths[i], color.copy(alpha = color.alpha * alpha * i / 255f))
        }
    }
}

@RequiresApi(33)
internal fun createRuntimeParticlePainter(): ParticlePainter? = try {
    RuntimeParticlePainter(RuntimeShader(NEURAL_PARTICLE_AGSL))
} catch (e: IllegalArgumentException) {
    Log.w(NeuralParticleTag, "AGSL compilation failed; using static particles", e)
    null
}

@RequiresApi(33)
private class RuntimeParticlePainter(private val shader: RuntimeShader) : ParticlePainter {
    override val kind = NeuralParticlePainterKind.Runtime
    private val paint = Paint().apply { this.shader = this@RuntimeParticlePainter.shader }
    private var vertices = FloatArray(8192)
    private var coordinates = FloatArray(8192)
    private var colors = IntArray(4096)
    override val animated = true

    override fun DrawScope.paint(
        geometry: CapsuleParticleGeometry,
        time: Float,
        alpha: Float,
        color: Color,
        style: ParticleFieldPixels,
        screenOrigin: Offset,
        tile: PoissonTile
    ) {
        if (style.radius <= 0f) return
        val shapeScale = if (style.shape == ParticleShape.Diamond) 1.25f else 1f
        val radiusStep = style.radius * shapeScale / 512f
        val slot = max(32f, ceil(2f * style.radius * shapeScale + 4f))
        var count = 0
        var pointColor = -1
        fun vertex(x: Float, y: Float, u: Float, v: Float) {
            colors[count / 2] = pointColor
            vertices[count] = x
            coordinates[count++] = u
            vertices[count] = y
            coordinates[count++] = v
        }
        visitParticles(tile, geometry, style, screenOrigin, time) { x, y, radius, opacity ->
            if (count + 12 > vertices.size) {
                vertices = vertices.copyOf(vertices.size * 2)
                coordinates = coordinates.copyOf(coordinates.size * 2)
                colors = colors.copyOf(vertices.size / 2)
            }
            pointColor = ((opacity * 255f).roundToInt().coerceIn(0, 255) shl 24) or 0xFFFFFF
            val index = (radius * shapeScale / radiusStep).roundToInt().coerceIn(1, 512)
            val extent = index * radiusStep + 0.75f
            val tag = (index + 0.5f) * slot
            vertex(x - extent, y - extent, -extent, tag - extent)
            vertex(x + extent, y - extent, extent, tag - extent)
            vertex(x + extent, y + extent, extent, tag + extent)
            vertex(x - extent, y - extent, -extent, tag - extent)
            vertex(x + extent, y + extent, extent, tag + extent)
            vertex(x - extent, y + extent, -extent, tag + extent)
        }
        if (count == 0) return
        shader.setFloatUniform("uRadiusEncoding", slot, radiusStep)
        shader.setIntUniform("uShapeType", style.shape.ordinal)
        shader.setFloatUniform("uAlpha", alpha)
        shader.setColorUniform("uColor", color.toArgb())
        val canvas = drawContext.canvas.nativeCanvas
        canvas.drawVertices(Canvas.VertexMode.TRIANGLES, count, vertices, 0, coordinates, 0, colors, 0, null, 0, 0, paint)
    }
}

/** Creates the runtime (AGSL) painter where available, otherwise the static path painter. */
internal fun createParticlePainter(forceFallback: Boolean): ParticlePainter =
    if (!forceFallback && Build.VERSION.SDK_INT >= 33) {
        createRuntimeParticlePainter() ?: StaticParticlePainter()
    } else {
        StaticParticlePainter()
    }
