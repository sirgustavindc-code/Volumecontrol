package com.volumecontrol.app.engine

/** Contas de volume sem dependência do Android (testável no computador). */
object VolumeMath {
    const val STEP_PERCENT = 5

    fun clampPercent(percent: Int): Int = percent.coerceIn(0, 100)

    /** Converte a porcentagem do controle em ganho linear 0.0..1.0 (a curva é linear). */
    fun toGain(percent: Int): Float = clampPercent(percent) / 100f

    /**
     * Sobe ou desce um degrau, alinhando à grade de múltiplos do degrau:
     * 47 sobe para 50 e desce para 45; 50 sobe para 55 e desce para 45.
     */
    fun step(current: Int, up: Boolean, step: Int = STEP_PERCENT): Int {
        val c = clampPercent(current)
        val next = when {
            up -> (c / step + 1) * step
            c % step == 0 -> c - step
            else -> (c / step) * step
        }
        return clampPercent(next)
    }
}
