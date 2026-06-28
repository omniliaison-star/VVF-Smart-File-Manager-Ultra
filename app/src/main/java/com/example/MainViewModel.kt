package com.example

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.database.DbSearchHistory
import com.example.data.model.FileItem
import com.example.data.repository.FileRepository
import com.example.data.repository.VaultRepository
import com.example.util.GeminiService
import com.example.util.ZipHelper
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

sealed class Screen {
    object Dashboard : Screen()
    object Explorer : Screen()
    object Search : Screen()
    object Duplicates : Screen()
    object JunkCleaner : Screen()
    object Vault : Screen()
    object MediaCenter : Screen()
    object Settings : Screen()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    val fileRepository = FileRepository(application, database.fileDao(), database.embeddingDao())
    val vaultRepository = VaultRepository(application, fileRepository)

    // UI Screen navigation State
    private val _currentScreen = MutableStateFlow<Screen>(Screen.Dashboard)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    // Explorer State
    private val _currentPath = MutableStateFlow("") // Empty string = Sandbox root
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    private val _explorerFiles = MutableStateFlow<List<FileItem>>(emptyList())
    val explorerFiles: StateFlow<List<FileItem>> = _explorerFiles.asStateFlow()

    private val _selectedFiles = MutableStateFlow<Set<String>>(emptySet())
    val selectedFiles: StateFlow<Set<String>> = _selectedFiles.asStateFlow()

    // Clipboard for copy/move operations
    private val _clipboard = MutableStateFlow<Pair<String, String>?>(null) // Pair(filePath, "COPY"/"MOVE")
    val clipboard: StateFlow<Pair<String, String>?> = _clipboard.asStateFlow()

    // Media Center State
    private val _mediaImages = MutableStateFlow<List<FileItem>>(emptyList())
    val mediaImages: StateFlow<List<FileItem>> = _mediaImages.asStateFlow()

    private val _mediaVideos = MutableStateFlow<List<FileItem>>(emptyList())
    val mediaVideos: StateFlow<List<FileItem>> = _mediaVideos.asStateFlow()

    private val _mediaAudio = MutableStateFlow<List<FileItem>>(emptyList())
    val mediaAudio: StateFlow<List<FileItem>> = _mediaAudio.asStateFlow()

    // AI Semantic Search State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<FileItem>>(emptyList())
    val searchResults: StateFlow<List<FileItem>> = _searchResults.asStateFlow()

    private val _searchHistory = MutableStateFlow<List<DbSearchHistory>>(emptyList())
    val searchHistory: StateFlow<List<DbSearchHistory>> = _searchHistory.asStateFlow()

    private val _aiSearching = MutableStateFlow(false)
    val aiSearching: StateFlow<Boolean> = _aiSearching.asStateFlow()

    private val _aiExplanation = MutableStateFlow<String?>(null)
    val aiExplanation: StateFlow<String?> = _aiExplanation.asStateFlow()

    // Duplicates Cleaner State
    private val _duplicates = MutableStateFlow<Map<String, List<FileItem>>>(emptyMap())
    val duplicates: StateFlow<Map<String, List<FileItem>>> = _duplicates.asStateFlow()

    private val _scanningDuplicates = MutableStateFlow(false)
    val scanningDuplicates: StateFlow<Boolean> = _scanningDuplicates.asStateFlow()

    // Junk Cleaner State
    private val _junkFiles = MutableStateFlow<Map<String, List<FileItem>>>(emptyMap())
    val junkFiles: StateFlow<Map<String, List<FileItem>>> = _junkFiles.asStateFlow()

    private val _scanningJunk = MutableStateFlow(false)
    val scanningJunk: StateFlow<Boolean> = _scanningJunk.asStateFlow()

    // Secure Vault State
    private val _vaultFiles = MutableStateFlow<List<FileItem>>(emptyList())
    val vaultFiles: StateFlow<List<FileItem>> = _vaultFiles.asStateFlow()

    private val _isVaultAuthenticated = MutableStateFlow(false)
    val isVaultAuthenticated: StateFlow<Boolean> = _isVaultAuthenticated.asStateFlow()

    private val _vaultError = MutableStateFlow<String?>(null)
    val vaultError: StateFlow<String?> = _vaultError.asStateFlow()

    // Indexing Worker state
    private val _isIndexing = MutableStateFlow(false)
    val isIndexing: StateFlow<Boolean> = _isIndexing.asStateFlow()

    private val _indexingProgress = MutableStateFlow(0)
    val indexingProgress: StateFlow<Int> = _indexingProgress.asStateFlow()

    private val _indexingTotal = MutableStateFlow(0)
    val indexingTotal: StateFlow<Int> = _indexingTotal.asStateFlow()

    // Storage Status
    private val _storageFreeSpace = MutableStateFlow(0L)
    val storageFreeSpace: StateFlow<Long> = _storageFreeSpace.asStateFlow()

    private val _storageTotalSpace = MutableStateFlow(0L)
    val storageTotalSpace: StateFlow<Long> = _storageTotalSpace.asStateFlow()

    init {
        navigateTo(Screen.Dashboard)
        loadSearchHistory()
        refreshStorageInfo()
        triggerBackgroundScanning()
    }

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
        _selectedFiles.value = emptySet()
        when (screen) {
            is Screen.Explorer -> loadExplorerFiles(_currentPath.value)
            is Screen.MediaCenter -> loadMediaCenter()
            is Screen.Vault -> loadVault()
            is Screen.Duplicates -> scanForDuplicates()
            is Screen.JunkCleaner -> scanForJunk()
            else -> {}
        }
        refreshStorageInfo()
    }

    // Storage information
    fun refreshStorageInfo() {
        viewModelScope.launch {
            val file = getApplication<Application>().filesDir
            _storageFreeSpace.value = file.freeSpace
            _storageTotalSpace.value = file.totalSpace
        }
    }

    // --- EXPLORER METHODS ---
    fun loadExplorerFiles(path: String) {
        viewModelScope.launch {
            _currentPath.value = path
            _explorerFiles.value = fileRepository.getFilesAndFolders(path)
            _selectedFiles.value = emptySet()
        }
    }

    fun navigateUp() {
        val path = _currentPath.value
        if (path.isEmpty() || path == fileRepository.sandboxRoot.absolutePath) return
        val parent = File(path).parent ?: return
        loadExplorerFiles(parent)
    }

    fun isAtSandboxRoot(): Boolean {
        return _currentPath.value.isEmpty() || _currentPath.value == fileRepository.sandboxRoot.absolutePath
    }

    fun toggleFileSelection(path: String) {
        val currentSet = _selectedFiles.value.toMutableSet()
        if (currentSet.contains(path)) {
            currentSet.remove(path)
        } else {
            currentSet.add(path)
        }
        _selectedFiles.value = currentSet
    }

    fun selectAllFiles() {
        val allPaths = _explorerFiles.value.map { it.path }.toSet()
        _selectedFiles.value = allPaths
    }

    fun clearSelection() {
        _selectedFiles.value = emptySet()
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            val success = fileRepository.createFolder(_currentPath.value, name)
            if (success) {
                loadExplorerFiles(_currentPath.value)
                triggerBackgroundScanning()
            }
        }
    }

    fun renameFile(path: String, newName: String) {
        viewModelScope.launch {
            val success = fileRepository.renameFile(path, newName)
            if (success) {
                loadExplorerFiles(_currentPath.value)
                triggerBackgroundScanning()
            }
        }
    }

    fun deleteSelectedFiles() {
        viewModelScope.launch {
            _selectedFiles.value.forEach { path ->
                fileRepository.deleteFile(path)
            }
            loadExplorerFiles(_currentPath.value)
            _selectedFiles.value = emptySet()
            triggerBackgroundScanning()
        }
    }

    fun deleteSingleFile(path: String) {
        viewModelScope.launch {
            fileRepository.deleteFile(path)
            loadExplorerFiles(_currentPath.value)
            triggerBackgroundScanning()
        }
    }

    fun setClipboard(mode: String) {
        val selected = _selectedFiles.value.firstOrNull() ?: return
        _clipboard.value = Pair(selected, mode)
        _selectedFiles.value = emptySet()
    }

    fun pasteClipboard() {
        val clip = _clipboard.value ?: return
        val destDir = if (_currentPath.value.isEmpty()) fileRepository.sandboxRoot.absolutePath else _currentPath.value
        viewModelScope.launch {
            val success = if (clip.second == "COPY") {
                fileRepository.copyFile(clip.first, destDir)
            } else {
                fileRepository.moveFile(clip.first, destDir)
            }
            if (success) {
                _clipboard.value = null
                loadExplorerFiles(_currentPath.value)
                triggerBackgroundScanning()
            }
        }
    }

    // Archive methods
    fun compressToZip(path: String) {
        viewModelScope.launch {
            val sourceFile = File(path)
            val zipFile = File(sourceFile.parent, sourceFile.nameWithoutExtension + ".zip")
            try {
                ZipHelper.compressToZip(sourceFile, zipFile)
                loadExplorerFiles(_currentPath.value)
                triggerBackgroundScanning()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Compression failed: ${e.message}")
            }
        }
    }

    fun extractZip(path: String) {
        viewModelScope.launch {
            val zipFile = File(path)
            val destDir = File(zipFile.parent, zipFile.nameWithoutExtension + "_extracted")
            try {
                ZipHelper.extractZip(zipFile, destDir)
                loadExplorerFiles(_currentPath.value)
                triggerBackgroundScanning()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Extraction failed: ${e.message}")
            }
        }
    }

    // --- MEDIA CENTER METHODS ---
    fun loadMediaCenter() {
        viewModelScope.launch {
            // Read standard sandbox files first to have offline fallback media
            val sandboxImages = mutableListOf<FileItem>()
            val sandboxVideos = mutableListOf<FileItem>()
            val sandboxAudio = mutableListOf<FileItem>()

            fun traverse(dir: File) {
                val list = dir.listFiles() ?: return
                for (f in list) {
                    if (f.isDirectory) {
                        traverse(f)
                    } else {
                        val mime = fileRepository.getMimeType(f)
                        val item = FileItem(f.absolutePath, f.name, f.absolutePath, f.length(), false, mime, f.lastModified())
                        if (mime.startsWith("image/")) sandboxImages.add(item)
                        else if (mime.startsWith("video/")) sandboxVideos.add(item)
                        else if (mime.startsWith("audio/")) sandboxAudio.add(item)
                    }
                }
            }
            traverse(fileRepository.sandboxRoot)

            // Try to merge with device media
            val deviceImages = try { fileRepository.getMediaStoreImages() } catch (e: Exception) { emptyList() }
            val deviceVideos = try { fileRepository.getMediaStoreVideos() } catch (e: Exception) { emptyList() }
            val deviceAudio = try { fileRepository.getMediaStoreAudio() } catch (e: Exception) { emptyList() }

            _mediaImages.value = (sandboxImages + deviceImages).distinctBy { it.path }
            _mediaVideos.value = (sandboxVideos + deviceVideos).distinctBy { it.path }
            _mediaAudio.value = (sandboxAudio + deviceAudio).distinctBy { it.path }
        }
    }

    // --- DUPLICATE CLEANER METHODS ---
    fun scanForDuplicates() {
        viewModelScope.launch {
            _scanningDuplicates.value = true
            _duplicates.value = fileRepository.findDuplicates()
            _scanningDuplicates.value = false
        }
    }

    fun deleteDuplicate(path: String) {
        viewModelScope.launch {
            val success = fileRepository.deleteFile(path)
            if (success) {
                scanForDuplicates()
                triggerBackgroundScanning()
            }
        }
    }

    // --- JUNK CLEANER METHODS ---
    fun scanForJunk() {
        viewModelScope.launch {
            _scanningJunk.value = true
            _junkFiles.value = fileRepository.scanJunk()
            _scanningJunk.value = false
        }
    }

    fun cleanJunk() {
        viewModelScope.launch {
            _scanningJunk.value = true
            _junkFiles.value.values.flatten().forEach { item ->
                fileRepository.deleteFile(item.path)
            }
            _junkFiles.value = emptyMap()
            _scanningJunk.value = false
            triggerBackgroundScanning()
        }
    }

    // --- SECURE VAULT METHODS ---
    fun isVaultPinSet(): Boolean {
        return vaultRepository.isPinSet()
    }

    fun setVaultPin(pin: String): Boolean {
        val success = vaultRepository.setPin(pin)
        if (success) {
            _isVaultAuthenticated.value = true
            loadVault()
        }
        return success
    }

    fun authenticateVault(pin: String): Boolean {
        val success = vaultRepository.verifyPin(pin)
        _isVaultAuthenticated.value = success
        if (success) {
            _vaultError.value = null
            loadVault()
        } else {
            _vaultError.value = "Incorrect PIN. Please try again."
        }
        return success
    }

    fun lockVault() {
        _isVaultAuthenticated.value = false
    }

    fun loadVault() {
        viewModelScope.launch {
            if (_isVaultAuthenticated.value) {
                _vaultFiles.value = vaultRepository.getVaultFiles()
            }
        }
    }

    fun moveFileToVault(path: String) {
        viewModelScope.launch {
            val success = vaultRepository.encryptToVault(path)
            if (success) {
                loadExplorerFiles(_currentPath.value)
                loadVault()
                triggerBackgroundScanning()
            }
        }
    }

    fun restoreFileFromVault(vaultPath: String) {
        viewModelScope.launch {
            val success = vaultRepository.decryptFromVault(vaultPath)
            if (success) {
                loadVault()
                loadExplorerFiles(_currentPath.value)
                triggerBackgroundScanning()
            }
        }
    }

    // --- AI SEMANTIC SEARCH METHODS ---
    private fun loadSearchHistory() {
        viewModelScope.launch {
            database.searchHistoryDao().getSearchHistory()
                .catch { Log.e("MainViewModel", "Error loading history: ${it.message}") }
                .collect {
                    _searchHistory.value = it
                }
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            database.searchHistoryDao().clearHistory()
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.isEmpty()) {
            _searchResults.value = emptyList()
        }
    }

    fun performSemanticSearch(query: String) {
        if (query.trim().isEmpty()) return
        viewModelScope.launch {
            _aiSearching.value = true
            _searchResults.value = emptyList()
            
            // Save search history
            database.searchHistoryDao().insertHistory(DbSearchHistory(query = query))

            // Run search
            _searchResults.value = fileRepository.semanticSearch(query)
            _aiSearching.value = false
        }
    }

    fun requestAIEvaluation(file: FileItem) {
        viewModelScope.launch {
            _aiExplanation.value = "Analyzing content using Gemini..."
            val realFile = File(file.path)
            if (!realFile.exists()) {
                _aiExplanation.value = "File does not exist."
                return@launch
            }
            
            val content = try {
                realFile.readText().take(4000)
            } catch (e: Exception) {
                "Unable to read file content text."
            }

            val prompt = "You are VVF Smart File Assistant. Please summarize or explain the following file content for the user. " +
                    "Keep it concise, smart, and highly helpful.\n\n" +
                    "Filename: ${file.name}\n" +
                    "MimeType: ${file.mimeType}\n" +
                    "Content snippet:\n$content"

            val response = GeminiService.generateResponse(prompt)
            _aiExplanation.value = response
        }
    }

    fun clearAIExplanation() {
        _aiExplanation.value = null
    }

    // --- BACKGROUND INDEXER WORKER SIMULATOR ---
    fun triggerBackgroundScanning() {
        viewModelScope.launch {
            _isIndexing.value = true
            fileRepository.indexAllFiles { progress, total ->
                _indexingProgress.value = progress
                _indexingTotal.value = total
            }
            _isIndexing.value = false
        }
    }
}
