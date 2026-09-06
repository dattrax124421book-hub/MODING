package com.example.signing

import android.content.Context
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec

class SigningKeyProvider(private val context: Context) {
    fun getPrivateKey(): PrivateKey {
        return context.assets.open("testkey.pk8").use { input ->
            val keyBytes = input.readBytes()
            val keySpec = PKCS8EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            keyFactory.generatePrivate(keySpec)
        }
    }

    fun getCertificates(): List<X509Certificate> {
        val certFactory = CertificateFactory.getInstance("X.509")
        val certs = context.assets.open("testkey.x509.pem").use { input ->
            certFactory.generateCertificates(input).filterIsInstance<X509Certificate>()
        }
        if (certs.isEmpty()) {
            throw IllegalStateException("No X.509 certificates found in testkey.x509.pem")
        }
        return certs
    }
}
