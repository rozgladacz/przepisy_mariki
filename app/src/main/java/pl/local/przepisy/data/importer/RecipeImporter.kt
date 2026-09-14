package pl.local.przepisy.data.importer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import pl.local.przepisy.domain.model.RecipeDraft
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class RecipeImporter(
    private val adapters: List<RecipeSourceAdapter>,
    client: OkHttpClient,
) {
    private val http = client.newBuilder()
        .followRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun import(urlText: String): RecipeDraft = withContext(Dispatchers.IO) {
        var url = urlText.trim().toHttpUrlOrNull()
            ?: throw RecipeImportException("Podaj pełny adres HTTPS przepisu.")
        validateUrl(url)

        var response: Response? = null
        var redirectCount = 0
        while (response == null) {
            val current = http.newCall(
                Request.Builder()
                    .url(url)
                    .header("User-Agent", "Przepisy/1.0 (private Android recipe importer)")
                    .header("Accept", "text/html,application/xhtml+xml")
                    .build(),
            ).execute()
            if (current.isRedirect) {
                if (redirectCount == MAX_REDIRECTS) {
                    current.close()
                    throw RecipeImportException("Strona przekierowuje zbyt wiele razy.")
                }
                val location = current.header("Location")
                val next = location?.let(url::resolve)
                current.close()
                url = next ?: throw RecipeImportException("Strona zwróciła nieprawidłowe przekierowanie.")
                validateUrl(url)
                redirectCount += 1
            } else {
                response = current
            }
        }

        val finalResponse = response
        finalResponse.use { fetched ->
            if (!fetched.isSuccessful) throw RecipeImportException("Serwis zwrócił błąd HTTP ${fetched.code}.")
            val contentType = fetched.body.contentType()?.toString().orEmpty()
            if (contentType.isNotBlank() && "html" !in contentType.lowercase()) {
                throw RecipeImportException("Podany adres nie prowadzi do strony HTML.")
            }
            val html = fetched.body.byteStream().readBounded(MAX_HTML_BYTES).toString(Charsets.UTF_8)
            val document = Jsoup.parse(html, url.toString())
            val canonical = canonicalUrl(document.selectFirst("link[rel=canonical]")?.attr("href"), url)
            val adapter = adapters.firstOrNull { it.supports(canonical.host) }
                ?: throw RecipeImportException("Ten serwis nie jest obsługiwany.")
            val draft = adapter.parse(document, canonical.toString())
                ?: throw RecipeImportException("Nie udało się rozpoznać przepisu. Układ strony mógł się zmienić.")
            if (draft.title.isBlank() || draft.ingredientSections.none { it.lines.isNotEmpty() } ||
                draft.instructionSections.none { it.steps.isNotEmpty() }
            ) {
                throw RecipeImportException("Strona nie zawiera kompletnego przepisu.")
            }
            draft
        }
    }

    private fun canonicalUrl(value: String?, fallback: HttpUrl): HttpUrl {
        val candidate = value?.let { fallback.resolve(it) } ?: fallback
        validateUrl(candidate)
        return candidate.newBuilder().fragment(null).build()
    }

    private fun validateUrl(url: HttpUrl) {
        if (url.scheme != "https") throw RecipeImportException("Dozwolone są wyłącznie adresy HTTPS.")
        if (adapters.none { it.supports(url.host) }) {
            throw RecipeImportException(
                "Obsługiwane są tylko mojewypieki.com, alaantkoweblw.pl, rozkoszny.pl i aniagotuje.pl.",
            )
        }
    }

    private fun java.io.InputStream.readBounded(limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) throw RecipeImportException("Strona jest zbyt duża do bezpiecznego importu.")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    companion object {
        private const val MAX_REDIRECTS = 5
        private const val MAX_HTML_BYTES = 5 * 1024 * 1024
    }
}
