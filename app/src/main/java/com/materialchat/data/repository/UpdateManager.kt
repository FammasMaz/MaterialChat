package com.materialchat.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.materialchat.BuildConfig
import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.data.remote.api.GitHubReleaseApiClient
import com.materialchat.domain.model.AppUpdate
import com.materialchat.domain.model.UpdateState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the complete update lifecycle including:
 * - Checking for updates from GitHub
 * - Downloading APK files
 * - Installing updates via PackageInstaller
 *
 * Uses Ackpine for modern PackageInstaller API with coroutine support.
 */
@Singleton
class UpdateManager @Inject constructor(
    private val githubApiClient: GitHubReleaseApiClient,
    private val appPreferences: AppPreferences,
    private val okHttpClient: OkHttpClient,
    @ApplicationContext private val context: Context
) {
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val updateDir = File(context.cacheDir, "updates")

    // Current version info from BuildConfig
    private val currentVersionCode: Int
        get() = BuildConfig.VERSION_CODE
    private val currentVersionName: String
        get() = BuildConfig.VERSION_NAME

    /**
     * Checks for available updates from GitHub.
     *
     * @param force If true, ignores the last check time preference
     * @return The resulting UpdateState
     */
    suspend fun checkForUpdates(force: Boolean = false): UpdateState {
        // Check if we should skip this check based on timing
        if (!force && !shouldCheckForUpdates()) {
            return _updateState.value
        }

        _updateState.value = UpdateState.Checking

        return try {
            val result = githubApiClient.getLatestRelease()

            result.fold(
                onSuccess = { update ->
                    // Update the last check timestamp
                    appPreferences.setLastUpdateCheck(System.currentTimeMillis())

                    // Check if this version is newer
                    if (isNewerVersion(update)) {
                        // Check if user has skipped this version
                        val skippedVersion = appPreferences.skippedUpdateVersion.first()
                        if (skippedVersion == update.versionName) {
                            _updateState.value = UpdateState.UpToDate
                        } else {
                            _updateState.value = UpdateState.Available(update)
                        }
                    } else {
                        _updateState.value = UpdateState.UpToDate
                    }
                    _updateState.value
                },
                onFailure = { error ->
                    _updateState.value = UpdateState.Error(
                        message = error.message ?: "Failed to check for updates",
                        canRetry = true
                    )
                    _updateState.value
                }
            )
        } catch (e: Exception) {
            _updateState.value = UpdateState.Error(
                message = e.message ?: "Failed to check for updates",
                canRetry = true
            )
            _updateState.value
        }
    }

    /**
     * Downloads the update APK file.
     *
     * @param update The update to download
     */
    suspend fun downloadUpdate(update: AppUpdate) {
        _updateState.value = UpdateState.Downloading(progress = 0f, update = update)

        try {
            // Ensure update directory exists
            if (!updateDir.exists()) {
                updateDir.mkdirs()
            }

            // Clean up old APK files
            cleanupOldApks()

            val apkFile = File(updateDir, "MaterialChat-${update.versionName}.apk")

            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url(update.downloadUrl)
                    .build()

                val response = okHttpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    throw Exception("Download failed: HTTP ${response.code}")
                }

                val body = response.body ?: throw Exception("Empty response body")
                val contentLength = body.contentLength()

                body.byteStream().use { input ->
                    FileOutputStream(apkFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalBytesRead = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead

                            // Update progress
                            if (contentLength > 0) {
                                val progress = totalBytesRead.toFloat() / contentLength
                                _updateState.value = UpdateState.Downloading(
                                    progress = progress,
                                    update = update
                                )
                            }
                        }
                    }
                }
            }

            _updateState.value = UpdateState.ReadyToInstall(
                update = update,
                apkPath = apkFile.absolutePath
            )
        } catch (e: Exception) {
            _updateState.value = UpdateState.Error(
                message = e.message ?: "Download failed",
                canRetry = true
            )
        }
    }

    /**
     * Installs the downloaded update using Ackpine PackageInstaller.
     */
    suspend fun installUpdate() {
        val currentState = _updateState.value
        if (currentState !is UpdateState.ReadyToInstall) {
            return
        }

        _updateState.value = UpdateState.Installing

        try {
            val apkFile = File(currentState.apkPath)
            if (!apkFile.exists()) {
                _updateState.value = UpdateState.Error(
                    message = "APK file not found. Please download again.",
                    canRetry = false
                )
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()
            ) {
                openInstallPermissionSettings()
                _updateState.value = UpdateState.ReadyToInstall(
                    update = currentState.update,
                    apkPath = currentState.apkPath
                )
                return
            }

            // Open the platform installer immediately and keep the update ready.
            // If Android/Play Protect dismisses the installer, tapping Install again
            // reuses the same verified APK instead of doing nothing.
            launchSystemInstaller(apkFile)
            _updateState.value = UpdateState.ReadyToInstall(
                update = currentState.update,
                apkPath = currentState.apkPath
            )
        } catch (e: Exception) {
            _updateState.value = UpdateState.Error(
                message = e.message ?: "Installation failed",
                canRetry = true
            )
        }
    }

    private fun launchSystemInstaller(apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    private fun openInstallPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Cancels the current download.
     */
    fun cancelDownload() {
        val currentState = _updateState.value
        if (currentState is UpdateState.Downloading) {
            _updateState.value = UpdateState.Available(currentState.update)
        }
    }

    /**
     * Skips the currently available update version.
     * The user won't be notified about this version again.
     */
    suspend fun skipVersion() {
        val currentState = _updateState.value
        if (currentState is UpdateState.Available) {
            appPreferences.setSkippedUpdateVersion(currentState.update.versionName)
            _updateState.value = UpdateState.UpToDate
        }
    }

    /**
     * Dismisses the update banner without skipping the version.
     */
    fun dismissBanner() {
        val currentState = _updateState.value
        when (currentState) {
            is UpdateState.Available,
            is UpdateState.ReadyToInstall -> {
                _updateState.value = UpdateState.UpToDate
            }
            else -> { /* No action needed */ }
        }
    }

    /**
     * Resets the update state to idle.
     */
    fun reset() {
        _updateState.value = UpdateState.Idle
    }

    /**
     * Gets the current app version for display.
     */
    fun getCurrentVersion(): String = currentVersionName

    /**
     * Checks if the provided update is newer than the current version.
     * Compares semantic version codes to match the format used by GitHubReleaseApiClient.
     */
    private fun isNewerVersion(update: AppUpdate): Boolean {
        val currentParsed = parseSemanticVersionCode(currentVersionName)
        return update.versionCode > currentParsed
    }

    /**
     * Parses a semantic version string (e.g. "2.6.1") into a comparable integer.
     * Mirrors the format used by [GitHubReleaseApiClient.parseVersionCode].
     */
    private fun parseSemanticVersionCode(version: String): Int {
        val parts = version.split(".").mapNotNull { it.toIntOrNull() }
        return when (parts.size) {
            3 -> parts[0] * 1_000_000 + parts[1] * 1_000 + parts[2]
            2 -> parts[0] * 1_000_000 + parts[1] * 1_000
            1 -> parts[0] * 1_000_000
            else -> 0
        }
    }

    /**
     * Determines if we should check for updates based on preferences and timing.
     */
    private suspend fun shouldCheckForUpdates(): Boolean {
        val autoCheckEnabled = appPreferences.autoCheckUpdates.first()
        if (!autoCheckEnabled) {
            return false
        }

        val lastCheck = appPreferences.lastUpdateCheck.first()
        val now = System.currentTimeMillis()
        val oneDayMillis = 24 * 60 * 60 * 1000L

        return (now - lastCheck) > oneDayMillis
    }

    /**
     * Cleans up old downloaded APK files.
     */
    private fun cleanupOldApks() {
        updateDir.listFiles()?.forEach { file ->
            if (file.name.endsWith(".apk")) {
                file.delete()
            }
        }
    }
}
