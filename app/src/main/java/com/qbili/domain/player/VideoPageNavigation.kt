package com.qbili.domain.player

fun adjacentVideoPage(currentIndex: Int, pageCount: Int, direction: Int): Int? {
    if (pageCount <= 0 || currentIndex !in 0 until pageCount || direction !in listOf(-1, 1)) return null
    return (currentIndex + direction).takeIf { it in 0 until pageCount }
}
