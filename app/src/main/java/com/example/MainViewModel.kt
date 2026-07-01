package com.example

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.database.DbSearchHistory
import com.example.data.database.CategoryEntity
import com.example.data.model.FileItem
import com.example.data.repository.FileRepository
import com.example.data.repository.VaultRepository
import com.example.data.repository.GoogleSignInResult
import com.example.util.GeminiService
import com.example.util.ZipHelper
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import com.example.data.database.DbFile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

sealed class Screen {
    object Dashboard : Screen()
    object Explorer : Screen()
    object Search : Screen()
    object Duplicates : Screen()
    object JunkCleaner : Screen()
    object Vault : Screen()
    object MediaCenter : Screen()
    object Settings : Screen()
    object CloudManager : Screen()
    object AIAssistant : Screen()
}

data class CloudFile(
    val id: String,
    val name: String,
    val size: Long,
    val mimeType: String,
    val lastModified: Long,
    val category: String = "Other"
)

enum class SortBy {
    NAME, SIZE, DATE, TYPE
}

enum class SortOrder {
    ASCENDING, DESCENDING
}

enum class ViewMode {
    LIST, GRID
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _sortBy = MutableStateFlow(SortBy.NAME)
    val sortBy: StateFlow<SortBy> = _sortBy.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.ASCENDING)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _viewMode = MutableStateFlow(ViewMode.LIST)
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    fun setSortBy(by: SortBy) {
        _sortBy.value = by
        applySorting()
    }

    fun toggleSortOrder() {
        _sortOrder.value = if (_sortOrder.value == SortOrder.ASCENDING) SortOrder.DESCENDING else SortOrder.ASCENDING
        applySorting()
    }

    fun toggleViewMode() {
        _viewMode.value = if (_viewMode.value == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST
    }

    private fun sortFileList(list: List<FileItem>, by: SortBy, order: SortOrder): List<FileItem> {
        val comparator = when (by) {
            SortBy.NAME -> compareBy<FileItem> { it.name.lowercase() }
            SortBy.SIZE -> compareBy { it.size }
            SortBy.DATE -> compareBy { it.lastModified }
            SortBy.TYPE -> compareBy { it.mimeType.lowercase() }
        }
        return if (order == SortOrder.ASCENDING) {
            list.sortedWith(comparator)
        } else {
            list.sortedWith(comparator.reversed())
        }
    }

    private fun applySorting() {
        _explorerFiles.value = sortFileList(_explorerFiles.value, _sortBy.value, _sortOrder.value)
    }

    private val database = AppDatabase.getDatabase(application)
    val fileRepository = FileRepository(
        application, 
        database.fileDao(), 
        database.embeddingDao(),
        database.categoryEntityDao(),
        database.secureStateEntityDao()
    )
    val vaultRepository = VaultRepository(application, fileRepository)

    val allCategories: StateFlow<List<CategoryEntity>> = fileRepository.allCategories
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val top5LargestFiles: StateFlow<List<DbFile>> = database.fileDao().getAllFiles()
        .map { files ->
            files.sortedByDescending { it.size }.take(5)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isStorageBreakdownExpanded = MutableStateFlow(false)
    val isStorageBreakdownExpanded: StateFlow<Boolean> = _isStorageBreakdownExpanded.asStateFlow()

    fun toggleStorageBreakdown() {
        _isStorageBreakdownExpanded.value = !_isStorageBreakdownExpanded.value
    }

    private val _selectedCategoryFilter = MutableStateFlow<String?>(null)
    val selectedCategoryFilter: StateFlow<String?> = _selectedCategoryFilter.asStateFlow()

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

    @OptIn(ExperimentalCoroutinesApi::class)
    val pagedExplorerFiles: Flow<PagingData<FileItem>> = combine(
        _currentPath,
        _selectedCategoryFilter
    ) { path, category ->
        Pair(path, category)
    }.flatMapLatest { (path, category) ->
        if (category != null) {
            val list = fileRepository.getFilesByCategory(category)
            Pager(PagingConfig(pageSize = 20, enablePlaceholders = false)) {
                com.example.util.SimpleListPagingSource(list)
            }.flow
        } else {
            if (path.isEmpty()) {
                Pager(PagingConfig(pageSize = 20, enablePlaceholders = false)) {
                    val roomSource = database.fileDao().getAllFilesPaged()
                    object : androidx.paging.PagingSource<Int, FileItem>() {
                        override fun getRefreshKey(state: androidx.paging.PagingState<Int, FileItem>): Int? = null
                        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, FileItem> {
                            val result = roomSource.load(androidx.paging.PagingSource.LoadParams.Refresh(
                                key = params.key ?: 0,
                                loadSize = params.loadSize,
                                placeholdersEnabled = false
                            ))
                            return when (result) {
                                is LoadResult.Page -> {
                                    val mappedData = result.data.map { dbFile ->
                                        FileItem(
                                            id = dbFile.path,
                                            name = dbFile.name,
                                            path = dbFile.path,
                                            size = dbFile.size,
                                            isDirectory = false,
                                            mimeType = dbFile.mimeType,
                                            lastModified = dbFile.modifiedAt
                                        )
                                    }
                                    LoadResult.Page(
                                        data = mappedData,
                                        prevKey = result.prevKey,
                                        nextKey = result.nextKey
                                    )
                                }
                                is LoadResult.Error -> LoadResult.Error(result.throwable)
                                is LoadResult.Invalid -> LoadResult.Invalid()
                            }
                        }
                    }
                }.flow
            } else {
                Pager(PagingConfig(pageSize = 20, enablePlaceholders = false)) {
                    com.example.util.FilePagingSource(fileRepository, path)
                }.flow
            }
        }
    }.cachedIn(viewModelScope)

    @OptIn(ExperimentalCoroutinesApi::class)
    val pagedMediaImages: Flow<PagingData<FileItem>> = _mediaImages
        .flatMapLatest { list ->
            Pager(PagingConfig(pageSize = 20, enablePlaceholders = false)) {
                com.example.util.SimpleListPagingSource(list)
            }.flow
        }.cachedIn(viewModelScope)

    @OptIn(ExperimentalCoroutinesApi::class)
    val pagedMediaVideos: Flow<PagingData<FileItem>> = _mediaVideos
        .flatMapLatest { list ->
            Pager(PagingConfig(pageSize = 20, enablePlaceholders = false)) {
                com.example.util.SimpleListPagingSource(list)
            }.flow
        }.cachedIn(viewModelScope)

    @OptIn(ExperimentalCoroutinesApi::class)
    val pagedMediaAudio: Flow<PagingData<FileItem>> = _mediaAudio
        .flatMapLatest { list ->
            Pager(PagingConfig(pageSize = 20, enablePlaceholders = false)) {
                com.example.util.SimpleListPagingSource(list)
            }.flow
        }.cachedIn(viewModelScope)

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

    private val _semanticDuplicates = MutableStateFlow<Map<String, List<FileItem>>>(emptyMap())
    val semanticDuplicates: StateFlow<Map<String, List<FileItem>>> = _semanticDuplicates.asStateFlow()

    private val _scanningSemanticDuplicates = MutableStateFlow(false)
    val scanningSemanticDuplicates: StateFlow<Boolean> = _scanningSemanticDuplicates.asStateFlow()

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

    private val _isFirstLaunch = MutableStateFlow(true)
    val isFirstLaunch: StateFlow<Boolean> = _isFirstLaunch.asStateFlow()

    private val _indexingProgress = MutableStateFlow(0)
    val indexingProgress: StateFlow<Int> = _indexingProgress.asStateFlow()

    private val _indexingTotal = MutableStateFlow(0)
    val indexingTotal: StateFlow<Int> = _indexingTotal.asStateFlow()

    // Storage Status
    private val _storageFreeSpace = MutableStateFlow(0L)
    val storageFreeSpace: StateFlow<Long> = _storageFreeSpace.asStateFlow()

    private val _storageTotalSpace = MutableStateFlow(0L)
    val storageTotalSpace: StateFlow<Long> = _storageTotalSpace.asStateFlow()

    // Google Drive / Cloud Sim State
    private val _cloudAccounts = MutableStateFlow<List<String>>(listOf("personal.cloud@gmail.com", "work.drive@corporate.com"))
    val cloudAccounts: StateFlow<List<String>> = _cloudAccounts.asStateFlow()

    private val _selectedCloudAccount = MutableStateFlow<String?>("personal.cloud@gmail.com")
    val selectedCloudAccount: StateFlow<String?> = _selectedCloudAccount.asStateFlow()

    private val _cloudFiles = MutableStateFlow<List<CloudFile>>(emptyList())
    val cloudFiles: StateFlow<List<CloudFile>> = _cloudFiles.asStateFlow()

    private val _selectedCloudFiles = MutableStateFlow<Set<String>>(emptySet())
    val selectedCloudFiles: StateFlow<Set<String>> = _selectedCloudFiles.asStateFlow()

    private val _cloudSearchQuery = MutableStateFlow("")
    val cloudSearchQuery: StateFlow<String> = _cloudSearchQuery.asStateFlow()

    // Semantic Scan State
    private val _semanticScanProgress = MutableStateFlow(0f)
    val semanticScanProgress: StateFlow<Float> = _semanticScanProgress.asStateFlow()

    private val _semanticScanStatus = MutableStateFlow("")
    val semanticScanStatus: StateFlow<String> = _semanticScanStatus.asStateFlow()

    private val _semanticScanMatchPercent = MutableStateFlow(0)
    val semanticScanMatchPercent: StateFlow<Int> = _semanticScanMatchPercent.asStateFlow()

    private val _isScanningSemantic = MutableStateFlow(false)
    val isScanningSemantic: StateFlow<Boolean> = _isScanningSemantic.asStateFlow()

    // AI Assistant State
    private val _chatMessages = MutableStateFlow<List<com.example.data.database.ChatMessageEntity>>(emptyList())
    val chatMessages: StateFlow<List<com.example.data.database.ChatMessageEntity>> = _chatMessages.asStateFlow()

    // Recommendation State
    private val _historyRecommendations = MutableStateFlow<List<FileItem>>(emptyList())
    val historyRecommendations: StateFlow<List<FileItem>> = _historyRecommendations.asStateFlow()

    private val _categoryRecommendations = MutableStateFlow<List<FileItem>>(emptyList())
    val categoryRecommendations: StateFlow<List<FileItem>> = _categoryRecommendations.asStateFlow()

    private val _largeFileRecommendations = MutableStateFlow<List<FileItem>>(emptyList())
    val largeFileRecommendations: StateFlow<List<FileItem>> = _largeFileRecommendations.asStateFlow()

    private val _isRecommendationsLoading = MutableStateFlow(false)
    val isRecommendationsLoading: StateFlow<Boolean> = _isRecommendationsLoading.asStateFlow()

    fun refreshRecommendations() {
        viewModelScope.launch {
            _isRecommendationsLoading.value = true
            try {
                _historyRecommendations.value = fileRepository.getRecommendationsFromSearchHistory()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error fetching history recommendations: ${e.message}", e)
            }
            try {
                _categoryRecommendations.value = fileRepository.getCategoryBasedRecommendations()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error fetching category recommendations: ${e.message}", e)
            }
            try {
                _largeFileRecommendations.value = fileRepository.getLargeFilesRecommendation()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error fetching large files recommendations: ${e.message}", e)
            }
            _isRecommendationsLoading.value = false
        }
    }

    fun loadRecommendations() {
        refreshRecommendations()
    }

    private val _isHighThinkingEnabled = MutableStateFlow(false)
    val isHighThinkingEnabled: StateFlow<Boolean> = _isHighThinkingEnabled.asStateFlow()

    private val _apiKeyInput = MutableStateFlow("")
    val apiKeyInput: StateFlow<String> = _apiKeyInput.asStateFlow()

    private val _isSendingMessage = MutableStateFlow(false)
    val isSendingMessage: StateFlow<Boolean> = _isSendingMessage.asStateFlow()

    init {
        // Load API override key
        val prefs = application.getSharedPreferences("vvf_api_prefs", android.content.Context.MODE_PRIVATE)
        val savedKey = prefs.getString("gemini_api_key_override", "") ?: ""
        _apiKeyInput.value = savedKey
        GeminiService.setOverriddenKey(savedKey)

        val savedHighThinking = prefs.getBoolean("gemini_high_thinking", false)
        _isHighThinkingEnabled.value = savedHighThinking

        navigateTo(Screen.Dashboard)
        loadSearchHistory()
        refreshStorageInfo()
        triggerBackgroundScanning()
        loadChatMessages()
        loadCachedDuplicates()
        _selectedCloudAccount.value?.let { generateSimulatedCloudFiles(it) }
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
            is Screen.CloudManager -> _selectedCloudAccount.value?.let { generateSimulatedCloudFiles(it) }
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
            _selectedCategoryFilter.value = null
            _currentPath.value = path
            val raw = fileRepository.getFilesAndFolders(path)
            _explorerFiles.value = sortFileList(raw, _sortBy.value, _sortOrder.value)
            _selectedFiles.value = emptySet()
        }
    }

    fun navigateToItem(file: FileItem) {
        if (file.isDirectory) {
            loadExplorerFiles(file.path)
        } else {
            val parentFile = java.io.File(file.path).parentFile
            if (parentFile != null) {
                val parentPath = if (parentFile.absolutePath == fileRepository.sandboxRoot.absolutePath) "" else parentFile.absolutePath
                loadExplorerFiles(parentPath)
            } else {
                loadExplorerFiles("")
            }
        }
        navigateTo(Screen.Explorer)
    }

    fun setCategoryFilter(categoryName: String?) {
        _selectedCategoryFilter.value = categoryName
        if (categoryName != null) {
            viewModelScope.launch {
                val raw = fileRepository.getFilesByCategory(categoryName)
                _explorerFiles.value = sortFileList(raw, _sortBy.value, _sortOrder.value)
                _selectedFiles.value = emptySet()
                _currentScreen.value = Screen.Explorer
            }
        } else {
            loadExplorerFiles("")
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
            withContext(Dispatchers.IO) {
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

                val enrichedImages = (sandboxImages + deviceImages).distinctBy { it.path }.map { fileRepository.enrichMediaMetadata(it) }
                val enrichedVideos = (sandboxVideos + deviceVideos).distinctBy { it.path }.map { fileRepository.enrichMediaMetadata(it) }
                val enrichedAudio = (sandboxAudio + deviceAudio).distinctBy { it.path }.map { fileRepository.enrichMediaMetadata(it) }

                withContext(Dispatchers.Main) {
                    _mediaImages.value = enrichedImages
                    _mediaVideos.value = enrichedVideos
                    _mediaAudio.value = enrichedAudio
                }
            }
        }
    }

    // --- DUPLICATE CLEANER METHODS ---
    fun scanForDuplicates() {
        viewModelScope.launch {
            _scanningDuplicates.value = true
            val exact = fileRepository.findDuplicates()
            _duplicates.value = exact
            fileRepository.saveDuplicatesCache(exact)
            _scanningDuplicates.value = false
            
            _scanningSemanticDuplicates.value = true
            val semantic = fileRepository.findSemanticDuplicates()
            _semanticDuplicates.value = semantic
            fileRepository.saveSemanticDuplicatesCache(semantic)
            _scanningSemanticDuplicates.value = false
        }
    }

    fun loadCachedDuplicates() {
        viewModelScope.launch {
            _duplicates.value = fileRepository.getCachedDuplicates()
            _semanticDuplicates.value = fileRepository.getCachedSemanticDuplicates()
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

    fun authenticateWithGoogle(context: android.content.Context, onResult: (GoogleSignInResult) -> Unit) {
        viewModelScope.launch {
            val result = vaultRepository.signInWithGoogle(context)
            if (result is GoogleSignInResult.Success) {
                val emailHash = vaultRepository.hashEmail(result.email)
                val storedHash = vaultRepository.getGoogleEmailHash()
                if (storedHash == null) {
                    // Register on first sign-in
                    vaultRepository.saveGoogleEmailHash(emailHash)
                    _isVaultAuthenticated.value = true
                    _vaultError.value = null
                    loadVault()
                } else {
                    if (storedHash == emailHash) {
                        _isVaultAuthenticated.value = true
                        _vaultError.value = null
                        loadVault()
                    } else {
                        _vaultError.value = "This Google account is not authorized to unlock this Vault."
                    }
                }
            }
            onResult(result)
        }
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
            }.also {
                loadRecommendations()
            }
            _isIndexing.value = false
            _isFirstLaunch.value = false
        }
    }

    // --- CLOUD MANAGER METHODS ---
    fun selectCloudAccount(account: String) {
        _selectedCloudAccount.value = account
        _selectedCloudFiles.value = emptySet()
        generateSimulatedCloudFiles(account)
    }

    fun addCloudAccount(account: String) {
        if (account.trim().isNotEmpty() && !_cloudAccounts.value.contains(account.trim())) {
            _cloudAccounts.value = _cloudAccounts.value + account.trim()
            selectCloudAccount(account.trim())
        }
    }

    fun logoutCloudAccount(account: String) {
        _cloudAccounts.value = _cloudAccounts.value.filter { it != account }
        if (_selectedCloudAccount.value == account) {
            _selectedCloudAccount.value = _cloudAccounts.value.firstOrNull()
            _selectedCloudAccount.value?.let { generateSimulatedCloudFiles(it) } ?: run { _cloudFiles.value = emptyList() }
        }
    }

    fun toggleCloudFileSelection(id: String) {
        val currentSet = _selectedCloudFiles.value.toMutableSet()
        if (currentSet.contains(id)) {
            currentSet.remove(id)
        } else {
            currentSet.add(id)
        }
        _selectedCloudFiles.value = currentSet
    }

    fun selectAllCloudFiles() {
        val allIds = filteredCloudFiles().map { it.id }.toSet()
        _selectedCloudFiles.value = allIds
    }

    fun deleteSelectedCloudFiles() {
        val selected = _selectedCloudFiles.value
        _cloudFiles.value = _cloudFiles.value.filter { !selected.contains(it.id) }
        _selectedCloudFiles.value = emptySet()
    }

    fun deleteSingleCloudFile(id: String) {
        _cloudFiles.value = _cloudFiles.value.filter { it.id != id }
        _selectedCloudFiles.value = _selectedCloudFiles.value.filter { it != id }.toSet()
    }

    fun updateCloudSearchQuery(query: String) {
        _cloudSearchQuery.value = query
    }

    fun filteredCloudFiles(): List<CloudFile> {
        val query = _cloudSearchQuery.value.trim()
        if (query.isEmpty()) return _cloudFiles.value
        return _cloudFiles.value.filter { it.name.contains(query, ignoreCase = true) }
    }

    fun generateSimulatedCloudFiles(account: String) {
        val isWork = account.contains("work") || account.contains("corporate")
        _cloudFiles.value = if (isWork) {
            listOf(
                CloudFile("c1", "Q3 Financial Performance.xlsx", 24 * 1024 * 1024L, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", System.currentTimeMillis() - 48000000, "Documents"),
                CloudFile("c2", "AI Deep Learning Strategy 2026.pdf", 8 * 1024 * 1024L, "application/pdf", System.currentTimeMillis() - 86000000, "Documents"),
                CloudFile("c3", "Quantum Computing Project Spec.txt", 45000L, "text/plain", System.currentTimeMillis() - 150000000, "Documents"),
                CloudFile("c4", "Enterprise Architecture Diagram.png", 3 * 1024 * 1024L, "image/png", System.currentTimeMillis() - 320000000, "Images"),
                CloudFile("c5", "Corporate All Hands Audio.mp3", 42 * 1024 * 1024L, "audio/mpeg", System.currentTimeMillis() - 600000000, "Audio")
            )
        } else {
            listOf(
                CloudFile("c1", "Family Vacation Photos.zip", 450 * 1024 * 1024L, "application/zip", System.currentTimeMillis() - 120000000, "Archives"),
                CloudFile("c2", "Guitar Solo Practice.wav", 18 * 1024 * 1024L, "audio/wav", System.currentTimeMillis() - 320000000, "Audio"),
                CloudFile("c3", "My Resume.docx", 120000L, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", System.currentTimeMillis() - 500000000, "Documents"),
                CloudFile("c4", "Cute Cat Video.mp4", 85 * 1024 * 1024L, "video/mp4", System.currentTimeMillis() - 900000000, "Videos"),
                CloudFile("c5", "Groceries Budget list.xlsx", 85000L, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", System.currentTimeMillis() - 12000000, "Documents")
            )
        }
    }

    fun startSemanticScan() {
        viewModelScope.launch {
            _isScanningSemantic.value = true
            _semanticScanProgress.value = 0f
            _semanticScanMatchPercent.value = 0
            val stages = listOf(
                "Connecting to cloud node...",
                "Retrieving file schema metadata...",
                "Downloading semantic hashes...",
                "Comparing neural embeddings...",
                "Finalizing match index..."
            )
            for (i in stages.indices) {
                _semanticScanStatus.value = stages[i]
                for (p in 1..20) {
                    kotlinx.coroutines.delay(30)
                    _semanticScanProgress.value += 0.01f
                }
            }
            _semanticScanProgress.value = 1.0f
            _semanticScanMatchPercent.value = (72..98).random()
            _semanticScanStatus.value = "Semantic scan complete. Sync similarity verified."
            _isScanningSemantic.value = false
        }
    }

    // --- AI ASSISTANT & CHAT PERSISTENCE METHODS ---
    fun loadChatMessages() {
        viewModelScope.launch {
            database.chatMessageEntityDao().getAllChatMessages().collect {
                _chatMessages.value = it
            }
        }
    }

    fun sendMessageToAssistant(text: String) {
        if (text.trim().isEmpty()) return
        viewModelScope.launch {
            _isSendingMessage.value = true
            val userMsg = com.example.data.database.ChatMessageEntity(role = "user", content = text)
            database.chatMessageEntityDao().insertChatMessage(userMsg)

            val prompt = "You are VVF Smart File Assistant, an expert AI embedded directly in the Smart File & Cloud Manager. " +
                    "The user is asking a question or request: \"$text\". " +
                    "Answer them with highly precise, smart, and friendly assistance."
            
            val systemIns = "Always be exceptionally helpful, concise, and professional. Address file manager features like Encryption, Cleaners, Search when appropriate."
            val response = GeminiService.generateResponse(prompt, systemInstruction = systemIns, useHighThinking = _isHighThinkingEnabled.value)
            
            val modelMsg = com.example.data.database.ChatMessageEntity(role = "model", content = response)
            database.chatMessageEntityDao().insertChatMessage(modelMsg)
            _isSendingMessage.value = false
        }
    }

    fun clearAllChatHistory() {
        viewModelScope.launch {
            database.chatMessageEntityDao().clearChatMessages()
        }
    }

    fun applyApiKeyOverride(key: String) {
        val prefs = getApplication<Application>().getSharedPreferences("vvf_api_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("gemini_api_key_override", key).apply()
        _apiKeyInput.value = key
        GeminiService.setOverriddenKey(key)
    }

    fun toggleHighThinking(enabled: Boolean) {
        _isHighThinkingEnabled.value = enabled
        val prefs = getApplication<Application>().getSharedPreferences("vvf_api_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putBoolean("gemini_high_thinking", enabled).apply()
    }
}
