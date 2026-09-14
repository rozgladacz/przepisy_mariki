package pl.local.przepisy.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipes ORDER BY favorite DESC, updatedAt DESC")
    @Transaction
    fun observeAll(): Flow<List<RecipeWithTags>>

    @Query(
        """
        SELECT recipes.* FROM recipes
        JOIN recipe_fts ON recipes.id = recipe_fts.recipeId
        WHERE recipe_fts MATCH :query
        ORDER BY recipes.favorite DESC, recipes.updatedAt DESC
        """,
    )
    @Transaction
    fun observeSearch(query: String): Flow<List<RecipeWithTags>>

    @Query(
        """
        SELECT tags.* FROM tags
        WHERE EXISTS (SELECT 1 FROM recipe_tags WHERE recipe_tags.tagId = tags.id)
        ORDER BY tags.name COLLATE NOCASE
        """,
    )
    fun observeTags(): Flow<List<TagEntity>>

    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :id")
    fun observeById(id: String): Flow<RecipeWithDetails?>

    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun getById(id: String): RecipeWithDetails?

    @Transaction
    @Query("SELECT * FROM recipes WHERE sourceUrl = :sourceUrl")
    suspend fun getBySourceUrl(sourceUrl: String): RecipeWithDetails?

    @Transaction
    @Query("SELECT * FROM recipes ORDER BY favorite DESC, updatedAt DESC")
    suspend fun getAll(): List<RecipeWithDetails>

    @Query("SELECT imagePath FROM recipes WHERE imagePath IS NOT NULL")
    suspend fun getImagePaths(): List<String>

    @Upsert
    suspend fun upsertRecipe(recipe: RecipeEntity)

    @Upsert
    suspend fun upsertTags(tags: List<TagEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecipeTags(recipeTags: List<RecipeTagCrossRef>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIngredientSections(sections: List<IngredientSectionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIngredients(ingredients: List<IngredientEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInstructionSections(sections: List<InstructionSectionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInstructions(instructions: List<InstructionEntity>)

    @Insert
    suspend fun insertFts(search: RecipeFtsEntity)

    @Query("DELETE FROM ingredient_sections WHERE recipeId = :recipeId")
    suspend fun deleteIngredientSections(recipeId: String)

    @Query("DELETE FROM instruction_sections WHERE recipeId = :recipeId")
    suspend fun deleteInstructionSections(recipeId: String)

    @Query("DELETE FROM recipe_fts WHERE recipeId = :recipeId")
    suspend fun deleteFts(recipeId: String)

    @Query("DELETE FROM recipe_tags WHERE recipeId = :recipeId")
    suspend fun deleteRecipeTags(recipeId: String)

    @Query("DELETE FROM tags WHERE NOT EXISTS (SELECT 1 FROM recipe_tags WHERE recipe_tags.tagId = tags.id)")
    suspend fun deleteUnusedTags()

    @Query("DELETE FROM recipes WHERE id = :recipeId")
    suspend fun deleteRecipe(recipeId: String)
}
