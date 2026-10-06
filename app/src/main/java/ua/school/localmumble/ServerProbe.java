package ua.school.localmumble;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;

final class ServerProbe {
    private ServerProbe() {}

    // Mumble's public UDP status request: zero uint32 + an opaque uint64 nonce.
    static int participantCount(int port) throws Exception {
        long nonce = System.nanoTime();
        byte[] query = ByteBuffer.allocate(12).putInt(0).putLong(nonce).array();
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("127.0.0.1"), port);
            socket.setSoTimeout(1000);
            socket.send(new DatagramPacket(query, query.length));
            byte[] response = new byte[24];
            DatagramPacket reply = new DatagramPacket(response, response.length);
            socket.receive(reply);
            if (reply.getLength() != 24) throw new IllegalStateException("Некоректна UDP-відповідь");
            ByteBuffer data = ByteBuffer.wrap(response);
            data.getInt();
            if (data.getLong() != nonce) throw new IllegalStateException("Некоректна UDP-відповідь");
            return data.getInt();
        }
    }
}
