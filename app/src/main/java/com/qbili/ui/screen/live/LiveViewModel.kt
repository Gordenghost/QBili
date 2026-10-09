package com.qbili.ui.screen.live

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import com.qbili.core.friendlyMessage
import com.qbili.data.remote.ApiConstants
import com.qbili.data.remote.live.LiveChatCodec
import com.qbili.data.repository.LiveRepository
import com.qbili.di.AppContainer
import com.qbili.domain.model.LiveChatMessage
import com.qbili.domain.model.LiveRoom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okio.ByteString
import okio.ByteString.Companion.toByteString
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class LiveViewModel(
    private val repository: LiveRepository,
    private val client: OkHttpClient,
    context: Context,
) : ViewModel() {
    data class UiState(
        val room: LiveRoom? = null,
        val loading: Boolean = true,
        val error: String? = null,
        val chatError: String? = null,
        val messages: List<LiveChatMessage> = emptyList(),
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var chatJob: Job? = null
    private var heartbeatJob: Job? = null
    private var socket: WebSocket? = null
    private var requestedRoomId: Long = 0

    val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build().apply {
        addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                _uiState.update { it.copy(error = "直播播放失败：${error.errorCodeName}") }
            }
        })
    }

    fun load(roomId: Long) {
        if (roomId == requestedRoomId && _uiState.value.room != null) return
        requestedRoomId = roomId
        refresh()
    }

    fun refresh() {
        loadJob?.cancel()
        disconnectChat()
        player.stop()
        _uiState.value = UiState()
        loadJob = viewModelScope.launch {
            try {
                val room = repository.room(requestedRoomId)
                _uiState.update { it.copy(room = room, loading = false) }
                room.streamUrl?.let { url ->
                    val dataSource = DefaultHttpDataSource.Factory().setDefaultRequestProperties(
                        mapOf(
                            "User-Agent" to ApiConstants.USER_AGENT,
                            "Referer" to "https://live.bilibili.com/${room.roomId}",
                            "Origin" to "https://live.bilibili.com",
                        ),
                    )
                    player.setMediaSource(HlsMediaSource.Factory(dataSource).createMediaSource(MediaItem.fromUri(url)))
                    player.prepare()
                    player.playWhenReady = true
                    connectChat(room.roomId)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { it.copy(loading = false, error = error.friendlyMessage()) }
            }
        }
    }

    private fun connectChat(roomId: Long) {
        chatJob = viewModelScope.launch {
            try {
                val connection = repository.chat(roomId)
                socket = client.newWebSocket(Request.Builder().url(connection.url).build(), object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        webSocket.send(LiveChatCodec.auth(connection.roomId, connection.token).toByteString())
                        heartbeatJob = viewModelScope.launch {
                            while (true) {
                                delay(30_000)
                                webSocket.send(LiveChatCodec.heartbeat().toByteString())
                            }
                        }
                    }

                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                        val messages = LiveChatCodec.messages(bytes.toByteArray())
                        if (messages.isNotEmpty()) _uiState.update {
                            it.copy(messages = (it.messages + messages).takeLast(150), chatError = null)
                        }
                    }

                    override fun onFailure(webSocket: WebSocket, error: Throwable, response: Response?) {
                        heartbeatJob?.cancel()
                        _uiState.update { it.copy(chatError = "弹幕连接失败：${error.friendlyMessage()}") }
                    }
                })
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { it.copy(chatError = "弹幕暂不可用：${error.friendlyMessage()}") }
            }
        }
    }

    private fun disconnectChat() {
        chatJob?.cancel()
        heartbeatJob?.cancel()
        socket?.close(1000, null)
        socket = null
    }

    fun stop() {
        loadJob?.cancel()
        disconnectChat()
        player.pause()
    }

    override fun onCleared() {
        stop()
        player.release()
        super.onCleared()
    }

    companion object {
        fun factory(container: AppContainer, context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer { LiveViewModel(container.liveRepository, container.network.client, context) }
        }
    }
}
