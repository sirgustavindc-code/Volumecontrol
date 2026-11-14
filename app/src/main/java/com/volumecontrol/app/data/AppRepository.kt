package com.volumecontrol.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap,
)

object AppRepository {
    private const val ICON_SIZE = 96

    /** Lista os apps que têm ícone no launcher (os que o usuário reconhece), em ordem alfabética. */
    fun load(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        val seen = HashSet<String>()
        return resolved.mapNotNull { ri ->
            val pkg = ri.activityInfo.packageName
            if (pkg == context.packageName || !seen.add(pkg)) return@mapNotNull null
            val label = ri.loadLabel(pm).toString()
            val icon = ri.loadIcon(pm).toBitmap(ICON_SIZE, ICON_SIZE).asImageBitmap()
            AppEntry(pkg, label, icon)
        }.sortedBy { it.label.lowercase() }
    }
}
