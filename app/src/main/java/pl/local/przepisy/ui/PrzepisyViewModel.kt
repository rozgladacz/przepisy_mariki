package pl.local.przepisy.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import pl.local.przepisy.data.update.UpdateUiState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pl.local.przepisy.AppContainer
import pl.local.przepisy.domain.model.Recipe
import pl.local.przepisy.domain.model.RecipeDraft
import pl.local.przepisy.domain.model.RecipeSummary
import pl.local.przepisy.domain.model.PanPreset
import pl.local.przepisy.domain.model.toRecipe

sealed interface ImportUiState {
    data class Idle(val initialUrl: String = "") : ImportUiState
    data class Loading(val url: String) : ImportUiState
    data class Preview(val draft: RecipeDraft, val existing: Recipe? = null) : ImportUiState
    data object Saving : ImportUiState
    data class Saved(val recipeId: String, val warning: String? = null) : ImportUiState
    data class Error(val message: String, val url: String = "") : ImportUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class PrzepisyViewModel(private val container: AppContainer) : ViewModel() {
    private val repository = container.repository
    private val query = MutableStateFlow("")
    private val tagFilters = MutableStateFlow<Set<String>>(emptySet())
    val searchQuery: StateFlow<String> = query.asStateFlow()
    val selectedTagFilters: StateFlow<Set<String>> = tagFilters.asStateFlow()
    val availableTags: StateFlow<List<String>> = repository.observeTags()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recipes: StateFlow<List<RecipeSummary>> = combine(query, tagFilters) { search, tags ->
        search to tags
    }
        .flatMapLatest { (search, tags) ->
            repository.observeRecipes(search).map { recipes ->
                if (tags.isEmpty()) {
                    recipes
                } else {
                    recipes.filter { recipe ->
                        tags.all { selected -> recipe.tags.any { it.equals(selected, ignoreCase = true) } }
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val panPresets: StateFlow<List<PanPreset>> = container.panPresetStore.presets

    private val _importState = MutableStateFlow<ImportUiState>(ImportUiState.Idle())
    val importState: StateFlow<ImportUiState> = _importState.asStateFlow()
    private val _pendingSharedUrl = MutableStateFlow<String?>(null)
    val pendingSharedUrl: StateFlow<String?> = _pendingSharedUrl.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val tagUpdateMutex = Mutex()
    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateState = _updateState.asStateFlow()

    fun checkForUpdates() {
        if (_updateState.value == UpdateUiState.Checking) return
        _updateState.value = UpdateUiState.Checking
        viewModelScope.launch {
            _updateState.value = try {
                container.appUpdates.check()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                UpdateUiState.Error
            }
        }
    }

    init {
        viewModelScope.launch {
            availableTags.collect { available ->
                tagFilters.value = tagFilters.value.filterTo(linkedSetOf()) { selected ->
                    available.any { it.equals(selected, ignoreCase = true) }
                }
            }
        }
    }

    fun setSearchQuery(value: String) { query.value = value }

    fun toggleTagFilter(tag: String) {
        tagFilters.value = if (tag in tagFilters.value) tagFilters.value - tag else tagFilters.value + tag
    }

    fun observeRecipe(id: String): Flow<Recipe?> = repository.observeRecipe(id)

    fun toggleFavorite(recipe: RecipeSummary) {
        viewModelScope.launch {
            repository.getById(recipe.id)?.let {
                repository.save(
                    it.copy(
                        favorite = !recipe.favorite,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    fun updateTags(recipe: Recipe, tags: Set<String>) {
        viewModelScope.launch {
            try {
                repository.save(
                    recipe.copy(
                        tags = tags.map(String::trim).filter(String::isNotBlank).distinct(),
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                _message.value = "Zapisano tagi."
            } catch (error: Throwable) {
                _message.value = error.message ?: "Nie udało się zapisać tagów."
            }
        }
    }

    fun toggleRecipeTag(recipeId: String, tag: String) {
        viewModelScope.launch {
            try {
                tagUpdateMutex.withLock {
                    val recipe = repository.getById(recipeId) ?: return@withLock
                    val selected = recipe.tags.any { it.equals(tag, ignoreCase = true) }
                    val tags = if (selected) {
                        recipe.tags.filterNot { it.equals(tag, ignoreCase = true) }
                    } else {
                        recipe.tags + tag
                    }
                    repository.save(
                        recipe.copy(
                            tags = tags,
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                }
            } catch (error: Throwable) {
                _message.value = error.message ?: "Nie udało się zmienić tagu."
            }
        }
    }

    fun addRecipeTag(recipeId: String, name: String) {
        val tag = name.trim()
        if (tag.isBlank()) return
        viewModelScope.launch {
            try {
                tagUpdateMutex.withLock {
                    val recipe = repository.getById(recipeId) ?: return@withLock
                    if (recipe.tags.none { it.equals(tag, ignoreCase = true) }) {
                        repository.save(
                            recipe.copy(
                                tags = recipe.tags + tag,
                                updatedAt = System.currentTimeMillis(),
                            ),
                        )
                    }
                }
            } catch (error: Throwable) {
                _message.value = error.message ?: "Nie udało się dodać tagu."
            }
        }
    }

    fun resetImport(initialUrl: String = "") {
        _importState.value = ImportUiState.Idle(initialUrl)
    }

    fun importUrl(url: String) {
        if (url.isBlank()) {
            _importState.value = ImportUiState.Error("Wklej adres przepisu.", url)
            return
        }
        viewModelScope.launch {
            _importState.value = ImportUiState.Loading(url)
            _importState.value = try {
                val draft = container.importer.import(url)
                ImportUiState.Preview(draft, repository.findBySourceUrl(draft.sourceUrl))
            } catch (error: Throwable) {
                ImportUiState.Error(error.message ?: "Nie udało się zaimportować przepisu.", url)
            }
        }
    }

    fun updateDraft(draft: RecipeDraft) {
        val current = _importState.value as? ImportUiState.Preview ?: return
        _importState.value = current.copy(draft = draft)
    }

    fun saveImportedDraft() {
        val preview = _importState.value as? ImportUiState.Preview ?: return
        viewModelScope.launch {
            _importState.value = ImportUiState.Saving
            try {
                var recipe = preview.draft.toRecipe(preview.existing, preview.existing?.imagePath)
                repository.save(recipe)
                var imageWarning: String? = null
                if (!preview.draft.imageUrl.isNullOrBlank()) {
                    try {
                        val newImage = container.imageStore.download(preview.draft.imageUrl, recipe.id)
                        val oldImage = recipe.imagePath
                        recipe = recipe.copy(imagePath = newImage, updatedAt = System.currentTimeMillis())
                        repository.save(recipe)
                        if (oldImage != newImage) container.imageStore.delete(oldImage)
                    } catch (_: Throwable) {
                        imageWarning = "Przepis zapisano, ale nie udało się zapisać zdjęcia offline."
                    }
                }
                container.imageStore.cleanup(repository.imagePaths())
                _importState.value = ImportUiState.Saved(recipe.id, imageWarning)
            } catch (error: Throwable) {
                _importState.value = ImportUiState.Error(error.message ?: "Nie udało się zapisać przepisu.")
            }
        }
    }

    fun saveEditedRecipe(existing: Recipe, draft: RecipeDraft) {
        viewModelScope.launch {
            try {
                repository.save(draft.toRecipe(existing, existing.imagePath))
                _message.value = "Zapisano zmiany."
            } catch (error: Throwable) {
                _message.value = error.message ?: "Nie udało się zapisać zmian."
            }
        }
    }

    fun savePanPreset(preset: PanPreset) = container.panPresetStore.save(preset)

    fun deletePanPreset(id: String) = container.panPresetStore.delete(id)

    fun deleteRecipe(recipe: Recipe) {
        viewModelScope.launch {
            try {
                val image = repository.delete(recipe.id)
                container.imageStore.delete(image)
                _message.value = "Usunięto przepis."
            } catch (error: Throwable) {
                _message.value = error.message ?: "Nie udało się usunąć przepisu."
            }
        }
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            _message.value = "Tworzenie kopii…"
            _message.value = try {
                val result = container.backupManager.exportTo(uri)
                "Wyeksportowano ${result.recipeCount} przepisów."
            } catch (error: Throwable) {
                error.message ?: "Nie udało się utworzyć kopii."
            }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            _message.value = "Importowanie kopii…"
            _message.value = try {
                val result = container.backupManager.importFrom(uri)
                "Scalono ${result.recipeCount} przepisów z kopii."
            } catch (error: Throwable) {
                error.message ?: "Nie udało się zaimportować kopii."
            }
        }
    }

    fun clearMessage() { _message.value = null }

    fun acceptSharedText(text: String?) {
        val url = text?.let { Regex("https://\\S+", RegexOption.IGNORE_CASE).find(it)?.value?.trimEnd('.', ',', ')') }
        if (url != null) _pendingSharedUrl.value = url
    }

    fun consumeSharedUrl() { _pendingSharedUrl.value = null }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(PrzepisyViewModel::class.java))
            return PrzepisyViewModel(container) as T
        }
    }
}
