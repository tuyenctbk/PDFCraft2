package com.example

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.MainViewModel
import com.example.ui.components.AppBottomNav
import com.example.ui.components.AppNavigationRail
import com.example.ui.components.AppTopBar
import com.example.ui.components.InfoPrivacyDialog
import com.example.ui.components.PdfMetadataDialog
import com.example.ui.components.PdfViewerDialog
import com.example.ui.components.PoliteAdBanner
import com.example.ui.components.PoliteAdDialog
import com.example.ui.components.SettingsDialog
import com.example.data.local.PdfOperationType
import com.example.ui.screens.CompressScreen
import com.example.ui.screens.EncryptScreen
import com.example.ui.screens.HubScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.MergeScreen
import com.example.ui.screens.MetadataScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.RotateScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SignScreen
import com.example.ui.screens.SplitScreen
import com.example.ui.screens.ToolsScreen
import com.example.ui.screens.WatermarkScreen
import com.example.ui.theme.PDFCraftTheme

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "PDFCraft_Logcat"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "MainActivity.onCreate() initiated")

        try {
            com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(applicationContext)
            Log.d(TAG, "PDFBoxResourceLoader initialized successfully")
        } catch (t: Throwable) {
            Log.e(TAG, "PDFBoxResourceLoader init error: ${t.localizedMessage}", t)
        }

        enableEdgeToEdge()

        setContent {
            val viewModel: MainViewModel = viewModel()
            val themeMode by viewModel.themeMode.collectAsState()

            PDFCraftTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PDFCraftApp(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun PDFCraftApp(
    viewModel: MainViewModel = viewModel()
) {
    val isOnboardingCompleted by viewModel.isOnboardingCompleted.collectAsState()
    val currentTab by viewModel.currentTab.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()
    val activeViewerFile by viewModel.activeViewerFile.collectAsState()
    val selectedMetadata by viewModel.selectedMetadata.collectAsState()
    val isLoadingMetadata by viewModel.isLoadingMetadata.collectAsState()
    val showPoliteAdDialog by viewModel.showPoliteAdDialog.collectAsState()

    var showInfoDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(userMessage) {
        userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserMessage()
        }
    }

    if (!isOnboardingCompleted) {
        OnboardingScreen(
            onFinishOnboarding = { viewModel.completeOnboarding() }
        )
        return
    }

    val isWideScreen = LocalConfiguration.current.screenWidthDp >= 600

    val activeTool by viewModel.activeTool.collectAsState()

    BackHandler(enabled = activeTool != null) {
        viewModel.setActiveTool(null)
    }

    @Composable
    fun MainContentArea(modifier: Modifier = Modifier) {
        Box(
            modifier = modifier.fillMaxSize()
        ) {
            if (activeTool != null) {
                when (activeTool) {
                    PdfOperationType.MERGE -> MergeScreen(viewModel = viewModel)
                    PdfOperationType.SPLIT -> SplitScreen(viewModel = viewModel)
                    PdfOperationType.COMPRESS -> CompressScreen(viewModel = viewModel)
                    PdfOperationType.METADATA -> MetadataScreen(viewModel = viewModel)
                    PdfOperationType.ENCRYPT -> EncryptScreen(viewModel = viewModel)
                    PdfOperationType.WATERMARK -> WatermarkScreen(viewModel = viewModel)
                    PdfOperationType.SIGN -> SignScreen(viewModel = viewModel)
                    PdfOperationType.ROTATE -> RotateScreen(viewModel = viewModel, onNavigateBack = { viewModel.setActiveTool(null) })
                    else -> {}
                }
            } else {
                Crossfade(
                    targetState = currentTab,
                    label = "tabCrossfade"
                ) { tab ->
                    when (tab) {
                        0 -> HubScreen(viewModel = viewModel)
                        1 -> ToolsScreen(viewModel = viewModel)
                        2 -> LibraryScreen(viewModel = viewModel)
                        3 -> SettingsScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }

    if (isWideScreen) {
        Row(modifier = Modifier.fillMaxSize()) {
            AppNavigationRail(
                selectedTab = currentTab,
                onTabSelected = { viewModel.selectTab(it) }
            )
            Scaffold(
                modifier = Modifier.weight(1f),
                topBar = {
                    AppTopBar(
                        onOpenInfoDialog = { showInfoDialog = true },
                        onOpenSettingsDialog = { showSettingsDialog = true }
                    )
                },
                bottomBar = {
                    PoliteAdBanner()
                },
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { innerPadding ->
                MainContentArea(modifier = Modifier.padding(innerPadding))
            }
        }
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                AppTopBar(
                    onOpenInfoDialog = { showInfoDialog = true },
                    onOpenSettingsDialog = { showSettingsDialog = true }
                )
            },
            bottomBar = {
                Column {
                    PoliteAdBanner()
                    AppBottomNav(
                        selectedTab = currentTab,
                        onTabSelected = { viewModel.selectTab(it) }
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            MainContentArea(modifier = Modifier.padding(innerPadding))
        }
    }

    // Dialogs
    if (showSettingsDialog) {
        SettingsDialog(
            viewModel = viewModel,
            onDismiss = { showSettingsDialog = false }
        )
    }

    if (showInfoDialog) {
        InfoPrivacyDialog(
            onDismiss = { showInfoDialog = false },
            onReplayOnboarding = { viewModel.resetOnboardingForTesting() }
        )
    }

    if (showPoliteAdDialog) {
        PoliteAdDialog(
            onDismiss = { viewModel.dismissPoliteAdDialog() }
        )
    }

    activeViewerFile?.let { file ->
        PdfViewerDialog(
            file = file,
            onDismiss = { viewModel.closeViewer() }
        )
    }

    if (selectedMetadata != null || isLoadingMetadata) {
        PdfMetadataDialog(
            metadata = selectedMetadata,
            isLoading = isLoadingMetadata,
            onDismiss = { viewModel.clearSelectedMetadata() },
            onOpenInViewer = { filePath ->
                viewModel.openInViewer(filePath)
            }
        )
    }
}


