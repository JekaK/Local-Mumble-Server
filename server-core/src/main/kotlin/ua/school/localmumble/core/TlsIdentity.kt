package ua.school.localmumble.core

import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.security.auth.x500.X500Principal

/** One self-signed RSA identity per installation; platform TLS handles all handshakes. */
internal object TlsIdentity {
    fun context(directory: File): SSLContext {
        require(directory.isDirectory || directory.mkdirs()) { "Cannot create identity directory" }
        val keyFile = File(directory, "identity-key.pk8")
        val certFile = File(directory, "identity-cert.der")
        if (!keyFile.isFile || !certFile.isFile) generate(keyFile, certFile)
        require(keyFile.length() in 1..16384 && certFile.length() in 1..16384) { "Invalid TLS identity size" }
        val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(certFile.inputStream().use { it.readBytes().inputStream() }) as X509Certificate
        certificate.verify(certificate.publicKey)
        val challenge = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(key); signature.update(challenge)
        val signed = signature.sign()
        signature.initVerify(certificate.publicKey); signature.update(challenge)
        require(signature.verify(signed)) { "TLS key and certificate do not match" }
        val store = KeyStore.getInstance(KeyStore.getDefaultType())
        store.load(null, null)
        val password = CharArray(0)
        store.setKeyEntry("server", key, password, arrayOf(certificate))
        val managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        managers.init(store, password)
        return SSLContext.getInstance("TLS").apply { init(managers.keyManagers, null, SecureRandom()) }
    }

    private fun generate(keyFile: File, certFile: File) {
        val random = SecureRandom()
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048, random) }.generateKeyPair()
        val algorithm = seq(oid(0x2a, 0x86, 0x48, 0x86, 0xf7, 0x0d, 1, 1, 11), der(5, byteArrayOf()))
        val name = X500Principal("CN=Local Mumble Server").encoded
        val now = System.currentTimeMillis()
        val validity = seq(time(Date(now - 86400000L)), time(Date(now + 10L * 365 * 86400000)))
        val extensions = der(0xa3, seq(
            seq(oid(0x55, 0x1d, 0x13), der(4, seq())),
            seq(oid(0x55, 0x1d, 0x0f), der(4, der(3, byteArrayOf(5, 0xa0.toByte())))),
            seq(oid(0x55, 0x1d, 0x25), der(4, seq(oid(0x2b, 6, 1, 5, 5, 7, 3, 1)))),
            seq(oid(0x55, 0x1d, 0x11), der(4, seq(
                der(0x82, "localhost".toByteArray(Charsets.US_ASCII)), der(0x87, byteArrayOf(127, 0, 0, 1))
            )))
        ))
        val body = seq(der(0xa0, der(2, byteArrayOf(2))),
            der(2, BigInteger(128, random).add(BigInteger.ONE).toByteArray()),
            algorithm, name, validity, name, pair.public.encoded, extensions)
        val signature = Signature.getInstance("SHA256withRSA").apply { initSign(pair.private); update(body) }.sign()
        val certificate = seq(body, algorithm, der(3, byteArrayOf(0) + signature))
        CertificateFactory.getInstance("X.509").generateCertificate(certificate.inputStream())
        privateWrite(keyFile, pair.private.encoded)
        privateWrite(certFile, certificate)
    }
    private fun privateWrite(file: File, data: ByteArray) {
        val pending = File(file.parentFile, file.name + ".tmp")
        pending.outputStream().use { it.write(data); it.fd.sync() }
        pending.setReadable(false, false); pending.setReadable(true, true)
        pending.setWritable(false, false); pending.setWritable(true, true)
        check(pending.renameTo(file)) { "Cannot persist TLS identity" }
    }
    private fun time(value: Date) = der(0x18, SimpleDateFormat("yyyyMMddHHmmss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(value).toByteArray(Charsets.US_ASCII))
    private fun oid(vararg value: Int) = der(6, value.map { it.toByte() }.toByteArray())
    private fun seq(vararg parts: ByteArray) = der(0x30, parts.fold(byteArrayOf()) { a, b -> a + b })
    private fun der(tag: Int, bytes: ByteArray): ByteArray {
        val size = bytes.size
        val length = when {
            size < 128 -> byteArrayOf(size.toByte())
            size < 256 -> byteArrayOf(0x81.toByte(), size.toByte())
            else -> byteArrayOf(0x82.toByte(), (size ushr 8).toByte(), size.toByte())
        }
        return byteArrayOf(tag.toByte()) + length + bytes
    }
}
