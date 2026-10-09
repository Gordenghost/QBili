package com.qbili.domain.model

data class AppVersion(val major: Int, val minor: Int, val build: Int) : Comparable<AppVersion> {
    val name: String get() = "$major.$minor.$build"

    override fun compareTo(other: AppVersion): Int =
        compareValuesBy(this, other, AppVersion::major, AppVersion::minor, AppVersion::build)

    companion object {
        fun parse(value: String): AppVersion? {
            val match = Regex("[vV]?([0-9]+)\\.([0-9]+)\\.([0-9]+)")
                .matchEntire(value.trim()) ?: return null
            val major = match.groupValues[1].toIntOrNull() ?: return null
            val minor = match.groupValues[2].toIntOrNull() ?: return null
            val build = match.groupValues[3].toIntOrNull() ?: return null
            return AppVersion(major, minor, build)
        }
    }
}

data class AppRelease(
    val versionName: String,
    val pageUrl: String,
    val downloadUrl: String?,
)

sealed interface UpdateCheckResult {
    data object NoPublishedRelease : UpdateCheckResult
    data class UpToDate(val latestVersionName: String) : UpdateCheckResult
    data class Available(val release: AppRelease) : UpdateCheckResult
}
