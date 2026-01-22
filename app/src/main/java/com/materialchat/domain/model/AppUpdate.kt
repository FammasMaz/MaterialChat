package com.materialchat.domain.model

/**
 * Represents an available app update from GitHub Releases.
 *
 * @property versionName The version name (e.g., "1.2.0")
 * @property versionCode Numeric version code for comparison
 * @property releaseNotes Markdown-formatted release notes
 * @property downloadUrl Direct download URL for the APK asset
 * @property browserUrl URL to view the release in browser
 * @property assetSize Size of the APK file in bytes
 * @property publishedAt ISO 8601 timestamp of when the release was published
 */
data class AppUpdate(
    val versionName: String,
    val versionCode: Int,
    val releaseNotes: String,
    val downloadUrl: String,
    val browserUrl: String,
    val assetSize: Long,
    val publishedAt: String
)

/**
 * Represents the current state of the update system.
 */
sealed interface UpdateState {
    /**
     * Initial state, no update check has been performed.
     */
    data object Idle : UpdateState

    /**
     * Currently checking for updates.
     */
    data object Checking : UpdateState

    /**
     * An update is available for download.
     */
    data class Available(val update: AppUpdate) : UpdateState

    /**
     * The app is up to date.
     */
    data object UpToDate : UpdateState

    /**
     * Update is being downloaded.
     * @property progress Download progress from 0f to 1f
     * @property update The update being downloaded
     */
    data class Downloading(
        val progress: Float,
        val update: AppUpdate
    ) : UpdateState

    /**
     * Download complete, ready to install.
     * @property update The downloaded update
     * @property apkPath Path to the downloaded APK file
     */
    data class ReadyToInstall(
        val update: AppUpdate,
        val apkPath: String
    ) : UpdateState

    /**
     * Installation in progress.
     */
    data object Installing : UpdateState

    /**
     * An error occurred during the update process.
     * @property message Human-readable error message
     * @property canRetry Whether the operation can be retried
     */
    data class Error(
        val message: String,
        val canRetry: Boolean = true
    ) : UpdateState
}

/**
 * Extension to check if the banner should be shown for this state.
 */
val UpdateState.shouldShowBanner: Boolean
    get() = when (this) {
        is UpdateState.Available,
        is UpdateState.Downloading,
        is UpdateState.ReadyToInstall -> true
        else -> false
    }

/**
 * Extension to get the update info if available.
 */
val UpdateState.updateInfo: AppUpdate?
    get() = when (this) {
        is UpdateState.Available -> update
        is UpdateState.Downloading -> update
        is UpdateState.ReadyToInstall -> update
        else -> null
    }
