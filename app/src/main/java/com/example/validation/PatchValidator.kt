package com.example.validation

import com.example.parser.ApkInfo
import com.example.parser.PatchManifest

sealed class ValidationResult {
    object Compatible : ValidationResult()
    data class Incompatible(val message: String) : ValidationResult()
    object Incomplete : ValidationResult()
}

object PatchValidator {
    fun validate(apkInfo: ApkInfo?, manifest: PatchManifest?): ValidationResult {
        if (apkInfo == null || manifest == null) {
            return ValidationResult.Incomplete
        }

        return if (apkInfo.packageName == manifest.targetPackage) {
            ValidationResult.Compatible
        } else {
            val patchAppDesc = if (manifest.appName.isNotBlank()) manifest.appName else manifest.targetPackage
            val apkAppDesc = if (apkInfo.appName.isNotBlank()) apkInfo.appName else apkInfo.packageName
            ValidationResult.Incompatible(
                "Selected patch is for $patchAppDesc, but selected APK is $apkAppDesc!"
            )
        }
    }
}
