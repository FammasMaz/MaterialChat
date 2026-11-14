#!/usr/bin/env bash
set -euo pipefail
out="${1:-test-evidence/assistant}"
mkdir -p "$out"
component=com.materialchat/com.materialchat.assistant.service.MaterialChatVoiceInteractionService
# Connected tests uninstall their target APK on completion; restore it for system invocation.
adb install -r app/build/outputs/apk/github/debug/app-github-debug.apk
# The test APK carries a deterministic recognition fixture (ContractRecognitionService) that
# the gate binds as the assistant's engine so the session genuinely reaches the listening
# state through the production path. Connected tests uninstall it too, so restore both.
adb install -r app/build/outputs/apk/androidTest/github/debug/app-github-debug-androidTest.apk
adb shell pm path com.materialchat > "$out/package.txt"
adb shell pm path com.materialchat.test > "$out/test-package.txt"
# Select only after installation. Older Android clears unavailable service choices.
adb shell settings put secure voice_interaction_service "$component"
adb shell settings put secure assistant "$component"
for attempt in $(seq 1 20); do
  adb shell dumpsys voiceinteraction > "$out/activation.txt"
  if grep -q 'mComponent=com.materialchat/' "$out/activation.txt"; then break; fi
  sleep 1
done
grep -q 'mComponent=com.materialchat/' "$out/activation.txt"

# Listening is part of the gate: the accent dots and their painter log exist only while the
# assistant listens (they follow recognition, not the whole session). The hosted runner has
# no microphone permission and no usable engine, so the gate grants RECORD_AUDIO to the app
# (and to the test package, whose fixture checks it against its own context on the
# direct-bind path below API 31), points the configured default recognizer at the fixture,
# and puts the fixture in hold mode: it reports ready and keeps the session open until the
# Keyboard button cancels it. This proves listening-driven rendering, not acoustics.
fixture=com.materialchat.test/com.materialchat.assistant.voice.ContractRecognitionService
adb shell pm grant com.materialchat android.permission.RECORD_AUDIO
adb shell pm grant com.materialchat.test android.permission.RECORD_AUDIO
# Selecting the assistant rewrote the default recognizer to our own declaration, so the
# fixture is set only now, after that selection has settled.
adb shell settings put secure voice_recognition_service "$fixture"
adb shell settings put global materialchat_contract_recognizer_hold 1
trap 'adb shell settings delete global materialchat_contract_recognizer_hold >/dev/null 2>&1 || true' EXIT
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
# Wait for the rendered controls AND the listening state: auto-listen binds the fixture,
# which reports ready, so the pill shows its Stop-listening button while it holds.
for attempt in $(seq 1 20); do
  sleep 1
  adb shell uiautomator dump /sdcard/assistant-window.xml >/dev/null 2>&1 || continue
  adb pull /sdcard/assistant-window.xml "$out/ui.xml" >/dev/null 2>&1 || continue
  if grep -q 'End assistant session' "$out/ui.xml" && grep -q 'Stop listening' "$out/ui.xml"; then break; fi
done
adb shell dumpsys voiceinteraction > "$out/voiceinteraction.txt"
adb shell dumpsys window > "$out/windows.txt"
adb exec-out screencap -p > "$out/screen.png"
adb logcat -d > "$out/logcat.txt"
# Require rendered assistant controls, not merely a live service or lifecycle callback.
grep -q 'End assistant session' "$out/ui.xml"
if grep -E 'FATAL EXCEPTION|Unable to add window|BadTokenException' "$out/logcat.txt"; then
  exit 1
fi
# The fixture must be the engine the production selection bound, and the session must be
# listening through it; otherwise there are no dots to check and the gate fails here with
# the reason instead of on the painter marker.
grep 'MaterialChatSpeech' "$out/logcat.txt" | tail -5 | tee "$out/recognizer.txt"
grep -q 'ContractRecognitionService' "$out/recognizer.txt"
grep -q 'Stop listening' "$out/ui.xml"

# --- Display keep-awake -----------------------------------------------------------
# On the lock screen the display timeout used to cut a spoken reply off mid-sentence.
# The session must hold FLAG_KEEP_SCREEN_ON on its own window while it works; it is
# listening at this point. The instrumentation test covers releasing it on hide, since a
# directly-constructed session has no platform window to carry the flag.
# The flag is applied from the overlay's composition, which can land a moment after the
# controls appear, so poll rather than sampling once.
keep_awake=
for attempt in $(seq 1 10); do
  adb shell dumpsys window > "$out/windows.txt"
  if keep_awake=$(
python3 - "$out/windows.txt" <<'PYKEEP'
import re, sys

# FLAG_KEEP_SCREEN_ON. Newer platforms print window flags by name, older ones as a hex
# mask (API 26: "fl=#8d810120"), so both forms are accepted.
KEEP_SCREEN_ON_BIT = 0x00000080

text = open(sys.argv[1], errors='replace').read()
blocks = re.split(r'\n(?=  Window #)', text)
session = [b for b in blocks
           if 'VoiceInteractionSession' in b.split('\n')[0] or 'ty=VOICE_INTERACTION' in b]
if not session:
    raise SystemExit('no voice-interaction session window in the window dump')
block = session[0]
if 'KEEP_SCREEN_ON' in block:
    print('keep-screen-on: session window holds FLAG_KEEP_SCREEN_ON')
    raise SystemExit(0)
mask = re.search(r'\bfl=[#]?(?:0x)?([0-9a-fA-F]+)', block)
if mask and int(mask.group(1), 16) & KEEP_SCREEN_ON_BIT:
    print('keep-screen-on: session window holds FLAG_KEEP_SCREEN_ON')
    raise SystemExit(0)
raise SystemExit(
    'the assistant session window does not hold FLAG_KEEP_SCREEN_ON while it works:\n'
    + '\n'.join(line for line in block.splitlines()[:12])
)
PYKEEP
  ); then
    printf '%s\n' "$keep_awake" | tee "$out/keep-awake.txt"
    break
  fi
  sleep 1
done
[ -n "$keep_awake" ] || {
  echo 'The assistant session never held FLAG_KEEP_SCREEN_ON while it was working' >&2
  grep -E 'keep-awake' "$out/logcat.txt" | tail -5 >&2 || true
  exit 1
}

# --- Neural dot lattice (assistant window only) ------------------------------------
# The assistant's backdrop texture is Gemini Live's: a bottom-anchored scrim band carrying a
# uniform dot lattice (1.2dp dots on a 7dp pitch, fading upward). Production logs one line per
# lattice signature from inside the draw path, so this marker proves the real assistant window
# laid the lattice out and drew it, rather than the modifier merely being attached.
for attempt in $(seq 1 20); do
  adb logcat -d > "$out/logcat-lattice.txt"
  if grep -q "NeuralLattice: dots=" "$out/logcat-lattice.txt"; then break; fi
  sleep 1
done
grep -q "NeuralLattice: dots=" "$out/logcat-lattice.txt"
grep "NeuralLattice" "$out/logcat-lattice.txt" | tail -5 | tee "$out/lattice-summary.txt"

# A raw frame proves the lattice is on screen and confined to its band: the band must carry
# dot texture (bright specks over its scrim) and the band well above the pill must not. Both
# are measured against the frame itself, so a busy wallpaper cannot satisfy them on its own.
adb shell wm density > "$out/density.txt"
sleep 1
adb exec-out screencap > "$out/lattice-frame.raw"
python3 - "$out/ui.xml" "$out/lattice-frame.raw" "$out/density.txt" <<'PYLATTICE' | tee "$out/lattice-band.txt"
import re, struct, sys, xml.etree.ElementTree as ET

ui, raw, density_path = sys.argv[1:4]


def load(path):
    data = open(path, 'rb').read()
    width, height, fmt = struct.unpack('<III', data[:12])
    assert fmt == 1, f'unexpected screencap format {fmt}'
    body = data[12:]
    assert len(body) >= width * height * 4, f'truncated screencap: {len(body)}'
    return width, height, body


def bounds(node):
    return list(map(int, re.findall(r'\d+', node.get('bounds', ''))))


all_nodes = list(ET.parse(ui).iter('node'))
sheets = [n for n in all_nodes if n.get('resource-id', '').split('/')[-1] == 'assistant_sheet']
assert len(sheets) == 1, 'Missing unique assistant sheet boundary'
pills = [n for n in all_nodes if n.get('resource-id', '').split('/')[-1] == 'assistant_pill']
left, top, right, bottom = bounds((pills or sheets)[0])
densities = re.findall(r'(?:Physical|Override) density:\s*(\d+)', open(density_path).read())
assert densities, 'Cannot establish display density'
scale = int(densities[-1]) / 160.0

w, h, px = load(raw)


def luma(x, y):
    off = (y * w + x) * 4
    return (px[off] * 299 + px[off + 1] * 587 + px[off + 2] * 114) // 1000


def speck_ratio(x0, y0, x1, y1, delta=10):
    """Fraction of pixels clearly brighter than their own row's median: the lattice's dots."""
    x0 = max(0, min(x0, w - 1))
    x1 = max(x0 + 1, min(x1, w))
    y0 = max(0, min(y0, h - 1))
    y1 = max(y0 + 1, min(y1, h))
    bright = total = 0
    for y in range(y0, y1):
        row = sorted(luma(x, y) for x in range(x0, x1))
        median = row[len(row) // 2]
        for x in range(x0, x1):
            if luma(x, y) >= median + delta:
                bright += 1
            total += 1
    return bright / total


# The lattice band is the bottom quarter of the window (AssistantLatticeStyle.BAND_FRACTION)
# and the pill sits inside it. Its dots are brightest at the very bottom, so measure the rows
# between the pill's bottom edge and the window's bottom edge; if the pill leaves too little
# room there, fall back to the rows just above its top edge.
band_top = max(0, h - int(h * 0.24))
sample_x0 = int(w * 0.05)
sample_x1 = int(w * 0.95)
below_y0 = bottom + int(2 * scale)
below_y1 = h - int(2 * scale)
above_y0 = max(band_top, top - int(56 * scale))
above_y1 = top - int(24 * scale)
if below_y1 - below_y0 >= 8:
    band_y0, band_y1, where = below_y0, below_y1, 'below the pill'
elif above_y1 - above_y0 >= 8:
    band_y0, band_y1, where = above_y0, above_y1, 'above the pill'
else:
    raise AssertionError(f'No room to sample the lattice band: pill={top}..{bottom} band_top={band_top}')
ctrl_y0 = int(h * 0.30)
ctrl_y1 = int(h * 0.40)
assert ctrl_y1 < band_top, f'Control band must lie above the lattice band: {ctrl_y1} >= {band_top}'

band = speck_ratio(sample_x0, band_y0, sample_x1, band_y1)
ctrl = speck_ratio(sample_x0, ctrl_y0, sample_x1, ctrl_y1)
print(f'Lattice band {sample_x0},{band_y0}-{sample_x1},{band_y1} ({where}): specks={band:.4f}')
print(f'Control band {sample_x0},{ctrl_y0}-{sample_x1},{ctrl_y1}: specks={ctrl:.4f}')
print(f'Card bounds={left},{top},{right},{bottom} band_top={band_top} density={scale}')
# 1.2dp dots on a 7dp pitch cover ~2.3% of the area; require a quarter of that at minimum.
assert band >= 0.004, f'The dot lattice must be visible in its band: {band:.4f}'
assert band >= ctrl * 2.5, f'The lattice must stay in its band: band={band:.4f} control={ctrl:.4f}'
print('Neural dot lattice passed')
PYLATTICE

# Exercise the actual keyboard state, not only initial overlay visibility.
python3 - "$out/ui.xml" <<'PYTAP' | xargs adb shell input tap
import sys, re, xml.etree.ElementTree as ET
nodes=ET.parse(sys.argv[1]).iter('node')
# The pill's keyboard toggle is an icon button: match its content description. Entering
# keyboard mode cancels listening (the fixture releases its hold), so the keyboard and
# alarm steps below run exactly as they did before listening was part of the gate.
n=next(n for n in nodes if n.get('content-desc') == 'Keyboard')
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
controls=[n for n in nodes if n.get('content-desc')=='Hide keyboard']
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
