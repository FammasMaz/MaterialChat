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
