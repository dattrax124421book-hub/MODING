package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.parser.ApkInfo
import com.example.parser.ApkInfoParser
import com.example.parser.PatchManifest
import com.example.patch.ApkPatcher
import com.example.patch.PatchState
import com.example.patch.SampleTestGenerator
import com.example.patch.ZipProcessor
import com.example.storage.FileManager
import com.example.validation.PatchValidator
import com.example.validation.ValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _apkInfo = MutableStateFlow<ApkInfo?>(null)
    val apkInfo: StateFlow<ApkInfo?> = _apkInfo.asStateFlow()

    private val _patchManifest = MutableStateFlow<PatchManifest?>(null)
    val patchManifest: StateFlow<PatchManifest?> = _patchManifest.asStateFlow()

    private val _patchFileName = MutableStateFlow<String?>(null)
    val patchFileName: StateFlow<String?> = _patchFileName.asStateFlow()

    private val _patchZipFile = MutableStateFlow<File?>(null)
    val patchZipFile: StateFlow<File?> = _patchZipFile.asStateFlow()

    private val _validationResult = MutableStateFlow<ValidationResult>(ValidationResult.Incomplete)
    val validationResult: StateFlow<ValidationResult> = _validationResult.asStateFlow()

    private val _patchState = MutableStateFlow<PatchState>(PatchState.Idle)
    val patchState: StateFlow<PatchState> = _patchState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val apkPatcher = ApkPatcher(application.applicationContext)

    fun clearError() {
        _errorMessage.value = null
    }

    private fun revalidate() {
        _validationResult.value = PatchValidator.validate(_apkInfo.value, _patchManifest.value)
    }

    fun selectApkUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _patchState.value = PatchState.Idle
            try {
                val originalFileName = FileManager.getFileNameFromUri(context, uri) ?: "original.apk"
                val tempApk = withContext(Dispatchers.IO) {
                    FileManager.copyUriToTemp(context, uri, "original", "apk")
                }
                val info = withContext(Dispatchers.IO) {
                    ApkInfoParser.parse(context, tempApk).copy(fileName = originalFileName)
                }
                _apkInfo.value = info
                revalidate()
            } catch (e: Exception) {
                _apkInfo.value = null
                _errorMessage.value = e.message ?: "Invalid APK file."
                revalidate()
            }
        }
    }

    fun selectPatchZipUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _patchState.value = PatchState.Idle
            try {
                val displayName = FileManager.getFileNameFromUri(context, uri) ?: "patch.zip"
                val tempZip = withContext(Dispatchers.IO) {
                    FileManager.copyUriToTemp(context, uri, "patch", "zip")
                }
                val manifest = withContext(Dispatchers.IO) {
                    ZipProcessor.readPatchManifest(tempZip)
                }
                _patchFileName.value = displayName
                _patchZipFile.value = tempZip
                _patchManifest.value = manifest
                revalidate()
            } catch (e: Exception) {
                _patchManifest.value = null
                _patchZipFile.value = null
                _patchFileName.value = null
                _errorMessage.value = e.message ?: "Invalid patch ZIP."
                revalidate()
            }
        }
    }

    fun loadSampleData(context: Context) {
        viewModelScope.launch {
            _patchState.value = PatchState.Idle
            try {
                val (sampleApk, sampleZip) = withContext(Dispatchers.IO) {
                    SampleTestGenerator.createSampleFiles(context)
                }
                val info = withContext(Dispatchers.IO) {
                    ApkInfoParser.parse(context, sampleApk)
                }
                val manifest = withContext(Dispatchers.IO) {
                    ZipProcessor.readPatchManifest(sampleZip)
                }

                _apkInfo.value = info
                _patchFileName.value = sampleZip.name
                _patchZipFile.value = sampleZip
                _patchManifest.value = manifest
                revalidate()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load sample: ${e.message}"
            }
        }
    }

    fun startPatching(context: Context) {
        val currentApk = _apkInfo.value ?: return
        val currentManifest = _patchManifest.value ?: return
        val currentZip = _patchZipFile.value ?: return

        if (_validationResult.value !is ValidationResult.Compatible) {
            return
        }

        viewModelScope.launch {
            val result = apkPatcher.runPatchPipeline(
                apkInfo = currentApk,
                patchManifest = currentManifest,
                patchZipFile = currentZip,
                onProgress = { progress ->
                    _patchState.value = PatchState.Processing(progress)
                }
            )

            result.onSuccess { finalFile ->
                _patchState.value = PatchState.Success(
                    finalApkFile = finalFile,
                    message = "Saved to Downloads: ${finalFile.name}"
                )
            }.onFailure { error ->
                val msg = error.message ?: "Failed to patch and build APK."
                _patchState.value = PatchState.Error(msg)
                _errorMessage.value = msg
            }
        }
    }
}
