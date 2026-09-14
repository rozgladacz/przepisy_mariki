package pl.local.przepisy.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pl.local.przepisy.data.local.AppDatabase
import pl.local.przepisy.data.local.IngredientEntity
import pl.local.przepisy.data.local.IngredientSectionEntity
import pl.local.przepisy.data.local.InstructionEntity
import pl.local.przepisy.data.local.InstructionSectionEntity
import pl.local.przepisy.data.local.RecipeFtsEntity
import pl.local.przepisy.data.local.RecipeTagCrossRef
import pl.local.przepisy.data.local.SearchNormalizer
import pl.local.przepisy.data.local.normalizeTags
import pl.local.przepisy.data.local.searchContent
import pl.local.przepisy.data.local.toDomain
import pl.local.przepisy.data.local.toEntity
import pl.local.przepisy.data.local.toSummary
import pl.local.przepisy.domain.model.Recipe
import pl.local.przepisy.domain.model.RecipeSummary

class OfflineRecipeRepository(private val database: AppDatabase) : RecipeRepository {
    private val dao = database.recipeDao()

    override fun observeRecipes(query: String): Flow<List<RecipeSummary>> {
        val ftsQuery = SearchNormalizer.toFtsQuery(query)
        val source = if (ftsQuery.isBlank()) dao.observeAll() else dao.observeSearch(ftsQuery)
        return source.map { recipes -> recipes.map { it.toSummary() } }
    }

    override fun observeRecipe(id: String): Flow<Recipe?> = dao.observeById(id).map { it?.toDomain() }

    override fun observeTags(): Flow<List<String>> = dao.observeTags().map { tags -> tags.map { it.name } }

    override suspend fun getById(id: String): Recipe? = dao.getById(id)?.toDomain()

    override suspend fun findBySourceUrl(sourceUrl: String): Recipe? = dao.getBySourceUrl(sourceUrl)?.toDomain()

    override suspend fun save(recipe: Recipe) {
        database.withTransaction { writeRecipe(recipe) }
    }

    override suspend fun delete(id: String): String? = database.withTransaction {
        val image = dao.getById(id)?.recipe?.imagePath
        dao.deleteFts(id)
        dao.deleteRecipe(id)
        dao.deleteUnusedTags()
        image
    }

    override suspend fun exportAll(): List<Recipe> = dao.getAll().map { it.toDomain() }

    override suspend fun mergeImported(recipes: List<Recipe>) {
        database.withTransaction {
            recipes.forEach { incoming ->
                val existing = dao.getById(incoming.id)?.toDomain()
                    ?: dao.getBySourceUrl(incoming.sourceUrl)?.toDomain()
                if (existing == null || incoming.updatedAt > existing.updatedAt) {
                    val merged = if (existing == null || existing.id == incoming.id) incoming else incoming.copy(id = existing.id)
                    writeRecipe(merged)
                }
            }
        }
    }

    override suspend fun imagePaths(): Set<String> = dao.getImagePaths().toSet()

    private suspend fun writeRecipe(recipe: Recipe) {
        dao.upsertRecipe(recipe.toEntity())
        dao.deleteRecipeTags(recipe.id)
        val tags = normalizeTags(recipe.tags)
        if (tags.isNotEmpty()) {
            dao.upsertTags(tags)
            dao.insertRecipeTags(tags.map { RecipeTagCrossRef(recipe.id, it.id) })
        }
        dao.deleteIngredientSections(recipe.id)
        dao.deleteInstructionSections(recipe.id)

        val ingredientSections = recipe.ingredientSections.mapIndexed { index, section ->
            IngredientSectionEntity(section.id, recipe.id, section.title, index)
        }
        val ingredients = recipe.ingredientSections.flatMap { section ->
            section.lines.mapIndexed { index, line ->
                IngredientEntity(
                    id = line.id,
                    sectionId = section.id,
                    position = index,
                    rawText = line.rawText,
                    qualifier = line.qualifier,
                    quantityMin = line.quantityMin,
                    quantityMax = line.quantityMax,
                    unitKey = line.unitKey,
                    body = line.body,
                    parseStatus = line.parseStatus.name,
                    scalable = line.scalable,
                )
            }
        }
        val instructionSections = recipe.instructionSections.mapIndexed { index, section ->
            InstructionSectionEntity(section.id, recipe.id, section.title, index)
        }
        val instructions = recipe.instructionSections.flatMap { section ->
            section.steps.mapIndexed { index, text ->
                InstructionEntity("${section.id}:$index", section.id, index, text)
            }
        }

        if (ingredientSections.isNotEmpty()) dao.insertIngredientSections(ingredientSections)
        if (ingredients.isNotEmpty()) dao.insertIngredients(ingredients)
        if (instructionSections.isNotEmpty()) dao.insertInstructionSections(instructionSections)
        if (instructions.isNotEmpty()) dao.insertInstructions(instructions)
        dao.deleteFts(recipe.id)
        dao.insertFts(RecipeFtsEntity(recipe.id, recipe.searchContent()))
        dao.deleteUnusedTags()
    }
}
