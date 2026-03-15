package com.example.hyperisland.xposed

import android.app.Notification
import android.graphics.drawable.Icon
import android.os.Bundle
import android.content.Context
import com.xzakota.hyper.notification.focus.FocusNotification
import de.robv.android.xposed.XposedBridge

/**
 * 通用进度条灵动岛通知构建器
 * 适用于任意含进度条的通知，按钮直接取自原通知（最多 2 个），不硬编码暂停/取消。
 */
object GenericProgressIslandNotification {

    /**
     * 判断文本是否属于"状态噪声"（速度、百分比、下载状态词等），
     * 这类文本不适合作为摘要右侧的内容标题。
     */
    private val NOISE_REGEX = Regex(
        // 文件大小进度：33MB/320MB  |  1.2 GB / 4 GB  |  500KB/1024KB
        """(?i)\d+(\.\d+)?\s*(b|kb|mb|gb|tb|kib|mib|gib)\s*/\s*\d+(\.\d+)?\s*(b|kb|mb|gb|tb|kib|mib|gib)""" +
        // 网速：12.3 MB/s  |  5 KB/s  |  100 Mbps  |  兆/秒 等
        """|(?i)\d+(\.\d+)?\s*(mb/s|kb/s|gb/s|mib/s|kib/s|mbps|kbps|gbps|m/s|兆/秒|兆字节/秒)""" +
        // 百分比：31%  |  100 %
        """|(?i)\d+\s*%""" +
        // 中文下载状态词
        """|下载中|正在下载|准备下载|开始下载|等待下载|排队中|等待中|连接中|获取中|暂停中|已暂停|下载完成|下载失败|下载错误""" +
        // 时间剩余
        """|剩余\s*\d+|还有\s*\d+|剩余时间""" +
        // 英文状态词
        """|(?i)\bdownloading\b|\bdownload\b|\bqueued\b|\bpending\b|\bwaiting\b|\bpaused\b|\bconnecting\b|\bpreparing\b|\bremaining\b"""
    )

    private fun isStatusNoise(text: String): Boolean = NOISE_REGEX.containsMatchIn(text)

    /**
     * 去除标题里常见的下载前缀（如"正在下载 "、"下载中："），
     * 以便在两者都有噪声时尽量提取出有意义的文件名部分。
     */
    private fun stripDownloadPrefix(text: String): String {
        var s = text.trim()
        for (prefix in listOf("正在下载", "下载中", "下载", "Downloading", "Download")) {
            if (s.startsWith(prefix, ignoreCase = true)) {
                s = s.removePrefix(prefix).trimStart(':', '：', ' ', '-')
                break
            }
        }
        return s.trim()
    }

    /**
     * 从 title / subtitle 中挑选适合显示在摘要右侧的内容文本：
     * - 优先选无噪声的一方
     * - 两者都无噪声时优先副标题
     * - 两者都有噪声时对 title 去除下载前缀后返回
     */
    private fun pickContent(title: String, subtitle: String): String {
        val subClean = subtitle.isNotEmpty() && !isStatusNoise(subtitle)
        val titleClean = title.isNotEmpty() && !isStatusNoise(title)
        return when {
            subClean   -> subtitle
            titleClean -> title
            subtitle.isNotEmpty() -> subtitle          // 两者都噪声，副标题可能含速度等，title 更可能是文件名
            else       -> stripDownloadPrefix(title)   // 仅有 title，去前缀后返回
        }
    }

    fun inject(
        context: Context,
        extras: Bundle,
        title: String,
        subtitle: String,
        progress: Int,
        actions: List<Notification.Action>,
        notifIcon: Icon?
    ) {
        try {
            val isComplete = progress >= 100

            // 摘要态左侧：固定显示状态，不显示进度数字
            val stateLabel = if (isComplete) "已完成" else "下载中"

            // 摘要右侧内容：智能从 title/subtitle 中选无噪声的一方
            val rightContent = pickContent(title, subtitle)

            // 展开态直接使用原始标题和副标题，不做过滤也不拼接进度
            val displayContent = subtitle.ifEmpty { title }

            // 图标：优先使用原通知图标，降级到系统下载图标
            val iconRes = if (isComplete) android.R.drawable.stat_sys_download_done
                          else           android.R.drawable.stat_sys_download
            val tintColor = if (isComplete) 0xFF4CAF50.toInt() else 0xFF2196F3.toInt()
            val fallbackIcon = Icon.createWithResource(context, iconRes).apply { setTint(tintColor) }
            val displayIcon = notifIcon ?: fallbackIcon

            val islandExtras = FocusNotification.buildV3 {
                val iconKey = createPicture("key_generic_progress_icon", displayIcon)

                islandFirstFloat = false
                enableFloat = false
                updatable = !isComplete  // 完成时不再等待后续更新，岛展示后自动消退

                // 摘要态
                island {
                    islandProperty = 1
                    bigIslandArea {
                        imageTextInfoLeft {
                            type = 1
                            picInfo {
                                type = 1
                                pic = iconKey
                            }
                            textInfo {
                                this.title = stateLabel
                            }
                        }
                        progressTextInfo {
                            textInfo {
                                this.title = rightContent
                                narrowFont = true
                            }
                            if (!isComplete) {
                                progressInfo {
                                    this.progress = progress
                                }
                            }
                        }
                    }
                    smallIslandArea {
                        picInfo {
                            type = 1
                            pic = iconKey
                        }
                    }
                }

                // 展开态：原样显示，主标题对应 title，副标题对应 subtitle
                iconTextInfo {
                    this.title = title
                    content = displayContent
                    animIconInfo {
                        type = 0
                        src = iconKey
                    }
                }

                // 操作按钮：完成时不显示，最多取原通知前 2 个按钮
                val effectiveActions = actions.take(2)
                if (!isComplete && effectiveActions.isNotEmpty()) {
                    textButton {
                        effectiveActions.forEachIndexed { index, action ->
                            addActionInfo {
                                val btnIcon = action.getIcon()
                                    ?: Icon.createWithResource(context, android.R.drawable.ic_menu_send)
                                val wrappedAction = Notification.Action.Builder(
                                    btnIcon,
                                    action.title ?: "",
                                    action.actionIntent
                                ).build()
                                this.action = createAction("action_generic_$index", wrappedAction)
                                actionTitle = action.title?.toString() ?: ""
                            }
                        }
                    }
                }
            }

            extras.putAll(islandExtras)

            val stateTag = if (isComplete) "done" else "${progress}%"
            XposedBridge.log("HyperIsland[Generic]: Island injected — $title ($stateTag) buttons=${actions.size}")

        } catch (e: Exception) {
            XposedBridge.log("HyperIsland[Generic]: Island injection error: ${e.message}")
        }
    }
}
