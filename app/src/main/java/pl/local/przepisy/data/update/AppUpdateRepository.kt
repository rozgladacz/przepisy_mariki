package pl.local.przepisy.data.update

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import pl.local.przepisy.BuildConfig

@Serializable
data class AppUpdate(
    val schemaVersion: Int,
    val applicationId: String,
    val versionCode: Long,
    val versionName: String,
    val minSdk: Int,
    val apkUrl: String,
    val sha256: String,
    val notes: String,
)

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object Current : UpdateUiState
    data object NotPublished : UpdateUiState
    data object Incompatible : UpdateUiState
    data class Available(val update: AppUpdate) : UpdateUiState
    data object Error : UpdateUiState
}

class AppUpdateRepository(client: OkHttpClient) {
    private val http = client.newBuilder().callTimeout(15, TimeUnit.SECONDS).build()

    suspend fun check(): UpdateUiState = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://github.com/${BuildConfig.UPDATE_REPOSITORY}/releases/latest/download/update.json")
            .header("Cache-Control", "no-cache")
            .build()
        http.newCall(request).execute().use { response ->
            if (response.code == 404) return@withContext UpdateUiState.NotPublished
            if (!response.isSuccessful) throw IOException("Update HTTP ${response.code}")
            val body = response.body
            val bytes = body.byteStream().use { it.readNBytes(65_537) }
            require(bytes.size <= 65_536) { "Update metadata too large" }
            evaluateUpdate(
                bytes.toString(Charsets.UTF_8), BuildConfig.UPDATE_REPOSITORY,
                BuildConfig.VERSION_CODE.toLong(), android.os.Build.VERSION.SDK_INT,
            )
        }
    }
}

private val updateJson = Json { ignoreUnknownKeys = true }

internal fun evaluateUpdate(
    metadata: String,
    repository: String,
    installedVersion: Long,
    sdk: Int,
): UpdateUiState {
    val update = updateJson.decodeFromString<AppUpdate>(metadata)
    require(update.schemaVersion == 1 && update.applicationId == "pl.local.przepisy")
    require(update.versionCode > 0 && update.minSdk > 0)
    require(Regex("[0-9]+\\.[0-9]+\\.[0-9]+").matches(update.versionName))
    require(Regex("[a-fA-F0-9]{64}").matches(update.sha256))
    require(update.notes.length <= 8_000)
    require(update.apkUrl == "https://github.com/$repository/releases/download/v${update.versionName}/Przepisy.apk")
    return when {
        update.versionCode <= installedVersion -> UpdateUiState.Current
        update.minSdk > sdk -> UpdateUiState.Incompatible
        else -> UpdateUiState.Available(update)
    }
}
