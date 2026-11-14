package com.volumecontrol.app

import android.app.Application
import android.util.Log
import com.volumecontrol.app.data.Settings
import com.volumecontrol.app.engine.ShizukuMonitor
import org.lsposed.hiddenapibypass.HiddenApiBypass

class VolumeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // O motor de áudio usa APIs ocultas por reflexão (AudioService, PlayerProxy).
        // Sem esta exceção o Android 9+ bloqueia essas chamadas.
        try {
            HiddenApiBypass.addHiddenApiExemptions("L")
        } catch (t: Throwable) {
            Log.w("VolumeApp", "Não foi possível liberar APIs ocultas", t)
        }
        Settings.init(this)
        ShizukuMonitor.init(this)
    }
}
