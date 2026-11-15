package com.volumecontrol.app.engine

import android.content.Context
import android.os.IBinder
import android.util.Log
import com.volumecontrol.app.data.Settings
import rikka.shizuku.ShizukuBinderWrapper
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * Parte do app que fala com o sistema de áudio. A decisão do que mudar fica em [VolumePlanner]
 * (testada em computador); aqui só há as chamadas ao Android.
 *
 * Como funciona (mesmo mecanismo do "volume por app" da MIUI e de apps como o AppMixer):
 *  1. IAudioService.getActivePlaybackConfigurations(), chamado com a identidade do Shizuku (shell).
 *     Com a permissão de sistema MODIFY_AUDIO_ROUTING a lista vem completa, com o controle (IPlayer)
 *     de cada player. Para um app comum a lista vem "anonimizada", sem controle.
 *  2. Cada item informa o uid do app dono do player (getClientUid).
 *  3. PlayerProxy.setVolume(float) ajusta um multiplicador só daquele player. Não mexe no volume
 *     do sistema nem nos outros apps.
 *
 * IMPORTANTE: IAudioService, PlayerProxy e ServiceManager NÃO são APIs públicas do Android.
 * São acessadas por reflexão e podem mudar ou sumir em qualquer atualização do sistema. Se isso
 * acontecer, [apply] devolve [Outcome.Failed] com [EngineProblem.API_UNAVAILABLE] e nada é alterado.
 */
class AudioEngine(context: Context) {

    sealed interface Outcome {
        class Ok(val playing: Set<String>, val unsupported: Set<String>) : Outcome
        class Failed(val problem: EngineProblem) : Outcome
    }

    private class Api(
        val getClientUid: Method,
        val getPlayerProxy: Method,
        val isActive: Method,
        val setVolume: Method,
        val getConfigs: Method,
    )

    private val pm = context.applicationContext.packageManager
    private var audioService: Any? = null

    // null = esta versão do Android não tem os métodos necessários.
    private val api: Api? by lazy { resolveApi() }

    /**
     * Aplica os volumes a todos os players existentes. Com [enabled] = false, devolve a 100% só o
     * que foi alterado antes por este app. Chamar fora da thread principal.
     */
    fun apply(volumes: Map<String, Int>, enabled: Boolean): Outcome {
        val api = api ?: return Outcome.Failed(EngineProblem.API_UNAVAILABLE)

        val configs: List<Any> = try {
            queryConfigs(api)
        } catch (t: Throwable) {
            audioService = null // força reconectar na próxima vez (ex.: Shizuku reiniciou)
            val cause = unwrap(t)
            Log.w(TAG, "Falha ao listar players de áudio", cause)
            return Outcome.Failed(classify(cause))
        }

        val players = configs.mapIndexed { index, cfg ->
            PlayerInfo(index, packagesOf(api, cfg), isActive(api, cfg))
        }
        val touchedBefore = Settings.touched()
        val plan = VolumePlanner.plan(players, volumes, enabled, touchedBefore)
        val results = plan.commands.map { cmd ->
            cmd to setVolume(api, configs[cmd.playerKey], VolumeMath.toGain(cmd.percent))
        }
        Settings.setTouched(VolumePlanner.nextTouched(touchedBefore, results))
        return Outcome.Ok(plan.playing, VolumePlanner.unsupported(results))
    }

    private fun packagesOf(api: Api, cfg: Any): List<String> = try {
        val uid = api.getClientUid.invoke(cfg) as Int
        pm.getPackagesForUid(uid)?.toList() ?: emptyList()
    } catch (t: Throwable) {
        emptyList()
    }

    private fun isActive(api: Api, cfg: Any): Boolean = try {
        api.isActive.invoke(cfg) as Boolean
    } catch (t: Throwable) {
        true
    }

    private fun setVolume(api: Api, cfg: Any, gain: Float): Boolean = try {
        val proxy = api.getPlayerProxy.invoke(cfg)
        if (proxy == null) {
            false // player sem controle externo: o sistema não entrega o IPlayer
        } else {
            api.setVolume.invoke(proxy, gain)
            true
        }
    } catch (t: Throwable) {
        Log.w(TAG, "setVolume falhou", unwrap(t))
        false
    }

    @Suppress("UNCHECKED_CAST")
    private fun queryConfigs(api: Api): List<Any> =
        (api.getConfigs.invoke(service()) as? List<Any>) ?: emptyList()

    private fun service(): Any {
        audioService?.let { return it }
        val binder = Class.forName("android.os.ServiceManager")
            .getMethod("getService", String::class.java)
            .invoke(null, "audio") as? IBinder
            ?: throw IllegalStateException("Serviço de áudio não encontrado")
        // As chamadas ao AudioService passam a sair com a identidade do Shizuku (shell).
        val svc = Class.forName("android.media.IAudioService\$Stub")
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, ShizukuBinderWrapper(binder))
            ?: throw IllegalStateException("IAudioService indisponível")
        audioService = svc
        return svc
    }

    private fun resolveApi(): Api? = try {
        val cfg = Class.forName("android.media.AudioPlaybackConfiguration")
        val proxy = Class.forName("android.media.PlayerProxy")
        val svc = Class.forName("android.media.IAudioService")
        Api(
            getClientUid = cfg.getMethod("getClientUid"),
            getPlayerProxy = cfg.getMethod("getPlayerProxy"),
            isActive = cfg.getMethod("isActive"),
            setVolume = proxy.getMethod("setVolume", Float::class.javaPrimitiveType),
            getConfigs = svc.getMethod("getActivePlaybackConfigurations"),
        )
    } catch (t: Throwable) {
        Log.w(TAG, "Funções internas de áudio indisponíveis neste Android", t)
        null
    }

    private fun classify(t: Throwable): EngineProblem = when (t) {
        is ClassNotFoundException, is NoSuchMethodException, is NoSuchFieldException,
        is IllegalAccessException, is LinkageError -> EngineProblem.API_UNAVAILABLE
        else -> EngineProblem.CALL_FAILED
    }

    private fun unwrap(t: Throwable): Throwable =
        if (t is InvocationTargetException) (t.targetException ?: t) else t

    private companion object {
        const val TAG = "AudioEngine"
    }
}
