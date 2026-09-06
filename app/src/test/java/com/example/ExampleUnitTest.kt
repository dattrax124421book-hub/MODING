package com.example

import com.android.apksig.ApkSigner
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.security.KeyFactory
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec

class ExampleUnitTest {
  @Test
  fun testSigningKeysAndApkSig() {
    val possiblePk8Paths = listOf(
      File("app/src/main/assets/testkey.pk8"),
      File("src/main/assets/testkey.pk8"),
      File("../app/src/main/assets/testkey.pk8")
    )
    val pk8File = possiblePk8Paths.firstOrNull { it.exists() }
    assertNotNull("pk8 file must exist in assets", pk8File)

    val possiblePemPaths = listOf(
      File("app/src/main/assets/testkey.x509.pem"),
      File("src/main/assets/testkey.x509.pem"),
      File("../app/src/main/assets/testkey.x509.pem")
    )
    val pemFile = possiblePemPaths.firstOrNull { it.exists() }
    assertNotNull("pem file must exist in assets", pemFile)

    val keyBytes = pk8File!!.readBytes()
    val keySpec = PKCS8EncodedKeySpec(keyBytes)
    val keyFactory = KeyFactory.getInstance("RSA")
    val privateKey = keyFactory.generatePrivate(keySpec)
    assertNotNull("Private key must be parsed", privateKey)

    val certFactory = CertificateFactory.getInstance("X.509")
    val certs = FileInputStream(pemFile!!).use { input ->
      certFactory.generateCertificates(input).filterIsInstance<X509Certificate>()
    }
    assertTrue("At least one certificate must be parsed", certs.isNotEmpty())

    val signerConfig = ApkSigner.SignerConfig.Builder(
      "testkey",
      privateKey,
      certs
    ).build()
    assertNotNull("SignerConfig must be created", signerConfig)
  }
}

