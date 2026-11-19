package com.materialchat.assistant.voice

/** Install state of one neural voice model, as shown in settings and consulted by the speech runtime. */
sealed class SherpaModelState {
    data object NotDownloaded : SherpaModelState()
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long, val stage: Stage) : SherpaModelState() {
        enum class Stage { DOWNLOADING, VERIFYING, INSTALLING }
        val progress: Float get() = if (totalBytes <= 0L) 0f else (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
    }
    data object Installed : SherpaModelState()
    data class Failed(val message: String) : SherpaModelState()

    val isInstalled: Boolean get() = this is Installed
    val isBusy: Boolean get() = this is Downloading
}

/** Events produced by an install job; applied through [reduce] so the transitions are testable without I/O. */
sealed class SherpaInstallEvent {
    data class Start(val totalBytes: Long) : SherpaInstallEvent()
    data class Progress(val downloadedBytes: Long) : SherpaInstallEvent()
    data object Verifying : SherpaInstallEvent()
    data object Installing : SherpaInstallEvent()
    data object Installed : SherpaInstallEvent()
    data class Failed(val message: String) : SherpaInstallEvent()
    data object Cancelled : SherpaInstallEvent()
    data object Deleted : SherpaInstallEvent()
}

/**
 * Pure transition table. A job that was cancelled or deleted cannot resurrect a Downloading state,
 * and progress never runs backwards or past the total.
 */
fun SherpaModelState.reduce(event: SherpaInstallEvent): SherpaModelState = when (event) {
    is SherpaInstallEvent.Start ->
        if (this is SherpaModelState.Downloading) this
        else SherpaModelState.Downloading(0L, event.totalBytes, SherpaModelState.Downloading.Stage.DOWNLOADING)
    is SherpaInstallEvent.Progress ->
        if (this is SherpaModelState.Downloading && stage == SherpaModelState.Downloading.Stage.DOWNLOADING)
            copy(downloadedBytes = event.downloadedBytes.coerceIn(downloadedBytes, totalBytes))
        else this
    SherpaInstallEvent.Verifying ->
        if (this is SherpaModelState.Downloading) copy(downloadedBytes = totalBytes, stage = SherpaModelState.Downloading.Stage.VERIFYING)
        else this
    SherpaInstallEvent.Installing ->
        if (this is SherpaModelState.Downloading) copy(downloadedBytes = totalBytes, stage = SherpaModelState.Downloading.Stage.INSTALLING)
        else this
    SherpaInstallEvent.Installed -> SherpaModelState.Installed
    is SherpaInstallEvent.Failed -> if (this is SherpaModelState.Downloading) SherpaModelState.Failed(event.message) else this
    SherpaInstallEvent.Cancelled -> if (this is SherpaModelState.Downloading) SherpaModelState.NotDownloaded else this
    SherpaInstallEvent.Deleted -> SherpaModelState.NotDownloaded
}

/**
 * Fallback decision for the speech runtime: null means the neural model can be used, otherwise the
 * user-facing reason the Android engine is used instead.
 */
fun sherpaFallbackMessage(state: SherpaModelState, model: SherpaTtsModel): String? = when (state) {
    SherpaModelState.Installed -> null
    SherpaModelState.NotDownloaded -> VoiceCopy.neuralNotDownloaded(model.label)
    is SherpaModelState.Downloading -> VoiceCopy.neuralInstalling(model.label)
    is SherpaModelState.Failed -> VoiceCopy.neuralInstallFailed(model.label, state.message)
}
