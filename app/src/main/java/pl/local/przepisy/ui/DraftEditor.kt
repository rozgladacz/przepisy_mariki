package pl.local.przepisy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import pl.local.przepisy.domain.model.IngredientLine
import pl.local.przepisy.domain.model.IngredientSection
import pl.local.przepisy.domain.model.InstructionSection
import pl.local.przepisy.domain.model.PanShape
import pl.local.przepisy.domain.model.PanSpec
import pl.local.przepisy.domain.model.ParseStatus
import pl.local.przepisy.domain.model.RecipeDraft
import pl.local.przepisy.domain.quantity.IngredientAmountParser
import java.io.File

@Composable
fun DraftEditor(
    draft: RecipeDraft,
    onDraftChange: (RecipeDraft) -> Unit,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    notice: String? = null,
    showAction: Boolean = true,
) {
    val parser = remember { IngredientAmountParser() }
    val imageModel: Any? = draft.imageUrl ?: draft.existingImagePath?.let(::File)
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            notice?.let {
                Card(Modifier.fillMaxWidth()) { Text(it, Modifier.padding(14.dp), color = MaterialTheme.colorScheme.primary) }
            }
        }
        if (imageModel != null) {
            item {
                AsyncImage(
                    model = imageModel,
                    contentDescription = "Zdjęcie główne przepisu",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            OutlinedTextField(
                value = draft.title,
                onValueChange = { onDraftChange(draft.copy(title = it)) },
                label = { Text("Tytuł") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = draft.description,
                onValueChange = { onDraftChange(draft.copy(description = it)) },
                label = { Text("Opis") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = draft.originalYield,
                onValueChange = { onDraftChange(draft.copy(originalYield = it)) },
                label = { Text("Porcje / wydajność źródłowa") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (draft.warnings.isNotEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Sprawdź import", fontWeight = FontWeight.SemiBold)
                        draft.warnings.forEach { Text("• $it") }
                    }
                }
            }
        }
        item {
            Text("Forma bazowa", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            PanEditor(draft.pan) { onDraftChange(draft.copy(pan = it)) }
        }
        item { Text("Składniki", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
        itemsIndexed(draft.ingredientSections, key = { _, section -> section.id }) { sectionIndex, section ->
            IngredientSectionEditor(
                section = section,
                parser = parser,
                canMoveUp = sectionIndex > 0,
                canMoveDown = sectionIndex < draft.ingredientSections.lastIndex,
                onChange = { changed ->
                    onDraftChange(draft.copy(ingredientSections = draft.ingredientSections.replaceAt(sectionIndex, changed)))
                },
                onMoveUp = {
                    onDraftChange(draft.copy(ingredientSections = draft.ingredientSections.swap(sectionIndex, sectionIndex - 1)))
                },
                onMoveDown = {
                    onDraftChange(draft.copy(ingredientSections = draft.ingredientSections.swap(sectionIndex, sectionIndex + 1)))
                },
                onRemove = {
                    onDraftChange(draft.copy(ingredientSections = draft.ingredientSections.toMutableList().also { it.removeAt(sectionIndex) }))
                },
            )
        }
        item {
            OutlinedButton(
                onClick = {
                    onDraftChange(
                        draft.copy(ingredientSections = draft.ingredientSections + IngredientSection(lines = listOf(parser.parse("")))),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Dodaj sekcję składników") }
        }
        item { Text("Instrukcja", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
        itemsIndexed(draft.instructionSections, key = { _, section -> section.id }) { sectionIndex, section ->
            InstructionSectionEditor(
                section = section,
                canMoveUp = sectionIndex > 0,
                canMoveDown = sectionIndex < draft.instructionSections.lastIndex,
                onChange = { changed ->
                    onDraftChange(draft.copy(instructionSections = draft.instructionSections.replaceAt(sectionIndex, changed)))
                },
                onMoveUp = {
                    onDraftChange(draft.copy(instructionSections = draft.instructionSections.swap(sectionIndex, sectionIndex - 1)))
                },
                onMoveDown = {
                    onDraftChange(draft.copy(instructionSections = draft.instructionSections.swap(sectionIndex, sectionIndex + 1)))
                },
                onRemove = {
                    onDraftChange(draft.copy(instructionSections = draft.instructionSections.toMutableList().also { it.removeAt(sectionIndex) }))
                },
            )
        }
        item {
            OutlinedButton(
                onClick = {
                    onDraftChange(
                        draft.copy(instructionSections = draft.instructionSections + InstructionSection(steps = listOf(""))),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Dodaj sekcję instrukcji") }
        }
        item {
            if (showAction) {
                Button(
                    onClick = onAction,
                    enabled = draft.canBeSaved(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(actionLabel) }
            }
            if (!draft.hasValidIngredientAmounts()) {
                Text("Popraw ilości zaznaczone do skalowania.", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun IngredientSectionEditor(
    section: IngredientSection,
    parser: IngredientAmountParser,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onChange: (IngredientSection) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = section.title,
                onValueChange = { onChange(section.copy(title = it)) },
                label = { Text("Nazwa sekcji") },
                modifier = Modifier.fillMaxWidth(),
            )
            section.lines.forEachIndexed { index, line ->
                IngredientLineEditor(
                    line = line,
                    parser = parser,
                    canMoveUp = index > 0,
                    canMoveDown = index < section.lines.lastIndex,
                    onChange = { onChange(section.copy(lines = section.lines.replaceAt(index, it))) },
                    onMoveUp = { onChange(section.copy(lines = section.lines.swap(index, index - 1))) },
                    onMoveDown = { onChange(section.copy(lines = section.lines.swap(index, index + 1))) },
                    onRemove = { onChange(section.copy(lines = section.lines.toMutableList().also { list -> list.removeAt(index) })) },
                )
                if (index < section.lines.lastIndex) HorizontalDivider()
            }
            TextButton(
                onClick = { onChange(section.copy(lines = section.lines + parser.parse(""))) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("+ Dodaj składnik") }
            ReorderRow(canMoveUp, canMoveDown, onMoveUp, onMoveDown, onRemove)
        }
    }
}

@Composable
private fun IngredientLineEditor(
    line: IngredientLine,
    parser: IngredientAmountParser,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onChange: (IngredientLine) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    var expanded by rememberSaveable(line.id) { mutableStateOf(line.parseStatus != ParseStatus.EXACT) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = line.rawText,
            onValueChange = { value -> onChange(parser.parse(value).copy(id = line.id)) },
            label = { Text("Składnik") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = { TextButton(onClick = onRemove) { Text("Usuń") } },
        )
        if (line.parseStatus != ParseStatus.EXACT) {
            Text(
                if (line.parseStatus == ParseStatus.INFERRED) "Ilość rozpoznana z zapisu słownego lub miary domyślnej."
                else "Nie rozpoznano dokładnej ilości.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Ukryj rozpoznanie" else "Sprawdź rozpoznanie") }
        if (expanded) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = line.quantityMin.orEmpty().replace('.', ','),
                    onValueChange = { onChange(line.copy(quantityMin = it.replace(',', '.').ifBlank { null })) },
                    label = { Text("Ilość") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = decimalKeyboardOptions,
                    singleLine = true,
                )
                OutlinedTextField(
                    value = line.quantityMax.orEmpty().replace('.', ','),
                    onValueChange = { onChange(line.copy(quantityMax = it.replace(',', '.').ifBlank { null })) },
                    label = { Text("Do") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = decimalKeyboardOptions,
                    singleLine = true,
                )
            }
            OutlinedTextField(
                value = line.unitKey.orEmpty(),
                onValueChange = { onChange(line.copy(unitKey = it.ifBlank { null })) },
                label = { Text("Jednostka kanoniczna") },
                supportingText = { Text("Np. łyżka, szklanka, gram, jajko") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = line.body,
                onValueChange = { onChange(line.copy(body = it)) },
                label = { Text("Pozostała nazwa składnika") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row {
                Checkbox(
                    checked = line.scalable,
                    onCheckedChange = { onChange(line.copy(scalable = it, parseStatus = ParseStatus.INFERRED)) },
                )
                Text("Skaluj tę ilość", Modifier.padding(top = 12.dp))
            }
        }
        ItemReorderRow(canMoveUp, canMoveDown, onMoveUp, onMoveDown)
    }
}

@Composable
private fun InstructionSectionEditor(
    section: InstructionSection,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onChange: (InstructionSection) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = section.title,
                onValueChange = { onChange(section.copy(title = it)) },
                label = { Text("Nazwa sekcji") },
                modifier = Modifier.fillMaxWidth(),
            )
            section.steps.forEachIndexed { index, step ->
                Column {
                    OutlinedTextField(
                        value = step,
                        onValueChange = { onChange(section.copy(steps = section.steps.replaceAt(index, it))) },
                        label = { Text("Krok ${index + 1}") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            TextButton(onClick = { onChange(section.copy(steps = section.steps.toMutableList().also { it.removeAt(index) })) }) {
                                Text("Usuń")
                            }
                        },
                    )
                    ItemReorderRow(
                        canMoveUp = index > 0,
                        canMoveDown = index < section.steps.lastIndex,
                        onMoveUp = { onChange(section.copy(steps = section.steps.swap(index, index - 1))) },
                        onMoveDown = { onChange(section.copy(steps = section.steps.swap(index, index + 1))) },
                    )
                }
            }
            TextButton(onClick = { onChange(section.copy(steps = section.steps + "")) }, modifier = Modifier.fillMaxWidth()) {
                Text("+ Dodaj krok")
            }
            ReorderRow(canMoveUp, canMoveDown, onMoveUp, onMoveDown, onRemove)
        }
    }
}

@Composable
private fun ReorderRow(
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Row {
            TextButton(enabled = canMoveUp, onClick = onMoveUp) { Text("↑") }
            TextButton(enabled = canMoveDown, onClick = onMoveDown) { Text("↓") }
        }
        TextButton(onClick = onRemove) { Text("Usuń sekcję") }
    }
}

@Composable
private fun ItemReorderRow(
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(enabled = canMoveUp, onClick = onMoveUp) { Text("↑") }
        TextButton(enabled = canMoveDown, onClick = onMoveDown) { Text("↓") }
    }
}

@Composable
private fun PanEditor(pan: PanSpec?, onChange: (PanSpec?) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = pan == null, onClick = { onChange(null) }, label = { Text("Brak") })
        FilterChip(
            selected = pan?.shape == PanShape.RECTANGLE,
            onClick = { onChange(PanSpec(PanShape.RECTANGLE, widthCm = pan?.widthCm ?: 20.0, heightCm = pan?.heightCm ?: 30.0)) },
            label = { Text("Prostokąt") },
        )
        FilterChip(
            selected = pan?.shape == PanShape.CIRCLE,
            onClick = { onChange(PanSpec(PanShape.CIRCLE, diameterCm = pan?.diameterCm ?: 24.0)) },
            label = { Text("Koło") },
        )
    }
    when (pan?.shape) {
        PanShape.RECTANGLE -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DecimalField("Szerokość cm", pan.widthCm, Modifier.weight(1f)) { onChange(pan.copy(widthCm = it)) }
            DecimalField("Długość cm", pan.heightCm, Modifier.weight(1f)) { onChange(pan.copy(heightCm = it)) }
        }
        PanShape.CIRCLE -> DecimalField("Średnica cm", pan.diameterCm, Modifier.fillMaxWidth()) { onChange(pan.copy(diameterCm = it)) }
        null -> Unit
    }
}

@Composable
private fun DecimalField(label: String, value: Double?, modifier: Modifier, onChange: (Double?) -> Unit) {
    OutlinedTextField(
        value = value?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString().replace('.', ',') }.orEmpty(),
        onValueChange = { onChange(it.replace(',', '.').toDoubleOrNull()) },
        label = { Text(label) },
        modifier = modifier,
        keyboardOptions = decimalKeyboardOptions,
        singleLine = true,
    )
}

private fun <T> List<T>.replaceAt(index: Int, value: T): List<T> = toMutableList().also { it[index] = value }
private fun <T> List<T>.swap(first: Int, second: Int): List<T> = toMutableList().also {
    val value = it[first]
    it[first] = it[second]
    it[second] = value
}

internal fun RecipeDraft.hasValidIngredientAmounts(): Boolean = ingredientSections
    .flatMap(IngredientSection::lines)
    .all { line ->
        val minimumValid = line.quantityMin?.toBigDecimalOrNull() != null
        val maximumValid = line.quantityMax == null || line.quantityMax.toBigDecimalOrNull() != null
        !line.scalable || minimumValid && maximumValid
    }

internal fun RecipeDraft.canBeSaved(): Boolean =
    title.isNotBlank() &&
        ingredientSections.any { it.lines.any { line -> line.rawText.isNotBlank() } } &&
        instructionSections.any { it.steps.any(String::isNotBlank) } &&
        hasValidIngredientAmounts()

private val decimalKeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
