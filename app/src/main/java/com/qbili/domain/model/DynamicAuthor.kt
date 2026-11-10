package com.qbili.domain.model

data class DynamicAuthor(val mid: Long, val name: String, val face: String)

fun List<DynamicAuthor>.mergeDynamicAuthors(next: List<DynamicAuthor>): List<DynamicAuthor> =
    (this + next).filter { it.mid > 0 }.distinctBy { it.mid }

fun List<DynamicAuthor>.searchDynamicAuthors(query: String): List<DynamicAuthor> {
    val term = query.trim()
    return if (term.isEmpty()) this else filter { it.name.contains(term, ignoreCase = true) || it.mid.toString() == term }
}
