package ua.school.localmumble.core

import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** A deliberately small single-channel Mumble-compatible server, implemented in Kotlin. */
class LocalVoiceServer(
    private val options: ServerOptions,
    private val identityDirectory: File,
    private val log: (String) -> Unit = {},
    private val onFailure: (Throwable) -> Unit = {},
) : Closeable {
    private val stateLock = Any()
    private val stopped = AtomicBoolean(false)
    private val peers = ConcurrentHashMap<Int, Peer>()
    private val connections = ConcurrentHashMap.newKeySet<Peer>()
    private val capacity = Semaphore(options.maxUsers + 8)
    private val nextSession = AtomicInteger(1)
    private val executor = Executors.newCachedThreadPool { task ->
        Thread(task, "local-voice-io").apply { isDaemon = true }
    }
    private val timers = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "local-voice-deadlines").apply { isDaemon = true }
    }
    private var tcp: ServerSocket? = null
    private var udp: DatagramSocket? = null
    @Volatile private var opus = true
    @Volatile private var celt = -1
    @Volatile var port: Int = 0
        private set
    val participantCount: Int get() = peers.values.count { it.ready && !it.closed.get() }

    @Synchronized fun start() {
        check(!stopped.get() && tcp == null) { "Server already started or closed" }
        try {
            val tlsFactory = TlsIdentity.context(identityDirectory).socketFactory
            val socket = ServerSocket()
            tcp = socket
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), options.port), options.maxUsers + 8)
            port = socket.localPort
            val datagram = DatagramSocket(null)
            udp = datagram
            datagram.reuseAddress = false
            datagram.bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), port))
            datagram.receiveBufferSize = 256 * 1024
            executor.execute { listener { acceptClients(socket, tlsFactory) } }
            executor.execute { listener { receiveDatagrams(datagram) } }
            log("KOTLIN_SERVER_READY TCP/UDP :$port · TLS 1.2+ · канал Клас")
        } catch (error: Exception) { close(); throw error }
    }

    private fun listener(block: () -> Unit) {
        try { block() } catch (error: Exception) {
            if (!stopped.get()) {
                log("Помилка мережі: ${error.javaClass.simpleName}")
                close(); onFailure(error)
            }
        }
    }
    private fun acceptClients(socket: ServerSocket, tlsFactory: SSLSocketFactory) {
        while (!stopped.get()) {
            val transport = socket.accept()
            if (!localAddress(transport.inetAddress) || !capacity.tryAcquire()) { transport.close(); continue }
            val client = try {
                (tlsFactory.createSocket(transport, transport.inetAddress.hostAddress, transport.port, true) as SSLSocket).apply {
                    useClientMode = false; needClientAuth = false
                    enabledProtocols = supportedProtocols.filter { it == "TLSv1.2" || it == "TLSv1.3" }.toTypedArray()
                }
            } catch (_: Exception) { transport.close(); capacity.release(); continue }
            val peer = try { Peer(nextSession.getAndIncrement(), client, transport) }
                catch (_: Exception) { transport.close(); client.close(); capacity.release(); continue }
            connections.add(peer)
            if (stopped.get()) peer.close() else executor.execute { peer.run() }
        }
    }
    private fun receiveDatagrams(socket: DatagramSocket) {
        val buffer = ByteArray(1025)
        while (!stopped.get()) {
            val packet = DatagramPacket(buffer, buffer.size)
            socket.receive(packet)
            if (!localAddress(packet.address) || packet.length > 1024) continue
            val data = packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
            val endpoint = packet.socketAddress as InetSocketAddress
            if (data.size == 12 && data.take(4).all { it == 0.toByte() }) {
                val reply = ByteBuffer.allocate(24).putInt(VERSION).put(data, 4, 8)
                    .putInt(participantCount).putInt(options.maxUsers).putInt(options.bandwidth).array()
                socket.send(DatagramPacket(reply, reply.size, endpoint)); continue
            }
            val candidates = peers.values.filter { it.ready && it.socket.inetAddress == endpoint.address }
                .sortedByDescending { it.endpoint == endpoint }
            var matched = false
            for (peer in candidates) {
                val plain = peer.crypt?.open(data) ?: continue
                matched = true
                peer.endpoint = endpoint
                peer.lastUdp = System.nanoTime()
                if (plain.isNotEmpty() && (plain[0].toInt() and 0xe0) == 0x20) peer.voice(plain)
                else forwardVoice(peer, plain)
                break
            }
            if (!matched) candidates.firstOrNull { it.endpoint == endpoint }?.requestResync()
        }
    }
    private fun authenticate(peer: Peer, data: Proto) {
        val name = data.text(1).orEmpty().trim()
        if (name.isBlank() || name.length > 64 || name.any { it.isISOControl() } || name.equals("SuperUser", true)) {
            peer.reject(2, "Invalid username"); return
        }
        if (options.password.isNotEmpty() && !MessageDigest.isEqual(
                options.password.toByteArray(Charsets.UTF_8), data.text(2).orEmpty().toByteArray(Charsets.UTF_8))) {
            peer.reject(4, "Wrong server password"); return
        }
        peer.name = name
        peer.supportsOpus = data.number(5) == 1L
        peer.celtVersions = data.numbers(4).map { it.toInt() }.filter { it != -1 }.toSet()
        if (!peer.supportsOpus && peer.celtVersions.isEmpty()) {
            peer.reject(1, "Client must support Opus or CELT"); return
        }
        synchronized(stateLock) {
            if (stopped.get() || peer.closed.get()) return
            if (peers.values.any { it.name.equals(name, true) }) { peer.reject(5, "Username already in use"); return }
            if (peers.size >= options.maxUsers) { peer.reject(6, "Server full"); return }
            val members = peers.values.toList() + peer
            val allOpus = members.all { it.supportsOpus }
            val shared = members.map { it.celtVersions }.reduce { a, b -> a.intersect(b) }.maxOrNull() ?: -1
            if (!allOpus && shared == -1) {
                peer.reject(1, "No common audio codec. Use Opus-compatible clients on all devices."); return
            }
            opus = allOpus; celt = shared
            val random = SecureRandom()
            fun randomBlock() = ByteArray(16).also { random.nextBytes(it) }
            val key = randomBlock(); val clientNonce = randomBlock(); val serverNonce = randomBlock()
            peer.crypt = Ocb2(key, serverNonce, clientNonce)
            peer.send(15, Proto.build { bytes(1, key); bytes(2, clientNonce); bytes(3, serverNonce) })
            val codec = codecMessage()
            peers.values.forEach { it.send(21, codec) }
            peer.send(21, codec)
            peer.send(7, Proto.build { number(1, 0); text(3, "Клас"); text(5, "Локальний голосовий канал"); number(11, options.maxUsers) })
            peers.values.forEach { peer.send(9, userState(it)) }
            peer.send(9, userState(peer))
            peer.send(24, Proto.build {
                number(1, options.bandwidth); text(2, "Локальний голосовий сервер")
                number(3, 0); number(4, 0); number(5, 0); number(6, options.maxUsers); number(7, 0)
            })
            peer.send(25, Proto.build { number(2, 0); number(3, 1) })
            peer.send(5, Proto.build {
                number(1, peer.session); number(2, options.bandwidth)
                text(3, "Локальний голосовий сервер · Клас"); number(4, PERMISSIONS)
            })
            peer.ready = true
            peer.authenticationDeadline.cancel(false)
            peer.socket.soTimeout = 75000
            peers[peer.session] = peer
            broadcast(9, userState(peer), peer)
        }
        log("Підключено: $name · учасників $participantCount · ${if (opus) "Opus" else "CELT"}")
    }
    private fun codecMessage() = Proto.build { number(1, celt); number(2, celt); number(3, 1); number(4, if (opus) 1 else 0) }
    private fun userState(peer: Peer) = Proto.build {
        number(1, peer.session); text(3, peer.name); number(5, 0)
        number(9, if (peer.selfMuted) 1 else 0); number(10, if (peer.selfDeafened) 1 else 0)
    }
    private fun broadcast(type: Int, body: ByteArray, except: Peer? = null) {
        peers.values.filter { it.ready && it !== except }.forEach { it.send(type, body) }
    }
    private fun forwardVoice(sender: Peer, body: ByteArray) {
        if (!sender.ready || sender.closed.get() || sender.selfMuted || sender.selfDeafened || body.size !in 2..1000) return
        val header = body[0].toInt() and 255
        val type = header ushr 5
        val target = header and 31
        if (target != 0 && target != 31) return // No whisper/ACL machinery in this classroom server.
        if ((opus && type != 4) || (!opus && type != 0 && type != 3)) return
        try {
            val reader = MumbleVarInt.Reader(body, 1)
            reader.unsigned() // Audio sequence number.
            if (type == 4) {
                val size = reader.unsigned()
                require(size <= 0x3fff)
                reader.skip((size and 0x1fff).toInt())
            } else {
                var more: Boolean
                do { val size = reader.byte(); reader.skip(size and 127); more = size and 128 != 0 } while (more)
            }
            require(reader.remaining == 0 || reader.remaining == 12)
        } catch (_: Exception) { return }
        if (!sender.consumeBandwidth(body.size + 32)) return
        val packet = byteArrayOf((header and 0xe0).toByte()) + MumbleVarInt.encode(sender.session.toLong()) + body.copyOfRange(1, body.size)
        if (target == 31) { sender.voice(packet); return }
        peers.values.filter { it.ready && it !== sender && !it.selfDeafened }.forEach { it.voice(packet) }
    }

    private inner class Peer(val session: Int, val socket: SSLSocket, private val transport: Socket) : Closeable {
        val closed = AtomicBoolean(false)
        @Volatile var ready = false
        val authenticationDeadline = timers.schedule({ if (!ready) close() }, 10, TimeUnit.SECONDS)
        var name = ""
        var supportsOpus = false
        var celtVersions = emptySet<Int>()
        @Volatile var selfMuted = false
        @Volatile var selfDeafened = false
        @Volatile var crypt: Ocb2? = null
        @Volatile var endpoint: InetSocketAddress? = null
        @Volatile var lastUdp = 0L
        private var lastResync = 0L
        private val outgoing = ArrayBlockingQueue<Frame>(128)
        private var output: DataOutputStream? = null
        private var tokens = options.bandwidth / 8.0
        private var tokenTime = System.nanoTime()
        private var controlTokens = 100.0
        private var controlTime = System.nanoTime()

        fun run() {
            try {
                socket.tcpNoDelay = true; socket.soTimeout = 10000
                socket.startHandshake()
                log("TLS підключено: ${socket.inetAddress.hostAddress}")
                output = DataOutputStream(socket.outputStream)
                executor.execute {
                    try { while (!closed.get()) { val frame = outgoing.take(); if (frame.type < 0) break; write(frame) } }
                    catch (_: Exception) { close() }
                }
                send(0, Proto.build { number(1, VERSION); text(2, "Local Kotlin Voice Server 2.0.0-beta1"); text(3, "Android/JVM") })
                val input = DataInputStream(socket.inputStream)
                while (!closed.get()) {
                    val type = input.readUnsignedShort()
                    val size = input.readInt()
                    require(size in 0..65536) { "Control message too large" }
                    val body = ByteArray(size); input.readFully(body)
                    if (type == 1) {
                        if (ready) { endpoint = null; forwardVoice(this, body) }
                        continue
                    }
                    require(consumeControl()) { "Control message flood" }
                    val message = Proto.parse(body)
                    when {
                        type == 0 -> log("Клієнт: ${message.text(2).orEmpty().take(80).filterNot { it.isISOControl() }}")
                        type == 2 && !ready -> authenticate(this, message)
                        !ready -> if (type != 3) error("Authentication required")
                        type == 3 -> send(3, Proto.build {
                            message.number(1)?.let { number(1, it) }
                            number(2, crypt?.good ?: 0); number(3, crypt?.late ?: 0); number(4, crypt?.lost ?: 0)
                        })
                        type == 9 -> updateState(message)
                        type == 15 -> {
                            val nonce = message.bytes(2)
                            if (nonce != null) { require(nonce.size == 16); crypt?.resetIncoming(nonce); endpoint = null }
                            else send(15, Proto.build { bytes(3, crypt!!.outgoingNonce()) })
                        }
                        type == 20 -> send(20, Proto.build { number(1, message.number(1) ?: 0); number(2, PERMISSIONS) })
                        type == 22 -> send(22, Proto.build {
                            number(1, session); number(2, 1); number(15, options.bandwidth); number(19, if (supportsOpus) 1 else 0)
                        })
                        type in setOf(6, 7, 8, 10, 11, 12, 18, 19, 24) -> deny("This server supports one voice channel; administration and text chat are disabled")
                    }
                }
            } catch (error: Exception) {
                if (!closed.get() && !stopped.get() && error !is EOFException) {
                    log("З'єднання закрито: ${error.javaClass.simpleName}: ${error.message.orEmpty().take(120)}")
                }
            } finally { close() }
        }
        private fun updateState(message: Proto) {
            if ((message.number(1) ?: session.toLong()) != session.toLong() ||
                (message.number(5) ?: 0L) != 0L) { deny("Only your own state in channel 0 can be changed"); return }
            synchronized(stateLock) {
                message.number(9)?.let { selfMuted = it != 0L }
                message.number(10)?.let { selfDeafened = it != 0L; if (selfDeafened) selfMuted = true }
                broadcast(9, userState(this))
            }
        }
        private fun deny(reason: String) = send(12, Proto.build { number(3, session); text(4, reason); number(5, 0) })
        fun reject(reason: Int, text: String) {
            try { write(Frame(4, Proto.build { number(1, reason); text(2, text) })) } finally { close() }
            log("Вхід відхилено: $text")
        }
        fun send(type: Int, body: ByteArray) {
            if (closed.get()) return
            if (!outgoing.offer(Frame(type, body)) && type != 1) close()
        }
        private fun write(frame: Frame) {
            val stream = output ?: return
            synchronized(stream) { stream.writeShort(frame.type); stream.writeInt(frame.body.size); stream.write(frame.body); stream.flush() }
        }
        fun voice(packet: ByteArray) {
            val destination = endpoint
            if (destination != null && System.nanoTime() - lastUdp < 15_000_000_000L) {
                try {
                    val encrypted = crypt?.seal(packet) ?: return
                    udp?.send(DatagramPacket(encrypted, encrypted.size, destination)); return
                } catch (_: Exception) { endpoint = null }
            }
            send(1, packet)
        }
        @Synchronized fun requestResync() {
            val now = System.nanoTime()
            if (now - lastUdp > 5_000_000_000L && now - lastResync > 5_000_000_000L) {
                lastResync = now; send(15, byteArrayOf())
            }
        }
        @Synchronized fun consumeBandwidth(bytes: Int): Boolean {
            val now = System.nanoTime()
            tokens = (tokens + (now - tokenTime) / 1e9 * options.bandwidth / 8).coerceAtMost(options.bandwidth / 8.0)
            tokenTime = now
            if (tokens < bytes) return false
            tokens -= bytes; return true
        }
        private fun consumeControl(): Boolean {
            val now = System.nanoTime()
            controlTokens = (controlTokens + (now - controlTime) / 1e9 * 20).coerceAtMost(100.0)
            controlTime = now
            if (controlTokens < 1) return false
            controlTokens--; return true
        }
        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            authenticationDeadline.cancel(false)
            outgoing.clear(); outgoing.offer(Frame(-1, byteArrayOf()))
            connections.remove(this); capacity.release()
            synchronized(stateLock) {
                if (peers.remove(session, this)) {
                    broadcast(8, Proto.build { number(1, session) })
                    val remaining = peers.values.toList()
                    opus = remaining.all { it.supportsOpus }
                    celt = if (remaining.isEmpty()) -1 else remaining.map { it.celtVersions }.reduce { a, b -> a.intersect(b) }.maxOrNull() ?: -1
                    broadcast(21, codecMessage())
                    log("Відключено: $name · учасників $participantCount")
                }
            }
            // Closing the underlying socket first cancels blocked TLS reads/writes.
            // Do not wait for an unresponsive client to send TLS close_notify.
            runCatching { transport.close() }
            runCatching { socket.close() }
        }
    }
    private data class Frame(val type: Int, val body: ByteArray)

    @Synchronized override fun close() {
        if (!stopped.compareAndSet(false, true)) return
        runCatching { tcp?.close() }; runCatching { udp?.close() }
        connections.toList().forEach { it.close() }
        executor.shutdownNow()
        timers.shutdownNow()
        log("KOTLIN_SERVER_STOPPED")
    }
    companion object {
        const val VERSION = 0x010204 // Use the widely implemented legacy Mumble UDP packet format.
        private const val PERMISSIONS = 0x0e // Traverse, Enter, Speak; no administrator/whisper permissions.
        internal fun localAddress(address: InetAddress): Boolean = address is Inet4Address &&
            (address.isLoopbackAddress || address.isSiteLocalAddress || address.isLinkLocalAddress)
    }
}
