#!/usr/bin/env bash
# Runs the instrumented suite the release gate cares about.
#
# The emulator runner occasionally loses adb right after the last test, which fails the
# Gradle task even though every test passed. One recovery attempt is cheap; whether the
# run is acceptable is decided from the test XML afterwards, not from this exit code.
set -uo pipefail

CLASSES=com.materialchat.util.AttachmentImporterTest,com.materialchat.util.GeneratedImageActionsTest,com.materialchat.data.repository.ConversationAttachmentCleanupTest,com.materialchat.ui.screens.chat.ChatViewModelAttachmentCleanupTest,com.materialchat.assistant.service.MaterialChatVoiceInteractionSessionTest,com.materialchat.assistant.voice.RecognitionContractTest,com.materialchat.assistant.ui.AssistantNeuralParticleFieldTest,com.materialchat.assistant.voice.SherpaNeuralSpeechTest,com.materialchat.assistant.attach.AssistantAttachmentDeliveryTest,com.materialchat.util.CameraCaptureTest,com.materialchat.ui.screens.chat.components.MessageBubbleTapThroughScrollTest,com.materialchat.ui.screens.chat.MessageListMemoryChipTest

run_connected() {
  ./gradlew :app:connectedGithubDebugAndroidTest --no-daemon --max-workers=2 \
    -Pkotlin.compiler.execution.strategy=in-process \
    -Dorg.gradle.jvmargs=-Xmx2g \
    -Pandroid.testInstrumentationRunnerArguments.class="$CLASSES"
}

if run_connected; then
  exit 0
fi

echo 'connected run failed; restarting adb and retrying once'
adb kill-server || true
sleep 5
adb start-server || true
sleep 5
run_connected
