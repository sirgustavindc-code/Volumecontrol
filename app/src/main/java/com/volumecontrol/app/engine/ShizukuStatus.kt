package com.volumecontrol.app.engine

enum class ShizukuStatus {
    /** App Shizuku não está instalado. */
    NOT_INSTALLED,

    /** Instalado, mas o serviço não está iniciado (ou parou). */
    NOT_RUNNING,

    /** Versão do Shizuku antiga demais (anterior à v11). */
    OUTDATED,

    /** Rodando; falta o usuário autorizar o Volume Control. */
    NEEDS_PERMISSION,

    /** O usuário negou e marcou "não perguntar de novo": só reativando dentro do Shizuku. */
    DENIED,

    READY,
}

/** Decisão pura (sem Android) do estado do Shizuku a partir do que foi observado. */
object ShizukuStatusRules {
    fun decide(
        installed: Boolean,
        binderAlive: Boolean,
        preV11: Boolean,
        granted: Boolean,
        denyForever: Boolean,
    ): ShizukuStatus = when {
        !binderAlive -> if (installed) ShizukuStatus.NOT_RUNNING else ShizukuStatus.NOT_INSTALLED
        preV11 -> ShizukuStatus.OUTDATED
        granted -> ShizukuStatus.READY
        denyForever -> ShizukuStatus.DENIED
        else -> ShizukuStatus.NEEDS_PERMISSION
    }
}

/** Quando o serviço de fundo deve estar rodando. */
object ServicePolicy {
    /**
     * Roda se o painel flutuante está ativo (a notificação "Abrir painel" precisa existir mesmo
     * sem Shizuku), OU se o Shizuku está pronto e (o controle está ligado OU ainda há apps
     * alterados que precisam ser devolvidos a 100%).
     */
    fun shouldRun(
        enabled: Boolean,
        ready: Boolean,
        hasTouched: Boolean,
        floatingActive: Boolean = false,
    ): Boolean = floatingActive || (ready && (enabled || hasTouched))
}

data class KeyDecision(val consume: Boolean, val newPercent: Int)

/** Regras das teclas físicas de volume. */
object KeyRules {
    /**
     * Com o app já em 100%, um toque NOVO em "volume +" é deixado para o Android
     * (sobe o volume geral, que é o teto do app). Segurando a tecla, nada passa para o sistema.
     */
    fun decide(up: Boolean, current: Int, repeatCount: Int): KeyDecision {
        val c = VolumeMath.clampPercent(current)
        if (up && c == 100 && repeatCount == 0) return KeyDecision(consume = false, newPercent = c)
        return KeyDecision(consume = true, newPercent = VolumeMath.step(c, up))
    }
}
