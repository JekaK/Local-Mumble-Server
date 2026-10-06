package ua.school.localmumble.core

import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress

class WireAndCryptoTest {
    @Test fun voiceIntegerBoundaries() {
        listOf(0L, 127, 128, 16383, 16384, 0x1fffff, 0x200000, 0xfffffff, 0x10000000, 0xffffffff)
            .forEach { assertEquals(it, MumbleVarInt.Reader(MumbleVarInt.encode(it)).unsigned()) }
        assertArrayEquals(byteArrayOf(0x80.toByte(), 0x80.toByte()), MumbleVarInt.encode(128))
        assertThrows(IllegalArgumentException::class.java) { MumbleVarInt.Reader(byteArrayOf(0xf0.toByte())).unsigned() }
    }
    @Test fun protobufUnknownFieldsAndUnicode() {
        val encoded = Proto.build { text(1, "Клас"); number(5, 48000); number(9, -1) } +
            byteArrayOf(0x35, 1, 2, 3, 4) // Unknown fixed32 field.
        val parsed = Proto.parse(encoded)
        assertEquals("Клас", parsed.text(1)); assertEquals(48000L, parsed.number(5)); assertEquals(-1L, parsed.number(9))
        val packed = Proto.parse(Proto.build { bytes(4, byteArrayOf(1, 2, 3)); number(4, 4) })
        assertEquals(listOf(1L, 2L, 3L, 4L), packed.numbers(4))
    }
    @Test fun malformedProtobufIsBounded() {
        listOf(byteArrayOf(0), byteArrayOf(0x0a, 127), byteArrayOf(0x08) + ByteArray(10) { 0xff.toByte() }, ByteArray(65537))
            .forEach { assertThrows(IllegalArgumentException::class.java) { Proto.parse(it) } }
        assertThrows(Exception::class.java) { Proto.parse(byteArrayOf(0x0a, 1, 0xff.toByte())).text(1) }
    }
    @Test fun validatesConfigurationAndPrivatePeers() {
        assertThrows(IllegalArgumentException::class.java) { ServerOptions(port = 80) }
        assertThrows(IllegalArgumentException::class.java) { ServerOptions(password = "a\nb") }
        assertTrue(LocalVoiceServer.localAddress(InetAddress.getByName("127.0.0.1")))
        assertTrue(LocalVoiceServer.localAddress(InetAddress.getByName("192.168.1.20")))
        assertFalse(LocalVoiceServer.localAddress(InetAddress.getByName("8.8.8.8")))
        assertFalse(LocalVoiceServer.localAddress(InetAddress.getByName("::1")))
    }
    // Vectors from Mumble TestCrypt / draft-krovetz-ocb-00; BSD notice in licenses/.
    @Test fun ocbMatchesIndependentPublishedVectors() {
        val key = ByteArray(16) { it.toByte() }
        val previous = key.copyOf().apply { this[0] = 0xff.toByte(); this[1] = 0 }
        assertArrayEquals(hex("00bf3108"), Ocb2(key, previous, previous).seal(byteArrayOf()))
        val expected = hex("009db0cd" + "f75d6bc8b4dc8d66b836a2b08b32a6369f1cd3c5228d79fd6c267f5f6aa7b231c7dfb9d59951ae9c")
        val plain = ByteArray(40) { it.toByte() }
        assertArrayEquals(expected, Ocb2(key, previous, previous).seal(plain))
        assertArrayEquals(plain, Ocb2(key, previous, previous).open(expected))
    }
    @Test fun ocbPacketLossLateReplayTamperAndWraparound() {
        val key = ByteArray(16) { (it + 1).toByte() }; val nonce = ByteArray(16).apply { this[0] = 0xfe.toByte() }
        val encoder = Ocb2(key, nonce, nonce); val decoder = Ocb2(key, nonce, nonce)
        val first = encoder.seal(byteArrayOf(1)); val second = encoder.seal(byteArrayOf(2)); val third = encoder.seal(byteArrayOf(3))
        assertArrayEquals(byteArrayOf(1), decoder.open(first))
        assertArrayEquals(byteArrayOf(3), decoder.open(third))
        assertEquals(1L, decoder.lost)
        assertArrayEquals(byteArrayOf(2), decoder.open(second))
        assertEquals(1L, decoder.late); assertEquals(0L, decoder.lost)
        assertNull(decoder.open(second)); assertNull(decoder.open(third))
        val next = encoder.seal(ByteArray(100) { (it + 42).toByte() })
        assertNull(decoder.open(next.copyOf().apply { this[20] = (this[20].toInt() xor 1).toByte() }))
        assertArrayEquals(ByteArray(100) { (it + 42).toByte() }, decoder.open(next))
        repeat(512) { assertArrayEquals(byteArrayOf(7), decoder.open(encoder.seal(byteArrayOf(7)))) }
    }
    @Test fun ocbAllLengthsAndXexStarMitigation() {
        val key = ByteArray(16) { it.toByte() }; val nonce = ByteArray(16) { 0x55 }
        for (size in 0..1000) {
            val data = ByteArray(size) { (it % 251 + 1).toByte() }
            assertArrayEquals(data, Ocb2(key, nonce, nonce).open(Ocb2(key, nonce, nonce).seal(data)))
        }
        val critical = ByteArray(32).apply { this[15] = 0x80.toByte(); for (i in 16..31) this[i] = 42 }
        val recovered = Ocb2(key, nonce, nonce).open(Ocb2(key, nonce, nonce).seal(critical))!!
        assertEquals(1.toByte(), recovered[0]); assertArrayEquals(critical.copyOfRange(1, 32), recovered.copyOfRange(1, 32))
    }
    @Test fun rejectsIndependentlyConstructedXexStarForgery() {
        val key = ByteArray(16) { it.toByte() }
        val previous = key.copyOf().apply { this[0] = 0xff.toByte(); this[1] = 0 }
        // Generated independently with AES from the attack construction in eprint 2019/311.
        assertNull(Ocb2(key, previous, previous).open(hex("00ded2d747cd9a349f26cb14827ee61e3378646c")))
    }
    private fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
