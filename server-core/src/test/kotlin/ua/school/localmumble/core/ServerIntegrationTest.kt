package ua.school.localmumble.core

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.nio.file.Files
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

class ServerIntegrationTest {
    private lateinit var server: LocalVoiceServer
    private lateinit var directory: java.io.File
    private val clients = mutableListOf<Client>()
    @Before fun start() {
        directory = Files.createTempDirectory("kotlin-mumble-test").toFile()
        server = LocalVoiceServer(ServerOptions(port = 0, maxUsers = 2, password = "school-test"), directory)
        server.start()
    }
    @After fun stop() { clients.forEach { it.close() }; server.close(); directory.deleteRecursively() }
    private fun client(name: String, password: String = "school-test", opus: Boolean = true, celt: Int? = null): Client =
        Client(server.port, name, password, opus, celt).also { clients += it }

    @Test fun legacyIosHeadersOverTls12() = verifyLegacyIosHeaders("TLSv1.2")
    @Test fun legacyIosHeadersOverTls13() = verifyLegacyIosHeaders("TLSv1.3")

    private fun verifyLegacyIosHeaders(protocol: String) {
        val teacher = Client(server.port, "Teacher", "school-test", true, null, true, protocol).also { clients += it }
        assertTrue("Own UserState must precede ServerSync", teacher.session in teacher.announcedSessions)
        assertTrue("Root ChannelState must precede ServerSync", 0 in teacher.announcedChannels)
        assertEquals("Клас", teacher.channel)
        assertEquals(16, teacher.crypto!!.bytes(1)!!.size)
        assertEquals(1L, teacher.codec!!.number(4))
        assertEquals(1, count())
        teacher.send(3, Proto.build { number(1, 98765) })
        assertEquals(98765L, Proto.parse(teacher.until(3)).number(1))
        val voice = opusVoice(1)
        teacher.send(1, voice.copyOf().apply { this[0] = (this[0].toInt() or 31).toByte() })
        assertArrayEquals(forwarded(teacher, voice), teacher.until(1))
        val rejected = Client(server.port, "Wrong", "wrong", true, null, true, protocol).also { clients += it }
        assertEquals(4L, rejected.rejection?.number(1))
    }

    @Test fun authenticationCapacityChannelAndKeepAlive() {
        assertEquals(0, count())
        assertEquals(4L, client("Wrong", "wrong").rejection?.number(1))
        val teacher = client("Teacher")
        val student = client("Учениця")
        assertNotEquals(teacher.session, student.session)
        assertEquals("Клас", student.channel)
        assertEquals(16, student.crypto!!.bytes(1)!!.size)
        assertEquals(1L, student.codec!!.number(4))
        assertEquals(2, count())
        assertEquals(5L, client("teacher").rejection?.number(1))
        assertEquals(6L, client("Full").rejection?.number(1))
        teacher.send(3, Proto.build { number(1, 123456L) })
        assertEquals(123456L, Proto.parse(teacher.until(3)).number(1))
        teacher.send(20, Proto.build { number(1, 0) })
        assertEquals(0x0eL, Proto.parse(teacher.until(20)).number(2))
    }
    @Test fun bidirectionalTlsVoiceAndLoopback() {
        val teacher = client("Teacher"); val student = client("Student")
        val voice = opusVoice(1)
        teacher.send(1, voice)
        assertArrayEquals(forwarded(teacher, voice), student.until(1))
        student.send(1, voice)
        assertArrayEquals(forwarded(student, voice), teacher.until(1))
        val loopback = voice.copyOf().apply { this[0] = (this[0].toInt() or 31).toByte() }
        teacher.send(1, loopback)
        assertArrayEquals(forwarded(teacher, voice), teacher.until(1))
    }
    @Test fun udpEncryptedVoiceReplayTamperAndFallback() {
        val teacher = client("Teacher"); val student = client("Student")
        DatagramSocket().use { a -> DatagramSocket().use { b ->
            a.soTimeout = 2000; b.soTimeout = 2000
            val first = teacher.cipher(); val second = student.cipher()
            val ping = byteArrayOf(0x20, 1)
            sendUdp(a, first.seal(ping)); assertArrayEquals(ping, first.open(receiveUdp(a)))
            sendUdp(b, second.seal(ping)); assertArrayEquals(ping, second.open(receiveUdp(b)))
            val voice = opusVoice(2)
            val encrypted = first.seal(voice)
            sendUdp(a, encrypted)
            assertArrayEquals(forwarded(teacher, voice), second.open(receiveUdp(b)))
            sendUdp(a, encrypted) // Replay must not reach the receiver.
            sendUdp(a, first.seal(voice).apply { this[5] = (this[5].toInt() xor 1).toByte() })
            b.soTimeout = 200
            assertThrows(java.net.SocketTimeoutException::class.java) { receiveUdp(b) }
            b.soTimeout = 2000
            sendUdp(a, first.seal(voice))
            assertArrayEquals(forwarded(teacher, voice), second.open(receiveUdp(b)))
            sendUdp(b, second.seal(voice))
            assertArrayEquals(forwarded(student, voice), first.open(receiveUdp(a)))
            // Tunneling switches that client to TLS, even after a UDP endpoint was established.
            student.send(1, voice)
            assertArrayEquals(forwarded(student, voice), first.open(receiveUdp(a)))
            teacher.send(1, voice)
            assertArrayEquals(forwarded(teacher, voice), student.until(1))
        } }
    }
    @Test fun oldClientCommonCeltNegotiationAndClearMismatchRejection() {
        val teacher = client("Teacher", opus = true, celt = 0x8000000b.toInt())
        // Historical signed CELT IDs are represented as protobuf int32.
        assertEquals(1L, teacher.codec!!.number(4))
        assertEquals(1L, client("Old", opus = false).rejection?.number(1))
        assertEquals(1L, client("NoShared", opus = false, celt = 7).rejection?.number(1))
    }
    @Test fun compatibleCeltClientsCanExchangePackets() {
        val version = 0x8000000b.toInt()
        val teacher = client("Teacher", opus = false, celt = version)
        val student = client("Student", opus = false, celt = version)
        assertEquals(0L, student.codec!!.number(4)); assertEquals(version.toLong(), student.codec!!.number(1))
        val voice = byteArrayOf(0, 1, 3, 42, 43, 44)
        teacher.send(1, voice)
        assertArrayEquals(forwarded(teacher, voice), student.until(1))
    }
    @Test fun selfMuteAndDeafenCannotBeUsedToModifyAnotherUser() {
        val teacher = client("Teacher"); val student = client("Student")
        student.send(9, Proto.build { number(1, teacher.session); number(9, 1) })
        assertEquals(0L, Proto.parse(student.until(12)).number(5))
        val voice = opusVoice(3)
        teacher.send(1, voice); assertArrayEquals(forwarded(teacher, voice), student.until(1))
        student.send(9, Proto.build { number(9, 1) })
        assertEquals(1L, Proto.parse(student.until(9)).number(9))
        student.send(1, voice)
        teacher.socket.soTimeout = 200
        assertThrows(java.net.SocketTimeoutException::class.java) { teacher.until(1) }
        teacher.socket.soTimeout = 3000
        student.send(9, Proto.build { number(10, 1) })
        assertEquals(1L, Proto.parse(student.until(9)).number(10))
        teacher.send(1, voice)
        student.socket.soTimeout = 200
        assertThrows(java.net.SocketTimeoutException::class.java) { student.until(1) }
        student.socket.soTimeout = 3000
        student.send(9, Proto.build { number(9, 0); number(10, 0) })
        assertEquals(0L, Proto.parse(student.until(9)).number(9))
        student.send(1, voice); assertArrayEquals(forwarded(student, voice), teacher.until(1))
    }
    @Test fun resyncUnknownMessagesMalformedFramesAndDisconnect() {
        val teacher = client("Teacher"); val student = client("Student")
        teacher.send(15, byteArrayOf())
        assertEquals(16, Proto.parse(teacher.until(15)).bytes(3)!!.size)
        teacher.send(29, Proto.build { number(30, 123) }) // A future control message is harmless.
        teacher.send(3, Proto.build { number(1, 88) })
        assertEquals(88L, Proto.parse(teacher.until(3)).number(1))
        teacher.send(1, byteArrayOf(0x80.toByte(), 1, 127)) // Truncated Opus frame is discarded.
        teacher.send(1, opusVoice(4))
        assertArrayEquals(forwarded(teacher, opusVoice(4)), student.until(1))
        student.rawHeader(3, Int.MAX_VALUE)
        assertEquals(student.session.toLong(), Proto.parse(teacher.until(8)).number(1))
        assertEquals(1, count())
    }
    @Test fun identityPersistsAndShutdownReleasesBothPorts() {
        val first = client("First")
        val certificate = (first.socket.session.peerCertificates.first() as X509Certificate).encoded
        val previousPort = server.port
        server.close()
        ServerSocket(previousPort).use { }
        DatagramSocket(previousPort).use { }
        server = LocalVoiceServer(ServerOptions(port = previousPort, password = "school-test"), directory)
        server.start()
        val second = client("Second")
        assertArrayEquals(certificate, (second.socket.session.peerCertificates.first() as X509Certificate).encoded)
    }
    @Test fun clientNonceResynchronizationRestoresUdp() {
        val teacher = client("Teacher")
        val nonce = ByteArray(16) { 0x77 }
        teacher.send(15, Proto.build { bytes(2, nonce) })
        teacher.send(15, byteArrayOf())
        val serverNonce = Proto.parse(teacher.until(15)).bytes(3)!!
        val cipher = Ocb2(teacher.crypto!!.bytes(1)!!, nonce, serverNonce)
        DatagramSocket().use { socket ->
            socket.soTimeout = 2000
            val ping = byteArrayOf(0x20, 2)
            sendUdp(socket, cipher.seal(ping)); assertArrayEquals(ping, cipher.open(receiveUdp(socket)))
        }
    }
    @Test fun repeatedDisconnectsDoNotExhaustConnectionSlots() {
        repeat(20) { index ->
            client("User$index").close()
            val deadline = System.nanoTime() + 2_000_000_000L
            while (count() != 0 && System.nanoTime() < deadline) Thread.sleep(5)
            assertEquals(0, count())
        }
        assertTrue(client("Last").session > 0)
    }
    private fun count(): Int = DatagramSocket().use { socket ->
        socket.soTimeout = 2000
        val query = ByteBuffer.allocate(12).putInt(0).putLong(123456).array()
        sendUdp(socket, query)
        val reply = ByteBuffer.wrap(receiveUdp(socket))
        assertEquals(LocalVoiceServer.VERSION, reply.int); assertEquals(123456L, reply.long)
        val value = reply.int; assertEquals(2, reply.int); assertEquals(48000, reply.int); value
    }
    private fun sendUdp(socket: DatagramSocket, body: ByteArray) {
        socket.send(DatagramPacket(body, body.size, InetSocketAddress("127.0.0.1", server.port)))
    }
    private fun receiveUdp(socket: DatagramSocket): ByteArray {
        val packet = DatagramPacket(ByteArray(1024), 1024); socket.receive(packet)
        return packet.data.copyOf(packet.length)
    }
    private fun opusVoice(sequence: Int) = byteArrayOf(0x80.toByte()) + MumbleVarInt.encode(sequence.toLong()) + byteArrayOf(3, 0xf8.toByte(), 0xff.toByte(), 0xfe.toByte())
    private fun forwarded(client: Client, body: ByteArray) = body.copyOfRange(0, 1) + MumbleVarInt.encode(client.session.toLong()) + body.copyOfRange(1, body.size)

    private class Client(
        port: Int, name: String, password: String, opus: Boolean, celt: Int?,
        private val legacyHeaders: Boolean = false, protocol: String? = null,
    ) : Closeable {
        // Only test clients accept the test server's self-signed certificate automatically.
        val socket = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(object : X509TrustManager {
                override fun getAcceptedIssuers() = emptyArray<X509Certificate>()
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            }), null)
        }.socketFactory.createSocket(InetAddress.getByName("127.0.0.1"), port) as SSLSocket
        private val input: DataInputStream
        private val output: DataOutputStream
        var session = 0
        var rejection: Proto? = null
        var crypto: Proto? = null
        var codec: Proto? = null
        var channel: String? = null
        val announcedSessions = mutableSetOf<Int>()
        val announcedChannels = mutableSetOf<Int>()
        init {
            protocol?.let { socket.enabledProtocols = arrayOf(it) }
            socket.soTimeout = 3000; socket.tcpNoDelay = true; socket.startHandshake()
            input = DataInputStream(socket.inputStream); output = DataOutputStream(socket.outputStream)
            send(0, Proto.build { number(1, 0x010203); text(2, "Kotlin protocol test") })
            send(2, Proto.build { text(1, name); text(2, password); number(5, if (opus) 1 else 0); celt?.let { number(4, it) } })
            var done = false
            repeat(40) {
                if (!done) {
                    val (kind, body) = read()
                    when (kind) {
                        4 -> { rejection = Proto.parse(body); done = true }
                        5 -> { session = Proto.parse(body).number(1)!!.toInt(); done = true }
                        7 -> Proto.parse(body).let { channel = it.text(3); announcedChannels += it.number(1)!!.toInt() }
                        9 -> announcedSessions += Proto.parse(body).number(1)!!.toInt()
                        15 -> crypto = Proto.parse(body)
                        21 -> codec = Proto.parse(body)
                    }
                }
            }
            check(done) { "ServerSync or Reject not received" }
        }
        fun cipher(): Ocb2 = crypto!!.let { Ocb2(it.bytes(1)!!, it.bytes(2)!!, it.bytes(3)!!) }
        fun send(type: Int, body: ByteArray) { output.writeShort(type); output.writeInt(body.size); output.write(body); output.flush() }
        fun rawHeader(type: Int, size: Int) { output.writeShort(type); output.writeInt(size); output.flush() }
        private fun read(): Pair<Int, ByteArray> {
            val header = if (legacyHeaders) {
                // MumbleKit's _dataReady reads the six-byte header once and drops short reads.
                val bytes = ByteArray(6)
                assertEquals("Legacy iOS client needs a complete header in one TLS read", 6, input.read(bytes))
                ByteBuffer.wrap(bytes)
            } else null
            val type = header?.short?.toInt()?.and(0xffff) ?: input.readUnsignedShort()
            val size = header?.int ?: input.readInt()
            check(size in 0..65536)
            val body = ByteArray(size); input.readFully(body); return type to body
        }
        fun until(expected: Int): ByteArray {
            repeat(100) { val (type, body) = read(); if (type == expected) return body }
            error("Message $expected not received")
        }
        override fun close() { runCatching { socket.close() } }
    }
}
