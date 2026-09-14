package pl.local.przepisy.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        RecipeEntity::class,
        RecipeFtsEntity::class,
        IngredientSectionEntity::class,
        IngredientEntity::class,
        InstructionSectionEntity::class,
        InstructionEntity::class,
        TagEntity::class,
        RecipeTagCrossRef::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao

    companion object {
        fun create(context: Context): AppDatabase = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "przepisy.db",
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recipes ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tags` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `recipe_tags` (
                        `recipeId` TEXT NOT NULL,
                        `tagId` TEXT NOT NULL,
                        PRIMARY KEY(`recipeId`, `tagId`),
                        FOREIGN KEY(`recipeId`) REFERENCES `recipes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipe_tags_recipeId` ON `recipe_tags` (`recipeId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipe_tags_tagId` ON `recipe_tags` (`tagId`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "DELETE FROM tags WHERE NOT EXISTS (SELECT 1 FROM recipe_tags WHERE recipe_tags.tagId = tags.id)",
                )
            }
        }
    }
}
