package pl.local.przepisy.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import pl.local.przepisy.domain.model.PanShape
import pl.local.przepisy.domain.model.PanPreset
import pl.local.przepisy.domain.model.PanSpec
import pl.local.przepisy.domain.model.Recipe
import pl.local.przepisy.domain.model.newId
import pl.local.przepisy.domain.quantity.QuantityFormatter
import pl.local.przepisy.domain.scaling.RecipeScaler
import pl.local.przepisy.domain.scaling.ScaleSpec
import java.io.File
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecipeDetailScreen(
    recipeId: String,
    viewModel: PrzepisyViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDeleted: () -> Unit,
) {
    val recipe by viewModel.observeRecipe(recipeId).collectAsStateWithLifecycle(initialValue = null)
    var deleteConfirmation by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(recipe?.title ?: "Przepis", maxLines = 1) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Wstecz") } },
                actions = {
                    TextButton(onClick = onEdit, enabled = recipe != null) { Text("Edytuj") }
                    TextButton(onClick = { deleteConfirmation = true }, enabled = recipe != null) { Text("Usuń") }
                },
            )
        },
    ) { padding ->
        val loaded = recipe
        if (loaded == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            RecipeDetail(loaded, viewModel, Modifier.padding(padding))
            if (deleteConfirmation) {
                AlertDialog(
                    onDismissRequest = { deleteConfirmation = false },
                    title = { Text("Usunąć przepis?") },
                    text = { Text("Tekst i zapisane zdjęcie zostaną trwale usunięte z urządzenia.") },
                    confirmButton = {
                        Button(onClick = {
                            deleteConfirmation = false
                            viewModel.deleteRecipe(loaded)
                            onDeleted()
                        }) { Text("Usuń") }
                    },
                    dismissButton = { TextButton(onClick = { deleteConfirmation = false }) { Text("Anuluj") } },
                )
            }
        }
    }
}

@Composable
private fun RecipeDetail(recipe: Recipe, viewModel: PrzepisyViewModel, modifier: Modifier) {
    var factor by remember(recipe.id) { mutableStateOf(BigDecimal.ONE) }
    var approximate by remember(recipe.id) { mutableStateOf(false) }
    var customMultiplier by rememberSaveable(recipe.id) { mutableStateOf("1") }
    var showCustomMultiplier by rememberSaveable(recipe.id) { mutableStateOf(false) }
    var descriptionExpanded by rememberSaveable(recipe.id) { mutableStateOf(false) }
    var showPanDialog by rememberSaveable(recipe.id) { mutableStateOf(false) }
    var showTagEditor by rememberSaveable(recipe.id) { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val panPresets by viewModel.panPresets.collectAsStateWithLifecycle()
    val availableTags by viewModel.availableTags.collectAsStateWithLifecycle()

    LazyColumn(modifier.fillMaxSize()) {
        recipe.imagePath?.let { imagePath ->
            item {
                AsyncImage(
                    model = File(imagePath),
                    contentDescription = "Zdjęcie przepisu ${recipe.title}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(280.dp),
                )
            }
        }
        item {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (recipe.description.isNotBlank()) {
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .clickable { descriptionExpanded = !descriptionExpanded },
                    ) {
                        Column(
                            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Opis", fontWeight = FontWeight.SemiBold)
                                Text(if (descriptionExpanded) "▲" else "▼")
                            }
                            if (descriptionExpanded) Text(recipe.description)
                        }
                    }
                }
                if (recipe.originalYield.isNotBlank()) Text(recipe.originalYield, fontWeight = FontWeight.Medium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AssistChip(
                        onClick = { uriHandler.openUri(recipe.sourceUrl) },
                        label = { Text("Źródło: ${recipe.sourceName}") },
                    )
                    recipe.tags.forEach { tag ->
                        AssistChip(onClick = { showTagEditor = true }, label = { Text(tag) })
                    }
                    AssistChip(onClick = { showTagEditor = true }, label = { Text("Zmień") })
                }
            }
        }
        item {
            ScaleControls(
                factor = factor,
                customMultiplier = customMultiplier,
                showCustomMultiplier = showCustomMultiplier,
                hasPan = recipe.pan != null,
                onQuickFactor = {
                    factor = it
                    customMultiplier = it.stripTrailingZeros().toPlainString().replace('.', ',')
                    showCustomMultiplier = false
                    approximate = false
                },
                onShowCustomMultiplier = { showCustomMultiplier = true },
                onCustomChange = { value ->
                    customMultiplier = value
                    value.replace(',', '.').toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }?.let {
                        factor = it
                        approximate = false
                    }
                },
                onPan = { showPanDialog = true },
            )
        }
        item {
            Text(
                "Składniki",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
        recipe.ingredientSections.forEach { section ->
            item {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    if (!section.title.equals("Składniki", ignoreCase = true)) {
                        Text(section.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    }
                    section.lines.forEach { line ->
                        Text("• ${QuantityFormatter.formatIngredient(line, factor, approximate)}")
                    }
                }
            }
        }
        item { HorizontalDivider(Modifier.padding(vertical = 10.dp)) }
        recipe.instructionSections.forEach { section ->
            item {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(section.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    section.steps.forEachIndexed { index, step ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("${index + 1}.", fontWeight = FontWeight.Bold)
                            Text(step, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        item { Box(Modifier.padding(bottom = 40.dp)) }
    }

    if (showPanDialog && recipe.pan != null) {
        PanScaleDialog(
            initial = recipe.pan,
            presets = panPresets,
            onSavePreset = viewModel::savePanPreset,
            onDeletePreset = viewModel::deletePanPreset,
            onDismiss = { showPanDialog = false },
            onApply = { target ->
                val result = RecipeScaler.calculate(ScaleSpec.Pan(recipe.pan, target))
                factor = result.factor
                approximate = result.approximate
                customMultiplier = result.factor.setScale(3, java.math.RoundingMode.HALF_UP)
                    .stripTrailingZeros().toPlainString().replace('.', ',')
                showCustomMultiplier = false
                showPanDialog = false
            },
        )
    }

    if (showTagEditor) {
        TagEditorDialog(
            availableTags = availableTags,
            currentTags = recipe.tags,
            onDismiss = { showTagEditor = false },
            onSave = { tags ->
                viewModel.updateTags(recipe, tags)
                showTagEditor = false
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagEditorDialog(
    availableTags: List<String>,
    currentTags: List<String>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit,
) {
    var knownTags by remember(availableTags, currentTags) {
        mutableStateOf((availableTags + currentTags).distinctBy { it.lowercase() }.sortedBy { it.lowercase() })
    }
    var selectedTags by remember(currentTags) { mutableStateOf(currentTags.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tagi przepisu") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (knownTags.isEmpty()) {
                    Text("Dodaj pierwszy tag, np. Obiad lub Do zrobienia.")
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        knownTags.forEach { tag ->
                            FilterChip(
                                selected = selectedTags.any { it.equals(tag, ignoreCase = true) },
                                onClick = {
                                    selectedTags = if (selectedTags.any { it.equals(tag, ignoreCase = true) }) {
                                        selectedTags.filterNot { it.equals(tag, ignoreCase = true) }.toSet()
                                    } else {
                                        selectedTags + tag
                                    }
                                },
                                label = { Text(tag) },
                                modifier = Modifier.semantics {
                                    contentDescription = "Edytuj tag: $tag"
                                },
                            )
                        }
                    }
                }
                NewTagInput(
                    onAddTag = { tag ->
                        val existing = knownTags.firstOrNull { it.equals(tag, ignoreCase = true) }
                        val selected = existing ?: tag
                        if (existing == null) knownTags = (knownTags + tag).sortedBy { it.lowercase() }
                        selectedTags = selectedTags + selected
                    },
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(selectedTags) }) { Text("Zapisz") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

@Composable
private fun ScaleControls(
    factor: BigDecimal,
    customMultiplier: String,
    showCustomMultiplier: Boolean,
    hasPan: Boolean,
    onQuickFactor: (BigDecimal) -> Unit,
    onShowCustomMultiplier: () -> Unit,
    onCustomChange: (String) -> Unit,
    onPan: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Skalowanie składników", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("½×" to "0.5", "1×" to "1", "1½×" to "1.5", "2×" to "2", "3×" to "3").forEach { (label, value) ->
                    AssistChip(onClick = { onQuickFactor(BigDecimal(value)) }, label = { Text(label) })
                }
                AssistChip(onClick = onShowCustomMultiplier, label = { Text("Inne") })
            }
            if (showCustomMultiplier) {
                OutlinedTextField(
                    value = customMultiplier,
                    onValueChange = onCustomChange,
                    label = { Text("Własny mnożnik") },
                    keyboardOptions = decimalKeyboardOptions,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text("Aktywny: ${QuantityFormatter.formatNumber(factor)}×")
            if (hasPan) {
                OutlinedButton(onClick = onPan, modifier = Modifier.fillMaxWidth()) { Text("Przelicz na inną formę") }
            }
        }
    }
}

@Composable
private fun PanScaleDialog(
    initial: PanSpec,
    presets: List<PanPreset>,
    onSavePreset: (PanPreset) -> Unit,
    onDeletePreset: (String) -> Unit,
    onDismiss: () -> Unit,
    onApply: (PanSpec) -> Unit,
) {
    var targetMode by remember { mutableStateOf(TargetPanMode.from(initial.shape)) }
    var targetShape by remember { mutableStateOf(initial.shape) }
    var targetWidth by remember { mutableStateOf(initial.widthCm?.numberText().orEmpty()) }
    var targetHeight by remember { mutableStateOf(initial.heightCm?.numberText().orEmpty()) }
    var targetDiameter by remember { mutableStateOf(initial.diameterCm?.numberText().orEmpty()) }
    var showPresetEditor by remember { mutableStateOf(false) }
    var creatingPreset by remember { mutableStateOf(false) }
    var selectedPresetId by remember { mutableStateOf<String?>(null) }
    var presetName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun loadTarget(pan: PanSpec) {
        targetShape = pan.shape
        targetWidth = pan.widthCm?.numberText().orEmpty()
        targetHeight = pan.heightCm?.numberText().orEmpty()
        targetDiameter = pan.diameterCm?.numberText().orEmpty()
    }

    fun targetPan(): PanSpec? = when (targetMode) {
        TargetPanMode.RECTANGLE -> panFromInput(PanShape.RECTANGLE, targetWidth, targetHeight, targetDiameter)
        TargetPanMode.CIRCLE -> panFromInput(PanShape.CIRCLE, targetWidth, targetHeight, targetDiameter)
        TargetPanMode.LIST -> if (selectedPresetId != null || creatingPreset) {
            panFromInput(targetShape, targetWidth, targetHeight, targetDiameter)
        } else {
            null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Przelicz formę") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Forma bazowa: ${initial.dimensionLabel()}",
                    fontWeight = FontWeight.SemiBold,
                )
                Text("Nowa forma", fontWeight = FontWeight.SemiBold)
                TargetModeSelector(targetMode) { selected ->
                    targetMode = selected
                    when (selected) {
                        TargetPanMode.RECTANGLE -> targetShape = PanShape.RECTANGLE
                        TargetPanMode.CIRCLE -> targetShape = PanShape.CIRCLE
                        TargetPanMode.LIST -> Unit
                    }
                    error = null
                }
                when (targetMode) {
                    TargetPanMode.RECTANGLE -> DimensionFields(
                        PanShape.RECTANGLE,
                        targetWidth,
                        targetHeight,
                        targetDiameter,
                        { targetWidth = it },
                        { targetHeight = it },
                        { targetDiameter = it },
                    )
                    TargetPanMode.CIRCLE -> DimensionFields(
                        PanShape.CIRCLE,
                        targetWidth,
                        targetHeight,
                        targetDiameter,
                        { targetWidth = it },
                        { targetHeight = it },
                        { targetDiameter = it },
                    )
                    TargetPanMode.LIST -> {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            presets.forEach { preset ->
                                FilterChip(
                                    selected = !creatingPreset && selectedPresetId == preset.id,
                                    onClick = {
                                        selectedPresetId = preset.id
                                        creatingPreset = false
                                        showPresetEditor = false
                                        presetName = preset.name
                                        loadTarget(preset.pan)
                                        error = null
                                    },
                                    label = { Text("${preset.name} — ${preset.pan.dimensionLabel()}") },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            FilterChip(
                                selected = creatingPreset,
                                onClick = {
                                    selectedPresetId = null
                                    creatingPreset = true
                                    showPresetEditor = true
                                    presetName = ""
                                    loadTarget(initial)
                                    error = null
                                },
                                label = { Text("Nowa") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (showPresetEditor) {
                            HorizontalDivider()
                            OutlinedTextField(
                                value = presetName,
                                onValueChange = { presetName = it },
                                label = { Text("Nazwa formy") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            ShapeSelector(targetShape) {
                                targetShape = it
                                error = null
                            }
                            DimensionFields(
                                targetShape,
                                targetWidth,
                                targetHeight,
                                targetDiameter,
                                { targetWidth = it },
                                { targetHeight = it },
                                { targetDiameter = it },
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Button(
                                    onClick = {
                                        val pan = panFromInput(
                                            targetShape,
                                            targetWidth,
                                            targetHeight,
                                            targetDiameter,
                                        )
                                        when {
                                            presetName.isBlank() -> error = "Podaj nazwę formy."
                                            pan == null || !pan.isValid() -> error = "Podaj dodatnie wymiary formy."
                                            else -> {
                                                val preset = PanPreset(
                                                    id = selectedPresetId ?: newId(),
                                                    name = presetName,
                                                    pan = pan,
                                                )
                                                onSavePreset(preset)
                                                selectedPresetId = preset.id
                                                creatingPreset = false
                                                showPresetEditor = false
                                                presetName = preset.name.trim()
                                                error = null
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                ) { Text("Zapisz formę") }
                                if (selectedPresetId != null) {
                                    OutlinedButton(
                                        onClick = {
                                            selectedPresetId?.let(onDeletePreset)
                                            selectedPresetId = null
                                            creatingPreset = false
                                            showPresetEditor = false
                                            error = null
                                        },
                                        modifier = Modifier.weight(1f),
                                    ) { Text("Usuń formę") }
                                }
                            }
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val target = targetPan()
                if (target == null || !target.isValid()) {
                    error = if (
                        targetMode == TargetPanMode.LIST &&
                        selectedPresetId == null &&
                        !creatingPreset
                    ) {
                        "Wybierz formę z listy lub utwórz nową."
                    } else {
                        "Podaj dodatnie wymiary formy."
                    }
                } else onApply(target)
            }) { Text("Zastosuj") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (
                    targetMode == TargetPanMode.LIST &&
                    selectedPresetId != null &&
                    !showPresetEditor
                ) {
                    TextButton(
                        onClick = {
                            showPresetEditor = true
                            error = null
                        },
                    ) { Text("Dostosuj") }
                }
                TextButton(onClick = onDismiss) { Text("Anuluj") }
            }
        },
    )
}

private enum class TargetPanMode {
    RECTANGLE,
    CIRCLE,
    LIST;

    companion object {
        fun from(shape: PanShape): TargetPanMode = when (shape) {
            PanShape.RECTANGLE -> RECTANGLE
            PanShape.CIRCLE -> CIRCLE
        }
    }
}

@Composable
private fun TargetModeSelector(value: TargetPanMode, onChange: (TargetPanMode) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = value == TargetPanMode.RECTANGLE,
            onClick = { onChange(TargetPanMode.RECTANGLE) },
            label = { Text("Prostokąt") },
        )
        FilterChip(
            selected = value == TargetPanMode.CIRCLE,
            onClick = { onChange(TargetPanMode.CIRCLE) },
            label = { Text("Koło") },
        )
        FilterChip(
            selected = value == TargetPanMode.LIST,
            onClick = { onChange(TargetPanMode.LIST) },
            label = { Text("Lista") },
        )
    }
}

@Composable
private fun ShapeSelector(value: PanShape, onChange: (PanShape) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = value == PanShape.RECTANGLE, onClick = { onChange(PanShape.RECTANGLE) }, label = { Text("Prostokąt") })
        FilterChip(selected = value == PanShape.CIRCLE, onClick = { onChange(PanShape.CIRCLE) }, label = { Text("Koło") })
    }
}

@Composable
private fun DimensionFields(
    shape: PanShape,
    width: String,
    height: String,
    diameter: String,
    onWidth: (String) -> Unit,
    onHeight: (String) -> Unit,
    onDiameter: (String) -> Unit,
) {
    if (shape == PanShape.RECTANGLE) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                width,
                onWidth,
                label = { Text("Szerokość cm") },
                modifier = Modifier.weight(1f),
                keyboardOptions = decimalKeyboardOptions,
                singleLine = true,
            )
            OutlinedTextField(
                height,
                onHeight,
                label = { Text("Długość cm") },
                modifier = Modifier.weight(1f),
                keyboardOptions = decimalKeyboardOptions,
                singleLine = true,
            )
        }
    } else {
        OutlinedTextField(
            diameter,
            onDiameter,
            label = { Text("Średnica cm") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = decimalKeyboardOptions,
            singleLine = true,
        )
    }
}

private fun panFromInput(shape: PanShape, width: String, height: String, diameter: String): PanSpec? = when (shape) {
    PanShape.RECTANGLE -> PanSpec(shape, widthCm = width.numberOrNull(), heightCm = height.numberOrNull())
    PanShape.CIRCLE -> PanSpec(shape, diameterCm = diameter.numberOrNull())
}

private fun String.numberOrNull(): Double? = replace(',', '.').toDoubleOrNull()
private fun Double.numberText(): String = if (this % 1.0 == 0.0) toInt().toString() else toString().replace('.', ',')
private fun PanSpec.dimensionLabel(): String = when (shape) {
    PanShape.RECTANGLE -> "${widthCm?.numberText()} × ${heightCm?.numberText()} cm"
    PanShape.CIRCLE -> "⌀ ${diameterCm?.numberText()} cm"
}

private val decimalKeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
