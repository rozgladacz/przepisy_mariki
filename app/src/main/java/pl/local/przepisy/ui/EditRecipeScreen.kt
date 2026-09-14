package pl.local.przepisy.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.local.przepisy.domain.model.toDraft

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRecipeScreen(
    recipeId: String,
    viewModel: PrzepisyViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val recipe by viewModel.observeRecipe(recipeId).collectAsStateWithLifecycle(initialValue = null)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edytuj przepis") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Wstecz") } },
            )
        },
    ) { padding ->
        val loaded = recipe
        if (loaded == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            var draft by remember(loaded.id) { mutableStateOf(loaded.toDraft()) }
            DraftEditor(
                draft = draft,
                onDraftChange = { draft = it },
                actionLabel = "Zapisz zmiany",
                onAction = { viewModel.saveEditedRecipe(loaded, draft); onSaved() },
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            )
        }
    }
}
