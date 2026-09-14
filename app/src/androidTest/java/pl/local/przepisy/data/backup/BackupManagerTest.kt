package pl.local.przepisy.data.backup

import android.content.Context
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import pl.local.przepisy.data.local.AppDatabase
import pl.local.przepisy.data.media.ImageStore
import pl.local.przepisy.data.repository.OfflineRecipeRepository
import pl.local.przepisy.domain.model.IngredientLine
import pl.local.przepisy.domain.model.IngredientSection
import pl.local.przepisy.domain.model.InstructionSection
import pl.local.przepisy.domain.model.Recipe
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class BackupManagerTest {
    private lateinit var context: Context
    private lateinit var sourceDatabase: AppDatabase
    private lateinit var targetDatabase: AppDatabase
    private lateinit var sourceRepository: OfflineRecipeRepository
    private lateinit var targetRepository: OfflineRecipeRepository
    private lateinit var imageStore: ImageStore
    private lateinit var sourceManager: BackupManager
    private lateinit var targetManager: BackupManager

    @Before
    fun setUp() {
        runBlocking {
            context = ApplicationProvider.getApplicationContext()
            sourceDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
            targetDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
            sourceRepository = OfflineRecipeRepository(sourceDatabase)
            targetRepository = OfflineRecipeRepository(targetDatabase)
            imageStore = ImageStore(context, OkHttpClient())
            imageStore.cleanup(emptySet())
            sourceManager = BackupManager(context.contentResolver, sourceRepository, imageStore)
            targetManager = BackupManager(context.contentResolver, targetRepository, imageStore)
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            sourceDatabase.close()
            targetDatabase.close()
            imageStore.cleanup(emptySet())
        }
    }

    @Test
    fun round_trip_przenosi_przepis_i_zdjecie() = runBlocking {
        val imagePath = imageStore.saveImported(testImage(), "recipe-1")
        sourceRepository.save(sampleRecipe(title = "Wersja źródłowa", updatedAt = 200, imagePath = imagePath))

        val archive = ByteArrayOutputStream()
        assertEquals(1, sourceManager.exportTo(archive).recipeCount)
        assertEquals(1, targetManager.importFrom(ByteArrayInputStream(archive.toByteArray())).recipeCount)

        val imported = targetRepository.getById("recipe-1")
        assertEquals("Wersja źródłowa", imported?.title)
        assertEquals(listOf("Do zrobienia"), imported?.tags)
        assertNotNull(imported?.imagePath)
        assertTrue(File(requireNotNull(imported?.imagePath)).isFile)
    }

    @Test
    fun scalanie_zachowuje_biezacy_przy_remisie_i_wybiera_nowszy() = runBlocking {
        targetRepository.save(sampleRecipe(title = "Bieżący", updatedAt = 300))

        sourceRepository.save(sampleRecipe(title = "Starszy", updatedAt = 200))
        targetManager.importFrom(ByteArrayInputStream(exportSource()))
        assertEquals("Bieżący", targetRepository.getById("recipe-1")?.title)

        sourceRepository.save(sampleRecipe(title = "Remis", updatedAt = 300))
        targetManager.importFrom(ByteArrayInputStream(exportSource()))
        assertEquals("Bieżący", targetRepository.getById("recipe-1")?.title)

        sourceRepository.save(sampleRecipe(title = "Nowszy", updatedAt = 400))
        targetManager.importFrom(ByteArrayInputStream(exportSource()))
        assertEquals("Nowszy", targetRepository.getById("recipe-1")?.title)
    }

    @Test
    fun odrzuca_niebezpieczny_zip_bez_zmian_w_bibliotece() = runBlocking {
        targetRepository.save(sampleRecipe(title = "Bez zmian", updatedAt = 300))
        val malicious = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("../escape.webp"))
                zip.write(byteArrayOf(1, 2, 3))
                zip.closeEntry()
            }
        }.toByteArray()

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { targetManager.importFrom(ByteArrayInputStream(malicious)) }
        }
        assertEquals("Bez zmian", targetRepository.getById("recipe-1")?.title)
        assertEquals(1, targetRepository.exportAll().size)
    }

    private suspend fun exportSource(): ByteArray = ByteArrayOutputStream().also { sourceManager.exportTo(it) }.toByteArray()

    private fun sampleRecipe(title: String, updatedAt: Long, imagePath: String? = null) = Recipe(
        id = "recipe-1",
        title = title,
        sourceName = "Moje Wypieki",
        sourceUrl = "https://mojewypieki.com/przepis/test",
        imagePath = imagePath,
        ingredientSections = listOf(IngredientSection(lines = listOf(IngredientLine(rawText = "250 g mąki")))),
        instructionSections = listOf(InstructionSection(steps = listOf("Wymieszaj i upiecz."))),
        tags = listOf("Do zrobienia"),
        createdAt = 100,
        updatedAt = updatedAt,
    )

    private fun testImage(): ByteArray {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().also { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            bitmap.recycle()
        }.toByteArray()
    }
}
