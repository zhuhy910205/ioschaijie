package com.chaijie.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.tencent.kuikly.compose.ComposeContainer
import com.tencent.kuikly.compose.coil3.rememberAsyncImagePainter
import com.tencent.kuikly.compose.foundation.Canvas
import com.tencent.kuikly.compose.foundation.Image
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.border
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.gestures.detectTapGestures
import com.tencent.kuikly.compose.foundation.gestures.detectTransformGestures
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.BoxWithConstraints
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.PaddingValues
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.RowScope
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.aspectRatio
import com.tencent.kuikly.compose.foundation.layout.fillMaxHeight
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.offset
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.layout.widthIn
import com.tencent.kuikly.compose.foundation.lazy.LazyColumn
import com.tencent.kuikly.compose.foundation.lazy.LazyRow
import com.tencent.kuikly.compose.foundation.lazy.items
import com.tencent.kuikly.compose.foundation.lazy.grid.GridCells
import com.tencent.kuikly.compose.foundation.lazy.grid.LazyVerticalGrid
import com.tencent.kuikly.compose.foundation.lazy.grid.items as gridItems
import com.tencent.kuikly.compose.foundation.pager.HorizontalPager
import com.tencent.kuikly.compose.foundation.pager.rememberPagerState
import com.tencent.kuikly.compose.foundation.shape.CircleShape
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.foundation.text.BasicTextField
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.setContent
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.clip
import com.tencent.kuikly.compose.ui.draw.rotate
import com.tencent.kuikly.compose.ui.geometry.Offset
import com.tencent.kuikly.compose.ui.graphics.Path
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.graphics.SolidColor
import com.tencent.kuikly.compose.ui.graphics.drawscope.Stroke
import com.tencent.kuikly.compose.ui.input.pointer.PointerInputChange
import com.tencent.kuikly.compose.ui.input.pointer.pointerInput
import com.tencent.kuikly.compose.ui.platform.LocalDensity
import com.tencent.kuikly.compose.ui.text.TextStyle
import com.tencent.kuikly.compose.ui.unit.Dp
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * 方隅页面（GPS 照片浏览）。对标 place_v1.html + 方隅GPS照片功能规格.md。
 *
 * 底图：接入高德矢量瓦片（webrd0{1-4}.is.autonavi.com/appmaptile，无需 key，
 * 规格文档第 7 节方案 A 同源瓦片），Web Mercator 投影将 GCJ-02 坐标投影到舞台像素，
 * 叠加苹果相册风格的照片 Pin。瓦片加载失败/离线时回退风格化网格底图。
 * 数据接入 chaijie 后端 /api/images/group_by_location，缩略图经 ApiConfig.chaijieImageUrl 转直链。
 */
@Page("Place")
internal class PlacePage : ComposeContainer() {

    companion object {
        private val C_BG = Color(0xFFE9E5DE)
        private val C_CARD = Color(0xFFF5F2EC)
        private val C_TEXT = Color(0xFF46413B)
        private val C_SUB = Color(0xFF968D83)
        private val C_ACCENT = Color(0xFFA88F79)
        private val C_CLAY = Color(0xFFB07D6B)
        private val C_ACCENT_L = Color(0xFFEAE1D8)
        private val C_WHITE = Color(0xFFFFFFFF)
        private val C_NAV_BG = Color(0xDDF5F2EC)
        private val C_BORDER = Color(0xFFE0DAD1)
        /** 高德瓦片初始缩放级别（默认聚焦照片最多地点时用 12，规格建议） */
        private const val MAP_ZOOM = 12
        /** 高德瓦片像素边长 */
        private const val TILE_PX = 256f
        /** 默认中心（无 GPS 数据时回退：金地自在城固定坐标） */
        private const val DEFAULT_LAT = 31.904091
        private const val DEFAULT_LNG = 118.663337
    }

    /** 高德标准瓦片 URL（style=8 道路图；子域 1-4 分散请求） */
    private fun amapTileUrl(tx: Int, ty: Int, z: Int): String {
        val s = (tx + ty).rem(4).let { if (it < 0) it + 4 else it } + 1
        return "https://webrd0$s.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x=$tx&y=$ty&z=$z"
    }

    data class Photo(
        val filename: String, val shootingTime: String?, val thumb: String,
        val lat: Double?, val lng: Double?, val hasGps: Boolean,
    )

    data class LocationGroup(
        val address: String, val count: Int, val hasGps: Boolean,
        val lat: Double, val lng: Double, val photos: List<Photo>, val noGps: Boolean,
    )

    private val groups = mutableStateOf<List<LocationGroup>>(emptyList())
    private val isLoading = mutableStateOf(true)
    private val loadError = mutableStateOf("")
    private val query = mutableStateOf("")
    private val view = mutableStateOf("map") // map | grid
    /** 地图交互状态：缩放级别 / 中心经纬度 / 拖动捏合中的实时 pan 偏移（px） */
    private val mapZoom = mutableStateOf(MAP_ZOOM)
    private val mapCenterLat = mutableStateOf(DEFAULT_LAT)
    private val mapCenterLng = mutableStateOf(DEFAULT_LNG)
    private val mapPan = mutableStateOf(Offset.Zero)
    /** 网格视图分组折叠状态：已折叠（收起照片）的分组地址集合 */
    private val collapsedGroups = mutableStateOf<Set<String>>(emptySet())
    private val activeSpot = mutableStateOf<String?>(null)
    private val sheetOpen = mutableStateOf(false)
    private val fsOpen = mutableStateOf(false)
    private val fsPhoto = mutableStateOf<Photo?>(null)
    private val fsGroup = mutableStateOf<LocationGroup?>(null)
    private val toastMsg = mutableStateOf("")
    private val toastSeq = mutableStateOf(0)

    override fun willInit() {
        super.willInit()
        setContent { PlaceContent() }
    }

    override fun created() {
        super.created()
        loadGroups()
    }

    private fun loadGroups() {
        getJson(ApiConfig.GROUP_BY_LOCATION) { success, data ->
            if (success && data.optBoolean("success", false)) {
                val arr = data.optJSONArray("groups") ?: JSONArray()
                val list = (0 until arr.length()).mapNotNull { parseGroup(arr.optJSONObject(it)) }
                groups.value = list
                loadError.value = if (list.isEmpty()) "还没有带位置的照片" else ""
            } else {
                loadError.value = "加载失败，请检查网络"
            }
            isLoading.value = false
            if (groups.value.isNotEmpty()) {
                // 地图中心聚焦照片最多的 GPS 地点（规格 4.1-5）
                val gpsTop = groups.value.filter { it.hasGps }.maxByOrNull { it.count }
                if (gpsTop != null) {
                    mapCenterLat.value = gpsTop.lat
                    mapCenterLng.value = gpsTop.lng
                }
                // 网格视图：默认折叠超大分组（>40 张），但照片最多的分组保持展开（缩略图齐全后不卡）
                val gpsTop2 = groups.value.maxByOrNull { it.count }
                collapsedGroups.value = groups.value
                    .filter { it.count > 40 && it.address != gpsTop2?.address }
                    .map { it.address }.toSet()
                val top = groups.value.maxByOrNull { it.count }
                if (top != null) {
                    activeSpot.value = top.address
                    showToast("已聚焦照片最多的地点：${top.address}")
                }
            }
        }
    }

    internal fun parseGroup(o: JSONObject?): LocationGroup? {
        if (o == null) return null
        val imgs = o.optJSONArray("images") ?: JSONArray()
        val photos = (0 until imgs.length()).mapNotNull { idx ->
            val p = imgs.optJSONObject(idx) ?: return@mapNotNull null
            val fn = p.optString("filename", "")
            if (fn.isEmpty()) return@mapNotNull null
            Photo(
                filename = fn,
                shootingTime = p.optString("shooting_time", ""),
                thumb = thumbUrl(p.optString("thumbnail_path"), ApiConfig.chaijieImageUrl(fn)),
                lat = p.optDouble("latitude", 0.0).takeIf { p.optBoolean("has_gps", false) },
                lng = p.optDouble("longitude", 0.0).takeIf { p.optBoolean("has_gps", false) },
                hasGps = p.optBoolean("has_gps", false),
            )
        }
        val hasGps = o.optBoolean("has_gps", false)
        val lat = o.optDouble("primary_latitude", 0.0)
        val lng = o.optDouble("primary_longitude", 0.0)
        return LocationGroup(
            address = o.optString("address", "未知地点"),
            count = o.optInt("count", photos.size),
            hasGps = hasGps,
            lat = lat, lng = lng,
            photos = photos,
            noGps = !hasGps,
        )
    }

    private fun goPage(name: String) {
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(name, null)
    }

    private fun showToast(msg: String) {
        toastMsg.value = msg
        toastSeq.value++
    }

    @Composable
    private fun PlaceContent() {
        LaunchedEffect(toastSeq.value) {
            if (toastMsg.value.isNotEmpty()) {
                kotlinx.coroutines.delay(1700)
                toastMsg.value = ""
            }
        }
        Column(modifier = Modifier.fillMaxSize().background(C_BG).padding(top = pageData.statusBarHeight.dp)) {
            // 从上到下：地图|网格切换（最上，融入式）→ 搜索框 → 详细功能（地图/网格）
            SegBar()
            SearchBar()
            if (view.value == "map") {
                MapStage(modifier = Modifier.weight(1f))
                SpotRail()
            } else {
                GridView(modifier = Modifier.weight(1f))
            }
            BottomNavBar()
        }
        if (sheetOpen.value) DetailSheet()
        if (fsOpen.value) FullscreenViewer()
        Toast()
    }

    /** 地图 / 网格 切换 —— 置顶居中，融入式（无外围背景条，选中项高亮胶囊） */
    @Composable
    private fun SegBar() {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            SegItem("地图", view.value == "map") { view.value = "map" }
            SegItem("网格", view.value == "grid") { view.value = "grid" }
        }
    }

    @Composable
    private fun SegItem(label: String, active: Boolean, onClick: () -> Unit) {
        val icon = if (label == "地图")
            "M9 4 L3 6.5 L3 19.5 L9 17 L15 19.5 L21 17 L21 4 L15 6.5 L9 4 Z M9 4 L9 17 M15 6.5 L15 19.5"
        else "M3 3 L10 3 L10 10 L3 10 Z M14 3 L21 3 L21 10 L14 10 Z M3 14 L10 14 L10 21 L3 21 Z M14 14 L21 14 L21 21 L14 21 Z"
        Row(
            modifier = Modifier.clip(RoundedCornerShape(17.dp)).background(if (active) C_ACCENT else Color.Transparent)
                .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(modifier = Modifier.size(14.dp)) {
                val u = size.width / 24f
                val w = size.width / 16f
                val p = Path()
                // 图标已预处理为绝对坐标（仅 M/L/Z），避免解析相对命令（v/h/l）出错
                icon.split("M").forEach { sub ->
                    if (sub.isEmpty()) return@forEach
                    val nums = sub.split("L").flatMap { it.split(" ") }
                        .filter { it.isNotEmpty() && it != "Z" && it != "z" }
                    nums.chunked(2).forEachIndexed { i, pair ->
                        if (pair.size < 2) return@forEachIndexed
                        val x = pair[0].toFloat() * u
                        val y = pair[1].toFloat() * u
                        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                    }
                    if (sub.contains("Z", ignoreCase = true)) p.close()
                }
                drawPath(p, if (active) C_WHITE else C_SUB, style = com.tencent.kuikly.compose.ui.graphics.drawscope.Stroke(w))
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = label, fontSize = 12.5.sp, color = if (active) C_WHITE else C_SUB, fontWeight = if (active) com.tencent.kuikly.compose.ui.text.font.FontWeight.SemiBold else com.tencent.kuikly.compose.ui.text.font.FontWeight.Normal)
        }
    }

    @Composable
    private fun SearchBar() {
        // 小红书风格：单白色圆角胶囊 + 放大镜 + 占位 + 灰色 ✕（与首页搜索框一致）
        Box(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp)
                .height(38.dp)
                .clip(RoundedCornerShape(19.dp))
                .background(Color.White)
                .border(0.5.dp, Color(0x1A000000), RoundedCornerShape(19.dp)),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(start = 12.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SearchIcon()
                Spacer(modifier = Modifier.width(8.dp))
                BasicTextField(
                    value = query.value,
                    onValueChange = { query.value = it },
                    singleLine = true,
                    cursorBrush = SolidColor(C_ACCENT),
                    textStyle = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, color = C_TEXT),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.value.isEmpty()) {
                                Text(text = "搜索地点、城市或照片", color = Color(0xFFBDBDBD), fontSize = 14.sp)
                            }
                            innerTextField()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                if (query.value.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier.size(16.dp).clip(CircleShape)
                            .background(Color(0xFFE5E5E5)).clickable { query.value = "" },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "\u2715", color = Color(0xFF999999), fontSize = 10.sp)
                    }
                }
            }
        }
    }

    @Composable
    private fun SearchIcon() {
        Canvas(modifier = Modifier.size(16.dp)) {
            val r = size.width * 0.28f
            val cx = size.width * 0.40f
            val cy = size.height * 0.40f
            drawCircle(
                color = Color(0xFF9A9189), radius = r, center = Offset(cx, cy),
                style = Stroke(width = 1.6.dp.toPx())
            )
            drawLine(
                color = Color(0xFF9A9189),
                start = Offset(cx + r * 0.7f, cy + r * 0.7f),
                end = Offset(size.width * 0.84f, size.height * 0.84f),
                strokeWidth = 1.6.dp.toPx()
            )
        }
    }

    private fun visibleGroups(): List<LocationGroup> {
        val q = query.value.trim().lowercase()
        if (q.isEmpty()) return groups.value
        return groups.value.filter { g ->
            g.address.lowercase().contains(q) ||
                g.photos.any { it.filename.lowercase().contains(q) || (it.shootingTime ?: "").lowercase().contains(q) }
        }
    }

    @Composable
    private fun MapStage(modifier: Modifier = Modifier) {
        BoxWithConstraints(
            modifier = modifier.fillMaxWidth().padding(horizontal = 11.dp)
                .clip(RoundedCornerShape(16.dp)).border(1.dp, Color(0x80FFFFFF), RoundedCornerShape(16.dp))
                .background(C_BG),
        ) {
            val W = maxWidth
            val H = maxHeight
            val density = LocalDensity.current.density
            val Wpx = W.value * density
            val Hpx = H.value * density
            val visible = visibleGroups()

            // 地图中心：可交互状态（拖动/缩放后更新）。base center 不含 pan：瓦片布局/Pin 投影均基于它
            val zoom = mapZoom.value
            val n = 2.0.pow(zoom)
            val panX = mapPan.value.x
            val panY = mapPan.value.y
            val baseCenterXt = (mapCenterLng.value + 180.0) / 360.0 * n
            val baseCenterYt = (1.0 - asinh(tan(mapCenterLat.value * PI / 180.0)) / PI) / 2.0 * n
            fun project(lat: Double, lng: Double): Pair<Dp, Dp> {
                val xt = (lng + 180.0) / 360.0 * n
                val yt = (1.0 - asinh(tan(lat * PI / 180.0)) / PI) / 2.0 * n
                val x = ((xt - baseCenterXt) * TILE_PX + Wpx / 2f) / density
                val y = ((yt - baseCenterYt) * TILE_PX + Hpx / 2f) / density
                return Pair(x.dp, y.dp)
            }
            // 拖动整体偏移（px → dp）：瓦片层 / Pin 层共用，拖动只移动这两个父节点，子节点零重排
            val panOff = Modifier.offset(x = (panX / density).dp, y = (panY / density).dp)

            // ===== 底图：高德瓦片拼图（按 base center 布局 + 预加载 3 圈余量） =====
            val halfW = Wpx / 2f / TILE_PX
            val halfH = Hpx / 2f / TILE_PX
            val tMinX = floor(baseCenterXt - halfW).toInt() - 3
            val tMaxX = ceil(baseCenterXt + halfW).toInt() + 3
            val tMinY = floor(baseCenterYt - halfH).toInt() - 3
            val tMaxY = ceil(baseCenterYt + halfH).toInt() + 3
            val tileDp = (TILE_PX / density).dp
            // 兜底网格（放瓦片下层：瓦片加载失败/离线时露出）
            Canvas(modifier = Modifier.fillMaxSize()) {
                val step = 56f
                var y = 0f
                while (y <= size.height) {
                    drawLine(color = Color(0x14A88F79), start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 1f)
                    y += step
                }
                var x = 0f
                while (x <= size.width) {
                    drawLine(color = Color(0x14A88F79), start = Offset(x, 0f), end = Offset(x, size.height), strokeWidth = 1f)
                    x += step
                }
            }
            Box(modifier = Modifier.fillMaxSize().background(Color(0x0A46413B)))
            // 高德瓦片层：整体 offset(pan)，拖动只移动这一层（单节点），瓦片子节点不重排 → 流畅
            Box(modifier = Modifier.fillMaxSize().then(panOff)) {
                for (tx in tMinX..tMaxX) {
                    for (ty in tMinY..tMaxY) {
                        val xPx = (tx - baseCenterXt) * TILE_PX + Wpx / 2f
                        val yPx = (ty - baseCenterYt) * TILE_PX + Hpx / 2f
                        Image(
                            painter = rememberAsyncImagePainter(amapTileUrl(tx, ty, zoom)),
                            contentDescription = null,
                            modifier = Modifier.offset(x = (xPx / density).dp, y = (yPx / density).dp)
                                .size(tileDp)
                        )
                    }
                }
            }
            // 拖动停止 400ms 后自动把 pan 并入中心（几何等价，无跳变），按新中心重铺瓦片补齐缺口
            LaunchedEffect(mapPan.value) {
                if (mapPan.value != Offset.Zero) {
                    kotlinx.coroutines.delay(400)
                    commitMapPan()
                }
            }

                        // 聚合 + 渲染 Pin（remember 缓存：拖动中 visible/center/zoom 不变 → 聚合结果复用，不重算）
            val clusters = remember(visible, mapCenterLat.value, mapCenterLng.value, mapZoom.value) {
                buildClusters(visible, ::project)
            }
            // 自定义手势层（不依赖 detectTransformGestures，绕开其双指缩放失效问题）：
            // 单指 = 拖动平移；双指 = 捏合缩放（以 centroid 为锚）+ 平移；单指短按 = 命中 Pin 打开详情
            Box(
                modifier = Modifier.fillMaxSize().pointerInput(visible.size, clusters) {
                    awaitPointerEventScope {
                        var gestureActive = false
                        var startPos: Offset? = null
                        var startTime = 0L
                        var maxPointer = 0
                        var lastDist = 0f
                        var lastCentroid: Offset? = null
                        val densityLocal = density
                        val clustersLocal = clusters
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) {
                                // 全部抬起：gesture 结束；单指短按且未移动 → tap 命中 Pin
                                if (gestureActive && maxPointer == 1 && startPos != null &&
                                    (event.changes.firstOrNull()?.uptimeMillis ?: startTime) - startTime < 280L
                                ) {
                                    val tap = startPos!!
                                    var best = -1
                                    var bestD = Float.MAX_VALUE
                                    for ((i, c) in clustersLocal.withIndex()) {
                                        val dx = tap.x - c.x.value * densityLocal
                                        val dy = tap.y - c.y.value * densityLocal
                                        val d = dx * dx + dy * dy
                                        if (d < bestD) { bestD = d; best = i }
                                    }
                                    if (best >= 0 && bestD < 52f * 52f * densityLocal * densityLocal) {
                                        openSpot(clustersLocal[best].groups.first())
                                    }
                                }
                                gestureActive = false; startPos = null; lastDist = 0f; lastCentroid = null; maxPointer = 0
                                continue
                            }
                            if (!gestureActive) {
                                gestureActive = true
                                startPos = pressed.first().position
                                startTime = pressed.first().uptimeMillis
                                maxPointer = pressed.size
                                lastDist = 0f; lastCentroid = null
                                continue
                            }
                            maxPointer = maxOf(maxPointer, pressed.size)
                            if (pressed.size >= 2) {
                                // 双指：捏合缩放（以 centroid 为锚）+ centroid 移动平移
                                val d = dist2(pressed)
                                if (lastDist > 0f && d > 0f) {
                                    val zoomChange = d / lastDist
                                    if (zoomChange != 1f) {
                                        val centroid = centroid2(pressed)
                                        commitMapPan()
                                        val z0 = mapZoom.value
                                        val z1 = (z0 + ln(zoomChange.toDouble()) / ln(2.0)).toInt().coerceIn(3, 19)
                                        if (z1 != z0) {
                                            val n0 = 2.0.pow(z0)
                                            val cxt = (mapCenterLng.value + 180.0) / 360.0 * n0 + (centroid.x - Wpx / 2f) / TILE_PX
                                            val cyt = (1.0 - asinh(tan(mapCenterLat.value * PI / 180.0)) / PI) / 2.0 * n0 + (centroid.y - Hpx / 2f) / TILE_PX
                                            val n1 = 2.0.pow(z1)
                                            mapCenterLng.value = ((cxt - (centroid.x - Wpx / 2f) / TILE_PX) / n1) * 360.0 - 180.0
                                            mapCenterLat.value = atan(sinh(PI * (1.0 - 2.0 * (cyt - (centroid.y - Hpx / 2f) / TILE_PX) / n1))) * 180.0 / PI
                                            mapZoom.value = z1
                                        }
                                    }
                                }
                                lastDist = d
                                val centroid = centroid2(pressed)
                                lastCentroid?.let { lc ->
                                    if (centroid != lc) {
                                        mapPan.value = Offset(mapPan.value.x + centroid.x - lc.x, mapPan.value.y + centroid.y - lc.y)
                                    }
                                }
                                lastCentroid = centroid
                            } else {
                                // 单指：拖动平移
                                val pos = pressed.first().position
                                lastCentroid?.let { lc ->
                                    if (pos != lc) {
                                        mapPan.value = Offset(mapPan.value.x + pos.x - lc.x, mapPan.value.y + pos.y - lc.y)
                                    }
                                }
                                lastCentroid = pos
                            }
                        }
                    }
                }
            )
            // Pin 层（整体 offset pan）：纯视觉节点，触摸穿透到手势层
            Box(modifier = Modifier.fillMaxSize().then(panOff)) {
                clusters.forEach { c ->
                    Box(
                        modifier = Modifier.offset(x = c.x - 28.dp, y = c.y - 56.dp)
                    ) { ApplePin(c) }
                }
            }

            // 统计
            val stat = "${visible.size} 个地点 · ${visible.sumOf { it.count }} 张"
            Box(
                modifier = Modifier.align(Alignment.TopStart).padding(11.dp)
                    .background(Color(0xDCFFFFFF), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
            ) { Text(text = stat, fontSize = 11.5.sp, color = C_TEXT) }

            // FAB：缩放（+ / −） / 显示全部 / 定位
            Column(modifier = Modifier.align(Alignment.BottomEnd).padding(11.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 缩放按钮（不依赖双指手势，兜底可用）
                Box(
                    modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xDCFFFFFF))
                        .border(1.dp, Color(0x99FFFFFF), RoundedCornerShape(12.dp)).clickable { zoomBy(1) },
                    contentAlignment = Alignment.Center,
                ) { Text(text = "+", fontSize = 19.sp, color = C_TEXT, fontWeight = com.tencent.kuikly.compose.ui.text.font.FontWeight.Bold) }
                Box(
                    modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xDCFFFFFF))
                        .border(1.dp, Color(0x99FFFFFF), RoundedCornerShape(12.dp)).clickable { zoomBy(-1) },
                    contentAlignment = Alignment.Center,
                ) { Text(text = "−", fontSize = 19.sp, color = C_TEXT, fontWeight = com.tencent.kuikly.compose.ui.text.font.FontWeight.Bold) }
                Fab("M3 8 L3 5 L5 3 L8 3 M16 3 L19 3 L21 5 L21 8 M21 16 L21 19 L19 21 L16 21 M8 21 L5 21 L3 19 L3 16",
                    onClick = { activeSpot.value = null; showToast("已显示全部拍摄地点") })
                Fab("M12 2 L12 5 M12 19 L12 22 M2 12 L5 12 M19 12 L22 12 M12 7 L13.91 7.38 L15.54 8.46 L16.62 10.09 L17 12",
                    onClick = { showToast("定位需授权（原型占位）") })
            }

            if (isLoading.value) {
                Box(modifier = Modifier.fillMaxSize().background(Color(0xDCFFFFFF)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.size(26.dp).background(C_ACCENT_L).clip(CircleShape)
                            .border(2.5.dp, C_ACCENT, CircleShape))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = "正在加载位置…", fontSize = 13.sp, color = C_SUB)
                    }
                }
            }
        }
    }

    internal fun boundingBox(list: List<LocationGroup>): Quad {
        if (list.isEmpty()) return Quad(0.0, 0.0, 0.0, 0.0)
        var minLat = Double.MAX_VALUE; var maxLat = -Double.MAX_VALUE
        var minLng = Double.MAX_VALUE; var maxLng = -Double.MAX_VALUE
        for (g in list) {
            if (!g.hasGps) continue
            minLat = minOf(minLat, g.lat); maxLat = maxOf(maxLat, g.lat)
            minLng = minOf(minLng, g.lng); maxLng = maxOf(maxLng, g.lng)
        }
        if (minLat == Double.MAX_VALUE) return Quad(list.first().lat, list.first().lat, list.first().lng, list.first().lng)
        return Quad(minLat, maxLat, minLng, maxLng)
    }

    data class Quad(val minLat: Double, val maxLat: Double, val minLng: Double, val maxLng: Double)

    data class Cluster(val groups: List<LocationGroup>, val x: Dp, val y: Dp, val count: Int, val label: String, val cover: String)

    internal fun buildClusters(visible: List<LocationGroup>, project: (Double, Double) -> Pair<Dp, Dp>): List<Cluster> {
        if (visible.isEmpty()) return emptyList()
        val pts = visible.map { g -> val (x, y) = project(g.lat, g.lng); Pair(g, x to y) }
        val used = BooleanArray(pts.size)
        val clusters = mutableListOf<Cluster>()
        val R = 74f
        for (i in pts.indices) {
            if (used[i]) continue
            used[i] = true
            val grp = mutableListOf(pts[i])
            for (j in i + 1 until pts.size) {
                if (used[j]) continue
                val dx = (pts[i].second.first - pts[j].second.first).value
                val dy = (pts[i].second.second - pts[j].second.second).value
                if (dx * dx + dy * dy < R * R) { used[j] = true; grp.add(pts[j]) }
            }
            val cx = grp.sumOf { it.second.first.value.toDouble() } / grp.size
            val cy = grp.sumOf { it.second.second.value.toDouble() } / grp.size
            val gs = grp.map { it.first }
            val cover = gs.first().photos.firstOrNull()?.thumb ?: ""
            val label = if (gs.size == 1) gs[0].address else "${gs.size} 个地点"
            clusters.add(Cluster(gs, cx.toFloat().dp, cy.toFloat().dp, gs.sumOf { it.count }, label, cover))
        }
        return clusters
    }

    @Composable
    private fun ApplePin(c: Cluster) {
        val single = c.groups.size == 1
        val g0 = c.groups.first()
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(56.dp)) {
                if (c.count > 1) {
                    Box(modifier = Modifier.fillMaxSize().offset(x = (-2).dp, y = 2.dp).rotate(-7f)
                        .clip(RoundedCornerShape(13.dp)).background(C_WHITE))
                    Box(modifier = Modifier.fillMaxSize().offset(x = 2.dp, y = 1.dp).rotate(6f)
                        .clip(RoundedCornerShape(13.dp)).background(C_WHITE))
                }
                Box(
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(13.dp))
                        .border(3.dp, if (g0.noGps) Color(0xFFCCAAAA) else C_WHITE, RoundedCornerShape(13.dp))
                        .background(if (c.cover.isEmpty()) Color(0xFFDDD7CE) else C_WHITE),
                ) {
                    if (c.cover.isNotEmpty()) {
                        Image(painter = rememberAsyncImagePainter(c.cover), contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(11.dp)), contentScale = com.tencent.kuikly.compose.ui.layout.ContentScale.Crop)
                    }
                }
                if (c.count > 1) {
                    Box(
                        modifier = Modifier.align(Alignment.TopEnd).offset(x = 7.dp, y = (-7).dp)
                            .background(Color(0xFFC99A8E), CircleShape).border(1.5.dp, C_WHITE, CircleShape)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text(text = if (c.count > 99) "99+" else c.count.toString(), fontSize = 10.5.sp, color = C_WHITE, fontWeight = com.tencent.kuikly.compose.ui.text.font.FontWeight.Bold)
                    }
                }
                if (activeSpot.value == g0.address && single) {
                    Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(13.dp))
                        .border(3.dp, C_ACCENT, RoundedCornerShape(13.dp)))
                }
            }
            // tail
            Box(modifier = Modifier.size(10.dp).background(if (g0.noGps) Color(0xFFCCAAAA) else C_WHITE)
                .offset(y = (-4).dp))
            // label
            Box(
                modifier = Modifier.padding(top = 3.dp).background(
                    if (g0.noGps) C_CLAY else if (c.count > 1) C_ACCENT_L else C_CARD,
                    RoundedCornerShape(10.dp),
                ).padding(horizontal = 8.dp, vertical = 2.5.dp).widthIn(max = 96.dp),
            ) {
                Text(text = c.label, fontSize = 10.5.sp, color = if (g0.noGps) C_WHITE else C_TEXT,
                    maxLines = 1, softWrap = false, fontWeight = com.tencent.kuikly.compose.ui.text.font.FontWeight.SemiBold)
            }
        }
    }

    @Composable
    private fun Fab(path: String, onClick: () -> Unit) {
        Box(
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xDCFFFFFF))
                .border(1.dp, Color(0x99FFFFFF), RoundedCornerShape(12.dp)).clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(19.dp)) {
                val u = size.width / 24f; val w = size.width / 14f
                val p = Path()
                path.split("M").forEach { sub ->
                    if (sub.isEmpty()) return@forEach
                    val nums = sub.split("L").flatMap { it.split(" ") }
                        .filter { it.isNotEmpty() && it != "Z" && it != "z" }
                    nums.chunked(2).forEachIndexed { i, pair ->
                        if (pair.size < 2) return@forEachIndexed
                        val x = pair[0].toFloat() * u
                        val y = pair[1].toFloat() * u
                        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                    }
                    if (sub.contains("Z", ignoreCase = true)) p.close()
                }
                drawPath(p, C_TEXT, style = com.tencent.kuikly.compose.ui.graphics.drawscope.Stroke(w))
            }
        }
    }

    @Composable
    private fun SpotRail() {
        val visible = visibleGroups()
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            if (visible.isEmpty()) {
                item { Text(text = "没有匹配的地点", fontSize = 12.5.sp, color = C_SUB, modifier = Modifier.padding(8.dp)) }
            } else {
                items(items = visible) { g ->
                    val isExpanded = activeSpot.value == g.address && sheetOpen.value
                    Row(
                        modifier = Modifier.clip(RoundedCornerShape(14.dp))
                            .background(if (isExpanded) C_ACCENT_L else Color(0x99F5F2EC))
                            .border(1.dp, if (isExpanded) C_ACCENT else Color(0x80FFFFFF), RoundedCornerShape(14.dp))
                            .clickable {
                                // 已展开则折叠返回；未展开则展开详情
                                if (isExpanded) sheetOpen.value = false else openSpot(g)
                            }.padding(start = 6.dp, top = 6.dp, bottom = 6.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp))
                                .background(if (g.photos.firstOrNull()?.thumb?.isEmpty() != false) Color(0xFFDDD7CE) else C_WHITE)
                                .border(1.dp, Color(0x80FFFFFF), RoundedCornerShape(9.dp)),
                        ) {
                            val t = g.photos.firstOrNull()?.thumb ?: ""
                            if (t.isNotEmpty()) {
                                Image(painter = rememberAsyncImagePainter(t), contentDescription = null,
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(9.dp)), contentScale = com.tencent.kuikly.compose.ui.layout.ContentScale.Crop)
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(text = g.address, fontSize = 12.5.sp, color = C_TEXT, fontWeight = com.tencent.kuikly.compose.ui.text.font.FontWeight.SemiBold)
                            Text(text = "${g.lat.cityName(g)} · ${g.count} 张", fontSize = 10.5.sp, color = C_SUB)
                        }
                        // 展开/折叠按钮：展开中显示「折叠 ▲」，点击返回上一层
                        if (isExpanded) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(10.dp))
                                    .background(Color(0x33A88F79))
                                    .clickable { sheetOpen.value = false }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "折叠 ▲", fontSize = 10.sp, color = C_ACCENT)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun GridView(modifier: Modifier = Modifier) {
        val visible = visibleGroups()
        LazyColumn(modifier = modifier.fillMaxWidth().padding(horizontal = 11.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (visible.isEmpty()) {
                item { Text(text = "没有找到符合条件的照片\n试试换个关键词", fontSize = 13.sp, color = C_SUB, modifier = Modifier.fillMaxWidth().padding(50.dp), textAlign = com.tencent.kuikly.compose.ui.text.style.TextAlign.Center) }
            } else {
                items(items = visible) { g ->
                    val collapsed = g.address in collapsedGroups.value
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 4.dp)
                                .clickable { toggleGroup(g.address) }, // 点击整行折叠/展开
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(text = g.address, fontSize = 16.sp, color = C_TEXT, fontWeight = com.tencent.kuikly.compose.ui.text.font.FontWeight.Bold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "${g.count} 张", fontSize = 11.sp, color = C_SUB)
                            Spacer(modifier = Modifier.weight(1f))
                            // 「在地图中显示」：返回地图视图并飞到该地点（规格 4.2）
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(11.dp))
                                    .background(Color(0x26A88F79))
                                    .clickable { view.value = "map"; openSpot(g) }
                                    .padding(horizontal = 9.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "在地图中显示", fontSize = 10.5.sp, color = C_ACCENT)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            // 折叠 / 展开按钮
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(11.dp))
                                    .background(Color(0x1F46413B))
                                    .clickable { toggleGroup(g.address) }
                                    .padding(horizontal = 9.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = if (collapsed) "展开 ▼" else "折叠 ▲", fontSize = 10.5.sp, color = C_SUB)
                            }
                        }
                        if (!collapsed) {
                            Spacer(modifier = Modifier.height(6.dp))
                            val photos = g.photos
                            val perPage = 12 // 4 列 × 3 行
                            val pageCount = (photos.size + perPage - 1) / perPage
                            val gridHeight = 292.dp
                            if (pageCount > 1) {
                                // 大组：3 行 × 4 列（12 张/页）整页横向翻页。
                                // 手动静态网格（无 LazyVerticalGrid 框架开销）：滑动时 pager 只做整页
                                // 合成平移 → 不掉帧。beyondViewportPageCount=1 → 最多当前+前后各1=3 页在途。
                                val pagerState = rememberPagerState(pageCount = { pageCount })
                                HorizontalPager(
                                    state = pagerState,
                                    modifier = Modifier.fillMaxWidth().height(gridHeight),
                                    beyondViewportPageCount = 1,
                                ) { page ->
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        repeat(3) { row ->
                                            Row(
                                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                repeat(4) { col ->
                                                    val idx = page * 12 + row * 4 + col
                                                    if (idx < photos.size) {
                                                        val p = photos[idx]
                                                        val t = p.thumb
                                                        Box(
                                                            modifier = Modifier.weight(1f).fillMaxHeight().padding(2.5.dp)
                                                                .clip(RoundedCornerShape(9.dp)).border(1.dp, Color(0x80FFFFFF), RoundedCornerShape(9.dp))
                                                                .background(Color(0xFFE4DED5)).clickable { openFs(g, p) },
                                                        ) {
                                                            if (t.isNotEmpty()) {
                                                                Image(painter = rememberAsyncImagePainter(t), contentDescription = null,
                                                                    modifier = Modifier.fillMaxSize(), contentScale = com.tencent.kuikly.compose.ui.layout.ContentScale.Crop)
                                                            }
                                                        }
                                                    } else {
                                                        Spacer(modifier = Modifier.weight(1f))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                // 页码指示
                                Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${pagerState.currentPage + 1} / $pageCount 页 · 共 ${photos.size} 张 · 左右滑动翻页",
                                        fontSize = 10.5.sp, color = C_SUB,
                                    )
                                }
                            } else {
                                // ≤12 张：单网格（4 列）
                                val rows = (photos.size + 3) / 4
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(4),
                                    modifier = Modifier.fillMaxWidth().height((rows * 94).dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    gridItems(items = photos) { p ->
                                        val t = p.thumb
                                        Box(
                                            modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(2.5.dp)
                                                .clip(RoundedCornerShape(9.dp)).border(1.dp, Color(0x80FFFFFF), RoundedCornerShape(9.dp))
                                                .background(Color(0xFFE4DED5)).clickable { openFs(g, p) },
                                        ) {
                                            if (t.isNotEmpty()) {
                                                Image(painter = rememberAsyncImagePainter(t), contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(), contentScale = com.tencent.kuikly.compose.ui.layout.ContentScale.Crop)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun openSpot(g: LocationGroup) {
        activeSpot.value = g.address
        fsGroup.value = g
        sheetOpen.value = true
        // 地图定位到该地点（规格 4.1-3：点 Pin easeTo 飞过去；点击底部地址卡片同样生效）
        if (g.hasGps) {
            mapCenterLat.value = g.lat
            mapCenterLng.value = g.lng
            mapZoom.value = maxOf(mapZoom.value, 13)
        }
    }

    /** 把拖动 pan 偏移并入地图中心（px → 经纬度），pan 清零 */
    private fun commitMapPan() {
        val pan = mapPan.value
        if (pan.x == 0f && pan.y == 0f) return
        val z = mapZoom.value
        val n = 2.0.pow(z)
        val xt = (mapCenterLng.value + 180.0) / 360.0 * n + pan.x / TILE_PX
        val yt = (1.0 - asinh(tan(mapCenterLat.value * PI / 180.0)) / PI) / 2.0 * n + pan.y / TILE_PX
        mapCenterLng.value = xt / n * 360.0 - 180.0
        mapCenterLat.value = atan(sinh(PI * (1.0 - 2.0 * yt / n))) * 180.0 / PI
        mapPan.value = Offset.Zero
    }

    /** 两指当前距离（px） */
    private fun dist2(changes: List<PointerInputChange>): Float {
        if (changes.size < 2) return 0f
        val a = changes[0].position
        val b = changes[1].position
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    /** 当前按下指针的平均坐标（px） */
    private fun centroid2(changes: List<PointerInputChange>): Offset {
        var x = 0f
        var y = 0f
        for (c in changes) { x += c.position.x; y += c.position.y }
        val n = changes.size.coerceAtLeast(1)
        return Offset(x / n, y / n)
    }

    /** 以屏幕中心为锚缩放（+/− 按钮） */
    private fun zoomBy(delta: Int) {
        commitMapPan()
        val z = mapZoom.value
        val z1 = (z + delta).coerceIn(3, 19)
        if (z1 == z) return
        val n0 = 2.0.pow(z)
        val n1 = 2.0.pow(z1)
        val cx = mapCenterLng.value
        val cy = mapCenterLat.value
        val cxt = (cx + 180.0) / 360.0 * n0
        val cyt = (1.0 - asinh(tan(cy * PI / 180.0)) / PI) / 2.0 * n0
        mapCenterLng.value = (cxt / n1) * 360.0 - 180.0
        mapCenterLat.value = atan(sinh(PI * (1.0 - 2.0 * cyt / n1))) * 180.0 / PI
        mapZoom.value = z1
    }

    /** 切换网格分组折叠状态（收起/展开照片网格） */
    private fun toggleGroup(addr: String) {
        collapsedGroups.value =
            if (addr in collapsedGroups.value) collapsedGroups.value - addr
            else collapsedGroups.value + addr
    }

    private fun openFs(g: LocationGroup, p: Photo) {
        fsGroup.value = g
        fsPhoto.value = p
        fsOpen.value = true
    }

    @Composable
    private fun DetailSheet() {
        val g = fsGroup.value ?: return
        Box(
            modifier = Modifier.fillMaxSize().background(Color(0x66000000)).clickable { sheetOpen.value = false },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp, 22.dp, 0.dp, 0.dp))
                    .background(C_CARD.copy(alpha = 0.92f)).clickable { }.padding(18.dp),
            ) {
                Box(modifier = Modifier.width(36.dp).height(4.dp).background(Color(0xFFD6CEC3), RoundedCornerShape(2.dp)).align(Alignment.CenterHorizontally))
                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = g.address, fontSize = 19.sp, color = C_TEXT,
                        fontWeight = com.tencent.kuikly.compose.ui.text.font.FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    // 折叠按钮：点击收起返回上一层（方隅地图/网格）
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(14.dp))
                            .background(Color(0x26A88F79))
                            .clickable { sheetOpen.value = false }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "折叠 ▲", fontSize = 11.sp, color = C_ACCENT)
                    }
                }
                val times = g.photos.mapNotNull { it.shootingTime?.takeIf { it.isNotEmpty() } }.sorted()
                val range = when {
                    times.isEmpty() -> "时间未知"
                    times.size == 1 -> times[0]
                    else -> "${times.first()} ~ ${times.last()}"
                }
                Text(text = "${g.count} 张照片 · $range", fontSize = 12.sp, color = C_SUB, modifier = Modifier.padding(top = 3.dp))
                // GPS + 海拔
                Row(
                    modifier = Modifier.fillMaxWidth().padding(9.dp).background(Color(0x80FFFFFF), RoundedCornerShape(11.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "📍", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (g.noGps) "未记录拍摄位置（归入固定坐标）"
                        else "GPS ${fmtGps(g.lat, g.lng)}",
                        fontSize = 11.5.sp, color = C_TEXT,
                    )
                }
                // 照片：3 行 × 4 列横向翻页（参考网格；当页 + 前后各 2 页 = 5 页在途，其余销毁）
                val perPage = 12
                val photos = g.photos
                val pageCount = (photos.size + perPage - 1) / perPage
                if (pageCount > 1) {
                    val sheetPager = rememberPagerState(pageCount = { pageCount })
                    HorizontalPager(
                        state = sheetPager,
                        modifier = Modifier.fillMaxWidth().height(300.dp).padding(top = 13.dp),
                        beyondViewportPageCount = 2,
                    ) { page ->
                        Column(modifier = Modifier.fillMaxSize()) {
                            repeat(3) { row ->
                                Row(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    repeat(4) { col ->
                                        val idx = page * 12 + row * 4 + col
                                        if (idx < photos.size) {
                                            val p = photos[idx]
                                            val t = p.thumb
                                            Box(
                                                modifier = Modifier.weight(1f).fillMaxHeight().padding(2.dp)
                                                    .clip(RoundedCornerShape(9.dp))
                                                    .background(Color(0xFFE4DED5)).clickable { openFs(g, p) },
                                            ) {
                                                if (t.isNotEmpty()) {
                                                    Image(painter = rememberAsyncImagePainter(t), contentDescription = null,
                                                        modifier = Modifier.fillMaxSize(), contentScale = com.tencent.kuikly.compose.ui.layout.ContentScale.Crop)
                                                }
                                            }
                                        } else {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 5.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "${sheetPager.currentPage + 1} / $pageCount 页 · 左右滑动翻页",
                            fontSize = 10.5.sp, color = C_SUB,
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.fillMaxWidth().height((((photos.size + 3) / 4) * 90).dp).padding(top = 13.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        gridItems(items = photos) { p ->
                            val t = p.thumb
                            Box(
                                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(9.dp))
                                    .background(Color(0xFFE4DED5)).clickable { openFs(g, p) },
                            ) {
                                if (t.isNotEmpty()) {
                                    Image(painter = rememberAsyncImagePainter(t), contentDescription = null,
                                        modifier = Modifier.fillMaxSize(), contentScale = com.tencent.kuikly.compose.ui.layout.ContentScale.Crop)
                                }
                            }
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 15.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(C_ACCENT).padding(vertical = 11.dp)
                            .clickable { showToast("已生成「${g.address}」地点回忆（原型占位）") },
                        contentAlignment = Alignment.Center,
                    ) { Text(text = "生成回忆", fontSize = 14.sp, color = C_WHITE, fontWeight = com.tencent.kuikly.compose.ui.text.font.FontWeight.SemiBold) }
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Color(0x73FFFFFF)).border(1.dp, Color(0x80FFFFFF), RoundedCornerShape(12.dp)).padding(vertical = 11.dp)
                            .clickable { showToast("调起导航到 ${g.address}（原型占位）") },
                        contentAlignment = Alignment.Center,
                    ) { Text(text = "导航到这", fontSize = 14.sp, color = C_ACCENT, fontWeight = com.tencent.kuikly.compose.ui.text.font.FontWeight.SemiBold) }
                }
            }
        }
    }

    @Composable
    private fun FullscreenViewer() {
        val g = fsGroup.value
        val p = fsPhoto.value ?: return
        Box(
            modifier = Modifier.fillMaxSize().background(Color(0xF0191513)).clickable { fsOpen.value = false },
            contentAlignment = Alignment.Center,
        ) {
            if (p.thumb.isNotEmpty()) {
                Image(painter = rememberAsyncImagePainter(p.thumb), contentDescription = null,
                    modifier = Modifier.fillMaxWidth().fillMaxSize(0.66f), contentScale = com.tencent.kuikly.compose.ui.layout.ContentScale.Fit)
            }
            Box(
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(34.dp).background(Color(0x33FFFFFF), CircleShape)
                    .clickable { fsOpen.value = false }, contentAlignment = Alignment.Center,
            ) { Text(text = "✕", color = C_WHITE, fontSize = 17.sp) }
            Column(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().background(
                    com.tencent.kuikly.compose.ui.graphics.Brush.verticalGradient(0f to Color.Transparent, 1f to Color(0xB3000000)),
                ).padding(20.dp, 30.dp),
            ) {
                Text(text = g?.address ?: "", fontSize = 15.sp, color = C_WHITE, fontWeight = com.tencent.kuikly.compose.ui.text.font.FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(11.dp))
                Text(text = "拍摄地点：${g?.address ?: "—"}", fontSize = 12.5.sp, color = C_WHITE)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "拍摄时间：${p.shootingTime ?: "—"}", fontSize = 12.5.sp, color = C_WHITE)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "GPS 坐标：${if (p.hasGps && p.lat != null && p.lng != null) fmtGps(p.lat, p.lng) else "未记录"}", fontSize = 12.5.sp, color = C_WHITE)
            }
        }
    }

    internal fun fmtGps(lat: Double, lng: Double): String {
        val ns = if (lat >= 0) "N" else "S"
        val ew = if (lng >= 0) "E" else "W"
        return "${fmtFixed4(kotlin.math.abs(lat))}°$ns, ${fmtFixed4(kotlin.math.abs(lng))}°$ew"
    }

    /** 纯 Kotlin 跨平台：等价于 JVM 的 String.format("%.4f")，四舍五入并固定保留 4 位小数（不足补零）。 */
    private fun fmtFixed4(v: Double): String {
        val scaled = kotlin.math.round(v * 10000.0) // Long
        val whole = scaled / 10000
        val frac = scaled % 10000
        return "$whole.${frac.toString().padStart(4, '0')}"
    }

    @Composable
    private fun BottomNavBar() {
        Row(
            modifier = Modifier.fillMaxWidth().background(C_NAV_BG).padding(vertical = 10.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            NavItem(NavIconName.HOME, "首页", false) { goPage("Home") }
            NavItem(NavIconName.YEAR, "流年", false) { goPage("YearFlow") }
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(C_CLAY).clickable { goPage("Upload") },
                contentAlignment = Alignment.Center,
            ) { NavIcon(NavIconName.PLUS, C_WHITE, 26.dp) }
            NavItem(NavIconName.PLACE, "方隅", true) {}
            NavItem(NavIconName.VIDEO, "视频", false) { goPage("Video") }
        }
    }

    @Composable
    private fun RowScope.NavItem(icon: NavIconName, label: String, active: Boolean, onClick: () -> Unit) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable { onClick() }.weight(1f),
        ) {
            NavIcon(icon, if (active) C_CLAY else C_SUB, 23.dp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = label, fontSize = 11.sp, color = if (active) C_TEXT else C_SUB)
        }
    }

    @Composable
    private fun Toast() {
        if (toastMsg.value.isNotEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(bottom = 96.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Box(modifier = Modifier.background(Color(0xED46413B), RoundedCornerShape(20.dp)).padding(horizontal = 18.dp, vertical = 10.dp)) {
                    Text(text = toastMsg.value, fontSize = 13.sp, color = C_WHITE)
                }
            }
        }
    }
}

/**
 * 方隅：分组无独立 city 字段，用地址本身（含城市/区/· 时取首段）展示。
 * 抽成顶层扩展，便于单测直接调用（不依赖页面实例）。
 */
internal fun Double.cityName(g: PlacePage.LocationGroup): String {
    val a = g.address
    return if (a.contains("·") || a.contains("区") || a.contains("市")) a else g.address
}
