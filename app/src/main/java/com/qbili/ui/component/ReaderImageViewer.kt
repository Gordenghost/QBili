package com.qbili.ui.component

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.qbili.core.friendlyMessage
import com.qbili.data.repository.DownloadedReaderImage
import com.qbili.domain.model.readerImageUrl
import com.qbili.ui.LocalAppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun ReaderImageViewer(images: List<String>, initialIndex: Int, onDismiss: () -> Unit) {
    if (images.isEmpty()) return
    val repository = LocalAppContainer.current.readerImageRepository
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbars = remember { SnackbarHostState() }
    var index by remember(images, initialIndex) { mutableIntStateOf(initialIndex.coerceIn(images.indices)) }
    val url = readerImageUrl(images[index])
    var zoom by remember(index) { mutableFloatStateOf(1f) }
    var offset by remember(index) { mutableStateOf(Offset.Zero) }
    var area by remember { mutableStateOf(IntSize.Zero) }
    var loading by remember(index) { mutableStateOf(url != null) }
    var failed by remember(index) { mutableStateOf(false) }
    var retry by remember(index) { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<DownloadedReaderImage?>(null) }
    DisposableEffect(Unit) { onDispose { pending?.file?.delete() } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/*")) { uri ->
        val image = pending
        pending = null
        if (uri == null || image == null) {
            image?.file?.delete()
            saving = false
        } else scope.launch {
            val message = try {
                repository.save(image, uri)
                "图片保存成功"
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                "保存失败：${error.friendlyMessage()}"
            } finally { image.file.delete(); saving = false }
            snackbars.showSnackbar(message)
        }
    }
    fun saveImage() {
        if (saving || url == null) return
        snackbars.currentSnackbarData?.dismiss()
        saving = true
        scope.launch {
            var image: DownloadedReaderImage? = null
            val message = try {
                image = repository.download(url)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    pending = image
                    picker.launch(image.name)
                    image = null
                    null
                } else {
                    repository.save(image)
                    "已保存到相册 Pictures/QBili"
                }
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                pending?.file?.delete()
                pending = null
                "保存失败：${error.friendlyMessage()}"
            } finally { image?.file?.delete(); if (pending == null) saving = false }
            if (message != null) snackbars.showSnackbar(message)
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
    )) {
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window
            window?.let {
                WindowCompat.getInsetsController(it, view).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
            onDispose { }
        }
        Scaffold(
            modifier = Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding(),
            containerColor = Color.Black,
            contentColor = Color.White,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "关闭图片", tint = Color.White) }
                    Text("${index + 1} / ${images.size}", color = Color.White, modifier = Modifier.weight(1f))
                    if (saving) CircularProgressIndicator(Modifier.size(24.dp), color = Color.White)
                    IconButton(onClick = ::saveImage, enabled = !saving && url != null) {
                        Icon(Icons.Outlined.Download, "保存图片", tint = if (saving) Color.Gray else Color.White)
                    }
                }
            },
            bottomBar = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("双指缩放 · 双击放大", color = Color.White,
                        style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        val buttonColors = ButtonDefaults.textButtonColors(
                            contentColor = Color.White, disabledContentColor = Color.Gray)
                        TextButton(onClick = { index-- }, enabled = index > 0, colors = buttonColors) { Text("上一张") }
                        TextButton(onClick = { index++ }, enabled = index < images.lastIndex, colors = buttonColors) { Text("下一张") }
                    }
                }
            },
            snackbarHost = {
                SnackbarHost(snackbars, Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) { data ->
                    Snackbar(data, containerColor = Color(0xFF323232), contentColor = Color.White)
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).clipToBounds().onSizeChanged { area = it }
                .pointerInput(index) {
                    detectTransformGestures { centroid, pan, factor, _ ->
                        val next = (zoom * factor).coerceIn(1f, 6f)
                        val origin = Offset(area.width / 2f, area.height / 2f)
                        val moved = (offset - (centroid - origin)) * (next / zoom) + (centroid - origin) + pan
                        offset = if (next == 1f) Offset.Zero else Offset(
                            moved.x.coerceIn(-area.width * (next - 1) / 2, area.width * (next - 1) / 2),
                            moved.y.coerceIn(-area.height * (next - 1) / 2, area.height * (next - 1) / 2))
                        zoom = next
                    }
                }.pointerInput(index) { detectTapGestures(onDoubleTap = { zoom = if (zoom > 1f) 1f else 2.5f; offset = Offset.Zero }) },
                contentAlignment = Alignment.Center) {
                AsyncImage(model = remember(url, retry) { ImageRequest.Builder(context).data(url).size(4096).build() },
                    contentDescription = "图片 ${index + 1}", contentScale = ContentScale.Fit,
                    onLoading = { loading = true; failed = false }, onSuccess = { loading = false },
                    onError = { loading = false; failed = true },
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = zoom; scaleY = zoom; translationX = offset.x; translationY = offset.y
                    })
                if (loading) CircularProgressIndicator(color = Color.White)
                if (failed || url == null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("图片加载失败", color = Color.White)
                    TextButton(onClick = { retry++ }) { Text("重试") }
                }
            }
        }
    }
}
