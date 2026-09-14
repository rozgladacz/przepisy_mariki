package pl.local.przepisy.data.repository

import kotlinx.coroutines.flow.Flow
import pl.local.przepisy.domain.model.Recipe
import pl.local.przepisy.domain.model.RecipeSummary

interface RecipeRepository {
    fun observeRecipes(query: String): Flow<List<RecipeSummary>>
    fun observeTags(): Flow<List<String>>
    fun observeRecipe(id: String): Flow<Recipe?>
    suspend fun getById(id: String): Recipe?
    suspend fun findBySourceUrl(sourceUrl: String): Recipe?
    suspend fun save(recipe: Recipe)
    suspend fun delete(id: String): String?
    suspend fun exportAll(): List<Recipe>
    suspend fun mergeImported(recipes: List<Recipe>)
    suspend fun imagePaths(): Set<String>
}
