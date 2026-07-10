package com.lightreader.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightreader.app.R
import com.lightreader.app.core.model.AppLanguage
import com.lightreader.app.core.model.AppSkin
import com.lightreader.app.core.model.Book
import com.lightreader.app.core.model.ReaderPreferences
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    viewModel: LibraryViewModel,
    onOpenAppSettings: () -> Unit,
    onSavePreferences: (ReaderPreferences) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<Book?>(null) }
    var pendingEdit by remember { mutableStateOf<Book?>(null) }
    var showMoreActions by remember { mutableStateOf(false) }
    var showSkinPicker by remember { mutableStateOf(false) }
    val taglines = stringArrayResource(R.array.brand_taglines)
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
                                    taglines.taglineAt(state.libraryTaglineIndex),
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.SansSerif),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSkinPicker = true }) {
                            Icon(Icons.Outlined.Palette, stringResource(R.string.library_change_skin))
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
                                    text = { Text(stringResource(R.string.library_app_settings)) },
                                    leadingIcon = { Icon(Icons.Outlined.Settings, null) },
                                    onClick = {
                                        showMoreActions = false
                                        onOpenAppSettings()
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
            if (state.shelfBooks.isEmpty()) {
                EmptyLibrary(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                    onImport = { importer.launch(arrayOf("text/plain", "application/epub+zip")) },
                )
            } else {
                Column(Modifier.fillMaxSize().padding(padding)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = ReaderUiTokens.pagePadding, vertical = 10.dp),
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
                            pluralStringResource(R.plurals.library_book_count, state.shelfBooks.size, state.shelfBooks.size),
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.SansSerif),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(if (state.shelfBooks.size == 1) 280.dp else 112.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = ReaderUiTokens.pagePadding, end = ReaderUiTokens.pagePadding, bottom = 104.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            RecentReadingCard(
                                shelfBook = state.shelfBooks.first(),
                                onOpen = { viewModel.openBook(state.shelfBooks.first().book.id) },
                                onEdit = { pendingEdit = state.shelfBooks.first().book },
                                onDelete = { pendingDelete = state.shelfBooks.first().book },
                            )
                        }
                        if (state.shelfBooks.size > 1) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    stringResource(R.string.library_all_books),
                                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = FontFamily.SansSerif,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                )
                            }
                            items(state.shelfBooks, key = { it.book.id }) { shelfBook ->
                                BookCard(
                                    shelfBook = shelfBook,
                                    onOpen = { viewModel.openBook(shelfBook.book.id) },
                                    onEdit = { pendingEdit = shelfBook.book },
                                    onDelete = { pendingDelete = shelfBook.book },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    pendingEdit?.let { book ->
        EditBookDialog(
            book = book,
            onDismiss = { pendingEdit = null },
            onSave = { title, author ->
                viewModel.updateBookMetadata(book.id, title, author)
                pendingEdit = null
            },
        )
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

    state.pendingDuplicateImport?.let { duplicate ->
        val existing = duplicate.candidate.existingBook ?: return@let
        AlertDialog(
            onDismissRequest = viewModel::dismissDuplicateImport,
            title = { Text(stringResource(R.string.library_duplicate_import_title)) },
            text = { Text(stringResource(R.string.library_duplicate_import_body, existing.title)) },
            dismissButton = {
                TextButton(onClick = viewModel::openExistingDuplicate) {
                    Text(stringResource(R.string.library_duplicate_import_open))
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::importDuplicateCopy) {
                    Text(stringResource(R.string.library_duplicate_import_copy))
                }
            },
        )
    }

    if (showSkinPicker) {
        ModalBottomSheet(
            onDismissRequest = { showSkinPicker = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            SkinPickerPanel(
                selected = state.preferences.appSkin,
                onSelect = { skin -> onSavePreferences(state.preferences.copy(appSkin = skin)) },
            )
        }
    }
}

@Composable
private fun EditBookDialog(
    book: Book,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var title by remember(book.id) { mutableStateOf(book.title) }
    var author by remember(book.id) { mutableStateOf(book.author.orEmpty()) }
    var titleTouched by remember(book.id) { mutableStateOf(false) }
    val titleError = titleTouched && title.trim().isBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_edit_book_info)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        titleTouched = true
                    },
                    label = { Text(stringResource(R.string.library_book_title)) },
                    isError = titleError,
                    supportingText = {
                        if (titleError) Text(stringResource(R.string.library_edit_title_required))
                    },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text(stringResource(R.string.library_book_author)) },
                    singleLine = true,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        confirmButton = {
            TextButton(
                onClick = {
                    titleTouched = true
                    if (title.trim().isNotBlank()) onSave(title, author)
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    preferences: ReaderPreferences,
    readerViewModel: ReaderViewModel,
    settingsViewModel: SettingsViewModel,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = {
                    Text(
                        stringResource(R.string.app_settings_title),
                        modifier = Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = settingsViewModel::toggleDeveloperTools,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = readerViewModel::goBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            AppSettingsSection(stringResource(R.string.app_settings_reading)) {
                Text(
                    stringResource(R.string.app_settings_language),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AppLanguage.entries.forEach { language ->
                    AppLanguageRow(
                        language = language,
                        selected = preferences.appLanguage == language,
                        onClick = { settingsViewModel.saveAppLanguage(language) },
                    )
                }
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_volume_keys),
                    checked = preferences.volumeKeys,
                    onCheckedChange = { settingsViewModel.savePreferences(preferences.copy(volumeKeys = it)) },
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_keep_screen_on),
                    checked = preferences.keepScreenOn,
                    onCheckedChange = { settingsViewModel.savePreferences(preferences.copy(keepScreenOn = it)) },
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_lock_portrait),
                    checked = preferences.lockPortrait,
                    onCheckedChange = { settingsViewModel.savePreferences(preferences.copy(lockPortrait = it)) },
                )
            }
            AppSettingsSection(stringResource(R.string.app_settings_import_download)) {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_clean_txt_noise),
                    checked = preferences.cleanTxtNoise,
                    onCheckedChange = { settingsViewModel.savePreferences(preferences.copy(cleanTxtNoise = it)) },
                )
                SettingsNavigationRow(
                    title = stringResource(R.string.library_web_import),
                    icon = { Icon(Icons.Outlined.CloudDownload, null) },
                    onClick = { readerViewModel.navigate(AppScreen.WebImport) },
                )
                SettingsNavigationRow(
                    title = stringResource(R.string.library_deepseek_settings),
                    icon = { Icon(Icons.Outlined.Key, null) },
                    onClick = { readerViewModel.navigate(AppScreen.ApiSettings) },
                )
            }
            AppSettingsSection(stringResource(R.string.app_settings_appearance)) {
                SettingsNavigationRow(
                    title = stringResource(R.string.library_change_skin),
                    icon = { Icon(Icons.Outlined.Palette, null) },
                    onClick = { settingsViewModel.savePreferences(preferences.copy(appSkin = preferences.appSkin.next())) },
                )
            }
            if (preferences.developerToolsEnabled) {
                AppSettingsSection(stringResource(R.string.app_settings_advanced_tools)) {
                    SettingsNavigationRow(
                        title = stringResource(R.string.library_dom_bridge),
                        icon = { Icon(Icons.Outlined.Code, null) },
                        onClick = { readerViewModel.navigate(AppScreen.WebDomBridge) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppSettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
            ),
        )
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content,
        )
    }
}

@Composable
private fun AppLanguageRow(language: AppLanguage, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(language.label(), style = MaterialTheme.typography.bodyLarge)
            Text(language.caption(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SettingsSwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsNavigationRow(title: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        icon()
        Text(title, Modifier.padding(start = 10.dp))
    }
}

@Composable
private fun AppLanguage.label(): String = when (this) {
    AppLanguage.SYSTEM -> stringResource(R.string.language_system)
    AppLanguage.ZH_CN -> stringResource(R.string.language_zh_cn)
    AppLanguage.EN -> stringResource(R.string.language_en)
}

@Composable
private fun AppLanguage.caption(): String = when (this) {
    AppLanguage.SYSTEM -> stringResource(R.string.language_system_caption)
    AppLanguage.ZH_CN -> stringResource(R.string.language_zh_cn_caption)
    AppLanguage.EN -> stringResource(R.string.language_en_caption)
}

@Composable
private fun EmptyLibrary(
    modifier: Modifier = Modifier,
    onImport: () -> Unit,
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
                TextButton(onClick = onImport) { Text(stringResource(R.string.library_import_file)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookCard(shelfBook: ShelfBookUi, onOpen: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val book = shelfBook.book
    var showBookActions by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ReaderUiTokens.cardRadius))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = .58f))
            .combinedClickable(onClick = onOpen, onLongClick = onDelete)
            .padding(8.dp),
    ) {
        Box {
            PastelBookCover(book)
            Box(Modifier.align(Alignment.TopEnd)) {
                IconButton(
                    onClick = { showBookActions = true },
                    modifier = Modifier.padding(5.dp).size(34.dp),
                ) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        stringResource(R.string.library_book_actions),
                        Modifier.size(17.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .58f),
                    )
                }
                DropdownMenu(
                    expanded = showBookActions,
                    onDismissRequest = { showBookActions = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.library_edit_book_info)) },
                        leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                        onClick = {
                            showBookActions = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete)) },
                        leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null) },
                        onClick = {
                            showBookActions = false
                            onDelete()
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
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
        Spacer(Modifier.height(3.dp))
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
        Spacer(Modifier.height(6.dp))
        Text(
            shelfBook.currentChapterTitle?.let {
                stringResource(R.string.library_current_chapter, it)
            } ?: stringResource(R.string.library_not_started, book.chapterCount, book.format.name),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.SansSerif,
                fontSize = 12.sp,
                lineHeight = 15.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .88f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(7.dp))
        ShelfProgressBar(progressPercent = shelfBook.progressPercent, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        Text(
            if (shelfBook.started) {
                stringResource(R.string.library_progress_percent, shelfBook.progressPercent)
            } else {
                stringResource(R.string.library_ready_to_start)
            },
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
            ),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun RecentReadingCard(shelfBook: ShelfBookUi, onOpen: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val book = shelfBook.book
    var showBookActions by remember { mutableStateOf(false) }
    val cardShape = RoundedCornerShape(ReaderUiTokens.cardRadius)
    val lastReadDate = shelfBook.updatedAt?.let { DateFormat.getDateInstance(DateFormat.SHORT).format(Date(it)) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .10f), cardShape)
            .clickable(onClick = onOpen),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .72f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box {
            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, top = 14.dp, end = 14.dp, bottom = 13.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(width = 64.dp, height = 88.dp)) {
                    PastelBookCover(book, compact = true)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        stringResource(R.string.library_recent_reading),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        book.title,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            lineHeight = 25.sp,
                        ),
                    )
                    Text(
                        book.author?.takeIf { it.isNotBlank() } ?: stringResource(R.string.library_unknown_author),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.SansSerif),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        shelfBook.currentChapterTitle?.let { stringResource(R.string.library_current_chapter, it) }
                            ?: stringResource(R.string.library_not_started, book.chapterCount, book.format.name),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.SansSerif),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .9f),
                    )
                    ShelfProgressBar(progressPercent = shelfBook.progressPercent, modifier = Modifier.fillMaxWidth())
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            stringResource(R.string.library_progress_percent, shelfBook.progressPercent),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                stringResource(R.string.library_continue_button),
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                            lastReadDate?.let {
                                Text(
                                    stringResource(R.string.library_last_read_date, it),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .68f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.SansSerif,
                                        fontSize = 10.sp,
                                        lineHeight = 12.sp,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
            Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                IconButton(
                    onClick = { showBookActions = true },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        stringResource(R.string.library_book_actions),
                        Modifier.size(17.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .58f),
                    )
                }
                DropdownMenu(
                    expanded = showBookActions,
                    onDismissRequest = { showBookActions = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.library_edit_book_info)) },
                        leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                        onClick = {
                            showBookActions = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete)) },
                        leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null) },
                        onClick = {
                            showBookActions = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ShelfProgressBar(progressPercent: Int, modifier: Modifier = Modifier) {
    val progress = (progressPercent.coerceIn(0, 100) / 100f)
    Box(
        modifier
            .height(4.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = .11f)),
    ) {
        if (progress > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = .82f)),
            )
        }
    }
}

@Composable
private fun PastelBookCover(book: Book, compact: Boolean = false) {
    val variants = listOf(
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.surfaceVariant,
        MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.secondaryContainer,
    )
    val colors = variants[(book.title.hashCode() and Int.MAX_VALUE) % variants.size]
    Card(
        modifier = Modifier.fillMaxWidth().aspectRatio(.72f),
        shape = RoundedCornerShape(if (compact) 14.dp else 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (compact) 1.dp else 2.dp),
    ) {
        Box(
            Modifier.fillMaxSize().background(Brush.linearGradient(listOf(colors.first, colors.second))),
        ) {
            Box(
                Modifier.size(if (compact) 48.dp else 62.dp).align(Alignment.TopEnd).padding(if (compact) 6.dp else 7.dp)
                    .clip(CircleShape).background(Color.White.copy(alpha = if (compact) .16f else .22f)),
            )
            Column(
                Modifier.fillMaxSize().padding(if (compact) 10.dp else 12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    book.format.name,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = if (compact) 9.sp else 10.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (compact) .76f else 1f),
                )
                Text(
                    book.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (compact) 13.sp else 15.sp,
                        lineHeight = if (compact) 17.sp else 20.sp,
                    ),
                    maxLines = if (compact) 2 else 3,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(R.string.library_cover_footer, book.chapterCount),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = if (compact) 7.sp else 9.sp,
                        lineHeight = if (compact) 9.sp else 11.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (compact) .46f else .78f),
                )
            }
        }
    }
}

private fun Array<String>.taglineAt(index: Int): String {
    if (isEmpty()) return ""
    val normalizedIndex = ((index % size) + size) % size
    return this[normalizedIndex]
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
            val isSelected = skin == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(ReaderUiTokens.cardRadius))
                    .background(if (isSelected) palette.soft.copy(alpha = .72f) else Color.Transparent)
                    .border(
                        width = if (isSelected) 1.5.dp else 0.dp,
                        color = if (isSelected) palette.primary.copy(alpha = .32f) else Color.Transparent,
                        shape = RoundedCornerShape(ReaderUiTokens.cardRadius),
                    )
                    .clickable { onSelect(skin) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(palette.soft))
                    Box(Modifier.size(34.dp).clip(CircleShape).background(palette.primary))
                }
                Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    Text(skin.label(), style = MaterialTheme.typography.titleMedium)
                    Text(skin.caption(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (isSelected) Icon(Icons.Outlined.CheckCircle, stringResource(R.string.action_selected), tint = MaterialTheme.colorScheme.primary)
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

private fun AppSkin.next(): AppSkin {
    val entries = AppSkin.entries
    return entries[(entries.indexOf(this) + 1) % entries.size]
}
