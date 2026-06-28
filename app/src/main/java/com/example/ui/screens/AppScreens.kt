package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.MainViewModel
import com.example.Screen
import com.example.R
import com.example.data.model.FileItem
import com.example.ui.theme.SaffronPrimary
import com.example.ui.theme.SaffronSecondary
import com.example.ui.theme.SaffronTertiary
import com.example.util.GeminiService
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- HELPER METADATA FORMATTING ---
fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

fun formatTimestamp(ms: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(ms))
}

// --- CORE APP LAYOUT WRAPPER ---
@Composable
fun AppNavigationWrapper(viewModel: MainViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val isIndexing by viewModel.isIndexing.collectAsState()
    val progress by viewModel.indexingProgress.collectAsState()
    val total by viewModel.indexingTotal.collectAsState()

    Scaffold(
        bottomBar = {
            NavigationBar(
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                NavigationBarItem(
                    selected = currentScreen is Screen.Dashboard || currentScreen is Screen.Explorer || currentScreen is Screen.Duplicates || currentScreen is Screen.JunkCleaner || currentScreen is Screen.Vault || currentScreen is Screen.MediaCenter,
                    onClick = { viewModel.navigateTo(Screen.Dashboard) },
                    icon = { Icon(Icons.Default.Folder, contentDescription = "Local Files") },
                    label = { Text("Local Manager") }
                )
                NavigationBarItem(
                    selected = currentScreen is Screen.CloudManager,
                    onClick = { viewModel.navigateTo(Screen.CloudManager) },
                    icon = { Icon(Icons.Default.Cloud, contentDescription = "Cloud Drive") },
                    label = { Text("Cloud Manager") }
                )
                NavigationBarItem(
                    selected = currentScreen is Screen.AIAssistant,
                    onClick = { viewModel.navigateTo(Screen.AIAssistant) },
                    icon = { Icon(Icons.Default.SmartButton, contentDescription = "AI Assistant") },
                    label = { Text("AI & Config") }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Indexing progress header
            if (isIndexing) {
                LinearProgressIndicator(
                    progress = if (total > 0) progress.toFloat() / total.toFloat() else 0f,
                    modifier = Modifier.fillMaxWidth(),
                    color = SaffronPrimary,
                    trackColor = SaffronSecondary.copy(alpha = 0.3f)
                )
                Text(
                    text = "Indexing sandbox storage... ($progress/$total)",
                    style = MaterialTheme.typography.bodySmall,
                    color = SaffronPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SaffronPrimary.copy(alpha = 0.1f))
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    textAlign = TextAlign.Center
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (currentScreen) {
                    is Screen.Dashboard -> DashboardScreen(viewModel)
                    is Screen.Explorer -> ExplorerScreen(viewModel)
                    is Screen.Search -> SearchScreen(viewModel)
                    is Screen.Duplicates -> DuplicatesScreen(viewModel)
                    is Screen.JunkCleaner -> JunkCleanerScreen(viewModel)
                    is Screen.Vault -> VaultScreen(viewModel)
                    is Screen.MediaCenter -> MediaCenterScreen(viewModel)
                    is Screen.Settings -> SettingsScreen(viewModel)
                    is Screen.CloudManager -> CloudManagerScreen(viewModel)
                    is Screen.AIAssistant -> AIAssistantScreen(viewModel)
                }
            }
        }
    }
}

// --- 1. DASHBOARD SCREEN ---
@Composable
fun DashboardScreen(viewModel: MainViewModel) {
    val freeSpace by viewModel.storageFreeSpace.collectAsState()
    val totalSpace by viewModel.storageTotalSpace.collectAsState()
    val usedSpace = totalSpace - freeSpace
    val usedRatio = if (totalSpace > 0) usedSpace.toFloat() / totalSpace.toFloat() else 0f

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Image(
                painter = painterResource(id = R.drawable.img_dashboard_hero),
                contentDescription = "Hero banner",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "VVF Smart File Manager Ultra",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                ),
                color = SaffronPrimary
            )
            Text(
                text = "Smart, secure, and offline-first data manager",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        // Storage Overview Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Circle Progress Drawing
                    Box(
                        modifier = Modifier.size(96.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(80.dp)) {
                            drawCircle(
                                color = Color.Gray.copy(alpha = 0.2f),
                                style = Stroke(width = 8.dp.toPx())
                            )
                            drawArc(
                                brush = Brush.sweepGradient(listOf(SaffronPrimary, SaffronTertiary)),
                                startAngle = -90f,
                                sweepAngle = usedRatio * 360f,
                                useCenter = false,
                                style = Stroke(width = 8.dp.toPx())
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${(usedRatio * 100).toInt()}%",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Used",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(24.dp))

                    Column {
                        Text(
                            text = "Internal Storage",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Used: ${formatFileSize(usedSpace)} / ${formatFileSize(totalSpace)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Free: ${formatFileSize(freeSpace)} available",
                            style = MaterialTheme.typography.bodySmall,
                            color = SaffronPrimary
                        )
                    }
                }
            }
        }

        // Smart Categories Row / Grid
        item {
            val categories by viewModel.allCategories.collectAsState()
            Text(
                text = "Smart Categories",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (categories.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(modifier = Modifier.padding(16.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No categories indexed. Run file explorer scan.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    }
                }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(categories) { category ->
                        val icon = when (category.name) {
                            "Photos" -> Icons.Default.Folder
                            "Videos" -> Icons.Default.PlayArrow
                            "Audio" -> Icons.Default.PlayArrow
                            "APK" -> Icons.Default.Settings
                            else -> Icons.Default.Folder
                        }
                        Card(
                            modifier = Modifier
                                .width(130.dp)
                                .clickable { viewModel.setCategoryFilter(category.name) }
                                .testTag("category_${category.name.lowercase().replace(" ", "_")}"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Icon(icon, contentDescription = category.name, tint = SaffronPrimary, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(category.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${category.fileCount} files", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                Text(formatFileSize(category.totalSize), style = MaterialTheme.typography.labelSmall, color = SaffronPrimary)
                            }
                        }
                    }
                }
            }
        }

        // Quick Actions Grid
        item {
            Text(
                text = "Quick Utilities",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(8.dp))

            val itemsList = listOf(
                Triple("Explorer", Icons.Default.Folder, Screen.Explorer),
                Triple("AI Search", Icons.Default.Search, Screen.Search),
                Triple("Duplicates", Icons.Default.Delete, Screen.Duplicates),
                Triple("Junk Clean", Icons.Default.Delete, Screen.JunkCleaner),
                Triple("Vault", Icons.Default.Lock, Screen.Vault),
                Triple("Media Center", Icons.Default.PlayArrow, Screen.MediaCenter),
                Triple("Settings", Icons.Default.Settings, Screen.Settings)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(itemsList) { (label, icon, screen) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.navigateTo(screen) }
                            .testTag("action_${label.lowercase().replace(" ", "_")}"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                icon,
                                contentDescription = label,
                                tint = SaffronPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- 2. FILE EXPLORER SCREEN ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExplorerScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val currentPath by viewModel.currentPath.collectAsState()
    val files by viewModel.explorerFiles.collectAsState()
    val pagedFiles = viewModel.pagedExplorerFiles.collectAsLazyPagingItems()
    val selected by viewModel.selectedFiles.collectAsState()
    val clipboard by viewModel.clipboard.collectAsState()
    val selectedCategoryFilter by viewModel.selectedCategoryFilter.collectAsState()

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    var activeFileItemForMenu by remember { mutableStateOf<FileItem?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameFileName by remember { mutableStateOf("") }
    var showDetailsDialog by remember { mutableStateOf<FileItem?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Toolbar with path address
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { 
                    if (selectedCategoryFilter != null) {
                        viewModel.setCategoryFilter(null)
                    } else {
                        viewModel.navigateUp() 
                    }
                },
                enabled = selectedCategoryFilter != null || !viewModel.isAtSandboxRoot()
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }

            Text(
                text = selectedCategoryFilter?.let { "Category: $it" } ?: (if (currentPath.isEmpty()) "VVF_Smart_Explorer" else File(currentPath).name),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (selectedCategoryFilter != null) {
                IconButton(onClick = { viewModel.setCategoryFilter(null) }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear Filter", tint = SaffronPrimary)
                }
            } else {
                IconButton(onClick = { showCreateFolderDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Create Folder", tint = SaffronPrimary)
                }
            }

            if (selected.isNotEmpty()) {
                IconButton(onClick = {
                    try {
                        val uris = ArrayList<android.net.Uri>()
                        val authority = "${context.packageName}.fileprovider"
                        for (path in selected) {
                            val f = File(path)
                            if (!f.isDirectory) {
                                val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, f)
                                uris.add(uri)
                            }
                        }
                        if (uris.isNotEmpty()) {
                            val intent = if (uris.size == 1) {
                                android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = context.contentResolver.getType(uris[0]) ?: "*/*"
                                    putExtra(android.content.Intent.EXTRA_STREAM, uris[0])
                                }
                            } else {
                                android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
                                    type = "*/*"
                                    putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, uris)
                                }
                            }
                            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            context.startActivity(android.content.Intent.createChooser(intent, "Share Files"))
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }) {
                    Icon(Icons.Default.Share, contentDescription = "Share Selected", tint = SaffronPrimary)
                }
                IconButton(onClick = { viewModel.deleteSelectedFiles() }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Selected", tint = Color.Red)
                }
                IconButton(onClick = { viewModel.clearSelection() }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear Selection")
                }
            }
        }

        // Selected Files Counter
        if (selected.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SaffronPrimary.copy(alpha = 0.1f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${selected.size} items selected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SaffronPrimary,
                    fontWeight = FontWeight.Bold
                )
                Row {
                    TextButton(onClick = { viewModel.setClipboard("COPY") }) {
                        Text("Copy", color = SaffronPrimary)
                    }
                    TextButton(onClick = { viewModel.setClipboard("MOVE") }) {
                        Text("Move", color = SaffronPrimary)
                    }
                }
            }
        }

        // Clipboard state panel
        if (clipboard != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SaffronTertiary.copy(alpha = 0.2f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Clipboard: ${clipboard?.first?.let { File(it).name }} ready to ${clipboard?.second}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Button(
                    onClick = { viewModel.pasteClipboard() },
                    colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary)
                ) {
                    Text("Paste")
                }
            }
        }

        // Sort & View Mode row
        val viewMode by viewModel.viewMode.collectAsState()
        val sortBy by viewModel.sortBy.collectAsState()
        val sortOrder by viewModel.sortOrder.collectAsState()
        var showSortMenu by remember { mutableStateOf(false) }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Sort Dropdown button
            Box {
                TextButton(
                    onClick = { showSortMenu = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = SaffronPrimary)
                ) {
                    Icon(
                        imageVector = if (sortOrder == com.example.SortOrder.ASCENDING) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                        contentDescription = "Sort Direction",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sort: ${sortBy.name.lowercase().capitalize(Locale.ROOT)}")
                }
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    com.example.SortBy.values().forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.name.lowercase().capitalize(Locale.ROOT)) },
                            onClick = {
                                viewModel.setSortBy(option)
                                showSortMenu = false
                            },
                            leadingIcon = {
                                if (sortBy == option) {
                                    Icon(Icons.Default.Check, contentDescription = "Selected", modifier = Modifier.size(16.dp))
                                }
                            }
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(if (sortOrder == com.example.SortOrder.ASCENDING) "Descending" else "Ascending") },
                        onClick = {
                            viewModel.toggleSortOrder()
                            showSortMenu = false
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (sortOrder == com.example.SortOrder.ASCENDING) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                contentDescription = "Toggle Order",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }

            // Grid / List toggle icon button
            IconButton(onClick = { viewModel.toggleViewMode() }) {
                Icon(
                    imageVector = if (viewMode == com.example.ViewMode.LIST) Icons.Default.GridView else Icons.Default.List,
                    contentDescription = if (viewMode == com.example.ViewMode.LIST) "Switch to Grid View" else "Switch to List View",
                    tint = SaffronPrimary
                )
            }
        }

        // Files & Folders List
        if (pagedFiles.itemCount == 0) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Folder, contentDescription = "Empty", tint = Color.Gray, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("This folder is empty.", color = Color.Gray)
                }
            }
        } else {
            if (viewMode == com.example.ViewMode.LIST) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(pagedFiles.itemCount) { index ->
                        val file = pagedFiles[index] ?: return@items
                        val isSelected = selected.contains(file.path)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (selected.isNotEmpty()) {
                                            viewModel.toggleFileSelection(file.path)
                                        } else if (file.isDirectory) {
                                            viewModel.loadExplorerFiles(file.path)
                                        }
                                    },
                                    onLongClick = {
                                        viewModel.toggleFileSelection(file.path)
                                    }
                                )
                                .background(if (isSelected) SaffronPrimary.copy(alpha = 0.15f) else Color.Transparent)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.Info,
                                contentDescription = if (file.isDirectory) "Folder" else "File",
                                tint = if (file.isDirectory) SaffronSecondary else Color.Gray,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (file.isDirectory) "Folder" else "${formatFileSize(file.size)} • ${formatTimestamp(file.lastModified)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
     
                            // More options trigger
                            IconButton(onClick = { activeFileItemForMenu = file }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "File Options")
                            }
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(110.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 80.dp, start = 8.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(pagedFiles.itemCount) { index ->
                        val file = pagedFiles[index] ?: return@items
                        val isSelected = selected.contains(file.path)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.95f)
                                .combinedClickable(
                                    onClick = {
                                        if (selected.isNotEmpty()) {
                                            viewModel.toggleFileSelection(file.path)
                                        } else if (file.isDirectory) {
                                            viewModel.loadExplorerFiles(file.path)
                                        }
                                    },
                                    onLongClick = {
                                        viewModel.toggleFileSelection(file.path)
                                    }
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) SaffronPrimary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, SaffronPrimary) else null
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.Info,
                                        contentDescription = if (file.isDirectory) "Folder" else "File",
                                        tint = if (file.isDirectory) SaffronSecondary else Color.Gray,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = file.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                    if (!file.isDirectory) {
                                        Text(
                                            text = formatFileSize(file.size),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                                Box(modifier = Modifier.align(Alignment.TopEnd)) {
                                    IconButton(
                                        onClick = { activeFileItemForMenu = file },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "File Options", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } }

    // CREATE FOLDER DIALOG
    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("Create Folder") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SaffronPrimary, focusedLabelColor = SaffronPrimary)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newFolderName.isNotEmpty()) {
                            viewModel.createFolder(newFolderName)
                            newFolderName = ""
                            showCreateFolderDialog = false
                        }
                    }
                ) {
                    Text("Create", color = SaffronPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // FILE MORE OPTIONS BOTTOM DIALOG
    if (activeFileItemForMenu != null) {
        val file = activeFileItemForMenu!!
        Dialog(onDismissRequest = { activeFileItemForMenu = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    HorizontalDivider()

                    // Rename option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                renameFileName = file.name
                                showRenameDialog = true
                                activeFileItemForMenu = null
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Rename", tint = SaffronPrimary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Rename")
                    }

                    // Share option
                    if (!file.isDirectory) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    try {
                                        val f = File(file.path)
                                        val authority = "${context.packageName}.fileprovider"
                                        val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, f)
                                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = context.contentResolver.getType(uri) ?: "*/*"
                                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(android.content.Intent.createChooser(intent, "Share File"))
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                    activeFileItemForMenu = null
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = SaffronPrimary)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Share File")
                        }
                    }

                    // Details option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showDetailsDialog = file
                                activeFileItemForMenu = null
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = "Details", tint = SaffronPrimary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Details")
                    }

                    // Vault option
                    if (!file.isDirectory) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.moveFileToVault(file.path)
                                    activeFileItemForMenu = null
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = "Encrypt", tint = SaffronPrimary)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Encrypt & Move to Vault")
                        }
                    }

                    // ZIP option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (file.name.endsWith(".zip")) {
                                    viewModel.extractZip(file.path)
                                } else {
                                    viewModel.compressToZip(file.path)
                                }
                                activeFileItemForMenu = null
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = "Zip", tint = SaffronPrimary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(if (file.name.endsWith(".zip")) "Extract ZIP Contents" else "Compress to ZIP")
                    }

                    // AI summarize option
                    if (!file.isDirectory && (file.mimeType.contains("text") || file.name.endsWith(".txt") || file.name.endsWith(".pdf"))) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.requestAIEvaluation(file)
                                    activeFileItemForMenu = null
                                    viewModel.navigateTo(Screen.Search) // Jump to see results
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "AI", tint = SaffronPrimary)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("AI Summarize File")
                        }
                    }

                    // Delete option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.deleteSingleFile(file.path)
                                activeFileItemForMenu = null
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Delete", color = Color.Red)
                    }
                }
            }
        }
    }

    // FILE DETAILS DIALOG
    if (showDetailsDialog != null) {
        val dFile = showDetailsDialog!!
        AlertDialog(
            onDismissRequest = { showDetailsDialog = null },
            title = { Text("File Details", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Name: ${dFile.name}", fontWeight = FontWeight.SemiBold)
                    Text("Path: ${dFile.path}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Text("Size: ${formatFileSize(dFile.size)}")
                    Text("Type: ${if (dFile.isDirectory) "Folder" else "File"}")
                    Text("MimeType: ${dFile.mimeType}")
                    Text("Last Modified: ${formatTimestamp(dFile.lastModified)}")
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetailsDialog = null }) {
                    Text("Close", color = SaffronPrimary)
                }
            }
        )
    }

    // RENAME FILE DIALOG
    if (showRenameDialog && activeFileItemForMenu != null) {
        val file = activeFileItemForMenu!!
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename File/Folder") },
            text = {
                OutlinedTextField(
                    value = renameFileName,
                    onValueChange = { renameFileName = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SaffronPrimary, focusedLabelColor = SaffronPrimary)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameFileName.isNotEmpty()) {
                            viewModel.renameFile(file.path, renameFileName)
                            showRenameDialog = false
                            activeFileItemForMenu = null
                        }
                    }
                ) {
                    Text("Rename", color = SaffronPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// --- 3. AI SEMANTIC SEARCH SCREEN ---
@Composable
fun SearchScreen(viewModel: MainViewModel) {
    val query by viewModel.searchQuery.collectAsState()
    val results by viewModel.searchResults.collectAsState()
    val history by viewModel.searchHistory.collectAsState()
    val isSearching by viewModel.aiSearching.collectAsState()
    val aiExplanation by viewModel.aiExplanation.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "AI Semantic Search",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = SaffronPrimary
        )
        Text(
            text = "Search by file meaning instead of literal names",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Natural Language Search Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text("e.g. hemp policy documents, mountain trip photos") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("ai_search_input"),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SaffronPrimary, focusedLabelColor = SaffronPrimary)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { viewModel.performSemanticSearch(query) },
                colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary),
                modifier = Modifier.testTag("search_button")
            ) {
                Icon(Icons.Default.Search, contentDescription = "Search")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // History Chips Row
        if (history.isNotEmpty() && query.isEmpty()) {
            Text(
                text = "Recent Searches",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                history.take(3).forEach { hist ->
                    Box(
                        modifier = Modifier
                            .background(SaffronPrimary.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                            .clickable {
                                viewModel.updateSearchQuery(hist.query)
                                viewModel.performSemanticSearch(hist.query)
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(hist.query, style = MaterialTheme.typography.bodySmall, color = SaffronPrimary)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Search Results List
        if (isSearching) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = SaffronPrimary)
            }
        } else if (aiExplanation != null) {
            // AI Explanation Panel
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "AI File Summary",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = SaffronPrimary
                        )
                        IconButton(onClick = { viewModel.clearAIExplanation() }) {
                            Icon(Icons.Default.Close, contentDescription = "Close Panel")
                        }
                    }
                    Divider()
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        item {
                            Text(
                                text = aiExplanation!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        } else if (results.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Search, contentDescription = "No Results", tint = Color.Gray, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No matching files found.", color = Color.Gray)
                    Text("Try: 'policy', 'mountain', or 'quantum'", color = SaffronPrimary, style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(results) { file ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (file.name.contains(".pdf") || file.name.contains(".txt")) {
                                    viewModel.requestAIEvaluation(file)
                                }
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = "Match",
                                tint = SaffronPrimary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    file.name,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    file.path.removePrefix(viewModel.fileRepository.sandboxRoot.absolutePath),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (file.name.contains("Match:")) {
                                // Match badge
                                Box(
                                    modifier = Modifier
                                        .background(SaffronPrimary.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("AI Match", style = MaterialTheme.typography.labelSmall, color = SaffronPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- 4. DUPLICATES CLEANER SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicatesScreen(viewModel: MainViewModel) {
    val duplicates by viewModel.duplicates.collectAsState()
    val semanticDuplicates by viewModel.semanticDuplicates.collectAsState()
    val isScanning by viewModel.scanningDuplicates.collectAsState()
    val isScanningSemantic by viewModel.scanningSemanticDuplicates.collectAsState()
    
    var selectedTab by remember { mutableStateOf(0) } // 0 = Exact, 1 = Semantic

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Duplicate Cleaner",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = SaffronPrimary
                )
                Text(
                    text = "Find and remove redundant copies easily",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            IconButton(onClick = { viewModel.scanForDuplicates() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Rescan", tint = SaffronPrimary)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Tab selection
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = SaffronPrimary
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Exact (${duplicates.values.flatten().size})", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Semantic (AI)", fontWeight = FontWeight.Bold) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isScanning || isScanningSemantic) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = SaffronPrimary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (isScanning) "Scanning byte-identical duplicates..." else "Comparing file embeddings...",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        } else if (selectedTab == 0) {
            // Exact duplicates list
            if (duplicates.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Check, contentDescription = "No Duplicates", tint = Color.Green, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Clean storage! No exact duplicate files found.", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    duplicates.forEach { (hash, groupFiles) ->
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Duplicate Group (MD5: ${hash.take(8)})",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = SaffronPrimary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    groupFiles.forEach { file ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    file.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    file.path.removePrefix(viewModel.fileRepository.sandboxRoot.absolutePath),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.Gray,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    formatFileSize(file.size),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.Gray
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                IconButton(onClick = { viewModel.deleteDuplicate(file.path) }) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Delete Copy", tint = Color.Red)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Semantic duplicates list
            val isApiKeyAvailable = com.example.util.GeminiService.isApiKeyAvailable()
            if (!isApiKeyAvailable) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = "Gemini Key Required", tint = SaffronPrimary, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Gemini AI Key Required",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Semantic scanning compares file embeddings to identify visually or semantically similar documents, recompressed images, or drafting revisions. Please provide a Gemini API Key in Settings to unlock this feature.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.navigateTo(Screen.Settings) },
                                colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary)
                            ) {
                                Text("Go to Settings")
                            }
                        }
                    }
                }
            } else if (semanticDuplicates.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Check, contentDescription = "No Duplicates", tint = Color.Green, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No near-duplicate or semantically similar files found.", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    semanticDuplicates.forEach { (mainPath, groupFiles) ->
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "AI Near-Duplicate Group",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = SaffronPrimary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    groupFiles.forEach { file ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    file.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    file.path.removePrefix(viewModel.fileRepository.sandboxRoot.absolutePath),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.Gray,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    formatFileSize(file.size),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.Gray
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                IconButton(onClick = { viewModel.deleteDuplicate(file.path) }) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Delete Copy", tint = Color.Red)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- 5. JUNK CLEANER SCREEN ---
@Composable
fun JunkCleanerScreen(viewModel: MainViewModel) {
    val junkFiles by viewModel.junkFiles.collectAsState()
    val isScanning by viewModel.scanningJunk.collectAsState()

    val totalLogs = junkFiles["logs"]?.sumOf { it.size } ?: 0L
    val totalTemp = junkFiles["temp"]?.sumOf { it.size } ?: 0L
    val totalResidual = junkFiles["residual"]?.sumOf { it.size } ?: 0L
    val totalEmptyCount = junkFiles["empty"]?.size ?: 0
    val totalJunkSize = totalLogs + totalTemp + totalResidual

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Junk Cleaner",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = SaffronPrimary
                )
                Text(
                    text = "Reclaim valuable storage safely",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            IconButton(onClick = { viewModel.scanForJunk() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Scan", tint = SaffronPrimary)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (isScanning) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SaffronPrimary)
            }
        } else if (totalJunkSize == 0L && totalEmptyCount == 0) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Check, contentDescription = "No Junk", tint = Color.Green, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Your storage is completely optimized!", fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Recoverable space overview
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = formatFileSize(totalJunkSize),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = SaffronPrimary
                            )
                        )
                        Text(
                            text = "Total Recoverable Space",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Safe to Delete", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = Color.Green)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(formatFileSize(totalLogs + totalTemp), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                    Text("$totalEmptyCount empty dirs", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                }
                            }
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Review First", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = SaffronPrimary)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(formatFileSize(totalResidual), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                    Text("${junkFiles["residual"]?.size ?: 0} residual files", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Breakdown",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("System Log Files", fontWeight = FontWeight.Bold)
                                    Text("Logs generated by background tasks", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Card(colors = CardDefaults.cardColors(containerColor = Color.Green.copy(alpha = 0.15f))) {
                                        Text("Safe to Delete", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Color.Green)
                                    }
                                }
                                Text(formatFileSize(totalLogs), color = SaffronPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Temporary Cache Files", fontWeight = FontWeight.Bold)
                                    Text("Extracted caches and scratch folders", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Card(colors = CardDefaults.cardColors(containerColor = Color.Green.copy(alpha = 0.15f))) {
                                        Text("Safe to Delete", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Color.Green)
                                    }
                                }
                                Text(formatFileSize(totalTemp), color = SaffronPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Empty Folders", fontWeight = FontWeight.Bold)
                                    Text("Zero files recursive folders", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Card(colors = CardDefaults.cardColors(containerColor = Color.Green.copy(alpha = 0.15f))) {
                                        Text("Safe to Delete", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Color.Green)
                                    }
                                }
                                Text("$totalEmptyCount folders", color = Color.Gray, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Residual Orphaned Files", fontWeight = FontWeight.Bold)
                                    Text("Physical files not indexed in database", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Card(colors = CardDefaults.cardColors(containerColor = SaffronPrimary.copy(alpha = 0.15f))) {
                                        Text("Review First", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = SaffronPrimary)
                                    }
                                }
                                Text(formatFileSize(totalResidual), color = SaffronPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { viewModel.cleanJunk() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("clean_now_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("CLEAN NOW", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

// --- 6. SECURE VAULT SCREEN ---
@Composable
fun VaultScreen(viewModel: MainViewModel) {
    val isAuthenticated by viewModel.isVaultAuthenticated.collectAsState()
    val vaultFiles by viewModel.vaultFiles.collectAsState()
    val errorMsg by viewModel.vaultError.collectAsState()

    var pinInput by remember { mutableStateOf("") }
    val isPinSet = viewModel.isVaultPinSet()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Secure Vault",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = SaffronPrimary
        )
        Text(
            text = "AES-256 encrypted Keystore safe folder",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (!isAuthenticated) {
            // Unauthenticated Lock Screen
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_vault_lock),
                    contentDescription = "Safe Vault Lock Illustration",
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (isPinSet) "Enter Vault PIN" else "Configure New Vault PIN",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = if (isPinSet) "Enter 4-digit PIN to access safe folder" else "Choose a 4-digit numeric code to protect your files",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { if (it.length <= 4) pinInput = it },
                    label = { Text("PIN") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SaffronPrimary, focusedLabelColor = SaffronPrimary),
                    modifier = Modifier.width(160.dp).testTag("vault_pin_input")
                )

                if (errorMsg != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(errorMsg!!, color = Color.Red, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (isPinSet) {
                            val success = viewModel.authenticateVault(pinInput)
                            if (success) pinInput = ""
                        } else {
                            if (pinInput.length == 4) {
                                viewModel.setVaultPin(pinInput)
                                pinInput = ""
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary),
                    modifier = Modifier.testTag("vault_submit_button")
                ) {
                    Text(if (isPinSet) "Unlock" else "Set PIN & Unlock")
                }
            }
        } else {
            // Authenticated Vault File Explorer Screen
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Your Encrypted Files", fontWeight = FontWeight.Bold)
                IconButton(onClick = { viewModel.lockVault() }) {
                    Icon(Icons.Default.Lock, contentDescription = "Lock Vault", tint = SaffronPrimary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (vaultFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Lock, contentDescription = "No Files", tint = Color.Gray, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Vault is empty.", color = Color.Gray)
                        Text("Encrypt files from the standard File Explorer.", style = MaterialTheme.typography.bodySmall, color = SaffronPrimary)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(vaultFiles) { file ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = "Encrypted", tint = SaffronPrimary)
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(
                                            file.name,
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "Encrypted File • ${formatFileSize(file.size)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                    }
                                }
                                Button(
                                    onClick = { viewModel.restoreFileFromVault(file.path) },
                                    colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary)
                                ) {
                                    Text("Decrypt")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- 7. MEDIA CENTER SCREEN ---
fun formatDuration(ms: Long): String {
    val sec = (ms / 1000) % 60
    val min = (ms / (1000 * 60)) % 60
    val hr = (ms / (1000 * 60 * 60))
    return if (hr > 0) {
        String.format("%d:%02d:%02d", hr, min, sec)
    } else {
        String.format("%d:%02d", min, sec)
    }
}

@Composable
fun MediaCenterScreen(viewModel: MainViewModel) {
    val images by viewModel.mediaImages.collectAsState()
    val videos by viewModel.mediaVideos.collectAsState()
    val audio by viewModel.mediaAudio.collectAsState()

    val pagedImages = viewModel.pagedMediaImages.collectAsLazyPagingItems()
    val pagedVideos = viewModel.pagedMediaVideos.collectAsLazyPagingItems()
    val pagedAudio = viewModel.pagedMediaAudio.collectAsLazyPagingItems()

    var selectedTab by remember { mutableStateOf(0) }
    var activeSlideshowIndex by remember { mutableStateOf<Int?>(null) }
    var isSlideshowPlaying by remember { mutableStateOf(true) }

    // Slideshow Auto-advance logic
    LaunchedEffect(activeSlideshowIndex, isSlideshowPlaying) {
        if (activeSlideshowIndex != null && isSlideshowPlaying) {
            kotlinx.coroutines.delay(3000)
            val currentIdx = activeSlideshowIndex!!
            val totalCount = pagedImages.itemCount
            if (totalCount > 0) {
                activeSlideshowIndex = (currentIdx + 1) % totalCount
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab, contentColor = SaffronPrimary) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                Box(modifier = Modifier.padding(16.dp)) { Text("Images (${images.size})") }
            }
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                Box(modifier = Modifier.padding(16.dp)) { Text("Videos (${videos.size})") }
            }
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }) {
                Box(modifier = Modifier.padding(16.dp)) { Text("Audio (${audio.size})") }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (selectedTab) {
            0 -> {
                // Images Grid
                if (pagedImages.itemCount == 0) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No Images Found.", color = Color.Gray)
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Slideshow Row Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = {
                                    activeSlideshowIndex = 0
                                    isSlideshowPlaying = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Start Slideshow")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Start Slideshow")
                            }
                        }

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(pagedImages.itemCount) { index ->
                                val img = pagedImages[index] ?: return@items
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .background(SaffronSecondary.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                        .clickable {
                                            activeSlideshowIndex = index
                                            isSlideshowPlaying = true
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(4.dp)) {
                                        Icon(Icons.Default.Info, contentDescription = "Image", tint = SaffronPrimary)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            img.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (img.width != null && img.height != null) {
                                            Text(
                                                "${img.width}x${img.height}",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                color = Color.Gray,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            1 -> {
                // Videos List
                if (pagedVideos.itemCount == 0) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No Videos Found.", color = Color.Gray)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        items(pagedVideos.itemCount) { index ->
                            val vid = pagedVideos[index] ?: return@items
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = SaffronPrimary)
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(vid.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        val durationStr = vid.duration?.let { formatDuration(it) } ?: "Video"
                                        val resStr = vid.resolution?.let { " • $it" } ?: ""
                                        Text("${formatFileSize(vid.size)} • $durationStr$resStr", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            2 -> {
                // Audio List
                if (pagedAudio.itemCount == 0) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No Audio Found.", color = Color.Gray)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        items(pagedAudio.itemCount) { index ->
                            val aud = pagedAudio[index] ?: return@items
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play Audio", tint = SaffronPrimary)
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(aud.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        val artistStr = aud.artist?.let { " • $it" } ?: ""
                                        val albumStr = aud.album?.let { " ($it)" } ?: ""
                                        val durationStr = aud.duration?.let { " • ${formatDuration(it)}" } ?: " • Audio"
                                        Text("${formatFileSize(aud.size)}$durationStr$artistStr$albumStr", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Slideshow Fullscreen Dialog
    if (activeSlideshowIndex != null) {
        val totalCount = pagedImages.itemCount
        val currentIndex = activeSlideshowIndex!!
        val currentImage = if (currentIndex in 0 until totalCount) pagedImages[currentIndex] else null

        Dialog(
            onDismissRequest = { activeSlideshowIndex = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black.copy(alpha = 0.95f)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header with name & progress
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentImage?.name ?: "Unknown Image",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (currentImage?.width != null && currentImage.height != null) {
                                Text(
                                    text = "${currentImage.width}x${currentImage.height} • ${formatFileSize(currentImage.size)}",
                                    color = Color.LightGray,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        IconButton(onClick = { activeSlideshowIndex = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close Slideshow", tint = Color.White)
                        }
                    }

                    // Image Display / Placeholder
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color.DarkGray, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Image,
                                contentDescription = "Slideshow Image",
                                tint = SaffronPrimary,
                                modifier = Modifier.size(128.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Slideshow Active",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }

                    // Progress indicator text
                    Text(
                        text = "${currentIndex + 1} of $totalCount",
                        color = Color.White,
                        modifier = Modifier.padding(vertical = 16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    // Controls Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                val prevIdx = if (totalCount > 0) (currentIndex - 1 + totalCount) % totalCount else null
                                activeSlideshowIndex = prevIdx
                            },
                            enabled = totalCount > 1
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Previous Image", tint = Color.White)
                        }

                        IconButton(
                            onClick = { isSlideshowPlaying = !isSlideshowPlaying }
                        ) {
                            Icon(
                                imageVector = if (isSlideshowPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isSlideshowPlaying) "Pause Slideshow" else "Play Slideshow",
                                tint = SaffronPrimary,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                val nextIdx = if (totalCount > 0) (currentIndex + 1) % totalCount else null
                                activeSlideshowIndex = nextIdx
                            },
                            enabled = totalCount > 1
                        ) {
                            Icon(Icons.Default.ArrowForward, contentDescription = "Next Image", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

// --- 8. SETTINGS SCREEN ---
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val isKeyAvailable = GeminiService.isApiKeyAvailable()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Settings & Configuration",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = SaffronPrimary
        )

        Divider()

        // Gemini AI status configuration
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Gemini AI Engine Status",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(if (isKeyAvailable) Color.Green else Color.Red, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isKeyAvailable) "Active and Connected" else "Inactive / API Key Absent",
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isKeyAvailable) "Your app is fully configured to fetch actual vector embeddings from Google servers for next-gen semantic searching!"
                    else "Enter your GEMINI_API_KEY in the AI Studio Secrets panel. The app will gracefully fall back to its smart local keyword semantic-ranker in the meantime.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        // Storage Scan Actions
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Maintenance Controls", fontWeight = FontWeight.Bold)
                
                Button(
                    onClick = { viewModel.triggerBackgroundScanning() },
                    colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Rebuild Smart Semantic Indexes")
                }

                OutlinedButton(
                    onClick = { viewModel.clearSearchHistory() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SaffronPrimary)
                ) {
                    Text("Clear AI Search History")
                }
            }
        }
    }
}

// --- 9. GOOGLE DRIVE SIM / CLOUD MANAGER ---
@Composable
fun CloudManagerScreen(viewModel: MainViewModel) {
    val cloudAccounts by viewModel.cloudAccounts.collectAsState()
    val selectedAccount by viewModel.selectedCloudAccount.collectAsState()
    val cloudFiles = viewModel.filteredCloudFiles()
    val selectedFiles by viewModel.selectedCloudFiles.collectAsState()
    val cloudSearchQuery by viewModel.cloudSearchQuery.collectAsState()

    // Semantic scan state
    val scanProgress by viewModel.semanticScanProgress.collectAsState()
    val scanStatus by viewModel.semanticScanStatus.collectAsState()
    val scanMatchPercent by viewModel.semanticScanMatchPercent.collectAsState()
    val isScanningSemantic by viewModel.isScanningSemantic.collectAsState()

    var showAddAccountDialog by remember { mutableStateOf(false) }
    var newAccountEmail by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Title
        Text(
            text = "Google Drive Sim",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = SaffronPrimary
        )
        Text(
            text = "Simulated next-gen neural cloud manager",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Multi-account Switcher Panel
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Connected Accounts", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                    IconButton(onClick = { showAddAccountDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Account", tint = SaffronPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (cloudAccounts.isEmpty()) {
                    Text("No connected cloud drives. Add one below.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        cloudAccounts.forEach { account ->
                            val isSelected = account == selectedAccount
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (isSelected) SaffronPrimary else Color.Transparent,
                                        RoundedCornerShape(16.dp)
                                    )
                                    .background(Color.Transparent)
                                    .clickable { viewModel.selectCloudAccount(account) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = account,
                                        color = if (isSelected) Color.Black else Color.White,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    if (isSelected) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            Icons.Default.Logout,
                                            contentDescription = "Logout",
                                            tint = Color.Black,
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clickable { viewModel.logoutCloudAccount(account) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Semantic Scan Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "AI", tint = SaffronPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AI Semantic Scan Status",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = SaffronPrimary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (isScanningSemantic) {
                    Column {
                        Text(
                            text = scanStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = SaffronPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = scanProgress,
                            modifier = Modifier.fillMaxWidth(),
                            color = SaffronPrimary,
                            trackColor = SaffronSecondary.copy(alpha = 0.2f)
                        )
                    }
                } else {
                    Column {
                        if (scanMatchPercent > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Similarity Match: $scanMatchPercent%",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color.Green
                                    )
                                    Text(
                                        text = "Cloud matches with local indexes verified.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .background(Color.Green.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                        .padding(8.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Verified", tint = Color.Green)
                                }
                            }
                        } else {
                            Text(
                                text = "Run a cross-node scan to compare your local directory indexing vectors with the cloud node's schema.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { viewModel.startSemanticScan() },
                            colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Run AI Semantic Scan", fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Search Bar & Multi-Select Header
        OutlinedTextField(
            value = cloudSearchQuery,
            onValueChange = { viewModel.updateCloudSearchQuery(it) },
            label = { Text("Search cloud files...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SaffronPrimary, focusedLabelColor = SaffronPrimary),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${cloudFiles.size} Cloud Files",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
            )

            Row {
                if (selectedFiles.isNotEmpty()) {
                    IconButton(onClick = { viewModel.deleteSelectedCloudFiles() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Selected", tint = Color.Red)
                    }
                }
                TextButton(onClick = { viewModel.selectAllCloudFiles() }) {
                    Text("Select All", color = SaffronPrimary)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Cloud File List
        if (selectedAccount == null) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Select or Add an Account above.", color = Color.Gray)
            }
        } else if (cloudFiles.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No files in this cloud directory.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(cloudFiles) { file ->
                    val isSelected = selectedFiles.contains(file.id)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleCloudFileSelection(file.id) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) SaffronSecondary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { viewModel.toggleCloudFileSelection(file.id) },
                                colors = CheckboxDefaults.colors(checkedColor = SaffronPrimary)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Icon(
                                imageVector = when (file.category) {
                                    "Images" -> Icons.Default.Image
                                    "Videos" -> Icons.Default.PlayArrow
                                    "Audio" -> Icons.Default.MusicNote
                                    else -> Icons.Default.InsertDriveFile
                                },
                                contentDescription = file.category,
                                tint = SaffronPrimary,
                                modifier = Modifier.size(28.dp)
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${formatFileSize(file.size)} • ${formatTimestamp(file.lastModified)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }

                            IconButton(onClick = { viewModel.deleteSingleCloudFile(file.id) }) {
                                Icon(Icons.Default.Close, contentDescription = "Delete file", tint = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Account Dialog
    if (showAddAccountDialog) {
        AlertDialog(
            onDismissRequest = { showAddAccountDialog = false },
            title = { Text("Connect New Cloud Drive", color = SaffronPrimary) },
            text = {
                Column {
                    Text("Enter the email address of the drive you wish to connect:", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newAccountEmail,
                        onValueChange = { newAccountEmail = it },
                        label = { Text("Account Email") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SaffronPrimary, focusedLabelColor = SaffronPrimary)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newAccountEmail.trim().isNotEmpty()) {
                            viewModel.addCloudAccount(newAccountEmail.trim())
                            newAccountEmail = ""
                            showAddAccountDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary)
                ) {
                    Text("Connect", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddAccountDialog = false }) {
                    Text("Cancel", color = SaffronPrimary)
                }
            }
        )
    }
}

// --- 10. AI ASSISTANT & CONFIG SCREEN ---
@Composable
fun AIAssistantScreen(viewModel: MainViewModel) {
    val chatMessages by viewModel.chatMessages.collectAsState()
    val isHighThinkingEnabled by viewModel.isHighThinkingEnabled.collectAsState()
    val apiKeyInput by viewModel.apiKeyInput.collectAsState()
    val isSendingMessage by viewModel.isSendingMessage.collectAsState()

    var chatTextInput by remember { mutableStateOf("") }
    var isSetupPanelExpanded by remember { mutableStateOf(false) }
    var localApiKeyInput by remember { mutableStateOf(apiKeyInput) }

    // Sync local input with view model state when it changes
    LaunchedEffect(apiKeyInput) {
        localApiKeyInput = apiKeyInput
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "VVF AI Assistant",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = SaffronPrimary
                )
                Text(
                    text = "Powered by Google Gemini Models",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            IconButton(onClick = { viewModel.clearAllChatHistory() }) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "Clear History", tint = Color.Red)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Collapsible Setup Panel
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isSetupPanelExpanded = !isSetupPanelExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, contentDescription = "Setup", tint = SaffronPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Gemini Setup Panel",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Icon(
                        imageVector = if (isSetupPanelExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand/Collapse"
                    )
                }

                if (isSetupPanelExpanded) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(12.dp))

                    // API Key input
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Gemini API Key override (re-saves dynamically):",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.LightGray
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = localApiKeyInput,
                                onValueChange = { localApiKeyInput = it },
                                placeholder = { Text("AI Studio key...") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SaffronPrimary),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = { viewModel.applyApiKeyOverride(localApiKeyInput) },
                                colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary)
                            ) {
                                Text("Apply", color = Color.Black)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // High thinking mode toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("High-thinking mode (Pro model)", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            Text("Uses gemini-3.1-pro-preview with high thinking enabled for advanced reasoning.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        Switch(
                            checked = isHighThinkingEnabled,
                            onCheckedChange = { viewModel.toggleHighThinking(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = SaffronPrimary, checkedTrackColor = SaffronSecondary)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Chat messages bubble list
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                .padding(8.dp)
        ) {
            if (chatMessages.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Chat, contentDescription = "Chat", tint = Color.Gray, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Chat with VVF Smart File Assistant!", fontWeight = FontWeight.Bold)
                    Text("Ask to explain files, security metrics, or operations.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(chatMessages) { msg ->
                        val isUser = msg.role == "user"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 12.dp,
                                            topEnd = 12.dp,
                                            bottomStart = if (isUser) 12.dp else 0.dp,
                                            bottomEnd = if (isUser) 0.dp else 12.dp
                                        )
                                    )
                                    .background(
                                        if (isUser) SaffronPrimary else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .padding(12.dp)
                                    .widthIn(max = 280.dp)
                            ) {
                                Text(
                                    text = msg.content,
                                    color = if (isUser) Color.Black else Color.White,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Input row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = chatTextInput,
                onValueChange = { chatTextInput = it },
                placeholder = { Text("Ask anything...") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SaffronPrimary),
                modifier = Modifier.weight(1f),
                enabled = !isSendingMessage
            )

            IconButton(
                onClick = {
                    if (chatTextInput.trim().isNotEmpty()) {
                        viewModel.sendMessageToAssistant(chatTextInput.trim())
                        chatTextInput = ""
                    }
                },
                enabled = !isSendingMessage && chatTextInput.trim().isNotEmpty(),
                modifier = Modifier
                    .background(
                        if (chatTextInput.trim().isEmpty() || isSendingMessage) Color.Gray else SaffronPrimary,
                        CircleShape
                    )
            ) {
                if (isSendingMessage) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.Black)
                }
            }
        }
    }
}

