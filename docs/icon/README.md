# MaterialChat launcher icon

## The idea

MaterialChat is a chat client that is also the phone's voice assistant, and its UI is Material 3
Expressive: cookie and scallop shapes, springy motion, tonal layering. The icon is a seven-lobe
M3 **cookie** pulled out into a speech-bubble point at the bottom-left, floating over a lighter
echoing cookie that peeks out at the top-right, with the assistant's three-bar listening waveform
cut into it. The cookie says "expressive", the point says "chat", the bars say "it listens", and
the echo gives it depth without a gradient.

- **Background** (`app/src/main/res/drawable/ic_launcher_background.xml`): flat `#EADDFF`
  (primary 90). No gradient, so nothing can band on API 26-28.
- **Foreground** (`ic_launcher_foreground.xml`): three flat paths. The echo cookie `#B69DF8`
  (primary 70), the bubble cookie `#4F378B` (primary 30), and the waveform `#EADDFF` (primary 90).
  Three tonal planes, one focal glyph.
- **Monochrome** (`ic_launcher_monochrome.xml`): the bubble outline with the bars cut out as
  negative space (`evenOdd`), so the themed-icon silhouette still reads as "listening bubble" in
  a single colour. The echo is dropped there; it would just fuse into the body.
- The bubble outline is **one closed path**: the tail is a spike in the radial function, not a
  separate triangle. That is what makes the `evenOdd` cutouts safe (a second overlapping shape
  would punch a hole where it crosses the body).
- Everything sits inside the 66dp safe circle: the generator asserts the farthest outline point
  is at most 33dp from the centre (currently 32.2dp), so no launcher mask clips the tail or the
  scallops. The bubble is nudged up-right of centre so the tail fits and the echo never collides
  with it.

Contrast: `#4F378B` on `#EADDFF` is about 7.9:1; the `#EADDFF` bars on `#4F378B` are the same.
The echo `#B69DF8` on `#EADDFF` is deliberately soft (about 1.6:1) so it reads as depth, not as
a second object.

## Source of truth

`docs/icon/gen_icon.py` generates everything: the two SVGs here and the three vector drawables
under `app/src/main/res/drawable/`. Edit the script, not the generated files.

```sh
python3 docs/icon/gen_icon.py              # shipped icon: SVGs + drawables
python3 docs/icon/gen_icon.py --candidates # every variant into docs/icon/candidates/
```

## Regenerating previews

```sh
docs/icon/render.sh docs/icon/ic_launcher.svg docs/icon/preview ic_launcher
```

This writes to `docs/icon/preview/`:

| File | What it shows |
|---|---|
| `ic_launcher_512.png` | full 108dp viewport at 512px |
| `ic_launcher_108_safezone.png` | full viewport at 3x with the 66dp safe-zone ring (red) and the 72dp visible ring (grey) |
| `ic_launcher_144.png` | the launcher-visible centre 72dp at 48dp (144px @ 3x) |
| `ic_launcher_48_1x.png` | the same at a true 48px; the harshest legibility check |
| `ic_launcher_circle_144.png` | the 48dp view under a circular mask |
| `ic_launcher_mono_144.png` | the themed-icon silhouette on a neutral circle |

For the candidates: `for s in docs/icon/candidates/*[^o].svg; do docs/icon/render.sh "$s"
docs/icon/candidates/out; done` (the `out/` directory is git-ignored).

Requires ImageMagick 7 (`magick`). On this machine ImageMagick uses its own internal SVG parser
(`rsvg-convert` is not on PATH); it silently drops paths whose `d` attribute spans multiple
lines, so the generator keeps each path's data on one line.

## Rejected directions

SVGs for every variant live in `docs/icon/candidates/`; the generator variants are listed in
`gen_icon.py`.

- **cookie7** (shipped): seven lobes at 12% amplitude. The scallops are still visible as
  individual bumps at a true 48px, and the tail reads as a point rather than an eighth lobe.
- **cookie9**: nine lobes at 10%. Smoother at 512px, but at 48px the scallops average into a
  slightly wobbly circle; the M3 shape language is gone exactly where it matters.
- **clover4**: four lobes at 18%. Unmistakably a flower, but the tail lands on a lobe and merges
  into it, so the "speech bubble" reading disappears and the bars float in a clover.
- **echo_below**: the echo offset down-left instead of up-right. It crowds the tail, and under the
  circular mask the two shapes read as one lumpy blob with a notch.
- Earlier rounds, still in `candidates/` as `a_`, `b_`, `c_`: the plain **wave pill** (the
  previous icon, too simple), the **lattice bubble** built from dots (collapses into a textured
  wedge at 48dp, fuses in monochrome), and the **dot capsule** (its escaped dot sits on the safe
  zone edge and is lost under the circular mask).

## Legacy mipmaps

There are none; the app ships only the adaptive icon (`mipmap-anydpi-v26`), which is correct for
`minSdk 26`.
