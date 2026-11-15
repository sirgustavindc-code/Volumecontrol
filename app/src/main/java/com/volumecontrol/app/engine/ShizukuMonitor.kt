package com.volumecontrol.app.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

/** Acompanha se o Shizuku está instalado, ativo e se o app já recebeu permissão. Nunca lança exceção. */
object ShizukuMonitor {
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val SHIZUKU_SITE = "https://shizuku.rikka.app/download/"
    private const val PERMISSION_REQUEST_CODE = 4242
    private const val TAG = "ShizukuMonitor"

    private lateinit var appContext: Context

    private val _status = MutableStateFlow(ShizukuStatus.NOT_RUNNING)
    val status: StateFlow<ShizukuStatus> = _status.asStateFlow()

    private val binderReceived = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDead = Shizuku.OnBinderDeadListener { refresh() }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

    fun init(context: Context) {
        appContext = context.applicationContext
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceived)
            Shizuku.addBinderDeadListener(binderDead)
            Shizuku.addRequestPermissionResultListener(permissionResult)
        } catch (t: Throwable) {
            Log.w(TAG, "Não foi possível registrar os ouvintes do Shizuku", t)
        }
        refresh()
    }

    fun refresh() {
        _status.value = compute()
    }

    /** Mostra o pedido de permissão do Shizuku. Só faz sentido no estado NEEDS_PERMISSION. */
    fun requestPermission() {
        try {
            if (compute() == ShizukuStatus.NEEDS_PERMISSION) {
                Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Falha ao pedir permissão", t)
        }
        refresh()
    }

    /** Abre o app Shizuku. Retorna false se ele não estiver instalado. */
    fun openShizukuApp(context: Context): Boolean = try {
        val intent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
        if (intent == null) {
            false
        } else {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }
    } catch (t: Throwable) {
        false
    }

    /** Abre a página de instalação do Shizuku (loja do aparelho; se não houver, o site oficial). */
    fun openInstallPage(context: Context) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$SHIZUKU_PACKAGE"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val web = Intent(Intent.ACTION_VIEW, Uri.parse(SHIZUKU_SITE)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(market)
        } catch (e: Exception) {
            try {
                context.startActivity(web)
            } catch (e2: Exception) {
                Log.w(TAG, "Nenhum app para abrir o link do Shizuku", e2)
            }
        }
    }

    private fun compute(): ShizukuStatus {
        val alive = try {
            Shizuku.pingBinder()
        } catch (t: Throwable) {
            false
        }
        if (!alive) {
            return ShizukuStatusRules.decide(isInstalled(), false, false, false, false)
        }
        return try {
            if (Shizuku.isPreV11()) {
                ShizukuStatusRules.decide(true, true, true, false, false)
            } else {
                val granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                // "true" depois de negado = o usuário marcou "negar e não perguntar de novo".
                val denyForever = !granted && Shizuku.shouldShowRequestPermissionRationale()
                ShizukuStatusRules.decide(true, true, false, granted, denyForever)
            }
        } catch (t: Throwable) {
            // O Shizuku parou no meio da consulta.
            ShizukuStatusRules.decide(isInstalled(), false, false, false, false)
        }
    }

    private fun isInstalled(): Boolean = try {
        @Suppress("DEPRECATION")
        appContext.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    } catch (t: Throwable) {
        false
    }
}
