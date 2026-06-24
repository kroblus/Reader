package com.lightreader.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lightreader.app.core.model.Book
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(state: MainUiState, viewModel: MainViewModel) {
    var pendingDelete by remember { mutableStateOf<Book?>(null) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importBook)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("轻阅", fontWeight = FontWeight.SemiBold)
                        Text("${state.books.size} 本书", style = MaterialTheme.typography.labelSmall)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.navigate(AppScreen.WebImport) }) {
                        Icon(Icons.Outlined.CloudDownload, "网页导入")
                    }
                    IconButton(onClick = { viewModel.navigate(AppScreen.ApiSettings) }) {
                        Icon(Icons.Outlined.Key, "DeepSeek 设置")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { importer.launch(arrayOf("text/plain", "application/epub+zip")) }) {
                Icon(Icons.Outlined.Add, "导入本地小说")
            }
        },
    ) { padding ->
        if (state.books.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.MenuBook, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text("书架还是空的", style = MaterialTheme.typography.titleLarge)
                    Text("导入 TXT 或 EPUB 开始阅读", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.books, key = { it.id }) { book ->
                    BookCard(book, onOpen = { viewModel.openBook(book.id) }, onDelete = { pendingDelete = book })
                }
            }
        }
    }
    pendingDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除书籍") },
            text = { Text("确定删除《${book.title}》及其阅读进度、书签和本地正文吗？") },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        viewModel.deleteBook(book.id)
                    },
                ) { Text("删除") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookCard(book: Book, onOpen: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onOpen, onLongClick = onDelete),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)),
    ) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(width = 52.dp, height = 72.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.MenuBook, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                book.author?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Spacer(Modifier.height(6.dp))
                Text(
                    "${book.format.name} · ${book.chapterCount} 章 · ${formatChars(book.totalChars)}字",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    book.lastReadAt?.let { "上次阅读 ${DateFormat.getDateInstance().format(Date(it))}" } ?: "尚未阅读",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.DeleteOutline, "删除") }
        }
    }
}

private fun formatChars(value: Long): String = when {
    value >= 10_000 -> "%.1f万".format(value / 10_000f)
    else -> value.toString()
}
