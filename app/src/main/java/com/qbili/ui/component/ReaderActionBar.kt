package com.qbili.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Message
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.qbili.core.formatCount

@Composable
fun ReaderActionBar(
    commentCount: Long?,
    likeCount: Long?,
    favoriteCount: Long?,
    liked: Boolean,
    favorited: Boolean,
    busy: Boolean,
    onCommentsClick: () -> Unit,
    onLikeClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onShareClick: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 4.dp),
        ) {
            ReaderAction(Icons.AutoMirrored.Outlined.Message, "评论", commentCount,
                enabled = true, selected = false, onClick = onCommentsClick,
                modifier = Modifier.weight(1f))
            ReaderAction(if (liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, "点赞", likeCount,
                enabled = !busy, selected = liked, onClick = onLikeClick,
                modifier = Modifier.weight(1f))
            ReaderAction(if (favorited) Icons.Filled.Star else Icons.Outlined.Star, "收藏", favoriteCount,
                enabled = !busy, selected = favorited, onClick = onFavoriteClick,
                modifier = Modifier.weight(1f))
            ReaderAction(Icons.Outlined.Share, "分享", null,
                enabled = true, selected = false, onClick = onShareClick,
                modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ReaderAction(
    icon: ImageVector,
    title: String,
    count: Long?,
    enabled: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(40.dp)) {
            Icon(icon, contentDescription = title,
                tint = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(if (count != null && count > 0) formatCount(count) else title,
            style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}
