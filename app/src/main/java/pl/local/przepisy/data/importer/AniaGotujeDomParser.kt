package pl.local.przepisy.data.importer

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Ania Gotuje exposes Recipe microdata instead of a Recipe JSON-LD object.
 * Newer posts use HowToStep nodes; older posts keep paragraphs in one recipe body.
 */
object AniaGotujeDomParser {
    fun parse(document: Document): DomRecipe? {
        val root = document.selectFirst("article[itemtype*='schema.org/Recipe']") ?: return null
        val body = document.selectFirst(".article-content-body") ?: return null
        val title = root.selectFirst("h1[itemprop=name],h1")?.text()?.cleanAnia().orEmpty()
        if (title.isBlank()) return null

        val ingredientContainer = body.selectFirst(".post-ingredients") ?: return null
        val ingredientGroups = ingredientContainer.select(".post-ingredients-group")
            .mapNotNull { group -> group.toIngredientGroup(title) }
            .toMutableList()
        if (ingredientGroups.isEmpty()) {
            val lines = ingredientContainer.select("[itemprop=recipeIngredient]")
                .map { it.ingredientText() }
                .map { it.cleanAnia() }
                .filter(String::isNotBlank)
            if (lines.isNotEmpty()) ingredientGroups += "Składniki" to lines
        }

        val knownIngredients = ingredientGroups.flatMap { it.second }
            .map { it.normalizedAnia() }
            .toSet()
        val additional = legacyAdditionalIngredients(ingredientContainer)
            .filterNot { it.normalizedAnia() in knownIngredients }
        if (additional.isNotEmpty()) ingredientGroups += "Dodatkowo" to additional

        val instructionLines = modernInstructions(body).ifEmpty {
            legacyInstructions(ingredientContainer)
        }
        if (ingredientGroups.sumOf { it.second.size } == 0 || instructionLines.isEmpty()) return null

        val description = root.selectFirst("meta[itemprop=description]")
            ?.attr("content")
            ?.cleanAnia()
            ?.takeIf(String::isNotBlank)
            ?: document.selectFirst("meta[name=description]")?.attr("content")?.cleanAnia().orEmpty()
        val yield = root.selectFirst("meta[itemprop=recipeYield]")
            ?.attr("content")
            ?.cleanAnia()
            .orEmpty()
        val image = root.selectFirst("meta[itemprop=image]")
            ?.attr("content")
            ?.takeIf(String::isNotBlank)
            ?: document.selectFirst("meta[property=og:image]")?.attr("content")?.takeIf(String::isNotBlank)

        return DomRecipe(
            title = title,
            description = description,
            yield = yield,
            imageUrl = image,
            ingredientGroups = ingredientGroups,
            instructionGroups = listOf("Wykonanie" to instructionLines),
        )
    }

    private fun Element.toIngredientGroup(recipeTitle: String): Pair<String, List<String>>? {
        val lines = select("[itemprop=recipeIngredient]")
            .map { it.ingredientText() }
            .map { it.cleanAnia() }
            .filter(String::isNotBlank)
        if (lines.isEmpty()) return null
        val rawHeading = selectFirst(".post-ingredients-group-header strong,.ing-header strong")
            ?.text()
            ?.cleanAnia()
            .orEmpty()
        return ingredientGroupTitle(rawHeading, recipeTitle) to lines
    }

    private fun modernInstructions(body: Element): List<String> {
        val container = body.selectFirst(".steps") ?: return emptyList()
        val steps = container.select(".step[itemprop=recipeInstructions]")
        if (steps.isEmpty()) return emptyList()

        val instructions = mutableListOf<String>()
        val prelude = container.parent()?.children()
            ?.takeWhile { it !== container }
            ?.filter { it.tagName() == "p" }
            ?.map(Element::text)
            ?.map { it.cleanAnia() }
            ?.filter(String::isNotBlank)
            .orEmpty()
        if (prelude.isNotEmpty()) instructions += prelude.joinToString("\n\n")

        steps.mapNotNullTo(instructions) { step ->
            val name = step.selectFirst(".step-name [itemprop=name],[itemprop=name]")
                ?.text()
                ?.cleanAnia()
                .orEmpty()
            val text = step.selectFirst(".step-text[itemprop=text],[itemprop=text]")
                ?.text()
                ?.cleanAnia()
                .orEmpty()
            when {
                name.isBlank() -> text.takeIf(String::isNotBlank)
                text.isBlank() -> name
                text.startsWith(name, ignoreCase = true) -> text
                else -> "$name\n\n$text"
            }
        }
        return instructions.filterNot { it.isEnjoymentOnly() }
    }

    private fun legacyInstructions(ingredientContainer: Element): List<String> {
        val content = generateSequence(ingredientContainer.nextElementSibling()) { it.nextElementSibling() }
            .firstOrNull { it.select("p").isNotEmpty() }
            ?: return emptyList()
        val instructions = mutableListOf<String>()
        for (paragraph in content.select("p")) {
            if (paragraph.parents().any { it.isAniaExcluded() }) continue
            val text = paragraph.text().cleanAnia()
            if (text.isEnjoymentOnly()) break
            if (text.isBlank() || text.matches(Regex("^-{3,}$"))) continue
            instructions += text
        }
        return instructions
    }

    private fun legacyAdditionalIngredients(ingredientContainer: Element): List<String> {
        val text = generateSequence(ingredientContainer.nextElementSibling()) { it.nextElementSibling() }
            .take(4)
            .map(Element::ownText)
            .firstOrNull { it.contains("Dodatkowo:", ignoreCase = true) }
            ?.substringAfter(':')
            ?: return emptyList()
        return text.split('•')
            .map { it.cleanAnia() }
            .filter(String::isNotBlank)
    }

    private fun Element.isAniaExcluded(): Boolean {
        val marker = "${id()} ${className()} ${attr("role")}".lowercase()
        return listOf("advert", "reklam", "vote", "comment", "related", "newsletter").any(marker::contains)
    }

    private fun ingredientGroupTitle(raw: String, recipeTitle: String): String {
        if (raw.isBlank() || raw.equals("Składniki", ignoreCase = true)) return "Składniki"
        val suffix = raw.replaceFirst(Regex("^składniki(?:\\s+na)?\\s*", RegexOption.IGNORE_CASE), "")
            .trim(' ', ':', '-', '–')
        if (suffix.isBlank() || suffix.equals(recipeTitle, ignoreCase = true)) return "Składniki"
        when (suffix.lowercase()) {
            "kruszonkę" -> return "Kruszonka"
            "masę" -> return "Masa"
            "polewę" -> return "Polewa"
        }
        return suffix.replaceFirstChar { it.uppercase() }
    }

    private fun String.isEnjoymentOnly(): Boolean =
        trim().startsWith("Smacznego", ignoreCase = true) && length <= 30

    /**
     * Current posts render the human-readable name before the amount even though
     * the amount parser (and the editor) use the conventional amount-first form.
     * Older posts have no child spans, so their original text remains untouched.
     */
    private fun Element.ingredientText(): String {
        val name = selectFirst(".ingredient-name")?.text()?.cleanAnia().orEmpty()
        val quantity = selectFirst(".ingredient-qty")?.text()?.cleanAnia().orEmpty()
        return if (name.isNotBlank() && quantity.isNotBlank()) "$quantity $name" else text()
    }

    private fun String.normalizedAnia(): String = cleanAnia().lowercase()

    private fun String.cleanAnia(): String = replace('\u00a0', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
        .trimEnd(';')
}
