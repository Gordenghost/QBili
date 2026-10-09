package com.qbili.data.repository

import com.qbili.core.QBiliLog
import com.qbili.data.remote.api.VideoApi
import com.qbili.data.remote.danmaku.DanmakuParser
import com.qbili.domain.model.DanmakuItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * 弹幕加载。
 *
 * 接口按 6 分钟一段切片，长视频要拉多段。分段之间互不依赖，
 * 所以并发拉取——串行拉一个一小时的视频要等 10 次往返。
 */
class DanmakuRepository(private val videoApi: VideoApi) {

    /**
     * @param cid 分 P 的 cid（接口参数名叫 oid，但值是 cid）
     * @param durationSeconds 该分 P 时长，用来算需要几段
     * @return 按出现时间升序的弹幕；个别分段失败不影响其余分段
     */
    suspend fun load(cid: Long, durationSeconds: Int): List<DanmakuItem> = coroutineScope {
        val needed = (durationSeconds / SEGMENT_SECONDS) + 1
        val segments = needed.coerceIn(1, MAX_SEGMENTS)
        if (needed > MAX_SEGMENTS) {
            // 宁可少加载也要说出来，避免「后半段没弹幕」被当成 bug
            QBiliLog.w(TAG, "视频过长($durationSeconds 秒)，弹幕只加载前 $MAX_SEGMENTS 段")
        }

        val results = (1..segments).map { index ->
            async {
                runCatching {
                    videoApi.danmakuSegment(type = 1, cid = cid, segmentIndex = index)
                        .use { body -> DanmakuParser.parseSegment(body.bytes()) }
                }.onFailure {
                    QBiliLog.w(TAG, "弹幕第 $index 段加载失败: ${it.message}")
                }.getOrDefault(emptyList())
            }
        }.awaitAll()

        results.flatten()
            .sortedBy { it.progressMillis }
            .also { QBiliLog.i(TAG, "弹幕加载完成: ${it.size} 条 / $segments 段 (cid=$cid)") }
    }

    private companion object {
        const val TAG = "DanmakuRepository"
        const val SEGMENT_SECONDS = 360
        /** 上限 3 小时，防止超长视频一次打出几十个请求 */
        const val MAX_SEGMENTS = 30
    }
}
