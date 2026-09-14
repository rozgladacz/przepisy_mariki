package pl.local.przepisy.data.local

import pl.local.przepisy.domain.model.IngredientLine
import pl.local.przepisy.domain.model.IngredientSection
import pl.local.przepisy.domain.model.InstructionSection
import pl.local.przepisy.domain.model.PanShape
import pl.local.przepisy.domain.model.PanSpec
import pl.local.przepisy.domain.model.ParseStatus
import pl.local.przepisy.domain.model.Recipe
import pl.local.przepisy.domain.model.RecipeSummary
import java.text.Normalizer
import java.util.Locale

fun RecipeWithDetails.toDomain(): Recipe = Recipe(
    id = recipe.id,
    title = recipe.title,
    description = recipe.description,
    sourceName = recipe.sourceName,
    sourceUrl = recipe.sourceUrl,
    originalYield = recipe.originalYield,
    imagePath = recipe.imagePath,
    pan = recipe.panShape?.let {
        PanSpec(
            shape = PanShape.valueOf(it),
            widthCm = recipe.panWidthCm,
            heightCm = recipe.panHeightCm,
            diameterCm = recipe.panDiameterCm,
        )
    },
    ingredientSections = ingredientSections.sortedBy { it.section.position }.map { aggregate ->
        IngredientSection(
            id = aggregate.section.id,
            title = aggregate.section.title,
            lines = aggregate.lines.sortedBy { it.position }.map { line ->
                IngredientLine(
                    id = line.id,
                    rawText = line.rawText,
                    qualifier = line.qualifier,
                    quantityMin = line.quantityMin,
                    quantityMax = line.quantityMax,
                    unitKey = line.unitKey,
                    body = line.body,
                    parseStatus = ParseStatus.valueOf(line.parseStatus),
                    scalable = line.scalable,
                )
            },
        )
    },
    instructionSections = instructionSections.sortedBy { it.section.position }.map { aggregate ->
        InstructionSection(
            id = aggregate.section.id,
            title = aggregate.section.title,
            steps = aggregate.steps.sortedBy { it.position }.map(InstructionEntity::text),
        )
    },
    favorite = recipe.favorite,
    tags = tags.sortedBy { it.name.lowercase(Locale.forLanguageTag("pl")) }.map(TagEntity::name),
    createdAt = recipe.createdAt,
    updatedAt = recipe.updatedAt,
)

fun RecipeWithTags.toSummary(): RecipeSummary = RecipeSummary(
    id = recipe.id,
    title = recipe.title,
    sourceName = recipe.sourceName,
    imagePath = recipe.imagePath,
    favorite = recipe.favorite,
    tags = tags.sortedBy { it.name.lowercase(Locale.forLanguageTag("pl")) }.map(TagEntity::name),
    updatedAt = recipe.updatedAt,
)

fun Recipe.toEntity(): RecipeEntity = RecipeEntity(
    id = id,
    title = title,
    description = description,
    sourceName = sourceName,
    sourceUrl = sourceUrl,
    originalYield = originalYield,
    imagePath = imagePath,
    panShape = pan?.shape?.name,
    panWidthCm = pan?.widthCm,
    panHeightCm = pan?.heightCm,
    panDiameterCm = pan?.diameterCm,
    favorite = favorite,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Recipe.searchContent(): String = SearchNormalizer.normalize(
    buildString {
        append(title)
        append(' ')
        ingredientSections.flatMap(IngredientSection::lines).forEach {
            append(it.rawText)
            append(' ')
        }
    },
)

object SearchNormalizer {
    fun normalize(value: String): String = Normalizer.normalize(
        value.lowercase(Locale.forLanguageTag("pl")).replace('ł', 'l'),
        Normalizer.Form.NFD,
    )
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    fun toFtsQuery(value: String): String = normalize(value)
        .split(Regex("\\s+"))
        .filter(String::isNotBlank)
        .joinToString(" AND ") { "${it.replace("\"", "") }*" }
}

fun normalizeTags(tags: Iterable<String>): List<TagEntity> = tags
    .map(String::trim)
    .filter(String::isNotBlank)
    .mapNotNull { name ->
        SearchNormalizer.normalize(name).takeIf(String::isNotBlank)?.let { id -> TagEntity(id, name) }
    }
    .distinctBy(TagEntity::id)
