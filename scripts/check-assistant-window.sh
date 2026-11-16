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

# --- Neural particle field (assistant window only) ---------------------------------
# The assistant's decoration is essentials' Gemini pill: the pill's own colour painted as a
# soft-edged capsule, the accent glowing inside along that edge, and scattered dots drifting in
# the band around it. Production logs one line per painter signature from inside the draw path,
# so this marker proves the real assistant window laid the field out and drew it, rather than
# the modifier merely being attached.
for attempt in $(seq 1 20); do
  adb logcat -d > "$out/logcat-particles.txt"
  if grep -q "NeuralParticles: painter=" "$out/logcat-particles.txt"; then break; fi
  sleep 1
done
grep -q "NeuralParticles: painter=" "$out/logcat-particles.txt"
grep "NeuralParticles" "$out/logcat-particles.txt" | tail -5 | tee "$out/particle-summary.txt"

# The marker carries the geometry the real window resolved, so the field is checked exactly
# rather than sampled from pixels: the assistant window is translucent and hosted over the
# launcher, where the wallpaper and dock icons dominate any pixel statistic (hosted run
# 43657360948 measured the wallpaper, 0.2792 specks, against the effect's 0.0051 and failed for
# the wrong reason). The dots themselves are proven from pixels by AssistantNeuralParticleFieldTest,
# which renders the field over a known background on both API levels.
adb shell wm density > "$out/density.txt"
python3 - "$out/particle-summary.txt" "$out/density.txt" <<'PYPARTICLES' | tee "$out/particle-band.txt"
import re, sys

summary = open(sys.argv[1], errors='replace').read()
density_text = open(sys.argv[2], errors='replace').read()
lines = [line for line in summary.splitlines() if 'NeuralParticles: painter=' in line]
if not lines:
    raise AssertionError('the particle field never logged a signature from its draw path')
last = lines[-1]
m = re.search(r'painter=(\w+) capsule=(\d+)x(\d+) radius=(\d+) band=(\d+)x(\d+) edge=(\w+)', last)
if not m:
    raise AssertionError('the particle marker is malformed: ' + last)
kind, cap_w, cap_h, radius, band_w, band_h, edge = m.groups()
cap_w, cap_h, radius = int(cap_w), int(cap_h), int(radius)
band_w, band_h = int(band_w), int(band_h)

densities = re.findall(r'(?:Physical|Override) density:\s*(\d+)', density_text)
if not densities:
    raise AssertionError('cannot establish display density')
density = int(densities[-1]) / 160.0

# The pill is a real capsule: wide, short, and rounded to half its height.
assert cap_w > 0 and cap_h > 0, f'the pill has no size: {cap_w}x{cap_h}'
assert cap_w > cap_h, f'the pill is not a capsule: {cap_w}x{cap_h}'
assert radius >= cap_h // 2 - 1, f'corner radius {radius}px does not round a {cap_h}px pill'
# The band is the window the field draws into; it must cover the pill it decorates.
assert band_w >= cap_w and band_h >= cap_h, (
    f'the particle band {band_w}x{band_h} does not cover the {cap_w}x{cap_h} pill')
# The soft edge is the point of the effect: blur on API 31+, the geometric feather below it.
assert edge in ('blur', 'gradient'), f'unknown edge treatment: {edge}'
print(f'Neural particle field: painter={kind} capsule={cap_w}x{cap_h} radius={radius} '
      f'band={band_w}x{band_h} edge={edge} density={density}')
print('Neural particle field passed')
PYPARTICLES

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

echo "gate passed" > "$out/gate-passed.txt"
