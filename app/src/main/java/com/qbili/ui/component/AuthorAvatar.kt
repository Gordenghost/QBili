package com.qbili.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.qbili.ui.util.biliAvatar

@Composable
fun AuthorAvatar(face: String, name: String, size: Dp = 40.dp) {
    var failed by remember(face) { mutableStateOf(face.isBlank()) }
    Box(
        modifier = Modifier.size(size).clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (failed) Text(name.take(1).ifBlank { "UP" },
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        else AsyncImage(
            model = face.biliAvatar(), contentDescription = "$name 的头像",
            contentScale = ContentScale.Crop,
            onError = { failed = true },
            modifier = Modifier.matchParentSize(),
        )
    }
}
