package io.github.hyperisland.xposed.templates

import android.app.Notification
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import io.github.hyperisland.xposed.IslandDispatcher
import io.github.hyperisland.xposed.IslandRequest
import io.github.hyperisland.xposed.IslandTemplate
import io.github.hyperisland.xposed.hook.FocusNotifStatusBarIconHook
import io.github.hyperisland.xposed.NotifData
import io.github.hyperisland.xposed.moduleContext
import io.github.hyperisland.xposed.toRounded
import de.robv.android.xposed.XposedBridge
import io.github.d4viddf.hyperisland_kit.HyperAction
import io.github.d4viddf.hyperisland_kit.HyperIslandNotification
import io.github.d4viddf.hyperisland_kit.HyperPicture
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoLeft
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoRight
import io.github.d4viddf.hyperisland_kit.models.PicInfo
import io.github.d4viddf.hyperisland_kit.models.TextInfo

/**
 * 通知超级岛|精简 通知构建器。
 * 基于 [NotificationIslandNotification]，额外对聊天类通知做文本清理：
 */
object NotificationIslandLiteNotification : IslandTemplate {

    const val TEMPLATE_ID = "notification_island_lite"

    override val id = TEMPLATE_ID

    override fun inject(context: Context, extras: Bundle, data: NotifData) {
        val cleanedTitle    = cleanTitle(data.title)
        val cleanedSubtitle = cleanSubtitle(data.subtitle, cleanedTitle)

        if (data.focusNotif == "off") {
            injectViaDispatcher(context, data, cleanedTitle, cleanedSubtitle)
            return
        }
        inject(
            context         = context,
            extras          = extras,
            notifId         = data.notifId,
            title           = cleanedTitle,
            subtitle        = cleanedSubtitle,
            rawTitle        = data.title,
            actions         = data.actions,
            notifIcon       = data.notifIcon,
            largeIcon       = data.largeIcon,
            appIconRaw      = data.appIconRaw,
            iconMode        = data.iconMode,
            focusIconMode   = data.focusIconMode,
            focusNotif      = data.focusNotif,
            preserveStatusBarSmallIcon = data.preserveStatusBarSmallIcon,
            firstFloat      = data.firstFloat,
            enableFloatMode = data.enableFloatMode,
            timeoutSecs     = data.islandTimeout,
            isOngoing       = data.isOngoing,
        )
    }

    private val TITLE_COUNT_BRACKET = Regex("""[\[【(（][^\]】)）]*\d+[^\]】)）]*[\]】)）]\s*|\s*[\[【(（][^\]】)）]*\d+[^\]】)）]*[\]】)）]""")

    private fun cleanTitle(title: String): String =
        title.replace(TITLE_COUNT_BRACKET, "").trim()

    private val SUBTITLE_COUNT_PREFIX = Regex("""^[\[【(（][^\]】)）]*\d+[^\]】)）]*[\]】)）]\s*""")

    private fun cleanSubtitle(subtitle: String, cleanedTitle: String): String {
        var s = subtitle.replace(SUBTITLE_COUNT_PREFIX, "")
        if (cleanedTitle.isNotEmpty() && cleanedTitle.length <= 30) {
            val senderPrefix = Regex("""^${Regex.escape(cleanedTitle)}\s*[:：]\s*""")
            s = s.replace(senderPrefix, "")
        }
        return s.trim()
    }

    private fun injectViaDispatcher(
        context: Context,
        data: NotifData,
        cleanedTitle: String,
        cleanedSubtitle: String,
    ) {
        try {
            val resolvedFirstFloat  = data.firstFloat      == "on"
            val resolvedEnableFloat = data.enableFloatMode == "on"

            // 优化：不再传递图标，由 SystemUI 自动获取
            IslandDispatcher.post(
                context,
                IslandRequest(
                    title            = cleanedTitle,
                    content          = cleanedSubtitle.ifEmpty { cleanedTitle },
                    timeoutSecs      = data.islandTimeout,
                    firstFloat       = resolvedFirstFloat,
                    enableFloat      = resolvedEnableFloat,
                    showNotification = false,
                    preserveStatusBarSmallIcon = data.preserveStatusBarSmallIcon != "off",
                    contentIntent    = data.contentIntent,
                    isOngoing        = data.isOngoing,
                    actions          = data.actions.take(2),
                ),
            )
            XposedBridge.log(
                "HyperIsland[NotifIslandLite]: Dispatcher island — $cleanedTitle | iconMode=${data.iconMode}"
            )
        } catch (e: Exception) {
            XposedBridge.log("HyperIsland[NotifIslandLite]: Dispatcher island error: ${e.message}")
        }
    }

    private fun inject(
        context: Context,
        extras: Bundle,
        notifId: Int,
        title: String,
        subtitle: String,
        rawTitle: String,
        actions: List<Notification.Action>,
        notifIcon: Icon?,
        largeIcon: Icon?,
        appIconRaw: Icon?,
        iconMode: String?,
        focusIconMode: String?,
        focusNotif: String,
        preserveStatusBarSmallIcon: String,
        firstFloat: String,
        enableFloatMode: String,
        timeoutSecs: Int,
        isOngoing: Boolean,
    ) {
        try {
            val fallbackIcon = Icon.createWithResource(context, android.R.drawable.ic_dialog_info)
            val displayIcon = when (iconMode) {
                "notif_small" -> notifIcon ?: fallbackIcon
                "notif_large" -> largeIcon ?: notifIcon ?: fallbackIcon
                "app_icon"    -> appIconRaw ?: fallbackIcon
                else          -> largeIcon ?: notifIcon ?: fallbackIcon
            }.toRounded(context)
            val focusDisplayIcon = when (focusIconMode) {
                "notif_small" -> notifIcon ?: appIconRaw ?: fallbackIcon
                "notif_large" -> largeIcon ?: appIconRaw ?: notifIcon ?: fallbackIcon
                "app_icon"    -> appIconRaw ?: fallbackIcon
                else          -> largeIcon ?: appIconRaw ?: notifIcon ?: fallbackIcon
            }.toRounded(context)

            val leftText     = title
            val rightContent = subtitle.ifEmpty { title }

            val resolvedFirstFloat  = firstFloat      == "on"
            val resolvedEnableFloat = enableFloatMode == "on"
            val showNotification    = focusNotif != "off"
            val shouldPreserveStatusBarSmallIcon =
                showNotification && preserveStatusBarSmallIcon != "off"

            val builder = HyperIslandNotification.Builder(context, TEMPLATE_ID, title)

            builder.addPicture(HyperPicture("key_notif_lite_icon", displayIcon))
            builder.addPicture(HyperPicture("key_notif_lite_focus_icon", focusDisplayIcon))

            builder.setIconTextInfo(
                picKey  = "key_notif_lite_focus_icon",
                title   = title,
                content = subtitle.ifEmpty { title },
            )

            builder.setIslandFirstFloat(resolvedFirstFloat)
            builder.setEnableFloat(resolvedEnableFloat)
            builder.setShowNotification(showNotification)
            builder.setIslandConfig(timeout = timeoutSecs)

            builder.setSmallIsland("key_notif_lite_icon")

            builder.setBigIslandInfo(
                left = ImageTextInfoLeft(
                    type     = 1,
                    picInfo  = PicInfo(type = 1, pic = "key_notif_lite_icon"),
                    textInfo = TextInfo(title = leftText),
                ),
                right = ImageTextInfoRight(
                    type     = 2,
                    textInfo = TextInfo(title = rightContent, narrowFont = true),
                ),
            )

            val effectiveActions = actions.take(2)
            if (effectiveActions.isNotEmpty()) {
                val hyperActions = effectiveActions.mapIndexed { index, action ->
                    HyperAction(
                        key              = "action_notif_lite_$index",
                        title            = action.title ?: "",
                        pendingIntent    = action.actionIntent,
                        actionIntentType = 2,
                    )
                }
                hyperActions.forEach { builder.addHiddenAction(it) }
                builder.setTextButtons(*hyperActions.toTypedArray())
            }

            val resourceBundle = builder.buildResourceBundle()
            extras.putAll(resourceBundle)
            flattenActionsToExtras(resourceBundle, extras)
            val jsonParam = fixTextButtonJson(builder.buildJsonParam())
                .let { if (!isOngoing) injectUpdatable(it, false) else it }
            extras.putString("miui.focus.param", jsonParam)
            if (showNotification) {
                extras.putBoolean("hyperisland_focus_proxy", true)
            }
            if (shouldPreserveStatusBarSmallIcon) {
                extras.putBoolean("hyperisland_preserve_status_bar_small_icon", true)
                FocusNotifStatusBarIconHook.markDirectProxyPosted(timeoutSecs)
            }
        } catch (e: Exception) {
            XposedBridge.log("HyperIsland[NotifIslandLite]: Island injection error: ${e.message}")
        }
    }

    private fun injectUpdatable(jsonParam: String, updatable: Boolean): String {
        return try {
            val json = org.json.JSONObject(jsonParam)
            val pv2  = json.optJSONObject("param_v2") ?: return jsonParam
            pv2.put("updatable", updatable)
            json.toString()
        } catch (_: Exception) { jsonParam }
    }

    private fun fixTextButtonJson(jsonParam: String): String {
        return try {
            val json = org.json.JSONObject(jsonParam)
            val pv2  = json.optJSONObject("param_v2") ?: return jsonParam
            val btns = pv2.optJSONArray("textButton")
            if (btns != null) {
                for (i in 0 until btns.length()) {
                    val btn = btns.getJSONObject(i)
                    val key = btn.optString("actionIntent").takeIf { it.isNotEmpty() } ?: continue
                    btn.put("action", key)
                    btn.remove("actionIntent")
                    btn.remove("actionIntentType")
                }
            }
            json.toString()
        } catch (_: Exception) { jsonParam }
    }

    private fun flattenActionsToExtras(resourceBundle: Bundle, extras: Bundle) {
        val nested = resourceBundle.getBundle("miui.focus.actions") ?: return
        for (key in nested.keySet()) {
            val action: Notification.Action? = if (Build.VERSION.SDK_INT >= 33) nested.getParcelable(key, Notification.Action::class.java) else @Suppress("DEPRECATION") nested.getParcelable(key)
            if (action != null) extras.putParcelable(key, action)
        }
    }

    private fun isWrapLongTextEnabled(context: Context): Boolean {
        return try {
            val uri = android.net.Uri.parse("content://io.github.hyperisland.settings/pref_wrap_long_text")
            context.contentResolver.query(uri, null, null, null, null)?.use { if (it.moveToFirst()) it.getInt(0) != 0 else false } ?: false
        } catch (_: Exception) { false }
    }
}
