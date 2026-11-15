package com.volumecontrol.app.engine

/** Um player de áudio ativo no sistema, já reduzido ao que o planejamento precisa saber. */
data class PlayerInfo(
    /** Posição na lista original (o motor usa para achar o objeto do sistema). */
    val key: Int,
    /** Pacotes que compartilham o uid dono do player. */
    val packages: List<String>,
    /** true se está tocando agora (false = pausado/parado, mas ainda existe). */
    val active: Boolean,
)

data class PlayerCommand(
    val playerKey: Int,
    val packageName: String,
    val percent: Int,
    val active: Boolean,
)

data class Plan(val commands: List<PlayerCommand>, val playing: Set<String>)

/**
 * Decide, sem tocar no sistema, quais players precisam de ajuste.
 * Regra de ouro: nunca mexe em app que o usuário não configurou nem em app que já alteramos antes.
 */
object VolumePlanner {

    fun plan(
        players: List<PlayerInfo>,
        volumes: Map<String, Int>,
        enabled: Boolean,
        touched: Set<String>,
    ): Plan {
        val playing = LinkedHashSet<String>()
        val commands = ArrayList<PlayerCommand>()
        for (p in players) {
            if (p.active) playing.addAll(p.packages)
            val target = p.packages.firstOrNull { it in volumes || it in touched } ?: continue
            val percent = if (enabled) VolumeMath.clampPercent(volumes[target] ?: 100) else 100
            // App configurado em 100% que nunca foi alterado: não há nada a fazer.
            if (percent == 100 && target !in touched) continue
            commands += PlayerCommand(p.key, target, percent, p.active)
        }
        return Plan(commands, playing)
    }

    /**
     * Novo conjunto de pacotes "alterados por nós" depois de executar os comandos.
     * Um pacote só sai do conjunto quando TODOS os seus players voltaram a 100%.
     */
    fun nextTouched(previous: Set<String>, results: List<Pair<PlayerCommand, Boolean>>): Set<String> {
        val next = HashSet(previous)
        for ((pkg, list) in results.groupBy { it.first.packageName }) {
            val reduced = list.any { it.first.percent < 100 && it.second }
            val allOk = list.all { it.second }
            when {
                reduced -> next += pkg
                allOk -> next -= pkg
                else -> Unit // falhou ao restaurar: mantém para tentar de novo
            }
        }
        return next
    }

    /** Apps que o usuário quer abaixar, estão tocando, e cujo player recusou o controle. */
    fun unsupported(results: List<Pair<PlayerCommand, Boolean>>): Set<String> =
        results
            .filter { (cmd, ok) -> !ok && cmd.active && cmd.percent < 100 }
            .map { it.first.packageName }
            .toSet()
}
