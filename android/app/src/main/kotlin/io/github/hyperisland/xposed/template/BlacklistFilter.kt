package io.github.hyperisland.xposed

import android.app.ActivityManager
import android.content.Context
import de.robv.android.xposed.XposedBridge

/**
 * 应用黑名单过滤器。
 * 在通知进入模板前检查前台应用是否在黑名单中：
 *  - 命中 → 返回禁用浮动的 [NotifData] 副本（岛正常展示，不自动展开）
 *  - 未命中 → 返回原始 [NotifData]
 */
object BlacklistFilter {

    fun applyTo(context: Context, data: NotifData): NotifData? {
        val cr = context.contentResolver

        val blacklistStr = try {
            val uri = android.net.Uri.parse("content://io.github.hyperisland.settings/pref_app_blacklist")
            cr.query(uri, null, null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else "" } ?: ""
        } catch (_: Exception) { "" }

        if (blacklistStr.isEmpty()) return data

        val foregroundApp = try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            @Suppress("DEPRECATION")
            am.getRunningTasks(1).firstOrNull()?.topActivity?.packageName ?: ""
        } catch (e: Exception) {
            XposedBridge.log("HyperIsland[Blacklist]: getRunningTasks failed: ${e.message}")
            ""
        }

        val isBlacklisted = foregroundApp.isNotEmpty() && blacklistStr.split(",").contains(foregroundApp)
        XposedBridge.log("HyperIsland[Blacklist]: foreground=$foregroundApp, isBlacklisted=$isBlacklisted")

        if (!isBlacklisted) return data

        XposedBridge.log("HyperIsland[Blacklist]: $foregroundApp skipped float")
        return data.copy(firstFloat = "off", enableFloatMode = "off")
    }
}
