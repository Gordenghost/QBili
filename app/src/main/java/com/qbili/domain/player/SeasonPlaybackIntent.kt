package com.qbili.domain.player

class SeasonPlaybackIntent {
    var requested: Boolean = false
        private set
    private var foreground = true

    val shouldPlay: Boolean get() = requested && foreground

    fun setRequested(value: Boolean) {
        requested = value
    }

    fun toggle() {
        requested = !requested
    }

    fun enter() {
        foreground = true
    }

    fun leave() {
        foreground = false
        requested = false
    }
}
