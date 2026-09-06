package com.example.patch

import com.example.parser.PatchManifest
import com.example.parser.PatchManifestParser
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object ZipProcessor {

    @Throws(Exception::class)
    fun readPatchManifest(patchZipFile: File): PatchManifest {
        if (!patchZipFile.exists() || !patchZipFile.canRead()) {
            throw IllegalArgumentException("Invalid patch ZIP.")
        }

        try {
            ZipFile(patchZipFile).use { zip ->
                val manifestEntry = zip.getEntry("patch_manifest.json")
                    ?: throw IllegalArgumentException("patch_manifest.json not found.")

                val content = zip.getInputStream(manifestEntry).use { input ->
                    input.bufferedReader(Charsets.UTF_8).readText()
                }
                return PatchManifestParser.parse(content)
            }
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid patch manifest.", e)
        }
    }

    @Throws(Exception::class)
    fun getInjectableEntries(patchZipFile: File): List<String> {
        val injectEntries = mutableListOf<String>()
        ZipFile(patchZipFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (!entry.isDirectory && entry.name.startsWith("inject/") && entry.name.length > 7) {
                    injectEntries.add(entry.name)
                }
            }
        }
        if (injectEntries.isEmpty()) {
            throw IllegalArgumentException("No injectable patch files found.")
        }
        return injectEntries
    }

    @Throws(Exception::class)
    fun injectPatchFiles(
        originalApk: File,
        patchZip: File,
        outputApk: File,
        replaceFiles: Boolean
    ) {
        if (!originalApk.exists()) {
            throw IllegalArgumentException("Invalid APK file.")
        }
        if (!patchZip.exists()) {
            throw IllegalArgumentException("Invalid patch ZIP.")
        }

        val patchZipFile = ZipFile(patchZip)
        val origZipFile = ZipFile(originalApk)

        try {
            // Map targetPath -> inject entry
            val injectedTargetMap = mutableMapOf<String, ZipEntry>()
            val patchEntries = patchZipFile.entries()
            while (patchEntries.hasMoreElements()) {
                val entry = patchEntries.nextElement()
                if (!entry.isDirectory && entry.name.startsWith("inject/") && entry.name.length > 7) {
                    val targetPath = entry.name.removePrefix("inject/")
                    injectedTargetMap[targetPath] = entry
                }
            }

            if (injectedTargetMap.isEmpty()) {
                throw IllegalArgumentException("No injectable patch files found.")
            }

            if (outputApk.exists()) {
                outputApk.delete()
            }

            val buffer = ByteArray(64 * 1024)

            ZipOutputStream(BufferedOutputStream(FileOutputStream(outputApk))).use { zos ->
                // Iterate through original APK entries
                val origEntries = origZipFile.entries()
                while (origEntries.hasMoreElements()) {
                    val origEntry = origEntries.nextElement()
                    val entryName = origEntry.name

                    // Skip existing Android APK signatures in META-INF to allow clean re-signing
                    if (isSignatureFile(entryName)) {
                        continue
                    }

                    // Check if this entry is targeted for replacement
                    if (injectedTargetMap.containsKey(entryName)) {
                        if (replaceFiles) {
                            // Skip original entry; will be replaced by patch
                            continue
                        }
                    }

                    // Copy original entry
                    copyZipEntry(origZipFile, origEntry, zos, buffer)
                }

                // Inject all patch files
                for ((targetPath, patchEntry) in injectedTargetMap) {
                    val newEntry = ZipEntry(targetPath)
                    newEntry.time = System.currentTimeMillis()
                    zos.putNextEntry(newEntry)
                    patchZipFile.getInputStream(patchEntry).use { input ->
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            zos.write(buffer, 0, bytesRead)
                        }
                    }
                    zos.closeEntry()
                }

                zos.finish()
            }
        } catch (e: Exception) {
            if (outputApk.exists()) {
                outputApk.delete()
            }
            throw e
        } finally {
            origZipFile.close()
            patchZipFile.close()
        }
    }

    private fun isSignatureFile(name: String): Boolean {
        if (!name.startsWith("META-INF/")) return false
        val upper = name.uppercase()
        return upper == "META-INF/MANIFEST.MF" ||
                upper.endsWith(".SF") ||
                upper.endsWith(".RSA") ||
                upper.endsWith(".DSA") ||
                upper.endsWith(".EC")
    }

    private fun copyZipEntry(
        sourceZip: ZipFile,
        entry: ZipEntry,
        targetZos: ZipOutputStream,
        buffer: ByteArray
    ) {
        val newEntry = ZipEntry(entry.name)
        if (entry.method == ZipEntry.STORED) {
            newEntry.method = ZipEntry.STORED
            newEntry.size = entry.size
            newEntry.compressedSize = entry.compressedSize
            newEntry.crc = entry.crc
        }
        newEntry.time = entry.time
        newEntry.extra = entry.extra
        newEntry.comment = entry.comment

        targetZos.putNextEntry(newEntry)
        sourceZip.getInputStream(entry).use { input ->
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                targetZos.write(buffer, 0, bytesRead)
            }
        }
        targetZos.closeEntry()
    }
}
