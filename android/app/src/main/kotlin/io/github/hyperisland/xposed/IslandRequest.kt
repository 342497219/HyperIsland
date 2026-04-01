package io.github.hyperisland.xposed

import android.app.Notification
import android.content.Intent
import android.os.Build
import android.os.Bundle

/**
 * 超级岛展示请求，跨进程通过 Intent extras 传递。
 * 
 * 优化版：移除了直接携带 Icon 对象，改由 SystemUI 进程根据包名自动获取，
 * 彻底解决 FAILED BINDER TRANSACTION (1MB 限制) 问题。
 */
data class IslandRequest(
    val title: String,
    val content: String,
    val notifId: Int = IslandDispatcher.NOTIF_ID,
    val timeoutSecs: Int = 5,
    val firstFloat: Boolean = true,
    val enableFloat: Boolean = true,
    val showNotification: Boolean = true,
    val preserveStatusBarSmallIcon: Boolean = true,
    val highlightColor: String? = null,
    val dismissIsland: Boolean = false,
    val contentIntent: android.app.PendingIntent? = null,
    val isOngoing: Boolean = false,
    val actions: List<Notification.Action> = emptyList(),
) {
    fun toBundle(): Bundle = Bundle().apply {
        putString(KEY_TITLE,          title)
        putString(KEY_CONTENT,        content)
        putInt(KEY_NOTIF_ID,          notifId)
        putInt(KEY_TIMEOUT,           timeoutSecs)
        putBoolean(KEY_FIRST_FLOAT,   firstFloat)
        putBoolean(KEY_ENABLE_FLOAT,  enableFloat)
        putBoolean(KEY_SHOW_NOTIF,    showNotification)
        putBoolean(KEY_PRESERVE_SMALL_ICON, preserveStatusBarSmallIcon)
        putString(KEY_HIGHLIGHT,      highlightColor)
        putBoolean(KEY_DISMISS,       dismissIsland)
        putParcelable(KEY_CONTENT_INTENT, contentIntent)
        putBoolean(KEY_ONGOING, isOngoing)
        if (actions.isNotEmpty()) putParcelableArray(KEY_ACTIONS, actions.toTypedArray())
    }

    companion object {
        private const val KEY_TITLE          = "title"
        private const val KEY_CONTENT        = "content"
        private const val KEY_NOTIF_ID       = "notifId"
        private const val KEY_TIMEOUT        = "timeoutSecs"
        private const val KEY_FIRST_FLOAT    = "firstFloat"
        private const val KEY_ENABLE_FLOAT   = "enableFloat"
        private const val KEY_SHOW_NOTIF     = "showNotification"
        private const val KEY_PRESERVE_SMALL_ICON = "preserveStatusBarSmallIcon"
        private const val KEY_HIGHLIGHT      = "highlightColor"
        private const val KEY_DISMISS        = "dismissIsland"
        private const val KEY_CONTENT_INTENT = "contentIntent"
        private const val KEY_ONGOING        = "isOngoing"
        private const val KEY_ACTIONS        = "actions"

        fun fromBundle(b: Bundle) = IslandRequest(
            title            = b.getString(KEY_TITLE, ""),
            content          = b.getString(KEY_CONTENT, ""),
            notifId          = b.getInt(KEY_NOTIF_ID, IslandDispatcher.NOTIF_ID),
            timeoutSecs      = b.getInt(KEY_TIMEOUT, 5),
            firstFloat       = b.getBoolean(KEY_FIRST_FLOAT, true),
            enableFloat      = b.getBoolean(KEY_ENABLE_FLOAT, true),
            showNotification = b.getBoolean(KEY_SHOW_NOTIF, true),
            preserveStatusBarSmallIcon = b.getBoolean(KEY_PRESERVE_SMALL_ICON, true),
            highlightColor   = b.getString(KEY_HIGHLIGHT),
            dismissIsland    = b.getBoolean(KEY_DISMISS, false),
            contentIntent    = pendingIntentFromBundle(b),
            isOngoing        = b.getBoolean(KEY_ONGOING, false),
            actions          = actionsFromBundle(b),
        )

        private fun actionsFromBundle(b: Bundle): List<Notification.Action> = try {
            if (Build.VERSION.SDK_INT >= 33)
                b.getParcelableArray(KEY_ACTIONS, Notification.Action::class.java)?.toList() ?: emptyList()
            else
                @Suppress("DEPRECATION")
                (b.getParcelableArray(KEY_ACTIONS) as? Array<*>)
                    ?.filterIsInstance<Notification.Action>() ?: emptyList()
        } catch (_: Exception) { emptyList() }

        private fun pendingIntentFromBundle(b: Bundle): android.app.PendingIntent? =
            if (Build.VERSION.SDK_INT >= 33)
                b.getParcelable(KEY_CONTENT_INTENT, android.app.PendingIntent::class.java)
            else
                @Suppress("DEPRECATION") b.getParcelable(KEY_CONTENT_INTENT)

        fun fromIntent(intent: Intent) = fromBundle(intent.extras ?: Bundle())
    }
}
