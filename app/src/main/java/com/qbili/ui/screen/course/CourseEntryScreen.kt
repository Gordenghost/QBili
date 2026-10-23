package com.qbili.ui.screen.course

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.qbili.domain.model.CourseTarget
import com.qbili.domain.model.parseCourseTarget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseEntryScreen(onBack: () -> Unit, onCourseClick: (CourseTarget) -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    var invalid by rememberSaveable { mutableStateOf(false) }
    fun open() {
        val target = parseCourseTarget(input)
        if (target == null) invalid = true else { invalid = false; onCourseClick(target) }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("课程") }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("粘贴B站课程的完整链接，或输入 ss课程ID／ep课时ID。课程与番剧的ID分别处理。")
            OutlinedTextField(input, onValueChange = { input = it; invalid = false }, singleLine = true,
                label = { Text("课程链接或ID") }, placeholder = { Text("https://www.bilibili.com/cheese/play/ss…") },
                isError = invalid, supportingText = { if (invalid) Text("请输入完整课程链接或ss／ep加数字；不解析短链或外部链接") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go), keyboardActions = KeyboardActions(onGo = { open() }),
                modifier = Modifier.fillMaxWidth())
            Button(onClick = ::open, enabled = input.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("打开课程") }
            Text("支持目录、讲师主页、介绍图片预览和平台允许的课程视频播放。付费／账号权限由B站判断，不提供购买或绕过限制；课程搜索、已购列表和直播课时暂不支持。")
        }
    }
}
