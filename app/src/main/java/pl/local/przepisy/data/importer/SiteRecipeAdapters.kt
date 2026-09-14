package pl.local.przepisy.data.importer

import org.jsoup.nodes.Document
import pl.local.przepisy.domain.model.IngredientSection
import pl.local.przepisy.domain.model.InstructionSection
import pl.local.przepisy.domain.model.RecipeDraft
import pl.local.przepisy.domain.model.newId
import pl.local.przepisy.domain.quantity.IngredientAmountParser
import pl.local.przepisy.domain.scaling.PanDetector

abstract class BaseRecipeSourceAdapter(
    private val sourceName: String,
    private val amountParser: IngredientAmountParser,
    private val jsonLd: JsonLdRecipeExtractor,
) : RecipeSourceAdapter {
    protected open fun parseDom(document: Document): DomRecipe? = DomRecipeParser.parse(document)

    protected open fun sanitizeInstructionGroups(
        groups: List<Pair<String, List<String>>>,
    ): List<Pair<String, List<String>>> = groups

    final override fun parse(document: Document, canonicalUrl: String): RecipeDraft? {
        jsonLd.extract(document)?.let { extracted ->
            return draft(
                title = extracted.title,
                description = extracted.description,
                yield = extracted.yield,
                imageUrl = resolveUrl(document, extracted.imageUrl),
                canonicalUrl = canonicalUrl,
                ingredientGroups = listOf("Składniki" to extracted.ingredients),
                instructionGroups = listOf("Wykonanie" to extracted.instructions),
                warnings = emptyList(),
            )
        }
        val dom = parseDom(document) ?: return null
        return draft(
            title = dom.title,
            description = dom.description,
            yield = dom.yield,
            imageUrl = resolveUrl(document, dom.imageUrl),
            canonicalUrl = canonicalUrl,
            ingredientGroups = dom.ingredientGroups,
            instructionGroups = dom.instructionGroups,
            warnings = listOf("Przepis rozpoznano z układu strony. Sprawdź składniki i instrukcję przed zapisem."),
        )
    }

    private fun draft(
        title: String,
        description: String,
        yield: String,
        imageUrl: String?,
        canonicalUrl: String,
        ingredientGroups: List<Pair<String, List<String>>>,
        instructionGroups: List<Pair<String, List<String>>>,
        warnings: List<String>,
    ): RecipeDraft {
        val ingredients = ingredientGroups.map { (heading, lines) ->
            IngredientSection(
                id = newId(),
                title = heading,
                lines = lines.filter(String::isNotBlank).map(amountParser::parse),
            )
        }.filter { it.lines.isNotEmpty() }
        val instructions = sanitizeInstructionGroups(instructionGroups).map { (heading, steps) ->
            InstructionSection(
                id = newId(),
                title = heading,
                steps = steps.map(String::trim).filter(String::isNotBlank),
            )
        }.filter { it.steps.isNotEmpty() }
        val panText = buildString {
            append(yield)
            append(' ')
            ingredients.flatMap(IngredientSection::lines).forEach { append(it.rawText).append(' ') }
            instructions.flatMap(InstructionSection::steps).forEach { append(it).append(' ') }
        }
        return RecipeDraft(
            title = title,
            description = description,
            sourceName = sourceName,
            sourceUrl = canonicalUrl,
            originalYield = yield,
            imageUrl = imageUrl,
            pan = PanDetector.detect(panText),
            ingredientSections = ingredients,
            instructionSections = instructions,
            warnings = warnings,
        )
    }

    private fun resolveUrl(document: Document, value: String?): String? {
        if (value.isNullOrBlank()) return null
        return runCatching { java.net.URI(document.baseUri()).resolve(value).toString() }.getOrDefault(value)
    }
}

class MojeWypiekiAdapter(
    amountParser: IngredientAmountParser,
    jsonLd: JsonLdRecipeExtractor,
) : BaseRecipeSourceAdapter("Moje Wypieki", amountParser, jsonLd) {
    override val supportedHosts: Set<String> = setOf("mojewypieki.com")

    override fun sanitizeInstructionGroups(
        groups: List<Pair<String, List<String>>>,
    ): List<Pair<String, List<String>>> {
        val lastGroup = groups.indexOfLast { (_, steps) -> steps.any(String::isNotBlank) }
        if (lastGroup < 0) return groups
        return groups.mapIndexed { index, (heading, steps) ->
            if (index != lastGroup) {
                heading to steps
            } else {
                val cleaned = steps.toMutableList()
                val lastStep = cleaned.indexOfLast(String::isNotBlank)
                if (lastStep >= 0 && cleaned[lastStep].isEnjoymentOnly()) cleaned.removeAt(lastStep)
                heading to cleaned
            }
        }
    }

    private fun String.isEnjoymentOnly(): Boolean = trim()
        .trimEnd('!', '.')
        .trim()
        .equals("Smacznego", ignoreCase = true)
}

class AlaantkoweblwAdapter(
    amountParser: IngredientAmountParser,
    jsonLd: JsonLdRecipeExtractor,
) : BaseRecipeSourceAdapter("Alaantkoweblw", amountParser, jsonLd) {
    override val supportedHosts: Set<String> = setOf("alaantkoweblw.pl")

    override fun parseDom(document: Document): DomRecipe? =
        DomRecipeParser.parse(document) ?: AlaantkoweblwLegacyParser.parse(document)
}

class RozkosznyAdapter(
    amountParser: IngredientAmountParser,
    jsonLd: JsonLdRecipeExtractor,
) : BaseRecipeSourceAdapter("Rozkoszny", amountParser, jsonLd) {
    override val supportedHosts: Set<String> = setOf("rozkoszny.pl")

    override fun parseDom(document: Document): DomRecipe? =
        DomRecipeParser.parse(document) ?: RozkosznyDomParser.parse(document)
}

class AniaGotujeAdapter(
    amountParser: IngredientAmountParser,
    jsonLd: JsonLdRecipeExtractor,
) : BaseRecipeSourceAdapter("Ania Gotuje", amountParser, jsonLd) {
    override val supportedHosts: Set<String> = setOf("aniagotuje.pl")

    override fun parseDom(document: Document): DomRecipe? = AniaGotujeDomParser.parse(document)
}
