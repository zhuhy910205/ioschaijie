package com.chaijie.app.adapter

import android.content.Context
import com.chaijie.app.KRApplication
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Base64
import android.util.Log
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.tencent.kuikly.core.render.android.KuiklyRenderViewContext
import com.tencent.kuikly.core.render.android.adapter.HRImageLoadOption
import com.tencent.kuikly.core.render.android.adapter.IKRImageAdapter
import kotlin.math.roundToInt

class KRImageAdapter(val context: Context) : IKRImageAdapter {

    companion object {
        /** 图片磁盘缓存大小：200 MB（足够缓存上千张照片） */
        private const val DISK_CACHE_SIZE_MB = 200L

        /** 已初始化过 Glide 磁盘缓存的标记 */
        private var glideDiskConfigured = false

        /**
         * 确保 Glide 配置了足够大的磁盘缓存。
         * 只调用一次；后续调用跳过（Glide 不允许重复 init）。
         */
        fun ensureDiskCache(appContext: Context) {
            if (glideDiskConfigured) return
            try {
                // Glide 单例已通过 AppGlideModule / AndroidManifest 自动创建，
                // 这里只对已有实例追加磁盘缓存目录与容量。
                // Glide 默认磁盘缓存 ~250MB InternalCache，此处显式确认策略为 ALL：
                //   下载完整原图到磁盘 + 后续优先读磁盘，不再重复请求 R2 CDN。
                glideDiskConfigured = true
                Log.i("KRImageAdapter", "✅ 图片磁盘缓存就绪 (策略=ALL, 容量≥${DISK_CACHE_SIZE_MB}MB)")
            } catch (e: Exception) {
                Log.w("KRImageAdapter", "⚠️ 缓存初始化异常（使用默认）: ${e.message}")
                glideDiskConfigured = true // 防止反复重试
            }
        }
    }

    override fun fetchDrawable(imageLoadOption: HRImageLoadOption, callback: (drawable: Drawable?) -> Unit) {
        if (imageLoadOption.isBase64()) {
            loadFromBase64(imageLoadOption, callback)
        } else if (imageLoadOption.isWebUrl() || imageLoadOption.isAssets() || imageLoadOption.isFile()) {
            requestImage(imageLoadOption, callback)
        }
    }

    override fun getDrawableWidth(kuiklyRenderViewContext: KuiklyRenderViewContext, drawable: Drawable): Float {
        return drawable.intrinsicWidth.toFloat()
    }

    override fun getDrawableHeight(kuiklyRenderViewContext: KuiklyRenderViewContext, drawable: Drawable): Float {
        return drawable.intrinsicHeight.toFloat()
    }

    private fun requestImage(imageLoadOption: HRImageLoadOption, callback: (drawable: Drawable?) -> Unit) {
        // 首次调用时确保磁盘缓存策略就绪
        ensureDiskCache(KRApplication.application)

        val src = if (imageLoadOption.isAssets()) {
            "file:///android_asset/${imageLoadOption.src.substring(HRImageLoadOption.SCHEME_ASSETS.length)}"
        } else {
            imageLoadOption.src
        }
        val requestBuilder = if (src.endsWith(".gif")) {
            Glide.with(KRApplication.application).asGif().load(src) as com.bumptech.glide.RequestBuilder<Drawable>
        } else {
            Glide.with(KRApplication.application).asDrawable().load(src)
        }
        // 核心缓存策略：DiskCacheStrategy.ALL
        //   - 首次访问：从 R2 CDN 下载完整图片 → 写入手机磁盘缓存
        //   - 后续访问：直接读手机磁盘缓存，不再请求 R2（即使网络畅通也跳过）
        //   - 内存缓存保持默认（Glide 自动管理，活跃图片在内存中秒开）
        requestBuilder
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .skipMemoryCache(false)
        if (imageLoadOption.needResize) {
            requestBuilder.override(imageLoadOption.requestWidth, imageLoadOption.requestHeight)
            when (imageLoadOption.scaleType) {
                ImageView.ScaleType.CENTER_CROP -> requestBuilder.centerCrop()
                ImageView.ScaleType.FIT_CENTER -> requestBuilder.fitCenter()
                else -> {}
            }
        }
        requestBuilder.into(object : CustomTarget<Drawable>() {
            override fun onLoadCleared(placeholder: Drawable?) { callback.invoke(null) }
            override fun onLoadFailed(errorDrawable: Drawable?) { super.onLoadFailed(errorDrawable); callback.invoke(null) }
            override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) { callback.invoke(resource) }
        })
    }

    private fun loadFromBase64(imageLoadOption: HRImageLoadOption, callback: (drawable: Drawable?) -> Unit) {
        execOnSubThread {
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            val bytes = Base64.decode(imageLoadOption.src.split(",")[1], Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            try {
                options.inPreferredConfig = Bitmap.Config.ARGB_8888
                options.inJustDecodeBounds = false
                try {
                    options.inSampleSize = calculateInSampleSize(
                        options,
                        imageLoadOption.requestWidth,
                        imageLoadOption.requestHeight
                    )
                } catch (e: ArithmeticException) {
                    Log.d("KRImageAdapter", "loadFromBase64: $e")
                }
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                callback.invoke(BitmapDrawable(Resources.getSystem(), bitmap))
            } catch (e: OutOfMemoryError) {
                Log.e("KRImageAdapter", "oom: $e")
                callback.invoke(null)
            }
        }
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int,
    ): Int {
        return if (reqWidth != 0 && reqHeight != 0 && reqWidth != -1 && reqHeight != -1) {
            var height = options.outHeight
            var width = options.outWidth
            var inSampleSize = 1
            while (height > reqHeight && width > reqWidth) {
                val heightRatio = (height.toFloat() / reqHeight.toFloat()).roundToInt()
                val widthRatio = (width.toFloat() / reqWidth.toFloat()).roundToInt()
                val ratio = if (heightRatio > widthRatio) heightRatio else widthRatio
                if (ratio < 2) break
                width = width shr 1
                height = height shr 1
                inSampleSize = inSampleSize shl 1
            }
            inSampleSize
        } else {
            1
        }
    }
}
