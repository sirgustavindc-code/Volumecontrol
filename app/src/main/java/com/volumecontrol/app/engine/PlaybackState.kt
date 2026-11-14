package com.volumecontrol.app.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Estado observado pelo serviço e mostrado na interface. */
object PlaybackState {
    private val _playing = MutableStateFlow<Set<String>>(emptySet())
    /** Pacotes que estão tocando áudio agora. */
    val playing: StateFlow<Set<String>> = _playing.asStateFlow()

    private val _unsupported = MutableStateFlow<Set<String>>(emptySet())
    /** Apps configurados cujo player não aceitou o controle de volume. */
    val unsupported: StateFlow<Set<String>> = _unsupported.asStateFlow()

    fun update(playing: Set<String>, unsupported: Set<String>) {
        if (_playing.value != playing) _playing.value = playing
        if (_unsupported.value != unsupported) _unsupported.value = unsupported
    }

    fun clear() = update(emptySet(), emptySet())
}
