package com.example.patch

import android.content.Context
import com.example.installer.ApkInstaller
import com.example.parser.ApkInfo
import com.example.parser.PatchManifest
import com.example.signing.ApkSignerWrapper
import com.example.signing.SigningKeyProvider
import com.example.storage.FileManager
import com.example.validation.PatchValidator
import com.example.validation.ValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

data class PatchProgress(
    val percentage: Float, // 0.0f to 1.0f
    val message: String
)

sealed class PatchState {
    object Idle : PatchState()
    data class Processing(val progress: PatchProgress) : PatchState()
    data class Success(val finalApkFile: File, val message: String) : PatchState()
    data class Error(val message: String) : PatchState()
}

class ApkPatcher(private val context: Context) {

    private val keyProvider = SigningKeyProvider(context)
    private val signerWrapper = ApkSignerWrapper(keyProvider)

    suspend fun runPatchPipeline(
        apkInfo: ApkInfo,
        patchManifest: PatchManifest,
        patchZipFile: File,
        onProgress: (PatchProgress) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val originalApkFile = apkInfo.file
        val cacheDir = context.cacheDir

        // 1. Validation
        val validation = PatchValidator.validate(apkInfo, patchManifest)
        if (validation is ValidationResult.Incompatible) {
            return@withContext Result.failure(IllegalArgumentException(validation.message))
        }

        // 2. Check storage space (estimate 3x APK size for working temp files)
        val requiredBytes = originalApkFile.length() * 3
        if (!FileManager.checkStorageAvailable(cacheDir, requiredBytes)) {
            return@withContext Result.failure(IllegalStateException("Insufficient free storage on device to complete patching."))
        }

        val tempInjectedApk = File(cacheDir, "temp_working_${System.currentTimeMillis()}_unaligned.apk")
        val tempAlignedApk = File(cacheDir, "temp_working_${System.currentTimeMillis()}_aligned.apk")
        val tempSignedApk = File(cacheDir, "temp_working_${System.currentTimeMillis()}_signed.apk")

        try {
            // Stage 1: Extract recipe (20%)
            onProgress(PatchProgress(0.20f, "Extracting recipe... (20%)"))
            delay(150)
            ZipProcessor.getInjectableEntries(patchZipFile)

            // Stage 2: Injecting patch files into archive (55%)
            onProgress(PatchProgress(0.55f, "Injecting patch files into archive... (55%)"))
            try {
                ZipProcessor.injectPatchFiles(
                    originalApk = originalApkFile,
                    patchZip = patchZipFile,
                    outputApk = tempInjectedApk,
                    replaceFiles = patchManifest.rules.replaceFiles
                )
            } catch (e: Exception) {
                throw IllegalStateException("Failed to inject patch files into APK.", e)
            }

            // Stage 3: Aligning 4-byte boundaries (Zipalign) (75%)
            onProgress(PatchProgress(0.75f, "Aligning 4-byte boundaries (Zipalign)... (75%)"))
            try {
                ZipalignUtils.align(tempInjectedApk, tempAlignedApk, alignment = 4)
            } catch (e: Exception) {
                throw IllegalStateException("APK alignment failed.", e)
            }

            // Stage 4: Signing APK with v2/v3 signatures (90%)
            onProgress(PatchProgress(0.90f, "Signing APK with v2/v3 signatures... (90%)"))
            try {
                signerWrapper.sign(
                    inputApk = tempAlignedApk,
                    outputApk = tempSignedApk,
                    rules = patchManifest.rules
                )
            } catch (e: Exception) {
                throw IllegalStateException("APK signing failed.", e)
            }

            // Stage 5: Save final APK to public Download folder & Done (100%)
            val outputFileName = FileManager.getOutputFileName(apkInfo.fileName, apkInfo.packageName)
            val finalSavedApk = try {
                FileManager.saveToDownloads(context, tempSignedApk, outputFileName)
            } catch (e: Exception) {
                throw IllegalStateException("Unable to save the generated APK.", e)
            }

            onProgress(PatchProgress(1.00f, "Done! (100%)"))

            // Trigger native installer within approximately one second
            delay(800)
            withContext(Dispatchers.Main) {
                try {
                    if (ApkInstaller.canInstallApks(context)) {
                        ApkInstaller.installApk(context, finalSavedApk)
                    }
                } catch (ignored: Exception) {
                }
            }

            Result.success(finalSavedApk)
        } catch (e: Exception) {
            val userMsg = when {
                e.message != null && e.message!!.startsWith("Selected patch is for") -> e.message!!
                e.message != null && (e.message!!.contains("failed") || e.message!!.contains("found") || e.message!!.contains("Invalid") || e.message!!.contains("Unable")) -> e.message!!
                else -> e.message ?: "An unexpected error occurred during APK patching."
            }
            Result.failure(Exception(userMsg, e))
        } finally {
            // Clean working temporary files
            if (tempInjectedApk.exists()) tempInjectedApk.delete()
            if (tempAlignedApk.exists()) tempAlignedApk.delete()
            if (tempSignedApk.exists()) tempSignedApk.delete()
        }
    }
}
