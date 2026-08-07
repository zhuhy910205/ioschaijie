package com.chaijie.app.module

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.tencent.kuikly.core.render.android.export.KuiklyRenderBaseModule
import com.tencent.kuikly.core.render.android.export.KuiklyRenderCallback
import com.chaijie.app.GalleryScanner
import com.chaijie.app.HttpMultipart
import com.chaijie.app.KRApplication
import com.chaijie.app.KuiklyRenderActivity
import com.chaijie.app.VideoPlayActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date

class KRBridgeModule : KuiklyRenderBaseModule() {

    companion object {
        const val MODULE_NAME = "HRBridgeModule"
        private const val PREFS_UPLOADED = "chaijie_uploaded"
        private const val KEY_UPLOADED_IDS = "uploaded_photo_ids"
    }

    // ===== 已上传照片 id 本地记录（上传去重：扫描排除 + 上传跳过）=====
    private fun getUploadedIds(): Set<Long> {
        return try {
            val sp = KRApplication.application.getSharedPreferences(PREFS_UPLOADED, android.content.Context.MODE_PRIVATE)
            sp.getStringSet(KEY_UPLOADED_IDS, emptySet())?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    private fun markUploadedIds(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        try {
            val sp = KRApplication.application.getSharedPreferences(PREFS_UPLOADED, android.content.Context.MODE_PRIVATE)
            val cur = getUploadedIds().toMutableSet()
            cur.addAll(ids)
            sp.edit().putStringSet(KEY_UPLOADED_IDS, cur.map { it.toString() }.toSet()).apply()
            Log.i("KRBridge", "markUploadedIds -> total=${cur.size} new=${ids.size}")
        } catch (e: Exception) {
            Log.e("KRBridge", "markUploadedIds error: %s".format(e.message))
        }
    }

    private fun hasGalleryPermission(): Boolean {
        val ctx = KRApplication.application
        val perm = if (Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_IMAGES
        else android.Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestGalleryPermission() {
        val act = activity ?: return
        val perm = if (Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_IMAGES
        else android.Manifest.permission.READ_EXTERNAL_STORAGE
        ActivityCompat.requestPermissions(act, arrayOf(perm), 1001)
    }

    /** 回调安全包装：Kuikly 回调需在主线程触发，避免子线程回调丢失导致 commonMain await 挂起 */
    private fun safeCallback(callback: KuiklyRenderCallback?, result: Map<String, Any?>) {
        val act = activity
        if (act != null) {
            act.runOnUiThread { callback?.invoke(result) }
        } else {
            callback?.invoke(result)
        }
    }

    private fun httpGet(url: String): String? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "GET"
                conn.connectTimeout = 15000
                conn.readTimeout = 300000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (ChaijieApp)")
                if (conn.responseCode in 200..299) conn.inputStream.bufferedReader().use { it.readText() } else null
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun call(method: String, params: String?, callback: KuiklyRenderCallback?): Any? {
        return when (method) {
            "closePage" -> activity?.finish()
            "openPage" -> {
                val paramJSON = JSONObject(params ?: "{}")
                val pageName = paramJSON.optString("pageName", paramJSON.optString("url", ""))
                val pageData = paramJSON.optJSONObject("pageData") ?: JSONObject()
                val ctx = activity ?: KRApplication.application
                KuiklyRenderActivity.start(ctx, pageName, pageData)
            }
            "toast" -> {
                val paramJSON = JSONObject(params ?: "{}")
                Toast.makeText(KRApplication.application, paramJSON.optString("content"), Toast.LENGTH_SHORT).show()
            }
            "playVideo" -> {
                val paramJSON = JSONObject(params ?: "{}")
                val arr = paramJSON.optJSONArray("urls")
                val urls = mutableListOf<String>()
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val u = arr.optString(i, "")
                        if (u.isNotEmpty()) urls.add(u)
                    }
                }
                if (urls.isEmpty()) {
                    paramJSON.optString("url", "").takeIf { it.isNotEmpty() }?.let { urls.add(it) }
                }
                if (urls.isNotEmpty()) {
                    val ctx = activity ?: KRApplication.application
                    VideoPlayActivity.start(ctx, urls, paramJSON.optInt("index", 0), paramJSON.optString("title", ""))
                }
                null
            }
            "log" -> {
                val paramJSON = JSONObject(params ?: "{}")
                Log.i("KuiklyRender", paramJSON.optString("content"))
            }
            "copyToPasteboard" -> {
                val paramJSON = JSONObject(params ?: "{}")
                val content = paramJSON.optString("content")
                val clipboard = KRApplication.application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("kuikly", content))
            }
            "currentTimestamp" -> (System.currentTimeMillis()).toString()
            "dateFormatter" -> {
                val paramJSON = JSONObject(params ?: "{}")
                val data = Date(paramJSON.optLong("timeStamp"))
                SimpleDateFormat(paramJSON.optString("format")).format(data)
            }
            // ===== 上传功能（+ 按钮）=====
            "scanGallery" -> {
                // 同步：扫描相册最近 N 张（跳过已上传的），返回带缩略图路径的列表
                if (!hasGalleryPermission()) {
                    requestGalleryPermission()
                    return JSONObject().put("success", false).put("needPermission", true).toString()
                }
                val limit = JSONObject(params ?: "{}").optInt("limit", 300)
                val excludeIds = getUploadedIds()
                val photos = GalleryScanner.scanRecent(KRApplication.application, limit, excludeIds)
                Log.i("KRBridge", "scanGallery -> %d photos (excluded %d uploaded)".format(photos.size, excludeIds.size))
                val arr = JSONArray()
                for (p in photos) {
                    val o = JSONObject()
                    o.put("id", p.id)
                    o.put("date", p.date)
                    o.put("thumb", p.thumbPath)
                    o.put("width", p.width)
                    o.put("height", p.height)
                    arr.put(o)
                }
                JSONObject().put("success", true).put("count", photos.size)
                    .put("excluded_uploaded", excludeIds.size).put("photos", arr).toString()
            }
            "scanUpload" -> {
                // 异步：上传缩略图 → 后端识别，回调返回 task_id
                val p = JSONObject(params ?: "{}")
                val arr = p.optJSONArray("files")
                val files = mutableListOf<String>()
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        arr.optString(i, "").takeIf { it.isNotEmpty() }?.let { files.add(it) }
                    }
                }
                if (files.isEmpty()) {
                    safeCallback(callback, mapOf("success" to false, "error" to "no files"))
                    return null
                }
                Log.i("KRBridge", "scanUpload -> %d thumbs".format(files.size))
                Thread {
                    try {
                        val body = HttpMultipart.upload(
                            "https://www.zhuyanyou.fun/api/upload/scan", "photos",
                            files.map { it to { File(it).inputStream() } },
                        )
                        if (body != null) {
                            val res = JSONObject(body)
                            val tid = res.optString("task_id", "")
                            Log.i("KRBridge", "scanUpload done taskId=%s".format(tid))
                            safeCallback(callback, mapOf("success" to (tid.isNotEmpty()), "taskId" to tid))
                        } else {
                            safeCallback(callback, mapOf("success" to false, "error" to "upload failed"))
                        }
                    } catch (e: Exception) {
                        Log.e("KRBridge", "scanUpload error: %s".format(e.message))
                        safeCallback(callback, mapOf("success" to false, "error" to (e.message ?: "unknown")))
                    }
                }.start()
                null
            }
            "scanPoll" -> {
                // 同步：轮询识别任务状态，直接返回 body JSON 字符串（httpGet 很快无需异步）
                val tid = JSONObject(params ?: "{}").optString("taskId", "")
                if (tid.isEmpty()) return JSONObject().put("success", false).put("error", "no taskId").toString()
                val body = httpGet("https://www.zhuyanyou.fun/api/upload/scan/$tid")
                body ?: JSONObject().put("success", false).put("error", "poll failed").toString()
            }
            "batchUpload" -> {
                // 异步：批量上传原图入库（photos: [{id, filename}]）
                // v2：支持可选 groups（JSON 数组 [{"name":"组名","indices":[0,1,2]}]），
                //     indices 为 photos 列表中 0-based 序号，后端据此把同组照片归入同一聚类；
                //     老版本不传 groups 时后端行为完全不变。
                // v3：上传前过滤本地已记录的上传照片（配合后端 hash 去重双保险）
                val p = JSONObject(params ?: "{}")
                val arr = p.optJSONArray("photos")
                val groupsArr = p.optJSONArray("groups")
                val resolver = KRApplication.application.contentResolver
                val alreadyUploaded = getUploadedIds()
                val parts = mutableListOf<Pair<String, () -> java.io.InputStream>>()
                val uploadedIds = mutableListOf<Long>()
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val id = o.optLong("id", -1)
                        val name = o.optString("filename", "photo_$id.jpg")
                        if (id > 0) {
                            if (alreadyUploaded.contains(id)) {
                                Log.i("KRBridge", "batchUpload 跳过已上传 photo id=$id")
                                continue
                            }
                            parts.add(name to { GalleryScanner.openOriginal(resolver, id) ?: java.io.ByteArrayInputStream(ByteArray(0)) })
                            uploadedIds.add(id)
                        }
                    }
                }
                if (parts.isEmpty()) {
                    // 全部已被跳过（或本来就没有）→ 视为成功（无新上传）
                    safeCallback(callback, mapOf("success" to true, "body" to JSONObject().put("success", true)
                        .put("uploaded", 0).put("skipped", uploadedIds.size).toString()))
                    return null
                }
                val extraFields = mutableMapOf<String, String>()
                if (groupsArr != null && groupsArr.length() > 0) {
                    extraFields["groups"] = groupsArr.toString()
                    Log.i("KRBridge", "batchUpload groups -> %s".format(groupsArr.toString()))
                }
                Log.i("KRBridge", "batchUpload -> %d originals (of %d, skipped %d)".format(parts.size, arr?.length() ?: 0, arr?.length()?.minus(parts.size) ?: 0))
                Thread {
                    try {
                        val body = HttpMultipart.upload(
                            "https://www.zhuyanyou.fun/api/upload/batch", "photos", parts,
                            extraFields = extraFields,
                        )
                        if (body != null) {
                            // 上传成功 → 记录这批照片为已上传（即使后端部分 skipped 也无妨，去重幂等）
                            markUploadedIds(uploadedIds)
                            safeCallback(callback, mapOf("success" to true, "body" to body))
                        } else {
                            safeCallback(callback, mapOf("success" to false, "error" to "upload failed"))
                        }
                    } catch (e: Exception) {
                        Log.e("KRBridge", "batchUpload error: %s".format(e.message))
                        safeCallback(callback, mapOf("success" to false, "error" to (e.message ?: "unknown")))
                    }
                }.start()
                null
            }
            "copyOriginal" -> {
                // 同步：把相册原图拷贝到本地 cache/originals/{id}.jpg，返回 file:// 路径
                // （Kuikly coil3 不支持 content://，放大查看需本地文件）
                val photoId = JSONObject(params ?: "{}").optLong("id", -1L)
                if (photoId <= 0) return ""
                try {
                    val resolver = KRApplication.application.contentResolver
                    val originalsDir = java.io.File(KRApplication.application.cacheDir, "originals")
                    originalsDir.mkdirs()
                    val outFile = java.io.File(originalsDir, "$photoId.jpg")
                    if (outFile.exists() && outFile.length() > 0) return "file://" + outFile.absolutePath
                    val input = GalleryScanner.openOriginal(resolver, photoId)
                    if (input != null) {
                        input.use { ins ->
                            java.io.FileOutputStream(outFile).use { fos ->
                                val buf = ByteArray(64 * 1024)
                                var n: Int
                                while (ins.read(buf).also { n = it } != -1) fos.write(buf, 0, n)
                            }
                        }
                        Log.i("KRBridge", "copyOriginal -> %s (%d KB)".format(outFile.absolutePath, outFile.length() / 1024))
                        return "file://" + outFile.absolutePath
                    }
                } catch (e: Exception) {
                    Log.e("KRBridge", "copyOriginal error: %s".format(e.message))
                }
                ""
            }
            else -> callback?.invoke(mapOf("code" to -1, "message" to "Method not found"))
        }
    }
}
