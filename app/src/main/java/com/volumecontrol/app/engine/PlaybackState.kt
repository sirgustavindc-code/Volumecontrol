package com.volumecontrol.app.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Por que o motor não conseguiu trabalhar (mostrado na interface em linguagem simples). */
enum class EngineProblem {
    /** Esta versão do Android não expõe as funções internas necessárias (nada foi alterado). */
    API_UNAVAILABLE,

    /** O sistema de áudio não respondeu (Shizuku parou, permissão retirada, erro do sistema). */
    CALL_FAILED,
}

/** Estado observado pelo serviço e mostrado na interface. */
object PlaybackState {
    private val _playing = MutableStateFlow<Set<String>>(emptySet())
    /** Pacotes que estão tocando áudio agora. */
    val playing: StateFlow<Set<String>> = _playing.asStateFlow()

    private val _unsupported = MutableStateFlow<Set<String>>(emptySet())
    /** Apps configurados cujo player não aceitou o controle de volume. */
    val unsupported: StateFlow<Set<String>> = _unsupported.asStateFlow()

    private val _problem = MutableStateFlow<EngineProblem?>(null)
    val problem: StateFlow<EngineProblem?> = _problem.asStateFlow()

    fun update(playing: Set<String>, unsupported: Set<String>) {
        if (_playing.value != playing) _playing.value = playing
        if (_unsupported.value != unsupported) _unsupported.value = unsupported
        if (_problem.value != null) _problem.value = null
    }

    fun fail(problem: EngineProblem) {
        _playing.value = emptySet()
        _unsupported.value = emptySet()
        _problem.value = problem
    }

    fun clear() {
        _playing.value = emptySet()
        _unsupported.value = emptySet()
        _problem.value = null
    }
}
