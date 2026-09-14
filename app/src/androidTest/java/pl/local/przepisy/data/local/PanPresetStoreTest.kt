package pl.local.przepisy.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import pl.local.przepisy.domain.model.PanPreset
import pl.local.przepisy.domain.model.PanShape
import pl.local.przepisy.domain.model.PanSpec

@RunWith(AndroidJUnit4::class)
class PanPresetStoreTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun clearPreferences() {
        context.getSharedPreferences("pan-presets", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun zapisuje_aktualizuje_i_usuwa_formy_po_ponownym_otwarciu_magazynu() {
        val store = PanPresetStore(context)
        store.save(
            PanPreset(
                id = "prostokat",
                name = "  Duża blacha  ",
                pan = PanSpec(PanShape.RECTANGLE, widthCm = 20.0, heightCm = 30.0),
            ),
        )
        store.save(
            PanPreset(
                id = "kolo",
                name = "Tortownica",
                pan = PanSpec(PanShape.CIRCLE, diameterCm = 24.0),
            ),
        )

        val reopened = PanPresetStore(context)
        assertEquals(listOf("Duża blacha", "Tortownica"), reopened.presets.value.map { it.name })
        assertEquals(24.0, reopened.presets.value.last().pan.diameterCm ?: 0.0, 0.0)

        reopened.save(
            reopened.presets.value.first().copy(
                name = "Mała blacha",
                pan = PanSpec(PanShape.RECTANGLE, widthCm = 18.0, heightCm = 24.0),
            ),
        )
        reopened.delete("kolo")

        val finalStore = PanPresetStore(context)
        assertEquals("Mała blacha", finalStore.presets.value.single().name)
        assertTrue(finalStore.presets.value.none { it.id == "kolo" })
    }
}
