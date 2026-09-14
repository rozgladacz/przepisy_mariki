package pl.local.przepisy.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migracja_1_2_zachowuje_przepis_i_ustawia_brak_ulubionego() {
        helper.createDatabase(DATABASE_NAME, 1).apply {
            execSQL(
                """
                INSERT INTO recipes (
                    id, title, description, sourceName, sourceUrl, originalYield,
                    imagePath, panShape, panWidthCm, panHeightCm, panDiameterCm,
                    createdAt, updatedAt
                ) VALUES (
                    'migration-id', 'Stary przepis', '', 'Test',
                    'https://mojewypieki.com/przepis/migracja', '',
                    NULL, NULL, NULL, NULL, NULL, 1000, 2000
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            2,
            true,
            AppDatabase.MIGRATION_1_2,
        ).use { database ->
            database.query(
                "SELECT title, favorite FROM recipes WHERE id = 'migration-id'",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals("Stary przepis", cursor.getString(0))
                assertEquals(0, cursor.getInt(1))
            }
        }
    }

    @Test
    fun migracja_2_3_zachowuje_przepis_i_tworzy_tabele_tagow() {
        helper.createDatabase(DATABASE_NAME_2_3, 2).apply {
            execSQL(
                """
                INSERT INTO recipes (
                    id, title, description, sourceName, sourceUrl, originalYield,
                    imagePath, panShape, panWidthCm, panHeightCm, panDiameterCm,
                    favorite, createdAt, updatedAt
                ) VALUES (
                    'tag-migration-id', 'Przepis z wersji 1.0.0', '', 'Test',
                    'https://mojewypieki.com/przepis/migracja-tagow', '',
                    NULL, NULL, NULL, NULL, NULL, 1, 1000, 2000
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            DATABASE_NAME_2_3,
            3,
            true,
            AppDatabase.MIGRATION_2_3,
        ).use { database ->
            database.query(
                "SELECT title, favorite FROM recipes WHERE id = 'tag-migration-id'",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals("Przepis z wersji 1.0.0", cursor.getString(0))
                assertEquals(1, cursor.getInt(1))
            }
            database.query("SELECT COUNT(*) FROM tags").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
            database.query("SELECT COUNT(*) FROM recipe_tags").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test
    fun migracja_3_4_usuwaja_tylko_nieuzywane_tagi() {
        helper.createDatabase(DATABASE_NAME_3_4, 3).apply {
            execSQL(
                """
                INSERT INTO recipes (
                    id, title, description, sourceName, sourceUrl, originalYield,
                    imagePath, panShape, panWidthCm, panHeightCm, panDiameterCm,
                    favorite, createdAt, updatedAt
                ) VALUES (
                    'tag-cleanup-id', 'Przepis z tagiem', '', 'Test',
                    'https://mojewypieki.com/przepis/migracja-sprzatania-tagow', '',
                    NULL, NULL, NULL, NULL, NULL, 0, 1000, 2000
                )
                """.trimIndent(),
            )
            execSQL("INSERT INTO tags (id, name) VALUES ('uzywany', 'Używany')")
            execSQL("INSERT INTO tags (id, name) VALUES ('sierota', 'Sierota')")
            execSQL("INSERT INTO recipe_tags (recipeId, tagId) VALUES ('tag-cleanup-id', 'uzywany')")
            close()
        }

        helper.runMigrationsAndValidate(
            DATABASE_NAME_3_4,
            4,
            true,
            AppDatabase.MIGRATION_3_4,
        ).use { database ->
            database.query("SELECT id, name FROM tags").use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals("uzywany", cursor.getString(0))
                assertEquals("Używany", cursor.getString(1))
            }
        }
    }

    private companion object {
        const val DATABASE_NAME = "migration-test"
        const val DATABASE_NAME_2_3 = "migration-test-2-3"
        const val DATABASE_NAME_3_4 = "migration-test-3-4"
    }
}
