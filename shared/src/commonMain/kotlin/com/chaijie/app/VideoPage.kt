package com.chaijie.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import com.tencent.kuikly.compose.ComposeContainer
import com.tencent.kuikly.compose.coil3.rememberAsyncImagePainter
import com.tencent.kuikly.compose.foundation.Image
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.border
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.RowScope
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.pager.PageSize
import com.tencent.kuikly.compose.foundation.pager.PagerState
import com.tencent.kuikly.compose.foundation.pager.VerticalPager
import com.tencent.kuikly.compose.foundation.pager.rememberPagerState
import com.tencent.kuikly.compose.foundation.shape.CircleShape
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.material3.TextField
import com.tencent.kuikly.compose.setContent
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.clip
import com.tencent.kuikly.compose.ui.graphics.graphicsLayer
import com.tencent.kuikly.compose.ui.graphics.Brush
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.layout.ContentScale
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.text.style.TextOverflow
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.module.Module
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import kotlin.math.abs
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 视频页（抖音式整屏 Feed）。对标 video_v2.html + video_SPEC.md。
 *
 * 说明：Kuikly Compose 当前无视频播放 / WebView 组件，故「视频画面」用封面图或暖色兜底渐变呈现，
 * 播放进度以模拟循环推进——与原型 video_v2.html 的 onerror 兜底（封面渐变 + 模拟进度）完全一致，
 * 弱网/离线不白屏。真实播放需后续原生视频组件扩展。其余视觉与交互（整屏吸附 Feed、轻点切静音、
 * 频道分段、上传 Sheet、底部导航视频高亮）均按原型 1:1 还原。
 * 数据接入 chaijie 后端 /api/videos（key/name/url/poster_url/type/last_modified）。
 */
internal class VideoBridgeModule : Module() {
    override fun moduleName(): String = MODULE_NAME

    /** 调起 Android 原生抖音式全屏播放器（传入整个视频列表 + 起始 index，支持上下滑切换） */
    fun playVideo(urls: List<String>, index: Int, title: String) {
        val arr = JSONArray()
        urls.forEach { arr.put(it) }
        val data = JSONObject().apply {
            put("urls", arr)
            put("index", index)
            put("title", title)
        }
        toNative(false, "playVideo", data.toString(), null, false)
    }

    companion object {
        const val MODULE_NAME = "HRBridgeModule"
    }
}

@Page("Video")
internal class VideoPage : ComposeContainer() {

    override fun createExternalModules(): Map<String, Module>? {
        return mapOf(VideoBridgeModule.MODULE_NAME to VideoBridgeModule())
    }

    companion object {
        private val C_BG = Color(0xFFE9E5DE)
        private val C_CARD = Color(0xFFF5F2EC)
        private val C_TEXT = Color(0xFF46413B)
        private val C_SUB = Color(0xFF968D83)
        private val C_ACCENT = Color(0xFFA88F79)
        private val C_CLAY = Color(0xFFB07D6B)
        private val C_WHITE = Color(0xFFFFFFFF)
        private val C_NAV_BG = Color(0xDDF5F2EC)

        // 封面兜底渐变（暖色系 8 组，对齐 video_SPEC 第八节）
        internal val PALETTES = listOf(
            listOf(Color(0xFFE8C9B8), Color(0xFFC99A8E)),
            listOf(Color(0xFFB8D8C4), Color(0xFF8FA982)),
            listOf(Color(0xFFF2D9A8), Color(0xFFD9A66A)),
            listOf(Color(0xFFC9A9D8), Color(0xFFA88FB8)),
            listOf(Color(0xFFF0B9A8), Color(0xFFC08368)),
            listOf(Color(0xFFA8C8D8), Color(0xFF7E9AA8)),
            listOf(Color(0xFFE5B8C8), Color(0xFFC08BA0)),
            listOf(Color(0xFFD8C8A8), Color(0xFFB8A06A)),
        )
    }

    data class VideoItem(
        val id: String,
        val title: String,
        val desc: String,
        val dur: Int,          // 时长（秒），0 表示未知
        val who: String,       // "我上传的" | "成长精选"
        val g: Int,            // 渐变索引（兜底封面）
        val tags: List<String>,
        val playUrl: String,
        val thumb: String,
        val shootTime: String,
    )

    private val videos = mutableStateOf<List<VideoItem>>(emptyList())
    private val isLoading = mutableStateOf(true)
    private val activeFilter = mutableStateOf("全部")
    private val hintMsg = mutableStateOf("")
    private val hintSeq = mutableStateOf(0)
    private val pickedName = mutableStateOf("")
    private val tagsInput = mutableStateOf("")
    private val shootInput = mutableStateOf("")
    private val uploading = mutableStateOf(false)
    private val toastMsg = mutableStateOf("")
    private val toastSeq = mutableStateOf(0)

    override fun willInit() {
        super.willInit()
        setContent { VideoContent() }
    }

    override fun created() {
        super.created()
        loadVideos()
    }

    private fun loadVideos() {
        getJson(ApiConfig.VIDEO_LIST) { success, data ->
            val list = if (success) parseVideos(data) else emptyList()
            // 接入真实接口；接口异常或为空时回退到原型内置演示数据，保证页面不白屏（对齐 SPEC 5.4 弱网兜底）
            videos.value = if (list.isNotEmpty()) list else DEMO_VIDEOS
            isLoading.value = false
        }
    }

    internal fun parseVideos(data: JSONObject): List<VideoItem> {
        // video_studio 返回顶层 JSON 数组（46 个视频），字段：id/name/title/desc/dur/who/play_url/thumbnail/shoot_time/tags
        val arr = parseArray(data)
        val out = mutableListOf<VideoItem>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id", "")
            if (id.isEmpty()) continue
            val playUrl = o.optString("play_url", "")
            if (playUrl.isEmpty()) continue // 无播放源跳过
            val name = o.optString("title", "").ifEmpty { o.optString("name", "未命名视频") }
            val who = o.optString("who", "我上传的") // 后端直接返回中文分组
            val thumb = o.optString("thumbnail", "") // 相对 /video/stream/... 或绝对 URL
            val shootRaw = o.optString("shoot_time", "")
            val shoot = if (shootRaw.length >= 10) shootRaw.substring(0, 10) else shootRaw
            val tags = o.optJSONArray("tags")?.let { ta ->
                (0 until ta.length()).mapNotNull { ta.optString(it) }
            } ?: emptyList()
            out.add(
                VideoItem(
                    id = id,
                    title = name,
                    desc = o.optString("desc", ""),
                    dur = o.optInt("dur", 0),
                    who = who,
                    g = abs(id.hashCode()) % 8,
                    tags = tags,
                    playUrl = if (playUrl.startsWith("http")) playUrl else ApiConfig.CHAIJIE_BASE + playUrl,
                    thumb = if (thumb.startsWith("http")) thumb else ApiConfig.CHAIJIE_BASE + thumb,
                    shootTime = shoot,
                )
            )
        }
        return out
    }

    internal val DEMO_VIDEOS = listOf(
        VideoItem("v1", "周末徒步的云海", "山顶风很大，但云海太美了，那一刻觉得一切都值得。", 342, "我上传的", 0, listOf("旅行", "风景"), "", "", "2026-05-02"),
        VideoItem("v2", "宝宝第一次走路", "摇摇晃晃的小步伐，全家人的心都化了。", 76, "成长精选", 2, listOf("成长", "家庭"), "", "", "2026-03-18"),
        VideoItem("v3", "海边日出延时", "三分钟看完整场日出，海面被染成金色。", 180, "我上传的", 5, listOf("旅行", "风景"), "", "", "2026-04-30"),
        VideoItem("v4", "生日派对合集", "吹蜡烛、切蛋糕、大合照，热热闹闹一整晚。", 268, "成长精选", 6, listOf("生日", "聚会"), "", "", "2026-02-14"),
        VideoItem("v5", "雪地打滚的柴犬", "第一次见雪，开心到在雪地里打滚。", 45, "我上传的", 1, listOf("宠物", "冬日"), "", "", "2026-01-12"),
        VideoItem("v6", "幼儿园文艺汇演", "台上的小大人，认认真真完成每个动作。", 521, "成长精选", 3, listOf("成长", "校园"), "", "", "2025-12-28"),
        VideoItem("v7", "夜市烟火气", "人挤人的快乐，烤串和糖葫芦都要。", 95, "我上传的", 4, listOf("美食", "城市"), "", "", "2025-11-03"),
        VideoItem("v8", "第一次游泳课", "敢把头埋进水里啦，进步神速。", 133, "成长精选", 7, listOf("成长", "夏天"), "", "", "2025-08-16"),
        VideoItem("v9", "秋日银杏大道", "满地的金黄，踩着咯吱响。", 210, "我上传的", 3, listOf("风景", "秋天"), "", "", "2025-10-25"),
        VideoItem("v10", "学骑自行车", "摔了三次，终于会了。", 189, "成长精选", 5, listOf("成长", "户外"), "", "", "2025-09-06"),
        VideoItem("v11", "猫咪的迷惑行为", "盯了逗猫棒十分钟。", 38, "我上传的", 1, listOf("宠物", "搞笑"), "", "", "2025-08-02"),
        VideoItem("v12", "第一次坐火车", "窗外的风景一路后退。", 98, "成长精选", 7, listOf("成长", "旅行"), "", "", "2025-06-20"),
    )

    private fun viewList(): List<VideoItem> {
        val all = videos.value
        return when (activeFilter.value) {
            "我上传的" -> all.filter { it.who == "我上传的" }
            "成长精选" -> all.filter { it.who == "成长精选" }
            else -> all
        }
    }

    private fun goPage(name: String) {
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(name, null)
    }

    private fun showToast(msg: String) {
        toastMsg.value = msg
        toastSeq.value++
    }

    internal fun fmtDur(sec: Int): String {
        if (sec <= 0) return "0:00"
        val m = sec / 60
        val s = sec % 60
        return "$m:${if (s < 10) "0$s" else "$s"}"
    }

    private fun playVideo(item: VideoItem) {
        try {
            // 抖音式全屏播放器：传整个当前筛选列表 + 点击的 index，支持上下滑切换全部视频
            val items = viewList()
            val idx = items.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
            acquireModule<VideoBridgeModule>(VideoBridgeModule.MODULE_NAME)
                .playVideo(items.map { it.playUrl }, idx, item.title)
        } catch (t: Throwable) {
            showToast("播放失败")
        }
    }

    @Composable
    private fun VideoContent() {
        val scope = rememberCoroutineScope()
        val pagerState = rememberPagerState(pageCount = { viewList().size })

        // 轻点提示自动消失
        LaunchedEffect(hintSeq.value) {
            if (hintMsg.value.isNotEmpty()) {
                delay(900)
                hintMsg.value = ""
            }
        }
        LaunchedEffect(toastSeq.value) {
            if (toastMsg.value.isNotEmpty()) {
                delay(1700)
                toastMsg.value = ""
            }
        }

        Column(modifier = Modifier.fillMaxSize().background(C_BG).padding(top = pageData.statusBarHeight.dp)) {
            SegBar(
                activeFilter = activeFilter.value,
                onFilter = { f ->
                    activeFilter.value = f
                    scope.launch { pagerState.scrollToPage(0) }
                },
            )
            Box(modifier = Modifier.fillMaxWidth().weight(1f).background(Color(0xFF111111))) {
                when {
                    isLoading.value -> LoadingBox()
                    viewList().isEmpty() -> EmptyState()
                    else -> {
                        VerticalPager(
                            state = pagerState,
                            pageSize = PageSize.Fill,
                            modifier = Modifier.fillMaxSize(),
                            key = { it },
                        ) { page ->
                            val item = viewList()[page]
                            val active = page == pagerState.currentPage
                            FeedItem(
                                item = item,
                                active = active,
                                onTap = { playVideo(item) },
                            )
                        }
                    }
                }
                // 轻点提示（全局居中浮现）
                if (hintMsg.value.isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = hintMsg.value,
                            color = C_WHITE, fontSize = 13.sp,
                            modifier = Modifier.background(Color(0x33000000), RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 7.dp),
                        )
                    }
                }
            }
            BottomNavBar()
        }
        Toast()
    }

    /** 频道分段 —— 置顶居中融入式（参考方隅 SegBar）：无外围背景条，选中项高亮胶囊；顺序：我上传的/全部/成长精选 */
    @Composable
    private fun SegBar(activeFilter: String, onFilter: (String) -> Unit) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(0f to Color(0xE5E9E5DE), 1f to Color(0x80E9E5DE)))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            SegBtn("我上传的", activeFilter == "我上传的") { onFilter("我上传的") }
            SegBtn("全部", activeFilter == "全部") { onFilter("全部") }
            SegBtn("成长精选", activeFilter == "成长精选") { onFilter("成长精选") }
        }
    }

    @Composable
    private fun SegBtn(label: String, active: Boolean, onClick: () -> Unit) {
        Box(
            modifier = Modifier.clip(RoundedCornerShape(999.dp))
                .background(
                    if (active) Brush.linearGradient(0f to Color(0xFFC99A8E), 1f to Color(0xFFB07D6B))
                    else Brush.linearGradient(0f to Color.Transparent, 1f to Color.Transparent),
                )
                .clickable { onClick() }.padding(horizontal = 11.dp, vertical = 6.dp),
        ) {
            Text(
                text = label,
                fontSize = 11.5.sp,
                color = if (active) C_WHITE else C_SUB,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }

    @Composable
    private fun LoadingBox() {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.size(26.dp).clip(CircleShape).background(C_CARD).border(2.5.dp, C_ACCENT, CircleShape))
                Spacer(modifier = Modifier.height(10.dp))
                Text(text = "加载中…", fontSize = 13.sp, color = C_SUB)
            }
        }
    }

    @Composable
    private fun EmptyState() {
        Column(
            modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0xFFEFEAE2), 1f to Color(0xFFE3DDD3))),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = "这个频道还没有视频", fontSize = 14.sp, color = C_SUB)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = "点右上角「上传」试试 ✨", fontSize = 13.sp, color = C_SUB)
        }
    }

    @Composable
    private fun FeedItem(item: VideoItem, active: Boolean, onTap: () -> Unit) {
        val pal = PALETTES[item.g % 8]
        Box(modifier = Modifier.fillMaxSize().clickable { onTap() }) {
            // 封面层（真实 thumbnail；Glide 磁盘缓存）
            if (item.thumb.isNotEmpty()) {
                Image(
                    painter = rememberAsyncImagePainter(item.thumb),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Brush.linearGradient(0f to pal[0], 1f to pal[1])))
            }
            // 暗角
            Box(modifier = Modifier.fillMaxSize().background(Color(0x1A000000)))
            // 来源角标（左上）
            Box(
                modifier = Modifier.align(Alignment.TopStart).padding(14.dp)
                    .background(Color(0xDCF5F2EC), RoundedCornerShape(8.dp)).padding(horizontal = 9.dp, vertical = 4.dp),
            ) {
                Text(
                    text = item.who,
                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    color = if (item.who == "我上传的") C_ACCENT else C_CLAY,
                )
            }
            // 右上：时长
            Row(modifier = Modifier.align(Alignment.TopEnd).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                if (item.dur > 0) {
                    Box(modifier = Modifier.background(Color(0x8C000000), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                        Text(text = fmtDur(item.dur), color = C_WHITE, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            // 底部元信息（渐变遮罩，不拦截点击）
            Box(
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(0f to Color.Transparent, 1f to Color(0xB8000000)))
                    .padding(start = 16.dp, end = 16.dp, top = 26.dp, bottom = 22.dp),
            ) {
                Column {
                    Text(text = item.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = C_WHITE)
                    if (item.desc.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = item.desc, fontSize = 13.sp, color = Color(0xFFECE8F7), maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    if (item.tags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item.tags.forEach { t ->
                                Box(
                                    modifier = Modifier.background(Color(0x33FFFFFF), RoundedCornerShape(99.dp))
                                        .border(1.dp, Color(0x4DFFFFFF), RoundedCornerShape(99.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
                                ) { Text(text = t, fontSize = 10.5.sp, color = C_WHITE) }
                            }
                        }
                    }
                }
            }
            // 点击播放提示（居中）
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier.size(62.dp)
                        .background(Color(0x33000000), CircleShape).border(1.dp, Color(0x99FFFFFF), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text(text = "▶", color = C_WHITE, fontSize = 26.sp) }
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "点击全屏播放", color = Color(0xCCFFFFFF), fontSize = 11.sp)
            }
        }
    }

    @Composable
    private fun BottomNavBar() {
        Row(
            modifier = Modifier.fillMaxWidth().background(C_NAV_BG).border(1.dp, Color(0x80FFFFFF))
                .padding(vertical = 10.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            NavItem(NavIconName.HOME, "首页", false) { goPage("Home") }
            NavItem(NavIconName.YEAR, "流年", false) { goPage("YearFlow") }
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(C_CLAY).clickable { goPage("Upload") },
                contentAlignment = Alignment.Center,
            ) { NavIcon(NavIconName.PLUS, C_WHITE, 26.dp) }
            NavItem(NavIconName.PLACE, "方隅", false) { goPage("Place") }
            NavItem(NavIconName.VIDEO, "视频", true) {}
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
            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 96.dp), contentAlignment = Alignment.BottomCenter) {
                Box(modifier = Modifier.background(Color(0xED46413B), RoundedCornerShape(20.dp)).padding(horizontal = 18.dp, vertical = 10.dp)) {
                    Text(text = toastMsg.value, fontSize = 13.sp, color = C_WHITE)
                }
            }
        }
    }
}
