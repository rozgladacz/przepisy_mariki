package pl.local.przepisy.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import pl.local.przepisy.domain.model.PanPreset

class PanPresetStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
    private val _presets = MutableStateFlow(readPresets())

    val presets: StateFlow<List<PanPreset>> = _presets.asStateFlow()

    @Synchronized
    fun save(preset: PanPreset) {
        require(preset.name.isNotBlank()) { "Podaj nazwę formy." }
        require(preset.pan.isValid()) { "Podaj poprawne wymiary formy." }
        val normalized = preset.copy(name = preset.name.trim())
        val current = _presets.value.toMutableList()
        val index = current.indexOfFirst { it.id == normalized.id }
        if (index >= 0) current[index] = normalized else current += normalized
        persist(current)
    }

    @Synchronized
    fun delete(id: String) {
        persist(_presets.value.filterNot { it.id == id })
    }

    private fun readPresets(): List<PanPreset> = runCatching {
        preferences.getString(KEY_DOCUMENT, null)
            ?.let { json.decodeFromString<PanPresetDocument>(it) }
            ?.presets
            ?.filter { it.name.isNotBlank() && it.pan.isValid() }
            .orEmpty()
    }.getOrDefault(emptyList())

    private fun persist(presets: List<PanPreset>) {
        preferences.edit()
            .putString(KEY_DOCUMENT, json.encodeToString(PanPresetDocument(presets = presets)))
            .apply()
        _presets.value = presets
    }

    @Serializable
    private data class PanPresetDocument(
        val version: Int = 1,
        val presets: List<PanPreset> = emptyList(),
    )

    private companion object {
        const val PREFERENCES_NAME = "pan-presets"
        const val KEY_DOCUMENT = "document"
    }
}
