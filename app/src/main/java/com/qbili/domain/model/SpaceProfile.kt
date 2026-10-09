package com.qbili.domain.model

data class SpaceProfile(
    val mid: Long,
    val name: String,
    val face: String,
    val sign: String,
    val followers: Long,
    val videoCount: Int,
    val level: Int,
    val following: Boolean,
)
