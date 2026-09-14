package pl.local.przepisy.data.importer

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.local.przepisy.domain.model.PanShape
import pl.local.przepisy.domain.quantity.IngredientAmountParser

class SiteRecipeAdaptersTest {
    private val amountParser = IngredientAmountParser()
    private val jsonLd = JsonLdRecipeExtractor()

    @Test
    fun `moje wypieki korzysta z json ld`() {
        val draft = parse("fixtures/mojewypieki-jsonld.html", MojeWypiekiAdapter(amountParser, jsonLd))
        assertEquals("Testowe ciasto", draft.title)
        assertEquals(2, draft.ingredientSections.single().lines.size)
        assertEquals(2, draft.instructionSections.single().steps.size)
        assertTrue(draft.instructionSections.single().steps.none { it.equals("Smacznego!", ignoreCase = true) })
        assertEquals(PanShape.RECTANGLE, draft.pan?.shape)
        assertTrue(draft.warnings.isEmpty())
    }

    @Test
    fun `moje wypieki ma kontrolowany fallback dom`() {
        val draft = parse("fixtures/mojewypieki-dom.html", MojeWypiekiAdapter(amountParser, jsonLd))
        assertEquals("Ciastka testowe", draft.title)
        assertEquals(2, draft.instructionSections.single().steps.size)
        assertTrue(draft.instructionSections.single().steps.none { it.equals("Smacznego!", ignoreCase = true) })
        assertTrue(draft.warnings.isNotEmpty())
    }

    @Test
    fun `moje wypieki nie dolacza noty zrodlowej ani panelu tagow`() {
        val draft = parse("fixtures/mojewypieki-current-boundary.html", MojeWypiekiAdapter(amountParser, jsonLd))
        val steps = draft.instructionSections.single().steps

        assertEquals(2, steps.size)
        assertTrue(steps.none { it.equals("Smacznego!", ignoreCase = true) })
        assertTrue(steps.none { it.contains("pochodzi", ignoreCase = true) })
        assertTrue(steps.none { it.contains("Ciasteczka", ignoreCase = true) })
        assertTrue(steps.none { it.contains("Dla dzieci", ignoreCase = true) })
    }

    @Test
    fun `moje wypieki rozpoznaje sekcje oznaczone akapitami`() {
        val draft = parse("fixtures/mojewypieki-paragraph-headings.html", MojeWypiekiAdapter(amountParser, jsonLd))

        assertEquals(listOf("Ciasto kruche", "Nadzienie migdałowe", "Dodatkowo"), draft.ingredientSections.map { it.title })
        assertEquals(listOf(2, 2, 1), draft.ingredientSections.map { it.lines.size })
        assertEquals("195 g mąki pszennej", draft.ingredientSections.first().lines.first().rawText)
        assertEquals(PanShape.CIRCLE, draft.pan?.shape)
        assertEquals(24.0, draft.pan?.diameterCm ?: 0.0, 0.0)
        assertTrue(draft.instructionSections.single().steps.none { it.startsWith("100 g") })
    }

    @Test
    fun `moje wypieki nie ucina przepisu na wyroznieniu wewnatrz instrukcji`() {
        val draft = parse("fixtures/mojewypieki-inline-emphasis.html", MojeWypiekiAdapter(amountParser, jsonLd))

        assertEquals(
            listOf("Kruszonkę", "Jabłka w cynamonie", "Masę serową"),
            draft.ingredientSections.map { it.title },
        )
        assertEquals(listOf(3, 3, 6), draft.ingredientSections.map { it.lines.size })
        assertEquals(8, draft.instructionSections.single().steps.size)
        assertTrue(draft.instructionSections.single().steps.any { it.contains("nie rozpadały") })
        assertTrue(draft.instructionSections.single().steps.any { it.startsWith("Piec przez 50") })
        assertTrue(draft.instructionSections.single().steps.none { it.equals("Smacznego!", ignoreCase = true) })
    }

    @Test
    fun `alaantkoweblw obsluguje graf json ld`() {
        val draft = parse("fixtures/alaantkoweblw-jsonld.html", AlaantkoweblwAdapter(amountParser, jsonLd))
        assertEquals("jajko", draft.ingredientSections.single().lines.first().unitKey)
    }

    @Test
    fun `alaantkoweblw rozdziela skladniki i wykonanie w dom`() {
        val draft = parse("fixtures/alaantkoweblw-dom.html", AlaantkoweblwAdapter(amountParser, jsonLd))
        assertEquals(2, draft.ingredientSections.single().lines.size)
        assertEquals(2, draft.instructionSections.single().steps.size)
    }

    @Test
    fun `alaantkoweblw rozpoznaje nowy blok recipe`() {
        val draft = parse("fixtures/alaantkoweblw-modern-dom.html", AlaantkoweblwAdapter(amountParser, jsonLd))

        assertEquals("Pełny opis nowego układu przepisu.", draft.description)
        assertEquals(3, draft.ingredientSections.single().lines.size)
        assertEquals(2, draft.instructionSections.single().steps.size)
        assertTrue(draft.instructionSections.single().steps.none { it.contains("szklanki mąki") })
    }

    @Test
    fun `alaantkoweblw rozpoznaje stary blok z liniami br`() {
        val draft = parse("fixtures/alaantkoweblw-legacy-br.html", AlaantkoweblwAdapter(amountParser, jsonLd))

        assertEquals(8, draft.ingredientSections.single().lines.size)
        assertEquals("500 g mięsa mielonego z indyka", draft.ingredientSections.single().lines.first().rawText)
        assertEquals(2, draft.instructionSections.single().steps.size)
    }

    @Test
    fun `alaantkoweblw pomija naglowek sosu w starym ukladzie`() {
        val draft = parse("fixtures/alaantkoweblw-legacy-heading.html", AlaantkoweblwAdapter(amountParser, jsonLd))

        assertEquals(5, draft.ingredientSections.single().lines.size)
        assertTrue(draft.ingredientSections.single().lines.none { it.rawText.contains("beszamelowy") })
        assertEquals(2, draft.instructionSections.single().steps.size)
    }

    @Test
    fun `alaantkoweblw rozpoznaje skladniki zapisane w osobnych divach`() {
        val draft = parse("fixtures/alaantkoweblw-legacy-divs.html", AlaantkoweblwAdapter(amountParser, jsonLd))

        assertEquals(6, draft.ingredientSections.single().lines.size)
        assertEquals(2, draft.instructionSections.single().steps.size)
        assertTrue(draft.instructionSections.single().steps.first().startsWith("Bakłażany"))
    }

    @Test
    fun `alaantkoweblw obsluguje uszkodzony zagniezdzony blok starego wpisu`() {
        val draft = parse("fixtures/alaantkoweblw-legacy-nested-div.html", AlaantkoweblwAdapter(amountParser, jsonLd))

        assertEquals("Pierś z indyka w curry i mleku kokosowym", draft.title)
        assertEquals(8, draft.ingredientSections.single().lines.size)
        assertEquals(3, draft.instructionSections.single().steps.size)
        assertTrue(draft.instructionSections.single().steps.none { it.startsWith("Ps.") })
    }

    @Test
    fun `alaantkoweblw rozpoznaje krotka instrukcje w liczbie mnogiej`() {
        val draft = parse(
            "fixtures/alaantkoweblw-legacy-short-instruction.html",
            AlaantkoweblwAdapter(amountParser, jsonLd),
        )

        assertEquals("Pasta z awokado i gruszki", draft.title)
        assertEquals(4, draft.ingredientSections.single().lines.size)
        assertEquals(1, draft.instructionSections.single().steps.size)
        assertTrue(draft.instructionSections.single().steps.single().contains("posypujemy sezamem"))
    }

    @Test
    fun `alaantkoweblw rozpoznaje zwykly naglowek i skladniki listy`() {
        val draft = parse(
            "fixtures/alaantkoweblw-legacy-list.html",
            AlaantkoweblwAdapter(amountParser, jsonLd),
        )

        assertEquals("Pasta kanapkowa ”pomidorowy hummus”", draft.title)
        assertEquals(5, draft.ingredientSections.single().lines.size)
        assertEquals("2 szklanki ugotowanej ciecierzycy", draft.ingredientSections.single().lines.first().rawText)
        assertEquals(1, draft.instructionSections.single().steps.size)
        assertTrue(draft.instructionSections.single().steps.single().startsWith("Wrzucamy wszystkie składniki"))
    }

    @Test
    fun `rozkoszny rozpoznaje skladniki kroki i forme`() {
        val draft = parse("fixtures/rozkoszny-rabarbar.html", RozkosznyAdapter(amountParser, jsonLd))

        assertEquals("Po prostu ciasto z rabarbarem", draft.title)
        assertEquals(6, draft.ingredientSections.single().lines.size)
        assertEquals(4, draft.instructionSections.single().steps.size)
        assertEquals("tortownica 23 cm", draft.originalYield)
        assertEquals(PanShape.CIRCLE, draft.pan?.shape)
        assertEquals(23.0, draft.pan?.diameterCm ?: 0.0, 0.0)
        assertTrue(draft.instructionSections.single().steps.none { it.contains("Rady") })
    }

    @Test
    fun `rozkoszny zachowuje dodatkowa sekcje skladnikow`() {
        val draft = parse("fixtures/rozkoszny-buleczki.html", RozkosznyAdapter(amountParser, jsonLd))

        assertEquals(listOf("Składniki", "Do podania"), draft.ingredientSections.map { it.title })
        assertEquals(listOf(7, 1), draft.ingredientSections.map { it.lines.size })
        assertEquals("9 sztuk", draft.originalYield)
        assertEquals(7, draft.instructionSections.single().steps.size)
        assertTrue(draft.instructionSections.single().steps.none { it.contains("Masło i ciasto") })
    }

    @Test
    fun `rozkoszny rozpoznaje nazwe pierwszej sekcji wewnatrz glownej karty`() {
        val draft = parse(
            "fixtures/rozkoszny-main-section-heading.html",
            RozkosznyAdapter(amountParser, jsonLd),
        )

        assertEquals(listOf("Kopytka", "Sos kurkowy + dodatki"), draft.ingredientSections.map { it.title })
        assertEquals(listOf(4, 3), draft.ingredientSections.map { it.lines.size })
        assertTrue(draft.ingredientSections.flattenLines().none { it.rawText == "Kopytka" })
        assertEquals(3, draft.instructionSections.single().steps.size)
    }

    @Test
    fun `ania gotuje rozpoznaje mikroformat z krokami i sekcjami skladnikow`() {
        val draft = parse("fixtures/aniagotuje-modern-microdata.html", AniaGotujeAdapter(amountParser, jsonLd))

        assertEquals("Ania Gotuje", draft.sourceName)
        assertEquals("Ciasto jogurtowe", draft.title)
        assertEquals("Puszyste ciasto jogurtowe z owocami i kruszonką.", draft.description)
        assertEquals("1700 gramów ciasta", draft.originalYield)
        assertEquals(listOf("Składniki", "Kruszonka"), draft.ingredientSections.map { it.title })
        assertEquals(listOf(3, 2), draft.ingredientSections.map { it.lines.size })
        assertEquals(3, draft.instructionSections.single().steps.size)
        assertTrue(draft.instructionSections.single().steps[1].startsWith("Ubij jajka z cukrem"))
        assertEquals(PanShape.RECTANGLE, draft.pan?.shape)
        assertEquals(24.0, draft.pan?.widthCm ?: 0.0, 0.0)
        assertEquals(24.0, draft.pan?.heightCm ?: 0.0, 0.0)
    }

    @Test
    fun `ania gotuje rozpoznaje starszy blok i dodatkowe skladniki`() {
        val draft = parse("fixtures/aniagotuje-legacy-microdata.html", AniaGotujeAdapter(amountParser, jsonLd))

        assertEquals(listOf("Składniki", "Dodatkowo"), draft.ingredientSections.map { it.title })
        assertEquals(listOf(3, 3), draft.ingredientSections.map { it.lines.size })
        assertEquals(4, draft.instructionSections.single().steps.size)
        assertTrue(draft.instructionSections.single().steps.none { it.startsWith("Smacznego") })
        assertEquals(PanShape.CIRCLE, draft.pan?.shape)
        assertEquals(20.0, draft.pan?.diameterCm ?: 0.0, 0.0)
    }

    @Test
    fun `ania gotuje przenosi ilosc sprzed nazwy do formatu skalowalnego`() {
        val draft = parse(
            "fixtures/aniagotuje-quantity-last-microdata.html",
            AniaGotujeAdapter(amountParser, jsonLd),
        )
        val lines = draft.ingredientSections.single().lines

        assertEquals(4, lines.size)
        assertEquals("600 g mięso mielone - np. łopatka wieprzowa", lines[0].rawText)
        assertEquals("600", lines[0].quantityMin)
        assertEquals("gram", lines[0].unitKey)
        assertTrue(lines[0].scalable)
        assertEquals("3", lines[2].quantityMin)
        assertEquals("ząbek", lines[2].unitKey)
        assertEquals("4", lines[3].quantityMin)
        assertEquals("łyżka", lines[3].unitKey)
    }

    private fun List<pl.local.przepisy.domain.model.IngredientSection>.flattenLines() = flatMap { it.lines }

    private fun parse(resource: String, adapter: RecipeSourceAdapter): pl.local.przepisy.domain.model.RecipeDraft {
        val html = requireNotNull(javaClass.classLoader?.getResource(resource)).readText()
        val document = Jsoup.parse(html, "https://${adapter.supportedHosts.first()}/przepis/test")
        val draft = adapter.parse(document, document.baseUri())
        assertNotNull(draft)
        return requireNotNull(draft)
    }
}
