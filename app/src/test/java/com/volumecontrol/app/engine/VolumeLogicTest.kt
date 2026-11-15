package com.volumecontrol.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeMathTest {
    @Test fun gainIsLinearAndClamped() {
        assertEquals(0f, VolumeMath.toGain(-10), 0f)
        assertEquals(0.25f, VolumeMath.toGain(25), 0.0001f)
        assertEquals(1f, VolumeMath.toGain(100), 0f)
        assertEquals(1f, VolumeMath.toGain(250), 0f)
    }

    @Test fun stepAlignsToGrid() {
        assertEquals(50, VolumeMath.step(47, up = true))
        assertEquals(45, VolumeMath.step(47, up = false))
        assertEquals(55, VolumeMath.step(50, up = true))
        assertEquals(45, VolumeMath.step(50, up = false))
    }

    @Test fun stepStopsAtLimits() {
        assertEquals(100, VolumeMath.step(100, up = true))
        assertEquals(100, VolumeMath.step(98, up = true))
        assertEquals(0, VolumeMath.step(0, up = false))
        assertEquals(0, VolumeMath.step(3, up = false))
    }
}

class VolumePlannerTest {
    private fun player(key: Int, vararg pkgs: String, active: Boolean = true) =
        PlayerInfo(key, pkgs.toList(), active)

    @Test fun onlyConfiguredAppsAreTouched() {
        val plan = VolumePlanner.plan(
            players = listOf(player(0, "yt"), player(1, "game"), player(2, "discord")),
            volumes = mapOf("yt" to 80, "game" to 25),
            enabled = true,
            touched = emptySet(),
        )
        assertEquals(setOf("yt", "game"), plan.commands.map { it.packageName }.toSet())
        assertEquals(setOf("yt", "game", "discord"), plan.playing)
    }

    @Test fun configuredAt100AndNeverTouchedIsLeftAlone() {
        val plan = VolumePlanner.plan(listOf(player(0, "yt")), mapOf("yt" to 100), true, emptySet())
        assertTrue(plan.commands.isEmpty())
    }

    @Test fun disabledOnlyRestoresWhatWeChanged() {
        val plan = VolumePlanner.plan(
            players = listOf(player(0, "yt"), player(1, "game")),
            volumes = mapOf("yt" to 80, "game" to 25),
            enabled = false,
            touched = setOf("yt"),
        )
        assertEquals(1, plan.commands.size)
        assertEquals("yt", plan.commands[0].packageName)
        assertEquals(100, plan.commands[0].percent)
    }

    @Test fun resetRestoresEvenWithoutConfiguration() {
        val plan = VolumePlanner.plan(listOf(player(0, "yt")), emptyMap(), true, setOf("yt"))
        assertEquals(100, plan.commands.single().percent)
        val next = VolumePlanner.nextTouched(setOf("yt"), listOf(plan.commands.single() to true))
        assertTrue(next.isEmpty())
    }

    @Test fun sharedUidPicksTheConfiguredPackage() {
        val plan = VolumePlanner.plan(listOf(player(0, "a.one", "a.two")), mapOf("a.two" to 40), true, emptySet())
        assertEquals("a.two", plan.commands.single().packageName)
    }

    @Test fun pausedPlayerIsAdjustedButNotReportedAsPlaying() {
        val plan = VolumePlanner.plan(listOf(player(0, "yt", active = false)), mapOf("yt" to 30), true, emptySet())
        assertEquals(1, plan.commands.size)
        assertTrue(plan.playing.isEmpty())
    }

    @Test fun failureWhileRestoringKeepsPackageTouched() {
        val cmd = PlayerCommand(0, "yt", 100, true)
        val next = VolumePlanner.nextTouched(setOf("yt"), listOf(cmd to false))
        assertEquals(setOf("yt"), next)
    }

    @Test fun packageLeavesTouchedOnlyWhenAllItsPlayersRestored() {
        val a = PlayerCommand(0, "yt", 100, true)
        val b = PlayerCommand(1, "yt", 100, true)
        assertEquals(setOf("yt"), VolumePlanner.nextTouched(setOf("yt"), listOf(a to true, b to false)))
        assertTrue(VolumePlanner.nextTouched(setOf("yt"), listOf(a to true, b to true)).isEmpty())
    }

    @Test fun unsupportedOnlyForActiveReducedFailures() {
        val results = listOf(
            PlayerCommand(0, "game", 25, active = true) to false,
            PlayerCommand(1, "idle", 25, active = false) to false,
            PlayerCommand(2, "restoring", 100, active = true) to false,
            PlayerCommand(3, "ok", 25, active = true) to true,
        )
        assertEquals(setOf("game"), VolumePlanner.unsupported(results))
    }
}

class VolumeCodecTest {
    @Test fun roundTrip() {
        val map = mapOf("com.google.android.youtube" to 80, "com.discord" to 40)
        assertEquals(map, VolumeCodec.decodeVolumes(VolumeCodec.encodeVolumes(map)))
    }

    @Test fun hundredPercentIsNotStored() {
        assertEquals("", VolumeCodec.encodeVolumes(mapOf("com.a.b" to 100)))
        assertTrue(VolumeCodec.decodeVolumes("com.a.b=100").isEmpty())
    }

    @Test fun clampsOutOfRangeValues() {
        assertEquals(mapOf("com.a.b" to 0), VolumeCodec.decodeVolumes("com.a.b=-5"))
        assertTrue(VolumeCodec.decodeVolumes("com.a.b=900").isEmpty())
    }

    @Test fun ignoresCorruptedData() {
        val raw = ";;;=5;semponto=30;com.a.b=abc;com.ok.app=20;lixo;com.x.y="
        assertEquals(mapOf("com.ok.app" to 20), VolumeCodec.decodeVolumes(raw))
        assertTrue(VolumeCodec.decodeVolumes(null).isEmpty())
        assertTrue(VolumeCodec.decodeVolumes("   ").isEmpty())
    }

    @Test fun setRoundTripAndValidation() {
        val set = setOf("com.a.b", "org.c.d")
        assertEquals(set, VolumeCodec.decodeSet(VolumeCodec.encodeSet(set)))
        assertEquals(setOf("com.a.b"), VolumeCodec.decodeSet("com.a.b;nope;;../etc"))
    }
}

class ShizukuStatusRulesTest {
    private fun decide(
        installed: Boolean = true, alive: Boolean = true, preV11: Boolean = false,
        granted: Boolean = false, denyForever: Boolean = false,
    ) = ShizukuStatusRules.decide(installed, alive, preV11, granted, denyForever)

    @Test fun notInstalled() = assertEquals(ShizukuStatus.NOT_INSTALLED, decide(installed = false, alive = false))
    @Test fun installedButStopped() = assertEquals(ShizukuStatus.NOT_RUNNING, decide(alive = false))
    @Test fun outdated() = assertEquals(ShizukuStatus.OUTDATED, decide(preV11 = true))
    @Test fun needsPermission() = assertEquals(ShizukuStatus.NEEDS_PERMISSION, decide())
    @Test fun deniedForever() = assertEquals(ShizukuStatus.DENIED, decide(denyForever = true))
    @Test fun ready() = assertEquals(ShizukuStatus.READY, decide(granted = true))
    @Test fun stoppedBeatsEverythingElse() =
        assertEquals(ShizukuStatus.NOT_RUNNING, decide(alive = false, granted = true, denyForever = true))
}

class KeyRulesTest {
    @Test fun normalStep() {
        assertEquals(KeyDecision(true, 45), KeyRules.decide(up = false, current = 50, repeatCount = 0))
        assertEquals(KeyDecision(true, 55), KeyRules.decide(up = true, current = 50, repeatCount = 0))
    }

    @Test fun freshPressUpAtMaxFallsThroughToSystem() {
        assertFalse(KeyRules.decide(up = true, current = 100, repeatCount = 0).consume)
    }

    @Test fun holdingUpAtMaxDoesNotLeakToSystem() {
        val d = KeyRules.decide(up = true, current = 100, repeatCount = 3)
        assertTrue(d.consume)
        assertEquals(100, d.newPercent)
    }

    @Test fun downAtZeroIsConsumed() {
        val d = KeyRules.decide(up = false, current = 0, repeatCount = 0)
        assertTrue(d.consume)
        assertEquals(0, d.newPercent)
    }
}

class ServicePolicyTest {
    @Test fun runsWhenEnabledAndReady() = assertTrue(ServicePolicy.shouldRun(true, true, false))
    @Test fun neverRunsWithoutShizuku() = assertFalse(ServicePolicy.shouldRun(true, false, true))
    @Test fun runsToRestoreWhenDisabledButTouched() = assertTrue(ServicePolicy.shouldRun(false, true, true))
    @Test fun idleWhenDisabledAndNothingToRestore() = assertFalse(ServicePolicy.shouldRun(false, true, false))
}

/** Simula o sistema de áudio (sem Android) para testar o fluxo completo de ponta a ponta. */
class SessionSimulationTest {
    private class FakePlayer(val pkgs: List<String>, var active: Boolean = true, val controllable: Boolean = true) {
        var gain = 1f
    }

    private fun cycle(
        players: List<FakePlayer>,
        volumes: Map<String, Int>,
        enabled: Boolean,
        touched: Set<String>,
    ): Set<String> {
        val plan = VolumePlanner.plan(
            players.mapIndexed { i, p -> PlayerInfo(i, p.pkgs, p.active) }, volumes, enabled, touched,
        )
        val results = plan.commands.map { cmd ->
            val p = players[cmd.playerKey]
            if (p.controllable) p.gain = VolumeMath.toGain(cmd.percent)
            cmd to p.controllable
        }
        return VolumePlanner.nextTouched(touched, results)
    }

    @Test fun eachAppKeepsItsOwnVolume() {
        val yt = FakePlayer(listOf("yt")); val game = FakePlayer(listOf("game"))
        val spotify = FakePlayer(listOf("spotify")); val discord = FakePlayer(listOf("discord"))
        val vols = mapOf("yt" to 80, "game" to 25, "spotify" to 60)
        cycle(listOf(yt, game, spotify, discord), vols, true, emptySet())
        assertEquals(0.8f, yt.gain, 0.0001f)
        assertEquals(0.25f, game.gain, 0.0001f)
        assertEquals(0.6f, spotify.gain, 0.0001f)
        assertEquals(1f, discord.gain, 0f) // não configurado: intocado
    }

    @Test fun changingOneAppDoesNotAffectTheOthers() {
        val yt = FakePlayer(listOf("yt")); val game = FakePlayer(listOf("game"))
        var touched = cycle(listOf(yt, game), mapOf("yt" to 80, "game" to 25), true, emptySet())
        touched = cycle(listOf(yt, game), mapOf("yt" to 30, "game" to 25), true, touched)
        assertEquals(0.3f, yt.gain, 0.0001f)
        assertEquals(0.25f, game.gain, 0.0001f)
    }

    @Test fun restartThenDisableStillRestoresEverything() {
        // Nomes realistas: o gravador descarta (de propósito) nomes sem ponto, que o Android não permite.
        val yt = FakePlayer(listOf("com.google.android.youtube")); val game = FakePlayer(listOf("com.example.game"))
        val touched = cycle(
            listOf(yt, game),
            mapOf("com.google.android.youtube" to 80, "com.example.game" to 25), true, emptySet(),
        )
        assertEquals(setOf("com.google.android.youtube", "com.example.game"), touched)
        // "Reinicia" o app: o que foi alterado passa pela gravação em disco e volta.
        val reloaded = VolumeCodec.decodeSet(VolumeCodec.encodeSet(touched))
        val after = cycle(listOf(yt, game), emptyMap(), false, reloaded)
        assertEquals(1f, yt.gain, 0f)
        assertEquals(1f, game.gain, 0f)
        assertTrue(after.isEmpty())
    }

    @Test fun newPlayerOfSameAppGetsTheSavedVolume() {
        val vols = mapOf("yt" to 40)
        val first = FakePlayer(listOf("yt"))
        var touched = cycle(listOf(first), vols, true, emptySet())
        val next = FakePlayer(listOf("yt")) // próxima faixa/vídeo cria um player novo em 100%
        touched = cycle(listOf(next), vols, true, touched)
        assertEquals(0.4f, next.gain, 0.0001f)
    }

    @Test fun resetAllReturnsEveryAppToFull() {
        val yt = FakePlayer(listOf("yt"))
        val touched = cycle(listOf(yt), mapOf("yt" to 10), true, emptySet())
        val after = cycle(listOf(yt), emptyMap(), true, touched)
        assertEquals(1f, yt.gain, 0f)
        assertTrue(after.isEmpty())
    }

    @Test fun shizukuLostThenBackKeepsStateAndRestores() {
        val yt = FakePlayer(listOf("yt"))
        val touched = cycle(listOf(yt), mapOf("yt" to 20), true, emptySet())
        // Shizuku para: o serviço fica ocioso (nenhum ciclo roda) e nada é perdido.
        assertFalse(ServicePolicy.shouldRun(enabled = true, ready = false, hasTouched = touched.isNotEmpty()))
        assertEquals(0.2f, yt.gain, 0.0001f)
        // Usuário desliga o controle enquanto o Shizuku está parado; ao voltar, deve restaurar.
        assertFalse(ServicePolicy.shouldRun(enabled = false, ready = false, hasTouched = true))
        assertTrue(ServicePolicy.shouldRun(enabled = false, ready = true, hasTouched = true))
        cycle(listOf(yt), emptyMap(), false, touched)
        assertEquals(1f, yt.gain, 0f)
    }

    @Test fun uncontrollablePlayerIsReportedAndOthersStillWork() {
        val game = FakePlayer(listOf("game"), controllable = false)
        val yt = FakePlayer(listOf("yt"))
        val plan = VolumePlanner.plan(
            listOf(game, yt).mapIndexed { i, p -> PlayerInfo(i, p.pkgs, p.active) },
            mapOf("game" to 25, "yt" to 50), true, emptySet(),
        )
        val results = plan.commands.map { it to (it.packageName != "game") }
        assertEquals(setOf("game"), VolumePlanner.unsupported(results))
        assertEquals(setOf("yt"), VolumePlanner.nextTouched(emptySet(), results))
    }
}
