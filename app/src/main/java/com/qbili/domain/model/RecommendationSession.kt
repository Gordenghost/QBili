package com.qbili.domain.model

class RecommendationSession(val mid: Long?, private val cookie: String?, private val tokenRevision: Long) {
    override fun equals(other: Any?): Boolean = other is RecommendationSession &&
        mid == other.mid && cookie == other.cookie && tokenRevision == other.tokenRevision

    override fun hashCode(): Int = 31 * (31 * (mid?.hashCode() ?: 0) + (cookie?.hashCode() ?: 0)) + tokenRevision.hashCode()
}
