package pl.local.przepisy.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.local.przepisy.domain.model.RecipeDraft

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ImportScreen(viewModel: PrzepisyViewModel, onBack: () -> Unit, onOpenRecipe: (String) -> Unit) {
    val state by viewModel.importState.collectAsStateWithLifecycle()
    var confirmReplace by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import przepisu") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Wstecz") } },
            )
        },
    ) { padding ->
        when (val current = state) {
            is ImportUiState.Idle -> UrlForm(current.initialUrl, Modifier.padding(padding)) { viewModel.importUrl(it) }
            is ImportUiState.Loading -> LoadingContent("Pobieranie i rozpoznawanie przepisu…", Modifier.padding(padding))
            is ImportUiState.Preview -> {
                ImportPreviewContent(
                    draft = current.draft,
                    onDraftChange = viewModel::updateDraft,
                    actionLabel = if (current.existing == null) "Zapisz offline" else "Zastąp zapisany przepis",
                    onAction = {
                        if (current.existing == null) viewModel.saveImportedDraft() else confirmReplace = true
                    },
                    notice = current.existing?.let { "Ten adres jest już zapisany. Zapis zastąpi obecną wersję po potwierdzeniu." },
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
                if (confirmReplace) {
                    AlertDialog(
                        onDismissRequest = { confirmReplace = false },
                        title = { Text("Zaktualizować przepis?") },
                        text = { Text("Obecna treść i zdjęcie zostaną zastąpione poprawionym importem.") },
                        confirmButton = {
                            Button(onClick = { confirmReplace = false; viewModel.saveImportedDraft() }) { Text("Zastąp") }
                        },
                        dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text("Anuluj") } },
                    )
                }
            }
            ImportUiState.Saving -> LoadingContent("Zapisywanie przepisu i zdjęcia…", Modifier.padding(padding))
            is ImportUiState.Saved -> {
                val recipe by viewModel.observeRecipe(current.recipeId).collectAsStateWithLifecycle(initialValue = null)
                val tags by viewModel.availableTags.collectAsStateWithLifecycle()
                ImportedRecipeSummary(
                    warning = current.warning,
                    availableTags = tags,
                    selectedTags = recipe?.tags.orEmpty(),
                    onOpen = { onOpenRecipe(current.recipeId) },
                    onToggleTag = { viewModel.toggleRecipeTag(current.recipeId, it) },
                    onAddTag = { viewModel.addRecipeTag(current.recipeId, it) },
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
            }
            is ImportUiState.Error -> {
                Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
                    Text(current.message)
                    UrlForm(current.url, Modifier.fillMaxWidth()) { viewModel.importUrl(it) }
                }
            }
        }
    }
}

@Composable
internal fun ImportPreviewContent(
    draft: RecipeDraft,
    onDraftChange: (RecipeDraft) -> Unit,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    notice: String? = null,
) {
    Column(modifier) {
        DraftEditor(
            draft = draft,
            onDraftChange = onDraftChange,
            actionLabel = actionLabel,
            onAction = onAction,
            notice = notice,
            showAction = false,
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 8.dp,
        ) {
            Button(
                onClick = onAction,
                enabled = draft.canBeSaved(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            ) { Text(actionLabel) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ImportedRecipeSummary(
    warning: String?,
    availableTags: List<String>,
    selectedTags: List<String>,
    onOpen: () -> Unit,
    onToggleTag: (String) -> Unit,
    onAddTag: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(24.dp),
    ) {
        Text(warning ?: "Przepis zapisano i jest dostępny offline.")
        Button(
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        ) { Text("Otwórz przepis") }
        Text(
            "Tagi",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
        )
        if (availableTags.isEmpty()) {
            Text(
                "Brak dostępnych tagów. Dodaj pierwszy poniżej.",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                availableTags.forEach { tag ->
                    FilterChip(
                        selected = selectedTags.any { it.equals(tag, ignoreCase = true) },
                        onClick = { onToggleTag(tag) },
                        label = { Text(tag) },
                    )
                }
            }
        }
        NewTagInput(
            onAddTag = onAddTag,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun UrlForm(initialUrl: String, modifier: Modifier, onImport: (String) -> Unit) {
    var url by rememberSaveable(initialUrl) { mutableStateOf(initialUrl) }
    Column(modifier.padding(20.dp)) {
        Text("Wklej adres przepisu z mojewypieki.com, alaantkoweblw.pl, rozkoszny.pl lub aniagotuje.pl.")
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Adres HTTPS") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        Button(onClick = { onImport(url) }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("Pobierz przepis") }
    }
}

@Composable
private fun LoadingContent(text: String, modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(text, Modifier.padding(top = 12.dp))
        }
    }
}
