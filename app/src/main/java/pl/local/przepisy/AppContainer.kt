package pl.local.przepisy

import android.content.Context
import okhttp3.OkHttpClient
import pl.local.przepisy.data.backup.BackupManager
import pl.local.przepisy.data.importer.AniaGotujeAdapter
import pl.local.przepisy.data.importer.AlaantkoweblwAdapter
import pl.local.przepisy.data.importer.JsonLdRecipeExtractor
import pl.local.przepisy.data.importer.MojeWypiekiAdapter
import pl.local.przepisy.data.importer.RecipeImporter
import pl.local.przepisy.data.importer.RozkosznyAdapter
import pl.local.przepisy.data.local.AppDatabase
import pl.local.przepisy.data.local.PanPresetStore
import pl.local.przepisy.data.media.ImageStore
import pl.local.przepisy.data.repository.OfflineRecipeRepository
import pl.local.przepisy.data.repository.RecipeRepository
import pl.local.przepisy.domain.quantity.IngredientAmountParser

class AppContainer(context: Context) {
    private val http = OkHttpClient()
    val appUpdates = pl.local.przepisy.data.update.AppUpdateRepository(http)
    private val amountParser = IngredientAmountParser()
    private val jsonLd = JsonLdRecipeExtractor()
    private val database = AppDatabase.create(context)

    val repository: RecipeRepository = OfflineRecipeRepository(database)
    val panPresetStore = PanPresetStore(context)
    val imageStore = ImageStore(context, http)
    val importer = RecipeImporter(
        adapters = listOf(
            MojeWypiekiAdapter(amountParser, jsonLd),
            AlaantkoweblwAdapter(amountParser, jsonLd),
            RozkosznyAdapter(amountParser, jsonLd),
            AniaGotujeAdapter(amountParser, jsonLd),
        ),
        client = http,
    )
    val backupManager = BackupManager(context.contentResolver, repository, imageStore)
    val ingredientAmountParser: IngredientAmountParser = amountParser
}
