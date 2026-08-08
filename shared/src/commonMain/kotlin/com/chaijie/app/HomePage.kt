package com.chaijie.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import com.tencent.kuikly.compose.ComposeContainer
import com.tencent.kuikly.compose.coil3.rememberAsyncImagePainter
import com.tencent.kuikly.compose.foundation.Image
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.border
import com.tencent.kuikly.compose.foundation.Canvas
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.PaddingValues
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.RowScope
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.aspectRatio
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.offset
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.lazy.LazyColumn
import com.tencent.kuikly.compose.foundation.lazy.LazyRow
import com.tencent.kuikly.compose.foundation.lazy.items
import com.tencent.kuikly.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import com.tencent.kuikly.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import com.tencent.kuikly.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import com.tencent.kuikly.compose.foundation.shape.CircleShape
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.foundation.text.BasicTextField
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.graphics.Brush
import com.tencent.kuikly.compose.ui.graphics.SolidColor
import com.tencent.kuikly.compose.setContent
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.clip
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.geometry.Offset
import com.tencent.kuikly.compose.ui.geometry.Size
import com.tencent.kuikly.compose.ui.graphics.drawscope.Stroke
import com.tencent.kuikly.compose.ui.layout.ContentScale
import com.tencent.kuikly.compose.ui.unit.Dp
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.compose.ui.text.TextStyle
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect

@Page("Home")
internal class HomePage : ComposeContainer() {

    private val photos = mutableStateOf<List<JSONObject>>(emptyList())
    private val clusters = mutableStateOf<List<JSONObject>>(emptyList())
    private val isLoading = mutableStateOf(true)
    private val loadError = mutableStateOf("")
    private val selectedCluster = mutableStateOf<String?>(null)
    private val moreOpen = mutableStateOf(false)
    private val searchText = mutableStateOf("")
    /** 搜索结果本地缓存：关键词 -> 结果列表，同词重复搜索秒出，避免重复请求后端 */
    private val searchCache = mutableStateOf<Map<String, List<JSONObject>>>(emptyMap())
    /** 瀑布流分页状态：当前页 / 后端总数 / 是否正在加载下一页 */
    private val photoPage = mutableStateOf(1)
    private val photoTotal = mutableStateOf(0)
    private val photoLoadingMore = mutableStateOf(false)

    private val analyzeVisible = mutableStateOf(false)
    private val analyzeLoading = mutableStateOf(false)
    private val analyzeImageUrl = mutableStateOf("")
    private val analyzeResult = mutableStateOf("")
    private val fullscreenUrl = mutableStateOf("")

    private val toastMsg = mutableStateOf("")
    private val toastSeq = mutableStateOf(0)
    // 删除确认：pendingDelete 存待确认的 filename；deleteConfirm 标记"已点过一次待二次确认"
    private val pendingDelete = mutableStateOf("")
    private val deleteArmed = mutableStateOf(false)

    // 人脸头像真实图片 URL 缓存：cluster_id -> 完整图片地址
    private val faceUrlMap = mutableStateOf<Map<String, String>>(emptyMap())

    companion object {
        private val C_BG_TOP = Color(0xFFEFEAE2)
        private val C_BG_BOT = Color(0xFFE3DDD3)
        private val C_CARD = Color(0xFFF5F2EC)
        private val C_TEXT = Color(0xFF46413B)
        private val C_SUB = Color(0xFF968D83)
        private val C_ACCENT = Color(0xFFA88F79)
        private val C_CLAY = Color(0xFFB07D6B)
        private val C_NAV_BG = Color(0xEBF5F2EC)
        private val C_ACCENT_L = Color(0xFFEAE1D8)
        private val C_WHITE = Color(0xFFFFFFFF)
        // 毛玻璃卡片底色（半透明，对齐原型 rgba(245,242,236,.55)）
        private val C_GLASS = Color(0x8CF5F2EC)
        private val C_GLASS_BORDER = Color(0x73FFFFFF)
        // 背景光晕低 alpha 圆斑
        private val HALO_ACCENT = Color(0x33A88F79)
        private val HALO_CLAY = Color(0x2AB07D6B)
        private val HALO_PINK = Color(0x33C99A8E)
        private val AVATAR_COLORS = listOf(
            0xFFA88F79, 0xFFB07D6B, 0xFF9CAF88, 0xFFC2A878, 0xFF8FA0A8, 0xFFBE8C7A
        )
    }

    override fun willInit() {
        super.willInit()
        setContent { HomeContent() }
    }

    override fun created() {
        super.created()
        loadClusters()
        loadPhotos()
    }

    // ===== 数据加载（瀑布流无限滚动：首屏 60 张，滚动到底部自动加载下一页） =====
    private fun loadPhotos(clusterId: String? = null) {
        isLoading.value = true
        photoPage.value = 1
        photoTotal.value = 0
        val url = if (clusterId != null)
            "${ApiConfig.CHAIJIE_BASE}/api/images?per_page=60&page=1&cluster_id=$clusterId"
        else ApiConfig.CHAIJIE_IMAGES
        getJson(url) { success, data ->
            if (success) {
                val arr = parseArray(data)
                photos.value = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
                // total 字段可选：>0 用后端总数，缺失则置 0（靠"空页"判定停止加载）
                val t = data.optInt("total", -1)
                photoTotal.value = if (t > 0) t else 0
                loadError.value = if (photos.value.isEmpty()) "暂时没有照片" else ""
            } else {
                loadError.value = "加载失败，请检查网络"
            }
            isLoading.value = false
        }
    }

    /** 滚动接近底部时加载下一页，追加到瀑布流（去重合并） */
    private fun loadMorePhotos() {
        if (photoLoadingMore.value || isLoading.value) return
        // 已加载全部（total 已知且当前数量 >= total）
        if (photoTotal.value > 0 && photos.value.size >= photoTotal.value) return
        val nextPage = photoPage.value + 1
        photoLoadingMore.value = true
        val base = selectedCluster.value?.let {
            "${ApiConfig.CHAIJIE_BASE}/api/images?per_page=60&page=$nextPage&cluster_id=$it"
        } ?: "${ApiConfig.CHAIJIE_BASE}/api/images?per_page=60&page=$nextPage"
        getJson(base) { success, data ->
            if (success) {
                val arr = parseArray(data)
                val more = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
                if (more.isEmpty()) {
                    // 返回空页 = 没有更多了（后端无 total 字段时的停止信号）
                    photoTotal.value = photos.value.size
                } else {
                    val existing = photos.value.map { it.optString("name") }.toSet()
                    val fresh = more.filter { it.optString("name") !in existing }
                    if (fresh.isNotEmpty()) {
                        photos.value = photos.value + fresh
                        photoPage.value = nextPage
                    } else {
                        // 全是重复（理论上不该发生）：提前结束，避免死循环
                        photoTotal.value = photos.value.size
                    }
                    val t = data.optInt("total", -1)
                    if (t > 0) photoTotal.value = t
                }
            }
            photoLoadingMore.value = false
        }
    }

    private fun loadClusters() {
        getJson(ApiConfig.CLUSTERS) { success, data ->
            if (success) {
                val arr = data.optJSONArray("clusters")
                if (arr != null) {
                    clusters.value = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
                        .filter { it.optString("cluster_id", "") != "999999" && it.optInt("face_count", 0) > 0 }
                }
            }
        }
    }

    private fun doSearch(text: String) {
        val q = text.trim()
        if (q.isEmpty()) { clearFilter(); return }
        searchText.value = q
        // 本地结果缓存：同一个关键词重复搜索直接秒出，不重复请求后端
        searchCache.value[q]?.let { cached ->
            photos.value = cached
            photoTotal.value = cached.size // 搜索结果全量，禁止分页加载
            loadError.value = if (cached.isEmpty()) "没有找到相关照片" else ""
            return
        }
        isLoading.value = true
        val body = JSONObject().apply { put("search_text", q) }
        postJson(ApiConfig.SEARCH, body) { success, data ->
            if (success) {
                val arr = data.optJSONArray("results")
                photos.value = if (arr != null)
                    (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
                else emptyList()
                photoTotal.value = photos.value.size // 搜索结果全量，禁止分页加载
                // 缓存本次结果，同词再搜秒出
                searchCache.value = searchCache.value + (q to photos.value)
                loadError.value = if (photos.value.isEmpty()) "没有找到相关照片" else ""
            } else {
                loadError.value = "搜索失败"
            }
            isLoading.value = false
        }
    }

    private fun clearFilter() {
        selectedCluster.value = null
        searchText.value = ""
        loadPhotos()
    }

    private fun toggleCluster(cid: String) {
        val next = if (selectedCluster.value == cid) null else cid
        selectedCluster.value = next
        loadPhotos(next)
    }

    private fun openAnalyze(item: JSONObject) {
        // 分析面板用优化图（浏览列表时已由 Glide 缓存 → 秒出）；
        // 原图仅在"查看原图"全屏（openFullscreen）时加载，避免每次分析都等大图下载
        analyzeImageUrl.value = item.optString(
            "cloud_optimized_url", item.optString("cloud_original_url", "")
        )
        analyzeResult.value = ""
        analyzeVisible.value = true
        analyzeLoading.value = true
        val body = JSONObject().apply { put("image_name", item.optString("name", "")) }
        postJson(ApiConfig.ANALYZE, body) { success, data ->
            analyzeResult.value = if (success) data.optString("analysis", "暂无分析结果") else "分析失败，请重试"
            analyzeLoading.value = false
        }
    }

    private fun openFullscreen(item: JSONObject) {
        fullscreenUrl.value = item.optString(
            "cloud_original_url", item.optString("cloud_optimized_url", "")
        )
    }

    /** 删除照片（两步确认）：第一次点变红色"再点删除"，3 秒内再点执行删除 */
    private fun confirmDelete(filename: String) {
        if (filename.isEmpty()) return
        if (!deleteArmed.value || pendingDelete.value != filename) {
            // 第一次点：进入待确认状态
            pendingDelete.value = filename
            deleteArmed.value = true
            toastMsg.value = "再点一次确认删除「$filename」"
            toastSeq.value++
            return
        }
        // 第二次点：执行删除
        deleteArmed.value = false
        pendingDelete.value = ""
        val body = JSONObject().apply { put("filename", filename) }
        postJson("${ApiConfig.CHAIJIE_BASE}/api/images/delete", body) { success, _ ->
            if (success) {
                // 从列表移除（当前页 + 缓存清掉）
                photos.value = photos.value.filterNot { it.optString("name", "") == filename }
                val cache = searchCache.value.toMutableMap()
                cache.keys.forEach { k ->
                    val cur = cache[k] ?: return@forEach
                    cache[k] = cur.filterNot { it.optString("name", "") == filename }
                }
                searchCache.value = cache
                toastMsg.value = "已删除「$filename」"
                toastSeq.value++
            } else {
                toastMsg.value = "删除失败，请重试"
                toastSeq.value++
            }
        }
    }

    private fun goPage(name: String) {
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(name, null)
    }

    private fun showToast(msg: String) {
        toastMsg.value = msg
        toastSeq.value++
    }

    private fun avatarColor(name: String): Color {
        val idx = (name.hashCode().rem(AVATAR_COLORS.size) + AVATAR_COLORS.size) % AVATAR_COLORS.size
        return Color(AVATAR_COLORS[idx])
    }

    // ===== UI =====
    @Composable
    private fun HomeContent() {
        Box(
            modifier = Modifier.fillMaxSize()
                .background(Brush.linearGradient(0f to C_BG_TOP, 1f to C_BG_BOT))
        ) {
            // 背景光晕（对齐原型 body::before 三处 radial-gradient）
            Box(
                modifier = Modifier.align(Alignment.TopStart).offset((-70).dp, (-90).dp)
                    .size(360.dp).clip(CircleShape).background(HALO_ACCENT)
            )
            Box(
                modifier = Modifier.align(Alignment.TopEnd).offset(50.dp, (-110).dp)
                    .size(340.dp).clip(CircleShape).background(HALO_CLAY)
            )
            Box(
                modifier = Modifier.align(Alignment.BottomEnd).offset(30.dp, 90.dp)
                    .size(420.dp).clip(CircleShape).background(HALO_PINK)
            )

            Column(modifier = Modifier.fillMaxSize().padding(top = pageData.statusBarHeight.dp)) {
                FaceFilterRow()
                if (moreOpen.value) MoreFacesPanel()
                FilterHint()
                SearchBar()
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    PhotoWaterfall()
                }
                BottomNavBar()
            }
            if (analyzeVisible.value) AnalyzeSheet()
            if (fullscreenUrl.value.isNotEmpty()) FullscreenOverlay()
            Toast()
        }
    }

    @Composable
    private fun FaceFilterRow() {
        val top = clusters.value.take(4)
        val moreCount = (clusters.value.size - top.size).coerceAtLeast(0)
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 6.dp),
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            items(items = top, key = { it.optString("cluster_id", it.hashCode().toString()) }) { c ->
                val cid = c.optString("cluster_id", "")
                val name = c.optString("person_name", c.optString("cluster_name", "?"))
                val count = c.optInt("face_count", 0)
                val sel = selectedCluster.value == cid
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { toggleCluster(cid) }.padding(horizontal = 6.dp)
                ) {
                    PersonAvatar(name = name, count = count, selected = sel, sizeDp = 56.dp, clusterId = cid, directFaceId = c.optString("representative_face_id", ""))
                }
            }
            if (moreCount > 0) {
                item {
                    Box(
                        modifier = Modifier.clickable { moreOpen.value = !moreOpen.value }
                            .padding(horizontal = 6.dp).size(56.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // 内层圆圈（比外层小 6dp，给右上角角标留 3dp 显示空间）
                        Box(
                            modifier = Modifier.size(50.dp).clip(CircleShape)
                                .background(Color(0xFFE2DBD1))
                                .border(2.dp, if (moreOpen.value) C_ACCENT else Color(0xFFC3B7A8), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "更多",
                                color = if (moreOpen.value) C_ACCENT else C_SUB,
                                fontSize = 14.sp
                            )
                        }
                        // 右上角红色数量角标（与 PersonAvatar 角标一致）
                        Box(
                            modifier = Modifier.align(Alignment.TopEnd)
                                .clip(CircleShape).background(C_CLAY)
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) { Text(text = "$moreCount", color = Color.White, fontSize = 10.sp) }
                    }
                }
            }
        }
    }

    @Composable
    private fun PersonAvatar(
        name: String,
        count: Int,
        selected: Boolean,
        sizeDp: Dp,
        clusterId: String,
        directFaceId: String
    ) {
        // 优先用聚类接口直接返回的 representative_face_id；否则按 cluster_id 拉取分组详情获取代表人脸
        val map = faceUrlMap.value
        val faceUrl = if (directFaceId.isNotEmpty()) "${ApiConfig.CHAIJIE_BASE}/admin/faces/$directFaceId"
                      else map[clusterId] ?: ""
        LaunchedEffect(clusterId, directFaceId) {
            if (directFaceId.isEmpty() && !map.containsKey(clusterId)) {
                this@HomePage.getJson("${ApiConfig.CHAIJIE_BASE}/admin/faces/group/$clusterId") { success, data ->
                    if (success) {
                        val gid = data.optJSONObject("group")?.optString("representative_face_id", "") ?: ""
                        if (gid.isNotEmpty()) {
                            val m = faceUrlMap.value.toMutableMap()
                            m[clusterId] = "${ApiConfig.CHAIJIE_BASE}/admin/faces/$gid"
                            faceUrlMap.value = m
                        }
                    }
                }
            }
        }
        Box(
            modifier = Modifier.size(sizeDp).clip(CircleShape)
                .background(if (selected) C_ACCENT_L else Color.Transparent)
                .padding(3.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize().clip(CircleShape).background(avatarColor(name))
                    .border(2.5.dp, C_WHITE, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // 占位人形剪影：图片加载中 / 加载失败时作为底色显示
                Canvas(modifier = Modifier.size((sizeDp.value * 0.52f).dp)) {
                    val w = size.width
                    val h = size.height
                    val headR = w * 0.20f
                    drawCircle(color = Color.White, radius = headR, center = Offset(w / 2f, h * 0.38f))
                    drawArc(
                        color = Color.White,
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = true,
                        topLeft = Offset(w * 0.16f, h * 0.54f),
                        size = Size(w * 0.68f, h * 0.60f)
                    )
                }
                // 真实人脸照片（覆盖在剪影之上）
                if (faceUrl.isNotEmpty()) {
                    Image(
                        painter = rememberAsyncImagePainter(faceUrl),
                        contentDescription = name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Box(
                modifier = Modifier.align(Alignment.TopEnd).clip(CircleShape).background(C_CLAY)
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) { Text(text = "$count", color = Color.White, fontSize = 10.sp) }
        }
    }

    @Composable
    private fun MoreFacesPanel() {
        val all = clusters.value
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(14.dp)).background(Color(0x99EAE1D8)).padding(10.dp, 8.dp)
        ) {
            LazyRow(contentPadding = PaddingValues(horizontal = 4.dp)) {
                items(items = all, key = { it.optString("cluster_id", it.hashCode().toString()) }) { c ->
                    val cid = c.optString("cluster_id", "")
                    val name = c.optString("person_name", c.optString("cluster_name", "?"))
                    val count = c.optInt("face_count", 0)
                    val sel = selectedCluster.value == cid
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { toggleCluster(cid) }.padding(horizontal = 7.dp)
                    ) {
                        PersonAvatar(name = name, count = count, selected = sel, sizeDp = 54.dp, clusterId = cid, directFaceId = c.optString("representative_face_id", ""))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = name, fontSize = 11.sp, color = if (sel) C_ACCENT else C_SUB, maxLines = 1)
                    }
                }
            }
        }
    }

    @Composable
    private fun FilterHint() {
        val txt = when {
            searchText.value.isNotEmpty() -> "搜索「${searchText.value}」的结果"
            selectedCluster.value != null -> {
                val p = clusters.value.firstOrNull { it.optString("cluster_id", "") == selectedCluster.value }
                val pname = p?.optString("person_name", p?.optString("cluster_name", "?") ?: "?") ?: "?"
                "已选择「$pname」，仅显示其相关照片"
            }
            else -> ""
        }
        if (txt.isNotEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp)).background(Color(0x99EAE1D8)).padding(8.dp, 6.dp)
            ) { Text(text = txt, color = C_ACCENT, fontSize = 13.sp) }
        }
    }

    @Composable
    private fun SearchBar() {
        // 小红书风格 v3：单白色圆角胶囊 + 内部竖线分隔「搜索」+ BasicTextField 极致压缩高度
        Box(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .height(38.dp)
                .clip(RoundedCornerShape(19.dp))
                .background(Color.White)
                .border(0.5.dp, Color(0x1A000000), RoundedCornerShape(19.dp)),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SearchIcon()
                Spacer(modifier = Modifier.width(8.dp))
                BasicTextField(
                    value = searchText.value,
                    onValueChange = { searchText.value = it },
                    singleLine = true,
                    cursorBrush = SolidColor(C_ACCENT),
                    textStyle = TextStyle(
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        color = C_TEXT
                    ),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (searchText.value.isEmpty()) {
                                Text(
                                    text = "搜索照片、人物、地点",
                                    color = Color(0xFFBDBDBD),
                                    fontSize = 14.sp
                                )
                            }
                            innerTextField()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                // 清除按钮（仅在有文字时显示，灰色圆形 + 白色 ✕）
                if (searchText.value.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier.size(16.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE5E5E5))
                            .clickable { clearFilter() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "\u2715", color = Color(0xFF999999), fontSize = 10.sp)
                    }
                }
                // 细灰色竖线分隔「搜索」按钮
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .width(0.5.dp)
                        .height(16.dp)
                        .background(Color(0xFFE0E0E0))
                )
                Spacer(modifier = Modifier.width(8.dp))
                // 「搜索」文字（在胶囊内）
                Text(
                    text = "搜索",
                    modifier = Modifier.clickable { doSearch(searchText.value) }
                        .padding(vertical = 8.dp, horizontal = 6.dp),
                    color = Color(0xFF333333),
                    fontSize = 14.sp
                )
            }
        }
    }

    @Composable
    private fun SearchIcon() {
        Canvas(modifier = Modifier.size(17.dp)) {
            val r = size.width * 0.28f
            val cx = size.width * 0.40f
            val cy = size.height * 0.40f
            drawCircle(
                color = Color(0xFFBDBDBD),
                radius = r,
                center = Offset(cx, cy),
                style = Stroke(width = 1.8.dp.toPx())
            )
            drawLine(
                color = Color(0xFFBDBDBD),
                start = Offset(cx + r * 0.70f, cy + r * 0.70f),
                end = Offset(size.width * 0.82f, size.height * 0.82f),
                strokeWidth = 1.8.dp.toPx()
            )
        }
    }

    @Composable
    private fun PhotoWaterfall() {
        when {
            isLoading.value -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "加载中…", color = C_SUB, fontSize = 14.sp)
                }
            }
            loadError.value.isNotEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = loadError.value, color = C_SUB, fontSize = 14.sp)
                }
            }
            else -> {
                val gridState = rememberLazyStaggeredGridState()
                // 无限滚动：接近底部（剩余 < 8 项）自动加载下一页
                LaunchedEffect(gridState, photos.value.size) {
                    snapshotFlow {
                        val info = gridState.layoutInfo
                        val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                        last to info.totalItemsCount
                    }.collect { (last, total) ->
                        if (total > 0 && last >= total - 8) loadMorePhotos()
                    }
                }
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 11.dp, end = 11.dp, bottom = 100.dp),
                    verticalItemSpacing = 9.dp,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    items(
                        count = photos.value.size,
                        // key 用 index 保证唯一（后端可能返回重复 filename，用文件名作 key 会
                        // 抛 IllegalArgumentException "Key was already used" 导致瀑布流渲染中断/空白）
                        key = { index -> index }
                    ) { index ->
                        PhotoCard(photos.value[index])
                    }
                    // 底部加载更多提示
                    if (photoLoadingMore.value) {
                        item { Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) { Text(text = "加载更多…", color = C_SUB, fontSize = 12.sp) } }
                    }
                }
            }
        }
    }

    @Composable
    private fun PhotoCard(item: JSONObject) {
        // 列表缩略图优先 CDN（cloud_optimized_url，R2 直链 + 长缓存），
        // 手机上传照片之前走 thumbnail_path（/api/optimized_image no-cache）导致滑动卡，
        // 统一 CDN 后流畅。点击查看大图时才用优化图/原图。
        val url = cdNThumbUrl(
            item.optString("cloud_optimized_url", ""),
            item.optString("cloud_original_url", ""),
            item.optString("thumbnail_path", "")
        )
        val w = item.optInt("width", 0)
        val h = item.optInt("height", 0)
        val ratio = if (w > 0 && h > 0) w.toFloat() / h.toFloat() else 1f
        val title = item.optString("name", "").substringBeforeLast(".")
        val filename = item.optString("name", "")
        Column(
            modifier = Modifier.clip(RoundedCornerShape(10.dp))
                .background(C_GLASS)
                .border(1.dp, C_GLASS_BORDER, RoundedCornerShape(10.dp))
                .padding(bottom = 8.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(ratio)) {
                Image(
                    painter = rememberAsyncImagePainter(url),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clickable { openFullscreen(item) }
                )
                // 右上角小删除标志：两步确认（第一次点变"再点确认"，3秒内再点执行删除）
                val armed = deleteArmed.value && pendingDelete.value == filename
                Box(
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (armed) Color(0xCCD9534F) else Color(0x99000000))
                        .clickable { confirmDelete(filename) }
                        .padding(horizontal = 5.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) { Text(text = if (armed) "确认?" else "\u2715", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
            }
            if (title.isNotEmpty()) {
                Text(
                    text = title,
                    fontSize = 13.sp, color = C_TEXT,
                    maxLines = 2,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(
                    modifier = Modifier.weight(1f).clickable { openAnalyze(item) }
                        .clip(RoundedCornerShape(7.dp)).background(Color(0x33A88F79))
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) { Text(text = "分析", color = C_ACCENT, fontSize = 12.sp) }
                Box(
                    modifier = Modifier.weight(1f).clickable { openFullscreen(item) }
                        .clip(RoundedCornerShape(7.dp)).background(Color(0x33A88F79))
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) { Text(text = "原图", color = C_ACCENT, fontSize = 12.sp) }
            }
        }
    }

    @Composable
    private fun BottomNavBar() {
        Row(
            modifier = Modifier.fillMaxWidth().background(C_NAV_BG)
                .padding(vertical = 10.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            NavItem(NavIconName.HOME, "首页", true, 0.dp) { goPage("Home") }
            NavItem(NavIconName.YEAR, "流年", false, (-10).dp) { goPage("YearFlow") }
            // ＋ 中间按钮，与左右四项一起五等分底部
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f).clickable { goPage("Upload") }
            ) {
                Box(
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(15.dp))
                        .background(C_CLAY).offset(y = (-4).dp),
                    contentAlignment = Alignment.Center
                ) { NavIcon(NavIconName.PLUS, Color.White, 26.dp) }
            }
            NavItem(NavIconName.PLACE, "方隅", false, 10.dp) { goPage("Place") }
            NavItem(NavIconName.VIDEO, "视频", false, 0.dp) { goPage("Video") }
        }
    }

    @Composable
    private fun RowScope.NavItem(icon: NavIconName, label: String, active: Boolean, offsetX: Dp = 0.dp, onClick: () -> Unit) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable { onClick() }.weight(1f).offset(x = offsetX)
        ) {
            NavIcon(icon, if (active) C_CLAY else C_SUB, 23.dp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = label, fontSize = 11.sp, color = if (active) C_TEXT else C_SUB)
        }
    }

    @Composable
    private fun AnalyzeSheet() {
        Box(
            modifier = Modifier.fillMaxSize().background(Color(0x66000000)).clickable { analyzeVisible.value = false },
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xBCF5F2EC))
                    .clickable { }.padding(16.dp)
            ) {
                if (analyzeImageUrl.value.isNotEmpty()) {
                    Image(
                        painter = rememberAsyncImagePainter(analyzeImageUrl.value),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                Text(text = "AI 分析", fontSize = 18.sp, color = C_TEXT)
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                    item {
                        Text(
                            text = if (analyzeLoading.value) "分析中…" else analyzeResult.value,
                            fontSize = 13.sp, color = C_TEXT,
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier.fillMaxWidth().clickable { analyzeVisible.value = false }
                        .clip(RoundedCornerShape(12.dp)).background(C_ACCENT).padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) { Text(text = "关闭", color = Color.White, fontSize = 15.sp) }
            }
        }
    }

    @Composable
    private fun FullscreenOverlay() {
        Box(
            modifier = Modifier.fillMaxSize().background(Color(0xEB000000)).clickable { fullscreenUrl.value = "" },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = rememberAsyncImagePainter(fullscreenUrl.value),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(36.dp)
                    .clip(CircleShape).background(Color(0x33FFFFFF)),
                contentAlignment = Alignment.Center
            ) { Text(text = "✕", color = Color.White, fontSize = 18.sp) }
        }
    }

    @Composable
    private fun Toast() {
        val msg = toastMsg.value
        if (msg.isNotEmpty()) {
            LaunchedEffect(toastSeq.value) {
                delay(1600)
                toastMsg.value = ""
            }
            Box(
                modifier = Modifier.fillMaxSize().padding(bottom = 92.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier.background(Color(0xEB46413B))
                        .clip(RoundedCornerShape(20.dp)).padding(horizontal = 18.dp, vertical = 10.dp)
                ) { Text(text = msg, color = Color.White, fontSize = 13.sp) }
            }
        }
    }
}
