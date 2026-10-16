package com.qbili.domain.model

enum class RankingTab(val label: String) {
    POPULAR("综合热门"), WEEKLY("每周必看"), PRECIOUS("入站必刷"), RANKING("排行榜"),
}

enum class RankingCategory(val rid: Int, val label: String) {
    ALL(0, "全站"), ANIMATION(1, "动画"), MUSIC(3, "音乐"), DANCE(129, "舞蹈"),
    GAME(4, "游戏"), KNOWLEDGE(36, "知识"), TECHNOLOGY(188, "科技"), SPORTS(234, "运动"),
    CAR(223, "汽车"), LIFE(160, "生活"), FOOD(211, "美食"), ANIMAL(217, "动物圈"),
    KICHIKU(119, "鬼畜"), FASHION(155, "时尚"), ENTERTAINMENT(5, "娱乐"), FILM(181, "影视投稿"),
}

data class WeeklyIssue(val number: Int, val name: String, val subject: String)

fun filterWeeklyIssues(issues: List<WeeklyIssue>, query: String): List<WeeklyIssue> {
    val keywords = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return issues.filter { issue ->
        val text = "第${issue.number}期 ${issue.name} ${issue.subject}"
        keywords.all { text.contains(it, ignoreCase = true) }
    }
}
