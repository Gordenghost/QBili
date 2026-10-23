package com.qbili.ui.screen.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.qbili.core.formatCount
import com.qbili.core.formatDuration
import com.qbili.domain.model.VideoItem
import com.qbili.ui.util.biliThumbnail

internal fun LazyListScope.relatedVideoSection(
    state: RelatedVideoViewModel.UiState,
    onRefresh: () -> Unit,
    onVideoClick: (VideoItem) -> Unit,
    onAuthorClick: (Long) -> Unit,
) {
    item(key = "related_header") {
        Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("相关推荐", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onRefresh, enabled = !state.loading) { Text("刷新") }
                if (state.loading) CircularProgressIndicator(Modifier.size(20.dp))
            }
            state.error?.let { error ->
                Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRefresh, enabled = !state.loading) { Text("重试") }
            }
            if (state.loaded && state.videos.isEmpty() && !state.loading && state.error == null) {
                Text("暂无可展示的普通视频推荐", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    items(state.videos, key = { "related:${it.aid}" }) { item ->
        Surface(onClick = { onVideoClick(item) }, color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface, shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(0.38f).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)) {
                    SubcomposeAsyncImage(model = item.cover.biliThumbnail(320, 180), contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                        loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(18.dp))
                        } }, error = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = "封面加载失败")
                        } })
                    if (item.durationSeconds > 0) {
                        Text(formatDuration(item.durationSeconds), style = MaterialTheme.typography.labelSmall,
                            color = Color.White, modifier = Modifier.align(Alignment.BottomEnd)
                                .background(Color.Black.copy(alpha = 0.65f)).padding(horizontal = 4.dp, vertical = 2.dp))
                    }
                }
                Column(Modifier.weight(0.62f)) {
                    Text(item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                    Text(item.authorName.ifBlank { "UP主资料暂不可用" }, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().clickable(enabled = item.authorMid > 0) {
                            onAuthorClick(item.authorMid)
                        }.padding(vertical = 6.dp))
                    Text("${formatCount(item.viewCount)}播放 · ${formatCount(item.danmakuCount)}弹幕",
                        maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (state.loaded) item(key = "related_footer") {
        Text("本次推荐已加载完毕 · ${state.videos.size}个普通视频", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
    }
}
