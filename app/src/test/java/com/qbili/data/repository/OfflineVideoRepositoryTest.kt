package com.qbili.data.repository

import com.qbili.core.BiliApiException
import com.qbili.core.BiliRiskControlException
import com.qbili.data.remote.api.OfflineVideoApi
import com.qbili.data.remote.cookie.LoginCookies
import com.qbili.domain.download.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

val offlineTestJson = Json { ignoreUnknownKeys = true }
fun offlineVideoFixture(): JsonObject = offlineTestJson.parseToJsonElement("""{"aid":2,"cid":3,"title":"测试","rights":{"download":1,"pay":0},"pages":[{"cid":3,"page":1,"part":"P1","duration":60}]}""").jsonObject
fun offlinePlayFixture(): JsonObject = offlineTestJson.parseToJsonElement("""{"is_preview":0,"timelength":60000,"dash":{"video":[{"id":80,"codecid":7,"codecs":"avc1.640028","base_url":"https://a.bilivideo.com/video","height":1080},{"id":120,"codecid":13,"codecs":"av01","base_url":"https://a.bilivideo.com/av1"}],"audio":[{"id":30280,"codecs":"mp4a.40.2","bandwidth":100,"base_url":"https://a.bilivideo.com/audio"}]}}""").jsonObject
fun wrappedOffline(raw: JsonObject): JsonObject = buildJsonObject { put("code", 0); put("data", raw) }

class OfflineVideoRepositoryTest {
    @Test fun offersOnlyRealAvcTracksNotAdvertisedQuality() {
        val options = parseDownloadOptions(offlinePlayFixture(), offlineTestJson, 60)
        assertEquals(listOf(80), options.map { it.quality })
        assertEquals(30280, options.first().audioId)
    }
    @Test fun previewAndTruncatedDurationAreRejected() {
        val raw = offlinePlayFixture()
        listOf(JsonObject(raw + ("is_preview" to JsonPrimitive(1))),
            JsonObject(raw + ("timelength" to JsonPrimitive(30000)))).forEach {
            assertThrows(IllegalStateException::class.java) { parseDownloadOptions(it, offlineTestJson, 60) }
        }
        assertEquals(1, parseDownloadOptions(JsonObject(raw + ("is_preview" to JsonPrimitive(false))), offlineTestJson, 60).size)
        assertEquals(1, parseDownloadOptions(JsonObject(raw - "is_preview"), offlineTestJson, 60).size)
        assertThrows(IllegalStateException::class.java) {
            parseDownloadOptions(JsonObject((raw - "is_preview") + ("timelength" to JsonPrimitive(30000))), offlineTestJson, 60)
        }
    }
    @Test fun rejectsMissingOrDisallowedDownloadRightsAndPaidVideo() {
        val raw = offlineVideoFixture()
        listOf(JsonObject(raw - "rights"), JsonObject(raw + ("rights" to buildJsonObject { put("download", 0) })),
            JsonObject(raw + ("rights" to buildJsonObject { put("download", 1); put("ugc_pay", 1) })),
            JsonObject(raw + ("redirect_url" to JsonPrimitive("https://www.bilibili.com/bangumi/play/ep1")))).forEach {
            assertThrows(IllegalStateException::class.java) { parseDownloadVideo(it, offlineTestJson) }
        }
        assertEquals(3, parseDownloadVideo(raw, offlineTestJson).pages.first().cid)
    }
    @Test fun rejectsEncryptedAndUnknownStreams() {
        val raw = offlinePlayFixture()
        listOf(JsonObject(raw + ("drm_tech_type" to JsonPrimitive(2))), JsonObject(raw - "dash"),
            JsonObject(raw + ("drm_info" to buildJsonObject {}))).forEach {
            assertThrows(IllegalStateException::class.java) { parseDownloadOptions(it, offlineTestJson, 60) }
        }
    }
    @Test fun zeroCodeVoucherIsAnErrorAtEitherLevel() {
        val risk = buildJsonObject { put("v_voucher", "test") }
        assertThrows(BiliRiskControlException::class.java) { downloadResponseData(risk) }
        assertThrows(BiliRiskControlException::class.java) { downloadResponseData(wrappedOffline(risk)) }
        assertThrows(BiliRiskControlException::class.java) { downloadResponseData(buildJsonObject { put("code", 0); put("result", risk) }) }
        assertThrows(BiliApiException::class.java) { downloadResponseData(buildJsonObject { put("code", -403); put("message", "禁止") }) }
        assertThrows(IllegalStateException::class.java) { downloadResponseData(buildJsonObject { put("code", 0) }) }
    }
    @Test fun fixesRequestCookieAndRejectsOldAccountResponse() = runBlocking {
        val session = LoginCookies("old", "csrf", 7)
        val sessions = MutableStateFlow(session)
        var cookie = ""
        val api = object : OfflineVideoApi {
            override suspend fun view(cookieHeader: String, aid: Long): JsonObject {
                cookie = cookieHeader
                sessions.value = LoginCookies("new", "new", 8)
                return wrappedOffline(offlineVideoFixture())
            }
            override suspend fun playurl(cookie: String, aid: Long, cid: Long, quality: Int, fnval: Int, fnver: Int, fourk: Int) = wrappedOffline(offlinePlayFixture())
        }
        val repository = OfflineVideoRepository(api, sessions, offlineTestJson)
        try { repository.video(session, 2); fail("old request must be rejected") } catch (_: IllegalStateException) { }
        assertEquals("SESSDATA=old; bili_jct=csrf; DedeUserID=7", cookie)
    }
    @Test fun ignoresUnusedNullDolbyMetadataFromRealApi() {
        val raw = offlinePlayFixture()
        val dash = JsonObject(raw["dash"]!!.jsonObject + ("dolby" to buildJsonObject { put("type", 0); put("audio", JsonNull) }))
        assertEquals(1, parseDownloadOptions(JsonObject(raw + ("dash" to dash)), offlineTestJson, 60).size)
    }
    @Test fun preservesSelectedAudioWhenNewHigherQualityAppears() {
        val raw = offlinePlayFixture()
        val dash = raw["dash"]!!.jsonObject
        val original = dash["audio"]!!.jsonArray.first().jsonObject
        val better = JsonObject(original + mapOf("id" to JsonPrimitive(30299), "bandwidth" to JsonPrimitive(999)))
        val updated = JsonObject(raw + ("dash" to JsonObject(dash + ("audio" to JsonArray(listOf(original, better))))))
        assertEquals(30280, parseDownloadOptions(updated, offlineTestJson, 60, 30280).first().audioId)
        assertThrows(IllegalStateException::class.java) { parseDownloadOptions(updated, offlineTestJson, 60, 123) }
    }
    @Test fun usesOfficialHttpsBackupForNonstandardMcdnPort() {
        val raw = offlinePlayFixture(); val dash = raw["dash"]!!.jsonObject
        val original = dash["audio"]!!.jsonArray.first().jsonObject
        val audio = JsonObject(original + mapOf("base_url" to JsonPrimitive("https://a.mcdn.bilivideo.cn:8082/audio"),
            "backup_url" to JsonArray(listOf(JsonPrimitive("http://a.bilivideo.com/audio"), JsonPrimitive("https://mirror.bilivideo.com/audio")))))
        val updated = JsonObject(raw + ("dash" to JsonObject(dash + ("audio" to JsonArray(listOf(audio))))))
        assertEquals("https://mirror.bilivideo.com/audio", parseDownloadOptions(updated, offlineTestJson, 60).first().audioUrl)
        val unsafe = JsonObject(audio - "backup_url")
        assertThrows(IllegalStateException::class.java) {
            parseDownloadOptions(JsonObject(raw + ("dash" to JsonObject(dash + ("audio" to JsonArray(listOf(unsafe)))))), offlineTestJson, 60)
        }
    }
}
