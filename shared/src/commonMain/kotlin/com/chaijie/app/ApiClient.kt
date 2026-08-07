package com.chaijie.app

import com.tencent.kuikly.compose.ComposeContainer
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * 通过 Kuikly NetworkModule 发起 GET 请求。
 * 注意：NetworkModule 在 willInit 之后才注册，必须在 created() 或之后调用。
 */
fun ComposeContainer.getJson(
    url: String,
    onResult: (success: Boolean, data: JSONObject) -> Unit
) {
    acquireModule<NetworkModule>(NetworkModule.MODULE_NAME).requestGet(url, JSONObject()) { data, success, _, _ ->
        onResult(success, data)
    }
}

/** POST 请求（搜索、分析等），NetworkModule 在 created() 之后才注册 */
fun ComposeContainer.postJson(
    url: String,
    body: JSONObject,
    onResult: (success: Boolean, data: JSONObject) -> Unit
) {
    // Kuikly NetworkModule 的 POST 不会自动带 Content-Type，需显式声明 application/json，
    // 否则后端 Flask 的 request.get_json() 收不到 JSON 会返回 500，前端判定 success=false。
    val headers = JSONObject().apply { put("Content-Type", "application/json") }
    acquireModule<NetworkModule>(NetworkModule.MODULE_NAME)
        .httpRequest(url, true, body, headers, null, 30) { data, success, _, _ ->
            onResult(success, data)
        }
}

/**
 * 兼容两种后端返回结构：
 * - chaijie /api/images -> { images: [...] }
 * - video_studio /api/videos -> 顶层数组，被 NetworkModule 兜底包成 { data: "[...]" }
 */
fun parseArray(data: JSONObject): JSONArray {
    val direct = data.optJSONArray("images")
    if (direct != null) return direct
    val wrapped = data.optString("data", "")
    if (wrapped.isNotEmpty()) {
        return try {
            JSONArray(wrapped)
        } catch (_: Throwable) {
            JSONArray()
        }
    }
    return JSONArray()
}

/**
 * 取列表缩略图 URL（缩略图优先，fallback 优化图/原图）。
 * - thumbnail_path：后端返回的真实缩略图（/static/cache/thumbnails/thumb_*.webp）
 * - fallback：cloud_optimized_url 等（缩略图未生成时用）
 */
fun thumbUrl(thumbPath: String, fallback: String): String {
    if (thumbPath.isNotEmpty()) {
        return if (thumbPath.startsWith("http")) thumbPath else ApiConfig.CHAIJIE_BASE + thumbPath
    }
    return fallback
}
