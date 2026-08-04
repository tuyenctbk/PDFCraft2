package com.example.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.PdfDatabase
import com.example.data.local.PdfOperationType
import com.example.data.local.PdfProjectEntity
import com.example.data.local.PdfRepository
import com.example.data.local.RecentFileEntity
import com.example.data.pdf.CompressionPreset
import com.example.data.pdf.PdfEngine
import com.example.data.pdf.PdfMetadata
import com.example.data.pdf.PdfPageSourceItem
import com.example.data.remote.FirebaseConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

import com.example.ui.theme.ThemeMode

enum class SplitMode {
    COMBINE_SELECTED,
    SEPARATE_FILES
}

data class PdfDocumentInfo(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val sizeFormatted: String,
    val sizeBytes: Long,
    val pageCount: Int,
    val uri: Uri
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = PdfDatabase.getInstance(application)
    private val repository = PdfRepository(db.pdfProjectDao(), db.recentFileDao())
    private val prefs = application.getSharedPreferences("pdfcraft_prefs", Context.MODE_PRIVATE)

    // Theme Mode State
    private val _themeMode = MutableStateFlow(
        run {
            val modeStr = prefs.getString("pref_theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
            try { ThemeMode.valueOf(modeStr) } catch (_: Exception) { ThemeMode.SYSTEM }
        }
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("pref_theme_mode", mode.name).apply()
        _themeMode.value = mode
    }

    // Session Counter & Samples Auto-Disappear State
    private val _sessionCount = MutableStateFlow(0)
    val sessionCount: StateFlow<Int> = _sessionCount.asStateFlow()

    private val _areSamplesVisible = MutableStateFlow(true)
    val areSamplesVisible: StateFlow<Boolean> = _areSamplesVisible.asStateFlow()

    // Onboarding State
    private val _isOnboardingCompleted = MutableStateFlow(
        prefs.getBoolean("pref_onboarding_completed", false)
    )
    val isOnboardingCompleted: StateFlow<Boolean> = _isOnboardingCompleted.asStateFlow()

    fun completeOnboarding() {
        prefs.edit().putBoolean("pref_onboarding_completed", true).apply()
        _isOnboardingCompleted.value = true
    }

    fun resetOnboardingForTesting() {
        prefs.edit().putBoolean("pref_onboarding_completed", false).apply()
        _isOnboardingCompleted.value = false
    }

    // Polite Ad Dialog state after PDF actions
    private val _showPoliteAdDialog = MutableStateFlow(false)
    val showPoliteAdDialog: StateFlow<Boolean> = _showPoliteAdDialog.asStateFlow()

    fun dismissPoliteAdDialog() {
        _showPoliteAdDialog.value = false
    }

    // Sample PDFs State
    private val _samplePdfs = MutableStateFlow<List<File>>(emptyList())
    val samplePdfs: StateFlow<List<File>> = _samplePdfs.asStateFlow()

    init {
        android.util.Log.d("PDFCraft_Logcat", "MainViewModel init block starting")

        // Increment session count on launch
        val newCount = prefs.getInt("pref_session_count", 0) + 1
        prefs.edit().putInt("pref_session_count", newCount).apply()
        _sessionCount.value = newCount

        try {
            FirebaseConfigManager.initialize(application)
        } catch (t: Throwable) {
            android.util.Log.e("PDFCraft_Logcat", "FirebaseConfigManager init error: ${t.localizedMessage}", t)
        }

        checkAndLoadSamples(newCount)
        android.util.Log.d("PDFCraft_Logcat", "MainViewModel init complete")
    }

    private fun checkAndLoadSamples(currentSessionCount: Int) {
        _areSamplesVisible.value = false
        _samplePdfs.value = emptyList()
    }

    fun toggleHideSamples(hide: Boolean) {
        // Sample documents permanently removed
        _areSamplesVisible.value = false
        _samplePdfs.value = emptyList()
    }

    fun restoreSamples() {
        // Sample documents permanently removed
        _areSamplesVisible.value = false
        _samplePdfs.value = emptyList()
    }

    val historyState: StateFlow<List<PdfProjectEntity>> = repository.allProjects
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val favoritesState: StateFlow<List<PdfProjectEntity>> = repository.favoriteProjects
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val recentFilesState: StateFlow<List<RecentFileEntity>> = repository.recentFiles
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun recordRecentFileAccess(file: File, pageCount: Int = 0) {
        if (!file.exists()) return
        viewModelScope.launch {
            repository.recordRecentFileAccess(
                filePath = file.absolutePath,
                fileName = file.name,
                fileSizeFormatted = PdfEngine.formatFileSize(file.length()),
                pageCount = pageCount
            )
        }
    }

    fun removeRecentFile(filePath: String) {
        viewModelScope.launch {
            repository.removeRecentFile(filePath)
            _userMessage.value = "Removed file from Recent list"
        }
    }

    fun clearRecentFiles() {
        viewModelScope.launch {
            repository.clearRecentFiles()
            _userMessage.value = "Recent Files cleared"
        }
    }

    // Navigation Tab
    private val _currentTab = MutableStateFlow(0)
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()

    private val _activeTool = MutableStateFlow<PdfOperationType?>(null)
    val activeTool: StateFlow<PdfOperationType?> = _activeTool.asStateFlow()

    fun setActiveTool(tool: PdfOperationType?) {
        _activeTool.value = tool
    }

    fun selectTab(index: Int) {
        _currentTab.value = index
        _activeTool.value = null
    }

    // Common Processing State
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _processingMessage = MutableStateFlow("")
    val processingMessage: StateFlow<String> = _processingMessage.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    fun clearUserMessage() {
        _userMessage.value = null
    }

    private fun loadSamplePdfs() {
        viewModelScope.launch {
            try {
                val samples = repository.generateSamples(getApplication())
                _samplePdfs.value = samples
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- MERGE TAB STATE ---
    private val _mergePages = MutableStateFlow<List<PdfPageSourceItem>>(emptyList())
    val mergePages: StateFlow<List<PdfPageSourceItem>> = _mergePages.asStateFlow()

    private val _mergeThumbnails = MutableStateFlow<Map<String, Bitmap>>(emptyMap())
    val mergeThumbnails: StateFlow<Map<String, Bitmap>> = _mergeThumbnails.asStateFlow()

    private val _mergeOutputName = MutableStateFlow("PDFCraft_Merged.pdf")
    val mergeOutputName: StateFlow<String> = _mergeOutputName.asStateFlow()

    private val _selectedMergePageIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedMergePageIds: StateFlow<Set<String>> = _selectedMergePageIds.asStateFlow()

    fun setMergeOutputName(name: String) {
        _mergeOutputName.value = name
    }

    fun addPdfsToMerge(uris: List<Uri>) {
        viewModelScope.launch {
            _isProcessing.value = true
            _processingMessage.value = "Importing & rendering thumbnails..."
            try {
                val newPages = mutableListOf<PdfPageSourceItem>()
                val newThumbnails = _mergeThumbnails.value.toMutableMap()

                for (uri in uris) {
                    val mimeType = getApplication<android.app.Application>().contentResolver.getType(uri) ?: ""
                    val isImage = mimeType.startsWith("image/") || 
                            uri.path?.lowercase()?.let { path ->
                                path.endsWith(".jpg") || path.endsWith(".jpeg") || 
                                path.endsWith(".png") || path.endsWith(".webp") || 
                                path.endsWith(".bmp")
                            } == true

                    if (isImage) {
                        val localFile = PdfEngine.getLocalImageFileFromUri(getApplication(), uri)
                        val itemId = java.util.UUID.randomUUID().toString()
                        val pageItem = PdfPageSourceItem(
                            id = itemId,
                            sourceTitle = localFile.name,
                            sourceUri = Uri.fromFile(localFile),
                            originalPageIndex = -1 // Indicates an image page
                        )
                        newPages.add(pageItem)

                        // Render image thumbnail
                        val bmp = PdfEngine.renderImageThumbnail(getApplication(), localFile)
                        if (bmp != null) {
                            newThumbnails[itemId] = bmp
                        }
                    } else {
                        val file = PdfEngine.convertAnyFileToPdf(getApplication(), uri)
                        val pfd = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                        val renderer = android.graphics.pdf.PdfRenderer(pfd)
                        val pageCount = renderer.pageCount
                        renderer.close()
                        pfd.close()

                        for (i in 0 until pageCount) {
                            val itemId = java.util.UUID.randomUUID().toString()
                            val pageItem = PdfPageSourceItem(
                                id = itemId,
                                sourceTitle = file.name,
                                sourceUri = Uri.fromFile(file),
                                originalPageIndex = i
                            )
                            newPages.add(pageItem)

                            // Render thumbnail bitmap
                            val bmp = repository.renderThumbnail(getApplication(), file, i)
                            if (bmp != null) {
                                newThumbnails[itemId] = bmp
                            }
                        }
                    }
                }

                _mergePages.value = _mergePages.value + newPages
                _mergeThumbnails.value = newThumbnails
                _userMessage.value = "Added ${newPages.size} items to studio grid"
            } catch (e: Exception) {
                _userMessage.value = "Failed to import files: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun addSampleToMerge(file: File) {
        addPdfsToMerge(listOf(Uri.fromFile(file)))
    }

    fun moveMergePage(fromIndex: Int, toIndex: Int) {
        val list = _mergePages.value.toMutableList()
        if (fromIndex in list.indices && toIndex in list.indices) {
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            _mergePages.value = list
        }
    }

    fun rotateMergePage(itemId: String) {
        val list = _mergePages.value.toMutableList()
        val index = list.indexOfFirst { it.id == itemId }
        if (index != -1) {
            val oldItem = list[index]
            list[index] = oldItem.copy(rotationDegrees = (oldItem.rotationDegrees + 90) % 360)
            _mergePages.value = list
        }
    }

    fun toggleMergePageSelection(itemId: String) {
        val current = _selectedMergePageIds.value
        _selectedMergePageIds.value = if (current.contains(itemId)) {
            current - itemId
        } else {
            current + itemId
        }
    }

    fun selectAllMergePages() {
        _selectedMergePageIds.value = _mergePages.value.map { it.id }.toSet()
    }

    fun clearMergePageSelection() {
        _selectedMergePageIds.value = emptySet()
    }

    fun rotateSelectedMergePages(degrees: Int) {
        val selectedIds = _selectedMergePageIds.value
        if (selectedIds.isEmpty()) return
        val list = _mergePages.value.map { item ->
            if (selectedIds.contains(item.id)) {
                item.copy(rotationDegrees = (item.rotationDegrees + degrees) % 360)
            } else {
                item
            }
        }
        _mergePages.value = list
    }

    fun rotateAllMergePages(degrees: Int) {
        val list = _mergePages.value.map { item ->
            item.copy(rotationDegrees = (item.rotationDegrees + degrees) % 360)
        }
        _mergePages.value = list
    }

    fun removeMergePage(itemId: String) {
        _mergePages.value = _mergePages.value.filterNot { it.id == itemId }
        _selectedMergePageIds.value = _selectedMergePageIds.value - itemId
    }

    fun clearMergeGrid() {
        _mergePages.value = emptyList()
        _mergeThumbnails.value = emptyMap()
        _selectedMergePageIds.value = emptySet()
    }

    fun executeMerge() {
        val pages = _mergePages.value
        if (pages.isEmpty()) {
            _userMessage.value = "Add at least one page to merge"
            return
        }

        viewModelScope.launch {
            _isProcessing.value = true
            _processingMessage.value = "Merging ${pages.size} pages locally..."
            try {
                var totalOriginalBytes = 0L
                pages.map { it.sourceUri }.distinct().forEach { uri ->
                    val file = PdfEngine.getLocalFileFromUri(getApplication(), uri)
                    totalOriginalBytes += file.length()
                }

                val outputFile = repository.mergePages(
                    context = getApplication(),
                    pageItems = pages,
                    outputName = _mergeOutputName.value,
                    originalSizeBytes = totalOriginalBytes
                )

                _userMessage.value = "Exported ${outputFile.name} (${PdfEngine.formatFileSize(outputFile.length())})!"
                FirebaseConfigManager.logAnalyticsEvent("pdf_merge_success", mapOf("page_count" to pages.size))
                if (FirebaseConfigManager.isAdMobEnabled.value) {
                    _showPoliteAdDialog.value = true
                }
            } catch (e: Exception) {
                _userMessage.value = "Merge error: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    // --- PDFBOX DIRECT DOCUMENT MERGE STATE ---
    private val _selectedPdfDocuments = MutableStateFlow<List<PdfDocumentInfo>>(emptyList())
    val selectedPdfDocuments: StateFlow<List<PdfDocumentInfo>> = _selectedPdfDocuments.asStateFlow()

    fun addPdfDocumentsForPdfBoxMerge(uris: List<Uri>) {
        viewModelScope.launch {
            _isProcessing.value = true
            _processingMessage.value = "Reading document headers..."
            try {
                val newDocs = mutableListOf<PdfDocumentInfo>()
                for (uri in uris) {
                    val file = PdfEngine.convertAnyFileToPdf(getApplication(), uri)
                    var pageCount = 1
                    try {
                        val pfd = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                        val renderer = android.graphics.pdf.PdfRenderer(pfd)
                        pageCount = renderer.pageCount
                        renderer.close()
                        pfd.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    newDocs.add(
                        PdfDocumentInfo(
                            title = file.name,
                            sizeFormatted = PdfEngine.formatFileSize(file.length()),
                            sizeBytes = file.length(),
                            pageCount = pageCount,
                            uri = Uri.fromFile(file)
                        )
                    )
                }
                _selectedPdfDocuments.value = _selectedPdfDocuments.value + newDocs
                _userMessage.value = "Added ${newDocs.size} document(s) for PDFBox merge"
            } catch (e: Exception) {
                _userMessage.value = "Error reading files: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun addSamplePdfDocumentsForPdfBoxMerge(files: List<File>) {
        addPdfDocumentsForPdfBoxMerge(files.map { Uri.fromFile(it) })
    }

    fun removePdfDocumentForPdfBoxMerge(id: String) {
        _selectedPdfDocuments.value = _selectedPdfDocuments.value.filterNot { it.id == id }
    }

    fun movePdfDocumentForPdfBoxMerge(fromIndex: Int, toIndex: Int) {
        val list = _selectedPdfDocuments.value.toMutableList()
        if (fromIndex in list.indices && toIndex in list.indices) {
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            _selectedPdfDocuments.value = list
        }
    }

    fun clearPdfDocumentsForPdfBoxMerge() {
        _selectedPdfDocuments.value = emptyList()
    }

    fun executePdfBoxMerge(customOutputName: String? = null) {
        val docs = _selectedPdfDocuments.value
        if (docs.isEmpty()) {
            _userMessage.value = "Please select at least one PDF file to merge"
            return
        }

        viewModelScope.launch {
            _isProcessing.value = true
            _processingMessage.value = "Merging ${docs.size} files with PDFBox Engine..."
            try {
                val uris = docs.map { it.uri }
                val name = customOutputName?.takeIf { it.isNotBlank() } ?: _mergeOutputName.value
                val totalBytes = docs.sumOf { it.sizeBytes }

                val outputFile = repository.mergePdfFilesWithPdfBox(
                    context = getApplication(),
                    pdfUris = uris,
                    outputName = name,
                    originalSizeBytes = totalBytes
                )

                _userMessage.value = "PDFBox Merge Complete! Saved ${outputFile.name} (${PdfEngine.formatFileSize(outputFile.length())})"
                FirebaseConfigManager.logAnalyticsEvent("pdfbox_merge_success", mapOf("file_count" to docs.size))
                if (FirebaseConfigManager.isAdMobEnabled.value) {
                    _showPoliteAdDialog.value = true
                }
            } catch (e: Exception) {
                _userMessage.value = "PDFBox Merge Error: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    // --- SPLIT TAB STATE ---
    private val _splitSourceUri = MutableStateFlow<Uri?>(null)
    val splitSourceUri: StateFlow<Uri?> = _splitSourceUri.asStateFlow()

    private val _splitSourceFile = MutableStateFlow<File?>(null)
    val splitSourceFile: StateFlow<File?> = _splitSourceFile.asStateFlow()

    private val _splitTotalPages = MutableStateFlow(0)
    val splitTotalPages: StateFlow<Int> = _splitTotalPages.asStateFlow()

    private val _splitSelectedPages = MutableStateFlow<Set<Int>>(emptySet())
    val splitSelectedPages: StateFlow<Set<Int>> = _splitSelectedPages.asStateFlow()

    private val _splitThumbnails = MutableStateFlow<List<Bitmap?>>(emptyList())
    val splitThumbnails: StateFlow<List<Bitmap?>> = _splitThumbnails.asStateFlow()

    private val _splitMode = MutableStateFlow(SplitMode.COMBINE_SELECTED)
    val splitMode: StateFlow<SplitMode> = _splitMode.asStateFlow()

    fun setSplitMode(mode: SplitMode) {
        _splitMode.value = mode
    }

    fun loadPdfForSplit(uri: Uri) {
        viewModelScope.launch {
            _isProcessing.value = true
            _processingMessage.value = "Reading document pages..."
            try {
                val file = PdfEngine.convertAnyFileToPdf(getApplication(), uri)
                _splitSourceUri.value = uri
                _splitSourceFile.value = file

                val pfd = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = android.graphics.pdf.PdfRenderer(pfd)
                val count = renderer.pageCount
                renderer.close()
                pfd.close()

                _splitTotalPages.value = count
                _splitSelectedPages.value = (0 until count).toSet() // Select all by default
                recordRecentFileAccess(file, count)

                // Render page thumbnails
                val thumbnails = mutableListOf<Bitmap?>()
                for (i in 0 until count) {
                    val bmp = repository.renderThumbnail(getApplication(), file, i, targetWidthDp = 180)
                    thumbnails.add(bmp)
                }
                _splitThumbnails.value = thumbnails
            } catch (e: Exception) {
                _userMessage.value = "Could not load PDF: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun toggleSplitPageSelection(pageIdx: Int) {
        val current = _splitSelectedPages.value.toMutableSet()
        if (current.contains(pageIdx)) {
            current.remove(pageIdx)
        } else {
            current.add(pageIdx)
        }
        _splitSelectedPages.value = current
    }

    fun selectAllSplitPages() {
        _splitSelectedPages.value = (0 until _splitTotalPages.value).toSet()
    }

    fun clearSplitPageSelection() {
        _splitSelectedPages.value = emptySet()
    }

    fun selectOddSplitPages() {
        _splitSelectedPages.value = (0 until _splitTotalPages.value).filter { it % 2 == 0 }.toSet() // 0-indexed odd pages (1, 3, 5...)
    }

    fun selectEvenSplitPages() {
        _splitSelectedPages.value = (0 until _splitTotalPages.value).filter { it % 2 != 0 }.toSet() // 0-indexed even pages (2, 4, 6...)
    }

    fun selectPageRange(rangeStr: String) {
        val total = _splitTotalPages.value
        if (total <= 0 || rangeStr.isBlank()) return

        val selected = mutableSetOf<Int>()
        val parts = rangeStr.split(",")
        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.contains("-")) {
                val rangeParts = trimmed.split("-")
                if (rangeParts.size == 2) {
                    val start = rangeParts[0].trim().toIntOrNull()
                    val end = rangeParts[1].trim().toIntOrNull()
                    if (start != null && end != null && start <= end) {
                        for (p in start..end) {
                            val idx = p - 1
                            if (idx in 0 until total) {
                                selected.add(idx)
                            }
                        }
                    }
                }
            } else {
                val p = trimmed.toIntOrNull()
                if (p != null) {
                    val idx = p - 1
                    if (idx in 0 until total) {
                        selected.add(idx)
                    }
                }
            }
        }
        _splitSelectedPages.value = selected
    }

    fun executeSplit() {
        val uri = _splitSourceUri.value ?: run {
            _userMessage.value = "Please select a PDF document first"
            return
        }
        val file = _splitSourceFile.value ?: return
        val selected = _splitSelectedPages.value.toList().sorted()

        if (selected.isEmpty()) {
            _userMessage.value = "Select at least one page to split"
            return
        }

        viewModelScope.launch {
            _isProcessing.value = true
            _processingMessage.value = "Splitting selected ${selected.size} pages..."
            try {
                if (_splitMode.value == SplitMode.COMBINE_SELECTED) {
                    val outputName = "Split_${file.nameWithoutExtension}_Pages_${selected.size}.pdf"
                    val outputFile = repository.splitPdf(
                        context = getApplication(),
                        sourceUri = uri,
                        selectedIndices = selected,
                        outputName = outputName,
                        originalSizeBytes = file.length()
                    )
                    _userMessage.value = "Created ${outputFile.name} (${PdfEngine.formatFileSize(outputFile.length())})!"
                } else {
                    val baseOutputName = "Split_${file.nameWithoutExtension}"
                    val outputFiles = repository.splitPdfToSeparate(
                        context = getApplication(),
                        sourceUri = uri,
                        selectedIndices = selected,
                        baseOutputName = baseOutputName,
                        originalSizeBytes = file.length()
                    )
                    _userMessage.value = "Created ${outputFiles.size} separate PDF files in exports folder!"
                }
                FirebaseConfigManager.logAnalyticsEvent("pdf_split_success", mapOf("selected_pages" to selected.size, "mode" to _splitMode.value.name))
                if (FirebaseConfigManager.isAdMobEnabled.value) {
                    _showPoliteAdDialog.value = true
                }
            } catch (e: Exception) {
                _userMessage.value = "Split failed: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    // --- COMPRESS & EXTRACT TAB STATE ---
    private val _compressSourceUri = MutableStateFlow<Uri?>(null)
    val compressSourceUri: StateFlow<Uri?> = _compressSourceUri.asStateFlow()

    private val _compressSourceFile = MutableStateFlow<File?>(null)
    val compressSourceFile: StateFlow<File?> = _compressSourceFile.asStateFlow()

    private val _compressPageCount = MutableStateFlow(0)
    val compressPageCount: StateFlow<Int> = _compressPageCount.asStateFlow()

    private val _compressSelectedPreset = MutableStateFlow(PdfEngine.COMPRESSION_PRESETS[1]) // Balanced
    val compressSelectedPreset: StateFlow<CompressionPreset> = _compressSelectedPreset.asStateFlow()

    private val _extractedImages = MutableStateFlow<List<File>>(emptyList())
    val extractedImages: StateFlow<List<File>> = _extractedImages.asStateFlow()

    fun selectCompressPreset(preset: CompressionPreset) {
        _compressSelectedPreset.value = preset
    }

    fun loadPdfForCompress(uri: Uri) {
        viewModelScope.launch {
            _isProcessing.value = true
            _processingMessage.value = "Analyzing document structure..."
            try {
                val file = PdfEngine.convertAnyFileToPdf(getApplication(), uri)
                _compressSourceUri.value = uri
                _compressSourceFile.value = file

                val pfd = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = android.graphics.pdf.PdfRenderer(pfd)
                _compressPageCount.value = renderer.pageCount
                recordRecentFileAccess(file, renderer.pageCount)
                renderer.close()
                pfd.close()

                _extractedImages.value = emptyList()
            } catch (e: Exception) {
                _userMessage.value = "Could not load document: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun executeCompress() {
        val uri = _compressSourceUri.value ?: run {
            _userMessage.value = "Select a PDF file to compress"
            return
        }
        val file = _compressSourceFile.value ?: return

        viewModelScope.launch {
            _isProcessing.value = true
            _processingMessage.value = "Optimizing page streams locally..."
            try {
                val preset = _compressSelectedPreset.value
                val outputName = "Compressed_${file.nameWithoutExtension}_${preset.title.replace(" ", "_")}.pdf"
                val outputFile = repository.compressPdf(
                    context = getApplication(),
                    sourceUri = uri,
                    preset = preset,
                    outputName = outputName,
                    originalSizeBytes = file.length()
                )

                val savedBytes = file.length() - outputFile.length()
                val percentSaved = if (file.length() > 0) ((savedBytes.toDouble() / file.length()) * 100).toInt() else 0

                _userMessage.value = "Compressed from ${PdfEngine.formatFileSize(file.length())} to ${PdfEngine.formatFileSize(outputFile.length())} ($percentSaved% smaller)!"
                FirebaseConfigManager.logAnalyticsEvent("pdf_compress_success", mapOf("preset" to preset.title))
                if (FirebaseConfigManager.isAdMobEnabled.value) {
                    _showPoliteAdDialog.value = true
                }
            } catch (e: Exception) {
                _userMessage.value = "Compression failed: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun executeExtractImages(format: String = "PNG") {
        val uri = _compressSourceUri.value ?: run {
            _userMessage.value = "Select a PDF document first"
            return
        }
        val file = _compressSourceFile.value ?: return
        val count = _compressPageCount.value

        viewModelScope.launch {
            _isProcessing.value = true
            _processingMessage.value = "Extracting high-res $format page images..."
            try {
                val indices = (0 until count).toList()
                val images = repository.extractImages(
                    context = getApplication(),
                    sourceUri = uri,
                    selectedIndices = indices,
                    format = format,
                    originalSizeBytes = file.length()
                )
                _extractedImages.value = images
                _userMessage.value = "Extracted ${images.size} $format images to exports folder!"
            } catch (e: Exception) {
                _userMessage.value = "Extraction failed: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    // --- LIBRARY & HISTORY ACTIONS ---
    fun toggleFavorite(id: Long, currentFav: Boolean) {
        viewModelScope.launch {
            repository.toggleFavorite(id, !currentFav)
        }
    }

    fun deleteProject(id: Long) {
        viewModelScope.launch {
            repository.deleteProject(id)
            _userMessage.value = "Project record deleted"
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            _userMessage.value = "History cleared"
        }
    }

    // Document Viewer Dialog
    private val _activeViewerFile = MutableStateFlow<File?>(null)
    val activeViewerFile: StateFlow<File?> = _activeViewerFile.asStateFlow()

    // Document Metadata Dialog
    private val _selectedMetadata = MutableStateFlow<PdfMetadata?>(null)
    val selectedMetadata: StateFlow<PdfMetadata?> = _selectedMetadata.asStateFlow()

    private val _isLoadingMetadata = MutableStateFlow(false)
    val isLoadingMetadata: StateFlow<Boolean> = _isLoadingMetadata.asStateFlow()

    fun inspectFileMetadata(filePath: String) {
        val file = File(filePath)
        if (!file.exists()) {
            _userMessage.value = "File no longer exists on local storage"
            return
        }
        viewModelScope.launch {
            _isLoadingMetadata.value = true
            _selectedMetadata.value = null
            val meta = PdfEngine.extractMetadata(file)
            _selectedMetadata.value = meta
            _isLoadingMetadata.value = false
            recordRecentFileAccess(file, meta.pageCount)
        }
    }

    fun clearSelectedMetadata() {
        _selectedMetadata.value = null
    }

    fun openInViewer(filePath: String) {
        val file = File(filePath)
        if (file.exists()) {
            _activeViewerFile.value = file
            recordRecentFileAccess(file)
        } else {
            _userMessage.value = "File no longer exists on local storage"
        }
    }

    fun closeViewer() {
        _activeViewerFile.value = null
    }

    // --- ENCRYPT TAB STATE ---
    private val _encryptSourceUri = MutableStateFlow<Uri?>(null)
    val encryptSourceUri: StateFlow<Uri?> = _encryptSourceUri.asStateFlow()

    private val _encryptSourceFile = MutableStateFlow<File?>(null)
    val encryptSourceFile: StateFlow<File?> = _encryptSourceFile.asStateFlow()

    private val _encryptPageCount = MutableStateFlow(0)
    val encryptPageCount: StateFlow<Int> = _encryptPageCount.asStateFlow()

    private val _encryptUserPassword = MutableStateFlow("")
    val encryptUserPassword: StateFlow<String> = _encryptUserPassword.asStateFlow()

    private val _encryptOwnerPassword = MutableStateFlow("")
    val encryptOwnerPassword: StateFlow<String> = _encryptOwnerPassword.asStateFlow()

    private val _encryptOutputName = MutableStateFlow("PDFCraft_Secure.pdf")
    val encryptOutputName: StateFlow<String> = _encryptOutputName.asStateFlow()

    fun setEncryptUserPassword(pw: String) {
        _encryptUserPassword.value = pw
    }

    fun setEncryptOwnerPassword(pw: String) {
        _encryptOwnerPassword.value = pw
    }

    fun setEncryptOutputName(name: String) {
        _encryptOutputName.value = name
    }

    fun loadPdfForEncrypt(uri: Uri) {
        viewModelScope.launch {
            _isProcessing.value = true
            _processingMessage.value = "Loading PDF for encryption..."
            try {
                val file = PdfEngine.convertAnyFileToPdf(getApplication(), uri)
                _encryptSourceUri.value = uri
                _encryptSourceFile.value = file

                val pfd = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = android.graphics.pdf.PdfRenderer(pfd)
                _encryptPageCount.value = renderer.pageCount
                recordRecentFileAccess(file, renderer.pageCount)
                renderer.close()
                pfd.close()

                _encryptOutputName.value = "Secure_${file.name}"
            } catch (e: Exception) {
                _userMessage.value = "Could not load PDF: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun executeEncrypt() {
        val uri = _encryptSourceUri.value ?: run {
            _userMessage.value = "Please select a PDF file to encrypt"
            return
        }
        val file = _encryptSourceFile.value ?: return
        val userPw = _encryptUserPassword.value
        if (userPw.isBlank()) {
            _userMessage.value = "Please enter an open password"
            return
        }
        val ownerPw = _encryptOwnerPassword.value.takeIf { it.isNotBlank() } ?: (userPw + "_owner")

        viewModelScope.launch {
            _isProcessing.value = true
            _processingMessage.value = "Encrypting PDF with 128-bit AES protection..."
            try {
                val outputFile = repository.encryptPdf(
                    context = getApplication(),
                    sourceUri = uri,
                    userPassword = userPw,
                    ownerPassword = ownerPw,
                    outputName = _encryptOutputName.value,
                    originalSizeBytes = file.length()
                )
                _userMessage.value = "Successfully encrypted and saved ${outputFile.name} (${PdfEngine.formatFileSize(outputFile.length())})!"
                FirebaseConfigManager.logAnalyticsEvent("pdf_encrypt_success", emptyMap())
                if (FirebaseConfigManager.isAdMobEnabled.value) {
                    _showPoliteAdDialog.value = true
                }
            } catch (e: Exception) {
                _userMessage.value = "Encryption failed: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    // --- WATERMARK TAB STATE ---
    private val _watermarkSourceUri = MutableStateFlow<Uri?>(null)
    val watermarkSourceUri: StateFlow<Uri?> = _watermarkSourceUri.asStateFlow()

    private val _watermarkSourceFile = MutableStateFlow<File?>(null)
    val watermarkSourceFile: StateFlow<File?> = _watermarkSourceFile.asStateFlow()

    private val _watermarkPageCount = MutableStateFlow(0)
    val watermarkPageCount: StateFlow<Int> = _watermarkPageCount.asStateFlow()

    private val _watermarkType = MutableStateFlow(com.example.data.pdf.WatermarkType.TEXT)
    val watermarkType: StateFlow<com.example.data.pdf.WatermarkType> = _watermarkType.asStateFlow()

    private val _watermarkText = MutableStateFlow("CONFIDENTIAL")
    val watermarkText: StateFlow<String> = _watermarkText.asStateFlow()

    private val _watermarkTextSize = MutableStateFlow(36f)
    val watermarkTextSize: StateFlow<Float> = _watermarkTextSize.asStateFlow()

    private val _watermarkTextColorRgb = MutableStateFlow(intArrayOf(180, 180, 180))
    val watermarkTextColorRgb: StateFlow<IntArray> = _watermarkTextColorRgb.asStateFlow()

    private val _watermarkImageUri = MutableStateFlow<Uri?>(null)
    val watermarkImageUri: StateFlow<Uri?> = _watermarkImageUri.asStateFlow()

    private val _watermarkImageScale = MutableStateFlow(0.4f)
    val watermarkImageScale: StateFlow<Float> = _watermarkImageScale.asStateFlow()

    private val _watermarkOpacity = MutableStateFlow(0.3f)
    val watermarkOpacity: StateFlow<Float> = _watermarkOpacity.asStateFlow()

    private val _watermarkRotation = MutableStateFlow(45f)
    val watermarkRotation: StateFlow<Float> = _watermarkRotation.asStateFlow()

    private val _watermarkPosition = MutableStateFlow(com.example.data.pdf.WatermarkPosition.CENTER)
    val watermarkPosition: StateFlow<com.example.data.pdf.WatermarkPosition> = _watermarkPosition.asStateFlow()

    private val _watermarkPageRange = MutableStateFlow(com.example.data.pdf.WatermarkPageRange.ALL_PAGES)
    val watermarkPageRange: StateFlow<com.example.data.pdf.WatermarkPageRange> = _watermarkPageRange.asStateFlow()

    private val _watermarkOutputName = MutableStateFlow("PDFCraft_Watermarked.pdf")
    val watermarkOutputName: StateFlow<String> = _watermarkOutputName.asStateFlow()

    private val _watermarkPreviewBitmap = MutableStateFlow<android.graphics.Bitmap?>(null)
    val watermarkPreviewBitmap: StateFlow<android.graphics.Bitmap?> = _watermarkPreviewBitmap.asStateFlow()

    private var watermarkPreviewJob: Job? = null

    fun setWatermarkType(type: com.example.data.pdf.WatermarkType) {
        _watermarkType.value = type
        triggerWatermarkPreviewUpdate()
    }

    fun setWatermarkText(text: String) {
        _watermarkText.value = text
        triggerWatermarkPreviewUpdate()
    }

    fun setWatermarkTextSize(size: Float) {
        _watermarkTextSize.value = size
        triggerWatermarkPreviewUpdate()
    }

    fun setWatermarkTextColorRgb(rgb: IntArray) {
        _watermarkTextColorRgb.value = rgb
        triggerWatermarkPreviewUpdate()
    }

    fun setWatermarkImageUri(uri: Uri?) {
        _watermarkImageUri.value = uri
        if (uri != null && _watermarkType.value != com.example.data.pdf.WatermarkType.IMAGE) {
            _watermarkType.value = com.example.data.pdf.WatermarkType.IMAGE
        }
        triggerWatermarkPreviewUpdate()
    }

    fun setWatermarkImageScale(scale: Float) {
        _watermarkImageScale.value = scale
        triggerWatermarkPreviewUpdate()
    }

    fun setWatermarkOpacity(opacity: Float) {
        _watermarkOpacity.value = opacity
        triggerWatermarkPreviewUpdate()
    }

    fun setWatermarkRotation(angle: Float) {
        _watermarkRotation.value = angle
        triggerWatermarkPreviewUpdate()
    }

    fun setWatermarkPosition(position: com.example.data.pdf.WatermarkPosition) {
        _watermarkPosition.value = position
        triggerWatermarkPreviewUpdate()
    }

    fun setWatermarkPageRange(range: com.example.data.pdf.WatermarkPageRange) {
        _watermarkPageRange.value = range
        triggerWatermarkPreviewUpdate()
    }

    fun setWatermarkOutputName(name: String) {
        _watermarkOutputName.value = name
    }

    fun getCurrentWatermarkOptions(): com.example.data.pdf.WatermarkOptions {
        return com.example.data.pdf.WatermarkOptions(
            type = _watermarkType.value,
            text = _watermarkText.value,
            textSize = _watermarkTextSize.value,
            textColorRgb = _watermarkTextColorRgb.value,
            imageUri = _watermarkImageUri.value,
            imageScale = _watermarkImageScale.value,
            opacity = _watermarkOpacity.value,
            rotationAngle = _watermarkRotation.value,
            position = _watermarkPosition.value,
            pageRange = _watermarkPageRange.value
        )
    }

    fun triggerWatermarkPreviewUpdate() {
        val uri = _watermarkSourceUri.value ?: return
        watermarkPreviewJob?.cancel()
        watermarkPreviewJob = viewModelScope.launch {
            kotlinx.coroutines.delay(120)
            val options = getCurrentWatermarkOptions()
            val bmp = PdfEngine.generateWatermarkedPreview(getApplication(), uri, options)
            _watermarkPreviewBitmap.value = bmp
        }
    }

    fun loadPdfForWatermark(uri: Uri) {
        viewModelScope.launch {
            _isProcessing.value = true
            _processingMessage.value = "Loading PDF for watermarking..."
            try {
                val file = PdfEngine.convertAnyFileToPdf(getApplication(), uri)
                _watermarkSourceUri.value = uri
                _watermarkSourceFile.value = file

                val pfd = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = android.graphics.pdf.PdfRenderer(pfd)
                _watermarkPageCount.value = renderer.pageCount
                recordRecentFileAccess(file, renderer.pageCount)
                renderer.close()
                pfd.close()

                _watermarkOutputName.value = "Watermarked_${file.name}"
                triggerWatermarkPreviewUpdate()
            } catch (e: Exception) {
                _userMessage.value = "Could not load PDF: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun executeWatermark() {
        val uri = _watermarkSourceUri.value ?: return
        val file = _watermarkSourceFile.value ?: return
        val options = getCurrentWatermarkOptions()

        viewModelScope.launch {
            _isProcessing.value = true
            _processingMessage.value = "Applying watermark to PDF pages..."
            try {
                val outputFile = repository.watermarkPdf(
                    context = getApplication(),
                    sourceUri = uri,
                    options = options,
                    outputName = _watermarkOutputName.value,
                    originalSizeBytes = file.length()
                )
                _userMessage.value = "Successfully watermarked ${outputFile.name}!"
                FirebaseConfigManager.logAnalyticsEvent("pdf_watermark_success", emptyMap())
            } catch (e: Exception) {
                _userMessage.value = "Watermark failed: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    // --- SIGN TAB STATE ---
    private val _signSourceUri = MutableStateFlow<Uri?>(null)
    val signSourceUri: StateFlow<Uri?> = _signSourceUri.asStateFlow()

    private val _signSourceFile = MutableStateFlow<File?>(null)
    val signSourceFile: StateFlow<File?> = _signSourceFile.asStateFlow()

    private val _signPageCount = MutableStateFlow(0)
    val signPageCount: StateFlow<Int> = _signPageCount.asStateFlow()

    private val _signerName = MutableStateFlow("Authorized Signatory")
    val signerName: StateFlow<String> = _signerName.asStateFlow()

    private val _signReason = MutableStateFlow("Approved & Verified")
    val signReason: StateFlow<String> = _signReason.asStateFlow()

    private val _signOutputName = MutableStateFlow("PDFCraft_Signed.pdf")
    val signOutputName: StateFlow<String> = _signOutputName.asStateFlow()

    fun setSignerName(name: String) {
        _signerName.value = name
    }

    fun setSignReason(reason: String) {
        _signReason.value = reason
    }

    fun setSignOutputName(name: String) {
        _signOutputName.value = name
    }

    fun loadPdfForSign(uri: Uri) {
        viewModelScope.launch {
            _isProcessing.value = true
            _processingMessage.value = "Loading PDF for digital signature..."
            try {
                val file = PdfEngine.convertAnyFileToPdf(getApplication(), uri)
                _signSourceUri.value = uri
                _signSourceFile.value = file

                val pfd = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = android.graphics.pdf.PdfRenderer(pfd)
                _signPageCount.value = renderer.pageCount
                recordRecentFileAccess(file, renderer.pageCount)
                renderer.close()
                pfd.close()

                _signOutputName.value = "Signed_${file.name}"
            } catch (e: Exception) {
                _userMessage.value = "Could not load PDF: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun executeSign() {
        val uri = _signSourceUri.value ?: return
        val file = _signSourceFile.value ?: return
        val name = _signerName.value
        val reason = _signReason.value

        viewModelScope.launch {
            _isProcessing.value = true
            _processingMessage.value = "Applying digital signature block..."
            try {
                val outputFile = repository.signPdf(
                    context = getApplication(),
                    sourceUri = uri,
                    signerName = name,
                    reason = reason,
                    outputName = _signOutputName.value,
                    originalSizeBytes = file.length()
                )
                _userMessage.value = "Successfully signed ${outputFile.name}!"
                FirebaseConfigManager.logAnalyticsEvent("pdf_sign_success", emptyMap())
            } catch (e: Exception) {
                _userMessage.value = "Signature failed: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    // --- METADATA EDITING STATE & FUNCTIONS ---
    private val _metadataSourceUri = MutableStateFlow<Uri?>(null)
    val metadataSourceUri: StateFlow<Uri?> = _metadataSourceUri.asStateFlow()

    private val _metadataSourceFile = MutableStateFlow<File?>(null)
    val metadataSourceFile: StateFlow<File?> = _metadataSourceFile.asStateFlow()

    private val _metadataTitle = MutableStateFlow("")
    val metadataTitle: StateFlow<String> = _metadataTitle.asStateFlow()

    private val _metadataAuthor = MutableStateFlow("")
    val metadataAuthor: StateFlow<String> = _metadataAuthor.asStateFlow()

    private val _metadataSubject = MutableStateFlow("")
    val metadataSubject: StateFlow<String> = _metadataSubject.asStateFlow()

    private val _metadataKeywords = MutableStateFlow("")
    val metadataKeywords: StateFlow<String> = _metadataKeywords.asStateFlow()

    private val _metadataCreator = MutableStateFlow("")
    val metadataCreator: StateFlow<String> = _metadataCreator.asStateFlow()

    private val _metadataProducer = MutableStateFlow("")
    val metadataProducer: StateFlow<String> = _metadataProducer.asStateFlow()

    private val _metadataCreationDate = MutableStateFlow("")
    val metadataCreationDate: StateFlow<String> = _metadataCreationDate.asStateFlow()

    private val _metadataModificationDate = MutableStateFlow("")
    val metadataModificationDate: StateFlow<String> = _metadataModificationDate.asStateFlow()

    private val _metadataPageCount = MutableStateFlow(0)
    val metadataPageCount: StateFlow<Int> = _metadataPageCount.asStateFlow()

    private val _metadataFileSizeFormatted = MutableStateFlow("")
    val metadataFileSizeFormatted: StateFlow<String> = _metadataFileSizeFormatted.asStateFlow()

    private val _metadataOutputName = MutableStateFlow("PDFCraft_Updated.pdf")
    val metadataOutputName: StateFlow<String> = _metadataOutputName.asStateFlow()

    fun setMetadataTitle(title: String) { _metadataTitle.value = title }
    fun setMetadataAuthor(author: String) { _metadataAuthor.value = author }
    fun setMetadataSubject(subject: String) { _metadataSubject.value = subject }
    fun setMetadataKeywords(keywords: String) { _metadataKeywords.value = keywords }
    fun setMetadataOutputName(name: String) { _metadataOutputName.value = name }

    fun loadPdfForMetadata(uri: Uri) {
        viewModelScope.launch {
            _isProcessing.value = true
            _processingMessage.value = "Reading document metadata..."
            try {
                val file = PdfEngine.convertAnyFileToPdf(getApplication(), uri)
                _metadataSourceUri.value = uri
                _metadataSourceFile.value = file

                val meta = PdfEngine.readPdfMetadata(getApplication(), uri)
                _metadataTitle.value = meta.title ?: ""
                _metadataAuthor.value = meta.author ?: ""
                _metadataSubject.value = meta.subject ?: ""
                _metadataKeywords.value = meta.keywords ?: ""
                _metadataCreator.value = meta.creator ?: ""
                _metadataProducer.value = meta.producer ?: ""
                _metadataCreationDate.value = meta.creationDateFormatted ?: ""
                _metadataModificationDate.value = meta.modDateFormatted ?: ""
                _metadataPageCount.value = meta.pageCount
                _metadataFileSizeFormatted.value = meta.fileSizeFormatted

                recordRecentFileAccess(file, meta.pageCount)

                _metadataOutputName.value = "Updated_${file.name}"
            } catch (e: Exception) {
                _userMessage.value = "Could not load PDF metadata: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun executeMetadataUpdate() {
        val uri = _metadataSourceUri.value ?: return
        val file = _metadataSourceFile.value ?: return

        val metadata = com.example.data.pdf.PdfMetadata(
            fileName = file.name,
            filePath = file.absolutePath,
            fileSizeFormatted = _metadataFileSizeFormatted.value,
            fileSizeBytes = file.length(),
            title = _metadataTitle.value,
            author = _metadataAuthor.value,
            subject = _metadataSubject.value,
            keywords = _metadataKeywords.value,
            creator = _metadataCreator.value,
            producer = _metadataProducer.value,
            pageCount = _metadataPageCount.value,
            creationDateFormatted = _metadataCreationDate.value,
            modDateFormatted = _metadataModificationDate.value,
            isEncrypted = false
        )

        viewModelScope.launch {
            _isProcessing.value = true
            _processingMessage.value = "Saving updated metadata..."
            try {
                val outputFile = repository.updatePdfMetadata(
                    context = getApplication(),
                    sourceUri = uri,
                    metadata = metadata,
                    outputName = _metadataOutputName.value,
                    originalSizeBytes = file.length()
                )
                _userMessage.value = "Successfully updated document metadata in ${outputFile.name}!"
                FirebaseConfigManager.logAnalyticsEvent("pdf_metadata_update_success", emptyMap())
            } catch (e: Exception) {
                _userMessage.value = "Metadata update failed: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    // --- ROTATE SCREEN STATE & FUNCTIONS ---
    private val _rotateSourceUri = MutableStateFlow<Uri?>(null)
    val rotateSourceUri: StateFlow<Uri?> = _rotateSourceUri.asStateFlow()

    private val _rotateSourceFile = MutableStateFlow<File?>(null)
    val rotateSourceFile: StateFlow<File?> = _rotateSourceFile.asStateFlow()

    private val _rotatePageCount = MutableStateFlow(0)
    val rotatePageCount: StateFlow<Int> = _rotatePageCount.asStateFlow()

    private val _rotateSelectedPages = MutableStateFlow<Set<Int>>(emptySet())
    val rotateSelectedPages: StateFlow<Set<Int>> = _rotateSelectedPages.asStateFlow()

    private val _rotatePageRotations = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val rotatePageRotations: StateFlow<Map<Int, Int>> = _rotatePageRotations.asStateFlow()

    private val _rotateOutputName = MutableStateFlow("PDFCraft_Rotated.pdf")
    val rotateOutputName: StateFlow<String> = _rotateOutputName.asStateFlow()

    fun setRotateOutputName(name: String) { _rotateOutputName.value = name }

    fun loadPdfForRotate(uri: Uri) {
        viewModelScope.launch {
            _isProcessing.value = true
            _processingMessage.value = "Analyzing document for page rotation..."
            try {
                val file = PdfEngine.convertAnyFileToPdf(getApplication(), uri)
                val info = PdfEngine.readPdfMetadata(getApplication(), uri)
                _rotateSourceUri.value = uri
                _rotateSourceFile.value = file
                _rotatePageCount.value = info.pageCount
                _rotateSelectedPages.value = (0 until info.pageCount).toSet() // select all by default
                _rotatePageRotations.value = emptyMap()
                _rotateOutputName.value = "Rotated_${file.name}"

                recordRecentFileAccess(file, info.pageCount)
            } catch (e: Exception) {
                _userMessage.value = "Could not load document for rotation: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun toggleSelectRotatePage(pageIndex: Int) {
        val current = _rotateSelectedPages.value.toMutableSet()
        if (current.contains(pageIndex)) {
            current.remove(pageIndex)
        } else {
            current.add(pageIndex)
        }
        _rotateSelectedPages.value = current
    }

    fun selectAllRotatePages() {
        _rotateSelectedPages.value = (0 until _rotatePageCount.value).toSet()
    }

    fun clearRotatePageSelection() {
        _rotateSelectedPages.value = emptySet()
    }

    fun rotateSelectedPagesBy(degrees: Int) {
        val currentRotations = _rotatePageRotations.value.toMutableMap()
        for (pageIdx in _rotateSelectedPages.value) {
            val oldDeg = currentRotations[pageIdx] ?: 0
            val newDeg = (oldDeg + degrees) % 360
            currentRotations[pageIdx] = if (newDeg < 0) newDeg + 360 else newDeg
        }
        _rotatePageRotations.value = currentRotations
    }

    fun rotateSinglePage(pageIndex: Int, degrees: Int) {
        val currentRotations = _rotatePageRotations.value.toMutableMap()
        val oldDeg = currentRotations[pageIndex] ?: 0
        val newDeg = (oldDeg + degrees) % 360
        currentRotations[pageIndex] = if (newDeg < 0) newDeg + 360 else newDeg
        _rotatePageRotations.value = currentRotations
    }

    fun resetRotatePageRotations() {
        _rotatePageRotations.value = emptyMap()
    }

    fun executePdfRotate() {
        val uri = _rotateSourceUri.value ?: return
        val file = _rotateSourceFile.value ?: return
        val rotations = _rotatePageRotations.value

        if (rotations.isEmpty() || rotations.all { it.value % 360 == 0 }) {
            _userMessage.value = "No page rotation changes specified. Please rotate pages before exporting."
            return
        }

        viewModelScope.launch {
            _isProcessing.value = true
            _processingMessage.value = "Rotating document pages and saving file..."
            try {
                val outputFile = repository.rotatePdfPages(
                    context = getApplication(),
                    sourceUri = uri,
                    pageRotations = rotations,
                    outputName = _rotateOutputName.value,
                    originalSizeBytes = file.length(),
                    pageCount = _rotatePageCount.value
                )
                _userMessage.value = "Successfully saved rotated PDF to ${outputFile.name}!"
                FirebaseConfigManager.logAnalyticsEvent("pdf_rotate_pages_success", emptyMap())
            } catch (e: Exception) {
                _userMessage.value = "Page rotation failed: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
            }
        }
    }
}

