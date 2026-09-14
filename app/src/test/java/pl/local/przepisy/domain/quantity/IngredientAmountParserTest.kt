package pl.local.przepisy.domain.quantity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class IngredientAmountParserTest {
    private val parser = IngredientAmountParser()

    @Test
    fun `rozpoznaje liczby jednostki ulamki i zakresy`() {
        assertParsed("250 g masła", "250", null, "gram", "masła")
        assertParsed("2,5 szklanki mąki", "2.5", null, "szklanka", "mąki")
        assertParsed("3/4 szklanki cukru", "0.75", null, "szklanka", "cukru")
        assertParsed("1 1/2 szklanki mleka", "1.5", null, "szklanka", "mleka")
        assertParsed("¾ szklanki cukru", "0.75", null, "szklanka", "cukru")
        assertParsed("pół łyżeczki soli", "0.5", null, "łyżeczka", "soli")
        assertParsed("1–2 ząbki czosnku", "1", "2", "ząbek", "czosnku")
        assertParsed("10 dkg twarogu", "10", null, "dekagram", "twarogu")
    }

    @Test
    fun `rozpoznaje liczby slowne i miary domyslne`() {
        val eggs = parser.parse("dwa jajka")
        assertEquals("2", eggs.quantityMin)
        assertEquals("jajko", eggs.unitKey)
        assertTrue(eggs.scalable)

        val handful = parser.parse("garść malin")
        assertEquals("1", handful.quantityMin)
        assertEquals("garść", handful.unitKey)
        assertEquals("malin", handful.body)
    }

    @Test
    fun `nie skaluje ilosci nieokreslonych`() {
        assertFalse(parser.parse("sól do smaku").scalable)
        assertFalse(parser.parse("kilka listków bazylii").scalable)
        assertFalse(parser.parse("oliwa").scalable)
    }

    @Test
    fun `zachowuje kwalifikatory i opcjonalnosc`() {
        val approximate = parser.parse("ok. 600 g mąki")
        assertEquals("ok.", approximate.qualifier)
        assertEquals("600", approximate.quantityMin)

        val optional = parser.parse("opcjonalnie: 2 łyżki kakao")
        assertEquals("opcjonalnie", optional.qualifier)
        assertEquals("2", optional.quantityMin)
        assertTrue(optional.scalable)
    }

    @Test
    fun `rozpoznaje polskie liczby slowne od jeden do dwadziescia`() {
        val values = listOf(
            "jeden" to "1", "dwie" to "2", "trzy" to "3", "cztery" to "4", "pięć" to "5",
            "sześć" to "6", "siedem" to "7", "osiem" to "8", "dziewięć" to "9", "dziesięć" to "10",
            "jedenaście" to "11", "dwanaście" to "12", "trzynaście" to "13", "czternaście" to "14",
            "piętnaście" to "15", "szesnaście" to "16", "siedemnaście" to "17", "osiemnaście" to "18",
            "dziewiętnaście" to "19", "dwadzieścia" to "20",
        )
        values.forEach { (word, value) -> assertEquals(value, parser.parse("$word jajek").quantityMin) }
        assertEquals("1.5", parser.parse("półtorej szklanki mąki").quantityMin)
        assertEquals("0.25", parser.parse("ćwierć łyżeczki soli").quantityMin)
    }

    @Test
    fun `formatuje ulamki i odmiane jajek`() {
        val eggs = parser.parse("dwa jajka")
        assertEquals("1 jajko", QuantityFormatter.formatIngredient(eggs, BigDecimal("0.5")))
        assertEquals("3 jajka", QuantityFormatter.formatIngredient(eggs, BigDecimal("1.5")))
        assertEquals("1 3/8 jajka", QuantityFormatter.formatIngredient(eggs, BigDecimal("0.7")))
        val flour = parser.parse("1 szklanka mąki")
        assertEquals("1/2 szklanki mąki", QuantityFormatter.formatIngredient(flour, BigDecimal("0.5")))
        val range = parser.parse("1–2 ząbki czosnku")
        assertEquals("1 1/2–3 ząbki czosnku", QuantityFormatter.formatIngredient(range, BigDecimal("1.5")))
        assertTrue(QuantityFormatter.formatIngredient(range, BigDecimal("1.5"), approximate = true).startsWith("≈"))
    }

    @Test
    fun `zaokragla drobne jednostki metryczne do pelnych wartosci`() {
        val grams = parser.parse("250 g mąki")
        assertEquals("251 g mąki", QuantityFormatter.formatIngredient(grams, BigDecimal("1.003")))

        val millilitres = parser.parse("125 ml mleka")
        assertEquals("63 ml mleka", QuantityFormatter.formatIngredient(millilitres, BigDecimal("0.5")))
    }

    @Test
    fun `zachowuje ulamki dla duzych jednostek metrycznych`() {
        val kilograms = parser.parse("1 kg mąki")
        assertEquals("1/2 kg mąki", QuantityFormatter.formatIngredient(kilograms, BigDecimal("0.5")))
    }

    private fun assertParsed(raw: String, min: String, max: String?, unit: String, body: String) {
        val parsed = parser.parse(raw)
        assertEquals(min, parsed.quantityMin)
        assertEquals(max, parsed.quantityMax)
        assertEquals(unit, parsed.unitKey)
        assertEquals(body, parsed.body)
        assertTrue(parsed.scalable)
    }
}
