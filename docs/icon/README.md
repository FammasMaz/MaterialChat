# MaterialChat launcher icon

## The idea

MaterialChat is a chat client that is also the phone's voice assistant. Its most distinctive
object is the assistant pill: a floating, near-black capsule with a live waveform inside and a
soft lattice of dots around it. The icon is that pill, with a speech-bubble tail, and the
waveform inside it. One shape says "chat", the glyph inside says "it listens".

- **Foreground** (`app/src/main/res/drawable/ic_launcher_foreground.xml`): a `#1C1B1F`
  capsule (40dp tall, fully rounded ends) with a short tail at the bottom-left, and three
  `#D0BCFF` rounded waveform bars inside. Two paths, nothing else.
- **Background** (`ic_launcher_background.xml`): flat `#EADDFF` (M3 primary container). No
  gradient, so it cannot band on API 26-28.
- **Monochrome** (`ic_launcher_monochrome.xml`): the same capsule with the three bars cut out as
  negative space (`evenOdd`), so the themed-icon silhouette still reads as "listening bubble" in
  one colour.
- All foreground content lives in a `scale(0.75)` group pivoted at the centre, keeping it inside
  the adaptive-icon safe zone (centre 66 of the 108 viewport).

Contrast: `#1C1B1F` on `#EADDFF` is about 14.8:1; the `#D0BCFF` bars on `#1C1B1F` are about
9.3:1. Both are far above the 3:1 needed for a graphic to read.

## Source of truth

- `docs/icon/ic_launcher.svg` — the colour icon (108x108 viewport, same coordinates as the
  vector drawables).
- `docs/icon/ic_launcher_mono.svg` — the monochrome silhouette.

Edit the SVGs, then copy the path data into the three drawables (the coordinates are identical;
only the XML wrapper differs).

## Regenerating previews

```sh
docs/icon/render.sh docs/icon/ic_launcher.svg docs/icon/preview ic_launcher
```

This writes to `docs/icon/preview/`:

| File | What it shows |
|---|---|
| `ic_launcher_512.png` | full 108dp viewport at 512px |
| `ic_launcher_144.png` | the launcher-visible centre 72dp at 48dp (144px @ 3x) |
| `ic_launcher_circle_144.png` | the same under a circular mask |
| `ic_launcher_mono_144.png` | the themed-icon silhouette on a neutral circle |

Requires ImageMagick 7 (`magick`). Note that on this machine ImageMagick uses its own internal
SVG parser (`rsvg-convert` is not on PATH); it silently drops paths whose `d` attribute spans
multiple lines, so keep each path's data on one line, or use `<circle>` elements.

## Rejected directions

The candidates are kept in `docs/icon/candidates/` with their renders in
`docs/icon/candidates/out/`.

- **A — wave pill** (shipped): bubble-shaped pill with the waveform. Reads at 48dp instantly and
  survives the circular mask and monochrome tint.
- **B — lattice bubble**: a speech bubble built entirely from the dot lattice, the tail being the
  last two dots. Beautiful at 512px, but at 48dp the body collapses into a textured wedge and the
  "bubble" is gone; the monochrome version is even worse because the dots fuse.
- **C — dot capsule**: the pill-bubble with one dot of negative space inside and one dot that has
  "escaped" outside the edge. The inner dot reads, but the escaped dot sits at the edge of the
  safe zone and is clipped or lost under the circular mask at 48dp; without it the icon is just a
  bubble with a hole.

## Legacy mipmaps

There are none; the app ships only the adaptive icon (`mipmap-anydpi-v26`), which is correct for
`minSdk 26`.
