package io.github.hyperisland.xposed.templates

import android.app.Notification
import android.os.Build
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Bundle
import de.robv.android.xposed.XposedBridge
import io.github.d4viddf.hyperisland_kit.HyperAction
import io.github.d4viddf.hyperisland_kit.HyperIslandNotification
import io.github.d4viddf.hyperisland_kit.HyperPicture
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoLeft
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoRight
import io.github.d4viddf.hyperisland_kit.models.PicInfo
import io.github.d4viddf.hyperisland_kit.models.TextInfo
import io.github.hyperisland.xposed.IslandDispatcher
import io.github.hyperisland.xposed.IslandRequest
import io.github.hyperisland.xposed.IslandTemplate
import io.github.hyperisland.xposed.NotifData
import io.github.hyperisland.xposed.hook.FocusNotifStatusBarIconHook
import io.github.hyperisland.xposed.toRounded
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * AI 增强版通知超级岛。
 * 在接收到通知时，将通知基本信息（包名、标题、正文）发送给 AI，
 * 由 AI 生成大岛左右文本。若 AI 3 秒内未响应，回退到默认逻辑。
 *
 * AI 响应格式：{"left": "来源", "right": "内容"}
 * 兼容 OpenAI 接口格式（支持 DeepSeek、Claude 等兼容接口）。
 */
object AINotificationIslandNotification : IslandTemplate {

    const val TEMPLATE_ID = "ai_notification_island"

    override val id = TEMPLATE_ID

    // 独立线程池，避免占用系统线程
    private val executor = Executors.newCachedThreadPool()

    override fun inject(context: Context, extras: Bundle, data: NotifData) {
        val aiConfig = loadAiConfig(context)
        val aiText = if (aiConfig.enabled && aiConfig.url.isNotEmpty()) {
            fetchAiText(aiConfig, data)
        } else {
            null
        }

        val leftText  = aiText?.left  ?: data.title
        val rightText = aiText?.right ?: data.subtitle.ifEmpty { data.title }

        if (aiText != null) {
            XposedBridge.log("HyperIsland[AINotifIsland]: AI text — left=$leftText | right=$rightText")
        } else {
            XposedBridge.log("HyperIsland[AINotifIsland]: Using fallback text — left=$leftText | right=$rightText")
        }

        if (data.focusNotif == "off") {
            injectViaDispatcher(context, data, leftText, rightText)
            return
        }

        injectIsland(
            context         = context,
            extras          = extras,
            notifId         = data.notifId,
            title           = data.title,
            subtitle        = data.subtitle,
            leftText        = leftText,
            rightText       = rightText,
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

    // ── AI 配置 ────────────────────────────────────────────────────────────────

    private data class AiConfig(
        val enabled: Boolean,
        val url: String,
        val apiKey: String,
        val model: String,
    )

    private data class AiIslandText(val left: String, val right: String)

    private fun loadAiConfig(context: Context): AiConfig {
        fun readString(key: String): String = try {
            val uri = android.net.Uri.parse("content://io.github.hyperisland.settings/$key")
            context.contentResolver.query(uri, null, null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else "" } ?: ""
        } catch (_: Exception) { "" }

        fun readBool(key: String): Boolean = try {
            val uri = android.net.Uri.parse("content://io.github.hyperisland.settings/$key")
            context.contentResolver.query(uri, null, null, null, null)
                ?.use { if (it.moveToFirst()) it.getInt(0) != 0 else false } ?: false
        } catch (_: Exception) { false }

        return AiConfig(
            enabled = readBool("pref_ai_enabled"),
            url     = readString("pref_ai_url"),
            apiKey  = readString("pref_ai_api_key"),
            model   = readString("pref_ai_model"),
        )
    }

    // ── AI 调用（带超时） ──────────────────────────────────────────────────────

    private fun fetchAiText(config: AiConfig, data: NotifData): AiIslandText? {
        val future: Future<AiIslandText?> = executor.submit<AiIslandText?> {
            callAiApi(config, data)
        }
        return try {
            future.get(3, TimeUnit.SECONDS)
        } catch (_: TimeoutException) {
            future.cancel(true)
            XposedBridge.log("HyperIsland[AINotifIsland]: AI request timed out, falling back")
            null
        } catch (e: Exception) {
            XposedBridge.log("HyperIsland[AINotifIsland]: AI request error: ${e.message}")
            null
        }
    }

    /**
     * 调用兼容 OpenAI 格式的 Chat Completions 接口。
     * 请求中包含通知来源、标题和正文，要求 AI 返回 JSON。
     */
    private fun callAiApi(config: AiConfig, data: NotifData): AiIslandText? {
        val requestBody = buildRequestBody(config.model, data)

        val conn = (URL(config.url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            if (config.apiKey.isNotEmpty()) {
                setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            }
            connectTimeout = 2500
            readTimeout    = 2500
            doOutput       = true
        }

        XposedBridge.log("HyperIsland[AINotifIsland]: POST ${config.url}")

        return try {
            conn.outputStream.use { it.write(requestBody.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                val errorBody = try {
                    conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                } catch (_: Exception) { "" }
                XposedBridge.log("HyperIsland[AINotifIsland]: HTTP $code — $errorBody")
                return null
            }

            val responseText = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            parseAiResponse(responseText)
        } finally {
            conn.disconnect()
        }
    }

    private fun buildRequestBody(model: String, data: NotifData): String {
        val systemPrompt = """
你是一个 Android 通知摘要助手。
根据通知信息，提取关键信息，仅返回如下 JSON，不得包含任何其他文字或代码块：
{"left":"通知来源（优先提取谁发的而不是应用名称，6字以内）","right":"核心内容（不超过6汉字或者12数字字母）"}
""".trimIndent()

        val userContent = buildString {
            append("应用包名：${data.pkg}\n")
            append("标题：${data.title}\n")
            if (data.subtitle.isNotEmpty()) append("正文：${data.subtitle}")
        }

        val messages = org.json.JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemPrompt))
            put(JSONObject().put("role", "user").put("content", userContent))
        }

        return JSONObject()
            .put("model", model.ifEmpty { "gpt-4o-mini" })
            .put("messages", messages)
            .put("max_tokens", 80)
            .put("temperature", 0.1)
            .toString()
    }

    private fun parseAiResponse(responseText: String): AiIslandText? {
        return try {
            // 从 choices[0].message.content 提取文本
            val root    = JSONObject(responseText)
            val content = root.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()

            // 兼容 AI 可能输出 markdown 代码块包裹的情况
            val jsonStr = content
                .removePrefix("```json").removePrefix("```").removeSuffix("```")
                .trim()

            val result = JSONObject(jsonStr)
            val left  = result.optString("left", "").trim()
            val right = result.optString("right", "").trim()

            if (left.isEmpty() && right.isEmpty()) null
            else AiIslandText(
                left  = left.ifEmpty  { "通知" },
                right = right.ifEmpty { "新消息" },
            )
        } catch (e: Exception) {
            XposedBridge.log("HyperIsland[AINotifIsland]: Failed to parse AI response: ${e.message}")
            null
        }
    }

    // ── 超级岛注入（与 NotificationIslandNotification 逻辑一致） ─────────────

    private fun injectViaDispatcher(
        context: Context,
        data: NotifData,
        leftText: String,
        rightText: String,
    ) {
        try {
            // 优化：不再传递图标，由 SystemUI 自动获取
            IslandDispatcher.post(
                context,
                IslandRequest(
                    title            = leftText,
                    content          = rightText,
                    timeoutSecs      = data.islandTimeout,
                    firstFloat       = data.firstFloat == "on",
                    enableFloat      = data.enableFloatMode == "on",
                    showNotification = false,
                    preserveStatusBarSmallIcon = data.preserveStatusBarSmallIcon != "off",
                    contentIntent    = data.contentIntent,
                    isOngoing        = data.isOngoing,
                    actions          = data.actions.take(2),
                ),
            )
        } catch (e: Exception) {
            XposedBridge.log("HyperIsland[AINotifIsland]: Dispatcher error: ${e.message}")
        }
    }

    private fun injectIsland(
        context: Context,
        extras: Bundle,
        notifId: Int,
        title: String,
        subtitle: String,
        leftText: String,
        rightText: String,
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
            // 超级岛区域图标（bigIslandArea / smallIslandArea）
            val displayIcon = when (iconMode) {
                "notif_small" -> notifIcon ?: fallbackIcon
                "notif_large" -> largeIcon ?: notifIcon ?: fallbackIcon
                "app_icon"    -> appIconRaw ?: fallbackIcon
                else          -> largeIcon ?: notifIcon ?: fallbackIcon  // auto
            }.toRounded(context)
            // 焦点图标（iconTextInfo）
            val focusDisplayIcon = when (focusIconMode) {
                "notif_small" -> notifIcon ?: appIconRaw ?: fallbackIcon
                "notif_large" -> largeIcon ?: appIconRaw ?: notifIcon ?: fallbackIcon
                "app_icon"    -> appIconRaw ?: fallbackIcon
                else          -> largeIcon ?: appIconRaw ?: notifIcon ?: fallbackIcon  // auto
            }.toRounded(context)

            val resolvedFirstFloat  = firstFloat      == "on"
            val resolvedEnableFloat = enableFloatMode == "on"
            val showNotification    = focusNotif != "off"
            val shouldPreserveStatusBarSmallIcon =
                showNotification && preserveStatusBarSmallIcon != "off"

            val builder = HyperIslandNotification.Builder(context, "ai_notif_island", title)

            builder.addPicture(HyperPicture("key_ai_island_icon", displayIcon))
            builder.addPicture(HyperPicture("key_ai_focus_icon", focusDisplayIcon))

            builder.setIconTextInfo(
                picKey  = "key_ai_focus_icon",
                title   = leftText,
                content = rightText,
            )

            builder.setIslandFirstFloat(resolvedFirstFloat)
            builder.setEnableFloat(resolvedEnableFloat)
            builder.setShowNotification(showNotification)
            builder.setIslandConfig(timeout = timeoutSecs)

            builder.setSmallIsland("key_ai_island_icon")

            builder.setBigIslandInfo(
                left = ImageTextInfoLeft(
                    type     = 1,
                    picInfo  = PicInfo(type = 1, pic = "key_ai_island_icon"),
                    textInfo = TextInfo(title = leftText),
                ),
                right = ImageTextInfoRight(
                    type     = 2,
                    textInfo = TextInfo(title = rightText, narrowFont = true),
                ),
            )

            val effectiveActions = actions.take(2)
            if (effectiveActions.isNotEmpty()) {
                val hyperActions = effectiveActions.mapIndexed { index, action ->
                    HyperAction(
                        key              = "action_ai_island_$index",
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
            XposedBridge.log("HyperIsland[AINotifIsland]: Island injection error: ${e.message}")
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

    private fun resolveIcon(data: NotifData, mode: String?, fallback: Icon): Icon {
        return when (mode) {
            "notif_small" -> data.notifIcon ?: fallback
            "notif_large" -> data.largeIcon ?: data.notifIcon ?: fallback
            "app_icon"    -> data.appIconRaw ?: fallback
            else          -> data.largeIcon ?: data.notifIcon ?: fallback
        }
    }
}
