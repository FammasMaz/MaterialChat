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
    /**
     * Hold mode for the real-window gate (scripts/check-assistant-window.sh): set this global
     * setting to "1" and the fixture reports ready, then keeps the session open (a genuine
     * listening state through the production binding path) until it is stopped, which delivers
     * the transcript, or cancelled. RecognitionContractTest never sets it.
     */
    public static final String HOLD_SETTING = "materialchat_contract_recognizer_hold";

    private Callback held;

    private boolean holdMode() {
        return "1".equals(android.provider.Settings.Global.getString(
                getContentResolver(), HOLD_SETTING));
    }

    private static void deliver(Callback listener) throws android.os.RemoteException {
        ArrayList<String> results = new ArrayList<>();
        results.add("verified Android recognition request");
        Bundle bundle = new Bundle();
        bundle.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, results);
        listener.results(bundle);
    }

    @Override
    public void onStartListening(Intent intent, Callback listener) {
        if (holdMode()) {
            try {
                listener.readyForSpeech(new Bundle());
                held = listener;
            } catch (android.os.RemoteException ignored) {
            }
            return;
        }
        boolean valid = RecognizerIntent.ACTION_RECOGNIZE_SPEECH.equals(intent.getAction())
                && "en-US".equals(intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
                && intent.getBooleanExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                && !intent.hasExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH)
                && !intent.hasExtra(
                        RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS);
        if (!valid) {
            try {
                listener.error(SpeechRecognizer.ERROR_CLIENT);
            } catch (android.os.RemoteException ignored) {
            }
            return;
        }
        try {
            listener.readyForSpeech(new Bundle());
            deliver(listener);
        } catch (android.os.RemoteException e) {
            try {
                listener.error(SpeechRecognizer.ERROR_CLIENT);
            } catch (android.os.RemoteException ignored) {
            }
        }
    }

    @Override
    public void onStopListening(Callback listener) {
        Callback pending = held;
        held = null;
        if (pending == null) return;
        try {
            deliver(pending);
        } catch (android.os.RemoteException ignored) {
        }
    }

    @Override
    public void onCancel(Callback listener) {
        held = null;
    }
}
