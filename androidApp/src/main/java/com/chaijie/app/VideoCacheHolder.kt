package com.chaijie.app

import android.content.Context
import androidx.media3.database.ExoDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * 全局共享视频磁盘缓存（SimpleCache 单例）。
 * 全屏播放器与 feed 播放器共用同一实例，避免 "Another SimpleCache instance uses the folder" 冲突。
 * 300MB LRU，播放过的视频落盘，二次播放秒开且离线可用。
 */
object VideoCacheHolder {

    @Volatile
    private var instance: SimpleCache? = null

    fun get(context: Context): SimpleCache {
        instance?.let { return it }
        synchronized(this) {
            instance?.let { return it }
            val appCtx = context.applicationContext
            val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES)
            val db = ExoDatabaseProvider(appCtx)
            val cache = SimpleCache(File(appCtx.cacheDir, "video_cache"), evictor, db)
            instance = cache
            return cache
        }
    }

    private const val MAX_CACHE_BYTES = 300L * 1024 * 1024 // 300MB
}
