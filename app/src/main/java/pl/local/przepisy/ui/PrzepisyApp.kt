package pl.local.przepisy.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable

@Serializable
private data object LibraryRoute : NavKey

@Serializable
private data object ImportRoute : NavKey

@Serializable
private data class DetailRoute(val recipeId: String) : NavKey

@Serializable
private data class EditRoute(val recipeId: String) : NavKey

@Serializable
private data object BackupRoute : NavKey

@Composable
fun PrzepisyApp(viewModel: PrzepisyViewModel) {
    val backStack = rememberNavBackStack(LibraryRoute)
    val pendingSharedUrl by viewModel.pendingSharedUrl.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(pendingSharedUrl) {
        pendingSharedUrl?.let { url ->
            viewModel.resetImport(url)
            viewModel.importUrl(url)
            if (backStack.lastOrNull() !is ImportRoute) backStack.add(ImportRoute)
            viewModel.consumeSharedUrl()
        }
    }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Box(Modifier.fillMaxSize()) {
        NavDisplay(
            backStack = backStack,
            onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
            entryProvider = entryProvider {
                entry<LibraryRoute> {
                    LibraryScreen(
                        viewModel = viewModel,
                        onImport = {
                            viewModel.resetImport()
                            backStack.add(ImportRoute)
                        },
                        onOpenRecipe = { backStack.add(DetailRoute(it)) },
                        onBackup = { backStack.add(BackupRoute) },
                    )
                }
                entry<ImportRoute> {
                    ImportScreen(
                        viewModel = viewModel,
                        onBack = { backStack.removeLastOrNull() },
                        onOpenRecipe = { id ->
                            backStack.removeLastOrNull()
                            backStack.add(DetailRoute(id))
                        },
                    )
                }
                entry<DetailRoute> { route ->
                    RecipeDetailScreen(
                        recipeId = route.recipeId,
                        viewModel = viewModel,
                        onBack = { backStack.removeLastOrNull() },
                        onEdit = { backStack.add(EditRoute(route.recipeId)) },
                        onDeleted = { backStack.removeLastOrNull() },
                    )
                }
                entry<EditRoute> { route ->
                    EditRecipeScreen(
                        recipeId = route.recipeId,
                        viewModel = viewModel,
                        onBack = { backStack.removeLastOrNull() },
                        onSaved = { backStack.removeLastOrNull() },
                    )
                }
                entry<BackupRoute> {
                    BackupScreen(viewModel = viewModel, onBack = { backStack.removeLastOrNull() })
                }
            },
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}
