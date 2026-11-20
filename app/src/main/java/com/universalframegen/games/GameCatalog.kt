package com.universalframegen.games

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build

/** Launcher-visible activities only; no QUERY_ALL_PACKAGES permission. */
data class GameEntry(
    val label: String,
    val packageName: String,
    val className: String,
    val markedGame: Boolean
)

object GameCatalog {
    fun installed(context: Context): List<GameEntry> {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val entries = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            pm.queryIntentActivities(launcher, 0)
        }
        return entries.mapNotNull { ri ->
            val ai = ri.activityInfo ?: return@mapNotNull null
            if (ai.packageName == context.packageName) return@mapNotNull null
            GameEntry(
                label = ri.loadLabel(pm)?.toString().orEmpty().ifBlank { ai.packageName },
                packageName = ai.packageName,
                className = ai.name,
                markedGame = ai.applicationInfo?.category == ApplicationInfo.CATEGORY_GAME
            )
        }.distinctBy { it.packageName }.sortedWith(
            compareByDescending<GameEntry> { it.markedGame }.thenBy { it.label.lowercase() }
        )
    }

    fun launch(context: Context, entry: GameEntry): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(entry.packageName, entry.className))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}
