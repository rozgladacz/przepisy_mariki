package pl.local.przepisy.domain.quantity

import java.math.BigDecimal
import java.util.Locale

data class UnitDefinition(
    val key: String,
    val aliases: Set<String>,
    val one: String,
    val few: String = one,
    val many: String = few,
    val invariant: Boolean = false,
)

data class UnitMatch(val definition: UnitDefinition, val consumedCharacters: Int)

object UnitRegistry {
    private val units = listOf(
        UnitDefinition("łyżeczka", setOf("łyżeczka", "łyżeczki", "łyżeczek", "łyżeczke", "łyżeczkę"), "łyżeczka", "łyżeczki", "łyżeczek"),
        UnitDefinition("łyżka", setOf("łyżka", "łyżki", "łyżek", "łyżke", "łyżkę"), "łyżka", "łyżki", "łyżek"),
        UnitDefinition("szklanka", setOf("szklanka", "szklanki", "szklanek", "szklanke", "szklankę"), "szklanka", "szklanki", "szklanek"),
        UnitDefinition("opakowanie", setOf("opakowanie", "opakowania", "opakowań", "opak."), "opakowanie", "opakowania", "opakowań"),
        UnitDefinition("pęczek", setOf("pęczek", "pęczki", "pęczków", "peczek", "peczki", "peczków"), "pęczek", "pęczki", "pęczków"),
        UnitDefinition("szczypta", setOf("szczypta", "szczypty", "szczypt"), "szczypta", "szczypty", "szczypt"),
        UnitDefinition("garść", setOf("garść", "garści", "garsc", "garsci"), "garść", "garści", "garści"),
        UnitDefinition("puszka", setOf("puszka", "puszki", "puszek"), "puszka", "puszki", "puszek"),
        UnitDefinition("ząbek", setOf("ząbek", "ząbki", "ząbków", "zabek", "zabki", "zabkow"), "ząbek", "ząbki", "ząbków"),
        UnitDefinition("plaster", setOf("plaster", "plastry", "plastrów", "plastrow"), "plaster", "plastry", "plastrów"),
        UnitDefinition("kromka", setOf("kromka", "kromki", "kromek"), "kromka", "kromki", "kromek"),
        UnitDefinition("sztuka", setOf("sztuka", "sztuki", "sztuk", "szt.", "szt"), "sztuka", "sztuki", "sztuk"),
        UnitDefinition("jajko", setOf("jajko", "jajka", "jajek"), "jajko", "jajka", "jajek"),
        UnitDefinition("gram", setOf("g", "gram", "gramy", "gramów", "gramow"), "g", invariant = true),
        UnitDefinition("dekagram", setOf("dag", "dkg", "dekagram", "dekagramy", "dekagramów"), "dag", invariant = true),
        UnitDefinition("kilogram", setOf("kg", "kilogram", "kilogramy", "kilogramów"), "kg", invariant = true),
        UnitDefinition("mililitr", setOf("ml", "mililitr", "mililitry", "mililitrów"), "ml", invariant = true),
        UnitDefinition("litr", setOf("l", "litr", "litra", "litry", "litrów"), "l", invariant = true),
    )

    private val aliases = units.flatMap { unit -> unit.aliases.map { it.lowercase(Locale.forLanguageTag("pl")) to unit } }
        .sortedByDescending { it.first.length }

    fun match(text: String): UnitMatch? {
        val lowered = text.trimStart().lowercase(Locale.forLanguageTag("pl"))
        val leadingWhitespace = text.length - text.trimStart().length
        for ((alias, unit) in aliases) {
            if (!lowered.startsWith(alias)) continue
            val next = lowered.getOrNull(alias.length)
            if (next != null && !next.isWhitespace() && next !in charArrayOf(',', ';', ':', '/', ')')) continue
            return UnitMatch(unit, leadingWhitespace + alias.length)
        }
        return null
    }

    fun label(key: String?, quantity: BigDecimal): String? {
        val unit = units.firstOrNull { it.key == key } ?: return null
        if (unit.invariant) return unit.one
        val stripped = quantity.stripTrailingZeros()
        if (stripped.scale() > 0) return unit.few
        val value = runCatching { stripped.longValueExact() }.getOrNull() ?: return unit.many
        val absolute = kotlin.math.abs(value)
        if (absolute == 1L) return unit.one
        val lastTwo = absolute % 100
        val last = absolute % 10
        return if (last in 2L..4L && lastTwo !in 12L..14L) unit.few else unit.many
    }
}
