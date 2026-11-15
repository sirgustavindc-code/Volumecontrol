package com.volumecontrol.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.volumecontrol.app.data.Settings
import com.volumecontrol.app.engine.ShizukuMonitor
import com.volumecontrol.app.service.OverlayState
import com.volumecontrol.app.ui.MainScreen
import com.volumecontrol.app.ui.VolumeControlTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val dark by Settings.darkTheme.collectAsState()
            DisposableEffect(dark) {
                val transparent = android.graphics.Color.TRANSPARENT
                val style = if (dark) {
                    SystemBarStyle.dark(transparent)
                } else {
                    SystemBarStyle.light(transparent, transparent)
                }
                this@MainActivity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            VolumeControlTheme(dark = dark) {
                MainScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // O usuário pode ter ligado o Shizuku ou concedido permissões enquanto estava fora.
        ShizukuMonitor.refresh()
        OverlayState.refresh(this)
    }
}
