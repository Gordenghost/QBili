package com.qbili.domain.model

enum class RecommendationSource(val key: String, val title: String, val description: String) {
    WEB("web", "网页端", "使用 B 站网页首页推荐接口，保持原有推荐方式"),
    APP("app", "App 端", "使用 B 站移动端首页推荐接口，推荐结果可能与网页端不同");

    companion object {
        fun fromKey(key: String?): RecommendationSource = entries.firstOrNull { it.key == key } ?: WEB
    }
}
