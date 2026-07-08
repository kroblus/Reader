package com.lightreader.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightreader.app.R
import com.lightreader.app.core.model.AppSkin
import com.lightreader.app.core.model.Book
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(state: MainUiState, viewModel: MainViewModel) {
    var pendingDelete by remember { mutableStateOf<Book?>(null) }
    var showSkinPicker by remember { mutableStateOf(false) }
    var showMoreActions by remember { mutableStateOf(false) }
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.ic_logo_lined),
                                    contentDescription = null,
                                    modifier = Modifier.size(30.dp),
                                )
                            }
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(
                                    stringResource(R.string.brand_name),
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontFamily = FontFamily.SansSerif,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                )
                                Text(
                                    stringResource(if (state.books.isEmpty()) R.string.brand_tagline_empty else R.string.brand_tagline_library),
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.SansSerif),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.navigate(AppScreen.WebImport) }) {
                            Icon(Icons.Outlined.CloudDownload, stringResource(R.string.library_web_import))
                        }
                        Box {
                            IconButton(onClick = { showMoreActions = true }) {
                                Icon(Icons.Outlined.MoreVert, stringResource(R.string.action_more))
                            }
                            DropdownMenu(
                                expanded = showMoreActions,
                                onDismissRequest = { showMoreActions = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.library_change_skin)) },
                                    leadingIcon = { Icon(Icons.Outlined.Palette, null) },
                                    onClick = {
                                        showMoreActions = false
                                        showSkinPicker = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.library_deepseek_settings)) },
                                    leadingIcon = { Icon(Icons.Outlined.Key, null) },
                                    onClick = {
                                        showMoreActions = false
                                        viewModel.navigate(AppScreen.ApiSettings)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.library_dom_bridge)) },
                                    leadingIcon = { Icon(Icons.Outlined.Code, null) },
                                    onClick = {
                                        showMoreActions = false
                                        viewModel.navigate(AppScreen.WebDomBridge)
                                    },
                                )
                            }
                        }
                    },
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { importer.launch(arrayOf("text/plain", "application/epub+zip")) },
                    icon = { Icon(Icons.Outlined.Add, null) },
                    text = {
                        Text(
                            stringResource(R.string.library_import_novel),
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.SansSerif),
                        )
                    },
                    shape = RoundedCornerShape(18.dp),
                )
            },
        ) { padding ->
            if (state.books.isEmpty()) {
                EmptyLibrary(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                    onImport = { importer.launch(arrayOf("text/plain", "application/epub+zip")) },
                    onWebImport = { viewModel.navigate(AppScreen.WebImport) },
                )
            } else {
                Column(Modifier.fillMaxSize().padding(padding)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(
                            stringResource(R.string.library_my_shelf),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                        Text(
                            pluralStringResource(R.plurals.library_book_count, state.books.size, state.books.size),
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
                            BookCard(
                                book = book,
                                onOpen = { viewModel.openBook(book.id) },
                                onDelete = { pendingDelete = book },
                            )
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
            title = { Text(stringResource(R.string.library_delete_book_title)) },
            text = { Text(stringResource(R.string.library_delete_book_message, book.title)) },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) } },
            confirmButton = {
                TextButton(onClick = { pendingDelete = null; viewModel.deleteBook(book.id) }) { Text(stringResource(R.string.action_delete)) }
            },
        )
    }
}

@Composable
private fun EmptyLibrary(
    modifier: Modifier = Modifier,
    onImport: () -> Unit,
    onWebImport: () -> Unit,
) {
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
                    Image(
                        painter = painterResource(R.drawable.ic_logo_lined),
                        contentDescription = null,
                        modifier = Modifier.size(58.dp),
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    stringResource(R.string.library_empty_title),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.library_empty_body),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.SansSerif),
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = onImport) { Text(stringResource(R.string.library_import_file)) }
                    TextButton(onClick = onWebImport) { Text(stringResource(R.string.library_web_import)) }
                }
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
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = .82f)),
            ) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    stringResource(R.string.action_delete),
                    Modifier.size(20.dp),
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
            book.author?.takeIf { it.isNotBlank() } ?: stringResource(R.string.library_unknown_author),
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
            book.lastReadAt?.let {
                stringResource(R.string.library_continue_reading, DateFormat.getDateInstance(DateFormat.SHORT).format(Date(it)))
            } ?: stringResource(R.string.library_not_started, book.chapterCount, book.format.name),
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
                    stringResource(R.string.library_cover_footer, book.chapterCount),
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
        Text(stringResource(R.string.library_skin_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.library_skin_body),
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
                if (skin == selected) Icon(Icons.Outlined.CheckCircle, stringResource(R.string.action_selected), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun AppSkin.label(): String = when (this) {
    AppSkin.MINT -> stringResource(R.string.skin_mint)
    AppSkin.OCEAN -> stringResource(R.string.skin_ocean)
    AppSkin.APRICOT -> stringResource(R.string.skin_apricot)
    AppSkin.SAKURA -> stringResource(R.string.skin_sakura)
}

@Composable
private fun AppSkin.caption(): String = when (this) {
    AppSkin.MINT -> stringResource(R.string.skin_mint_caption)
    AppSkin.OCEAN -> stringResource(R.string.skin_ocean_caption)
    AppSkin.APRICOT -> stringResource(R.string.skin_apricot_caption)
    AppSkin.SAKURA -> stringResource(R.string.skin_sakura_caption)
}
