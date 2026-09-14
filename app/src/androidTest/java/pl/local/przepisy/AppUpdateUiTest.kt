package pl.local.przepisy

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import pl.local.przepisy.data.update.AppUpdate
import pl.local.przepisy.data.update.UpdateUiState
import pl.local.przepisy.ui.UpdateSection

class AppUpdateUiTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun updatesAreOnBackupScreen() {
        composeRule.onNodeWithText("Kopia").performClick()
        composeRule.onNodeWithText("Eksportuj bibliotekę").assertIsDisplayed()
        composeRule.onNodeWithText("Importuj i scal archiwum").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Sprawdź aktualizacje"))
        composeRule.onNodeWithText("Sprawdź aktualizacje").assertIsDisplayed()
    }

    @Test fun checkingDisablesRepeatedRequests() {
        composeRule.activity.setContent {
            MaterialTheme { Column { UpdateSection(UpdateUiState.Checking, {}, {}) } }
        }
        composeRule.onNodeWithText("Sprawdź aktualizacje").assertIsNotEnabled()
    }

    @Test fun downloadUsesExactRelease() {
        val update = AppUpdate(1, "pl.local.przepisy", 12, "1.3.4", 33,
            "https://github.com/rozgladacz/przepisy_mariki/releases/download/v1.3.4/Przepisy.apk",
            "a".repeat(64), "Poprawki")
        var downloaded: String? = null
        composeRule.activity.setContent {
            MaterialTheme { Column { UpdateSection(UpdateUiState.Available(update), {}, { downloaded = it }) } }
        }
        composeRule.onNodeWithText("Pobierz aktualizację").performClick()
        composeRule.runOnIdle { assertEquals(update.apkUrl, downloaded) }
    }

    @Test fun failedCheckCanBeRetried() {
        var checks = 0
        composeRule.activity.setContent {
            MaterialTheme { Column { UpdateSection(UpdateUiState.Error, { checks++ }, {}) } }
        }
        composeRule.onNodeWithText("Sprawdź aktualizacje").performClick()
        composeRule.runOnIdle { assertEquals(1, checks) }
    }
}
