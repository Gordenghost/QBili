package com.qbili

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.qbili.core.QBiliLog
import com.qbili.di.AppContainer

class QBiliApp : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        QBiliLog.init(this, "会话开始 ${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})")
        container = AppContainer(this)
        container.bootstrap()
    }

    /**
     * 图片加载共用一套带 Referer 的 OkHttp，否则部分 B 站图床会返回 403。
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { container.network.imageClient }
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.20)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(200L * 1024 * 1024)
                .build()
        }
        .crossfade(true)
        .respectCacheHeaders(false)
        .build()
}
