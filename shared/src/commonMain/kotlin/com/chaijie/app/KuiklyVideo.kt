package com.chaijie.app

import androidx.compose.runtime.Composable
import com.tencent.kuikly.compose.extension.MakeKuiklyComposeNode
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.views.PlayState
import com.tencent.kuikly.core.views.VideoPlayControl
import com.tencent.kuikly.core.views.VideoView

/**
 * Kuikly 视频组件（Compose 封装）。
 * 参考 KuiklyUI demo ComposeVideoDemo / VideoView.kt：
 * 通过 MakeKuiklyComposeNode 把原生 VideoView 接入 Compose 树。
 * 底层为 Android 原生播放器（自带网络流缓存）。
 */
@Composable
fun KuiklyVideo(
    src: String,
    playControl: VideoPlayControl,
    modifier: Modifier = Modifier,
    onPlayStateChanged: ((state: PlayState, extInfo: JSONObject) -> Unit)? = null,
    onPlayTimeChanged: ((curTime: Int, totalTime: Int) -> Unit)? = null,
) {
    MakeKuiklyComposeNode<VideoView>(
        factory = { VideoView() },
        modifier = modifier,
        viewInit = {
            getViewAttr().run {
                src(src)
                playControl(playControl)
            }
            onPlayStateChanged?.let { getViewEvent().playStateDidChanged(it) }
            onPlayTimeChanged?.let { cb ->
                getViewEvent().playTimeDidChanged { cur, total ->
                    cb(cur, total)
                }
            }
        },
        viewUpdate = { view ->
            view.getViewAttr().run {
                src(src)
                playControl(playControl)
            }
        }
    )
}
