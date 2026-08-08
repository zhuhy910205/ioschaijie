package com.chaijie.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.tencent.kuikly.compose.ComposeContainer
import com.tencent.kuikly.compose.coil3.rememberAsyncImagePainter
import com.tencent.kuikly.compose.foundation.Canvas
import com.tencent.kuikly.compose.foundation.Image
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.border
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.gestures.detectDragGestures
import com.tencent.kuikly.compose.foundation.gestures.detectTapGestures
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.BoxScope
import com.tencent.kuikly.compose.foundation.layout.BoxWithConstraints
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.ColumnScope
import com.tencent.kuikly.compose.foundation.layout.PaddingValues
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.RowScope
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxHeight
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.offset
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.lazy.LazyRow
import com.tencent.kuikly.compose.foundation.lazy.items
import com.tencent.kuikly.compose.foundation.shape.CircleShape
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.setContent
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.alpha
import com.tencent.kuikly.compose.ui.draw.clip
import com.tencent.kuikly.compose.ui.draw.rotate
import com.tencent.kuikly.compose.ui.geometry.Offset
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.graphics.drawscope.Stroke
import com.tencent.kuikly.compose.ui.input.pointer.pointerInput
import com.tencent.kuikly.compose.ui.layout.ContentScale
import com.tencent.kuikly.compose.ui.unit.Dp
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.compose.animation.core.InfiniteRepeatableSpec
import com.tencent.kuikly.compose.animation.core.RepeatMode
import com.tencent.kuikly.compose.animation.core.TweenSpec
import com.tencent.kuikly.compose.animation.core.animateFloat
import com.tencent.kuikly.compose.animation.core.rememberInfiniteTransition
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.delay

/**
 * 流年页（底部导航第 2 个 Tab）。
 * 参考原型 yearflow_v1.html + 规格 yearflow_SPEC.md；数据接口参考 chaijie 的
 * app/routes/timeline.py（GET /api/timeline/images）与 static/js/timeline.js。
 *
 * 说明：
 * - 真实接口不返回原型的 title/loc 字段，标题用 photo_fts.summary（description），地点暂缺。
 * - "那年今日"需要"今天"的月日；commonMain 无 kotlinx.datetime，故以相册中最新日期作为基准日
 *   （refYear / refMD），语义等价于"最近一次拍照的这天，往前回溯 N 年"。
 * - 圆环用 Compose 布局绘制（box+border+绝对定位），不用 SVG/Canvas，避免平台差异。
 * - 脉冲呼吸、虚线岁月环、四季粒子为简化实现（季节色 / 节气名胶囊 / emoji 粒子均按规格呈现）。
 */
@Page("YearFlow")
internal class YearFlowPage : ComposeContainer() {

    private val photos = mutableStateOf<List<YfPhoto>>(emptyList())
    private val isLoading = mutableStateOf(true)
    private val loadError = mutableStateOf("")

    private val refYear = mutableStateOf(2026)
    private val refMD = mutableStateOf("08-06")

    private val otdPhoto = mutableStateOf<YfPhoto?>(null)
    private val otdYear = mutableStateOf(2026)
    private val otdRandom = mutableStateOf(false)
    private val otdTag = mutableStateOf("")
    private val otdTitle = mutableStateOf("")
    private val otdMeta = mutableStateOf("")

    private val playIdx = mutableStateOf(0)
    /** 拖动预览角度（度，0~360，null = 未在拖动）。拖动时只更新此轻量状态（仅活跃点跟随），松手才 commit 到 playIdx */
    private val dragAngle = mutableStateOf<Float?>(null)
    private val playing = mutableStateOf(true)
    private val yearFilter = mutableStateOf("all")

    private val lastSolarIdx = mutableStateOf(-1)
    private val fxSeason = mutableStateOf("")
    private val fxTag = mutableStateOf("")
    private val fxTagVisible = mutableStateOf(false)
    private val fxTagKey = mutableStateOf(0)
    /** 粒子特效层可见标志：触发后 5.6s 自动清除，避免无限动画泄漏拖慢性能 */
    private val fxFxVisible = mutableStateOf(false)

    private val fullscreenUrl = mutableStateOf("")
    private val toastText = mutableStateOf("")

    companion object {
        private val C_BG = Color(0xFFE9E5DE)
        private val C_CARD = Color(0xFFF5F2EC)
        private val C_TEXT = Color(0xFF46413B)
        private val C_SUB = Color(0xFF968D83)
        private val C_ACCENT = Color(0xFFA88F79)
        private val C_CLAY = Color(0xFFB07D6B)
        private val C_ACCENT_L = Color(0xFFEAE1D8)
        private val C_NAV_BG = Color(0xEBF5F2EC)
        private val C_RING_OUTER = Color(0x40968D83)
        private val C_DOT = Color(0xFFC99A8E)
        private val C_WHITE = Color(0xFFFFFFFF)

        // ================= 可调参数（自己调好数值发我，我同步进代码） =================
        /** 中央大图直径占圆盘的比例（0.72 = 72%）。圆点环紧贴中央图外圈 */
        private val CENTER_IMG_RATIO = 0.72f
        /** 点环半径比例（相对圆盘半径）：中央图半径 + 余量，圆点贴在中央图外圈 */
        private val DOT_RING_RATIO = CENTER_IMG_RATIO / 2f + 0.025f
        /** 普通圆点半径（px）。照片 >20 张时用 MANY，否则用 FEW（已缩小至原 30%） */
        private val DOT_R_MANY = 4.5f
        private val DOT_R_FEW = 6.3f
        /** 播放中活跃点直径（dp）（已缩小至原 30%） */
        private val HOT_DOT_SIZE = 16.dp
        /** 顶部 OtdCard 行高度（dp）—— 照片越大这个值越大（同时需同步调小 RING_SECTION_HEIGHT） */
        private val OTD_ROW_HEIGHT = 220.dp
        /** 圆盘卡片高度（dp）—— 越大圆盘越大（但会被底部导航压缩） */
        private val RING_SECTION_HEIGHT = 440.dp
        /** 年份 chip 文字大小（sp） */
        private val CHIP_FONT = 14.sp
        /** 年份 chip 垂直间距（dp） */
        private val CHIP_SPACING = 8.dp
    }

    override fun willInit() {
        super.willInit()
        setContent { YearFlowContent() }
    }

    override fun created() {
        super.created()
        loadData()
    }

    // ===== 数据加载（分批：先显示第一页立即可用，后台并发补全剩余页） =====
    private fun loadData() {
        isLoading.value = true
        fetchPage(1) { first, total, hasMore ->
            val pagesNeeded = if (total > 0) (total + 99) / 100 else if (hasMore) 6 else 1
            // 首屏：立即显示第一页（100 张），页面 <1s 可交互
            finishLoad(first, resetIdx = true)
            // 后台：并发拉剩余页补全到全量
            if (pagesNeeded > 1) {
                val merged = first.toMutableList()
                var pending = pagesNeeded - 1
                for (p in 2..pagesNeeded) {
                    fetchPage(p) { arr, _, _ ->
                        merged.addAll(arr)
                        pending--
                        if (pending == 0) finishLoad(merged, resetIdx = false)
                    }
                }
            }
        }
    }

    private fun fetchPage(page: Int, cb: (List<JSONObject>, total: Int, hasMore: Boolean) -> Unit) {
        val url = "${ApiConfig.CHAIJIE_BASE}/api/timeline/images?page=$page&limit=100"
        getJson(url) { success, data ->
            if (!success) {
                loadError.value = "加载失败，请检查网络"
                isLoading.value = false
                return@getJson
            }
            val arr = data.optJSONArray("images") ?: data.optJSONArray("photos")
            val list = if (arr != null) (0 until arr.length()).mapNotNull { arr.optJSONObject(it) } else emptyList()
            cb(list, data.optInt("total", 0), data.optBoolean("has_more", false))
        }
    }

    private fun finishLoad(all: List<JSONObject>, resetIdx: Boolean) {
        photos.value = all.map { parseYf(it) }
        isLoading.value = false
        sortedCacheKey = "" // 强制重算排序缓存
        // 以相册中最新日期作为"今天"基准
        val maxDate = photos.value.maxOfOrNull { it.dateStr }
        if (maxDate != null && maxDate.length >= 4) {
            refYear.value = maxDate.substring(0, 4).toIntOrNull() ?: 2026
            refMD.value = maxDate.substring(5, 10) // MM-DD
        }
        computeOTD(refYear.value - 1)
        // 首屏重置到第 0 张；后台补全时保留当前位置
        if (resetIdx) playIdx.value = 0 else {
            val n = currentList().size
            if (n > 0) playIdx.value = playIdx.value.coerceIn(0, n - 1)
        }
    }

    // 排序缓存：避免每次 recompose 都对几百张照片重新排序
    private var sortedCache: List<YfPhoto>? = null
    private var sortedCacheKey: String = ""
    private fun currentList(): List<YfPhoto> {
        val key = "${photos.value.size}:${yearFilter.value}"
        if (sortedCacheKey != key) {
            val base = photos.value
            val filtered = if (yearFilter.value == "all") base
            else base.filter { it.year == (yearFilter.value.toIntOrNull() ?: -1) }
            sortedCache = filtered.sortedBy { it.dateStr }
            sortedCacheKey = key
        }
        return sortedCache ?: emptyList()
    }

    private fun step(delta: Int) {
        val n = currentList().size
        if (n == 0) return
        playIdx.value = ((playIdx.value + delta) % n + n) % n
    }

    /** 归一化角度到 [0, 360) */
    private fun normalizeDeg(deg: Double): Double {
        var d = deg
        while (d < 0.0) d += 360.0
        while (d >= 360.0) d -= 360.0
        return d
    }

    /** 拖动结束：把预览角度 commit 成正式播放索引，并退出预览模式 */
    private fun commitDrag() {
        val d = dragAngle.value ?: return
        val n = currentList().size
        if (n > 0) {
            val idx = ((d / 360.0) * n).toInt().coerceIn(0, n - 1)
            if (idx != playIdx.value) playIdx.value = idx
        }
        dragAngle.value = null
    }

    // ===== 那年今日 =====
    private fun computeOTD(startYear: Int) {
        val md = refMD.value
        var found: YfPhoto? = null
        var y = startYear
        while (y >= refYear.value - 5) {
            val hit = photos.value.firstOrNull { it.dateStr.length >= 10 && it.dateStr.substring(5, 10) == md && it.year == y }
            if (hit != null) { found = hit; break }
            y--
        }
        if (found != null) {
            otdPhoto.value = found
            otdYear.value = found.year
            otdRandom.value = false
        } else {
            otdPhoto.value = photos.value.randomOrNull()
            otdYear.value = refYear.value - 5
            otdRandom.value = true
        }
        updateOtdText()
    }

    private fun updateOtdText() {
        val p = otdPhoto.value ?: return
        otdTitle.value = p.title
        otdMeta.value = "${p.year}年${p.month}月${p.day}日"
        otdTag.value = when {
            otdRandom.value -> "今日无旧照 · 随机回忆"
            p.year == refYear.value - 1 -> "去年今日 · ${refMD.value}"
            else -> "${refYear.value - p.year} 年前的今天"
        }
    }

    private fun goBackYear() {
        if (otdYear.value <= refYear.value - 5) {
            showToast("已是最早可回溯的年份")
            return
        }
        computeOTD(otdYear.value - 1)
    }

    // ===== 节气特效触发 =====
    private fun triggerFxIfNeeded() {
        val p = currentList().getOrNull(playIdx.value) ?: return
        val idx = solarIdx(p.month, p.day)
        if (idx != lastSolarIdx.value) {
            lastSolarIdx.value = idx
            fxSeason.value = seasonOfSolar(idx)
            fxTag.value = SOLAR_NAME[idx]
            fxTagVisible.value = true
            fxTagKey.value++
            fxFxVisible.value = true // 粒子层出现，5.6s 后由 LaunchedEffect 清除
        }
    }

    private fun openFullscreen(p: YfPhoto) {
        fullscreenUrl.value = if (p.original.isNotEmpty()) p.original else p.thumb
    }

    private fun showToast(msg: String) {
        toastText.value = msg
    }

    private fun goPage(name: String) {
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(name, null)
    }

    // ===== UI =====
    @Composable
    private fun YearFlowContent() {
        // 自动轮播
        LaunchedEffect(playing.value) {
            if (!playing.value) return@LaunchedEffect
            while (true) {
                delay(3000)
                step(1)
            }
        }
        // 切换照片时触发节气特效
        LaunchedEffect(playIdx.value) { triggerFxIfNeeded() }
        // 节气名胶囊自动隐藏
        LaunchedEffect(fxTagKey.value) {
            if (fxTagKey.value == 0) return@LaunchedEffect
            delay(2800)
            fxTagVisible.value = false
        }
        // 粒子特效层 5.6s 后自动清除（防止无限动画拖慢）
        LaunchedEffect(fxFxVisible.value) {
            if (!fxFxVisible.value) return@LaunchedEffect
            delay(5600)
            fxFxVisible.value = false
        }
        // toast 自动隐藏
        LaunchedEffect(toastText.value) {
            if (toastText.value.isNotEmpty()) {
                delay(1600)
                toastText.value = ""
            }
        }

        Box(modifier = Modifier.fillMaxSize().background(C_BG)) {
            Column(modifier = Modifier.fillMaxSize().padding(top = pageData.statusBarHeight.dp, bottom = 34.dp)) {
                // 顶部整块 Header（返回/流年标题/日历）已按用户要求删除，照片区直接顶到状态栏下方
                // 顶部第一行：OtdCard（左侧 3/4 宽，高度 220dp 放大）+ 年份 chips（右侧 1/4 窄条竖排）
                Row(
                    modifier = Modifier.fillMaxWidth().height(OTD_ROW_HEIGHT).padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(3f).fillMaxHeight()) { OtdCard() }
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) { Chips() }
                }
                RingSection()
                // 把 BottomNavBar 推到底部（避开 home indicator 已由上方 bottom=34.dp 处理）
                Spacer(modifier = Modifier.weight(1f))
                BottomNavBar()
            }
            if (fullscreenUrl.value.isNotEmpty()) FullscreenOverlay()
            if (toastText.value.isNotEmpty()) {
                Box(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 92.dp)
                        .clip(RoundedCornerShape(20.dp)).background(Color(0xE646413B)).padding(horizontal = 18.dp, vertical = 10.dp)
                ) { Text(text = toastText.value, color = C_WHITE, fontSize = 13.sp) }
            }
        }
    }

    @Composable
    private fun Header() {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp, 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape).background(Color(0xA6F5F2EC)).clickable { goPage("Home") },
                contentAlignment = Alignment.Center
            ) { Text(text = "‹", color = C_TEXT, fontSize = 22.sp) }
            Text(text = "流年", fontSize = 17.sp, color = C_TEXT, letterSpacing = 3.sp)
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape).background(Color(0xA6F5F2EC)).clickable { showToast("日历（占位）") },
                contentAlignment = Alignment.Center
            ) { Text(text = "▦", color = C_TEXT, fontSize = 18.sp) }
        }
    }

    @Composable
    private fun OtdCard(modifier: Modifier = Modifier) {
        val p = otdPhoto.value
        // 整体缩小 1/3：去掉标题/日期行，只保留图片本身（外层 Box 由调用处控制尺寸）
        Box(modifier = modifier.clip(RoundedCornerShape(18.dp)).background(C_CARD)) {
            if (p != null) {
                Image(
                    painter = rememberAsyncImagePainter(p.thumb),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            // 顶角标签
            Box(
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
                    .clip(RoundedCornerShape(15.dp)).background(Color(0x8C46413B)).padding(horizontal = 10.dp, vertical = 5.dp)
            ) { Text(text = otdTag.value, color = C_WHITE, fontSize = 11.sp) }
            // 右上「再往前」
            Box(
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)
                    .clip(RoundedCornerShape(15.dp)).background(Color(0xD9FFFFFF)).clickable { goBackYear() }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) { Text(text = "再往前", color = C_TEXT, fontSize = 11.sp) }
        }
    }

    @Composable
    private fun RingSection() {
        val list = currentList()
        // 固定高度毛玻璃卡片（460dp 更高，padding 收窄让圆盘更大；chips 已移到顶部，无"时光轴"标题）
        Box(
            modifier = Modifier.fillMaxWidth().height(RING_SECTION_HEIGHT)
                .padding(horizontal = 12.dp)
                .padding(top = 2.dp, bottom = 8.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0x99F5F2EC))
                .border(1.dp, Color(0x80FFFFFF), RoundedCornerShape(18.dp))
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 圆盘（多重圆）—— 上移贴顶部，给底部信息框留空间；高度固定 = 卡片高 - InfoCard
                BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    val availH = (maxHeight - 6.dp).value
                    val side = min(maxWidth.value, availH)
                    val ring = max(side, 120f)
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        Ring(ring = ring.dp, list = list)
                    }
                }
                // 底部照片介绍信息框（从数据库读取的 description + 日期）
                InfoCard()
            }
            // 节气名胶囊：悬浮覆盖在卡片左上角（不占布局流 → 显示与否都不影响圆盘大小/位置）
            if (fxTagVisible.value) {
                Box(
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                        .clip(RoundedCornerShape(15.dp)).background(Color(0x9C46413B))
                        .padding(horizontal = 14.dp, vertical = 5.dp)
                ) { Text(text = fxTag.value, color = C_WHITE, fontSize = 12.sp) }
            }
        }
    }

    @Composable
    private fun InfoCard() {
        val list = currentList()
        val p = list.getOrNull(playIdx.value)
        // 底部信息框：当前播放照片的介绍（后端从数据库读取的 description）+ 日期
        Column(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xE6FFFFFF))
                .border(1.dp, Color(0x80FFFFFF), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Text(
                text = p?.title?.ifEmpty { "这张照片还没有介绍" } ?: "加载照片介绍中…",
                fontSize = 13.sp,
                color = C_TEXT,
                maxLines = 2
            )
            if (p != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "📷 ${p.dateStr}", fontSize = 11.sp, color = C_SUB)
            }
        }
    }

    @Composable
    private fun BoxScope.Ring(ring: Dp, list: List<YfPhoto>) {
        val n = list.size
        Box(
            modifier = Modifier.size(ring).align(Alignment.Center)
                // 点击：中央圆内 → 全屏；外环 → 命中圆点跳转（不依赖 playIdx，避免拖动时重启手势）
                .pointerInput(list.size) {
                    detectTapGestures { tap ->
                        if (n == 0) return@detectTapGestures
                        val cxp = size.width / 2f
                        val cyp = size.height / 2f
                        val distCenter = sqrt((tap.x - cxp) * (tap.x - cxp) + (tap.y - cyp) * (tap.y - cyp))
                        // 中央圆内 → 打开全屏（命中半径 = CENTER_IMG_RATIO 对应半径 + 余量）
                        if (distCenter < size.width / 2f * (CENTER_IMG_RATIO / 2f + 0.03f)) {
                            list.getOrNull(playIdx.value)?.let { openFullscreen(it) }
                            return@detectTapGestures
                        }
                        // 外环 → 命中最近的圆点
                        val dotR = DOT_RING_RATIO * (size.width / 2f)
                        var best = -1
                        var bestD = Float.MAX_VALUE
                        for (i in 0 until n) {
                            val a = i.toFloat() / n * 360f - 90f
                            val rad = a * PI / 180.0
                            val x = cxp + dotR * cos(rad).toFloat()
                            val y = cyp + dotR * sin(rad).toFloat()
                            val d = sqrt((tap.x - x) * (tap.x - x) + (tap.y - y) * (tap.y - y))
                            if (d < bestD) { bestD = d; best = i }
                        }
                        if (best >= 0 && bestD < 65f) playIdx.value = best
                    }
                }
                // 拖动：绕外圆滑动 = 快进/快退。拖动中只更新轻量 dragAngle（仅活跃点跟随，
                // 中央图/Canvas/InfoCard 零重组），松手才 commit 到 playIdx —— 消除拖动卡顿
                .pointerInput(list.size) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            if (n == 0) return@detectDragGestures
                            val dx = pos.x - size.width / 2f
                            val dy = pos.y - size.height / 2f
                            dragAngle.value = normalizeDeg(atan2(dx, -dy) * 180.0 / PI).toFloat()
                        },
                        onDrag = { change, _ ->
                            if (n == 0) return@detectDragGestures
                            change.consume()
                            val dx = change.position.x - size.width / 2f
                            val dy = change.position.y - size.height / 2f
                            dragAngle.value = normalizeDeg(atan2(dx, -dy) * 180.0 / PI).toFloat()
                        },
                        onDragEnd = { commitDrag() },
                        onDragCancel = { commitDrag() }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // 圆盘底盘（淡米色毛玻璃）+ 最外圈(r=156) + 虚线岁月环(r=128)，均按原型 360 viewBox 等比缩放
            Box(modifier = Modifier.size(ring * (312f / 360f)).background(Color(0x66F5F2EC), CircleShape))
            Box(modifier = Modifier.size(ring * (312f / 360f)).border(1.dp, C_RING_OUTER, CircleShape))
            // 虚线岁月环(r=128)已按用户要求移除
            val curMonth = list.getOrNull(playIdx.value)?.month ?: 0
            for (m in 1..12) Tick(m, ring, curMonth)
            // 月份标签已按用户要求移除
            // 照片点环：Canvas 一次性绘制所有点（性能关键：550 个 Box → 1 个 Canvas）。
            // 不读取任何可变状态（list 为普通参数），拖动/切片时零重绘，避免卡顿
            Canvas(
                modifier = Modifier.size(ring).align(Alignment.Center)
            ) {
                val dotR = DOT_RING_RATIO * (size.width / 2f)
                // 圆点半径（可调参数 DOT_R_MANY / DOT_R_FEW）
                val r = if (n > 20) DOT_R_MANY else DOT_R_FEW
                val cx = size.width / 2f
                val cy = size.height / 2f
                for (i in 0 until n) {
                    val a = i.toFloat() / n * 360f - 90f
                    val rad = a * PI / 180.0
                    val x = cx + dotR * cos(rad).toFloat()
                    val y = cy + dotR * sin(rad).toFloat()
                    drawCircle(color = Color(0xCCC99A8E), radius = r, center = Offset(x, y))
                    drawCircle(color = C_WHITE, radius = r, center = Offset(x, y), style = Stroke(width = 1.2f))
                }
            }
            // 播放中的点（紧贴最外环 r=156）+ 拖动手柄视觉（半透明环 + ◁▷ 提示可拖动快进）
            // 拖动预览中：活跃点直接跟随 dragAngle（仅此处重组，代价极小）
            if (n > 0 && playIdx.value in 0 until n) {
                val previewDeg = dragAngle.value
                val aDeg = if (previewDeg != null) {
                    previewDeg - 90f  // dragAngle 以 12 点为 0° 顺时针，换算成 canvas 角度
                } else {
                    val i = playIdx.value
                    i.toFloat() / n * 360f - 90f
                }
                val rad = aDeg * PI / 180.0
                val dotRd = (156f / 180f) * (ring.value / 2f) // 最外环半径
                val hx = ring.value / 2f + dotRd * cos(rad).toFloat()
                val hy = ring.value / 2f + dotRd * sin(rad).toFloat()
                // 外层半透明手柄环（直径 HOT_DOT_SIZE * 2.5，带 ◁ ▷ 箭头）
                Box(
                    modifier = Modifier.align(Alignment.TopStart)
                        .offset(x = (hx - HOT_DOT_SIZE.value * 1.25f).dp, y = (hy - HOT_DOT_SIZE.value * 1.25f).dp)
                        .size(HOT_DOT_SIZE * 2.5f).clip(CircleShape)
                        .background(Color(0x38B07D6B)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "◁", modifier = Modifier.align(Alignment.CenterStart).padding(start = 3.dp), color = Color(0xCCFFFFFF), fontSize = 11.sp)
                    Text(text = "▷", modifier = Modifier.align(Alignment.CenterEnd).padding(end = 3.dp), color = Color(0xCCFFFFFF), fontSize = 11.sp)
                }
                // 中心棕色圆点
                Box(
                    modifier = Modifier.align(Alignment.TopStart)
                        .offset(x = (hx - HOT_DOT_SIZE.value / 2).dp, y = (hy - HOT_DOT_SIZE.value / 2).dp)
                        .size(HOT_DOT_SIZE).clip(CircleShape).background(C_CLAY)
                        .border(2.dp, C_WHITE, CircleShape)
                )
            }
            CenterImage(ring, list)
            if (fxSeason.value.isNotEmpty() && fxFxVisible.value) FxLayer(ring, fxSeason.value)
        }
    }

    @Composable
    private fun BoxScope.Tick(m: Int, ring: Dp, curMonth: Int) {
        val aDeg = (m - 1) / 12f * 360f - 90f
        val rad = aDeg * PI / 180.0
        val rMid = (if (m % 3 == 0) 146 else 147).toFloat() / 180f * (ring.value / 2f)
        val cx = ring.value / 2f
        val cy = ring.value / 2f
        val x = cx + rMid * cos(rad)
        val y = cy + rMid * sin(rad)
        val len = (if (m % 3 == 0) 12 else 8).dp
        val w = 2.dp
        val hot = m == curMonth
        val col = if (hot) C_CLAY else seasonColor(m)
        Box(
            modifier = Modifier.align(Alignment.TopStart)
                .offset(x = (x - len.value / 2).dp, y = (y - w.value / 2).dp)
                .size(len, w).rotate(aDeg).background(col)
        )
    }

    @Composable
    private fun BoxScope.Label(m: Int, ring: Dp, curMonth: Int) {
        val aDeg = (m - 1) / 12f * 360f - 90f
        val rad = aDeg * PI / 180.0
        val r = 163f / 180f * (ring.value / 2f)
        val cx = ring.value / 2f
        val cy = ring.value / 2f
        val x = cx + r * cos(rad)
        val y = cy + r * sin(rad)
        val hot = m == curMonth
        val fs = if (hot) 11.5.sp else 10.sp
        Box(
            modifier = Modifier.align(Alignment.TopStart)
                .offset(x = (x - 12).dp, y = (y - 8).dp).rotate(aDeg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${m}月",
                fontSize = fs,
                color = if (hot) C_CLAY else seasonColor(m)
            )
        }
    }

    @Composable
    private fun BoxScope.CenterImage(ring: Dp, list: List<YfPhoto>) {
        val p = list.getOrNull(playIdx.value)
        val target = p?.thumb ?: ""
        // 中央大图直径 = 圆盘 × CENTER_IMG_RATIO（可调参数，见 Companion）
        val c = ring * CENTER_IMG_RATIO
        Box(
            modifier = Modifier.align(Alignment.Center).size(c).clip(CircleShape)
                .background(C_BG) // 稳定底色：加载瞬间不露出圆盘背景（防闪）
                .border(3.dp, C_WHITE, CircleShape)
        ) {
            if (target.isNotEmpty()) {
                // 直接按当前照片加载（不做双缓冲——KuiklyPainter 复用已 Success painter 时
                // onSuccess 不触发，双缓冲会导致中央图卡死不动）
                Image(
                    painter = rememberAsyncImagePainter(target),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                modifier = Modifier.align(Alignment.BottomCenter)
                    .background(Color(0x99000000)).padding(horizontal = 8.dp, vertical = 3.dp)
                    .clip(RoundedCornerShape(9.dp))
            ) { Text(text = p?.dateStr ?: "", color = C_WHITE, fontSize = 10.sp) }
        }
        // 预加载前后各一张（1dp + 全透明，仅触发底层 Glide 内存/磁盘缓存）：
        // 切换/拖动 commit 到下一张时目标图已缓存 → 解码几乎瞬时 → 无感切换不闪背景
        val prev = list.getOrNull(playIdx.value - 1)
        val next = list.getOrNull(playIdx.value + 1)
        listOfNotNull(prev, next).forEach { np ->
            Image(
                painter = rememberAsyncImagePainter(np.thumb),
                contentDescription = null,
                modifier = Modifier.size(1.dp).alpha(0f)
            )
        }
    }

    @Composable
    private fun BoxScope.FxLayer(ring: Dp, season: String) {
        Box(modifier = Modifier.size(ring).align(Alignment.Center).clip(CircleShape)) {
            val cnt = fxCount(season)
            val emoji = fxEmoji(season)
            for (k in 0 until cnt) Particle(emoji, ring, k)
        }
    }

    @Composable
    private fun BoxScope.Particle(emoji: String, ring: Dp, seed: Int) {
        val trans = rememberInfiniteTransition(label = "p$seed")
        val frac by trans.animateFloat(
            0f, 1f,
            InfiniteRepeatableSpec(TweenSpec<Float>(2000 + (seed * 137) % 1400), RepeatMode.Restart),
            label = "y"
        )
        val xPct = ((seed * 53) % 100).toFloat()
        Box(
            modifier = Modifier.align(Alignment.TopStart)
                .padding(start = (xPct / 100f * ring.value).dp, top = (frac * ring.value).dp)
                .size(18.dp),
            contentAlignment = Alignment.Center
        ) { Text(text = emoji, fontSize = 16.sp) }
    }

    @Composable
    private fun Chips() {
        val years = photos.value.map { it.year }.distinct().sortedDescending()
        // 竖排年份筛选（再放大 1.1 倍：文字 14sp + 间距 8dp + padding 7dp）
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CHIP_SPACING)
        ) {
            YearChip("全部", yearFilter.value == "all") {
                yearFilter.value = "all"
                playIdx.value = 0
            }
            years.forEach { y ->
                YearChip(y.toString(), yearFilter.value == y.toString()) {
                    yearFilter.value = y.toString()
                    playIdx.value = 0
                }
            }
        }
    }

    @Composable
    private fun YearChip(label: String, active: Boolean, onClick: () -> Unit) {
        Box(modifier = Modifier.clickable { onClick() }) {
            Box(
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
                    .background(if (active) C_ACCENT else Color(0x80FFFFFF))
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) { Text(text = label, fontSize = CHIP_FONT, color = if (active) C_WHITE else C_SUB) }
        }
    }

    @Composable
    private fun BottomNavBar() {
        Row(
            modifier = Modifier.fillMaxWidth().background(C_NAV_BG).padding(vertical = 10.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            NavItem(NavIconName.HOME, "首页", false) { goPage("Home") }
            NavItem(NavIconName.YEAR, "流年", true) {}
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(C_CLAY)
                    .clickable { goPage("Upload") },
                contentAlignment = Alignment.Center
            ) { NavIcon(NavIconName.PLUS, C_WHITE, 26.dp) }
            NavItem(NavIconName.PLACE, "方隅", false) { goPage("Place") }
            NavItem(NavIconName.VIDEO, "视频", false) { goPage("Video") }
        }
    }

    @Composable
    private fun RowScope.NavItem(icon: NavIconName, label: String, active: Boolean, onClick: () -> Unit) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable { onClick() }.weight(1f)
        ) {
            NavIcon(icon, if (active) C_CLAY else C_SUB, 23.dp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = label, fontSize = 11.sp, color = if (active) C_TEXT else C_SUB)
        }
    }

    @Composable
    private fun FullscreenOverlay() {
        Box(
            modifier = Modifier.fillMaxSize().background(Color(0xFF000000)).clickable { fullscreenUrl.value = "" },
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
            ) { Text(text = "✕", color = C_WHITE, fontSize = 18.sp) }
        }
    }
}

// ===== 数据模型与工具（文件级私有） =====

private data class YfPhoto(
    val id: String,
    val name: String,
    val thumb: String,
    val original: String,
    val dateStr: String,
    val year: Int,
    val month: Int,
    val day: Int,
    val title: String
)

private fun parseYf(j: JSONObject): YfPhoto {
    val rawDate = j.optString("date", "")
    val dateStr = if (rawDate.length >= 10) rawDate.substring(0, 10) else rawDate
    val year = if (dateStr.length >= 4) dateStr.substring(0, 4).toIntOrNull() ?: 0 else 0
    val month = if (dateStr.length >= 7) dateStr.substring(5, 7).toIntOrNull() ?: 0 else 0
    val day = if (dateStr.length >= 10) dateStr.substring(8, 10).toIntOrNull() ?: 0 else 0
    val name = j.optString("name", j.optString("filename", ""))
    val urlRel = j.optString("url", "")
    val fallback = if (urlRel.startsWith("http")) urlRel else ApiConfig.CHAIJIE_BASE + urlRel
    val thumb = thumbUrl(j.optString("thumbnail_path"), fallback)
    val original = j.optString("cloud_original_url", j.optString("original_url", thumb))
    val title = cleanDesc(j.optString("description", if (name.isNotEmpty()) name else "成长时刻记录"))
    return YfPhoto(
        id = j.optString("id", name),
        name = name,
        thumb = thumb,
        original = original,
        dateStr = dateStr,
        year = year,
        month = month,
        day = day,
        title = title
    )
}

/**
 * 清理 AI 生成的 description 里的格式噪音开头：
 * - 数字编号："12. " / "12、" / "12: "
 * - 加粗标签："**总结**" / "**总结**：" / "**基础信息分析**" 等
 * - 孤立冒号、多余空格
 * 例："12. **总结**:   12. 类似这种..." → "类似这种..."
 */
private fun cleanDesc(raw: String): String {
    var s = raw.trim()
    // 最多循环 3 轮，逐层剥掉开头噪音
    for (round in 0 until 3) {
        val before = s
        s = s.trim()
        // 1) 开头数字编号："12. " "12、" "12: "
        s = s.replace(Regex("^\\d+[\\u3001.、:：]\\s*"), "")
        // 2) 开头加粗标签："**总结**" "**总结**:" "**总结**："（标签内最多 12 字）
        s = s.replace(Regex("^\\*\\*[^*\\n]{1,12}\\*\\*\\s*[:：]?\\s*"), "")
        // 3) 开头孤立冒号/破折号/列表符
        s = s.replace(Regex("^[:：\\-–—\\s]+"), "")
        if (s == before) break
    }
    return s.trim()
}

private fun seasonColor(m: Int): Color = when {
    m in 3..5 -> Color(0xFF7FA37E)
    m in 6..8 -> Color(0xFFD9A66A)
    m in 9..11 -> Color(0xFFC08368)
    else -> Color(0xFF7E9AA8)
}

// 二十四节气（每月两个近似日期）
private val SOLAR_MD = listOf(
    Pair(1, 5), Pair(1, 20), Pair(2, 4), Pair(2, 19), Pair(3, 5), Pair(3, 20),
    Pair(4, 5), Pair(4, 20), Pair(5, 5), Pair(5, 21), Pair(6, 6), Pair(6, 21),
    Pair(7, 7), Pair(7, 23), Pair(8, 7), Pair(8, 23), Pair(9, 7), Pair(9, 23),
    Pair(10, 8), Pair(10, 23), Pair(11, 7), Pair(11, 22), Pair(12, 7), Pair(12, 22)
)
private val SOLAR_NAME = listOf(
    "小寒", "大寒", "立春", "雨水", "惊蛰", "春分", "清明", "谷雨",
    "立夏", "小满", "芒种", "夏至", "小暑", "大暑", "立秋", "处暑",
    "白露", "秋分", "寒露", "霜降", "立冬", "小雪", "大雪", "冬至"
)

private fun solarIdx(m: Int, d: Int): Int {
    val a = SOLAR_MD[(m - 1) * 2]
    val b = SOLAR_MD[(m - 1) * 2 + 1]
    return when {
        d >= b.second -> (m - 1) * 2 + 1
        d >= a.second -> (m - 1) * 2
        else -> ((m + 10) % 12) * 2 + 1
    }
}

private fun seasonOfSolar(idx: Int): String = when {
    idx in 2..7 -> "spring"
    idx in 8..13 -> "summer"
    idx in 14..19 -> "autumn"
    else -> "winter"
}

private fun fxEmoji(season: String): String = when (season) {
    "spring" -> "\uD83C\uDF27"   // 🌧
    "summer" -> "\u2600"         // ☀
    "autumn" -> "\uD83C\uDF41"   // 🍁
    else -> "\u2744"             // ❄
}

private fun fxCount(season: String): Int = when (season) {
    "spring" -> 10
    "summer" -> 12
    "autumn" -> 12
    else -> 16
}
