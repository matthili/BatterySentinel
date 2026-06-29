package at.mafue.batterysentinel.util

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import at.mafue.batterysentinel.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Handles checking for updates on GitHub, downloading APKs, and triggering installation.
 *
 * Queries the GitHub Releases API for the latest release of matthili/BatterySentinel,
 * compares the tag_name against the current BuildConfig.VERSION_NAME, and if a newer
 * version is available, downloads the APK asset and launches the system installer.
 */
object GitHubUpdater {
    private const val TAG = "GitHubUpdater"
    private const val GITHUB_API_URL =
        "https://api.github.com/repos/matthili/BatterySentinel/releases/latest"
    private const val APK_FILE_NAME = "BatterySentinel-update.apk"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000

    /**
     * Represents the result of a version check against the GitHub Releases API.
     */
    sealed class UpdateResult {
        /** The app is already at the latest version. */
        data object UpToDate : UpdateResult()

        /** A newer version is available for download. */
        data class UpdateAvailable(
            val newVersion: String,
            val downloadUrl: String
        ) : UpdateResult()

        /** The check failed due to a network or parsing error. */
        data class Error(val message: String) : UpdateResult()
    }

    /**
     * Checks whether a newer release exists on GitHub.
     *
     * Compares the remote tag_name (stripped of a leading "v") against
     * [BuildConfig.VERSION_NAME]. Looks for the first asset whose name
     * ends with ".apk" in the release.
     */
    suspend fun checkForUpdate(): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val url = URL(GITHUB_API_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                // Avoid aggressive caching so the user always gets the latest info
                setRequestProperty("Cache-Control", "no-cache")
            }

            try {
                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext UpdateResult.Error("GitHub API returned HTTP $responseCode")
                }

                val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseBody)

                // tag_name is typically "v2.6" or "2.6"
                val tagName = json.getString("tag_name")
                val remoteVersion = tagName.removePrefix("v").trim()
                val currentVersion = BuildConfig.VERSION_NAME.trim()

                if (!isNewerVersion(remoteVersion, currentVersion)) {
                    return@withContext UpdateResult.UpToDate
                }

                // Find the APK download URL from the release assets
                val assets = json.getJSONArray("assets")
                var apkUrl: String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.getString("name")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.getString("browser_download_url")
                        break
                    }
                }

                if (apkUrl == null) {
                    return@withContext UpdateResult.Error(
                        "Release $remoteVersion found but contains no APK asset"
                    )
                }

                UpdateResult.UpdateAvailable(remoteVersion, apkUrl)
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Update check failed", e)
            UpdateResult.Error(e.localizedMessage ?: e.toString())
        }
    }

    /**
     * Downloads an APK from [url] into the app's cache directory.
     *
     * The file is stored under `cacheDir/updates/BatterySentinel-update.apk`.
     * Any previous download at that path is deleted first.
     *
     * @param onProgress optional callback receiving a value between 0f and 1f,
     *                   or -1f when the total size is unknown.
     * @return the local [File] containing the downloaded APK.
     */
    suspend fun downloadApk(
        context: Context,
        url: String,
        onProgress: ((Float) -> Unit)? = null
    ): File = withContext(Dispatchers.IO) {
        val updateDir = File(context.cacheDir, "updates")
        if (!updateDir.exists()) {
            updateDir.mkdirs()
        }

        val targetFile = File(updateDir, APK_FILE_NAME)
        // Clean up any leftover file from a previous download
        if (targetFile.exists()) {
            targetFile.delete()
        }

        val connection = URL(url).openConnection() as HttpURLConnection
        connection.apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
        }

        try {
            val totalBytes = connection.contentLength.toLong()
            var downloadedBytes = 0L

            connection.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        if (totalBytes > 0) {
                            onProgress?.invoke(downloadedBytes.toFloat() / totalBytes.toFloat())
                        } else {
                            onProgress?.invoke(-1f)
                        }
                    }
                }
            }

            Log.d(TAG, "APK downloaded: ${targetFile.absolutePath} ($downloadedBytes bytes)")
            targetFile
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Launches the Android Package Installer for the given APK [file].
     *
     * Uses [FileProvider] to create a secure `content://` URI. The caller
     * should check `packageManager.canRequestPackageInstalls()` before
     * calling this method and guide the user to grant the permission if needed.
     */
    fun installApk(context: Context, file: File) {
        val authority = "${context.packageName}.fileprovider"
        val apkUri = FileProvider.getUriForFile(context, authority, file)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Simple version comparison that works for dot-separated numeric versions
     * like "2.5" vs "2.6" or "2.5.1" vs "3.0".
     *
     * Returns true if [remote] is strictly newer than [current].
     */
    private fun isNewerVersion(remote: String, current: String): Boolean {
        val remoteParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(remoteParts.size, currentParts.size)

        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false // equal
    }
}
