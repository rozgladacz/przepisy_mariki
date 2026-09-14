package pl.local.przepisy.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalContext
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import pl.local.przepisy.BuildConfig
import pl.local.przepisy.data.update.UpdateUiState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(viewModel: PrzepisyViewModel, onBack: () -> Unit) {
    val message by viewModel.message.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let(viewModel::exportBackup) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importBackup)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kopia lokalna") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Wstecz") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Archiwum zawiera przepisy i zapisane zdjęcia. Nie wymaga konta ani chmury.")
            Button(
                onClick = {
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
                    exportLauncher.launch("przepisy-$date.przepisy.zip")
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Eksportuj bibliotekę") }
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Importuj i scal archiwum") }
            Text("Przy konflikcie zachowywany jest przepis z nowszą datą modyfikacji.")
            message?.let { Text(it) }
            HorizontalDivider()
            Text("Aktualizacje", style = MaterialTheme.typography.titleMedium)
            Text("Wersja ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
            UpdateSection(updateState, viewModel::checkForUpdates) { url ->
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, "Brak przeglądarki do pobrania aktualizacji.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}


@Composable
internal fun UpdateSection(state: UpdateUiState, onCheck: () -> Unit, onDownload: (String) -> Unit) {
    when (state) {
        UpdateUiState.Idle -> Unit
        UpdateUiState.Checking -> Text("Sprawdzanie aktualizacji…")
        UpdateUiState.Current -> Text("Masz aktualną wersję aplikacji.")
        UpdateUiState.NotPublished -> Text("Nie ma jeszcze opublikowanej aktualizacji.")
        UpdateUiState.Incompatible -> Text("Nowa wersja wymaga nowszego Androida.")
        UpdateUiState.Error -> Text("Nie udało się sprawdzić aktualizacji. Sprawdź połączenie i spróbuj ponownie.")
        is UpdateUiState.Available -> {
            Text("Dostępna wersja ${state.update.versionName}")
            if (state.update.notes.isNotBlank()) Text(state.update.notes)
            Button(onClick = { onDownload(state.update.apkUrl) }, modifier = Modifier.fillMaxWidth()) {
                Text("Pobierz aktualizację")
            }
            Text("Otwórz pobrany plik i potwierdź aktualizację. Zapisane przepisy pozostaną w aplikacji.")
        }
    }
    OutlinedButton(
        onClick = onCheck,
        enabled = state != UpdateUiState.Checking,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Sprawdź aktualizacje") }
}
