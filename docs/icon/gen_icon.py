#!/usr/bin/env python3
"""Generate the MaterialChat launcher icon (SVG previews + Android vector drawables).

Concept: a Material 3 Expressive seven-lobe "cookie" pulled out into a speech-bubble point at
the bottom-left, floating over a second cookie (same family, half a lobe out of phase, lighter
tone) that peeks out at the top-right, with the assistant's three-bar listening waveform as the
focal glyph. Three flat tonal planes (field / echo / body) plus the glyph; no gradients.

All geometry lives in the 108x108 adaptive-icon viewport and stays inside the 66dp safe circle
(radius 33 around 54,54). The bubble outline is ONE closed path (the tail is a spike in the
radial function, not a separate triangle), so the monochrome layer can cut the bars out with
evenOdd without the tail punching a hole in the body.

Run from the repo root:

    python3 docs/icon/gen_icon.py                  # writes the shipped icon (SVGs + drawables)
    python3 docs/icon/gen_icon.py --candidates     # writes every variant to docs/icon/candidates
    docs/icon/render.sh docs/icon/ic_launcher.svg docs/icon/preview ic_launcher
"""
import math
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DRAWABLE = os.path.join(ROOT, "app/src/main/res/drawable")
DOCS = os.path.join(ROOT, "docs/icon")

# Palette: M3 baseline primary tonal steps (flat fills only, no gradients).
FIELD = "#EADDFF"   # primary 90 - background layer
ECHO = "#B69DF8"    # primary 70 - back tonal plane
BODY = "#4F378B"    # primary 30 - the cookie bubble
GLYPH = "#EADDFF"   # primary 90 - waveform on the body


def lobed(cx, cy, radius, lobes, amp, phase_deg, tail_deg=None, tail_len=0.0, tail_width=0.5,
          segments=140):
    """Closed cubic-Bezier path for r(t) = R(1 + amp*cos(lobes*(t-phase))) + optional tail spike.

    The tail is a spike max(0, 1-|d|/w)^1.5 added to the radius, so it ends in a crisp point
    (the speech-bubble tip) while staying part of the same closed outline.
    """
    phase = math.radians(phase_deg)
    tail = math.radians(tail_deg) if tail_deg is not None else None

    def r(t):
        v = radius * (1.0 + amp * math.cos(lobes * (t - phase)))
        if tail is not None:
            d = (t - tail + math.pi) % (2 * math.pi) - math.pi
            v += tail_len * max(0.0, 1.0 - abs(d) / tail_width) ** 1.5
        return v

    def p(t):
        rr = r(t)
        return cx + rr * math.cos(t), cy + rr * math.sin(t)

    def dp(t, h=1e-3):
        x1, y1 = p(t - h)
        x2, y2 = p(t + h)
        return (x2 - x1) / (2 * h), (y2 - y1) / (2 * h)

    step = 2 * math.pi / segments
    pts = [p(i * step) for i in range(segments)]
    ders = [dp(i * step) for i in range(segments)]
    out = ["M%.2f,%.2f" % pts[0]]
    for i in range(segments):
        j = (i + 1) % segments
        x0, y0 = pts[i]
        x1, y1 = pts[j]
        dx0, dy0 = ders[i]
        dx1, dy1 = ders[j]
        c1 = (x0 + dx0 * step / 3, y0 + dy0 * step / 3)
        c2 = (x1 - dx1 * step / 3, y1 - dy1 * step / 3)
        out.append("C%.2f,%.2f %.2f,%.2f %.2f,%.2f" % (c1 + c2 + (x1, y1)))
    out.append("Z")
    return " ".join(out)


def bar(cx, cy, w, h):
    """Fully rounded vertical bar centred at (cx, cy), wound clockwise."""
    r = w / 2
    top = cy - h / 2 + r
    bottom = cy + h / 2 - r
    return ("M%.2f,%.2f A%.2f,%.2f 0 0 1 %.2f,%.2f L%.2f,%.2f A%.2f,%.2f 0 0 1 %.2f,%.2f Z"
            % (cx - r, top, r, r, cx + r, top, cx + r, bottom, r, r, cx - r, bottom))


def bars(cx, cy, w=6.5, gap=11.0, heights=(9.5, 17.0, 11.5)):
    """The listening waveform: short / tall / medium rounded bars centred on (cx, cy)."""
    return " ".join(bar(cx + (i - 1) * gap, cy, w, h) for i, h in enumerate(heights))


# Body centre is nudged up-right so the tail (down-left) stays inside the safe circle; the
# echo sits up-right of the body so it never collides with the tail.
BODY_C = (56.0, 52.0)


def variant_cookie7():
    """Shipped: 7-lobe cookie bubble, echo cookie half a lobe out of phase up-right."""
    body = lobed(*BODY_C, radius=22.0, lobes=7, amp=0.12, phase_deg=135,
                 tail_deg=135, tail_len=7.5, tail_width=0.5)
    echo = lobed(60.5, 47.5, radius=20.5, lobes=7, amp=0.12, phase_deg=135 + 360 / 14)
    return dict(body=body, echo=echo, glyph=bars(*BODY_C))


def variant_cookie9():
    """9 lobes at lower amplitude: smoother, but the scallops vanish at 48dp."""
    body = lobed(*BODY_C, radius=22.0, lobes=9, amp=0.10, phase_deg=135,
                 tail_deg=135, tail_len=7.5, tail_width=0.5)
    echo = lobed(60.5, 47.5, radius=20.5, lobes=9, amp=0.10, phase_deg=135 + 360 / 18)
    return dict(body=body, echo=echo, glyph=bars(*BODY_C))


def variant_clover4():
    """4 lobes at high amplitude: reads as a clover/flower, the tail merges into a lobe."""
    body = lobed(*BODY_C, radius=22.0, lobes=4, amp=0.18, phase_deg=135,
                 tail_deg=135, tail_len=7.5, tail_width=0.5)
    echo = lobed(60.5, 47.5, radius=20.5, lobes=4, amp=0.18, phase_deg=180)
    return dict(body=body, echo=echo, glyph=bars(*BODY_C))


def variant_echo_below():
    """Echo offset down-left instead: it crowds the tail and the bubble loses its point."""
    body = lobed(*BODY_C, radius=22.0, lobes=7, amp=0.12, phase_deg=135,
                 tail_deg=135, tail_len=7.5, tail_width=0.5)
    echo = lobed(50.0, 58.0, radius=20.5, lobes=7, amp=0.12, phase_deg=135 + 360 / 14)
    return dict(body=body, echo=echo, glyph=bars(*BODY_C))


VARIANTS = {
    "cookie7": variant_cookie7,
    "cookie9": variant_cookie9,
    "clover4": variant_clover4,
    "echo_below": variant_echo_below,
}
SHIPPED = "cookie7"


def check_safe_zone(name, geo):
    # Only the two outlines: they are pure M/C commands, so every pair is a point. The glyph's
    # arc commands carry radii pairs and it lies inside the body anyway.
    worst = 0.0
    for path in (geo["body"], geo["echo"]):
        for x, y in re.findall(r"(-?\d+\.\d+),(-?\d+\.\d+)", path):
            worst = max(worst, math.hypot(float(x) - 54, float(y) - 54))
    print("%s: max radius from centre %.1f (safe zone 33)" % (name, worst))
    assert worst <= 33.0, "%s leaves the 66dp safe zone" % name


SVG = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="108" height="108">
<!-- Generated by docs/icon/gen_icon.py - edit the script, not this file. -->
<rect width="108" height="108" fill="{field}"/>
<path fill="{echo}" d="{echo_d}"/>
<path fill="{body}" d="{body_d}"/>
<path fill="{glyph}" d="{glyph_d}"/>
</svg>
"""

SVG_MONO = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="108" height="108">
<!-- Generated by docs/icon/gen_icon.py - edit the script, not this file. -->
<path fill="#000000" fill-rule="evenodd" d="{body_d} {glyph_d}"/>
</svg>
"""

HEADER = """<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by docs/icon/gen_icon.py - edit the script, not this file. -->
"""

VECTOR_OPEN = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
"""

FOREGROUND = HEADER + """<!--
  MaterialChat launcher foreground: an M3 Expressive seven-lobe cookie pulled into a
  speech-bubble point, over a lighter echo cookie, with the assistant's three-bar listening
  waveform inside. Everything is inside the 66dp safe circle.
-->
""" + VECTOR_OPEN + """    <!-- Echo plane (primary 70). -->
    <path
        android:fillColor="{echo}"
        android:pathData="{echo_d}" />
    <!-- Cookie speech bubble (primary 30). -->
    <path
        android:fillColor="{body}"
        android:pathData="{body_d}" />
    <!-- Listening waveform (primary 90). -->
    <path
        android:fillColor="{glyph}"
        android:pathData="{glyph_d}" />
</vector>
"""

BACKGROUND = HEADER + """<!-- Flat primary-container field; no gradient, so it cannot band on API 26-28. -->
""" + VECTOR_OPEN + """    <path
        android:fillColor="{field}"
        android:pathData="M0,0h108v108h-108z" />
</vector>
"""

MONOCHROME = HEADER + """<!--
  Themed-icon silhouette: the cookie bubble with the waveform cut out as negative space (evenOdd),
  so the glyph still reads when the launcher tints it a single colour.
-->
""" + VECTOR_OPEN + """    <path
        android:fillColor="#000000"
        android:fillType="evenOdd"
        android:pathData="{body_d} {glyph_d}" />
</vector>
"""


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        f.write(text)
    print("wrote", os.path.relpath(path, ROOT))


def fmt_for(geo):
    return dict(field=FIELD, echo=ECHO, body=BODY, glyph=GLYPH,
                echo_d=geo["echo"], body_d=geo["body"], glyph_d=geo["glyph"])


if __name__ == "__main__":
    if "--candidates" in sys.argv:
        for name, fn in VARIANTS.items():
            geo = fn()
            check_safe_zone(name, geo)
            f = fmt_for(geo)
            write(os.path.join(DOCS, "candidates", "%s.svg" % name), SVG.format(**f))
            write(os.path.join(DOCS, "candidates", "%s_mono.svg" % name), SVG_MONO.format(**f))
        sys.exit(0)
    geo = VARIANTS[SHIPPED]()
    check_safe_zone(SHIPPED, geo)
    f = fmt_for(geo)
    write(os.path.join(DOCS, "ic_launcher.svg"), SVG.format(**f))
    write(os.path.join(DOCS, "ic_launcher_mono.svg"), SVG_MONO.format(**f))
    write(os.path.join(DRAWABLE, "ic_launcher_foreground.xml"), FOREGROUND.format(**f))
    write(os.path.join(DRAWABLE, "ic_launcher_background.xml"), BACKGROUND.format(**f))
    write(os.path.join(DRAWABLE, "ic_launcher_monochrome.xml"), MONOCHROME.format(**f))
