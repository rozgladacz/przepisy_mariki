package pl.local.przepisy.data.update

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateTest {
    private val repo = "rozgladacz/przepisy_mariki"
    private val update = AppUpdate(
        1, "pl.local.przepisy", 11, "1.3.3", 33,
        "https://github.com/$repo/releases/download/v1.3.3/Przepisy.apk",
        "a".repeat(64), "Nowa wersja",
    )

    private fun check(value: AppUpdate = update, installed: Long = 10, sdk: Int = 33) =
        evaluateUpdate(Json.encodeToString(value), repo, installed, sdk)

    @Test fun detectsNewerVersion() {
        assertTrue(check() is UpdateUiState.Available)
    }

    @Test fun equalOrOlderVersionDoesNotOfferDowngrade() {
        assertEquals(UpdateUiState.Current, check(installed = 11))
        assertEquals(UpdateUiState.Current, check(installed = 12))
    }

    @Test fun incompatibleAndroidDoesNotOfferDownload() {
        assertEquals(UpdateUiState.Incompatible, check(update.copy(minSdk = 36)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsForeignDownload() {
        check(update.copy(apkUrl = "https://example.com/Przepisy.apk"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMismatchedReleaseVersion() {
        check(update.copy(versionName = "1.3.4"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDifferentApplication() {
        check(update.copy(applicationId = "other.app"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownMetadataSchema() {
        check(update.copy(schemaVersion = 2))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidChecksum() {
        check(update.copy(sha256 = "invalid"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMalformedJson() {
        evaluateUpdate("{}", repo, 10, 33)
    }
}
