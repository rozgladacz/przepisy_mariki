package pl.local.przepisy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun NewTagInput(
    onAddTag: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var newTag by rememberSaveable { mutableStateOf("") }
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = newTag,
            onValueChange = { newTag = it },
            label = { Text("Nowy tag") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = {
                val tag = newTag.trim()
                if (tag.isNotBlank()) {
                    onAddTag(tag)
                    newTag = ""
                }
            },
            enabled = newTag.isNotBlank(),
        ) { Text("Dodaj") }
    }
}
