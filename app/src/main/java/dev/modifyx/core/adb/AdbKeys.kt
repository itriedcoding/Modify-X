package dev.modifyx.core.adb

import android.content.Context
import java.io.File
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * The key pair Modify-X presents to adbd. adbd asks the user to allow it once; after that
 * the key stays authorised and commands run as the shell user.
 */
class AdbKeys(context: Context) {

    private val directory = File(context.filesDir, "adb-keys")
    private val privateFile = File(directory, "private.pk8")
    private val publicFile = File(directory, "public.der")

    @Volatile
    private var cached: KeyPair? = null

    @Synchronized
    fun keyPair(): KeyPair {
        cached?.let { return it }
        val loaded = load()
        val pair = loaded ?: generate()
        cached = pair
        return pair
    }

    /** adbd verifies PKCS#1 v1.5 RSA signatures over its 20-byte token. */
    fun sign(token: ByteArray): ByteArray {
        val signature = Signature.getInstance("SHA1withRSA")
        signature.initSign(keyPair().private)
        signature.update(token)
        return signature.sign()
    }

    fun publicKeyDer(): ByteArray = keyPair().public.encoded

    /** Shown in the app so the fingerprint on adbd's dialog can be compared. */
    fun fingerprint(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(publicKeyDer())
        return digest.joinToString(":") { "%02X".format(it) }
    }

    fun reset() {
        synchronized(this) {
            privateFile.delete()
            publicFile.delete()
            cached = null
        }
    }

    private fun load(): KeyPair? = runCatching {
        if (!privateFile.isFile || !publicFile.isFile) return null
        val factory = KeyFactory.getInstance("RSA")
        val private = factory.generatePrivate(PKCS8EncodedKeySpec(privateFile.readBytes()))
        val public = factory.generatePublic(X509EncodedKeySpec(publicFile.readBytes()))
        KeyPair(public, private)
    }.getOrNull()

    private fun generate(): KeyPair {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        val pair = generator.generateKeyPair()
        runCatching {
            directory.mkdirs()
            privateFile.writeBytes(pair.private.encoded)
            publicFile.writeBytes(pair.public.encoded)
        }
        return pair
    }

    /** Convenience for adb's own public-key line format, useful in diagnostics. */
    fun publicKeyBase64(): String = Base64.getEncoder().encodeToString(publicKeyDer())
}
