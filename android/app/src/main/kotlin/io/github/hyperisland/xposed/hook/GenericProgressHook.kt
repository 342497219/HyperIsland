package io.github.hyperisland.xposed.hook

import android.app.Notification
import android.service.notification.StatusBarNotification
import io.github.hyperisland.xposed.IslandDispatcher
import io.github.hyperisland.xposed.IslandRequest
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class GenericProgressHook : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "HyperIsland[GenericProgressHook]"
        private val trackedForCancel = mutableMapOf<String, Int>()

        private fun loadBooleanSetting(context: android.content.Context, cacheKey: String, prefKey: String, default: Boolean): Boolean {
            return try {
                val uri = android.net.Uri.parse("content://io.github.hyperisland.settings/$prefKey")
                context.contentResolver.query(uri, null, null, null, null)
                    ?.use { if (it.moveToFirst()) it.getInt(0) != 0 else default } ?: default
            } catch (e: Exception) { default }
        }

        private fun getContext(lpparam: XC_LoadPackage.LoadPackageParam): android.content.Context? {
            return try {
                val activityThreadClass = Class.forName("android.app.ActivityThread")
                val currentActivityThread = activityThreadClass.getMethod("currentActivityThread").invoke(null)
                val getApplication = activityThreadClass.getMethod("getApplication")
                getApplication.invoke(currentActivityThread) as? android.content.Context
            } catch (e: Exception) { null }
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.android.systemui") return

        val cancelCallback = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val sbn = param.args[0] as? StatusBarNotification ?: return
                val key = "${sbn.packageName}#${sbn.id}"
                val proxyId = trackedForCancel.remove(key) ?: return
                val context = getContext(lpparam) ?: return
                
                // 常驻岛逻辑
                val isPersistent = loadBooleanSetting(context, "global:persistent_island", "pref_persistent_island", false)
                if (isPersistent) {
                    // 优化：不再携带图标，由 SystemUI 自动获取
                    val request = IslandRequest(
                        title = "常驻岛",
                        content = "",
                        isOngoing = true,
                        timeoutSecs = -1, // 内部会转为 Int.MAX_VALUE
                        firstFloat = false,
                        enableFloat = false
                    )
                    IslandDispatcher.post(context, request)
                } else {
                    IslandDispatcher.cancel(context, proxyId)
                }
            }
        }

        try {
            // 记录需要取消的通知
            XposedHelpers.findAndHookMethod("com.miui.systemui.notification.MiuiBaseNotifUtil", lpparam.classLoader, "generateInnerNotifBean", StatusBarNotification::class.java, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val sbn = param.args[0] as? StatusBarNotification ?: return
                    if (sbn.notification?.extras?.containsKey("miui.focus.param") == true) {
                        val key = "${sbn.packageName}#${sbn.id}"
                        trackedForCancel[key] = sbn.id
                    }
                }
            })

            // Hook 通知移除事件
            val rankingMapClass = lpparam.classLoader.loadClass("android.service.notification.NotificationListenerService\$RankingMap")
            XposedHelpers.findAndHookMethod("android.service.notification.NotificationListenerService", lpparam.classLoader, "onNotificationRemoved", StatusBarNotification::class.java, rankingMapClass, Int::class.javaPrimitiveType!!, cancelCallback)
        } catch (e: Throwable) {
            try {
                XposedHelpers.findAndHookMethod("android.service.notification.NotificationListenerService", lpparam.classLoader, "onNotificationRemoved", StatusBarNotification::class.java, cancelCallback)
            } catch (e2: Throwable) {}
        }
    }
}
