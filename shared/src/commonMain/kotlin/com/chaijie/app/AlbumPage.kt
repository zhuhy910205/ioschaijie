package com.chaijie.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.tencent.kuikly.compose.animation.core.FastOutSlowInEasing
import com.tencent.kuikly.compose.animation.core.LinearEasing
import com.tencent.kuikly.compose.animation.core.RepeatMode
import com.tencent.kuikly.compose.animation.core.animateFloat
import com.tencent.kuikly.compose.animation.core.infiniteRepeatable
import com.tencent.kuikly.compose.animation.core.rememberInfiniteTransition
import com.tencent.kuikly.compose.animation.core.tween
import com.tencent.kuikly.compose.foundation.Canvas
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
import com.tencent.kuikly.compose.ComposeContainer
import com.tencent.kuikly.compose.coil3.rememberAsyncImagePainter
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.graphics.StrokeCap
import com.tencent.kuikly.compose.ui.graphics.drawscope.Stroke
import com.tencent.kuikly.compose.ui.graphics.graphicsLayer
import com.tencent.kuikly.compose.ui.layout.ContentScale
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.module.Module
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

@Page("Album")
internal class AlbumPage : ComposeContainer() {

    // 用 MutableState 持有数据，created() 中网络返回后写入，Composable 自动重组
    private val imageItems = mutableStateOf<List<JSONObject>>(emptyList())
    private val isLoading = mutableStateOf(true)
    private val loadError = mutableStateOf("")

    // 缩略图本地缓存路径：photoId -> file:// 路径（有缓存后优先用本地，节省网络）
    private val thumbCache = mutableStateMapOf<String, String>()
    // 当前放大查看的原图（file:// 本地路径），null 关闭
    private var zoomPath by mutableStateOf<String?>(null)
    private var zoomLoading by mutableStateOf(false)

    private val bridge = lazy { acquireModule<CacheBridgeModule>(CacheBridgeModule.MODULE_NAME) }

    override fun createExternalModules(): Map<String, Module>? =
        mapOf(CacheBridgeModule.MODULE_NAME to CacheBridgeModule() as Module)

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

    /** 取缩略图：直接返回 URL（rememberAsyncImagePainter 异步加载，不阻塞 UI 线程） */
    private fun thumbFor(item: JSONObject): String {
        val pid = item.optString("name", "p")
        thumbCache[pid]?.let { return it }
        val remote = cdNThumbUrl(
            item.optString("cloud_optimized_url", ""),
            item.optString("cloud_original_url", ""),
            item.optString("thumbnail_path", "")
        )
        if (remote.isNotEmpty() && remote.startsWith("http")) {
            // 记录到内存缓存（URL 本身，Image 组件异步加载）
            thumbCache[pid] = remote
        }
        return remote
    }

    /** 点击照片：放大查看原图（异步加载，优先本地缓存/相册原图秒开，无则下载缓存） */
    private fun openZoom(item: JSONObject, scope: kotlinx.coroutines.CoroutineScope) {
        val pid = item.optString("name", "p")
        val photoId = item.optLong("photo_id", -1L)
        val remote = item.optString("cloud_original_url", "")
            .ifEmpty { item.optString("cloud_optimized_url", "") }
            .ifEmpty { thumbUrl(item.optString("thumbnail_path"), "") }
        zoomLoading = true
        zoomPath = null
        // 先检查本地已有缓存（夜间预缓存/本机相册直读）→ 秒开不转圈
        val cached = bridge.value.checkLocalOriginal(photoId)
        if (cached.startsWith("file://")) {
            zoomPath = cached
            zoomLoading = false
            return
        }
        // 后台线程：本机相册直读（有映射才读，避免误命中）
        scope.launch {
            val local = withContext(Dispatchers.Default) {
                if (photoId > 0) bridge.value.copyOriginal(photoId) else ""
            }
            if (local.startsWith("file://")) {
                zoomPath = local
                zoomLoading = false
                return@launch
            }
            // 云端照片：原生子线程异步下载（不阻塞 UI → 动画正常渲染），回调更新
            if (remote.startsWith("http")) {
                bridge.value.asyncCacheRemoteOriginal(remote, pid) { res ->
                    val path = res?.optString("path", "") ?: ""
                    zoomPath = if (path.isNotEmpty()) path else remote
                    zoomLoading = false
                }
            } else {
                zoomPath = remote
                zoomLoading = false
            }
        }
    }

    @Composable
    private fun AlbumContent() {
        val scope = rememberCoroutineScope()
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
                Spacer(modifier = Modifier.weight(1f))
                Text(text = "点击照片看原图", color = Color(0xCCFFFFFF), fontSize = 11.sp)
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
                            val url = thumbFor(item)
                            Image(
                                painter = rememberAsyncImagePainter(url),
                                contentDescription = name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .clickable { openZoom(item, scope) },
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
        }
        // 原图放大查看浮层
        if (zoomLoading) {
            // 加载动画：旋转圆环 + 省略号跳动 + 整体呼吸，避免黑屏干等
            val inf = rememberInfiniteTransition()
            val rotate by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(1200, easing = LinearEasing)))
            val breath by inf.animateFloat(0.55f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse))
            val dotPhase by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing)))
            Box(modifier = Modifier.fillMaxSize().background(Color(0xC9000000)).graphicsLayer { alpha = breath }, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // 旋转圆环（用弧线 Canvas 画渐变弧形，旋转动画）
                    Box(
                        modifier = Modifier.size(56.dp)
                            .graphicsLayer { rotationZ = rotate },
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val stroke = 5.dp.toPx()
                            val arcSize = com.tencent.kuikly.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                            // 底层暗环
                            drawArc(color = Color(0x33FFFFFF), startAngle = 0f, sweepAngle = 360f, useCenter = false,
                                topLeft = com.tencent.kuikly.compose.ui.geometry.Offset(stroke / 2, stroke / 2), size = arcSize,
                                style = Stroke(width = stroke, cap = StrokeCap.Round))
                            // 上层亮弧（约 100°，随旋转扫过）
                            drawArc(color = Color(0xFFD97A52), startAngle = 0f, sweepAngle = 110f, useCenter = false,
                                topLeft = com.tencent.kuikly.compose.ui.geometry.Offset(stroke / 2, stroke / 2), size = arcSize,
                                style = Stroke(width = stroke, cap = StrokeCap.Round))
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    // "正在加载原图" + 三个跳动的点（每个点相位差 1/3 周期）
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("正在加载原图", color = Color.White, fontSize = 13.sp)
                        Spacer(Modifier.width(2.dp))
                        Text(text = if (dotPhase < 0.33f) "." else " ", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(text = if (dotPhase in 0.33f..0.66f) "." else " ", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(text = if (dotPhase > 0.66f) "." else " ", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("首次加载稍慢，之后秒开", color = Color(0x99FFFFFF), fontSize = 10.5.sp)
                }
            }
        } else if (zoomPath != null) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xB3000000)).clickable { zoomPath = null },
                contentAlignment = Alignment.Center
            ) {
                val p = zoomPath ?: ""
                Image(
                    painter = rememberAsyncImagePainter(p),
                    contentDescription = "原图",
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    contentScale = ContentScale.Fit
                )
            }
            Text(
                text = "点击任意处关闭",
                color = Color(0xCCFFFFFF), fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp)
            )
        }
    }
}
