package com.example.parser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import java.io.File

data class ApkInfo(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val fileName: String,
    val iconBitmap: Bitmap? = null,
    val file: File
)

object ApkInfoParser {
    @Throws(Exception::class)
    fun parse(context: Context, apkFile: File): ApkInfo {
        if (!apkFile.exists() || !apkFile.canRead()) {
            throw IllegalArgumentException("Invalid APK file.")
        }

        val pm = context.packageManager
        val packageInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
            ?: throw IllegalArgumentException("Invalid APK file.")

        val appInfo = packageInfo.applicationInfo
            ?: throw IllegalArgumentException("Invalid APK file.")

        appInfo.sourceDir = apkFile.absolutePath
        appInfo.publicSourceDir = apkFile.absolutePath

        val appName = try {
            val label = pm.getApplicationLabel(appInfo).toString()
            if (label.isNotBlank() && label != appInfo.packageName) label else packageInfo.packageName
        } catch (e: Exception) {
            packageInfo.packageName
        }

        val iconBitmap = try {
            val drawable = pm.getApplicationIcon(appInfo)
            drawableToBitmap(drawable)
        } catch (e: Exception) {
            null
        }

        val versionName = packageInfo.versionName ?: "1.0"

        return ApkInfo(
            appName = appName,
            packageName = packageInfo.packageName,
            versionName = versionName,
            fileName = apkFile.name,
            iconBitmap = iconBitmap,
            file = apkFile
        )
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap? {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }

        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
