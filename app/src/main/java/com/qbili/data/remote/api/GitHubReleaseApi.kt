package com.qbili.data.remote.api

import com.qbili.data.remote.dto.GitHubReleaseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers

interface GitHubReleaseApi {
    @Headers(
        "Accept: application/vnd.github+json",
        "X-GitHub-Api-Version: 2022-11-28",
        "User-Agent: QBili-Android",
    )
    @GET("repos/Gordenghost/QBili/releases/latest")
    suspend fun latestRelease(): Response<GitHubReleaseDto>
}
