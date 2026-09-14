package pl.local.przepisy.data.importer

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

data class ExtractedRecipe(
    val title: String,
    val description: String,
    val yield: String,
    val imageUrl: String?,
    val ingredients: List<String>,
    val instructions: List<String>,
)

class JsonLdRecipeExtractor {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun extract(document: Document): ExtractedRecipe? {
        document.select("script[type=application/ld+json]").forEach { script ->
            val element = runCatching { json.parseToJsonElement(script.data().ifBlank { script.html() }) }.getOrNull()
                ?: return@forEach
            val recipe = findRecipe(element) ?: return@forEach
            val title = recipe.string("name")?.clean().orEmpty()
            val ingredients = recipe["recipeIngredient"].strings().map { it.clean() }.filter(String::isNotBlank)
            val instructions = instructionTexts(recipe["recipeInstructions"])
                .map { it.clean() }
                .filter(String::isNotBlank)
            if (title.isBlank() || ingredients.isEmpty() || instructions.isEmpty()) return@forEach
            return ExtractedRecipe(
                title = title,
                description = recipe.string("description")?.clean().orEmpty(),
                yield = recipe["recipeYield"].strings().joinToString(", ").clean(),
                imageUrl = imageUrl(recipe["image"]),
                ingredients = ingredients,
                instructions = instructions,
            )
        }
        return null
    }

    private fun findRecipe(element: JsonElement?): JsonObject? = when (element) {
        is JsonArray -> element.firstNotNullOfOrNull(::findRecipe)
        is JsonObject -> {
            if (element.isRecipe()) element
            else element["@graph"]?.let(::findRecipe)
                ?: element.values.firstNotNullOfOrNull(::findRecipe)
        }
        else -> null
    }

    private fun JsonObject.isRecipe(): Boolean {
        val type = this["@type"]
        return when (type) {
            is JsonPrimitive -> type.contentOrNull.equals("Recipe", ignoreCase = true)
            is JsonArray -> type.any { (it as? JsonPrimitive)?.contentOrNull.equals("Recipe", ignoreCase = true) }
            else -> false
        }
    }

    private fun instructionTexts(element: JsonElement?): List<String> = when (element) {
        null -> emptyList()
        is JsonPrimitive -> listOfNotNull(element.contentOrNull)
        is JsonArray -> element.flatMap(::instructionTexts)
        is JsonObject -> {
            val nested = element["itemListElement"] ?: element["steps"]
            if (nested != null) instructionTexts(nested)
            else listOfNotNull(element.string("text") ?: element.string("name"))
        }
    }

    private fun imageUrl(element: JsonElement?): String? = when (element) {
        is JsonPrimitive -> element.contentOrNull
        is JsonArray -> element.firstNotNullOfOrNull(::imageUrl)
        is JsonObject -> element.string("url") ?: element.string("contentUrl")
        else -> null
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonElement?.strings(): List<String> = when (this) {
        is JsonPrimitive -> listOfNotNull(contentOrNull)
        is JsonArray -> flatMap { it.strings() }
        else -> emptyList()
    }

    private fun String.clean(): String = Jsoup.parse(this).text().replace(Regex("\\s+"), " ").trim()
}
