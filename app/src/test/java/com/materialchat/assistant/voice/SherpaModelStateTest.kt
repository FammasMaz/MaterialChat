package com.materialchat.assistant.voice

import com.materialchat.assistant.voice.SherpaInstallEvent as E
import com.materialchat.assistant.voice.SherpaModelState as S
import org.junit.Assert.*
import org.junit.Test

class SherpaModelStateTest {
    private val downloading = S.Downloading(0L, 100L, S.Downloading.Stage.DOWNLOADING)

    @Test fun `start begins a download and is idempotent while downloading`() {
        assertEquals(downloading, S.NotDownloaded.reduce(E.Start(100L)))
        val progressed = downloading.reduce(E.Progress(40L))
        assertEquals(progressed, progressed.reduce(E.Start(999L)))
        assertEquals(downloading, S.Failed("x").reduce(E.Start(100L)))
    }

    @Test fun `progress is monotonic, capped and only counted while downloading`() {
        val forty = downloading.reduce(E.Progress(40L)) as S.Downloading
        assertEquals(40L, forty.downloadedBytes)
        assertEquals(0.4f, forty.progress, 1e-6f)
        assertEquals(40L, (forty.reduce(E.Progress(10L)) as S.Downloading).downloadedBytes)
        assertEquals(100L, (forty.reduce(E.Progress(500L)) as S.Downloading).downloadedBytes)
        val verifying = forty.reduce(E.Verifying) as S.Downloading
        assertEquals(S.Downloading.Stage.VERIFYING, verifying.stage)
        assertEquals(100L, verifying.downloadedBytes)
        assertEquals(verifying, verifying.reduce(E.Progress(50L)))
        assertEquals(S.NotDownloaded, S.NotDownloaded.reduce(E.Progress(50L)))
        assertEquals(0f, S.Downloading(0L, 0L, S.Downloading.Stage.DOWNLOADING).progress, 0f)
    }

    @Test fun `verify then install then installed`() {
        val installing = downloading.reduce(E.Verifying).reduce(E.Installing) as S.Downloading
        assertEquals(S.Downloading.Stage.INSTALLING, installing.stage)
        assertEquals(S.Installed, installing.reduce(E.Installed))
        assertTrue(installing.isBusy)
        assertTrue(S.Installed.isInstalled)
    }

    @Test fun `cancel and failure only leave a download in progress`() {
        assertEquals(S.NotDownloaded, downloading.reduce(E.Cancelled))
        assertEquals(S.Failed("boom"), downloading.reduce(E.Failed("boom")))
        assertEquals(S.Installed, S.Installed.reduce(E.Cancelled))
        assertEquals(S.Installed, S.Installed.reduce(E.Failed("late")))
        assertEquals(S.NotDownloaded, S.NotDownloaded.reduce(E.Failed("late")))
        // A late event from a cancelled job cannot resurrect progress.
        assertEquals(S.NotDownloaded, downloading.reduce(E.Cancelled).reduce(E.Progress(10L)))
    }

    @Test fun `delete clears every state`() {
        for (state in listOf(S.NotDownloaded, downloading, S.Installed, S.Failed("x"))) {
            assertEquals(S.NotDownloaded, state.reduce(E.Deleted))
        }
    }

    @Test fun `fallback decision explains why the android voice is used`() {
        val model = SherpaTtsModel.SUPERTONIC
        assertNull(sherpaFallbackMessage(S.Installed, model))
        assertTrue(sherpaFallbackMessage(S.NotDownloaded, model)!!.contains("isn't downloaded"))
        assertTrue(sherpaFallbackMessage(downloading, model)!!.contains("still installing"))
        val failed = sherpaFallbackMessage(S.Failed("checksum mismatch."), model)!!
        assertTrue(failed.contains("checksum mismatch"))
        assertTrue(failed.contains(model.label))
    }
}
