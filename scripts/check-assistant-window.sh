#!/usr/bin/env bash
set -euo pipefail
out="${1:-test-evidence/assistant}"
mkdir -p "$out"
component=com.materialchat/com.materialchat.assistant.service.MaterialChatVoiceInteractionService
adb shell settings put secure voice_interaction_service "$component"
adb shell settings put secure assistant "$component"
adb logcat -c
adb shell input keyevent KEYCODE_HOME
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
