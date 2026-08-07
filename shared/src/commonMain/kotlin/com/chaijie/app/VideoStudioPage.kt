package com.chaijie.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import com.tencent.kuikly.compose.ComposeContainer
import com.tencent.kuikly.compose.coil3.rememberAsyncImagePainter
import com.tencent.kuikly.compose.foundation.Image
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.PaddingValues
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.lazy.LazyColumn
import com.tencent.kuikly.compose.foundation.lazy.items
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.setContent
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.layout.ContentScale
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

@Page("VideoStudio")
internal class VideoStudioPage : ComposeContainer() {

    private val videoItems = mutableStateOf<List<JSONObject>>(emptyList())
    private val isLoading = mutableStateOf(true)
    private val loadError = mutableStateOf("")

    override fun willInit() {
        super.willInit()
        setContent {
            VideoContent()
        }
    }

    override fun created() {
        super.created()
        loadVideos()
    }

    private fun loadVideos() {
        getJson(ApiConfig.VIDEO_LIST) { success, data ->
            if (success) {
                val arr = parseArray(data)
                val list = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
                videoItems.value = list
                loadError.value = if (list.isEmpty()) "暂时没有视频" else ""
            } else {
                loadError.value = "加载失败，请检查网络"
            }
            isLoading.value = false
        }
    }

    private fun goBack() {
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
    }

    @Composable
    private fun VideoContent() {
        Column(modifier = Modifier.fillMaxSize().background(Color(0xFFE9E5DE)).padding(top = pageData.statusBarHeight.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(Color(0xFFEF4444))
                    .clickable { goBack() }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "‹ 返回", color = Color.White, fontSize = 16.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = "视频工作室", color = Color.White, fontSize = 18.sp)
            }

            when {
                isLoading.value -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "加载中…", color = Color.Gray, fontSize = 14.sp)
                    }
                }
                loadError.value.isNotEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = loadError.value, color = Color.Gray, fontSize = 14.sp)
                    }
                }
                else -> {
                    val items = videoItems.value
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = items,
                            key = { it.optString("id", it.hashCode().toString()) }
                        ) { item ->
                            VideoCard(item)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun VideoCard(item: JSONObject) {
        val title = item.optString("title", item.optString("name", "未命名视频"))
        val who = item.optString("who", "")
        val dur = item.optString("dur", "")
        val thumb = item.optString("thumbnail", "")
        val desc = item.optString("desc", "")

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .clickable { /* 播放功能后续接入 */ }
        ) {
            // 封面
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(Color(0xFF222222)),
                contentAlignment = Alignment.Center
            ) {
                if (thumb.isNotEmpty()) {
                    Image(
                        painter = rememberAsyncImagePainter(thumb),
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                // 时长徽标
                if (dur.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .background(Color(0xAA000000))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(text = dur, color = Color.White, fontSize = 12.sp)
                    }
                }
            }
            // 信息区
            Column(modifier = Modifier.padding(12.dp)) {
                Text(text = title, color = Color.Black, fontSize = 16.sp)
                if (who.isNotEmpty() || desc.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = listOf(who, desc).filter { it.isNotEmpty() }.joinToString(" · "),
                        color = Color.Gray, fontSize = 13.sp
                    )
                }
            }
        }
    }
}
