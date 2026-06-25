package com.lightreader.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightreader.app.core.model.AppSkin
import com.lightreader.app.core.model.Book
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(state: MainUiState, viewModel: MainViewModel) {
    var pendingDelete by remember { mutableStateOf<Book?>(null) }
    var showSkinPicker by remember { mutableStateOf(false) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importBook)
    }

    FreshBackdrop(state.preferences.appSkin, Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = {
                        Column {
                            Text(
                                "轻阅",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                            Text(
                                if (state.books.isEmpty()) "慢一点，读得更深" else "今天也留一点时间给故事",
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.SansSerif),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSkinPicker = true }) {
                            Icon(Icons.Outlined.Palette, "更换皮肤")
                        }
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
                ExtendedFloatingActionButton(
                    onClick = { importer.launch(arrayOf("text/plain", "application/epub+zip")) },
                    icon = { Icon(Icons.Outlined.Add, null) },
                    text = { Text("导入小说", style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.SansSerif)) },
                    shape = RoundedCornerShape(18.dp),
                )
            },
        ) { padding ->
            if (state.books.isEmpty()) {
                EmptyLibrary(Modifier.fillMaxSize().padding(padding).padding(24.dp))
            } else {
                Column(Modifier.fillMaxSize().padding(padding)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(
                            "我的书架",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                        Text(
                            "${state.books.size} 本",
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.SansSerif),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(96.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 104.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        items(state.books, key = { it.id }) { book ->
                            BookCard(book, onOpen = { viewModel.openBook(book.id) }, onDelete = { pendingDelete = book })
                        }
                    }
                }
            }
        }
    }

    if (showSkinPicker) {
        ModalBottomSheet(
            onDismissRequest = { showSkinPicker = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            SkinPickerPanel(
                selected = state.preferences.appSkin,
                onSelect = { skin -> viewModel.savePreferences(state.preferences.copy(appSkin = skin)) },
            )
        }
    }

    pendingDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除书籍") },
            text = { Text("确定删除《${book.title}》及其阅读进度、书签和本地正文吗？") },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
            confirmButton = {
                TextButton(onClick = { pendingDelete = null; viewModel.deleteBook(book.id) }) { Text("删除") }
            },
        )
    }
}

@Composable
private fun EmptyLibrary(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .92f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(
                Modifier.padding(horizontal = 34.dp, vertical = 38.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.MenuBook,
                        null,
                        Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    "书架还是空的",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "导入 TXT 或 EPUB\n让下一段故事在这里发生",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.SansSerif),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookCard(book: Book, onOpen: () -> Unit, onDelete: () -> Unit) {
    Column(Modifier.fillMaxWidth().combinedClickable(onClick = onOpen, onLongClick = onDelete)) {
        Box {
            PastelBookCover(book)
            IconButton(
                onClick = onDelete,
                modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = .78f)),
            ) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    "删除",
                    Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            book.title,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 18.sp,
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            book.author?.takeIf { it.isNotBlank() } ?: "佚名",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = FontFamily.SansSerif,
                fontSize = 12.sp,
                lineHeight = 15.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            book.lastReadAt?.let { DateFormat.getDateInstance(DateFormat.SHORT).format(Date(it)) }
                ?: "${book.chapterCount} 章 · ${book.format.name}",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.SansSerif,
                fontSize = 11.sp,
                lineHeight = 14.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .74f),
        )
        }
}

@Composable
private fun PastelBookCover(book: Book) {
    val variants = listOf(
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.surfaceVariant,
        MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.secondaryContainer,
    )
    val colors = variants[(book.title.hashCode() and Int.MAX_VALUE) % variants.size]
    Card(
        modifier = Modifier.fillMaxWidth().aspectRatio(.72f),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box(
            Modifier.fillMaxSize().background(Brush.linearGradient(listOf(colors.first, colors.second))),
        ) {
            Box(
                Modifier.size(62.dp).align(Alignment.TopEnd).padding(7.dp)
                    .clip(CircleShape).background(Color.White.copy(alpha = .22f)),
            )
            Column(
                Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    book.format.name,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.SansSerif, fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    book.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                    ),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "LIGHT READER · ${book.chapterCount}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.SansSerif, fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SkinPickerPanel(selected: AppSkin, onSelect: (AppSkin) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, bottom = 30.dp)) {
        Text("换一种阅读心情", style = MaterialTheme.typography.headlineSmall)
        Text(
            "皮肤会应用到书架和功能页面，正文背景仍可单独设置。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(18.dp))
        AppSkin.entries.forEach { skin ->
            val palette = skin.palette()
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { onSelect(skin) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                    Box(Modifier.size(42.dp).clip(CircleShape).background(palette.soft))
                    Box(Modifier.size(42.dp).clip(CircleShape).background(palette.primary))
                }
                Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    Text(skin.label(), style = MaterialTheme.typography.titleMedium)
                    Text(skin.caption(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (skin == selected) Icon(Icons.Outlined.CheckCircle, "已选择", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private fun AppSkin.label(): String = when (this) {
    AppSkin.MINT -> "薄荷绿白"
    AppSkin.OCEAN -> "海盐蓝白"
    AppSkin.APRICOT -> "奶杏白"
    AppSkin.SAKURA -> "樱花粉白"
}

private fun AppSkin.caption(): String = when (this) {
    AppSkin.MINT -> "清透、安静，适合长时间阅读"
    AppSkin.OCEAN -> "像晴天海风一样轻盈"
    AppSkin.APRICOT -> "温暖柔和的纸张气息"
    AppSkin.SAKURA -> "克制的粉调与生活感"
}

private fun formatChars(value: Long): String = when {
    value >= 10_000 -> "%.1f万".format(value / 10_000f)
    else -> value.toString()
}
