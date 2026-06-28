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
                    selected = currentScreen is Screen.Dashboard,
                    onClick = { viewModel.navigateTo(Screen.Dashboard) },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Dashboard") },
                    label = { Text("Dashboard") }
                )
                NavigationBarItem(
                    selected = currentScreen is Screen.Explorer,
                    onClick = { viewModel.navigateTo(Screen.Explorer) },
                    icon = { Icon(Icons.Default.Folder, contentDescription = "Explorer") },
                    label = { Text("Explorer") }
                )
                NavigationBarItem(
                    selected = currentScreen is Screen.Search,
                    onClick = { viewModel.navigateTo(Screen.Search) },
                    icon = { Icon(Icons.Default.Search, contentDescription = "AI Search") },
                    label = { Text("AI Search") }
                )
                NavigationBarItem(
                    selected = currentScreen is Screen.Vault,
                    onClick = { viewModel.navigateTo(Screen.Vault) },
                    icon = { Icon(Icons.Default.Lock, contentDescription = "Vault") },
                    label = { Text("Vault") }
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
    val currentPath by viewModel.currentPath.collectAsState()
    val files by viewModel.explorerFiles.collectAsState()
    val selected by viewModel.selectedFiles.collectAsState()
    val clipboard by viewModel.clipboard.collectAsState()

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    var activeFileItemForMenu by remember { mutableStateOf<FileItem?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameFileName by remember { mutableStateOf("") }

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
                onClick = { viewModel.navigateUp() },
                enabled = !viewModel.isAtSandboxRoot()
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }

            Text(
                text = if (currentPath.isEmpty()) "VVF_Smart_Explorer" else File(currentPath).name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            IconButton(onClick = { showCreateFolderDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Create Folder", tint = SaffronPrimary)
            }

            if (selected.isNotEmpty()) {
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

        // Files & Folders List
        if (files.isEmpty()) {
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
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(files) { file ->
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
        }
    }

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
                    Divider()

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
@Composable
fun DuplicatesScreen(viewModel: MainViewModel) {
    val duplicates by viewModel.duplicates.collectAsState()
    val isScanning by viewModel.scanningDuplicates.collectAsState()

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

        Spacer(modifier = Modifier.height(16.dp))

        if (isScanning) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SaffronPrimary)
            }
        } else if (duplicates.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Check, contentDescription = "No Duplicates", tint = Color.Green, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Clean storage! No duplicate files found.", fontWeight = FontWeight.Bold)
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
    }
}

// --- 5. JUNK CLEANER SCREEN ---
@Composable
fun JunkCleanerScreen(viewModel: MainViewModel) {
    val junkFiles by viewModel.junkFiles.collectAsState()
    val isScanning by viewModel.scanningJunk.collectAsState()

    val totalLogs = junkFiles["logs"]?.sumOf { it.size } ?: 0L
    val totalTemp = junkFiles["temp"]?.sumOf { it.size } ?: 0L
    val totalJunkSize = totalLogs + totalTemp

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
        } else if (totalJunkSize == 0L && (junkFiles["empty"]?.isEmpty() != false)) {
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
                // Large Saffron circle showing space recoverable
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .background(SaffronPrimary.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = formatFileSize(totalJunkSize),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = SaffronPrimary
                            )
                        )
                        Text(
                            text = "Safe to Delete",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Breakdown list
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("System Log Files", fontWeight = FontWeight.SemiBold)
                            Text(formatFileSize(totalLogs), color = SaffronPrimary, fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Temporary Cache Files", fontWeight = FontWeight.SemiBold)
                            Text(formatFileSize(totalTemp), color = SaffronPrimary, fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Empty Folders Detected", fontWeight = FontWeight.SemiBold)
                            Text("${junkFiles["empty"]?.size ?: 0} folders", color = Color.Gray)
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

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
@Composable
fun MediaCenterScreen(viewModel: MainViewModel) {
    val images by viewModel.mediaImages.collectAsState()
    val videos by viewModel.mediaVideos.collectAsState()
    val audio by viewModel.mediaAudio.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }

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
                if (images.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No Images Found.", color = Color.Gray)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(images) { img ->
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .background(SaffronSecondary.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
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
                                }
                            }
                        }
                    }
                }
            }
            1 -> {
                // Videos List
                if (videos.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No Videos Found.", color = Color.Gray)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        items(videos) { vid ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = SaffronPrimary)
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(vid.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${formatFileSize(vid.size)} • Video", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            2 -> {
                // Audio List
                if (audio.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No Audio Found.", color = Color.Gray)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        items(audio) { aud ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play Audio", tint = SaffronPrimary)
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(aud.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${formatFileSize(aud.size)} • MP3 Audio", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
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
