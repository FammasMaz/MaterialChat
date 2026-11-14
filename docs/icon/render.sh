#!/usr/bin/env bash
# Render launcher-icon previews from an SVG pair (<name>.svg + <name>_mono.svg).
# Usage: docs/icon/render.sh <svg> <outdir> [basename]
# Produces: <base>_512.png (full 108dp viewport), <base>_144.png (launcher-visible
# centre 72dp at 48dp = 144px), <base>_circle_144.png (circular mask), and
# <base>_mono_144.png (themed-icon silhouette, circular).
set -euo pipefail
svg="$1"; out="$2"; base="${3:-$(basename "${svg%.svg}")}"
mono="${svg%.svg}_mono.svg"
mkdir -p "$out"
# 108px @72dpi * 341.333/72 = 512px
magick -background none -density 341.333 "$svg" -resize 512x512! "$out/${base}_512.png"
# Launcher shows the centre 72/108 of the viewport: crop 2/3 and scale to 144px.
magick "$out/${base}_512.png" -gravity center -crop 341x341+0+0 +repage -resize 144x144! "$out/${base}_144.png"
magick "$out/${base}_144.png" \( +clone -fill black -colorize 100 -fill white -draw 'circle 72,72 72,0' \) \
  -alpha off -compose CopyOpacity -composite "$out/${base}_circle_144.png"
if [ -f "$mono" ]; then
  magick -background none -density 341.333 "$mono" -resize 512x512! -gravity center -crop 341x341+0+0 +repage -resize 144x144! \
    \( -size 144x144 xc:none -fill '#DCD6E4' -draw 'circle 72,72 72,0' \) +swap -compose over -composite \
    "$out/${base}_mono_144.png"
fi
