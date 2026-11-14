package com.materialchat.assistant.voice

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList

/** Streams one artifact to [target]; must honor cancellation and throw on HTTP or transport failure. */
fun interface SherpaArtifactFetcher {
    suspend fun fetch(url: String, expectedBytes: Long, target: File, onProgress: (Long) -> Unit)
}

/** A user-facing install failure; the message is shown verbatim in settings. */
class SherpaInstallException(message: String) : IOException(message)

/**
 * Owns `rootDir/<model id>/` for every [SherpaTtsModel]: download, verify, unpack, delete.
 * Downloads go to `.part` files and are unpacked into a staging directory that is renamed into place
 * only after every checksum and required file has been verified, so a model is either fully installed
 * or absent. Stale temporary files from a killed process are removed at construction.
 */
class SherpaModelStore(
    private val rootDir: File,
    private val fetcher: SherpaArtifactFetcher,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO
) {
    private val lock = Any()
    private val jobs = HashMap<SherpaTtsModel, Job>()
    private val removalListeners = CopyOnWriteArrayList<(SherpaTtsModel) -> Unit>()
    private val _states = MutableStateFlow(scan())
    val states: StateFlow<Map<SherpaTtsModel, SherpaModelState>> = _states.asStateFlow()

    init {
        scope.launch(io) { cleanupStale() }
    }

    fun state(model: SherpaTtsModel): SherpaModelState = _states.value[model] ?: SherpaModelState.NotDownloaded
    fun installDir(model: SherpaTtsModel): File = File(rootDir, model.id)
    /** Install directory when the model is fully installed, else null. */
    fun installedDir(model: SherpaTtsModel): File? = installDir(model).takeIf { state(model).isInstalled }

    /** Called after a model's files were deleted so a loaded synthesizer can be released. */
    fun onRemoved(listener: (SherpaTtsModel) -> Unit) { removalListeners += listener }

    /** Starts (or restarts after failure) the install; a no-op while installing or installed. */
    fun download(model: SherpaTtsModel) {
        synchronized(lock) {
            if (jobs[model]?.isActive == true || state(model).isInstalled) return
            jobs[model] = scope.launch(io) { install(model) }
        }
    }

    fun cancel(model: SherpaTtsModel) {
        synchronized(lock) { jobs[model]?.cancel() }
    }

    /** Cancels any install and removes the files; listeners are told so native handles are freed. */
    fun delete(model: SherpaTtsModel) {
        val job = synchronized(lock) { jobs[model]?.also { it.cancel() } }
        scope.launch(io) {
            job?.join()
            installDir(model).deleteRecursively()
            apply(model, SherpaInstallEvent.Deleted)
            removalListeners.forEach { it(model) }
        }
    }

    private fun apply(model: SherpaTtsModel, event: SherpaInstallEvent) {
        _states.update { it + (model to (it[model] ?: SherpaModelState.NotDownloaded).reduce(event)) }
    }

    private fun scan(): Map<SherpaTtsModel, SherpaModelState> = SherpaTtsModel.entries.associateWith { model ->
        if (isInstalled(model)) SherpaModelState.Installed else SherpaModelState.NotDownloaded
    }

    private fun isInstalled(model: SherpaTtsModel): Boolean {
        val dir = installDir(model)
        val manifest = File(dir, MANIFEST)
        return manifest.isFile && runCatching { manifest.readText() }.getOrNull() == model.manifest &&
            model.files.all { File(dir, it).isFile }
    }

    private fun cleanupStale() {
        rootDir.listFiles().orEmpty().forEach { file ->
            val temporary = file.name.endsWith(PART_SUFFIX) || file.name.endsWith(STAGING_SUFFIX)
            val knownInstall = SherpaTtsModel.entries.any { it.id == file.name }
            // A directory for a known model that is not a verified install is a leftover of an old catalog.
            val stale = temporary || (knownInstall && state(SherpaTtsModel.entries.first { it.id == file.name }) != SherpaModelState.Installed)
            if (stale) file.deleteRecursively()
        }
    }

    private suspend fun install(model: SherpaTtsModel) {
        apply(model, SherpaInstallEvent.Start(model.downloadBytes))
        val staging = File(rootDir, model.id + STAGING_SUFFIX)
        val parts = model.artifacts.indices.map { File(rootDir, "${model.id}.$it$PART_SUFFIX") }
        try {
            rootDir.mkdirs()
            val needed = model.downloadBytes * 2 + 50_000_000L
            if (rootDir.usableSpace in 1 until needed) {
                throw SherpaInstallException("Not enough free storage. About ${formatModelBytes(needed)} is needed.")
            }
            staging.deleteRecursively()
            check(staging.mkdirs()) { "Could not create the model directory." }
            var completed = 0L
            model.artifacts.forEachIndexed { index, artifact ->
                currentCoroutineContext().ensureActive()
                val part = parts[index]
                part.delete()
                val base = completed
                fetcher.fetch(artifact.url, artifact.sizeBytes, part) { apply(model, SherpaInstallEvent.Progress(base + it)) }
                if (part.length() != artifact.sizeBytes) {
                    throw SherpaInstallException("Download was incomplete. Check the connection and try again.")
                }
                completed += artifact.sizeBytes
            }
            apply(model, SherpaInstallEvent.Verifying)
            model.artifacts.forEachIndexed { index, artifact ->
                currentCoroutineContext().ensureActive()
                if (sha256(parts[index]) != artifact.sha256) {
                    throw SherpaInstallException("Downloaded file failed verification (checksum mismatch). Try again.")
                }
            }
            apply(model, SherpaInstallEvent.Installing)
            model.artifacts.forEachIndexed { index, artifact ->
                if (artifact.archive) unpack(model, parts[index], staging)
                else parts[index].copyTo(File(staging, artifact.fileName), overwrite = true)
            }
            val missing = model.files.filter { !File(staging, it).isFile }
            if (missing.isNotEmpty()) throw SherpaInstallException("Model package is missing ${missing.first()}.")
            File(staging, MANIFEST).writeText(model.manifest)
            currentCoroutineContext().ensureActive()
            val target = installDir(model)
            target.deleteRecursively()
            if (!staging.renameTo(target)) throw SherpaInstallException("Could not move the model into app storage.")
            apply(model, SherpaInstallEvent.Installed)
        } catch (cancelled: CancellationException) {
            apply(model, SherpaInstallEvent.Cancelled)
            throw cancelled
        } catch (failure: SherpaInstallException) {
            apply(model, SherpaInstallEvent.Failed(failure.message ?: "Model install failed."))
        } catch (failure: IOException) {
            apply(model, SherpaInstallEvent.Failed(describe(failure)))
        } catch (_: RuntimeException) {
            apply(model, SherpaInstallEvent.Failed("Model install failed unexpectedly. Try again."))
        } finally {
            parts.forEach { it.delete() }
            staging.deleteRecursively()
        }
    }

    private suspend fun unpack(model: SherpaTtsModel, archive: File, staging: File) {
        BZip2CompressorInputStream(BufferedInputStream(FileInputStream(archive), 1 shl 16)).use { bz2 ->
            TarArchiveInputStream(bz2).use { tar ->
                var entry = tar.nextEntry
                while (entry != null) {
                    currentCoroutineContext().ensureActive()
                    if (!entry.isDirectory) {
                        val relative = SherpaTtsModel.installPath(model, entry.name)
                        if (relative != null) {
                            val out = File(staging, relative)
                            out.parentFile?.mkdirs()
                            FileOutputStream(out).use { tar.copyTo(it, 1 shl 16) }
                        }
                    }
                    entry = tar.nextEntry
                }
            }
        }
    }

    private fun describe(failure: IOException): String = when (failure) {
        is UnknownHostException, is ConnectException, is SocketTimeoutException ->
            "No internet connection. Connect to a network and try again."
        else -> "Download failed. Check the connection and try again."
    }

    companion object {
        internal const val MANIFEST = "installed.manifest"
        private const val PART_SUFFIX = ".part"
        private const val STAGING_SUFFIX = ".installing"

        internal fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { input ->
                val buffer = ByteArray(1 shl 16)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
