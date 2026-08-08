package com.chaijie.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.tencent.kuikly.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.tencent.kuikly.compose.ComposeContainer
import com.tencent.kuikly.compose.coil3.rememberAsyncImagePainter
import com.tencent.kuikly.compose.foundation.Image
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.border
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.combinedClickable
import com.tencent.kuikly.compose.foundation.gestures.detectTapGestures
import com.tencent.kuikly.compose.foundation.gestures.detectTransformGestures
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.aspectRatio
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.lazy.LazyColumn
import com.tencent.kuikly.compose.foundation.lazy.items
import com.tencent.kuikly.compose.foundation.shape.CircleShape
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.ui.input.pointer.pointerInput
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.material3.TextField
import com.tencent.kuikly.compose.setContent
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.clip
import com.tencent.kuikly.compose.ui.graphics.Brush
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.graphics.graphicsLayer
import com.tencent.kuikly.compose.ui.layout.ContentScale
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.text.style.TextAlign
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.compose.ui.window.Dialog
import com.tencent.kuikly.compose.ui.window.DialogProperties
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.module.CallbackFn
import com.tencent.kuikly.core.module.Module
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay

@Page("Upload")
internal class UploadPage : ComposeContainer() {

    companion object {
        // 与 android/KRBridgeModule.MODULE_NAME 一致
        const val BRIDGE_MODULE_NAME = "HRBridgeModule"
        private val C_BG = Color(0xFFE9E5DE)
        private val C_CARD = Color(0xFFF5F2EC)
        private val C_TEXT = Color(0xFF46413B)
        private val C_SUB = Color(0xFF968D83)
        private val C_CLAY = Color(0xFFB07D6B)
        private val C_WHITE = Color(0xFFFFFFFF)
        private val G1 = Color(0xFFC99A8E)
        private val G2 = Color(0xFFB07D6B)
    }

    /** 上传桥接模块：转发原生扫相册/上传缩略图/轮询/批量上传能力 */
    internal class UploadBridgeModule : Module() {
        override fun moduleName(): String = BRIDGE_MODULE_NAME
        fun scanGallery(limit: Int): String =
            syncToNativeMethod("scanGallery", JSONObject().apply { put("limit", limit) }, null)
        fun scanUpload(files: List<String>, cb: CallbackFn?) {
            val arr = JSONArray()
            files.forEach { arr.put(it) }
            asyncToNativeMethod("scanUpload", JSONObject().apply { put("files", arr) }, cb)
        }
        fun scanPoll(taskId: String): String =
            syncToNativeMethod("scanPoll", JSONObject().apply { put("taskId", taskId) }, null)
        fun copyOriginal(photoId: Long): String =
            syncToNativeMethod("copyOriginal", JSONObject().apply { put("id", photoId) }, null)
        fun batchUpload(photos: List<JSONObject>, groups: List<JSONObject>?, cb: CallbackFn?) {
            val arr = JSONArray()
            photos.forEach { arr.put(it) }
            val jo = JSONObject().apply { put("photos", arr) }
            if (groups != null && groups.isNotEmpty()) {
                val garr = JSONArray()
                groups.forEach { garr.put(it) }
                jo.put("groups", garr)
            }
            asyncToNativeMethod("batchUpload", jo, cb)
        }
        companion object { const val MODULE_NAME = "HRBridgeModule" }
    }

    override fun createExternalModules(): Map<String, Module>? =
        mapOf(UploadBridgeModule.MODULE_NAME to UploadBridgeModule())

    override fun willInit() {
        super.willInit()
        setContent { UploadContent() }
    }

    @Composable
    private fun UploadContent() {
        val scope = rememberCoroutineScope()
        when (step) {
            0 -> EntryScreen(scope)
            1 -> ScanningScreen(scope)
            2 -> GroupScreen(scope)
            3 -> UploadingScreen(scope)
            4 -> DoneScreen()
        }
    }

    // ===== 0 入口 =====
    @Composable
    private fun EntryScreen(scope: CoroutineScope) {
        Column(
            modifier = Modifier.fillMaxSize().background(C_BG).padding(top = pageData.statusBarHeight.dp),
        ) {
            Spacer(Modifier.height(80.dp))
            Box(
                modifier = Modifier.fillMaxWidth().height(110.dp)
                    .padding(horizontal = 28.dp).clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(0f to G1, 1f to G2)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("上传照片", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C_WHITE)
                    Text("扫描 · 人脸识别 · 勾选上传", fontSize = 13.sp, color = Color(0xCCFFFFFF), modifier = Modifier.padding(top = 6.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("使用流程", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = C_TEXT, modifier = Modifier.padding(horizontal = 28.dp))
            Spacer(Modifier.height(8.dp))
            listOf(
                "扫描相册最近 300 张照片" to "本地生成缩略图，不上传原图",
                "后端人脸识别聚类" to "缩略图上传，~30 秒内完成识别",
                "按人脸分组确认上传" to "默认勾选人脸组，「其他」不上传",
                "批次上传原图入库" to "选中的照片原图批量入库到 R2",
            ).forEach { (t, d) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(14.dp)).background(C_CARD).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.size(34.dp).clip(CircleShape).background(Color(0x21B07D6B)), contentAlignment = Alignment.Center) {
                        Text("✓", fontSize = 16.sp, color = C_CLAY, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(t, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = C_TEXT)
                        Text(d, fontSize = 11.sp, color = C_SUB, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (errMsg.isNotEmpty()) {
                Text(errMsg, fontSize = 13.sp, color = Color(0xFFB0453A),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 6.dp),
                    textAlign = TextAlign.Center)
            }
            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Brush.linearGradient(0f to G1, 1f to G2)).clickable { startScan(scope) }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text("开始扫描相册", color = C_WHITE, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
            Text("预计耗时 1–2 分钟", fontSize = 11.sp, color = C_SUB, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp))
            // 底部导航占位
            Spacer(Modifier.height(80.dp))
        }
    }

    // ===== 1 扫描中 =====
    @Composable
    private fun ScanningScreen(scope: CoroutineScope) {
        Column(modifier = Modifier.fillMaxSize().background(C_BG).padding(top = pageData.statusBarHeight.dp)) {
            Spacer(Modifier.height(40.dp))
            Text("上传照片", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = C_TEXT, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(50.dp))
            // 环形进度（简化：用圆形 Box + 百分比）
            Box(modifier = Modifier.size(160.dp).align(Alignment.CenterHorizontally).clip(CircleShape).background(C_CARD), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${(scanPct * 100).toInt()}%", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = C_CLAY)
                    Text("扫描进度", fontSize = 11.sp, color = C_SUB, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(scanStage, fontSize = 13.sp, color = C_SUB, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(36.dp))
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 30.dp).clip(RoundedCornerShape(16.dp)).background(C_CARD).padding(16.dp)) {
                Column {
                    listOf("扫描相册照片" to "${photos.size} / 300", "人脸检测" to if (scanPct > 0.2f) "识别中…" else "等待中", "人脸聚类分组" to if (scanPct > 0.8f) "完成" else "等待中", "生成「其他」分组" to if (groups.isNotEmpty()) "完成" else "等待中").forEachIndexed { i, (k, v) ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(if (scanPct > i * 0.25f) Brush.linearGradient(0f to G1, 1f to G2) else Brush.linearGradient(listOf(Color(0xFFE3DDD3), Color(0xFFE3DDD3)))), contentAlignment = Alignment.Center) {
                                Text("${i + 1}", fontSize = 10.sp, color = C_WHITE, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(k, fontSize = 13.sp, color = C_TEXT, modifier = Modifier.weight(1f))
                            Text(v, fontSize = 11.sp, color = C_SUB)
                        }
                    }
                }
            }
        }
    }

    // ===== 2 分组 =====
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun GroupScreen(scope: CoroutineScope) {
        Column(modifier = Modifier.fillMaxSize().padding(top = pageData.statusBarHeight.dp)) {
            // ===== 顶部：Header（对齐原型）=====
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("\uD83D\uDC65 人脸分组", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = C_TEXT)
                    Spacer(Modifier.width(8.dp))
                    Text("合并交互", fontSize = 11.sp, color = C_SUB, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(Color(0x14B07D6B)).padding(horizontal = 8.dp, vertical = 2.dp))
                }
                Text("扫描 ${photos.size} 张 · 后端识别返回 ${scanTotal} 张 · ${groups.size} 个分组 · 未上传", fontSize = 11.5.sp,
                    color = C_SUB, modifier = Modifier.padding(top = 4.dp))
            }
            // ===== 模式切换 tab（对齐原型）=====
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(14.dp)).background(Color(0x33FFFFFF).copy(alpha = 0.55f)).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                MergeTab.entries.forEach { t ->
                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(11.dp))
                        .background(if (mergeTab == t) Brush.linearGradient(0f to G1, 1f to G2) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                        .clickable { mergeTab = t }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text(t.label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold,
                            color = if (mergeTab == t) C_WHITE else C_SUB)
                    }
                }
            }
            // ===== 模式说明卡 =====
            Text(mergeTab.tip, fontSize = 11.sp, color = C_SUB, lineHeight = 16.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).clip(RoundedCornerShape(12.dp))
                    .background(Color(0x33FFFFFF).copy(alpha = 0.5f)).padding(horizontal = 12.dp, vertical = 8.dp))
            // ===== 提示条（带闪烁圆点）=====
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(12.dp)).background(if (mergeMode) Color(0x29D98E6B) else Color(0x1AB07D6B))
                .padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(if (mergeMode) Color(0xFFD97A52) else C_CLAY))
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        mergeMode -> "已选 ${mergePick.size} 组 · 点底部「合并所选」"
                        mergeTab == MergeTab.PICK -> "长按分组进入合并选择 · 点击卡片预览"
                        mergeTab == MergeTab.DRAG -> "长按分组拖到目标分组上松手合并（即将支持）"
                        else -> "系统将标记疑似同一人的分组（即将支持）"
                    },
                    fontSize = 11.5.sp, color = if (mergeMode) Color(0xFFA05A38) else C_CLAY, modifier = Modifier.weight(1f),
                )
                if (mergeMode) {
                    Text("取消", fontSize = 12.sp, color = Color(0xFFA05A38), fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { exitMergeMode() })
                } else {
                    Text(
                        if (selected.size == totalFacePhotos() && totalFacePhotos() > 0) "取消全选" else "全选",
                        fontSize = 12.sp, color = C_CLAY, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { toggleAll() },
                    )
                }
            }
            // ===== 合并统计条 =====
            if (mergedCount > 0) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(12.dp)).background(Color(0x1F7FA88A)).padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("✅", fontSize = 12.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("已合并 $mergedCount 个碎片分组，疑似同一人的照片已归并", fontSize = 11.sp, color = Color(0xFF5F8468))
                }
            }
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(groups, key = { (it.optInt("id", 0) ?: 0) }) { g ->
                    val gid = g.optInt("id", -1) ?: -1
                    val sampleIdx = g.optInt("sample_index", -1)
                    val thumb = photos.getOrNull(sampleIdx)?.optString("thumb", "") ?: ""
                    val count = g.optInt("photo_count", 0)
                    val matchedName = g.optString("matched_person_name", "")
                    val displayName = if (matchedName.isNotEmpty()) matchedName else "人脸组 ${gid + 1}"
                    val palettes = listOf(
                        listOf(0xFFE8B4A0, 0xFFD08A72), listOf(0xFFC9A9D8, 0xFFA88FB8),
                        listOf(0xFFA8C8D8, 0xFF7E9AA8), listOf(0xFFF0B9A8, 0xFFC08368),
                        listOf(0xFFE5B8C8, 0xFFC08BA0)
                    )
                    val pal = palettes[((gid % palettes.size).coerceAtLeast(0))]
                    val isPicked = if (mergeMode) mergePick.contains(gid) else selected.contains(gid)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 5.dp)
                            .clip(RoundedCornerShape(16.dp)).background(C_CARD)
                            .border(1.dp, if (isPicked) C_CLAY else Color(0xCCFFFFFF), RoundedCornerShape(16.dp))
                            .combinedClickable(
                                onClick = {
                                    if (mergeMode) {
                                        if (mergePick.contains(gid)) mergePick.remove(gid) else mergePick.add(gid)
                                    } else {
                                        previewGroup = g
                                    }
                                },
                                onLongClick = {
                                    if (!mergeMode) {
                                        mergeMode = true
                                        mergePick.clear()
                                        mergePick.add(gid)
                                    }
                                },
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.size(50.dp).clip(RoundedCornerShape(13.dp))
                                .background(Brush.linearGradient(0f to Color(pal[0]), 1f to Color(pal[1]))),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (thumb.isNotEmpty()) {
                                Image(
                                    painter = rememberAsyncImagePainter("file://$thumb"),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Text("\uD83D\uDC64", fontSize = 22.sp)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(displayName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = C_TEXT)
                                Spacer(Modifier.width(6.dp))
                                Text("$count 张", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = C_CLAY,
                                    modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(Color(0x21B07D6B)).padding(horizontal = 8.dp, vertical = 2.dp))
                            }
                            Text(
                                if (matchedName.isNotEmpty()) "已匹配已有分组" else "已识别为同一人",
                                fontSize = 11.sp, color = C_SUB, modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                        Box(
                            modifier = Modifier.size(24.dp).clip(CircleShape)
                                .background(if (isPicked) Brush.linearGradient(0f to G1, 1f to G2) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                                .border(1.dp, if (isPicked) Color.Transparent else Color(0xFFD8D1C7), CircleShape)
                                .clickable {
                                    if (mergeMode) {
                                        if (mergePick.contains(gid)) mergePick.remove(gid) else mergePick.add(gid)
                                    } else {
                                        if (selected.contains(gid)) selected.remove(gid) else selected.add(gid)
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) { if (isPicked) Text(if (mergeMode) "\u2B55" else "\u2713", fontSize = 13.sp, color = C_WHITE, fontWeight = FontWeight.Bold) }
                    }
                }
                if (otherIndices.isNotEmpty()) {
                    item {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 5.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFEDE9E1)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(50.dp).clip(RoundedCornerShape(13.dp)).background(Brush.linearGradient(0f to Color(0xFFB9B2A6), 1f to Color(0xFF9C9488))), contentAlignment = Alignment.Center) { Text("\uD83D\uDDC1", fontSize = 22.sp) }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("其他", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = C_TEXT)
                                    Spacer(Modifier.width(6.dp))
                                    Text("${otherIndices.size} 张", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = C_SUB,
                                        modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(Color(0x22FFFFFF)).padding(horizontal = 8.dp, vertical = 2.dp))
                                }
                                Text("无人脸 / 未识别", fontSize = 11.sp, color = C_SUB, modifier = Modifier.padding(top = 3.dp))
                            }
                            Text("不上传", fontSize = 11.sp, color = C_SUB)
                        }
                    }
                }
            }
            if (mergeMode) {
                // 合并模式：合并所选按钮
                Box(modifier = Modifier.fillMaxWidth().padding(14.dp).clip(RoundedCornerShape(999.dp))
                    .background(if (mergePick.size >= 2) Brush.linearGradient(0f to G1, 1f to G2) else Brush.linearGradient(listOf(Color(0xFFD8D1C7), Color(0xFFD8D1C7))))
                    .clickable { if (mergePick.size >= 2) mergeDialog = true }
                    .padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                    Text(if (mergePick.size >= 2) "合并所选 ${mergePick.size} 组" else "至少选择 2 组才能合并",
                        color = if (mergePick.size >= 2) C_WHITE else C_SUB, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().padding(14.dp).clip(RoundedCornerShape(999.dp))
                    .background(Brush.linearGradient(0f to G1, 1f to G2)).clickable { startUpload(scope) }
                    .padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                    Text("上传所选（${selectedPhotoCount()} 张）", color = C_WHITE, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Text(
                if (mergeMode) "合并后照片将归入同一聚类 \u00B7 点「取消」退出合并" else "\u300C其他\u300D分组默认不选 \u00B7 点卡片预览 \u00B7 长按合并",
                fontSize = 10.5.sp, color = C_SUB, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }
        // 合并确认弹框
        if (mergeDialog) {
            MergeDialog()
        }
        // 预览弹框（点击分组卡片触发）
        previewGroup?.let { g ->
            PreviewDialog(g = g) { previewGroup = null }
        }
        // 照片放大查看
        if (zoomPhotoIdx >= 0) {
            ZoomPhotoDialog(photoIdx = zoomPhotoIdx) {
                zoomPhotoIdx = -1
            }
        }
    }

    // ===== 照片放大查看（点击预览照片 → 全屏原图 + 双指缩放 + 单击关闭）=====
    @Composable
    private fun ZoomPhotoDialog(photoIdx: Int, onClose: () -> Unit) {
        val photo = photos.getOrNull(photoIdx)
        val photoId = photo?.optString("id", "").orEmpty()
        val thumb = photo?.optString("thumb", "").orEmpty()
        // 原图：调原生拷贝到本地 cache（Kuikly coil3 不支持 content://），失败回退缩略图
        var originalUri by mutableStateOf("file://" + thumb)
        LaunchedEffect(photoIdx) {
            val pid = photoId.toLongOrNull() ?: -1L
            if (pid > 0) {
                val p = bridge.value.copyOriginal(pid)
                if (p.isNotEmpty()) originalUri = p
            }
        }
        var scale by mutableStateOf(1f)
        var offsetX by mutableStateOf(0f)
        var offsetY by mutableStateOf(0f)
        Dialog(
            onDismissRequest = { onClose() },
            properties = DialogProperties(usePlatformDefaultWidth = false, scrimColor = Color(0xE6000000)),
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .pointerInput("zoom") {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            offsetX += pan.x
                            offsetY += pan.y
                        }
                    }
                    .pointerInput("tap") {
                        detectTapGestures(onTap = { onClose() })
                    }
                    .background(Color(0xE6000000)),
                contentAlignment = Alignment.Center,
            ) {
                if (originalUri.isNotEmpty()) {
                    Image(
                        painter = rememberAsyncImagePainter(originalUri),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offsetX
                                translationY = offsetY
                            },
                        contentScale = ContentScale.Fit,
                    )
                }
                // 顶部关闭提示
                Text("单击关闭 · 双指缩放", fontSize = 12.sp, color = Color(0x99FFFFFF),
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 50.dp))
            }
        }
    }

    // 预览弹框（点击分组卡片 → 预览该组所有人脸 + 取消选择）
    @Composable
    private fun PreviewDialog(g: JSONObject, onClose: () -> Unit) {
        val gid = g.optInt("id", -1) ?: -1
        val photoArr = g.optJSONArray("photo_indices")
        val indices = if (photoArr != null) (0 until (photoArr.length())).mapNotNull { photoArr.optInt(it) } else emptyList()
        val matchedName = g.optString("matched_person_name", "")
        val title = if (matchedName.isNotEmpty()) matchedName else "人脸组 ${gid + 1}"
        Dialog(
            onDismissRequest = { onClose() },
            properties = DialogProperties(usePlatformDefaultWidth = false, scrimColor = Color(0x99000000)),
        ) {
            Column(modifier = Modifier.fillMaxSize().background(C_BG).padding(top = 30.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = C_TEXT, modifier = Modifier.weight(1f))
                    Text("${indices.size} 张", fontSize = 12.sp, color = C_SUB)
                    Spacer(Modifier.width(10.dp))
                    Box(modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Color(0x1AB07D6B)).clickable { onClose() }.padding(horizontal = 12.dp, vertical = 6.dp)) { Text("关闭", fontSize = 12.sp, color = C_CLAY, fontWeight = FontWeight.SemiBold) }
                }
                Text("点照片放大 · 点右下角 √ 取消选择该张", fontSize = 11.sp, color = C_SUB, modifier = Modifier.padding(horizontal = 16.dp))
                val cols = 4
                val rows = (indices.size + cols - 1) / cols
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 6.dp)) {
                    items(rows) { r ->
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (c in 0 until cols) {
                                val pi = r * cols + c
                                if (pi < indices.size) {
                                    val idx = indices[pi]
                                    val t = photos.getOrNull(idx)?.optString("thumb", "") ?: ""
                                    val isOff = deselectedIndices.contains(idx)
                                    Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(10.dp))
                                        .background(if (isOff) Color(0xFFB8B0A6) else Color(0xFFE4DED5))
                                        .border(1.dp, Color(0x80FFFFFF), RoundedCornerShape(10.dp))
                                        .clickable { zoomPhotoIdx = idx }) {
                                        if (t.isNotEmpty()) {
                                            Image(painter = rememberAsyncImagePainter("file://$t"), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                        }
                                        if (isOff) {
                                            Box(modifier = Modifier.fillMaxSize().background(Color(0x59000000)))
                                        }
                                        Text("${pi + 1}", fontSize = 9.sp, color = C_WHITE, modifier = Modifier.align(Alignment.TopStart).padding(3.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x66000000)).padding(horizontal = 4.dp, vertical = 1.dp))
                                        // 右下角选择圈：点它切换选中/取消（照片其他区域点击放大）
                                        Box(
                                            modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp).size(20.dp)
                                                .clip(CircleShape)
                                                .background(if (isOff) Brush.linearGradient(listOf(Color(0xFFE3DDD3), Color(0xFFE3DDD3))) else Brush.linearGradient(0f to G1, 1f to G2))
                                                .border(1.dp, if (isOff) Color(0xFFD8D1C7) else Color.Transparent, CircleShape)
                                                .clickable {
                                                    if (deselectedIndices.contains(idx)) deselectedIndices.remove(idx) else deselectedIndices.add(idx)
                                                },
                                            contentAlignment = Alignment.Center,
                                        ) { if (!isOff) Text("\u221A", fontSize = 11.sp, color = C_WHITE, fontWeight = FontWeight.Bold) }
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                                }
                            }
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(999.dp)).background(Color(0xFFE3DDD3)).clickable {
                        // 取消整组：移除该组所有照片的 deselected 标记，再取消整组选择
                        indices.forEach { deselectedIndices.remove(it) }
                        selected.remove(gid); onClose()
                    }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Text("取消整组选择", fontSize = 13.sp, color = C_SUB, fontWeight = FontWeight.SemiBold)
                    }
                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(999.dp)).background(Brush.linearGradient(0f to G1, 1f to G2)).clickable { onClose() }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Text("保留选择", fontSize = 13.sp, color = C_WHITE, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    // ===== 合并确认弹框 =====
    @Composable
    private fun MergeDialog() {
        val pickedGroups = mergePick.mapNotNull { gid -> groups.firstOrNull { (it.optInt("id", -1) ?: -1) == gid } }
        if (pickedGroups.size < 2) { mergeDialog = false; return }
        // 主组：优先已匹配人名的组，其次人数最多的组
        val mainGroup = pickedGroups.firstOrNull { it.optString("matched_person_name", "").isNotEmpty() }
            ?: pickedGroups.maxByOrNull { it.optInt("photo_count", 0) }
            ?: pickedGroups.first()
        val mainName = mainGroup.optString("matched_person_name", "").ifEmpty { "人脸组 ${(mainGroup.optInt("id", -1) ?: 0) + 1}" }
        var mergedName by mutableStateOf(mainName)
        val totalCount = pickedGroups.sumOf { it.optInt("photo_count", 0) }
        Dialog(
            onDismissRequest = { mergeDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, scrimColor = Color(0x99000000)),
        ) {
            Column(modifier = Modifier.fillMaxSize().background(C_BG).padding(top = 30.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("合并 ${pickedGroups.size} 个分组", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = C_TEXT, modifier = Modifier.weight(1f))
                    Box(modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Color(0x1AB07D6B)).clickable { mergeDialog = false }.padding(horizontal = 12.dp, vertical = 6.dp)) { Text("取消", fontSize = 12.sp, color = C_CLAY, fontWeight = FontWeight.SemiBold) }
                }
                Text("合并后这些照片将归入同一聚类", fontSize = 11.sp, color = C_SUB, modifier = Modifier.padding(horizontal = 16.dp))
                // 参与合并的分组
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp)) {
                    items(pickedGroups, key = { it.optInt("id", 0) ?: 0 }) { g ->
                        val gid = g.optInt("id", -1) ?: -1
                        val cnt = g.optInt("photo_count", 0)
                        val nm = g.optString("matched_person_name", "").ifEmpty { "人脸组 ${gid + 1}" }
                        val isMain = g == mainGroup
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(14.dp)).background(C_CARD).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(if (isMain) Brush.linearGradient(0f to G1, 1f to G2) else Brush.linearGradient(listOf(Color(0xFFE3DDD3), Color(0xFFE3DDD3)))), contentAlignment = Alignment.Center) {
                                Text(if (isMain) "\u2B50" else "\uD83D\uDC64", fontSize = 15.sp)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(nm, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = C_TEXT)
                                Text(if (isMain) "主分组（保留此名称）" else "$cnt 张", fontSize = 10.5.sp, color = C_SUB, modifier = Modifier.padding(top = 2.dp))
                            }
                            Text("$cnt 张", fontSize = 11.5.sp, color = C_CLAY, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text("合并后分组名称", fontSize = 12.sp, color = C_SUB, modifier = Modifier.padding(horizontal = 18.dp))
                        TextField(
                            value = mergedName,
                            onValueChange = { mergedName = it },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            singleLine = true,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("将合并为 1 组 · 共 $totalCount 张", fontSize = 11.sp, color = C_CLAY,
                            modifier = Modifier.padding(horizontal = 18.dp))
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(999.dp)).background(Color(0xFFE3DDD3)).clickable { mergeDialog = false }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Text("取消", fontSize = 13.sp, color = C_SUB, fontWeight = FontWeight.SemiBold)
                    }
                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(999.dp)).background(Brush.linearGradient(0f to G1, 1f to G2)).clickable { doMerge(mergedName) }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Text("确认合并", fontSize = 13.sp, color = C_WHITE, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    // ===== 合并逻辑 =====
    private fun doMerge(newName: String) {
        if (mergePick.size < 2) return
        val picked = groups.filter { g -> mergePick.contains(g.optInt("id", -1) ?: -1) }
        if (picked.size < 2) { mergeDialog = false; return }
        // 主组：优先已匹配人名的组，其次人数最多的组
        val main = picked.firstOrNull { it.optString("matched_person_name", "").isNotEmpty() }
            ?: picked.maxByOrNull { it.optInt("photo_count", 0) }
            ?: picked.first()
        val mainId = main.optInt("id", -1) ?: -1
        // 合并 photo_indices
        val allIdx = mutableListOf<Int>()
        picked.forEach { g ->
            val arr = g.optJSONArray("photo_indices")
            if (arr != null) for (i in 0 until arr.length()) allIdx.add(arr.optInt(i))
        }
        val sortedIdx = allIdx.distinct().sorted()
        val idxArr = JSONArray()
        sortedIdx.forEach { idxArr.put(it) }
        val newGroup = JSONObject()
        newGroup.put("id", mainId)
        newGroup.put("photo_indices", idxArr)
        newGroup.put("photo_count", sortedIdx.size)
        newGroup.put("sample_index", main.optInt("sample_index", sortedIdx.firstOrNull() ?: 0))
        // 组名：用新名称，若匹配已有 person 保留 matched 信息
        val finalName = newName.trim().ifEmpty { main.optString("matched_person_name", "").ifEmpty { "人脸组 ${mainId + 1}" } }
        val matchedPersonId = main.optString("matched_person_id", "")
        if (matchedPersonId.isNotEmpty()) {
            newGroup.put("matched_person_id", matchedPersonId)
            newGroup.put("matched_person_name", finalName)
        } else {
            // 未匹配已有 person：把合并后的自定义名写入 matched_person_name，
            // 上传时后端将按此名新建/归并聚类
            newGroup.put("matched_person_name", finalName)
        }
        // 更新 groups：删除被合并的组，加入合并组
        val mergeIds = mergePick.toSet()
        val keep = groups.filter { g -> !mergeIds.contains(g.optInt("id", -1) ?: -1) }
        groups = keep + newGroup
        mergedCount += mergeIds.size - 1
        // 更新上传选择：合并组加入 selected（若原来选了其中任一），移除被合并组
        val hadSelected = mergeIds.any { selected.contains(it) }
        mergeIds.forEach { selected.remove(it) }
        if (hadSelected) selected.add(mainId)
        // 退出合并模式
        mergeDialog = false
        exitMergeMode()
    }

    private fun exitMergeMode() {
        mergeMode = false
        mergePick.clear()
    }

    // ===== 3 上传中 =====
    @Composable
    private fun UploadingScreen(scope: CoroutineScope) {
        Column(modifier = Modifier.fillMaxSize().padding(top = pageData.statusBarHeight.dp).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(44.dp))
            Text("正在上传所选照片", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = C_TEXT)
            Text("已选 ${selected.size} 组 · ${selectedPhotoCount()} 张", fontSize = 12.5.sp, color = C_SUB, modifier = Modifier.padding(top = 5.dp))
            Spacer(Modifier.height(24.dp))
            Box(modifier = Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(99.dp)).background(Color(0xFFE3DDD3))) {
                Box(modifier = Modifier.fillMaxWidth(uploadPct.coerceIn(0f, 1f)).height(14.dp).clip(RoundedCornerShape(99.dp)).background(Brush.linearGradient(listOf(G1, G2))))
            }
            Text("${(uploadPct * 100).toInt()}%", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = C_CLAY, modifier = Modifier.padding(top = 10.dp))
            if (uploadResult.isNotEmpty()) {
                Text(uploadResult, fontSize = 12.sp, color = C_SUB, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp))
            }
        }
    }

    // ===== 4 完成 =====
    @Composable
    private fun DoneScreen() {
        Column(modifier = Modifier.fillMaxSize().padding(top = pageData.statusBarHeight.dp).padding(horizontal = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(60.dp))
            Box(modifier = Modifier.size(96.dp).clip(CircleShape).background(Brush.linearGradient(0f to Color(0xFF9CC5A5), 1f to Color(0xFF7FA88A))), contentAlignment = Alignment.Center) {
                Text("\u2713", fontSize = 46.sp, color = C_WHITE, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(24.dp))
            Text("上传完成", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = C_TEXT)
            Text(
                if (uploadedReal >= 0) "已成功上传 $uploadedReal 张照片${if (skippedReal > 0) "，跳过 $skippedReal 张已上传过的" else ""}"
                else "已成功上传 ${selectedPhotoCount()} 张照片",
                fontSize = 13.sp, color = C_SUB, lineHeight = 20.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp),
            )
            if (uploadResult.isNotEmpty() && uploadedReal != selectedPhotoCount()) {
                Text(uploadResult, fontSize = 11.5.sp, color = C_SUB, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(30.dp))
            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(999.dp)).background(Brush.linearGradient(0f to G1, 1f to G2)).clickable { resetAll() }.padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                Text("完成", color = C_WHITE, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    // ===== 状态 =====
    private var step by mutableStateOf(0)
    private val bridge = lazy { acquireModule<UploadBridgeModule>(UploadBridgeModule.MODULE_NAME) }
    private var photos by mutableStateOf<List<JSONObject>>(emptyList())
    private var scanPct by mutableStateOf(0f)
    private var scanStage by mutableStateOf("正在准备…")
    private var taskId by mutableStateOf("")
    private var groups by mutableStateOf<List<JSONObject>>(emptyList())
    private var otherIndices by mutableStateOf<List<Int>>(emptyList())
    /** 后端识别接口返回的实际处理照片数（排查"扫描 300 但分组不足 300"的差值用） */
    private var scanTotal by mutableStateOf(0)
    private val selected = mutableStateListOf<Int>()
    private var uploadPct by mutableStateOf(0f)
    private var uploadResult by mutableStateOf("")
    private var uploadedReal by mutableStateOf(-1)  // 服务器实际入库数（-1 表示未知）
    private var skippedReal by mutableStateOf(0)   // 服务器跳过的已上传数
    private var errMsg by mutableStateOf("")
    private var previewGroup by mutableStateOf<JSONObject?>(null)
    private var zoomPhotoIdx by mutableStateOf(-1) // 放大查看的照片索引（-1 关闭）
    private val deselectedIndices = mutableStateListOf<Int>() // 预览弹框内被单独取消选择的照片索引
    // 合并交互：长按分组进入合并选择模式，点击多选，底部合并
    private var mergeMode by mutableStateOf(false)
    private val mergePick = mutableStateListOf<Int>()
    private var mergeDialog by mutableStateOf(false) // 合并确认弹框
    private var mergedCount by mutableStateOf(0)      // 已合并的碎片分组数（合并统计条）
    private var mergeTab by mutableStateOf(MergeTab.PICK) // 当前模式 tab（对齐原型三模式）

    // ===== 合并模式 tab（对齐原型：拖拽合并 / 长按多选 / 相似度推荐）=====
    private enum class MergeTab(val label: String, val tip: String) {
        DRAG("🖐️ 拖拽合并", "按住分组卡片，拖到目标分组上松手即可合并。合并前会弹出确认。"),
        PICK("☝️ 长按多选", "长按第一个分组进入选择，再点其他分组（可多点）。选好后点底部「合并所选」。"),
        SMART("✨ 相似度推荐", "系统自动比对相似度，高相似分组会显示推荐标签，点标签一键合并。"),
    }

    private fun totalFacePhotos(): Int = groups.sumOf { it.optInt("photo_count", 0) }
    private fun selectedPhotoCount(): Int {
        val selectedIds = selected.flatMap { gid ->
            groups.firstOrNull { (it.optInt("id", -1) ?: -1) == gid }?.let { g ->
                val arr = g.optJSONArray("photo_indices")
                if (arr != null) (0 until arr.length()).mapNotNull { arr.optInt(it) } else emptyList()
            } ?: emptyList()
        }.toSet()
        val offInSelected = deselectedIndices.count { selectedIds.contains(it) }
        return (selectedIds.size - offInSelected).coerceAtLeast(0)
    }
    private fun toggleAll() {
        if (selected.size == groups.size) selected.clear()
        else { selected.clear(); groups.forEach { selected.add(it.optInt("id", -1) ?: -1) } }
    }

    // ===== 逻辑 =====
    private fun startScan(scope: CoroutineScope) {
        errMsg = ""; step = 1; scanPct = 0f; scanStage = "正在扫描相册照片…"
        scope.launch {
            try {
                val res = bridge.value.scanGallery(300)
                val jo = JSONObject(res)
                if (!jo.optBoolean("success", false)) {
                    if (jo.optBoolean("needPermission", false)) { errMsg = "需要相册权限，请授权后重试"; step = 0; return@launch }
                    errMsg = "扫描失败：${jo.optString("error", "未知错误")}"; step = 0; return@launch
                }
                val arr = jo.optJSONArray("photos") ?: JSONArray()
                photos = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
                if (photos.isEmpty()) {
                    val excluded = jo.optInt("excluded_uploaded", 0)
                    errMsg = if (excluded > 0) "相册最近照片均已上传过，无需重复上传" else "相册中没有找到照片"
                    step = 0; return@launch
                }
                scanPct = 0.3f; scanStage = "正在上传缩略图进行人脸识别…"
                val thumbs = photos.mapNotNull { it.optString("thumb", "").takeIf { t -> t.isNotEmpty() } }
                val uploadDone = CompletableDeferred<String>()
                bridge.value.scanUpload(thumbs) { res -> uploadDone.complete(res?.toString() ?: "") }
                val upRes = JSONObject(uploadDone.await())
                taskId = upRes.optString("taskId", "")
                if (taskId.isEmpty()) { errMsg = "识别任务提交失败"; step = 0; return@launch }
                var tries = 0
                while (tries < 900) {
                    delay(3000)
                    val body = bridge.value.scanPoll(taskId)
                    val st = JSONObject(body)
                    val status = st.optString("status", "")
                    if (status == "done") {
                        scanTotal = st.optInt("total", 0)
                        val ga = st.optJSONArray("groups") ?: JSONArray()
                        groups = (0 until ga.length()).mapNotNull { ga.optJSONObject(it) }
                        val otherArr = st.optJSONArray("other")
                        otherIndices = if (otherArr != null) (0 until (otherArr.length())).mapNotNull { otherArr.optInt(it) } else emptyList()
                        scanPct = 1f
                        selected.clear()
                        groups.forEach { selected.add(it.optInt("id", -1) ?: -1) }
                        step = 2; return@launch
                    }
                    if (status == "error") { errMsg = "识别失败：${st.optString("msg", "未知错误")}"; step = 0; return@launch }
                    tries++
                }
                errMsg = "识别超时，请重试"; step = 0
            } catch (e: Throwable) { errMsg = "扫描出错：${e.message ?: "未知"}"; step = 0 }
        }
    }

    private fun startUpload(scope: CoroutineScope) {
        errMsg = ""; step = 3; uploadPct = 0f; uploadResult = ""
        scope.launch {
            try {
                val chosenIds = selected.flatMap { gid ->
                    groups.firstOrNull { (it.optInt("id", -1) ?: -1) == gid }?.let { g ->
                        val arr = g.optJSONArray("photo_indices")
                        if (arr != null) (0 until arr.length()).mapNotNull { arr.optInt(it) } else emptyList()
                    } ?: emptyList()
                }.distinct().filter { !deselectedIndices.contains(it) }
                if (chosenIds.isEmpty()) { errMsg = "请选择要上传的人脸分组"; step = 2; return@launch }
                val photosToUpload = chosenIds.mapNotNull { i -> photos.getOrNull(i) }

                // v2：按分组传参 —— 每组记录组名 + 该组照片在本批上传列表中的 0-based 下标，
                // 后端据此把同组照片的人脸归入同一聚类（老版本不传 groups 后端行为不变）
                val groupParams = mutableListOf<JSONObject>()
                selected.forEach { gid ->
                    val g = groups.firstOrNull { (it.optInt("id", -1) ?: -1) == gid } ?: return@forEach
                    val arr = g.optJSONArray("photo_indices") ?: return@forEach
                    val indices = mutableListOf<Int>()
                    for (k in 0 until arr.length()) {
                        val pi = arr.optInt(k)
                        val pos = chosenIds.indexOf(pi)
                        if (pos >= 0) indices.add(pos)
                    }
                    if (indices.isEmpty()) return@forEach
                    val name = g.optString("matched_person_name", "").ifEmpty { "人脸组 ${gid + 1}" }
                    val idxArr = JSONArray()
                    indices.forEach { idxArr.put(it) }
                    groupParams.add(JSONObject().apply { put("name", name); put("indices", idxArr) })
                }

                val done = CompletableDeferred<String>()
                bridge.value.batchUpload(photosToUpload, groupParams.takeIf { it.isNotEmpty() }) { res -> done.complete(res?.toString() ?: "") }
                val body = done.await()
                val jo = JSONObject(body)
                uploadedReal = jo.optInt("uploaded", 0)
                skippedReal = jo.optInt("skipped", 0)
                uploadResult = if (jo.optBoolean("success", false)) {
                    val uploaded = jo.optInt("uploaded", 0)
                    val failed = jo.optInt("failed", 0)
                    val skipped = jo.optInt("skipped", 0)
                    buildString {
                        append("成功 $uploaded 张")
                        if (skipped > 0) append("，跳过 $skipped 张已上传过的")
                        if (failed > 0) append("，失败 $failed 张")
                    }
                } else "上传失败：${jo.optString("error", "未知")}"
                step = 4
            } catch (e: Throwable) { uploadResult = "上传出错：${e.message ?: "未知"}"; step = 4 }
        }
    }

    private fun resetAll() {
        step = 0; errMsg = ""; groups = emptyList(); otherIndices = emptyList(); selected.clear()
        scanPct = 0f; scanStage = "正在准备…"; uploadResult = ""; uploadPct = 0f; taskId = ""; previewGroup = null
        uploadedReal = -1; skippedReal = 0
        mergeMode = false; mergePick.clear(); mergeDialog = false; mergedCount = 0; mergeTab = MergeTab.PICK
        deselectedIndices.clear()
    }
}