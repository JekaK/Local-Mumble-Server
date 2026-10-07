package ua.school.localmumble.data.network

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer

internal object ServerProbe {
    fun participantCount(port: Int): Int {
        val nonce = System.nanoTime()
        val query = ByteBuffer.allocate(12).putInt(0).putLong(nonce).array()
        return DatagramSocket().use { socket ->
            socket.connect(InetAddress.getByName("127.0.0.1"), port)
            socket.soTimeout = 1000
            socket.send(DatagramPacket(query, query.size))
            val reply = DatagramPacket(ByteArray(25), 25)
            socket.receive(reply)
            check(reply.length == 24) { "Некоректна UDP-відповідь" }
            val data = ByteBuffer.wrap(reply.data, 0, reply.length)
            data.int
            check(data.long == nonce) { "Некоректний nonce" }
            data.int
        }
    }
}
