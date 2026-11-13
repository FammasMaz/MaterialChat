/*
 * Ported from sameerasw/essentials (MIT) — island/plugins/media/particles/GeminiParticleShader.kt
 * AGSL: each particle is a quad whose y coordinate encodes its radius bucket; the shader
 * reconstructs the circle/diamond signed distance and antialiases the edge.
 */
package com.materialchat.ui.theme.particles

internal const val NEURAL_PARTICLE_AGSL = """
uniform float2 uRadiusEncoding;
uniform float uAlpha;
uniform int uShapeType;
layout(color) uniform half4 uColor;

half4 main(float2 coord) {
    float index = floor(coord.y/uRadiusEncoding.x);
    float radius = index*uRadiusEncoding.y;
    float2 p = float2(coord.x,coord.y-(index+0.5)*uRadiusEncoding.x);
    float metric = uShapeType == 1 ? abs(p.x)+abs(p.y) : length(p);
    float sd = (metric-radius)*(uShapeType == 1 ? 0.70710678 : 1.0);
    half alpha = half((1.0-smoothstep(-0.5,0.5,sd))*min(2.0*radius,1.0)*uAlpha)*uColor.a;
    return half4(uColor.rgb*alpha,alpha);
}
"""
