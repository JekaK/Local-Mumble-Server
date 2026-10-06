"""Independent short-packet OCB2 interop check against uMurmur's C crypto.

Algorithm follows umurmur/crypt.c by Martin Johansson / Thorvald Natvig;
BSD-3-Clause, see app/src/main/cpp/umurmur/LICENSE. Test code uses optional
cryptography's AES ECB primitive. This is NOT a general purpose OCB library.
"""
import socket

from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes


def xor(a, b):
    return bytes(x ^ y for x, y in zip(a, b))


def double(value):
    bits = int.from_bytes(value, "big")
    return (((bits << 1) & ((1 << 128) - 1)) ^ (0x87 if bits >> 127 else 0)).to_bytes(16, "big")


class ShortOCB:
    def __init__(self, setup):
        self.key = setup[1]
        self.encrypt_nonce = setup[2]
        self.decrypt_nonce = setup[3]

    def aes(self, block):
        encryptor = Cipher(algorithms.AES(self.key), modes.ECB()).encryptor()
        return encryptor.update(block) + encryptor.finalize()

    def pad(self, nonce, size):
        assert size <= 16
        delta = double(self.aes(nonce))
        return delta, self.aes(xor(delta, (size * 8).to_bytes(16, "big")))

    @staticmethod
    def next_nonce(nonce):
        return ((int.from_bytes(nonce, "little") + 1) % (1 << 128)).to_bytes(16, "little")

    def encrypt(self, plain):
        self.encrypt_nonce = self.next_nonce(self.encrypt_nonce)
        delta, pad = self.pad(self.encrypt_nonce, len(plain))
        checksum = plain + pad[len(plain):]
        tag = self.aes(xor(xor(delta, double(delta)), checksum))
        return self.encrypt_nonce[:1] + tag[:3] + xor(plain, pad)

    def decrypt(self, packet):
        for _ in range(256):
            self.decrypt_nonce = self.next_nonce(self.decrypt_nonce)
            if self.decrypt_nonce[0] == packet[0]:
                break
        data = packet[4:]
        delta, pad = self.pad(self.decrypt_nonce, len(data))
        plain = xor(data, pad)
        tag = self.aes(xor(xor(delta, double(delta)), plain + pad[len(plain):]))
        assert tag[:3] == packet[1:4], "UDP authentication tag mismatch"
        return plain


def test_udp_voice(port, teacher, student, voice):
    first = ShortOCB(teacher.crypt)
    second = ShortOCB(student.crypt)
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as a, \
            socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as b:
        a.settimeout(3)
        b.settimeout(3)
        for channel, cipher in ((a, first), (b, second)):
            channel.sendto(cipher.encrypt(b"\x20\x01"), ("127.0.0.1", port))
            reply, _ = channel.recvfrom(100)
            assert cipher.decrypt(reply) == b"\x20\x01"
        a.sendto(first.encrypt(voice), ("127.0.0.1", port))
        reply, _ = b.recvfrom(100)
        assert second.decrypt(reply) == voice[:1] + bytes([teacher.session]) + voice[1:]
        b.sendto(second.encrypt(voice), ("127.0.0.1", port))
        reply, _ = a.recvfrom(100)
        assert first.decrypt(reply) == voice[:1] + bytes([student.session]) + voice[1:]
    print("PASS encrypted UDP ping / OCB2 authentication / bidirectional Opus forwarding")
