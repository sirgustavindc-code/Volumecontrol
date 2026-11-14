package com.volumecontrol.app.engine

import android.content.Context
import android.os.IBinder
import android.util.Log
import rikka.shizuku.ShizukuBinderWrapper
import java.lang.reflect.InvocationTargetException

/**
 * Núcleo do app: controla o volume de cada aplicativo de verdade.
 *
 * Como funciona (mesmo mecanismo usado pelo "volume por app" da MIUI e por apps como o AppMixer):
 *  1. Chama IAudioService.getActivePlaybackConfigurations() com a identidade do Shizuku (shell).
 *     Quem tem MODIFY_AUDIO_ROUTING recebe a lista COMPLETA, incluindo o IPlayer de cada player.
 *     Um app comum recebe a lista "anonimizada" (sem IPlayer) e não consegue controlar nada.
 *  2. Cada AudioPlaybackConfiguration traz o uid do app dono do player (getClientUid).
 *  3. PlayerProxy.setVolume(float) chama IPlayer.setVolume() dentro do processo do app dono,
 *     alterando um multiplicador de volume só daquele player (não mexe no volume do sistema).
 *
 * Tudo isso são APIs ocultas/de sistema, acessadas por reflexão (ver VolumeApp para a liberação).
 */
class AudioEngine(context: Context) {

    class Result(val playing: Set<String>, val unsupported: Set<String>)

    private val pm = context.applicationContext.packageManager
    private var audioService: Any? = null

    // Pacotes cujos players tiveram o volume reduzido por nós (precisam ser devolvidos a 100%).
    private val touched = HashSet<String>()

    private val configClass by lazy { Class.forName("android.media.AudioPlaybackConfiguration") }
    private val getClientUid by lazy { configClass.getMethod("getClientUid") }
    private val getPlayerProxy by lazy { configClass.getMethod("getPlayerProxy") }
    private val isActive by lazy { configClass.getMethod("isActive") }
    private val setVolumeMethod by lazy {
        Class.forName("android.media.PlayerProxy")
            .getMethod("setVolume", Float::class.javaPrimitiveType)
    }
    private val getConfigsMethod by lazy {
        Class.forName("android.media.IAudioService").getMethod("getActivePlaybackConfigurations")
    }

    /**
     * Aplica os volumes a todos os players ativos. Chamado pelo serviço a cada evento de áudio.
     * Se [enabled] for false, devolve todos os players alterados para 100%.
     * Retorna null se não foi possível falar com o sistema de áudio (ex.: Shizuku parou).
     */
    fun apply(volumes: Map<String, Int>, enabled: Boolean): Result? {
        val configs = try {
            queryConfigs()
        } catch (t: Throwable) {
            audioService = null
            Log.w(TAG, "Falha ao listar players de áudio", unwrap(t))
            return null
        }

        val resetPkgs = HashSet(touched)
        val playing = HashSet<String>()
        val unsupported = HashSet<String>()
        val nowTouched = HashSet<String>()

        for (cfg in configs) {
            val uid = try {
                getClientUid.invoke(cfg) as Int
            } catch (t: Throwable) {
                continue
            }
            val pkgs = pm.getPackagesForUid(uid) ?: continue
            val active = try {
                isActive.invoke(cfg) as Boolean
            } catch (t: Throwable) {
                true
            }
            if (active) playing.addAll(pkgs)

            val target = pkgs.firstOrNull { volumes.containsKey(it) || resetPkgs.contains(it) }
                ?: continue
            val percent = if (enabled) (volumes[target] ?: 100) else 100

            val ok = setPlayerVolume(cfg, toGain(percent))
            if (!ok && active) unsupported.add(target)
            if (ok && percent < 100) nowTouched.add(target)
        }

        touched.clear()
        touched.addAll(nowTouched)
        return Result(playing, unsupported)
    }

    /** Converte a porcentagem do controle deslizante em ganho linear (0.0..1.0). */
    private fun toGain(percent: Int): Float = percent.coerceIn(0, 100) / 100f

    private fun setPlayerVolume(cfg: Any, gain: Float): Boolean = try {
        val proxy = getPlayerProxy.invoke(cfg)
        if (proxy == null) {
            false // player sem IPlayer: o sistema não permite controlar (alguns players nativos)
        } else {
            setVolumeMethod.invoke(proxy, gain)
            true
        }
    } catch (t: Throwable) {
        Log.w(TAG, "setVolume falhou", unwrap(t))
        false
    }

    @Suppress("UNCHECKED_CAST")
    private fun queryConfigs(): List<Any> {
        val result = getConfigsMethod.invoke(service()) as? List<Any>
        return result ?: emptyList()
    }

    private fun service(): Any {
        audioService?.let { return it }
        val binder = Class.forName("android.os.ServiceManager")
            .getMethod("getService", String::class.java)
            .invoke(null, "audio") as IBinder
        // A chamada ao AudioService passa a ser feita com a identidade do Shizuku (shell).
        val wrapped = ShizukuBinderWrapper(binder)
        val svc = Class.forName("android.media.IAudioService\$Stub")
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, wrapped)
            ?: throw IllegalStateException("IAudioService indisponível")
        audioService = svc
        return svc
    }

    private fun unwrap(t: Throwable): Throwable =
        if (t is InvocationTargetException) (t.targetException ?: t) else t

    private companion object {
        const val TAG = "AudioEngine"
    }
}
