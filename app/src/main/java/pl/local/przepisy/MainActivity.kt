package pl.local.przepisy

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import pl.local.przepisy.ui.PrzepisyApp
import pl.local.przepisy.ui.PrzepisyViewModel
import pl.local.przepisy.ui.theme.PrzepisyTheme

class MainActivity : ComponentActivity() {
    private val viewModel: PrzepisyViewModel by viewModels {
        PrzepisyViewModel.Factory((application as PrzepisyApplication).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            PrzepisyTheme { PrzepisyApp(viewModel) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    internal fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            viewModel.acceptSharedText(intent.getStringExtra(Intent.EXTRA_TEXT))
        }
    }
}
