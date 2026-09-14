package pl.local.przepisy.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(
    tableName = "recipes",
    indices = [Index(value = ["sourceUrl"], unique = true)],
)
data class RecipeEntity(
    @androidx.room.PrimaryKey val id: String,
    val title: String,
    val description: String,
    val sourceName: String,
    val sourceUrl: String,
    val originalYield: String,
    val imagePath: String?,
    val panShape: String?,
    val panWidthCm: Double?,
    val panHeightCm: Double?,
    val panDiameterCm: Double?,
    val favorite: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "recipe_fts")
data class RecipeFtsEntity(
    val recipeId: String,
    val content: String,
)

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
)

@Entity(
    tableName = "recipe_tags",
    primaryKeys = ["recipeId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("recipeId"), Index("tagId")],
)
data class RecipeTagCrossRef(
    val recipeId: String,
    val tagId: String,
)

@Entity(
    tableName = "ingredient_sections",
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("recipeId")],
)
data class IngredientSectionEntity(
    @androidx.room.PrimaryKey val id: String,
    val recipeId: String,
    val title: String,
    val position: Int,
)

@Entity(
    tableName = "ingredients",
    foreignKeys = [
        ForeignKey(
            entity = IngredientSectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sectionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sectionId")],
)
data class IngredientEntity(
    @androidx.room.PrimaryKey val id: String,
    val sectionId: String,
    val position: Int,
    val rawText: String,
    val qualifier: String?,
    val quantityMin: String?,
    val quantityMax: String?,
    val unitKey: String?,
    val body: String,
    val parseStatus: String,
    val scalable: Boolean,
)

@Entity(
    tableName = "instruction_sections",
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("recipeId")],
)
data class InstructionSectionEntity(
    @androidx.room.PrimaryKey val id: String,
    val recipeId: String,
    val title: String,
    val position: Int,
)

@Entity(
    tableName = "instructions",
    foreignKeys = [
        ForeignKey(
            entity = InstructionSectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sectionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sectionId")],
)
data class InstructionEntity(
    @androidx.room.PrimaryKey val id: String,
    val sectionId: String,
    val position: Int,
    val text: String,
)

data class IngredientSectionWithLines(
    @Embedded val section: IngredientSectionEntity,
    @Relation(parentColumn = "id", entityColumn = "sectionId")
    val lines: List<IngredientEntity>,
)

data class InstructionSectionWithSteps(
    @Embedded val section: InstructionSectionEntity,
    @Relation(parentColumn = "id", entityColumn = "sectionId")
    val steps: List<InstructionEntity>,
)

data class RecipeWithDetails(
    @Embedded val recipe: RecipeEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = RecipeTagCrossRef::class,
            parentColumn = "recipeId",
            entityColumn = "tagId",
        ),
    )
    val tags: List<TagEntity>,
    @Relation(
        entity = IngredientSectionEntity::class,
        parentColumn = "id",
        entityColumn = "recipeId",
    )
    val ingredientSections: List<IngredientSectionWithLines>,
    @Relation(
        entity = InstructionSectionEntity::class,
        parentColumn = "id",
        entityColumn = "recipeId",
    )
    val instructionSections: List<InstructionSectionWithSteps>,
)

data class RecipeWithTags(
    @Embedded val recipe: RecipeEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = RecipeTagCrossRef::class,
            parentColumn = "recipeId",
            entityColumn = "tagId",
        ),
    )
    val tags: List<TagEntity>,
)
