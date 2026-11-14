package com.materialchat.assistant.voice

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.UnknownHostException

@OptIn(ExperimentalCoroutinesApi::class)
class SherpaModelStoreTest {
    @get:Rule val folder = TemporaryFolder()
    private val model = SherpaTtsModel.SUPERTONIC

    private fun installed(root: File, model: SherpaTtsModel) {
        val dir = File(root, model.id).apply { mkdirs() }
        model.files.forEach { File(dir, it).writeText("x") }
        File(dir, SherpaModelStore.MANIFEST).writeText(model.manifest)
    }

    @Test fun `scan recognises a verified install and ignores a stale one`() = runTest {
        val root = folder.newFolder("tts")
        installed(root, SherpaTtsModel.KOKORO)
        val stale = File(root, model.id).apply { mkdirs() }
        File(stale, "tts.json").writeText("x") // no manifest: a leftover from an old catalog
        File(root, "${model.id}.0.part").writeText("partial")
        val store = SherpaModelStore(root, { _, _, _, _ -> error("no network expected") }, this, coroutineContext[kotlinx.coroutines.CoroutineDispatcher]!!)
        assertEquals(SherpaModelState.Installed, store.state(SherpaTtsModel.KOKORO))
        assertEquals(SherpaModelState.NotDownloaded, store.state(model))
        assertNotNull(store.installedDir(SherpaTtsModel.KOKORO))
        assertNull(store.installedDir(model))
        advanceUntilIdle()
        assertFalse(stale.exists())
        assertFalse(File(root, "${model.id}.0.part").exists())
        assertTrue(File(root, SherpaTtsModel.KOKORO.id).isDirectory)
    }

    @Test fun `checksum mismatch fails with a user message and leaves nothing installed`() = runTest {
        val root = folder.newFolder("tts")
        val progress = mutableListOf<Long>()
        val store = SherpaModelStore(root, { _, expected, target, onProgress ->
            target.outputStream().use { out ->
                val chunk = ByteArray(1 shl 20)
                var written = 0L
                while (written < expected) {
                    val n = minOf(chunk.size.toLong(), expected - written).toInt()
                    out.write(chunk, 0, n); written += n; onProgress(written)
                }
            }
        }, this, coroutineContext[kotlinx.coroutines.CoroutineDispatcher]!!)
        store.states.value // force construction
        store.download(model)
        advanceUntilIdle()
        val state = store.state(model)
        assertTrue(state.toString(), state is SherpaModelState.Failed)
        assertTrue((state as SherpaModelState.Failed).message.contains("checksum"))
        assertFalse(store.installDir(model).exists())
        assertTrue(root.listFiles().orEmpty().none { it.name.endsWith(".part") || it.name.endsWith(".installing") })
        // A failed install may be retried.
        store.download(model)
        assertTrue(store.state(model) is SherpaModelState.Downloading)
        advanceUntilIdle()
    }

    @Test fun `incomplete download and no network produce specific messages`() = runTest {
        val root = folder.newFolder("tts")
        val short = SherpaModelStore(root, { _, _, target, _ -> target.writeText("too short") }, this, coroutineContext[kotlinx.coroutines.CoroutineDispatcher]!!)
        short.download(model); advanceUntilIdle()
        assertTrue((short.state(model) as SherpaModelState.Failed).message.contains("incomplete"))

        val offline = SherpaModelStore(folder.newFolder("tts2"), { _, _, _, _ -> throw UnknownHostException("github.com") }, this, coroutineContext[kotlinx.coroutines.CoroutineDispatcher]!!)
        offline.download(model); advanceUntilIdle()
        assertTrue((offline.state(model) as SherpaModelState.Failed).message.contains("No internet"))

        val http = SherpaModelStore(folder.newFolder("tts3"), { _, _, _, _ -> throw SherpaInstallException("Download failed (HTTP 503). Try again later.") }, this, coroutineContext[kotlinx.coroutines.CoroutineDispatcher]!!)
        http.download(model); advanceUntilIdle()
        assertEquals("Download failed (HTTP 503). Try again later.", (http.state(model) as SherpaModelState.Failed).message)
    }

    @Test fun `cancel returns to not downloaded and removes partial files`() = runTest {
        val root = folder.newFolder("tts")
        val store = SherpaModelStore(root, { _, _, target, onProgress ->
            target.writeText("partial")
            onProgress(7L)
            awaitCancellation() // never finishes unless cancelled
        }, this, coroutineContext[kotlinx.coroutines.CoroutineDispatcher]!!)
        store.download(model)
        advanceUntilIdle()
        val busy = store.state(model)
        assertTrue(busy.toString(), busy is SherpaModelState.Downloading)
        assertEquals(7L, (busy as SherpaModelState.Downloading).downloadedBytes)
        store.cancel(model)
        advanceUntilIdle()
        assertEquals(SherpaModelState.NotDownloaded, store.state(model))
        assertTrue(root.listFiles().orEmpty().none { it.name.endsWith(".part") || it.name.endsWith(".installing") })
    }

    @Test fun `delete removes an install and notifies listeners`() = runTest {
        val root = folder.newFolder("tts")
        installed(root, model)
        val store = SherpaModelStore(root, { _, _, _, _ -> error("unused") }, this, coroutineContext[kotlinx.coroutines.CoroutineDispatcher]!!)
        val removed = mutableListOf<SherpaTtsModel>()
        store.onRemoved { removed += it }
        assertEquals(SherpaModelState.Installed, store.state(model))
        store.download(model) // no-op while installed
        assertEquals(SherpaModelState.Installed, store.state(model))
        store.delete(model)
        advanceUntilIdle()
        assertEquals(SherpaModelState.NotDownloaded, store.state(model))
        assertFalse(store.installDir(model).exists())
        assertEquals(listOf(model), removed)
    }
}
