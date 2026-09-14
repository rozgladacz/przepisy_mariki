package pl.local.przepisy.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import pl.local.przepisy.data.repository.OfflineRecipeRepository
import pl.local.przepisy.domain.model.IngredientLine
import pl.local.przepisy.domain.model.IngredientSection
import pl.local.przepisy.domain.model.InstructionSection
import pl.local.przepisy.domain.model.Recipe

@RunWith(AndroidJUnit4::class)
class RecipeRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: OfflineRecipeRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = OfflineRecipeRepository(database)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun zapisuje_i_wyszukuje_po_skladniku() = runBlocking {
        repository.save(
            Recipe(
                title = "Ciasto śliwkowe",
                sourceName = "Moje Wypieki",
                sourceUrl = "https://mojewypieki.com/przepis/test",
                ingredientSections = listOf(IngredientSection(lines = listOf(IngredientLine(rawText = "śliwki i łyżka cukru")))),
                instructionSections = listOf(InstructionSection(steps = listOf("Upiecz."))),
            ),
        )
        assertEquals("Ciasto śliwkowe", repository.observeRecipes("sliwki").first().single().title)
        assertEquals("Ciasto śliwkowe", repository.observeRecipes("lyzka").first().single().title)
        assertEquals("Ciasto śliwkowe", repository.observeRecipes("CIASTO").first().single().title)
        assertEquals(1, repository.observeRecipes("!!!").first().size)
    }

    @Test
    fun zachowuje_kolejnosc_sekcji_i_aktualizuje_przepis() = runBlocking {
        val recipe = sampleRecipe("id-1", "https://mojewypieki.com/przepis/kolejnosc").copy(
            ingredientSections = listOf(
                IngredientSection(id = "s2", title = "Druga", lines = listOf(IngredientLine(id = "i2", rawText = "2 jajka"))),
                IngredientSection(id = "s1", title = "Pierwsza", lines = listOf(IngredientLine(id = "i1", rawText = "mąka"))),
            ),
        )
        repository.save(recipe)
        assertEquals(listOf("Druga", "Pierwsza"), repository.getById(recipe.id)?.ingredientSections?.map { it.title })

        repository.save(recipe.copy(title = "Nowy tytuł", updatedAt = recipe.updatedAt + 1))
        assertEquals("Nowy tytuł", repository.getById(recipe.id)?.title)
    }

    @Test
    fun wymusza_unikalny_url_i_przy_usunieciu_zwraca_sciezke_zdjecia() = runBlocking {
        val url = "https://alaantkoweblw.pl/blog/przepis/unikalny/"
        repository.save(sampleRecipe("id-a", url).copy(imagePath = "/tmp/test.webp"))
        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            runBlocking { repository.save(sampleRecipe("id-b", url)) }
        }
        assertEquals("/tmp/test.webp", repository.delete("id-a"))
        assertNull(repository.getById("id-a"))
    }

    @Test
    fun ulubione_sa_trwale_i_pokazywane_przed_nowszymi_przepisami() = runBlocking {
        repository.save(
            sampleRecipe("newer", "https://mojewypieki.com/przepis/nowszy")
                .copy(title = "Nowszy", updatedAt = 2_000),
        )
        repository.save(
            sampleRecipe("favorite", "https://mojewypieki.com/przepis/ulubiony")
                .copy(title = "Ulubiony", favorite = true, updatedAt = 1_000),
        )

        val recipes = repository.observeRecipes("").first()
        assertEquals(listOf("Ulubiony", "Nowszy"), recipes.map { it.title })
        assertEquals(true, repository.getById("favorite")?.favorite)
    }

    @Test
    fun zapisuje_tagi_globalnie_i_przy_przepisie() = runBlocking {
        val recipe = sampleRecipe("tagged", "https://mojewypieki.com/przepis/tagged").copy(
            tags = listOf("Obiad", "Do zrobienia", " obiad "),
        )
        repository.save(recipe)

        assertEquals(listOf("Do zrobienia", "Obiad"), repository.getById("tagged")?.tags)
        assertEquals(listOf("Do zrobienia", "Obiad"), repository.observeRecipes("").first().single().tags)
        assertEquals(listOf("Do zrobienia", "Obiad"), repository.observeTags().first())

        repository.save(
            sampleRecipe("second-tagged", "https://mojewypieki.com/przepis/second-tagged").copy(
                tags = listOf("Do zrobienia"),
            ),
        )
        repository.save(recipe.copy(tags = listOf("Obiad"), updatedAt = recipe.updatedAt + 1))
        assertEquals(listOf("Obiad"), repository.getById("tagged")?.tags)
        assertEquals(listOf("Do zrobienia", "Obiad"), repository.observeTags().first())

        repository.delete("second-tagged")
        assertEquals(
            "Tag bez żadnego przepisu jest automatycznie usuwany",
            listOf("Obiad"),
            repository.observeTags().first(),
        )

        repository.save(recipe.copy(tags = emptyList(), updatedAt = recipe.updatedAt + 2))
        assertEquals(emptyList<String>(), repository.observeTags().first())
    }

    private fun sampleRecipe(id: String, url: String) = Recipe(
        id = id,
        title = "Przepis testowy",
        sourceName = "Test",
        sourceUrl = url,
        ingredientSections = listOf(IngredientSection(lines = listOf(IngredientLine(rawText = "250 g mąki")))),
        instructionSections = listOf(InstructionSection(steps = listOf("Wymieszaj."))),
    )
}
