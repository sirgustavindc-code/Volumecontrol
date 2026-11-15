package com.volumecontrol.app

import android.app.Application
import android.util.Log
import com.volumecontrol.app.data.Settings
import com.volumecontrol.app.engine.ShizukuMonitor
import com.volumecontrol.app.service.OverlayState
import org.lsposed.hiddenapibypass.HiddenApiBypass

class VolumeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Settings.init(this)
        // O motor de áudio usa por reflexão classes internas do Android (ver AudioEngine).
        // A liberação é restrita a estes dois grupos, e só vale para o processo deste app.
        try {
            HiddenApiBypass.addHiddenApiExemptions("Landroid/media/", "Landroid/os/ServiceManager;")
        } catch (t: Throwable) {
            Log.w("VolumeApp", "Não foi possível liberar as APIs internas de áudio", t)
        }
        ShizukuMonitor.init(this)
        OverlayState.refresh(this)
    }
}
