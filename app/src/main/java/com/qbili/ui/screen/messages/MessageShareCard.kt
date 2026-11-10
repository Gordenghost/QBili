package com.qbili.ui.screen.messages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.InsertLink
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.qbili.domain.model.MessageCard
import com.qbili.domain.model.MessageOpenTarget

@Composable
internal fun MessageShareCard(card: MessageCard, onContentClick: (MessageOpenTarget) -> Unit) {
    val singleLink = card.links.singleOrNull()
    Column(
        modifier = Modifier.widthIn(min = 160.dp, max = 280.dp)
            .clickable(enabled = singleLink != null, onClickLabel = singleLink?.label) {
                singleLink?.let { onContentClick(it.target) }
            }.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(card.label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                val cover = card.cover
                if (cover != null) SubcomposeAsyncImage(
                    model = cover, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(64.dp),
                    loading = { Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } },
                    error = { Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.InsertLink, contentDescription = "封面暂不可用")
                    } },
                ) else Icon(Icons.Outlined.InsertLink, contentDescription = null)
            }
            Text(card.title, modifier = Modifier.weight(1f), maxLines = 3, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        }
        if (card.author.isNotBlank()) Text(card.author, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (card.description.isNotBlank()) Text(card.description, maxLines = 4, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        when {
            singleLink != null -> Text("${singleLink.label} ›", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
            card.links.isNotEmpty() -> card.links.forEach { link ->
                TextButton(onClick = { onContentClick(link.target) }, modifier = Modifier.fillMaxWidth()) {
                    Text(link.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            else -> Text("暂不支持在应用内打开", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
