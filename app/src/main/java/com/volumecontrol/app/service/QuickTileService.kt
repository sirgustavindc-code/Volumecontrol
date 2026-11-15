package com.volumecontrol.app.service

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.volumecontrol.app.R
import com.volumecontrol.app.data.Settings
import com.volumecontrol.app.engine.ShizukuMonitor
import com.volumecontrol.app.engine.ShizukuStatus
import com.volumecontrol.app.ui.QuickPanelActivity

/** Botão nos Ajustes rápidos: abre o painel com os apps que estão tocando agora. */
class QuickTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        ShizukuMonitor.refresh()
        val tile = qsTile ?: return
        val ready = Settings.enabled.value && ShizukuMonitor.status.value == ShizukuStatus.READY
        tile.state = if (ready) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        tile.subtitle = if (ready) null else getString(R.string.tile_not_ready)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_notification)
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, QuickPanelActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        startActivityAndCollapse(pi) // API 34+
    }
}
