#!/usr/bin/env python3
"""Mumble wire-protocol smoke test. No third-party Python dependencies.

Start the host executable using --binary, or test an already running Android
server through adb's TCP port forwarding using --port and --skip-udp.
This verifies voice packet transport, not microphone/speaker audio quality.
"""
import argparse
import contextlib
import select
import socket
import ssl
import struct
import subprocess
import tempfile
import time
from pathlib import Path


def varint(value):
    out = bytearray()
    while value > 127:
        out.append((value & 127) | 128)
        value >>= 7
    out.append(value)
    return bytes(out)


def field(number, value):
    if isinstance(value, str):
        value = value.encode()
    if isinstance(value, bytes):
        return varint(number << 3 | 2) + varint(len(value)) + value
    return varint(number << 3) + varint(value)


def parse(data):
    def readvar(offset):
        result, shift = 0, 0
        while True:
            byte = data[offset]
            offset += 1
            result |= (byte & 127) << shift
            if byte < 128:
                return result, offset
            shift += 7
    values, offset = {}, 0
    while offset < len(data):
        key, offset = readvar(offset)
        kind = key & 7
        if kind == 0:
            value, offset = readvar(offset)
        elif kind == 2:
            length, offset = readvar(offset)
            value, offset = data[offset:offset + length], offset + length
        elif kind in (1, 5):
            length = 8 if kind == 1 else 4
            value, offset = data[offset:offset + length], offset + length
        else:
            raise AssertionError(f"Unsupported protobuf kind {kind}")
        values[key >> 3] = value
    return values


class Client:
    def __init__(self, port, name, password):
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
        context.check_hostname = False
        context.verify_mode = ssl.CERT_NONE  # Explicitly testing self-signed local TLS.
        self.socket = context.wrap_socket(socket.create_connection(("127.0.0.1", port), 20), server_hostname="localhost")
        self.socket.settimeout(20)
        self.send(0, field(1, 0x010203) + field(2, "smoke-test") + field(3, "test"))
        self.send(2, field(1, name) + field(2, password) + field(5, 1))
        self.session = None
        self.rejection = None
        for _ in range(40):
            kind, body = self.read()
            if kind == 4:
                self.rejection = parse(body)
                return
            if kind == 15:
                self.crypt = parse(body)
            if kind == 7:
                assert parse(body).get(3) == "Клас".encode(), "Channel name not preserved"
            if kind == 5:
                self.session = parse(body)[1]
                return
        raise AssertionError("ServerSync not received")

    def exact(self, size):
        data = b""
        while len(data) < size:
            part = self.socket.recv(size - len(data))
            if not part:
                raise EOFError("Server closed the connection")
            data += part
        return data

    def read(self):
        kind, size = struct.unpack("!HI", self.exact(6))
        assert size < 1024 * 1024
        return kind, self.exact(size)

    def send(self, kind, body):
        self.socket.sendall(struct.pack("!HI", kind, len(body)) + body)

    def until(self, expected):
        for _ in range(50):
            kind, body = self.read()
            if kind == expected:
                return body
        raise AssertionError(f"Message {expected} not received")

    def close(self):
        self.socket.close()


def udp_count(port):
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as udp:
        udp.settimeout(3)
        nonce = time.monotonic_ns() & ((1 << 64) - 1)
        udp.sendto(struct.pack("!IQ", 0, nonce), ("127.0.0.1", port))
        body, _ = udp.recvfrom(100)
        version, returned, count, capacity, bitrate = struct.unpack("!IQIII", body)
        assert returned == nonce
        assert bitrate == 48000
        return count


def tests(port, password, skip_udp, udp_voice=False):
    if not skip_udp:
        assert udp_count(port) == 0
        print("PASS UDP status / bitrate / nonce")
    wrong = Client(port, "WrongPassword", "wrong-password")
    assert wrong.rejection and wrong.rejection[1] == 4
    wrong.close()
    print("PASS password rejection")
    teacher = Client(port, "Teacher", password)
    student = Client(port, "Student", password)
    assert teacher.session and student.session and teacher.session != student.session
    assert len(teacher.crypt[1]) == 16
    print("PASS two TLS clients / Opus negotiation / channel / crypto setup")
    if not skip_udp:
        assert udp_count(port) == 2
        print("PASS participant counter")
    duplicate = Client(port, "Teacher", password)
    assert duplicate.rejection and duplicate.rejection[1] == 5
    duplicate.close()
    print("PASS duplicate username rejection")

    # Legacy Mumble UDP voice packet tunneled inside TLS: Opus + sequence + len + silence.
    # IDs and sequence below remain <128, so Mumble's varint equals a one-byte integer.
    voice = bytes([4 << 5, 1, 3, 0xF8, 0xFF, 0xFE])
    teacher.send(1, voice)
    forwarded = student.until(1)
    assert forwarded == voice[:1] + bytes([teacher.session]) + voice[1:]
    student.send(1, voice)
    forwarded = teacher.until(1)
    assert forwarded == voice[:1] + bytes([student.session]) + voice[1:]
    print("PASS bidirectional Opus voice packet forwarding over TLS")
    teacher.send(3, field(1, 123456))
    assert parse(teacher.until(3))[1] == 123456
    print("PASS authenticated keep-alive ping")
    if udp_voice:
        from udp_voice import test_udp_voice
        test_udp_voice(port, teacher, student, voice)
    teacher.close()
    student.close()
    print("ALL PROTOCOL TESTS PASSED")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--binary")
    parser.add_argument("--port", type=int, default=64738)
    parser.add_argument("--password", default="school-test")
    parser.add_argument("--skip-udp", action="store_true")
    parser.add_argument("--udp-voice", action="store_true", help="Also test encrypted UDP; requires cryptography")
    args = parser.parse_args()
    if not args.binary:
        tests(args.port, args.password, args.skip_udp, args.udp_voice)
        return
    with tempfile.TemporaryDirectory(prefix="localmumble-test-") as directory:
        config = Path(directory) / "test.conf"
        config.write_text(f'''max_bandwidth = 48000;
certificate = "{directory}/certificate.crt";
private_key = "{directory}/private_key.key";
password = "{args.password}";
max_users = 30;
bindport = {args.port};
bindport6 = {args.port};
bindaddr = "127.0.0.1";
username = "";
channels = ({{ name = "Клас"; parent = ""; }});
default_channel = "Клас";
''', encoding="utf-8")
        process = subprocess.Popen([args.binary, "-d", "-c", str(config)], stderr=subprocess.PIPE, text=True)
        try:
            deadline = time.monotonic() + 30
            while time.monotonic() < deadline:
                assert process.poll() is None, "Server exited before readiness"
                try:
                    udp_count(args.port)
                    break
                except (OSError, AssertionError):
                    time.sleep(.1)
            else:
                raise AssertionError("Server startup timeout")
            tests(args.port, args.password, args.skip_udp, args.udp_voice)
        finally:
            process.terminate()
            process.wait(timeout=5)
        assert process.returncode == 0, process.returncode
        print("PASS graceful shutdown")


if __name__ == "__main__":
    main()
