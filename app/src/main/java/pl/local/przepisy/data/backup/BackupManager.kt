package pl.local.przepisy.data.backup

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import pl.local.przepisy.data.media.ImageStore
import pl.local.przepisy.data.repository.RecipeRepository
import pl.local.przepisy.domain.model.Recipe
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
data class BackupRecipe(
    val recipe: Recipe,
    val imageEntry: String? = null,
)

@Serializable
data class BackupEnvelope(
    val schemaVersion: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val recipes: List<BackupRecipe>,
)

data class BackupResult(val recipeCount: Int)

class BackupManager(
    private val contentResolver: ContentResolver,
    private val repository: RecipeRepository,
    private val imageStore: ImageStore,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun exportTo(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        val output = contentResolver.openOutputStream(uri, "w") ?: error("Nie można otworzyć pliku do zapisu")
        output.use { exportToStream(it) }
    }

    internal suspend fun exportTo(output: OutputStream): BackupResult = withContext(Dispatchers.IO) {
        output.use { exportToStream(it) }
    }

    private suspend fun exportToStream(output: OutputStream): BackupResult {
        val recipes = repository.exportAll()
        val backupRecipes = recipes.map { recipe ->
            val image = recipe.imagePath?.let(::File)?.takeIf { it.isFile }
            BackupRecipe(
                recipe = recipe.copy(imagePath = null),
                imageEntry = image?.let { "media/${recipe.id}.webp" },
            )
        }
        val envelope = BackupEnvelope(recipes = backupRecipes)
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            zip.write(json.encodeToString(envelope).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            backupRecipes.forEach { backup ->
                val entry = backup.imageEntry ?: return@forEach
                val source = recipes.first { it.id == backup.recipe.id }.imagePath?.let(::File) ?: return@forEach
                if (!source.isFile) return@forEach
                zip.putNextEntry(ZipEntry(entry))
                source.inputStream().buffered().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return BackupResult(recipes.size)
    }

    suspend fun importFrom(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        val input = contentResolver.openInputStream(uri) ?: error("Nie można otworzyć archiwum")
        input.use { importFromStream(it) }
    }

    internal suspend fun importFrom(input: InputStream): BackupResult = withContext(Dispatchers.IO) {
        input.use { importFromStream(it) }
    }

    private suspend fun importFromStream(input: InputStream): BackupResult {
        val entries = readAndValidateArchive(input)
        val manifestBytes = entries[MANIFEST_ENTRY] ?: error("Archiwum nie zawiera manifest.json")
        require(manifestBytes.size <= MAX_MANIFEST_BYTES) { "Manifest jest zbyt duży" }
        val envelope = json.decodeFromString<BackupEnvelope>(manifestBytes.toString(Charsets.UTF_8))
        require(envelope.schemaVersion == 1) { "Nieobsługiwana wersja archiwum: ${envelope.schemaVersion}" }
        require(envelope.recipes.size <= MAX_RECIPES) { "Archiwum zawiera zbyt wiele przepisów" }
        envelope.recipes.forEach(::validateRecipe)
        require(envelope.recipes.map { it.recipe.id }.distinct().size == envelope.recipes.size) {
            "Archiwum zawiera powielone ID przepisów"
        }
        require(envelope.recipes.map { it.recipe.sourceUrl }.distinct().size == envelope.recipes.size) {
            "Archiwum zawiera powielone adresy źródłowe"
        }

        val stagedPaths = mutableListOf<String>()
        try {
            val recipes = envelope.recipes.map { backup ->
                val bytes = backup.imageEntry?.let { entry ->
                    require(isSafeEntry(entry)) { "Niebezpieczna ścieżka zdjęcia" }
                    entries[entry] ?: error("Brak zdjęcia $entry")
                }
                val imagePath = bytes?.let {
                    imageStore.saveImported(it, backup.recipe.id).also(stagedPaths::add)
                }
                backup.recipe.copy(imagePath = imagePath)
            }
            repository.mergeImported(recipes)
            // Porządki po zatwierdzeniu danych są best-effort: ich błąd nie może
            // zmienić udanego, atomowego scalenia w pozornie nieudany import.
            runCatching { imageStore.cleanup(repository.imagePaths()) }
            return BackupResult(recipes.size)
        } catch (error: Throwable) {
            stagedPaths.forEach { imageStore.delete(it) }
            throw error
        }
    }

    private fun readAndValidateArchive(input: InputStream): Map<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        var total = 0L
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory) { "Archiwum zawiera nieoczekiwany katalog" }
                require(isSafeEntry(entry.name)) { "Archiwum zawiera niebezpieczną ścieżkę" }
                require(entry.name !in entries) { "Archiwum zawiera powielony plik" }
                val limit = if (entry.name == MANIFEST_ENTRY) MAX_MANIFEST_BYTES else ImageStore.MAX_IMAGE_BYTES
                val bytes = zip.readBounded(limit)
                total += bytes.size
                require(total <= MAX_ARCHIVE_BYTES) { "Archiwum jest zbyt duże" }
                entries[entry.name] = bytes
                zip.closeEntry()
            }
        }
        require(entries.keys.all { it == MANIFEST_ENTRY || it.startsWith("media/") }) {
            "Archiwum zawiera nieobsługiwane pliki"
        }
        return entries
    }

    private fun validateRecipe(backup: BackupRecipe) {
        val recipe = backup.recipe
        require(recipe.id.isNotBlank() && recipe.title.isNotBlank()) { "Przepis w archiwum nie ma tytułu lub ID" }
        require(recipe.title.length <= MAX_TEXT_LENGTH && recipe.description.length <= MAX_DESCRIPTION_LENGTH) {
            "Przepis zawiera zbyt długi tekst"
        }
        val uri = runCatching { java.net.URI(recipe.sourceUrl) }.getOrNull()
        require(uri?.scheme == "https") { "Przepis ma nieprawidłowy adres źródłowy" }
        val host = uri.host?.lowercase()?.removePrefix("www.")
        require(host in setOf("mojewypieki.com", "alaantkoweblw.pl", "rozkoszny.pl", "aniagotuje.pl")) {
            "Archiwum zawiera nieobsługiwane źródło"
        }
        require(recipe.ingredientSections.any { it.lines.isNotEmpty() }) { "Przepis nie ma składników" }
        require(recipe.instructionSections.any { it.steps.isNotEmpty() }) { "Przepis nie ma instrukcji" }
        require(recipe.tags.size <= MAX_TAGS_PER_RECIPE) { "Przepis ma zbyt wiele tagów" }
        require(recipe.tags.all { it.isNotBlank() && it.length <= MAX_TAG_LENGTH }) {
            "Przepis zawiera nieprawidłowy tag"
        }
        backup.imageEntry?.let { entry ->
            require(entry.startsWith("media/") && isSafeEntry(entry)) { "Nieprawidłowa ścieżka zdjęcia" }
        }
        recipe.ingredientSections.flatMap { it.lines }.forEach { line ->
            require(line.rawText.length <= MAX_TEXT_LENGTH && line.body.length <= MAX_TEXT_LENGTH) {
                "Składnik zawiera zbyt długi tekst"
            }
            require(line.quantityMin == null || line.quantityMin.toBigDecimalOrNull() != null) {
                "Składnik zawiera nieprawidłową ilość"
            }
            require(line.quantityMax == null || line.quantityMax.toBigDecimalOrNull() != null) {
                "Składnik zawiera nieprawidłowy zakres"
            }
        }
        require(recipe.instructionSections.flatMap { it.steps }.all { it.length <= MAX_DESCRIPTION_LENGTH }) {
            "Krok instrukcji jest zbyt długi"
        }
    }

    private fun isSafeEntry(name: String): Boolean {
        if (name.isBlank() || name.startsWith('/') || name.startsWith('\\')) return false
        return name.replace('\\', '/').split('/').none { it == ".." || it.isBlank() }
    }

    private fun java.io.InputStream.readBounded(limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            require(total <= limit) { "Plik w archiwum jest zbyt duży" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    companion object {
        private const val MANIFEST_ENTRY = "manifest.json"
        private const val MAX_MANIFEST_BYTES = 5 * 1024 * 1024
        private const val MAX_ARCHIVE_BYTES = 100L * 1024L * 1024L
        private const val MAX_RECIPES = 10_000
        private const val MAX_TEXT_LENGTH = 10_000
        private const val MAX_DESCRIPTION_LENGTH = 100_000
        private const val MAX_TAGS_PER_RECIPE = 100
        private const val MAX_TAG_LENGTH = 80
    }
}
