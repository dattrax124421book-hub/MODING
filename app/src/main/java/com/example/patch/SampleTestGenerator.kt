package com.example.patch

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object SampleTestGenerator {

    @Throws(Exception::class)
    fun createSampleFiles(context: Context): Pair<File, File> {
        val cacheDir = context.cacheDir

        // 1. Create a valid test APK for com.fingersoft.hillclimb
        val sampleApk = File(cacheDir, "hill_climb_v1.60.apk")
        if (sampleApk.exists()) sampleApk.delete()

        // Generate binary AndroidManifest or use self APK as base if available
        val currentApk = File(context.applicationInfo.sourceDir)
        if (currentApk.exists()) {
            currentApk.copyTo(sampleApk, overwrite = true)
        } else {
            ZipOutputStream(FileOutputStream(sampleApk)).use { zos ->
                zos.putNextEntry(ZipEntry("assets/game_settings.json"))
                zos.write("{\"coins\": 100, \"ads\": true}".toByteArray())
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("classes.dex"))
                zos.write("dex\n035\u0000dummy_dex_bytes".toByteArray())
                zos.closeEntry()
            }
        }

        // 2. Create the matching sample patch ZIP
        val samplePatchZip = File(cacheDir, "hill_climb_mod_patch.zip")
        if (samplePatchZip.exists()) samplePatchZip.delete()

        // Determine target package to match base APK
        val targetPackage = try {
            val pi = context.packageManager.getPackageArchiveInfo(sampleApk.absolutePath, 0)
            pi?.packageName ?: "com.fingersoft.hillclimb"
        } catch (e: Exception) {
            "com.fingersoft.hillclimb"
        }

        val appName = if (targetPackage == context.packageName) "APK Mod Injector" else "Hill Climb Racing"

        val manifestJson = """
        {
          "modEngineVersion": "2.4",
          "targetPackage": "$targetPackage",
          "appName": "$appName",
          "appliedPatches": [
            "Unlimited Coins",
            "No Ads",
            "God Mode"
          ],
          "rules": {
            "replaceFiles": true,
            "signV2": true,
            "signV3": true
          }
        }
        """.trimIndent()

        ZipOutputStream(FileOutputStream(samplePatchZip)).use { zos ->
            // patch_manifest.json
            zos.putNextEntry(ZipEntry("patch_manifest.json"))
            zos.write(manifestJson.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // inject/assets/mod_engine_config.json
            zos.putNextEntry(ZipEntry("inject/assets/mod_engine_config.json"))
            zos.write("{\"modEngine\": \"2.4\", \"active\": true}".toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // inject/assets/game_settings.json
            zos.putNextEntry(ZipEntry("inject/assets/game_settings.json"))
            zos.write("{\"coins\": 99999999, \"ads\": false, \"godMode\": true}".toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // inject/lib/arm64-v8a/libil2cpp.so
            zos.putNextEntry(ZipEntry("inject/lib/arm64-v8a/libil2cpp.so"))
            zos.write("ELF_MODDED_IL2CPP_LIBRARY".toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // inject/classes.dex
            zos.putNextEntry(ZipEntry("inject/classes.dex"))
            zos.write("MODDED_CLASSES_DEX".toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        return Pair(sampleApk, samplePatchZip)
    }
}
