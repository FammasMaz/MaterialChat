package com.materialchat.assistant.voice;

import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionService;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import java.util.ArrayList;

/**
 * Deterministic contract fixture, not an acoustic transcription engine or accuracy benchmark.
 *
 * Java, not Kotlin: the system binds this service inside the test package's own process, whose
 * classloader does not load the test APK's Kotlin stdlib (NoClassDefFoundError kotlin.jvm.internal.
 * Intrinsics on API 35). Plain Java avoids that dependency entirely.
 */
public final class ContractRecognitionService extends RecognitionService {
    @Override
    public void onStartListening(Intent intent, Callback listener) {
        boolean valid = RecognizerIntent.ACTION_RECOGNIZE_SPEECH.equals(intent.getAction())
                && "en-US".equals(intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
                && intent.getBooleanExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                && !intent.hasExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH)
                && !intent.hasExtra(
                        RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS);
        if (!valid) {
            listener.error(SpeechRecognizer.ERROR_CLIENT);
            return;
        }
        listener.readyForSpeech(new Bundle());
        ArrayList<String> results = new ArrayList<>();
        results.add("verified Android recognition request");
        Bundle bundle = new Bundle();
        bundle.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, results);
        listener.results(bundle);
    }

    @Override
    public void onStopListening(Callback listener) {
    }

    @Override
    public void onCancel(Callback listener) {
    }
}
