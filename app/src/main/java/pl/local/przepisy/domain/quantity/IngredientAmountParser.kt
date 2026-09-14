package pl.local.przepisy.domain.quantity

import pl.local.przepisy.domain.model.IngredientLine
import pl.local.przepisy.domain.model.ParseStatus
import java.math.BigDecimal
import java.math.MathContext
import java.util.Locale

class IngredientAmountParser {
    private val fractionReplacements = mapOf(
        '¼' to "1/4", '½' to "1/2", '¾' to "3/4", '⅓' to "1/3", '⅔' to "2/3",
        '⅛' to "1/8", '⅜' to "3/8", '⅝' to "5/8", '⅞' to "7/8",
    )
    private val numericPattern = "(?:\\d+\\s+\\d+/\\d+|\\d+/\\d+|\\d+(?:[.,]\\d+)?)"
    private val leadingNumeric = Regex("^($numericPattern)(?:\\s*(?:-|–|—|do)\\s*($numericPattern))?(?=\\s|$)")
    private val qualifierRegex = Regex("^(opcjonalnie(?:\\s*[:,])?|ok\\.?|około)\\s*", RegexOption.IGNORE_CASE)
    private val wordNumbers = mapOf(
        "jeden" to "1", "jedna" to "1", "jedno" to "1", "jedną" to "1",
        "dwa" to "2", "dwie" to "2", "trzy" to "3", "cztery" to "4", "pięć" to "5",
        "piec" to "5", "sześć" to "6", "szesc" to "6", "siedem" to "7", "osiem" to "8",
        "dziewięć" to "9", "dziewiec" to "9", "dziesięć" to "10", "dziesiec" to "10",
        "jedenaście" to "11", "jedenascie" to "11", "dwanaście" to "12", "dwanascie" to "12",
        "trzynaście" to "13", "trzynascie" to "13", "czternaście" to "14", "czternascie" to "14",
        "piętnaście" to "15", "pietnascie" to "15", "szesnaście" to "16", "szesnascie" to "16",
        "siedemnaście" to "17", "siedemnascie" to "17", "osiemnaście" to "18", "osiemnascie" to "18",
        "dziewiętnaście" to "19", "dziewietnascie" to "19", "dwadzieścia" to "20", "dwadziescia" to "20",
        "pół" to "0.5", "pol" to "0.5", "półtora" to "1.5", "półtorej" to "1.5",
        "poltora" to "1.5", "poltorej" to "1.5", "ćwierć" to "0.25", "cwierc" to "0.25",
    )

    fun parse(rawText: String): IngredientLine {
        val raw = rawText.trim().trimEnd(';')
        if (raw.isBlank()) return IngredientLine(rawText = raw)

        var working = normalizeFractions(raw)
        val qualifierMatch = qualifierRegex.find(working)
        val qualifier = qualifierMatch?.value?.trim()?.trimEnd(':', ',')
        if (qualifierMatch != null) working = working.drop(qualifierMatch.range.last + 1).trimStart()

        val lowered = working.lowercase(Locale.forLanguageTag("pl"))
        if ("do smaku" in lowered || lowered.startsWith("kilka ") || lowered == "kilka") {
            return IngredientLine(
                rawText = raw,
                qualifier = qualifier,
                body = working,
                parseStatus = ParseStatus.UNPARSED,
                scalable = false,
            )
        }

        var min: BigDecimal? = null
        var max: BigDecimal? = null
        var inferred = false

        val numeric = leadingNumeric.find(working)
        if (numeric != null) {
            min = parseNumber(numeric.groupValues[1])
            max = numeric.groupValues.getOrNull(2)?.takeIf(String::isNotBlank)?.let(::parseNumber)
            if (min != null) working = working.drop(numeric.value.length).trimStart()
        } else {
            val firstWord = working.substringBefore(' ').trimEnd(',', ':').lowercase(Locale.forLanguageTag("pl"))
            val wordValue = wordNumbers[firstWord]
            if (wordValue != null) {
                min = BigDecimal(wordValue)
                working = working.drop(firstWord.length).trimStart()
                inferred = true
            }
        }

        var unitMatch = UnitRegistry.match(working)
        if (min == null && unitMatch != null) {
            min = BigDecimal.ONE
            inferred = true
        }
        if (min != null && unitMatch != null) {
            working = working.drop(unitMatch.consumedCharacters).trimStart().trimStart(',', ':', '-')
        } else if (min == null) {
            unitMatch = null
        }

        if (min == null) {
            return IngredientLine(
                rawText = raw,
                qualifier = qualifier,
                body = working,
                parseStatus = ParseStatus.UNPARSED,
                scalable = false,
            )
        }

        return IngredientLine(
            rawText = raw,
            qualifier = qualifier,
            quantityMin = min.stripTrailingZeros().toPlainString(),
            quantityMax = max?.stripTrailingZeros()?.toPlainString(),
            unitKey = unitMatch?.definition?.key,
            body = working.trim(),
            parseStatus = if (inferred) ParseStatus.INFERRED else ParseStatus.EXACT,
            scalable = true,
        )
    }

    private fun normalizeFractions(value: String): String {
        var result = value.replace(Regex("(?<=\\d)([¼½¾⅓⅔⅛⅜⅝⅞])"), " $1")
        fractionReplacements.forEach { (symbol, fraction) -> result = result.replace(symbol.toString(), fraction) }
        return result.replace(Regex("\\s+"), " ").trim()
    }

    private fun parseNumber(token: String): BigDecimal? = runCatching {
        val normalized = token.trim().replace(',', '.')
        if (' ' in normalized && '/' in normalized) {
            val (whole, fraction) = normalized.split(Regex("\\s+"), limit = 2)
            BigDecimal(whole).add(parseFraction(fraction))
        } else if ('/' in normalized) {
            parseFraction(normalized)
        } else {
            BigDecimal(normalized)
        }
    }.getOrNull()

    private fun parseFraction(value: String): BigDecimal {
        val parts = value.split('/', limit = 2)
        val denominator = BigDecimal(parts[1])
        require(denominator.compareTo(BigDecimal.ZERO) != 0)
        return BigDecimal(parts[0]).divide(denominator, MathContext.DECIMAL128)
    }
}
