package pl.local.przepisy

import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import pl.local.przepisy.domain.model.IngredientLine
import pl.local.przepisy.domain.model.IngredientSection
import pl.local.przepisy.domain.model.InstructionSection
import pl.local.przepisy.domain.model.ParseStatus
import pl.local.przepisy.domain.model.PanPreset
import pl.local.przepisy.domain.model.PanShape
import pl.local.przepisy.domain.model.PanSpec
import pl.local.przepisy.domain.model.Recipe
import pl.local.przepisy.domain.model.RecipeDraft
import pl.local.przepisy.ui.ImportPreviewContent
import pl.local.przepisy.ui.ImportedRecipeSummary

class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun clearLibrary() = runBlocking {
        val container = (composeRule.activity.application as PrzepisyApplication).container
        val repository = container.repository
        repository.exportAll().forEach { repository.delete(it.id) }
        container.panPresetStore.presets.value.forEach { container.panPresetStore.delete(it.id) }
    }

    @Test
    fun pokazuje_pusta_biblioteke() {
        composeRule.onNodeWithText("Przepisy").assertIsDisplayed()
        composeRule.onNodeWithText("Nie masz jeszcze przepisów.\nDodaj pierwszy przyciskiem +.").assertIsDisplayed()
    }

    @Test
    fun odrzuca_nieobslugiwana_domene_przed_polaczeniem_z_siecia() {
        composeRule.onNodeWithText("+").performClick()
        composeRule.onNodeWithText("Adres HTTPS").performTextInput("https://example.com/przepis")
        composeRule.onNodeWithText("Pobierz przepis").performClick()
        waitForText("Obsługiwane są tylko mojewypieki.com, alaantkoweblw.pl, rozkoszny.pl i aniagotuje.pl.")
    }

    @Test
    fun obsluguje_url_udostepniony_przez_action_send() {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.handleIntent(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "Zobacz https://example.com/przepis")
                },
            )
        }
        waitForText("Obsługiwane są tylko mojewypieki.com, alaantkoweblw.pl, rozkoszny.pl i aniagotuje.pl.")
    }

    @Test
    fun edytuje_i_usuwa_zapisany_przepis() {
        val repository = (composeRule.activity.application as PrzepisyApplication).container.repository
        runBlocking {
            repository.save(
                Recipe(
                    id = "ui-recipe",
                    title = "Przepis do edycji",
                    sourceName = "Moje Wypieki",
                    sourceUrl = "https://mojewypieki.com/przepis/ui-test",
                    ingredientSections = listOf(IngredientSection(lines = listOf(IngredientLine(rawText = "2 jajka")))),
                    instructionSections = listOf(InstructionSection(steps = listOf("Wymieszaj."))),
                ),
            )
        }

        waitForText("Przepis do edycji")
        composeRule.onNodeWithText("Przepis do edycji").performClick()
        composeRule.onNodeWithText("Edytuj").performClick()
        composeRule.onNodeWithText("Przepis do edycji").performTextReplacement("Zmieniony przepis")
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Zapisz zmiany"))
        composeRule.onNodeWithText("Zapisz zmiany").performClick()
        waitForText("Zmieniony przepis")

        composeRule.onNodeWithText("Usuń").performClick()
        composeRule.onAllNodesWithText("Usuń")[1].performClick()
        waitForText("Nie masz jeszcze przepisów.\nDodaj pierwszy przyciskiem +.")
    }

    @Test
    fun przelacza_ulubione_na_liscie() {
        val repository = (composeRule.activity.application as PrzepisyApplication).container.repository
        runBlocking {
            repository.save(sampleUiRecipe(id = "favorite-ui", title = "Przepis z gwiazdką"))
        }

        waitForText("Przepis z gwiazdką")
        composeRule.onNodeWithContentDescription("Dodaj do ulubionych").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("★").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Usuń z ulubionych").assertIsDisplayed()
    }

    @Test
    fun szczegoly_maja_zwijany_opis_ukryty_wlasny_mnoznik_i_widoczne_skalowanie() {
        val repository = (composeRule.activity.application as PrzepisyApplication).container.repository
        runBlocking {
            repository.save(
                sampleUiRecipe(id = "detail-ui", title = "Skalowany przepis").copy(
                    description = "Pełny opis przepisu do ukrycia.",
                    ingredientSections = listOf(
                        IngredientSection(
                            lines = listOf(
                                IngredientLine(
                                    rawText = "2 jajka",
                                    quantityMin = "2",
                                    unitKey = "jajko",
                                    body = "",
                                    parseStatus = ParseStatus.EXACT,
                                    scalable = true,
                                ),
                            ),
                        ),
                    ),
                ),
            )
        }

        waitForText("Skalowany przepis")
        composeRule.onNodeWithText("Skalowany przepis").performClick()
        assertTextAbsent("Pełny opis przepisu do ukrycia.")
        assertTextAbsent("Własny mnożnik")

        composeRule.onNodeWithText("Opis").performClick()
        composeRule.onNodeWithText("Pełny opis przepisu do ukrycia.").assertIsDisplayed()
        composeRule.onNodeWithText("Inne").performClick()
        composeRule.onNodeWithText("Własny mnożnik").assertIsDisplayed()
        composeRule.onNodeWithText("2×").performClick()
        assertTextAbsent("Własny mnożnik")
        waitForText("• 4 jajka")
    }

    @Test
    fun dialog_formy_ma_stala_baze_i_pozwala_zapisac_oraz_usunac_preset() {
        val container = (composeRule.activity.application as PrzepisyApplication).container
        runBlocking {
            container.repository.save(
                sampleUiRecipe(id = "pan-ui", title = "Przepis z formą").copy(
                    pan = PanSpec(PanShape.RECTANGLE, widthCm = 20.0, heightCm = 30.0),
                ),
            )
        }
        container.panPresetStore.save(
            PanPreset(
                id = "tortownica-ui",
                name = "Tortownica",
                pan = PanSpec(PanShape.CIRCLE, diameterCm = 24.0),
            ),
        )

        waitForText("Przepis z formą")
        composeRule.onNodeWithText("Przepis z formą").performClick()
        composeRule.onNodeWithText("Przelicz na inną formę").performClick()

        composeRule.onNodeWithText("Forma bazowa: 20 × 30 cm").assertIsDisplayed()
        composeRule.onNodeWithText("Lista").performClick()
        composeRule.onNodeWithText("Tortownica — ⌀ 24 cm").assertIsDisplayed()
        composeRule.onNodeWithText("Tortownica — ⌀ 24 cm").performClick()
        assertTextAbsent("Nazwa formy")
        composeRule.onNodeWithText("Dostosuj").assertIsDisplayed()
        composeRule.onNodeWithText("Dostosuj").performClick()
        composeRule.onNodeWithText("Nazwa formy").assertIsDisplayed()
        composeRule.onNodeWithText("Zapisz formę").assertIsDisplayed()
        composeRule.onNodeWithText("Usuń formę").assertIsDisplayed()
        composeRule.onNodeWithText("Zapisz formę").performClick()
        assertTextAbsent("Nazwa formy")
        composeRule.onNodeWithText("Dostosuj").assertIsDisplayed()
        composeRule.onNodeWithText("Dostosuj").performClick()
        composeRule.onNodeWithText("Usuń formę").performClick()
        assertTextAbsent("Tortownica — ⌀ 24 cm")
        assertTextAbsent("Nazwa formy")

        composeRule.onNodeWithText("Nowa").performClick()
        composeRule.onNodeWithText("Nazwa formy").performTextInput("Duża blacha")
        composeRule.onNodeWithText("Zapisz formę").performClick()
        composeRule.onNodeWithText("Duża blacha — 20 × 30 cm").assertIsDisplayed()
        assertTextAbsent("Nazwa formy")
        composeRule.onNodeWithText("Dostosuj").assertIsDisplayed()
    }

    @Test
    fun edytuje_tagi_i_filtruje_biblioteke_chmura_tagow() {
        val repository = (composeRule.activity.application as PrzepisyApplication).container.repository
        runBlocking {
            repository.save(
                sampleUiRecipe(id = "tag-obiad", title = "Zupa testowa").copy(tags = listOf("Obiad")),
            )
            repository.save(
                sampleUiRecipe(id = "tag-deser", title = "Ciasto testowe").copy(tags = listOf("Deser")),
            )
        }

        waitForText("Zupa testowa")
        waitForText("Ciasto testowe")
        composeRule.onNodeWithText("Obiad").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Ciasto testowe").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithText("Zupa testowa").assertIsDisplayed()

        composeRule.onNodeWithText("Obiad").performClick()
        waitForText("Ciasto testowe")
        composeRule.onNodeWithText("Zupa testowa").performClick()
        composeRule.onNodeWithText("Źródło: Moje Wypieki").assertIsDisplayed()
        composeRule.onNodeWithText("Zmień").performClick()
        composeRule.onNodeWithText("Tagi przepisu").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Edytuj tag: Obiad").performClick()
        composeRule.onNodeWithText("Deser").performClick()
        composeRule.onNodeWithText("Nowy tag").performTextInput("Do zrobienia")
        composeRule.onNodeWithText("Dodaj").performClick()
        composeRule.onNodeWithText("Zapisz").performClick()

        waitForText("Deser")
        waitForText("Do zrobienia")
        assertTextAbsent("Obiad")
    }

    @Test
    fun przycisk_zapisu_importu_pozostaje_widoczny_podczas_przewijania() {
        val draft = RecipeDraft(
            title = "Długi import",
            sourceName = "Moje Wypieki",
            sourceUrl = "https://mojewypieki.com/przepis/dlugi-import",
            ingredientSections = listOf(
                IngredientSection(lines = List(12) { IngredientLine(rawText = "${it + 1} g składnika") }),
            ),
            instructionSections = listOf(
                InstructionSection(steps = List(20) { "Długi krok numer ${it + 1}." }),
            ),
        )
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                MaterialTheme {
                    var editedDraft by remember { mutableStateOf(draft) }
                    ImportPreviewContent(
                        draft = editedDraft,
                        onDraftChange = { editedDraft = it },
                        actionLabel = "Zapisz offline",
                        onAction = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("Zapisz offline").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Dodaj sekcję instrukcji"))
        composeRule.onNodeWithText("Zapisz offline").assertIsDisplayed()
    }

    @Test
    fun podsumowanie_importu_pozwala_od_razu_zaznaczyc_tagi() {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                MaterialTheme {
                    var selectedTags by remember { mutableStateOf(setOf("Obiad")) }
                    var availableTags by remember { mutableStateOf(listOf("Obiad", "Do zrobienia")) }
                    ImportedRecipeSummary(
                        warning = null,
                        availableTags = availableTags,
                        selectedTags = selectedTags.toList(),
                        onOpen = {},
                        onToggleTag = { tag ->
                            selectedTags = if (tag in selectedTags) selectedTags - tag else selectedTags + tag
                        },
                        onAddTag = { tag ->
                            availableTags = (availableTags + tag).distinct()
                            selectedTags = selectedTags + tag
                        },
                    )
                }
            }
        }

        composeRule.onNodeWithText("Otwórz przepis").assertIsDisplayed()
        composeRule.onNodeWithText("Obiad").assertIsSelected().performClick().assertIsNotSelected()
        composeRule.onNodeWithText("Do zrobienia").assertIsNotSelected().performClick().assertIsSelected()
        composeRule.onNodeWithText("Nowy tag").performTextInput("Kolacja")
        composeRule.onNodeWithText("Dodaj").performClick()
        composeRule.onNodeWithText("Kolacja").assertIsSelected()
    }

    private fun sampleUiRecipe(id: String, title: String) = Recipe(
        id = id,
        title = title,
        sourceName = "Moje Wypieki",
        sourceUrl = "https://mojewypieki.com/przepis/$id",
        ingredientSections = listOf(IngredientSection(lines = listOf(IngredientLine(rawText = "2 jajka")))),
        instructionSections = listOf(InstructionSection(steps = listOf("Wymieszaj."))),
    )

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText(text)[0].assertIsDisplayed()
    }

    private fun assertTextAbsent(text: String) {
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty())
    }
}
