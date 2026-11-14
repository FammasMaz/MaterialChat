package com.materialchat.assistant.voice;

import androidx.annotation.Keep;

import kotlin.jvm.functions.Function1;

/**
 * Audio callback handed to sherpa-onnx's native generator.
 *
 * <p>The JNI layer resolves {@code invoke} with the exact descriptor
 * {@code ([F)Ljava/lang/Integer;}. A Kotlin lambda compiles through invokedynamic to a class
 * that only has the erased {@code invoke(Object)Object}, so the native lookup fails and the
 * process aborts. This Java class declares the exact method; the keep rule stops R8 from
 * inlining it into the bridge.
 */
@Keep
public final class SherpaSampleCallback implements Function1<float[], Integer> {
    /** Returns true to continue generating, false to stop. */
    public interface Sink {
        boolean accept(float[] samples);
    }

    private final Sink sink;

    public SherpaSampleCallback(Sink sink) {
        this.sink = sink;
    }

    @Keep
    @Override
    public Integer invoke(float[] samples) {
        return sink.accept(samples) ? 1 : 0;
    }
}
