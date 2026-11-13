#!/usr/bin/env bash
set -euo pipefail
out="${1:-test-evidence/assistant}"
mkdir -p "$out"
component=com.materialchat/com.materialchat.assistant.service.MaterialChatVoiceInteractionService
# Connected tests uninstall their target APK on completion; restore it for system invocation.
adb install -r app/build/outputs/apk/github/debug/app-github-debug.apk
adb shell pm path com.materialchat > "$out/package.txt"
# Select only after installation. Older Android clears unavailable service choices.
adb shell settings put secure voice_interaction_service "$component"
adb shell settings put secure assistant "$component"
for attempt in $(seq 1 20); do
  adb shell dumpsys voiceinteraction > "$out/activation.txt"
  if grep -q 'mComponent=com.materialchat/' "$out/activation.txt"; then break; fi
  sleep 1
done
grep -q 'mComponent=com.materialchat/' "$out/activation.txt"
adb logcat -c || true
adb shell input keyevent KEYCODE_HOME
# Changing the selected assistant restarts SystemUI on API 26. Wait for its
# status-bar binder before sending the key, otherwise the invocation is lost.
for attempt in $(seq 1 30); do
  adb shell dumpsys statusbar > "$out/statusbar.txt"
  if grep -Eq 'mBar=.*(Proxy|Stub)' "$out/statusbar.txt"; then break; fi
  sleep 1
done
adb shell input keyevent KEYCODE_ASSIST
sleep 4
adb shell dumpsys voiceinteraction > "$out/voiceinteraction.txt"
adb shell dumpsys window > "$out/windows.txt"
adb shell uiautomator dump /sdcard/assistant-window.xml
adb pull /sdcard/assistant-window.xml "$out/ui.xml"
adb exec-out screencap -p > "$out/screen.png"
adb logcat -d > "$out/logcat.txt"
# Require rendered assistant controls, not merely a live service or lifecycle callback.
grep -q 'End assistant session' "$out/ui.xml"
if grep -E 'FATAL EXCEPTION|Unable to add window|BadTokenException' "$out/logcat.txt"; then
  exit 1
fi

# --- Neural particle field (assistant window only) --------------------------------
# The decoration is the ported essentials particle field: an AGSL runtime painter on
# API 33+ and a static Path painter below. Production logs one line per painter/capsule
# signature from inside the draw path, so this marker proves the real assistant window
# resolved the expected painter and drew the field with a valid capsule.
sdk=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
if [ "$sdk" -ge 33 ]; then expected_painter=runtime; else expected_painter=static; fi
for attempt in $(seq 1 20); do
  adb logcat -d > "$out/logcat-particles.txt"
  if grep -q "NeuralParticles: painter=$expected_painter" "$out/logcat-particles.txt"; then break; fi
  sleep 1
done
grep -q "NeuralParticles: painter=$expected_painter" "$out/logcat-particles.txt"
grep "NeuralParticles" "$out/logcat-particles.txt" | tail -5 | tee "$out/particle-painter.txt"

# Two raw frames prove the field is live around the real card: on API 33+ the AGSL
# painter must animate the band just outside the card while a control band far from
# the card stays static; below 33 the static painter must draw without animating.
# Visible pixel coverage of the static painter on API 26 is asserted by
# AssistantNeuralParticleFieldTest, which renders the production card and decoration
# host on the same API level and compares enabled/disabled captures.
adb shell wm density > "$out/density.txt"
adb exec-out screencap > "$out/particles-frame-a.raw"
sleep 1
adb exec-out screencap > "$out/particles-frame-b.raw"
python3 - "$out/ui.xml" "$out/particles-frame-a.raw" "$out/particles-frame-b.raw" "$out/density.txt" "$sdk" <<'PYPARTICLES' | tee "$out/particle-band.txt"
import re, struct, sys, xml.etree.ElementTree as ET

ui, raw_a, raw_b, density_path, sdk = sys.argv[1:6]


def load(path):
    data = open(path, 'rb').read()
    width, height, fmt = struct.unpack('<III', data[:12])
    assert fmt == 1, f'unexpected screencap format {fmt}'
    body = data[12:]
    assert len(body) >= width * height * 4, f'truncated screencap: {len(body)}'
    return width, height, body


def bounds(node):
    return list(map(int, re.findall(r'\d+', node.get('bounds', ''))))


sheets = [n for n in ET.parse(ui).iter('node')
          if n.get('resource-id', '').split('/')[-1] == 'assistant_sheet']
assert len(sheets) == 1, 'Missing unique assistant sheet boundary'
left, top, right, bottom = bounds(sheets[0])
densities = re.findall(r'(?:Physical|Override) density:\s*(\d+)', open(density_path).read())
assert densities, 'Cannot establish display density'
scale = int(densities[-1]) / 160.0

wa, ha, a = load(raw_a)
wb, hb, b = load(raw_b)
assert (wa, ha) == (wb, hb), 'Screenshot sizes differ between frames'
w, h = wa, ha


def diff_ratio(x0, y0, x1, y1, threshold=12):
    x0 = max(0, min(x0, w - 1))
    x1 = max(x0 + 1, min(x1, w))
    y0 = max(0, min(y0, h - 1))
    y1 = max(y0 + 1, min(y1, h))
    changed = total = 0
    for y in range(y0, y1):
        off = (y * w + x0) * 4
        span = (x1 - x0) * 4
        row_a = a[off:off + span]
        row_b = b[off:off + span]
        for i in range(0, span, 4):
            delta = abs(row_a[i] - row_b[i]) + abs(row_a[i + 1] - row_b[i + 1]) + abs(row_a[i + 2] - row_b[i + 2])
            if delta >= threshold:
                changed += 1
            total += 1
    return changed, total


bx0 = int(left + (right - left) * 0.1)
bx1 = int(right - (right - left) * 0.1)
by0 = max(0, int(top - 56 * scale))
by1 = max(by0 + 1, int(top - 2 * scale))
cy0 = int(h * 0.12)
cy1 = int(h * 0.22)

band_changed, band_total = diff_ratio(bx0, by0, bx1, by1)
ctrl_changed, ctrl_total = diff_ratio(bx0, cy0, bx1, cy1)
band_ratio = band_changed / band_total
ctrl_ratio = ctrl_changed / ctrl_total
print(f'Particle band {bx0},{by0}-{bx1},{by1}: changed={band_ratio:.4f} ({band_changed}/{band_total})')
print(f'Control band {bx0},{cy0}-{bx1},{cy1}: changed={ctrl_ratio:.4f} ({ctrl_changed}/{ctrl_total})')
print(f'Card bounds={left},{top},{right},{bottom} density={scale} sdk={sdk}')
assert ctrl_ratio <= 0.005, f'Control band must stay static: {ctrl_ratio:.4f}'
if int(sdk) >= 33:
    assert band_ratio >= 0.015, f'AGSL particle field must animate around the card: {band_ratio:.4f}'
else:
    assert band_ratio <= 0.005, f'Static painter must not animate the band: {band_ratio:.4f}'
print(f'Neural particle field passed on API {sdk}')
PYPARTICLES

# Exercise the actual keyboard state, not only initial overlay visibility.
python3 - "$out/ui.xml" <<'PYTAP' | xargs adb shell input tap
import sys, re, xml.etree.ElementTree as ET
nodes=ET.parse(sys.argv[1]).iter('node')
n=next(n for n in nodes if n.get('text') == 'Keyboard')
x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')))
print((x1+x2)//2,(y1+y2)//2)
PYTAP
sleep 3
adb shell dumpsys input_method > "$out/keyboard-input-method.txt"
adb shell dumpsys window > "$out/keyboard-windows.txt"
adb shell uiautomator dump /sdcard/assistant-keyboard.xml
adb pull /sdcard/assistant-keyboard.xml "$out/keyboard-ui.xml"
adb exec-out screencap -p > "$out/keyboard-screen.png"
grep -q 'Hide keyboard' "$out/keyboard-ui.xml"

# Require a real visible IME, not merely the keyboard-mode button.
grep -Eq "mInputShown=true|isInputViewShown=true|inputShown=true" "$out/keyboard-input-method.txt"

# Fail closed on covered controls or a second keyboard-sized gap.
adb shell wm density > "$out/density.txt"
previous_geometry=""
stable_samples=0
for sample in $(seq 1 8); do
  adb shell dumpsys input_method > "$out/keyboard-input-method.txt"
  adb shell dumpsys window > "$out/keyboard-windows.txt"
  adb shell uiautomator dump /sdcard/assistant-keyboard.xml >/dev/null
  adb pull /sdcard/assistant-keyboard.xml "$out/keyboard-ui.xml" >/dev/null
  cp "$out/keyboard-windows.txt" "$out/keyboard-windows-$sample.txt"
  cp "$out/keyboard-input-method.txt" "$out/keyboard-input-method-$sample.txt"
  cp "$out/keyboard-ui.xml" "$out/keyboard-ui-$sample.xml"
  if geometry=$(
python3 - "$out/keyboard-ui.xml" "$out/keyboard-windows.txt" "$out/keyboard-input-method.txt" "$out/density.txt" <<'PYGEOMETRY'
import re, sys, xml.etree.ElementTree as ET
nodes=list(ET.parse(sys.argv[1]).iter('node'))
def bounds(n): return list(map(int,re.findall(r'\d+',n.get('bounds',''))))
windows=open(sys.argv[2]).read()
frames=re.findall(r'type=ime frame=\[\d+,(\d+)\]\[\d+,\d+\].*visible=true',windows)
if not frames:
    # API 26 reports IME content in local coordinates, not InsetsSource frames.
    method=open(sys.argv[3]).read()
    inset=re.search(r'contentTopInsets=(\d+)',method)
    ime=re.search(r'Window #\d+ Window\{[^\n]* InputMethod\}:(.*?)(?=\n  Window #|\Z)',windows,re.S)
    origin=re.search(r'mShownPosition=\[(-?\d+),(-?\d+)\]',ime.group(1)) if ime else None
    if not (inset and origin and 'Surface: shown=true' in ime.group(1)):
        raise SystemExit('Cannot establish visible IME geometry')
    frames=[int(inset.group(1))+int(origin.group(2))]
top=min(map(int,frames))
controls=[n for n in nodes if n.get('text')=='Hide keyboard']
assert len(controls)==1, 'Missing keyboard control'
b= bounds(controls[0])
assert b[3] <= top, f'Keyboard covers assistant controls: {b}, IME top={top}'
sheets=[n for n in nodes if n.get('resource-id','').split('/')[-1]=='assistant_sheet']
assert len(sheets)==1, 'Missing unique assistant sheet boundary'
sheet=bounds(sheets[0])
densities=re.findall(r'(?:Physical|Override) density:\s*(\d+)',open(sys.argv[4]).read())
assert densities, 'Cannot establish display density'
scale=int(densities[-1])/160
assert sheet[2]>sheet[0] and sheet[3]>sheet[1], f'Empty sheet: {sheet}'
gap=(top-sheet[3])/scale
assert abs(gap-8)<=2, f'Incorrect card-to-keyboard gap: {gap:.2f}dp; sheet={sheet}, IME top={top}'
assert sheet[0]<=b[0]<b[2]<=sheet[2] and sheet[1]<=b[1]<b[3]<=sheet[3], 'Keyboard control outside sheet'
print(f'Keyboard geometry passed: card gap={gap:.2f}dp, sheet={sheet}, IME top={top}, density={scale}')
PYGEOMETRY
  ); then
    printf '%s\n' "$geometry" | tee "$out/keyboard-geometry-$sample.txt"
    if [ "$geometry" = "$previous_geometry" ]; then
      stable_samples=$((stable_samples + 1))
    else
      stable_samples=1
    fi
    previous_geometry="$geometry"
    if [ "$stable_samples" -ge 3 ]; then break; fi
  else
    stable_samples=0
    previous_geometry=""
  fi
  sleep 1
done
[ "$stable_samples" -ge 3 ] || { echo 'Assistant keyboard geometry did not settle into three valid identical samples' >&2; exit 1; }
adb exec-out screencap -p > "$out/keyboard-screen.png"


# Submit through the real assistant; no provider credentials are configured.
adb shell input text 'set%salarm%sat%s7%sam'
adb shell input keyevent KEYCODE_ENTER
sleep 3
adb shell dumpsys activity activities > "$out/clock-activities.txt"
adb shell uiautomator dump /sdcard/assistant-clock.xml
adb pull /sdcard/assistant-clock.xml "$out/clock-ui.xml"
adb exec-out screencap -p > "$out/clock-screen.png"
# A resumed Clock activity proves the session handoff escaped background-launch restrictions.
grep -Ei '(ResumedActivity|topResumedActivity|mResumedActivity).*com\.(android|google\.android)\.deskclock' "$out/clock-activities.txt"
