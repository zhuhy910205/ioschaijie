package com.chaijie.app

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

/**
 * 相册扫描：读取最近 limit 张照片（按添加时间倒序），生成缩略图 jpeg 到本地缓存。
 * 供「+ 上传」功能：扫描 → 缩略图 → 后端人脸识别分组。
 */
object GalleryScanner {

    data class PhotoInfo(
        val id: Long,
        val date: Long,
        val thumbPath: String,
        val width: Int,
        val height: Int,
        val mime: String,
    )

    /** 扫描相册最近 limit 张照片（排除已上传的），返回带本地缩略图路径的元数据列表 */
    fun scanRecent(context: Context, limit: Int = 300, excludeIds: Set<Long> = emptySet()): List<PhotoInfo> {
        val out = mutableListOf<PhotoInfo>()
        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.MIME_TYPE,
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        android.util.Log.i("KRBridge", "scanRecent start, sdk=${Build.VERSION.SDK_INT}, uri=$collection, exclude=${excludeIds.size}")
        try {
            val cursor = resolver.query(collection, projection, null, null, sortOrder)
            android.util.Log.i("KRBridge", "scanRecent query cursor=${cursor?.count}")
            cursor?.use { c ->
                var n = 0
                var thumbFail = 0
                var skipUploaded = 0
                while (c.moveToNext() && n < limit) {
                    val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                    // 已上传的照片直接跳过（不生成缩略图、不参与分组）
                    if (excludeIds.contains(id)) {
                        skipUploaded++
                        continue
                    }
                    val dateAdded = c.getLong(c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)) * 1000L
                    var dateTaken = c.getLong(c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN))
                    if (dateTaken <= 0) dateTaken = dateAdded
                    val w = c.getInt(c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH))
                    val h = c.getInt(c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT))
                    val mime = c.getString(c.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)) ?: ""
                    val thumb = makeThumb(resolver, id, w, h)
                    if (thumb != null) {
                        out.add(PhotoInfo(id, dateTaken, thumb, w, h, mime))
                        n++
                    } else {
                        thumbFail++
                        if (thumbFail <= 3) android.util.Log.w("KRBridge", "thumb fail id=$id w=$w h=$h mime=$mime")
                    }
                }
                android.util.Log.i("KRBridge", "scanRecent rows=${c.count} ok=$n thumbFail=$thumbFail skipUploaded=$skipUploaded")
            }
        } catch (e: Exception) {
            android.util.Log.e("KRBridge", "scanRecent query exception: ${e.message}", e)
        }
        android.util.Log.i("KRBridge", "scanRecent done, total=${out.size}")
        return out
    }

    /** 生成缩略图 jpeg（最长边 512）到 cache/gallery_thumbs/{id}.jpg */
    private fun makeThumb(resolver: ContentResolver, id: Long, w: Int, h: Int): String? {
        val dir = File(KRApplication.application.cacheDir, "gallery_thumbs")
        dir.mkdirs()
        val f = File(dir, "$id.jpg")
        if (f.exists() && f.length() > 0) return f.absolutePath
        return try {
            val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            var mw = if (w > 0) w else bounds.outWidth
            var mh = if (h > 0) h else bounds.outHeight
            while (maxOf(mw, mh) / sample > 512) sample *= 2
            val dOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, dOpts) } ?: return null
            FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 82, it) }
            bmp.recycle()
            f.absolutePath
        } catch (e: Exception) {
            android.util.Log.w("KRBridge", "makeThumb exception id=$id: ${e.message}")
            null
        }
    }

    /** 通过相册 id 打开原图输入流（用于批量上传原图） */
    fun openOriginal(resolver: ContentResolver, id: Long): java.io.InputStream? {
        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
        return try {
            resolver.openInputStream(uri)
        } catch (e: Exception) {
            null
        }
    }
}
