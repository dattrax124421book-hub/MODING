package com.example.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object FileManager {

    fun getOutputFileName(originalFileName: String?, packageName: String): String {
        return if (!originalFileName.isNullOrBlank() && originalFileName.endsWith(".apk", ignoreCase = true)) {
            val baseName = originalFileName.substringBeforeLast(".apk")
            "${baseName}_MODDED.apk"
        } else if (!packageName.isBlank()) {
            "${packageName}_MODDED.apk"
        } else {
            "app_MODDED.apk"
        }
    }

    @Throws(Exception::class)
    fun copyUriToTemp(context: Context, uri: Uri, prefix: String, extension: String): File {
        val tempFile = File(context.cacheDir, "temp_${prefix}.${extension}")
        if (tempFile.exists()) {
            tempFile.delete()
        }

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
                output.flush()
            }
        } ?: throw IllegalArgumentException("Cannot open selected file stream.")

        return tempFile
    }

    fun getFileNameFromUri(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") {
            return uri.lastPathSegment
        }
        var name: String? = null
        try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        name = it.getString(nameIndex)
                    }
                }
            }
        } catch (ignored: Exception) {
        }
        return name ?: uri.lastPathSegment
    }

    fun checkStorageAvailable(targetDir: File, requiredBytes: Long): Boolean {
        return targetDir.usableSpace >= requiredBytes
    }

    @Throws(Exception::class)
    fun saveToDownloads(context: Context, sourceFile: File, outputFileName: String): File {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }

        val destinationFile = File(downloadDir, outputFileName)
        if (destinationFile.exists()) {
            destinationFile.delete()
        }

        try {
            // Write directly to Download directory
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(destinationFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                    output.flush()
                }
            }

            if (!destinationFile.exists() || destinationFile.length() == 0L) {
                throw IllegalStateException("Generated APK is empty or missing after save.")
            }

            // Also register in MediaStore if Android 10+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, outputFileName)
                        put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                } catch (ignored: Exception) {
                }
            }

            return destinationFile
        } catch (e: Exception) {
            // If direct external storage write had permission restriction, try fallback to externalFilesDir or cache
            val fallbackFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir, outputFileName)
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(fallbackFile).use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            }
            if (fallbackFile.exists() && fallbackFile.length() > 0L) {
                return fallbackFile
            }
            throw IllegalStateException("Unable to save the generated APK.", e)
        }
    }

    fun getFileProviderUri(context: Context, file: File): Uri {
        val authority = "${context.packageName}.provider"
        return FileProvider.getUriForFile(context, authority, file)
    }

    fun cleanTempFiles(context: Context) {
        try {
            context.cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("temp_") || file.name.startsWith("input_")) {
                    file.delete()
                }
            }
        } catch (ignored: Exception) {
        }
    }
}
