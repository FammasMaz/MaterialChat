package com.materialchat.assistant.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Streams PCM chunks into one AudioTrack as they arrive; nonblocking writes bound cancellation latency. */
class AudioTrackSherpaPlayer : SherpaPcmSink {
    override suspend fun play(sampleRate: Int, first: ByteArray, rest: ReceiveChannel<ByteArray>) = withContext(Dispatchers.IO) {
        require(sampleRate in 8_000..48_000 && first.size % 2 == 0)
        val minimum = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minimum > 0)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(maxOf(minimum, sampleRate / 2)).build()
        try {
            check(track.state == AudioTrack.STATE_INITIALIZED)
            track.play()
            var frames = 0L
            suspend fun write(pcm: ByteArray) {
                var offset = 0
                while (offset < pcm.size) {
                    currentCoroutineContext().ensureActive()
                    val written = track.write(pcm, offset, minOf(4096, pcm.size - offset), AudioTrack.WRITE_NON_BLOCKING)
                    check(written >= 0) { "Audio output unavailable" }
                    offset += written
                    if (written == 0) delay(5)
                }
                frames += pcm.size / 2
            }
            write(first)
            for (chunk in rest) write(chunk)
            while ((track.playbackHeadPosition.toLong() and 0xffffffffL) < frames) {
                currentCoroutineContext().ensureActive()
                delay(5)
            }
        } finally {
            try { track.pause(); track.flush() } finally { track.release() }
        }
    }
}
