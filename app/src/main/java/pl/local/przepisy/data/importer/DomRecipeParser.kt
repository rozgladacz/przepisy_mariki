package pl.local.przepisy.data.importer

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

data class DomRecipe(
    val title: String,
    val description: String,
    val yield: String,
    val imageUrl: String?,
    val ingredientGroups: List<Pair<String, List<String>>>,
    val instructionGroups: List<Pair<String, List<String>>>,
)

object DomRecipeParser {
    private val ingredientHeading = Regex("składnik|dodatkowo", RegexOption.IGNORE_CASE)
    private val instructionHeading = Regex("wykonanie|przygotowanie|sposób", RegexOption.IGNORE_CASE)
    private val plainIngredientHeading = Regex("^(?:składniki|dodatkowo)\\s*:?$", RegexOption.IGNORE_CASE)
    private val plainInstructionHeading = Regex(
        "^(?:wykonanie|przygotowanie|sposób przygotowania)\\s*:?$",
        RegexOption.IGNORE_CASE,
    )
    private val boundaryHeading = Regex("komentar|opini|newsletter|podobne|zobacz także|polecane", RegexOption.IGNORE_CASE)
    private val boundaryText = Regex(
        "^(?:przepis(?:,|\\s).*pochodzi|inspiracja(?:\\s|:)|przegl.daj wpisy po sk.adnikach|tagi\\s*:|kategoria\\s*:)",
        RegexOption.IGNORE_CASE,
    )
    private val excludedMarker = Regex(
        "comment|komentar|advert|reklam|related|polecan|newsletter|social|share",
        RegexOption.IGNORE_CASE,
    )
    private val ingredientLike = Regex(
        "^(?:\\d|[¼½¾⅓⅔⅛⅜⅝⅞]|pół|półtora|półtorej|ćwierć|jeden|jedna|jedno|dwa|dwie|trzy|cztery|pięć|sześć|siedem|osiem|dziewięć|dziesięć|garść|szczypta|pęczek|opakowanie|puszka)",
        RegexOption.IGNORE_CASE,
    )

    fun parse(document: Document): DomRecipe? {
        val root = document.select(
            "section.recipe,.article__content,.main-content-container.wysiwyg-content,.entry-content,.post-content",
        )
            .firstOrNull { it.containsRecipeStart() }
            ?: document.selectFirst("article")?.takeIf { it.containsRecipeStart() }
            ?: document.selectFirst("main")?.takeIf { it.containsRecipeStart() }
            ?: document.body().takeIf { it.containsRecipeStart() }
            ?: return null
        val elements = root.recipeElements()
        val title = document.selectFirst("h1")?.text()?.clean().orEmpty()
        if (title.isBlank()) return null

        val firstIngredientHeading = elements.indexOfFirst { it.isIngredientHeading() }
        val firstIngredient = if (firstIngredientHeading >= 0) {
            firstIngredientHeading
        } else {
            elements.indexOfFirst { it.isIngredientItem() }
        }
        if (firstIngredient < 0) return null

        val ingredientGroups = mutableListOf<Pair<String, MutableList<String>>>()
        val instructions = mutableListOf<String>()
        var currentIngredients: MutableList<String>? = if (firstIngredientHeading < 0) {
            mutableListOf<String>().also { ingredientGroups += "Składniki" to it }
        } else {
            null
        }
        var mode = if (firstIngredientHeading < 0) ParseMode.INGREDIENTS else ParseMode.BEFORE_RECIPE
        for (index in firstIngredient until elements.size) {
            val element = elements[index]
            val text = element.text().clean()
            if (text.isBlank()) continue
            if (boundaryText.containsMatchIn(text)) break

            if (element.isIngredientHeading()) {
                currentIngredients = mutableListOf<String>().also {
                    ingredientGroups += ingredientSectionTitle(text) to it
                }
                mode = ParseMode.INGREDIENTS
                continue
            }
            if (element.isInstructionHeading()) {
                currentIngredients = null
                mode = ParseMode.INSTRUCTIONS
                continue
            }
            if (element.isHeadingLike()) {
                if (mode == ParseMode.INSTRUCTIONS && instructions.isNotEmpty()) break
                continue
            }
            if (text.length < 2 || boundaryHeading.containsMatchIn(text)) continue

            when (mode) {
                ParseMode.BEFORE_RECIPE -> Unit
                ParseMode.INGREDIENTS -> {
                    if (element.isIngredientItem() || (element.tagName() == "p" && ingredientLike.containsMatchIn(text))) {
                        currentIngredients?.add(element.ingredientText())
                    } else if (element.tagName() in setOf("p", "li", "div")) {
                        currentIngredients = null
                        mode = ParseMode.INSTRUCTIONS
                        instructions += text
                    }
                }
                ParseMode.INSTRUCTIONS -> if (element.tagName() in setOf("p", "li", "div")) instructions += text
            }
        }

        val ingredientLines = ingredientGroups.sumOf { it.second.size }
        if (ingredientLines == 0) return null
        if (instructions.isEmpty()) return null

        val image = document.selectFirst("meta[property=og:image]")?.attr("content")?.takeIf(String::isNotBlank)
            ?: root.selectFirst("img[src]")?.absUrl("src")?.takeIf(String::isNotBlank)
        val description = document.selectFirst("meta[name=description]")?.attr("content")?.clean()?.takeIf(String::isNotBlank)
            ?: document.findAlaantkoweIntro()
            ?: elements.take(firstIngredient).asReversed().firstOrNull {
                it.tagName() in setOf("h2", "h3") && !it.text().equals(title, ignoreCase = true)
            }?.text()?.clean()
            ?: document.selectFirst(".post-header__excerpt")?.text()?.clean()
            ?: ""
        val yield = document.select("h2,h3").firstOrNull { it.text().contains("składnik", ignoreCase = true) }
            ?.text()?.substringAfter("na ", "")?.clean().orEmpty()
        return DomRecipe(
            title = title,
            description = description,
            yield = yield,
            imageUrl = image,
            ingredientGroups = ingredientGroups.map { it.first to it.second.toList() },
            instructionGroups = listOf("Wykonanie" to instructions),
        )
    }

    private enum class ParseMode { BEFORE_RECIPE, INGREDIENTS, INSTRUCTIONS }

    private fun Element.recipeElements(): List<Element> =
        select("h1,h2,h3,h4,p,li,div.recipe__ingredients-item,.wysiwyg-content > div")
        .filterNot { it.tagName() == "p" && it.parents().any { parent -> parent.tagName() == "li" } }
        .filterNot { it.isExcluded() }

    private fun Element.containsRecipeStart(): Boolean = recipeElements().any { it.isIngredientHeading() }
        || select("div.recipe__ingredients-item").isNotEmpty()

    private fun Element.isHeading(): Boolean = tagName() in setOf("h1", "h2", "h3", "h4")
    private fun Element.isHeadingLike(): Boolean {
        if (isHeading()) return true
        if (tagName() != "p" || ownText().isNotBlank()) return false
        val decoratedChild = children().singleOrNull() ?: return false
        return decoratedChild.tagName() in setOf("strong", "u") ||
            decoratedChild.attr("style").contains("underline", ignoreCase = true)
    }
    private fun Element.isIngredientHeading(): Boolean =
        (isHeadingLike() && ingredientHeading.containsMatchIn(text())) ||
            (tagName() == "p" && plainIngredientHeading.matches(text().clean()))

    private fun Element.isInstructionHeading(): Boolean =
        (isHeadingLike() && instructionHeading.containsMatchIn(text())) ||
            (tagName() == "p" && plainInstructionHeading.matches(text().clean()))
    private fun Element.isIngredientItem(): Boolean = tagName() == "li" || hasClass("recipe__ingredients-item")
    private fun Element.ingredientText(): String {
        val copy = clone()
        copy.select("[style*=color]").filter {
            it.attr("style").contains("999") || it.attr("style").contains("gray", ignoreCase = true)
        }.forEach(Element::remove)
        return copy.text().clean()
    }
    private fun Element.isExcluded(): Boolean = (parents() + this).any { element ->
        val marker = "${element.id()} ${element.className()} ${element.attr("role")}"
        excludedMarker.containsMatchIn(marker)
    }
    private fun String.clean(): String = replace(Regex("\\s+"), " ").trim().trimEnd(';')

    private fun ingredientSectionTitle(raw: String): String {
        val text = raw.clean().trimEnd(':')
        if (text.startsWith("dodatkowo", ignoreCase = true)) return "Dodatkowo"
        val match = Regex("składniki(?:\\s+na)?\\s*", RegexOption.IGNORE_CASE).find(text) ?: return text
        if (match.range.first > 0) return "Składniki"
        val suffix = text.substring(match.range.last + 1).trim(' ', ':', '-', '–')
        if (suffix.isBlank() || Regex("(?:około|porcj|sztuk)", RegexOption.IGNORE_CASE).containsMatchIn(suffix)) {
            return "Składniki"
        }
        return suffix.replaceFirstChar { it.uppercase() }
    }

    private fun Document.findAlaantkoweIntro(): String? = select(".section__title").asSequence()
        .mapNotNull { heading -> heading.parent()?.selectFirst(".wysiwyg-content h2")?.text()?.clean() }
        .firstOrNull(String::isNotBlank)
}
