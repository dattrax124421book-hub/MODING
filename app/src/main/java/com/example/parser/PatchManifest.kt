package com.example.parser

import org.json.JSONObject

data class PatchRules(
    val replaceFiles: Boolean = true,
    val signV2: Boolean = true,
    val signV3: Boolean = true
)

data class PatchManifest(
    val modEngineVersion: String,
    val targetPackage: String,
    val appName: String,
    val appliedPatches: List<String>,
    val rules: PatchRules
)

object PatchManifestParser {
    @Throws(Exception::class)
    fun parse(jsonString: String): PatchManifest {
        val json = JSONObject(jsonString)
        val modEngineVersion = json.optString("modEngineVersion", "1.0")
        if (!json.has("targetPackage")) {
            throw IllegalArgumentException("Missing targetPackage in patch manifest")
        }
        val targetPackage = json.getString("targetPackage")
        val appName = json.optString("appName", "Unknown Application")

        val appliedPatchesList = mutableListOf<String>()
        val patchesArray = json.optJSONArray("appliedPatches")
        if (patchesArray != null) {
            for (i in 0 until patchesArray.length()) {
                appliedPatchesList.add(patchesArray.getString(i))
            }
        }

        val rulesObj = json.optJSONObject("rules")
        val rules = if (rulesObj != null) {
            PatchRules(
                replaceFiles = rulesObj.optBoolean("replaceFiles", true),
                signV2 = rulesObj.optBoolean("signV2", true),
                signV3 = rulesObj.optBoolean("signV3", true)
            )
        } else {
            PatchRules()
        }

        return PatchManifest(
            modEngineVersion = modEngineVersion,
            targetPackage = targetPackage,
            appName = appName,
            appliedPatches = appliedPatchesList,
            rules = rules
        )
    }
}
