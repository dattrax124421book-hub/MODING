package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.installer.ApkInstaller
import com.example.patch.PatchState
import com.example.ui.theme.CyberTeal
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.validation.ValidationResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val apkInfo by viewModel.apkInfo.collectAsStateWithLifecycle()
    val patchManifest by viewModel.patchManifest.collectAsStateWithLifecycle()
    val patchFileName by viewModel.patchFileName.collectAsStateWithLifecycle()
    val validationResult by viewModel.validationResult.collectAsStateWithLifecycle()
    val patchState by viewModel.patchState.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    var showPermissionDialog by remember { mutableStateOf(false) }

    // APK File Picker Launcher
    val apkPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.selectApkUri(context, it) }
    }

    // Patch ZIP Picker Launcher
    val patchPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.selectPatchZipUri(context, it) }
    }

    // Permission check
    fun checkPermissionsAndProceed(action: () -> Unit) {
        val hasStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
        val canInstall = ApkInstaller.canInstallApks(context)

        if (!hasStorage || !canInstall) {
            showPermissionDialog = true
        } else {
            action()
        }
    }

    // Status label & color
    val (statusText, statusColor) = when (val state = patchState) {
        is PatchState.Processing -> Pair("Patching...", CyberTeal)
        is PatchState.Success -> Pair("Completed", SuccessGreen)
        is PatchState.Error -> Pair("Failed", ErrorRed)
        is PatchState.Idle -> {
            when (validationResult) {
                is ValidationResult.Compatible -> Pair("Compatible", SuccessGreen)
                is ValidationResult.Incompatible -> Pair("Incompatible", ErrorRed)
                is ValidationResult.Incomplete -> Pair("Ready", MaterialTheme.colorScheme.primary)
            }
        }
    }

    val isPatching = patchState is PatchState.Processing
    val isCompatible = validationResult is ValidationResult.Compatible

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "APK Mod Injector",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    IconButton(
                        onClick = { showPermissionDialog = true },
                        modifier = Modifier.testTag("permissions_action_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Permissions",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with status indicator (Section 5)
            AppHeader(
                statusText = statusText,
                statusColor = statusColor
            )

            // Section 6: Original APK Card
            OriginalApkCard(
                apkInfo = apkInfo,
                onSelectApkClicked = {
                    apkPickerLauncher.launch("application/vnd.android.package-archive")
                }
            )

            // Section 7: Mod Patch ZIP Card
            ModPatchCard(
                manifest = patchManifest,
                patchFileName = patchFileName,
                onSelectPatchClicked = {
                    patchPickerLauncher.launch("application/zip")
                }
            )

            // Section 8: Compatibility Validation Card
            val warningMsg = (validationResult as? ValidationResult.Incompatible)?.message
            CompatibilityStatusCard(
                isCompatible = isCompatible,
                warningMessage = warningMsg
            )

            // Section 18: Progress UI during building
            AnimatedVisibility(
                visible = isPatching,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                if (patchState is PatchState.Processing) {
                    val progress = (patchState as PatchState.Processing).progress
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("progress_card"),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = progress.message,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${(progress.percentage * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { progress.percentage },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surface
                            )
                        }
                    }
                }
            }

            // Success Card with Install trigger (Section 19 & 22)
            AnimatedVisibility(
                visible = patchState is PatchState.Success,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                if (patchState is PatchState.Success) {
                    val success = patchState as PatchState.Success
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("success_card"),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = SuccessGreen.copy(alpha = 0.15f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SuccessGreen)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = SuccessGreen,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "APK Modded & Signed Successfully!",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = SuccessGreen
                                    )
                                    Text(
                                        text = success.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    try {
                                        ApkInstaller.installApk(context, success.finalApkFile)
                                    } catch (e: Exception) {
                                        viewModel.clearError()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("install_now_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                            ) {
                                Icon(imageVector = Icons.Default.InstallMobile, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Install Modded APK",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Error Card
            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                if (errorMessage != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("error_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.12f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = ErrorRed,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = errorMessage ?: "Unknown error",
                                style = MaterialTheme.typography.bodySmall,
                                color = ErrorRed,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.clearError() }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = ErrorRed,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Primary Action Button: 🚀 Inject Mods & Build APK (Section 8)
            Button(
                onClick = {
                    checkPermissionsAndProceed {
                        viewModel.startPatching(context)
                    }
                },
                enabled = isCompatible && !isPatching,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("inject_and_build_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                if (isPatching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Patching APK...",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                } else {
                    Text(
                        text = "🚀 Inject Mods & Build APK",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            // Sample / Quick Test Helper
            OutlinedButton(
                onClick = {
                    viewModel.loadSampleData(context)
                },
                enabled = !isPatching,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("load_sample_button"),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            ) {
                Icon(
                    imageVector = Icons.Default.Science,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Load Sample Mod & APK (Hill Climb Racing)",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Permission Dialog (Section 2)
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "App Permissions Required")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Storage Access
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Storage Access Required",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "«APK ko patch karne aur Downloads folder mein save karne ke liye permission enable karein.»",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            FilledTonalButton(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        try {
                                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                                data = Uri.parse("package:${context.packageName}")
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            val fallback = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                            context.startActivity(fallback)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Enable Storage Access")
                            }
                        }
                    }

                    // Install Unknown Apps
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Install Unknown Apps",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "«Modded APK install karne ke liye permission allow karein.»",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            FilledTonalButton(
                                onClick = {
                                    ApkInstaller.openInstallPermissionSettings(context)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Allow Install Unknown Apps")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("Done")
                }
            }
        )
    }
}
