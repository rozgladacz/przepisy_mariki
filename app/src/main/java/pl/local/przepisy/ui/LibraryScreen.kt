package pl.local.przepisy.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import pl.local.przepisy.domain.model.RecipeSummary
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(
    viewModel: PrzepisyViewModel,
    onImport: () -> Unit,
    onOpenRecipe: (String) -> Unit,
    onBackup: () -> Unit,
) {
    val recipes by viewModel.recipes.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val tags by viewModel.availableTags.collectAsStateWithLifecycle()
    val selectedTags by viewModel.selectedTagFilters.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Przepisy", fontWeight = FontWeight.SemiBold) },
                actions = { TextButton(onClick = onBackup) { Text("Kopia") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onImport) { Text("+", style = MaterialTheme.typography.headlineMedium) }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setSearchQuery,
                label = { Text("Szukaj po nazwie lub składniku") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (tags.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    tags.forEach { tag ->
                        FilterChip(
                            selected = tag in selectedTags,
                            onClick = { viewModel.toggleTagFilter(tag) },
                            label = { Text(tag) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (recipes.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        when {
                            selectedTags.isNotEmpty() -> "Brak przepisów z wybranymi tagami."
                            query.isBlank() -> "Nie masz jeszcze przepisów.\nDodaj pierwszy przyciskiem +."
                            else -> "Brak wyników dla „$query”."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(recipes, key = RecipeSummary::id) { recipe ->
                        RecipeCard(
                            recipe = recipe,
                            onClick = { onOpenRecipe(recipe.id) },
                            onFavorite = { viewModel.toggleFavorite(recipe) },
                        )
                    }
                    item { Spacer(Modifier.height(88.dp)) }
                }
            }
        }
    }
}

@Composable
private fun RecipeCard(
    recipe: RecipeSummary,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (recipe.imagePath != null) {
                AsyncImage(
                    model = File(recipe.imagePath),
                    contentDescription = "Zdjęcie przepisu ${recipe.title}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(80.dp).clip(RoundedCornerShape(12.dp)),
                )
            } else {
                Box(
                    Modifier.size(80.dp).clip(RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text("🍲", style = MaterialTheme.typography.headlineMedium) }
            }
            Column(Modifier.weight(1f)) {
                Text(recipe.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text(recipe.sourceName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
            TextButton(
                onClick = onFavorite,
                modifier = Modifier
                    .size(48.dp)
                    .semantics {
                        contentDescription = if (recipe.favorite) {
                            "Usuń z ulubionych"
                        } else {
                            "Dodaj do ulubionych"
                        }
                    },
            ) {
                Text(
                    text = if (recipe.favorite) "★" else "☆",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (recipe.favorite) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
