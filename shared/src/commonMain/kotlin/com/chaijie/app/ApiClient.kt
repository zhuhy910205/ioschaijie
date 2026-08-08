package com.chaijie.app

import com.tencent.kuikly.compose.ComposeContainer
import com.tencent.kuikly.core.module.CallbackFn
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.module.Module
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
 * 云端图片本地缓存桥接：把云端缩略图/原图下载到本地缓存，返回 file:// 路径。
 * 有缓存直接返回（cached=true），无缓存先下载（cached=false）。
 * 供各页面（AlbumPage 等）在 createExternalModules 里注册。
 */
class CacheBridgeModule : Module() {
    override fun moduleName(): String = "HRBridgeModule"
    fun cacheRemoteThumb(url: String, photoId: String): String {
        return try {
            val res = syncToNativeMethod("cacheRemoteThumb", JSONObject().apply { put("url", url); put("photoId", photoId) }, null)
            res ?: ""
        } catch (e: Throwable) { "" }
    }
    fun cacheRemoteOriginal(url: String, photoId: String): String {
        return try {
            val res = syncToNativeMethod("cacheRemoteOriginal", JSONObject().apply { put("url", url); put("photoId", photoId) }, null)
            res ?: ""
        } catch (e: Throwable) { "" }
    }
    /** 异步下载原图到本地缓存（原生子线程下载，回调返回 path），不阻塞 UI → 加载动画可渲染 */
    fun asyncCacheRemoteOriginal(url: String, photoId: String, cb: CallbackFn?) {
        asyncToNativeMethod("asyncCacheRemoteOriginal", JSONObject().apply { put("url", url); put("photoId", photoId) }, cb)
    }
    /** 从本地相册拷贝原图到缓存（手机上传的照片本地就有 → 秒开），返回 file:// 或空 */
    fun copyOriginal(photoId: Long): String {
        if (photoId <= 0) return ""
        return try {
            syncToNativeMethod("copyOriginal", JSONObject().apply { put("id", photoId) }, null) ?: ""
        } catch (e: Throwable) { "" }
    }
    /** 立即把相册原图拷入上传队列缓存（秒收），返回 JSON {imported, skipped} */
    fun importToUploadQueue(photoIds: List<Long>): String {
        val arr = JSONArray()
        photoIds.forEach { arr.put(it) }
        return try {
            syncToNativeMethod("importToUploadQueue", JSONObject().apply { put("photoIds", arr) }, null) ?: ""
        } catch (e: Throwable) { "" }
    }
    /** 后台慢慢上传队列，返回 JSON {uploaded, failed, total} */
    fun flushUploadQueue(cb: CallbackFn?) {
        asyncToNativeMethod("flushUploadQueue", JSONObject(), cb)
    }
    companion object { const val MODULE_NAME = "HRBridgeModule" }
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

/**
 * 列表缩略图 URL（CDN 优先，解决滑动卡顿）。
 * 优先用 cloud_optimized_url（R2/Cloudflare CDN 直链，Cache-Control: max-age 长缓存 +
 * CDN 边缘缓存 HIT → 秒开）；cloud_original_url 其次；thumbnail_path（chaijie 本地
 * 动态接口 /api/optimized_image，no-cache + 服务器中转）最后兜底。
 * 修复：手机上传照片之前走 thumbnail_path → 每次滑动经 Flask 中转 → 卡；
 * 云端照片走 CDN → 不卡。统一 CDN 后两类照片都流畅。
 */
fun cdNThumbUrl(
    cloudOptimized: String,
    cloudOriginal: String,
    thumbPath: String
): String {
    if (cloudOptimized.isNotEmpty()) return cloudOptimized
    if (cloudOriginal.isNotEmpty()) return cloudOriginal
    return thumbUrl(thumbPath, "")
}
