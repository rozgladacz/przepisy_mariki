package pl.local.przepisy.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

@Serializable
enum class ParseStatus {
    EXACT,
    INFERRED,
    UNPARSED,
}

@Serializable
enum class PanShape {
    RECTANGLE,
    CIRCLE,
}

@Serializable
data class PanSpec(
    val shape: PanShape,
    val widthCm: Double? = null,
    val heightCm: Double? = null,
    val diameterCm: Double? = null,
) {
    fun isValid(): Boolean = when (shape) {
        PanShape.RECTANGLE -> widthCm != null && heightCm != null && widthCm > 0 && heightCm > 0
        PanShape.CIRCLE -> diameterCm != null && diameterCm > 0
    }
}

@Serializable
data class PanPreset(
    val id: String = newId(),
    val name: String,
    val pan: PanSpec,
)

@Serializable
data class IngredientLine(
    val id: String = newId(),
    val rawText: String,
    val qualifier: String? = null,
    val quantityMin: String? = null,
    val quantityMax: String? = null,
    val unitKey: String? = null,
    val body: String = rawText,
    val parseStatus: ParseStatus = ParseStatus.UNPARSED,
    val scalable: Boolean = false,
)

@Serializable
data class IngredientSection(
    val id: String = newId(),
    val title: String = "Składniki",
    val lines: List<IngredientLine> = emptyList(),
)

@Serializable
data class InstructionSection(
    val id: String = newId(),
    val title: String = "Wykonanie",
    val steps: List<String> = emptyList(),
)

@Serializable
data class Recipe(
    val id: String = newId(),
    val title: String,
    val description: String = "",
    val sourceName: String,
    val sourceUrl: String,
    val originalYield: String = "",
    val imagePath: String? = null,
    val pan: PanSpec? = null,
    val ingredientSections: List<IngredientSection>,
    val instructionSections: List<InstructionSection>,
    val favorite: Boolean = false,
    val tags: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

data class RecipeSummary(
    val id: String,
    val title: String,
    val sourceName: String,
    val imagePath: String?,
    val favorite: Boolean,
    val tags: List<String>,
    val updatedAt: Long,
)

data class RecipeDraft(
    val title: String,
    val description: String = "",
    val sourceName: String,
    val sourceUrl: String,
    val originalYield: String = "",
    val imageUrl: String? = null,
    val existingImagePath: String? = null,
    val pan: PanSpec? = null,
    val ingredientSections: List<IngredientSection>,
    val instructionSections: List<InstructionSection>,
    val warnings: List<String> = emptyList(),
)

fun Recipe.toDraft(): RecipeDraft = RecipeDraft(
    title = title,
    description = description,
    sourceName = sourceName,
    sourceUrl = sourceUrl,
    originalYield = originalYield,
    existingImagePath = imagePath,
    pan = pan,
    ingredientSections = ingredientSections,
    instructionSections = instructionSections,
)

fun RecipeDraft.toRecipe(existing: Recipe? = null, imagePath: String? = existingImagePath): Recipe {
    val now = System.currentTimeMillis()
    return Recipe(
        id = existing?.id ?: newId(),
        title = title.trim(),
        description = description.trim(),
        sourceName = sourceName,
        sourceUrl = sourceUrl,
        originalYield = originalYield.trim(),
        imagePath = imagePath,
        pan = pan?.takeIf(PanSpec::isValid),
        ingredientSections = ingredientSections,
        instructionSections = instructionSections,
        favorite = existing?.favorite ?: false,
        tags = existing?.tags.orEmpty(),
        createdAt = existing?.createdAt ?: now,
        updatedAt = now,
    )
}
