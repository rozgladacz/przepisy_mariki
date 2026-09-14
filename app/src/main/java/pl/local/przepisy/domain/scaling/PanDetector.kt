package pl.local.przepisy.domain.scaling

import pl.local.przepisy.domain.model.PanShape
import pl.local.przepisy.domain.model.PanSpec

object PanDetector {
    private val rectangle = Regex("(\\d+(?:[,.]\\d+)?)\\s*[x×]\\s*(\\d+(?:[,.]\\d+)?)\\s*cm", RegexOption.IGNORE_CASE)
    private val circle = Regex("(?:tortownic(?:a|y|ę)|średnic(?:a|y|ę))[^\\d]{0,24}(\\d+(?:[,.]\\d+)?)\\s*cm", RegexOption.IGNORE_CASE)

    fun detect(text: String): PanSpec? {
        rectangle.find(text)?.let { match ->
            return PanSpec(
                shape = PanShape.RECTANGLE,
                widthCm = match.groupValues[1].toDoublePl(),
                heightCm = match.groupValues[2].toDoublePl(),
            )
        }
        circle.find(text)?.let { match ->
            return PanSpec(
                shape = PanShape.CIRCLE,
                diameterCm = match.groupValues[1].toDoublePl(),
            )
        }
        return null
    }

    private fun String.toDoublePl(): Double = replace(',', '.').toDouble()
}
