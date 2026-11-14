package com.materialchat.assistant.voice

import org.junit.Assert.*
import org.junit.Test

class SherpaTtsCatalogTest {
    @Test fun `default model is Supertonic and unknown ids fall back to it`() {
        assertEquals(SherpaTtsModel.SUPERTONIC, SherpaTtsModel.DEFAULT)
        assertEquals(SherpaTtsModel.SUPERTONIC, SherpaTtsModel.fromId(null))
        assertEquals(SherpaTtsModel.SUPERTONIC, SherpaTtsModel.fromId(""))
        assertEquals(SherpaTtsModel.SUPERTONIC, SherpaTtsModel.fromId("nope"))
        assertEquals(SherpaTtsModel.KOKORO, SherpaTtsModel.fromId(" kokoro-int8-en-v0_19 "))
    }

    @Test fun `every artifact is https with a 64 hex checksum and positive size`() {
        for (model in SherpaTtsModel.entries) {
            assertTrue(model.artifacts.isNotEmpty())
            for (artifact in model.artifacts) {
                assertTrue(artifact.url, artifact.url.startsWith("https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/"))
                assertTrue(artifact.sha256, artifact.sha256.matches(Regex("[0-9a-f]{64}")))
                assertTrue(artifact.sizeBytes > 0)
                assertEquals(artifact.url.substringAfterLast('/'), artifact.fileName)
            }
            assertEquals(model.artifacts.sumOf { it.sizeBytes }, model.downloadBytes)
            assertTrue(model.files.isNotEmpty())
        }
        assertEquals(84_692_981L, SherpaTtsModel.SUPERTONIC.downloadBytes)
        assertEquals(103_248_205L + 6_305_773L, SherpaTtsModel.KOKORO.downloadBytes)
        assertTrue(SherpaTtsModel.KOKORO.slowerThanRealTime)
        assertFalse(SherpaTtsModel.SUPERTONIC.slowerThanRealTime)
    }

    @Test fun `manifest changes whenever a checksum changes`() {
        val manifest = SherpaTtsModel.KOKORO.manifest
        assertTrue(manifest.contains(SherpaTtsModel.KOKORO.artifacts[0].sha256))
        assertTrue(manifest.contains("lexicon-en-us.txt"))
        assertNotEquals(SherpaTtsModel.SUPERTONIC.manifest, manifest)
    }

    @Test fun `installPath keeps required files and drops unused or unsafe entries`() {
        val model = SherpaTtsModel.KOKORO
        assertEquals("model.int8.onnx", SherpaTtsModel.installPath(model, "kokoro-int8-en-v0_19/model.int8.onnx"))
        assertEquals("voices.bin", SherpaTtsModel.installPath(model, "./kokoro-int8-en-v0_19/voices.bin"))
        assertEquals("LICENSE", SherpaTtsModel.installPath(model, "kokoro-int8-en-v0_19/LICENSE"))
        assertNull(SherpaTtsModel.installPath(model, "kokoro-int8-en-v0_19/espeak-ng-data/en_dict"))
        assertNull(SherpaTtsModel.installPath(model, "other-dir/model.int8.onnx"))
        assertNull(SherpaTtsModel.installPath(model, "kokoro-int8-en-v0_19/../model.int8.onnx"))
        assertNull(SherpaTtsModel.installPath(model, "kokoro-int8-en-v0_19//model.int8.onnx"))
        assertNull(SherpaTtsModel.installPath(model, "kokoro-int8-en-v0_19/"))
        assertEquals("tts.json", SherpaTtsModel.installPath(SherpaTtsModel.SUPERTONIC, "sherpa-onnx-supertonic-tts-int8-2026-03-06/tts.json"))
    }

    @Test fun `formatModelBytes rounds to readable units`() {
        assertEquals("85 MB", formatModelBytes(84_692_981L))
        assertEquals("110 MB", formatModelBytes(109_553_978L))
        assertEquals("1.5 GB", formatModelBytes(1_500_000_000L))
        assertEquals("6 kB", formatModelBytes(6_305L))
        assertEquals("12 B", formatModelBytes(12L))
    }

    @Test fun `supertonic language maps tags onto supported set`() {
        assertEquals("en", supertonicLanguage("en-US"))
        assertEquals("ko", supertonicLanguage("ko-KR"))
        assertEquals("pt", supertonicLanguage("pt-BR"))
        assertEquals("en", supertonicLanguage("de-DE"))
        assertEquals("fr", supertonicLanguage(null, java.util.Locale.FRANCE))
        assertEquals("en", supertonicLanguage(" ", java.util.Locale.JAPAN))
    }

    @Test fun `pcm16 is little endian and clamps`() {
        val out = pcm16(floatArrayOf(0f, 1f, -1f, 2f, 0.5f))
        assertEquals(10, out.size)
        assertEquals(0, out[0].toInt()); assertEquals(0, out[1].toInt())
        assertEquals(0xff.toByte(), out[2]); assertEquals(0x7f.toByte(), out[3])
        assertEquals(0x01.toByte(), out[4]); assertEquals(0x80.toByte(), out[5])
        assertEquals(0xff.toByte(), out[6]); assertEquals(0x7f.toByte(), out[7])
        val half = (out[8].toInt() and 0xff) or (out[9].toInt() shl 8)
        assertEquals(16383, half)
    }
}
