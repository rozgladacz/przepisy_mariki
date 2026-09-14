package pl.local.przepisy.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.util.UUID

class ImageStore(
    context: Context,
    client: OkHttpClient,
) {
    private val directory = File(context.filesDir, "recipe_images").apply { mkdirs() }
    private val http = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    suspend fun download(urlText: String, recipeId: String): String = withContext(Dispatchers.IO) {
        var url = urlText.toHttpUrlOrNull() ?: error("Nieprawidłowy adres zdjęcia")
        var response: okhttp3.Response? = null
        var redirectCount = 0
        while (response == null) {
            validateRemoteUrl(url.scheme, url.host)
            val fetched = http.newCall(
                Request.Builder()
                    .url(url)
                    .header("User-Agent", "Przepisy/1.0")
                    .build(),
            ).execute()
            if (fetched.isRedirect) {
                if (redirectCount == MAX_REDIRECTS) {
                    fetched.close()
                    error("Zdjęcie przekierowuje zbyt wiele razy")
                }
                val next = fetched.header("Location")?.let(url::resolve)
                fetched.close()
                url = next ?: error("Nieprawidłowe przekierowanie zdjęcia")
                redirectCount += 1
            } else {
                response = fetched
            }
        }
        response.use {
            require(it.isSuccessful) { "Błąd pobierania zdjęcia: HTTP ${it.code}" }
            val bytes = it.body.byteStream().readBounded(MAX_IMAGE_BYTES)
            saveValidated(bytes, recipeId)
        }
    }

    suspend fun saveImported(bytes: ByteArray, recipeId: String): String = withContext(Dispatchers.IO) {
        require(bytes.size <= MAX_IMAGE_BYTES) { "Zdjęcie w archiwum jest zbyt duże" }
        saveValidated(bytes, recipeId)
    }

    suspend fun delete(path: String?) = withContext(Dispatchers.IO) {
        safeFile(path)?.delete()
    }

    suspend fun cleanup(usedPaths: Set<String>) = withContext(Dispatchers.IO) {
        val canonicalUsed = usedPaths.mapNotNull { runCatching { File(it).canonicalPath }.getOrNull() }.toSet()
        directory.listFiles()?.forEach { file ->
            if (file.isFile && file.canonicalPath !in canonicalUsed) file.delete()
        }
    }

    private fun saveValidated(bytes: ByteArray, recipeId: String): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth in 1..MAX_SOURCE_DIMENSION && bounds.outHeight in 1..MAX_SOURCE_DIMENSION) {
            "Nieprawidłowe lub zbyt duże zdjęcie"
        }
        var sample = 1
        while (bounds.outWidth / sample > MAX_DECODE_DIMENSION || bounds.outHeight / sample > MAX_DECODE_DIMENSION) {
            sample *= 2
        }
        val bitmap = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: error("Nie udało się odczytać zdjęcia")
        val longest = maxOf(bitmap.width, bitmap.height)
        val outputBitmap = if (longest > MAX_OUTPUT_DIMENSION) {
            val ratio = MAX_OUTPUT_DIMENSION.toDouble() / longest
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt().coerceAtLeast(1),
                (bitmap.height * ratio).toInt().coerceAtLeast(1),
                true,
            ).also { if (it !== bitmap) bitmap.recycle() }
        } else bitmap

        val safeId = recipeId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val destination = File(directory, "${safeId}_${UUID.randomUUID()}.webp")
        val temporary = File(directory, ".${destination.name}.tmp")
        try {
            temporary.outputStream().buffered().use { stream ->
                check(outputBitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 85, stream)) {
                    "Nie udało się zapisać zdjęcia"
                }
            }
            check(temporary.renameTo(destination)) { "Nie udało się zatwierdzić zdjęcia" }
        } finally {
            outputBitmap.recycle()
            temporary.delete()
        }
        return destination.absolutePath
    }

    private fun safeFile(path: String?): File? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        val root = directory.canonicalFile
        val candidate = runCatching { file.canonicalFile }.getOrNull() ?: return null
        return candidate.takeIf { it.path.startsWith(root.path + File.separator) }
    }

    private fun isPublicHost(host: String): Boolean {
        if (host.equals("localhost", true) || host.endsWith(".localhost", true) || host.endsWith(".local", true)) return false
        return runCatching {
            InetAddress.getAllByName(host).all { address ->
                !(address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress)
            }
        }.getOrDefault(false)
    }

    private fun validateRemoteUrl(scheme: String, host: String) {
        require(scheme == "https") { "Zdjęcie musi używać HTTPS" }
        require(isPublicHost(host)) { "Niedozwolony adres zdjęcia" }
    }

    private fun java.io.InputStream.readBounded(limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            require(total <= limit) { "Zdjęcie jest zbyt duże" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    companion object {
        const val MAX_IMAGE_BYTES = 12 * 1024 * 1024
        private const val MAX_REDIRECTS = 5
        private const val MAX_SOURCE_DIMENSION = 12_000
        private const val MAX_DECODE_DIMENSION = 3_200
        private const val MAX_OUTPUT_DIMENSION = 1_600
    }
}
