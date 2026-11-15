package com.volumecontrol.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelRulesTest {

    @Test fun clampKeepsPanelInsideTheScreen() {
        assertEquals(PanelRules.Pos(500, 100), PanelRules.clamp(500, 100, 300, 400, 1000, 800))
        assertEquals(PanelRules.Pos(700, 400), PanelRules.clamp(900, 900, 300, 400, 1000, 800))
        assertEquals(PanelRules.Pos(0, 0), PanelRules.clamp(-50, -20, 300, 400, 1000, 800))
    }

    @Test fun clampHandlesPanelBiggerThanScreen() {
        assertEquals(PanelRules.Pos(0, 0), PanelRules.clamp(120, 90, 1200, 900, 1000, 800))
    }

    @Test fun savedPositionIsRecoveredAfterRotationWithoutLeavingTheScreen() {
        // Posição salva em retrato (x=800) e tela agora em paisagem mais estreita em altura.
        val pos = PanelRules.clamp(800, 700, 300, 400, 1600, 720)
        assertEquals(PanelRules.Pos(800, 320), pos)
    }

    @Test fun defaultPositionIsTopRightWithMargin() {
        assertEquals(PanelRules.Pos(688, 72), PanelRules.defaultPosition(300, 1000, 12, 72))
        assertEquals(0, PanelRules.defaultPosition(1200, 1000, 12, 72).x)
    }

    @Test fun widthIsCappedAndProportional() {
        assertEquals(340, PanelRules.panelWidthDp(800))
        assertEquals(324, PanelRules.panelWidthDp(360))
        assertEquals(270, PanelRules.panelWidthDp(300))
    }

    @Test fun listHeightFitsLandscapeAndNeverCollapses() {
        assertEquals(530, PanelRules.maxListHeightDp(800))  // retrato
        assertEquals(156, PanelRules.maxListHeightDp(360))  // paisagem (jogos)
        assertEquals(96, PanelRules.maxListHeightDp(100))   // tela minúscula: mínimo usável
    }

    @Test fun colorIndexIsStableAndInRange() {
        val pkg = "com.google.android.youtube"
        assertEquals(PanelRules.colorIndex(pkg), PanelRules.colorIndex(pkg))
        for (i in 0 until 5000) {
            val idx = PanelRules.colorIndex("com.example.app$i")
            assertTrue(idx in 0..3)
        }
        // Hash negativo extremo não pode gerar índice negativo.
        assertTrue(PanelRules.colorIndex("polygenelubricants") in 0..3)
    }

    @Test fun compactListShowsPlayingConfiguredAndPinned() {
        val all = listOf("a.a", "b.b", "c.c", "d.d", "e.e")
        val shown = PanelRules.visiblePackages(
            all = all, playing = setOf("b.b"), configured = setOf("d.d"),
            expanded = false, pinned = setOf("e.e"),
        )
        assertEquals(listOf("b.b", "d.d", "e.e"), shown) // mantém a ordem alfabética
    }

    @Test fun expandedListShowsEveryApp() {
        val all = listOf("a.a", "b.b", "c.c")
        assertEquals(all, PanelRules.visiblePackages(all, emptySet(), emptySet(), true, emptySet()))
    }

    @Test fun compactListIsEmptyWhenNothingPlaysAndNothingConfigured() {
        assertTrue(PanelRules.visiblePackages(listOf("a.a"), emptySet(), emptySet(), false, emptySet()).isEmpty())
    }
}

class FloatingServicePolicyTest {
    @Test fun floatingPanelKeepsServiceAliveEvenWithoutShizuku() =
        assertTrue(ServicePolicy.shouldRun(enabled = true, ready = false, hasTouched = false, floatingActive = true))

    @Test fun floatingPanelKeepsServiceAliveEvenWhenIndividualControlIsOff() =
        assertTrue(ServicePolicy.shouldRun(enabled = false, ready = true, hasTouched = false, floatingActive = true))

    @Test fun serviceStopsWhenNothingNeedsIt() =
        assertFalse(ServicePolicy.shouldRun(enabled = false, ready = true, hasTouched = false, floatingActive = false))

    @Test fun oldBehaviourIsPreservedWhenFloatingIsOff() {
        assertTrue(ServicePolicy.shouldRun(true, true, false))
        assertFalse(ServicePolicy.shouldRun(true, false, true))
        assertTrue(ServicePolicy.shouldRun(false, true, true))
    }
}
