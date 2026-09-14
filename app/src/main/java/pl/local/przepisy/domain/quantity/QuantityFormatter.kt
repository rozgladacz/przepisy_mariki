package pl.local.przepisy.domain.quantity

import pl.local.przepisy.domain.model.IngredientLine
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.floor

object QuantityFormatter {
    private val wholeMetricUnits = setOf("gram", "dekagram", "mililitr")

    private data class DisplayAmount(
        val text: String,
        val roundedValue: BigDecimal,
    )

    private val glyphs = mapOf(
        Pair(1, 8) to "⅛", Pair(1, 4) to "¼", Pair(1, 3) to "⅓", Pair(3, 8) to "⅜",
        Pair(1, 2) to "½", Pair(5, 8) to "⅝", Pair(2, 3) to "⅔", Pair(3, 4) to "¾",
        Pair(7, 8) to "⅞",
    )

    fun formatIngredient(
        ingredient: IngredientLine,
        factor: BigDecimal,
        approximate: Boolean = false,
    ): String {
        if (!ingredient.scalable || ingredient.quantityMin == null) return ingredient.rawText

        val minimum = ingredient.quantityMin.toBigDecimalOrNull()?.multiply(factor) ?: return ingredient.rawText
        val maximum = ingredient.quantityMax?.let { value ->
            value.toBigDecimalOrNull()?.multiply(factor) ?: return ingredient.rawText
        }
        val minimumDisplay = formatIngredientAmount(minimum, ingredient.unitKey)
        val maximumDisplay = maximum?.let { formatIngredientAmount(it, ingredient.unitKey) }
        val amount = if (maximumDisplay == null) {
            minimumDisplay.text
        } else {
            "${minimumDisplay.text}–${maximumDisplay.text}"
        }
        val inflectionValue = maximumDisplay?.roundedValue ?: minimumDisplay.roundedValue
        val unit = UnitRegistry.label(ingredient.unitKey, inflectionValue)
        return listOfNotNull(
            ingredient.qualifier,
            (if (approximate) "≈" else "") + amount,
            unit,
            ingredient.body.takeIf(String::isNotBlank),
        ).joinToString(" ")
    }

    private fun formatIngredientAmount(value: BigDecimal, unitKey: String?): DisplayAmount {
        if (unitKey in wholeMetricUnits) {
            val rounded = value.setScale(0, RoundingMode.HALF_UP)
            return DisplayAmount(rounded.toPlainString(), rounded)
        }

        val eighths = value.multiply(BigDecimal(8)).setScale(0, RoundingMode.HALF_UP)
        val rounded = eighths.divide(BigDecimal(8))
        return DisplayAmount(formatEighths(eighths.toLong()), rounded)
    }

    private fun formatEighths(eighths: Long): String {
        val sign = if (eighths < 0) "-" else ""
        val absolute = kotlin.math.abs(eighths)
        val whole = absolute / 8
        val remainder = (absolute % 8).toInt()
        if (remainder == 0) return "$sign$whole"

        val divisor = greatestCommonDivisor(remainder, 8)
        val numerator = remainder / divisor
        val denominator = 8 / divisor
        val fraction = "$numerator/$denominator"
        return when {
            whole == 0L -> "$sign$fraction"
            else -> "$sign$whole $fraction"
        }
    }

    private fun greatestCommonDivisor(first: Int, second: Int): Int {
        var a = first
        var b = second
        while (b != 0) {
            val next = a % b
            a = b
            b = next
        }
        return a
    }

    fun formatNumber(value: BigDecimal): String {
        val number = value.toDouble()
        val whole = floor(number).toLong()
        val remainder = number - whole
        if (abs(remainder) < 0.0005) return whole.toString()

        val best = glyphs.keys.minByOrNull { (numerator, denominator) ->
            abs(remainder - numerator.toDouble() / denominator)
        }
        if (best != null) {
            val difference = abs(remainder - best.first.toDouble() / best.second)
            if (difference < 0.006) {
                return (if (whole == 0L) "" else whole.toString()) + glyphs.getValue(best)
            }
        }

        return value.setScale(3, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
            .replace('.', ',')
    }
}
