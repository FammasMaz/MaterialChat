package com.materialchat.assistant.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Nonblocking writes bound cancellation latency; each invocation owns and releases its track. */
class AudioTrackPcmPlayer : GeminiPcmPlayer {
    override suspend fun play(pcm: ByteArray, sampleRate: Int) = withContext(Dispatchers.IO) {
        require(sampleRate == 24000 && pcm.isNotEmpty() && pcm.size % 2 == 0)
        val minimum = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minimum > 0)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(maxOf(minimum, 8192)).build()
        try {
            check(track.state == AudioTrack.STATE_INITIALIZED)
            track.play()
            var offset = 0
            while (offset < pcm.size) {
                currentCoroutineContext().ensureActive()
                val written = track.write(pcm, offset, minOf(4096, pcm.size - offset), AudioTrack.WRITE_NON_BLOCKING)
                check(written >= 0) { "Audio output unavailable" }
                offset += written
                if (written == 0) delay(5)
            }
            while (track.playbackHeadPosition.toLong() < pcm.size / 2L) {
                currentCoroutineContext().ensureActive()
                delay(5)
            }
        } finally {
            try { track.pause(); track.flush() } finally { track.release() }
        }
    }
}
