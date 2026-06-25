package com.lightreader.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lightreader.app.core.data.DownloadTaskEntity
import com.lightreader.app.core.settings.AiConfiguration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(viewModel: MainViewModel) {
    val state by viewModel.searchState.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text("书内搜索") },
                navigationIcon = { IconButton(onClick = viewModel::goBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::updateSearchQuery,
                    modifier = Modifier.weight(1f),
                    label = { Text("关键词") },
                    singleLine = true,
                )
                Button(onClick = viewModel::search, enabled = state.query.isNotBlank() && !state.searching) { Text("搜索") }
            }
            if (state.searching) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.results) { result ->
                    Card(Modifier.fillMaxWidth().clickable { viewModel.jumpToSearchResult(result) }) {
                        Column(Modifier.padding(14.dp)) {
                            Text(result.chapterTitle, style = MaterialTheme.typography.titleSmall)
                            Text(highlightedExcerpt(result.excerpt), maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Text("字符位置 ${result.charOffset}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

private fun highlightedExcerpt(raw: String) = buildAnnotatedString {
    var cursor = 0
    val pattern = Regex("<b>(.*?)</b>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    pattern.findAll(raw).forEach { match ->
        append(raw.substring(cursor, match.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.groupValues[1]) }
        cursor = match.range.last + 1
    }
    if (cursor < raw.length) append(raw.substring(cursor))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebImportScreen(tasks: List<DownloadTaskEntity>, viewModel: MainViewModel) {
    val state by viewModel.webState.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text("网页小说") },
                navigationIcon = { IconButton(onClick = viewModel::goBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") } },
                actions = { IconButton(onClick = { viewModel.navigate(AppScreen.ApiSettings) }) { Icon(Icons.Outlined.Key, "API Key") } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("粘贴用户有权保存的 HTTPS 小说目录页。应用先本地解析，必要时才调用 DeepSeek。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                OutlinedTextField(
                    value = state.url,
                    onValueChange = viewModel::updateWebUrl,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("目录页网址") },
                    placeholder = { Text("https://example.com/book/123") },
                    minLines = 2,
                )
            }
            item {
                Button(onClick = viewModel::previewWebBook, enabled = state.url.startsWith("https://") && !state.loading) {
                    Text("识别并预览")
                }
            }
            if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
            state.preview?.let { preview ->
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(preview.title, style = MaterialTheme.typography.titleLarge)
                            Text("识别到 ${preview.chapters.size} 章")
                            Text(preview.sample, maxLines = 8, overflow = TextOverflow.Ellipsis)
                            Button(onClick = viewModel::startWebDownload) { Text("开始整本下载") }
                        }
                    }
                }
            }
            if (tasks.isNotEmpty()) {
                item { Text("下载任务", style = MaterialTheme.typography.titleMedium) }
                items(tasks, key = { it.id }) { task -> DownloadTaskCard(task, viewModel) }
            }
        }
    }
}

@Composable
private fun DownloadTaskCard(task: DownloadTaskEntity, viewModel: MainViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(task.title, style = MaterialTheme.typography.titleSmall)
                    Text("${task.completedChapters}/${task.totalChapters} · ${task.status}", style = MaterialTheme.typography.labelMedium)
                }
                when (task.status) {
                    "DOWNLOADING", "QUEUED" -> IconButton(onClick = { viewModel.pauseDownload(task.id) }) { Icon(Icons.Outlined.Pause, "暂停") }
                    "PAUSED", "FAILED" -> IconButton(onClick = { viewModel.resumeDownload(task.id) }) { Icon(Icons.Outlined.PlayArrow, "继续或重试") }
                }
            }
            LinearProgressIndicator(
                progress = { if (task.totalChapters == 0) 0f else task.completedChapters.toFloat() / task.totalChapters },
                modifier = Modifier.fillMaxWidth(),
            )
            task.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiSettingsScreen(viewModel: MainViewModel) {
    val web by viewModel.webState.collectAsState()
    var key by remember { mutableStateOf("") }
    var configuration by remember { mutableStateOf(viewModel.aiConfiguration()) }
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text("DeepSeek 设置") },
                navigationIcon = { IconButton(onClick = viewModel::goBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("API Key 仅使用 Android Keystore 加密保存在本机，不写入数据库、日志或备份。")
            Text(if (web.hasApiKey) "当前已配置 API Key" else "当前未配置 API Key", color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("DeepSeek API Key") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
            )
            Button(onClick = { viewModel.saveApiKey(key); key = "" }, enabled = key.isNotBlank()) { Text("加密保存") }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = viewModel::testApiKey, enabled = web.hasApiKey) { Text("测试连接") }
                OutlinedButton(onClick = viewModel::deleteApiKey, enabled = web.hasApiKey) { Text("删除密钥") }
            }
            Text("高级设置", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = configuration.baseUrl,
                onValueChange = { configuration = configuration.copy(baseUrl = it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API Base URL") },
                singleLine = true,
            )
            OutlinedTextField(
                value = configuration.model,
                onValueChange = { configuration = configuration.copy(model = it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("模型") },
                singleLine = true,
            )
            OutlinedTextField(
                value = configuration.timeoutSeconds.toString(),
                onValueChange = { raw -> raw.toIntOrNull()?.let { configuration = configuration.copy(timeoutSeconds = it) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("超时秒数（10-120）") },
                singleLine = true,
            )
            OutlinedButton(onClick = { viewModel.saveAiConfiguration(configuration) }) { Text("保存高级设置") }
            Text("DeepSeek 只在本地网页规则无法识别时接收裁剪后的 HTML 样本，不接收已成功下载的整本正文。", style = MaterialTheme.typography.bodySmall)
        }
    }
}
