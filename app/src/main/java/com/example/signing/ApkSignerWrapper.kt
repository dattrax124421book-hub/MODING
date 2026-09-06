package com.example.signing

import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import com.example.parser.PatchRules
import java.io.File

class ApkSignerWrapper(private val keyProvider: SigningKeyProvider) {

    @Throws(Exception::class)
    fun sign(
        inputApk: File,
        outputApk: File,
        rules: PatchRules
    ) {
        val privateKey = keyProvider.getPrivateKey()
        val certificates = keyProvider.getCertificates()

        val signerConfig = ApkSigner.SignerConfig.Builder(
            "testkey",
            privateKey,
            certificates
        ).build()

        val builder = ApkSigner.Builder(listOf(signerConfig))
            .setInputApk(inputApk)
            .setOutputApk(outputApk)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(rules.signV2)
            .setV3SigningEnabled(rules.signV3)

        val signer = builder.build()
        signer.sign()

        // Verify signed APK
        val verifier = ApkVerifier.Builder(outputApk).build()
        val result = verifier.verify()
        if (!result.isVerified) {
            val errors = result.errors.joinToString { it.toString() }
            throw IllegalStateException("APK signing failed verification: $errors")
        }
    }
}
