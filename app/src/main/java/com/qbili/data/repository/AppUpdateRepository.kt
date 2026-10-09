package com.qbili.data.repository

import com.qbili.core.AppInfo
import com.qbili.data.remote.api.GitHubReleaseApi
import com.qbili.domain.model.AppRelease
import com.qbili.domain.model.AppVersion
import com.qbili.domain.model.UpdateCheckResult
import java.io.IOException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class AppUpdateRepository(private val api: GitHubReleaseApi) {
    suspend fun check(currentVersionName: String, debugBuild: Boolean): UpdateCheckResult {
        val currentVersion = AppVersion.parse(currentVersionName)
            ?: throw IOException("无法识别当前应用版本，请打开 GitHub 发布页面查看")
        val response = api.latestRelease()
        when (response.code()) {
            404 -> return UpdateCheckResult.NoPublishedRelease
            403, 429 -> throw IOException("GitHub 暂时限制了请求，请稍后重试或打开发布页面")
        }
        if (!response.isSuccessful) {
            throw IOException("检查更新失败（GitHub HTTP ${response.code()}），请稍后重试")
        }
        val release = response.body() ?: throw IOException("GitHub 返回了空的版本信息，请重试")
        if (release.draft || release.prerelease) return UpdateCheckResult.NoPublishedRelease
        val latestVersion = AppVersion.parse(release.tagName)
            ?: throw IOException("无法识别 GitHub 发布版本，请打开发布页面查看")
        if (latestVersion <= currentVersion) return UpdateCheckResult.UpToDate(latestVersion.name)

        val variant = if (debugBuild) "debug" else "release"
        val apkNames = setOf("QBili-${latestVersion.name}-$variant.apk", "app-$variant.apk")
        val downloadPrefix = "/Gordenghost/QBili/releases/download/${release.tagName}/"
        val downloadUrl = release.assets.firstOrNull { asset ->
            val url = asset.downloadUrl.toHttpUrlOrNull()
            asset.state == "uploaded" && asset.name in apkNames && url != null &&
                url.scheme == "https" && url.host == "github.com" && url.port == 443 &&
                url.username.isEmpty() && url.password.isEmpty() &&
                url.encodedPath.startsWith(downloadPrefix) &&
                url.pathSegments.lastOrNull() == asset.name
        }?.downloadUrl
        return UpdateCheckResult.Available(
            AppRelease(
                versionName = latestVersion.name,
                pageUrl = "${AppInfo.RELEASES_URL}/tag/${release.tagName}",
                downloadUrl = downloadUrl,
            ),
        )
    }
}
