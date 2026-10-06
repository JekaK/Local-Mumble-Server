// Copyright The Mumble Developers. All rights reserved.
// Kotlin adaptation: Local Mumble Server contributors, 2026.
// BSD-3-Clause; see licenses/MUMBLE-BSD-3-Clause.txt.
// Adapted from Mumble CryptStateOCB2.cpp, including XEX* counter-cryptanalysis.
package ua.school.localmumble.core

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/** Mumble legacy OCB2-AES128 transport. This is not a general-purpose crypto API. */
internal class Ocb2(key: ByteArray, encryptNonce: ByteArray, decryptNonce: ByteArray) {
    private val encrypt = Cipher.getInstance("AES/ECB/NoPadding").apply {
        init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.also { require(it.size == 16) }, "AES"))
    }
    private val decrypt = Cipher.getInstance("AES/ECB/NoPadding").apply {
        init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"))
    }
    private var outgoing = encryptNonce.copyOf().also { require(it.size == 16) }
    private var incoming = decryptNonce.copyOf().also { require(it.size == 16) }
    private val history = arrayOfNulls<ByteArray>(256)
    var good = 0L; private set
    var late = 0L; private set
    var lost = 0L; private set
    @Synchronized fun outgoingNonce() = outgoing.copyOf()
    @Synchronized fun resetIncoming(nonce: ByteArray) {
        require(nonce.size == 16); incoming = nonce.copyOf(); history.fill(null)
    }

    @Synchronized fun seal(plain: ByteArray): ByteArray {
        require(plain.size <= 1020)
        outgoing = advance(outgoing, 1)
        val (body, tag) = transform(plain, outgoing, false)
        return byteArrayOf(outgoing[0]) + tag.copyOf(3) + body
    }
    @Synchronized fun open(packet: ByteArray): ByteArray? {
        if (packet.size !in 4..1024) return null
        var difference = (packet[0].toInt() and 255) - (incoming[0].toInt() and 255)
        if (difference > 128) difference -= 256
        if (difference < -128) difference += 256
        if (difference == 0 || difference <= -30) return null
        val nonce = advance(incoming, difference)
        val index = nonce[0].toInt() and 255
        if (history[index]?.contentEquals(nonce) == true) return null
        val transformed = try { transform(packet.copyOfRange(4, packet.size), nonce, true) }
            catch (_: IllegalArgumentException) { return null }
        if (!MessageDigest.isEqual(transformed.second.copyOf(3), packet.copyOfRange(1, 4))) return null
        history[index] = nonce.copyOf()
        good++
        if (difference < 0) { late++; lost = (lost - 1).coerceAtLeast(0) }
        else { incoming = nonce; lost += difference - 1 }
        return transformed.first
    }

    private fun transform(data: ByteArray, nonce: ByteArray, decoding: Boolean): Pair<ByteArray, ByteArray> {
        var delta = encrypt.doFinal(nonce)
        var checksum = ByteArray(16)
        val result = ByteArray(data.size)
        var offset = 0
        while (data.size - offset > 16) {
            delta = double(delta)
            val block = data.copyOfRange(offset, offset + 16)
            val plain: ByteArray
            val output: ByteArray
            if (decoding) {
                plain = xor(delta, decrypt.doFinal(xor(delta, block)))
                output = plain
            } else {
                plain = block
                // Mumble's XEX* mitigation: prevent a critical all-zero penultimate block.
                if (data.size - offset <= 32 && plain.take(15).all { it == 0.toByte() }) {
                    plain[0] = (plain[0].toInt() xor 1).toByte()
                }
                output = xor(delta, encrypt.doFinal(xor(delta, plain)))
            }
            output.copyInto(result, offset)
            checksum = xor(checksum, plain)
            offset += 16
        }
        delta = double(delta)
        val length = data.size - offset
        val bits = ByteArray(16).apply { this[15] = (length * 8).toByte() }
        val pad = encrypt.doFinal(xor(delta, bits))
        val last = pad.copyOf()
        for (i in 0 until length) {
            if (decoding) {
                last[i] = (data[offset + i].toInt() xor pad[i].toInt()).toByte()
                result[offset + i] = last[i]
            } else {
                last[i] = data[offset + i]
                result[offset + i] = (last[i].toInt() xor pad[i].toInt()).toByte()
            }
        }
        if (decoding) require((0 until 15).any { last[it] != delta[it] }) { "OCB2 XEX* attack" }
        checksum = xor(checksum, last)
        val tag = encrypt.doFinal(xor(xor(delta, double(delta)), checksum))
        return result to tag
    }
    private fun xor(a: ByteArray, b: ByteArray) = ByteArray(16) { (a[it].toInt() xor b[it].toInt()).toByte() }
    private fun double(value: ByteArray): ByteArray {
        val out = ByteArray(16)
        for (i in 0..15) {
            out[i] = (((value[i].toInt() and 255) shl 1) or
                (if (i < 15) (value[i + 1].toInt() and 255) ushr 7 else 0)).toByte()
        }
        if ((value[0].toInt() and 128) != 0) out[15] = (out[15].toInt() xor 0x87).toByte()
        return out
    }
    private fun advance(nonce: ByteArray, amount: Int): ByteArray {
        val out = nonce.copyOf()
        var carry = amount
        for (i in out.indices) {
            val sum = (out[i].toInt() and 255) + carry
            out[i] = sum.toByte(); carry = sum shr 8
        }
        return out
    }
}
