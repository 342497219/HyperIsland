package com.example.hyperisland

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import org.json.JSONObject

/**
 * 小米灵动岛辅助类
 * 用于发送各种类型的超级岛通知
 */
object HyperIslandHelper {
    private const val TAG = "HyperIslandHelper"
    private const val CHANNEL_ID = "hyperisland_channel"
    private const val NOTIFICATION_ID = 1001

    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "HyperIsland",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "小米超级岛测试通知"
                setShowBadge(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 构建岛通知参数JSON
     */
    private fun buildIslandParams(
        title: String,
        content: String,
        progress: Int = -1,
        isIndeterminate: Boolean = false
    ): String {
        val paramV2 = JSONObject().apply {
            put("protocol", 1)
            put("business", "download")
            put("enableFloat", true)
            put("updatable", progress >= 0 && progress < 100)

            // 状态栏数据
            put("ticker", title)
            put("tickerPic", "miui.focus.pic_ticker")

            // 息屏AOD数据
            put("aodTitle", if (progress > 0) "下载中 $progress%" else title)
            put("aodPic", "miui.focus.pic_ticker")

            // 岛数据
            val paramIsland = JSONObject().apply {
                put("islandProperty", 1)

                // 大岛内容
                val bigIslandArea = JSONObject().apply {
                    // 大岛A区 - 图文信息
                    val imageTextInfoLeft = JSONObject().apply {
                        put("type", 1)

                        val picInfo = JSONObject().apply {
                            put("type", 1)
                            put("pic", "miui.focus.pic_ticker")
                        }
                        put("picInfo", picInfo)

                        val textInfo = JSONObject().apply {
                            put("frontTitle", if (isIndeterminate) "准备中" else if (progress >= 100) "完成" else "下载中")
                            put("title", if (progress >= 0) "$progress%" else content)
                            put("content", "仅供测试")
                            put("useHighLight", progress >= 100)
                        }
                        put("textInfo", textInfo)
                    }
                    put("imageTextInfoLeft", imageTextInfoLeft)
                }
                put("bigIslandArea", bigIslandArea)

                // 小岛内容
                val smallIslandArea = JSONObject().apply {
                    val picInfo = JSONObject().apply {
                        put("type", 1)
                        put("pic", "miui.focus.pic_ticker")
                    }
                    put("picInfo", picInfo)

                    // 小岛文字
                    val textInfo = JSONObject().apply {
                        put("title", if (progress >= 0) "$progress%" else "下载")
                    }
                    put("textInfo", textInfo)
                }
                put("smallIslandArea", smallIslandArea)
            }
            put("param_island", paramIsland)

            // 焦点通知数据
            val baseInfo = JSONObject().apply {
                put("title", "仅供测试")
                put("content", content)
                put("colorTitle", "#006EFF")
                put("type", 2)
            }
            put("baseInfo", baseInfo)

            // 提示信息
            val hintInfo = JSONObject().apply {
                put("type", 1)
                put("title", if (progress >= 100) "下载完成" else if (isIndeterminate) "准备中" else "下载中")
            }
            put("hintInfo", hintInfo)
        }

        val root = JSONObject().apply {
            put("param_v2", paramV2)
        }

        return root.toString()
    }

    /**
     * 发送岛通知
     */
    private fun sendIslandNotification(
        context: Context,
        title: String,
        content: String,
        progress: Int = -1,
        isIndeterminate: Boolean = false,
        isError: Boolean = false
    ): Boolean {
        return try {
            createNotificationChannel(context)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 创建点击Intent
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 构建岛通知参数
            val islandParams = buildIslandParams(title, content, progress, isIndeterminate)

            // 创建通知Builder
            val builder = NotificationCompat.Builder(context, CHANNEL_ID).apply {
                setSmallIcon(
                    when {
                        progress >= 100 -> android.R.drawable.stat_sys_download_done
                        isError -> android.R.drawable.stat_notify_error
                        else -> android.R.drawable.stat_sys_download
                    }
                )
                setContentTitle(title)
                setContentText(content)
                setOngoing(progress in 0..99)
                setAutoCancel(progress >= 100 || isError)
                setContentIntent(pendingIntent)

                // 设置进度
                when {
                    isIndeterminate -> setProgress(0, 0, true)
                    progress >= 0 -> setProgress(100, progress, false)
                    else -> setProgress(0, 0, false)
                }
            }

            // 创建Bundle添加图片资源
            val bundle = android.os.Bundle().apply {
                val pics = android.os.Bundle().apply {
                    // 添加图标资源
                    putParcelable(
                        "miui.focus.pic_ticker",
                        Icon.createWithResource(
                            context,
                            when {
                                progress >= 100 -> android.R.drawable.stat_sys_download_done
                                isError -> android.R.drawable.stat_notify_error
                                else -> android.R.drawable.stat_sys_download
                            }
                        )
                    )
                }
                putBundle("miui.focus.pics", pics)
            }

            builder.addExtras(bundle)

            // 构建通知
            val notification = builder.build()

            // 添加岛通知参数到extras
            notification.extras.putString("miui.focus.param", islandParams)

            // 发送通知
            notificationManager.notify(NOTIFICATION_ID, notification)

            Log.d(TAG, "Island notification sent: $title - $progress%")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error sending island notification", e)
            e.printStackTrace()
            false
        }
    }

    /**
     * 显示下载进度
     */
    fun showDownloadProgress(
        context: Context,
        title: String,
        fileName: String,
        progress: Int,
        speed: String = "",
        remainingTime: String = ""
    ): Boolean {
        val content = buildString {
            append(fileName)
            if (speed.isNotEmpty()) append("\n速度: $speed")
            if (remainingTime.isNotEmpty()) append("\n剩余: $remainingTime")
        }
        return sendIslandNotification(context, title, content, progress, false)
    }

    /**
     * 显示下载完成
     */
    fun showDownloadComplete(
        context: Context,
        title: String,
        fileName: String
    ): Boolean {
        return sendIslandNotification(context, title, fileName, 100, false)
    }

    /**
     * 显示下载失败
     */
    fun showDownloadFailed(
        context: Context,
        title: String,
        fileName: String,
        error: String
    ): Boolean {
        val content = if (error.isNotEmpty()) "$fileName: $error" else fileName
        return sendIslandNotification(context, title, content, -1, false, true)
    }

    /**
     * 显示不确定进度
     */
    fun showIndeterminateProgress(
        context: Context,
        title: String,
        content: String
    ): Boolean {
        return sendIslandNotification(context, title, content, -1, true)
    }

    /**
     * 显示自定义通知
     */
    fun showCustomFocus(
        context: Context,
        type: String,
        title: String,
        content: String,
        icon: String?
    ): Boolean {
        return sendIslandNotification(context, title, content, -1, false)
    }

    /**
     * 移除当前通知
     */
    fun removeCurrentFocus(context: Context): Boolean {
        return try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_ID)
            Log.d(TAG, "Notification removed")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error removing notification", e)
            false
        }
    }

    /**
     * 检查是否支持超级岛
     */
    fun isSupportIsland(context: Context): Boolean {
        return try {
            // 检查系统属性
            val systemProperties = Class.forName("android.os.SystemProperties")
            val method = systemProperties.getDeclaredMethod("getBoolean", String::class.java, Boolean::class.javaPrimitiveType)
            val result = method.invoke(null, "persist.sys.feature.island", false)
            result as? Boolean ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking island support", e)
            false
        }
    }

    /**
     * 获取焦点通知协议版本
     */
    fun getFocusProtocolVersion(context: Context): Int {
        return try {
            android.provider.Settings.System.getInt(
                context.contentResolver,
                "notification_focus_protocol",
                0
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting focus protocol version", e)
            0
        }
    }
}
