package com.qbili.data.repository

import com.qbili.core.stripHtml
import com.qbili.data.remote.api.OfflineVideoApi
import com.qbili.data.remote.cookie.LoginCookies
import com.qbili.data.remote.dto.PlayurlDataDto
import com.qbili.data.remote.dto.VideoViewDto
import com.qbili.domain.download.*
import com.qbili.domain.model.VideoQuality
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.*

class OfflineVideoRepository(private val api: OfflineVideoApi, private val sessions: StateFlow<LoginCookies>, private val json: Json) {
    suspend fun video(session: LoginCookies, aid: Long): DownloadVideo {
        check(aid > 0)
        validate(session)
        val raw = downloadResponseData(api.view(cookie(session), aid))
        validate(session)
        return parseDownloadVideo(raw, json).also { check(it.aid == aid) { "返回的视频与目标不一致" } }
    }

    suspend fun options(session: LoginCookies, aid: Long, page: DownloadPage, quality: Int = 127, audioId: Int = 0): List<DownloadOption> {
        validate(session)
        val raw = downloadResponseData(api.playurl(cookie(session), aid, page.cid, quality, 4048, 0, 1))
        validate(session)
        return parseDownloadOptions(raw, json, page.durationSeconds, audioId)
    }

    suspend fun resolve(session: LoginCookies, task: OfflineDownload): DownloadOption {
        val detail = video(session, task.aid)
        val page = detail.pages.firstOrNull { it.cid == task.cid } ?: error("该分 P 已不在当前视频目录中")
        return options(session, task.aid, page, task.quality, task.audioId).firstOrNull {
            it.quality == task.quality && it.audioId == task.audioId
        } ?: error("当前账号未返回原缓存画质或音轨，请删除后重新选择")
    }

    private fun validate(session: LoginCookies) { check(sessions.value == session) { "账号已变化，请重新操作" } }
    private fun cookie(session: LoginCookies): String {
        if (!session.isLoggedIn) return ""
        val values = listOf("SESSDATA" to session.sessData, "bili_jct" to session.biliJct, "DedeUserID" to session.mid.toString())
        require(values.all { (_, value) -> value == null || value.none { it == ';' || it == '\r' || it == '\n' } }) { "登录信息无效" }
        return values.mapNotNull { (key, value) -> value?.let { "$key=$it" } }.joinToString("; ")
    }
}

internal fun parseDownloadVideo(raw: JsonObject, json: Json): DownloadVideo {
    rejectDownloadRisk(raw)
    val rights = raw["rights"] as? JsonObject ?: error("接口未明确提供缓存权限")
    check((rights["download"] as? JsonPrimitive)?.intOrNull == 1) { "平台未允许缓存该视频" }
    check((raw["redirect_url"] as? JsonPrimitive)?.contentOrNull.isNullOrEmpty()) { "暂不支持番剧或重定向内容缓存" }
    check(listOf("movie", "pay", "ugc_pay", "arc_pay").all { rights[it] == null || (rights[it] as? JsonPrimitive)?.intOrNull == 0 }) {
        "本阶段暂不缓存付费或影视内容"
    }
    val detail = json.decodeFromJsonElement<VideoViewDto>(raw)
    check(detail.aid > 0 && detail.pages.isNotEmpty()) { "视频目录缺失" }
    val pages = detail.pages.filter { it.cid > 0 && it.duration > 0 }.distinctBy { it.cid }.map {
        DownloadPage(it.cid, it.page, stripHtml(it.part).ifBlank { "P${it.page}" }, it.duration)
    }
    check(pages.isNotEmpty()) { "没有有效的分 P" }
    return DownloadVideo(detail.aid, stripHtml(detail.title), detail.owner?.name.orEmpty(), pages)
}

internal fun parseDownloadOptions(raw: JsonObject, json: Json, durationSeconds: Int, audioId: Int = 0): List<DownloadOption> {
    rejectDownloadRisk(raw)
    val preview = raw["is_preview"] as? JsonPrimitive
    check(raw["is_preview"] == null || preview?.intOrNull == 0 || preview?.booleanOrNull == false) { "试看或播放权限未明确，不提供缓存" }
    check(listOf("drm_tech_type", "drm_type").all { raw[it] == null || (raw[it] as? JsonPrimitive)?.intOrNull == 0 } &&
        raw["drm_info"] == null) { "暂不支持加密内容缓存" }
    val standard = raw["dash"] as? JsonObject ?: error("本阶段只支持标准 DASH 音视频缓存，不支持 HLS 或分段旧格式")
    check(standard["video"] is JsonArray && standard["audio"] is JsonArray) { "接口缺少标准音视频轨道列表" }
    val result = json.decodeFromJsonElement<PlayurlDataDto>(buildJsonObject {
        raw["timelength"]?.let { put("timelength", it) }
        put("dash", JsonObject(standard.filterKeys { it in listOf("duration", "video", "audio") }))
    })
    check(result.timelength > 0 && result.timelength >= durationSeconds * 1000L - 2_000) { "未返回完整视频时长" }
    val dash = result.dash ?: error("本阶段只支持标准 DASH 音视频缓存，不支持 HLS 或分段旧格式")
    fun safeUrl(track: com.qbili.data.remote.dto.DashStreamDto): String? =
        (listOf(track.url) + track.backupUrls).firstOrNull(::trustedDownloadUrl)
    val audioTracks = dash.audio.filter { it.id > 0 && it.codecs.startsWith("mp4a") && safeUrl(it) != null }
    val audio = (if (audioId > 0) audioTracks.firstOrNull { it.id == audioId } else audioTracks.maxByOrNull { it.bandwidth })
        ?: error("未返回原选择或可缓存的标准音轨")
    val options = dash.video.filter { it.id > 0 && it.codecid == 7 && it.codecs.startsWith("avc1") && safeUrl(it) != null }
        .distinctBy { it.id }.sortedByDescending { it.id }.map {
            DownloadOption(it.id, VideoQuality.entries.firstOrNull { quality -> quality.qn == it.id }?.label
                ?: "${it.height}P", audio.id, safeUrl(it)!!, safeUrl(audio)!!)
        }
    check(options.isNotEmpty()) { "未返回可缓存的 AVC 画质" }
    return options
}

private fun rejectDownloadRisk(raw: JsonObject) {
    if (raw.containsKey("v_voucher")) throw com.qbili.core.BiliRiskControlException(
        (raw["v_voucher"] as? JsonPrimitive)?.contentOrNull.orEmpty())
    (raw["result"] as? JsonObject)?.let { result ->
        if (result.containsKey("v_voucher")) throw com.qbili.core.BiliRiskControlException(
            (result["v_voucher"] as? JsonPrimitive)?.contentOrNull.orEmpty())
    }
}

internal fun downloadResponseData(raw: JsonObject): JsonObject {
    rejectDownloadRisk(raw)
    val code = (raw["code"] as? JsonPrimitive)?.intOrNull ?: error("接口响应缺少错误码")
    if (code != 0) throw com.qbili.core.BiliApiException(code, (raw["message"] as? JsonPrimitive)?.contentOrNull ?: "缓存接口请求失败")
    val data = raw["data"] as? JsonObject ?: error("缓存接口未返回数据")
    rejectDownloadRisk(data)
    return data
}
