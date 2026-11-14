package com.volumecontrol.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Configurações persistidas (SharedPreferences) e expostas como StateFlow.
 * Volumes guardados em porcentagem (0..100). 100 é o padrão e não é guardado.
 */
object Settings {
    private const val PREFS = "volume_control"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_DARK = "dark_theme"
    private const val KEY_KEYS = "volume_keys"
    private const val KEY_VOLUMES = "volumes"

    private lateinit var prefs: SharedPreferences

    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _darkTheme = MutableStateFlow(true)
    val darkTheme: StateFlow<Boolean> = _darkTheme.asStateFlow()

    private val _keysEnabled = MutableStateFlow(true)
    /** Teclas de volume mudam só o volume do app em uso (precisa do serviço de acessibilidade). */
    val keysEnabled: StateFlow<Boolean> = _keysEnabled.asStateFlow()

    private val _volumes = MutableStateFlow<Map<String, Int>>(emptyMap())
    val volumes: StateFlow<Map<String, Int>> = _volumes.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _enabled.value = prefs.getBoolean(KEY_ENABLED, true)
        _darkTheme.value = prefs.getBoolean(KEY_DARK, true)
        _keysEnabled.value = prefs.getBoolean(KEY_KEYS, true)
        _volumes.value = readVolumes()
    }

    fun setKeysEnabled(value: Boolean) {
        _keysEnabled.value = value
        prefs.edit().putBoolean(KEY_KEYS, value).apply()
    }

    fun setEnabled(value: Boolean) {
        _enabled.value = value
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
    }

    fun setDarkTheme(value: Boolean) {
        _darkTheme.value = value
        prefs.edit().putBoolean(KEY_DARK, value).apply()
    }

    /** Atualiza o volume em memória (aplicado na hora) e, se pedido, grava em disco. */
    fun setVolume(packageName: String, percent: Int, persist: Boolean) {
        val v = percent.coerceIn(0, 100)
        _volumes.value = if (v == 100) _volumes.value - packageName else _volumes.value + (packageName to v)
        if (persist) persist()
    }

    fun persist() {
        val json = JSONObject()
        for ((pkg, v) in _volumes.value) json.put(pkg, v)
        prefs.edit().putString(KEY_VOLUMES, json.toString()).apply()
    }

    fun resetAll() {
        _volumes.value = emptyMap()
        persist()
    }

    private fun readVolumes(): Map<String, Int> {
        val raw = prefs.getString(KEY_VOLUMES, null) ?: return emptyMap()
        return try {
            val json = JSONObject(raw)
            val out = HashMap<String, Int>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = json.optInt(k, 100).coerceIn(0, 100)
                if (v != 100) out[k] = v
            }
            out
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
