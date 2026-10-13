package com.qbili.domain.model

enum class ThemeMode(val key: String, val title: String, val description: String) {
    SYSTEM("system", "跟随系统", "根据系统的浅色或深色设置自动切换"),
    LIGHT("light", "清爽白", "白色卡片、浅灰背景，搭配粉色与蓝色点缀"),
    DARK("dark", "深色", "深色背景，适合夜间阅读与浏览"),
    ;

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}
