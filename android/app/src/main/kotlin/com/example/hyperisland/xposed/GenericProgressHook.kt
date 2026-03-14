package com.example.hyperisland.xposed

import android.app.Notification
import android.os.Bundle
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 通用进度条通知 Hook — 拦截任意 App 含进度条的通知，注入澎湃超级岛样式。
 * 与 DownloadHook 互不干扰：
 *   - 跳过 DownloadHook 已覆盖的下载管理器包
 *   - 跳过带 hyperisland_processed 标记的通知（DownloadHook 已处理）
 *   - 跳过不确定进度条（indeterminate）或无有效进度的通知
 */
class GenericProgressHook : IXposedHookLoadPackage {

    companion object {
        /** DownloadHook 已处理的包，此处跳过避免重复注入 */
        private val SKIP_PACKAGES = setOf(
            "com.example.hyperisland",
            "com.android.providers.downloads",
            "com.xiaomi.android.app.downloadmanager"
        )
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName in SKIP_PACKAGES) return

        try {
            val nmClass = lpparam.classLoader.loadClass("android.app.NotificationManager")
            hookNotify(nmClass, lpparam, hasTag = true)
            hookNotify(nmClass, lpparam, hasTag = false)
        } catch (e: Throwable) {
            // 大量包不含 NotificationManager，静默跳过
        }
    }

    private fun hookNotify(
        nmClass: Class<*>,
        lpparam: XC_LoadPackage.LoadPackageParam,
        hasTag: Boolean
    ) {
        try {
            val paramTypes = if (hasTag)
                arrayOf(String::class.java, Int::class.javaPrimitiveType, Notification::class.java)
            else
                arrayOf(Int::class.javaPrimitiveType, Notification::class.java)

            XposedHelpers.findAndHookMethod(nmClass, "notify", *paramTypes, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val notif = (if (hasTag) param.args[2] else param.args[1]) as? Notification ?: return
                    handleNotification(notif, lpparam)
                }
            })
        } catch (_: Throwable) {}
    }

    private fun handleNotification(notif: Notification, lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val extras = DownloadHook.extrasField?.get(notif) as? Bundle ?: return

            // 跳过已被 DownloadHook 或本 Hook 处理过的通知
            if (extras.getBoolean("hyperisland_processed", false)) return
            if (extras.getBoolean("hyperisland_generic_processed", false)) return

            // ── 进度条检测 ────────────────────────────────────────────────────────
            val progressMax = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
            val indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
            // 必须有确定的进度条（max > 0 且非不确定）
            if (progressMax <= 0 || indeterminate) return

            val progressRaw = extras.getInt(Notification.EXTRA_PROGRESS, -1)
            if (progressRaw < 0) return

            val progressPercent = (progressRaw * 100 / progressMax).coerceIn(0, 100)

            // ── 提取标题 / 副标题 ─────────────────────────────────────────────────
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()
                ?: return   // 无标题则不处理

            // 副标题优先级：subText > text > infoText > bigText
            val subtitle = listOf(
                extras.getCharSequence(Notification.EXTRA_SUB_TEXT),
                extras.getCharSequence(Notification.EXTRA_TEXT),
                extras.getCharSequence(Notification.EXTRA_INFO_TEXT),
                extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ).firstNotNullOfOrNull { it?.toString()?.takeIf { s -> s.isNotEmpty() } } ?: ""

            // ── 提取原通知按钮（最多 2 个）────────────────────────────────────────
            val actions: List<Notification.Action> = notif.actions?.take(2) ?: emptyList()

            XposedBridge.log(
                "HyperIsland[Generic]: ${lpparam.packageName} | " +
                "$title | $progressPercent% | buttons=${actions.size}"
            )

            val context = getContext(lpparam) ?: return

            val notifIcon = if (InProcessController.useHookAppIconEnabled)
                InProcessController.getAppIcon(context, lpparam.packageName) ?: notif.smallIcon
            else
                notif.smallIcon

            GenericProgressIslandNotification.inject(
                context     = context,
                extras      = extras,
                title       = title,
                subtitle    = subtitle,
                progress    = progressPercent,
                actions     = actions,
                notifIcon   = notifIcon
            )

            extras.putBoolean("hyperisland_generic_processed", true)

        } catch (e: Throwable) {
            XposedBridge.log("HyperIsland[Generic]: handleNotification error: ${e.message}")
        }
    }

    private fun getContext(lpparam: XC_LoadPackage.LoadPackageParam): android.content.Context? {
        return try {
            val at = lpparam.classLoader.loadClass("android.app.ActivityThread")
            at.getMethod("currentApplication").invoke(null) as? android.content.Context
        } catch (_: Exception) {
            try {
                val at = lpparam.classLoader.loadClass("android.app.ActivityThread")
                (at.getMethod("getSystemContext").invoke(null) as? android.content.Context)?.applicationContext
            } catch (_: Exception) { null }
        }
    }
}
