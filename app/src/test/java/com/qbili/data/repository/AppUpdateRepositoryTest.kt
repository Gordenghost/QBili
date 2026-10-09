package com.qbili.data.repository

import com.qbili.core.AppInfo
import com.qbili.data.remote.api.GitHubReleaseApi
import com.qbili.data.remote.dto.GitHubReleaseAssetDto
import com.qbili.data.remote.dto.GitHubReleaseDto
import com.qbili.domain.model.UpdateCheckResult
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class AppUpdateRepositoryTest {
    private val json = Json { ignoreUnknownKeys = true }
    private class FakeApi(
        private val response: Response<GitHubReleaseDto>,
    ) : GitHubReleaseApi {
        var calls = 0
        override suspend fun latestRelease(): Response<GitHubReleaseDto> {
            calls += 1
            return response
        }
    }

    private fun asset(
        name: String = "QBili-0.2.236-debug.apk",
        url: String = "${AppInfo.RELEASES_URL}/download/v0.2.236/$name",
        state: String = "uploaded",
    ) = GitHubReleaseAssetDto(name = name, state = state, downloadUrl = url)

    private suspend fun check(
        release: GitHubReleaseDto,
        current: String = "0.2.235",
        debug: Boolean = true,
    ): UpdateCheckResult = AppUpdateRepository(FakeApi(Response.success(release))).check(current, debug)

    @Test
    fun `最新 GitHub 响应支持附加字段并选择匹配 Debug APK`() = runBlocking {
        val release = json.decodeFromString<GitHubReleaseDto>(
            """{
                "tag_name":"v0.2.236","html_url":"https://github.com/Gordenghost/QBili/releases/tag/v0.2.236",
                "draft":false,"prerelease":false,"body":"更新说明","assets":[
                    {"name":"QBili-0.2.236-release.apk","state":"uploaded",
                     "browser_download_url":"https://github.com/Gordenghost/QBili/releases/download/v0.2.236/QBili-0.2.236-release.apk"},
                    {"name":"QBili-0.2.236-debug.apk","state":"uploaded","size":23647000,
                     "browser_download_url":"https://github.com/Gordenghost/QBili/releases/download/v0.2.236/QBili-0.2.236-debug.apk"}
                ]}
            """,
        )
        val result = check(release) as UpdateCheckResult.Available
        assertEquals("0.2.236", result.release.versionName)
        assertEquals("${AppInfo.RELEASES_URL}/tag/v0.2.236", result.release.pageUrl)
        assertEquals(asset().downloadUrl, result.release.downloadUrl)
    }

    @Test
    fun `相同和更旧的发布版本均不提示升级或降级`() = runBlocking {
        val release = GitHubReleaseDto(tagName = "v0.2.235")
        assertEquals(UpdateCheckResult.UpToDate("0.2.235"), check(release))
        assertEquals(UpdateCheckResult.UpToDate("0.2.235"), check(release, current = "0.2.236"))
    }

    @Test
    fun `数字位数跨越时仍然识别升级`() = runBlocking {
        val result = check(GitHubReleaseDto(tagName = "v0.2.100"), current = "0.2.99")
        assertTrue(result is UpdateCheckResult.Available)
    }

    @Test
    fun `没有匹配附件时返回发布页而不是错误或其他构建类型`() = runBlocking {
        val release = GitHubReleaseDto(tagName = "v0.2.236", assets = listOf(asset("app-release.apk")))
        val result = check(release) as UpdateCheckResult.Available
        assertNull(result.release.downloadUrl)
        assertEquals("${AppInfo.RELEASES_URL}/tag/v0.2.236", result.release.pageUrl)
        assertNull((check(release.copy(assets = emptyList())) as UpdateCheckResult.Available).release.downloadUrl)
    }

    @Test
    fun `非 Debug 安装不使用 Debug 附件`() = runBlocking {
        val release = GitHubReleaseDto(tagName = "v0.2.236", assets = listOf(asset(), asset("app-release.apk")))
        assertEquals(asset("app-release.apk").downloadUrl,
            (check(release, debug = false) as UpdateCheckResult.Available).release.downloadUrl)
    }

    @Test
    fun `兼容默认 APK 文件名和没有 v 前缀的标签`() = runBlocking {
        val apk = asset("app-debug.apk", "${AppInfo.RELEASES_URL}/download/0.2.236/app-debug.apk")
        val release = GitHubReleaseDto(tagName = "0.2.236", assets = listOf(apk))
        assertEquals(apk.downloadUrl, (check(release) as UpdateCheckResult.Available).release.downloadUrl)
    }

    @Test
    fun `拒绝外部非 HTTPS错误标签路径和未上传附件`() = runBlocking {
        val invalidAssets = listOf(
            asset(url = "https://example.com/QBili-0.2.236-debug.apk"),
            asset(url = "http://github.com/Gordenghost/QBili/releases/download/v0.2.236/QBili-0.2.236-debug.apk"),
            asset(url = "https://github.com.evil.example/Gordenghost/QBili/releases/download/v0.2.236/QBili-0.2.236-debug.apk"),
            asset(url = "https://github.com/another/repo/releases/download/v0.2.236/QBili-0.2.236-debug.apk"),
            asset(url = "${AppInfo.RELEASES_URL}/download/v0.2.235/QBili-0.2.236-debug.apk"),
            asset(url = "https://user:password@github.com/Gordenghost/QBili/releases/download/v0.2.236/QBili-0.2.236-debug.apk"),
            asset(url = "https://github.com:8443/Gordenghost/QBili/releases/download/v0.2.236/QBili-0.2.236-debug.apk"),
            asset(state = "new"),
            asset(name = "checksums.txt"),
        )
        invalidAssets.forEach { apk ->
            val release = GitHubReleaseDto(tagName = "v0.2.236", assets = listOf(apk))
            assertNull(apk.downloadUrl, (check(release) as UpdateCheckResult.Available).release.downloadUrl)
        }
    }

    @Test
    fun `过滤草稿与预发布避免提示不稳定版本`() = runBlocking {
        assertEquals(UpdateCheckResult.NoPublishedRelease, check(GitHubReleaseDto(tagName = "v0.2.236", draft = true)))
        assertEquals(UpdateCheckResult.NoPublishedRelease, check(GitHubReleaseDto(tagName = "v0.2.236-beta", prerelease = true)))
    }

    @Test
    fun `404 表示暂无版本而非已经最新`() = runBlocking {
        val api = FakeApi(Response.error(404, "{}".toResponseBody("application/json".toMediaType())))
        assertEquals(UpdateCheckResult.NoPublishedRelease, AppUpdateRepository(api).check("0.2.235", true))
    }

    @Test
    fun `HTTP限流与服务错误不能误报已是最新版本`() {
        listOf(403, 429, 500).forEach { code ->
            val api = FakeApi(Response.error(code, "{}".toResponseBody("application/json".toMediaType())))
            val error = assertThrows(IOException::class.java) {
                runBlocking { AppUpdateRepository(api).check("0.2.235", true) }
            }
            assertTrue(error.message.orEmpty().contains(if (code == 500) "500" else "限制"))
        }
    }

    @Test
    fun `未知版本与空响应提示错误而不是错误升级`() {
        val invalidApi = FakeApi(Response.success(GitHubReleaseDto(tagName = "latest")))
        assertThrows(IOException::class.java) {
            runBlocking { AppUpdateRepository(invalidApi).check("0.2.235", true) }
        }
        val emptyApi = FakeApi(Response.success(null))
        assertThrows(IOException::class.java) {
            runBlocking { AppUpdateRepository(emptyApi).check("0.2.235", true) }
        }
        assertThrows(IOException::class.java) {
            runBlocking { AppUpdateRepository(emptyApi).check("invalid", true) }
        }
        assertEquals(1, emptyApi.calls)
    }

    @Test
    fun `网络异常和取消请求不被吞成无更新`() {
        val failures = listOf(IOException("network unavailable"), CancellationException("cancelled"))
        failures.forEach { failure ->
            val api = object : GitHubReleaseApi {
                override suspend fun latestRelease(): Response<GitHubReleaseDto> = throw failure
            }
            assertThrows(failure.javaClass) {
                runBlocking { AppUpdateRepository(api).check("0.2.235", true) }
            }
        }
    }
}
