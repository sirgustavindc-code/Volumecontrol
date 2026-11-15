package com.volumecontrol.app.engine

/** Regras do painel flutuante sem dependência do Android (testáveis no computador). */
object PanelRules {
    const val MAX_WIDTH_DP = 340

    // Altura ocupada por cabeçalho, rodapé e margens (o resto é a lista de apps).
    private const val CHROME_DP = 150
    private const val MIN_LIST_DP = 96

    data class Pos(val x: Int, val y: Int)

    /** Mantém o painel inteiro dentro da tela. Se ele for maior que a tela, fica no canto 0,0. */
    fun clamp(x: Int, y: Int, viewWidth: Int, viewHeight: Int, screenWidth: Int, screenHeight: Int): Pos {
        val maxX = (screenWidth - viewWidth).coerceAtLeast(0)
        val maxY = (screenHeight - viewHeight).coerceAtLeast(0)
        return Pos(x.coerceIn(0, maxX), y.coerceIn(0, maxY))
    }

    /** Primeira posição (ainda sem posição salva): canto superior direito, com margem. */
    fun defaultPosition(viewWidth: Int, screenWidth: Int, margin: Int, top: Int): Pos =
        Pos((screenWidth - viewWidth - margin).coerceAtLeast(0), top)

    /** Largura do painel: no máximo 340 dp e nunca mais que 90% da tela. */
    fun panelWidthDp(screenWidthDp: Int): Int = minOf(MAX_WIDTH_DP, screenWidthDp * 9 / 10)

    /** Altura máxima da lista: 85% da tela menos cabeçalho e rodapé (jogos costumam ser em paisagem). */
    fun maxListHeightDp(screenHeightDp: Int): Int =
        (screenHeightDp * 85 / 100 - CHROME_DP).coerceAtLeast(MIN_LIST_DP)

    /** Cor do controle deslizante de cada app: estável (sempre a mesma para o mesmo app). */
    fun colorIndex(packageName: String, palette: Int = 4): Int =
        (packageName.hashCode() and Int.MAX_VALUE) % palette

    /**
     * Quais apps aparecem na lista. Compacto: os que estão tocando, os já configurados e os que o
     * usuário mexeu nesta abertura (para a linha não sumir debaixo do dedo). Expandido: todos.
     */
    fun visiblePackages(
        all: List<String>,
        playing: Set<String>,
        configured: Set<String>,
        expanded: Boolean,
        pinned: Set<String>,
    ): List<String> =
        if (expanded) all else all.filter { it in playing || it in configured || it in pinned }
}
