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
import com.tencent.kuikly.compose.foundation.lazy.grid.GridCells
import com.tencent.kuikly.compose.foundation.lazy.grid.LazyVerticalGrid
import com.tencent.kuikly.compose.foundation.lazy.grid.items
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

@Page("Album")
internal class AlbumPage : ComposeContainer() {

    // 用 MutableState 持有数据，created() 中网络返回后写入，Composable 自动重组
    private val imageItems = mutableStateOf<List<JSONObject>>(emptyList())
    private val isLoading = mutableStateOf(true)
    private val loadError = mutableStateOf("")

    override fun willInit() {
        super.willInit()
        setContent {
            AlbumContent()
        }
    }

    // Module 在 willInit 之后才注册，必须在 created() 或之后调用网络
    override fun created() {
        super.created()
        loadImages()
    }

    private fun loadImages() {
        getJson(ApiConfig.CHAIJIE_IMAGES) { success, data ->
            if (success) {
                val arr = parseArray(data)
                val list = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
                imageItems.value = list
                loadError.value = if (list.isEmpty()) "暂时没有照片" else ""
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
    private fun AlbumContent() {
        Column(modifier = Modifier.fillMaxSize().background(Color(0xFFE9E5DE)).padding(top = pageData.statusBarHeight.dp)) {
            // 顶部标题栏 + 返回
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(Color(0xFF3B82F6))
                    .clickable { goBack() }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "‹ 返回", color = Color.White, fontSize = 16.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = "柴杰相册", color = Color.White, fontSize = 18.sp)
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
                    val items = imageItems.value
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(items = items, key = { it.hashCode() }) { item ->
                            val name = item.optString("name", "")
                            val url = thumbUrl(item.optString("thumbnail_path"), item.optString("cloud_optimized_url", ""))
                            Image(
                                painter = rememberAsyncImagePainter(url),
                                contentDescription = name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
        }
    }
}
