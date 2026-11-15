package com.volumecontrol.app.data

import android.content.Context
import android.content.SharedPreferences
import com.volumecontrol.app.engine.VolumeCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Configurações guardadas só no aparelho (SharedPreferences privado do app).
 * Volumes em porcentagem 0..100; 100 é o padrão e não é gravado.
 */
object Settings {
    private const val PREFS = "volume_control"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_DARK = "dark_theme"
    private const val KEY_KEYS = "volume_keys"
    private const val KEY_FLOATING = "floating_panel"
    private const val KEY_PANEL_X = "panel_x"
    private const val KEY_PANEL_Y = "panel_y"
    private const val KEY_PANEL_LOCKED = "panel_locked"
    private const val KEY_VOLUMES = "volumes_v2"
    private const val KEY_TOUCHED = "touched_v2"
    private const val KEY_VOLUMES_LEGACY = "volumes" // JSON da versão 1.0.0

    private lateinit var prefs: SharedPreferences

    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _darkTheme = MutableStateFlow(true)
    val darkTheme: StateFlow<Boolean> = _darkTheme.asStateFlow()

    private val _keysEnabled = MutableStateFlow(true)
    /** Teclas de volume mudam só o volume do app em uso (precisa do serviço de acessibilidade ligado). */
    val keysEnabled: StateFlow<Boolean> = _keysEnabled.asStateFlow()

    private val _floatingEnabled = MutableStateFlow(false)
    /** Painel flutuante ligado: a notificação com "Abrir painel" fica disponível. Desligado por padrão. */
    val floatingEnabled: StateFlow<Boolean> = _floatingEnabled.asStateFlow()

    private val _panelLocked = MutableStateFlow(false)
    /** Posição do painel fixada (evita arrastar sem querer durante uma partida). */
    val panelLocked: StateFlow<Boolean> = _panelLocked.asStateFlow()

    private val _volumes = MutableStateFlow<Map<String, Int>>(emptyMap())
    val volumes: StateFlow<Map<String, Int>> = _volumes.asStateFlow()

    private val lock = Any()
    private var touched: Set<String> = emptySet()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _enabled.value = prefs.getBoolean(KEY_ENABLED, true)
        _darkTheme.value = prefs.getBoolean(KEY_DARK, true)
        _keysEnabled.value = prefs.getBoolean(KEY_KEYS, true)
        _floatingEnabled.value = prefs.getBoolean(KEY_FLOATING, false)
        _panelLocked.value = prefs.getBoolean(KEY_PANEL_LOCKED, false)
        _volumes.value = readVolumes()
        touched = VolumeCodec.decodeSet(prefs.getString(KEY_TOUCHED, null))
    }

    fun setEnabled(value: Boolean) {
        _enabled.value = value
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
    }

    fun setDarkTheme(value: Boolean) {
        _darkTheme.value = value
        prefs.edit().putBoolean(KEY_DARK, value).apply()
    }

    fun setKeysEnabled(value: Boolean) {
        _keysEnabled.value = value
        prefs.edit().putBoolean(KEY_KEYS, value).apply()
    }

    fun setFloatingEnabled(value: Boolean) {
        _floatingEnabled.value = value
        prefs.edit().putBoolean(KEY_FLOATING, value).apply()
    }

    fun setPanelLocked(value: Boolean) {
        _panelLocked.value = value
        prefs.edit().putBoolean(KEY_PANEL_LOCKED, value).apply()
    }

    /** Última posição do painel em pixels (canto superior esquerdo), ou null se nunca foi movido. */
    fun panelPosition(): Pair<Int, Int>? {
        val x = prefs.getInt(KEY_PANEL_X, -1)
        val y = prefs.getInt(KEY_PANEL_Y, -1)
        return if (x >= 0 && y >= 0) Pair(x, y) else null
    }

    fun setPanelPosition(x: Int, y: Int) {
        prefs.edit().putInt(KEY_PANEL_X, x.coerceAtLeast(0)).putInt(KEY_PANEL_Y, y.coerceAtLeast(0)).apply()
    }

    /** Atualiza o volume (aplicado na hora) e, se pedido, grava em disco. */
    fun setVolume(packageName: String, percent: Int, persist: Boolean) {
        val v = percent.coerceIn(0, 100)
        _volumes.value = if (v == 100) _volumes.value - packageName else _volumes.value + (packageName to v)
        if (persist) persist()
    }

    fun persist() {
        prefs.edit().putString(KEY_VOLUMES, VolumeCodec.encodeVolumes(_volumes.value)).apply()
    }

    fun resetAll() {
        _volumes.value = emptyMap()
        persist()
    }

    /** Pacotes cujos players foram abaixados por nós e ainda não voltaram a 100%. Sobrevive a reinícios. */
    fun touched(): Set<String> = synchronized(lock) { touched }

    fun setTouched(value: Set<String>) {
        synchronized(lock) {
            if (value == touched) return
            touched = value
            prefs.edit().putString(KEY_TOUCHED, VolumeCodec.encodeSet(value)).apply()
        }
    }

    private fun readVolumes(): Map<String, Int> {
        val current = prefs.getString(KEY_VOLUMES, null)
        if (current != null) return VolumeCodec.decodeVolumes(current)
        // Migração da versão 1.0.0 (JSON). Reaproveita o decodificador para validar os dados.
        val legacy = prefs.getString(KEY_VOLUMES_LEGACY, null) ?: return emptyMap()
        val migrated = try {
            val json = JSONObject(legacy)
            json.keys().asSequence().joinToString(";") { "$it=${json.optInt(it, 100)}" }
        } catch (e: Exception) {
            ""
        }
        val map = VolumeCodec.decodeVolumes(migrated)
        prefs.edit()
            .putString(KEY_VOLUMES, VolumeCodec.encodeVolumes(map))
            .remove(KEY_VOLUMES_LEGACY)
            .apply()
        return map
    }
}
