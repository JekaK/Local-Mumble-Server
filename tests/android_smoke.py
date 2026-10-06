#!/usr/bin/env python3
"""Launch a disposable emulator and exercise the installed Android server.

Requires the mumble_test AVD (API 35 default x86_64) and an assembled debug APK.
An emulator cannot verify a physical phone's hotspot client capacity / isolation.
"""
import argparse
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

from protocol_smoke import tests


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--sdk", required=True)
    parser.add_argument("--apk", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    sdk = Path(args.sdk)
    output = Path(args.output)
    output.mkdir(parents=True, exist_ok=True)
    adb = str(sdk / "platform-tools" / "adb")

    def command(*arguments, timeout=60, check=True):
        result = subprocess.run([adb, "-s", "emulator-5554", *arguments],
                                capture_output=True, timeout=timeout)
        if check and result.returncode:
            raise AssertionError(result.stderr.decode(errors="replace"))
        return result.stdout

    def hierarchy():
        command("shell", "uiautomator", "dump", "/sdcard/window_dump.xml", timeout=90)
        xml = command("exec-out", "cat", "/sdcard/window_dump.xml")
        return ET.fromstring(xml)

    def find(resource):
        for node in hierarchy().iter("node"):
            if node.attrib.get("resource-id") == "ua.school.localmumble:id/" + resource:
                return node
        return None

    def tap(resource):
        for _ in range(5):
            node = find(resource)
            if node is not None:
                bounds = node.attrib["bounds"].replace("][", ",").strip("[]").split(",")
                left, top, right, bottom = map(int, bounds)
                command("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))
                return
            command("shell", "input", "swipe", "500", "2000", "500", "650", "400")
        raise AssertionError("Missing UI control: " + resource)

    with (output / "emulator.log").open("wb") as log:
        emulator = subprocess.Popen([str(sdk / "emulator" / "emulator"), "-avd", "mumble_test",
                                     "-no-window", "-no-audio", "-no-snapshot", "-read-only",
                                     "-show-kernel",
                                     "-gpu", "swiftshader_indirect", "-accel", "off",
                                     "-memory", "1536", "-cores", "2", "-port", "5554"],
                                    stdout=log, stderr=subprocess.STDOUT)
        try:
            subprocess.run([adb, "start-server"], check=True)
            deadline = time.monotonic() + 360
            attempts = 0
            while time.monotonic() < deadline:
                assert emulator.poll() is None, "Emulator exited; inspect emulator.log"
                boot = command("shell", "getprop", "sys.boot_completed", check=False, timeout=10).strip()
                if boot == b"1":
                    break
                attempts += 1
                if attempts % 15 == 0:
                    print("BOOT STATUS: " + command("get-state", check=False).decode(errors="replace").strip(), flush=True)
                time.sleep(2)
            else:
                raise AssertionError("Emulator did not boot in 6 minutes")
            print("PASS Android 15 emulator boot", flush=True)
            command("shell", "wm", "size", "1080x2400")
            command("shell", "input", "keyevent", "82")
            command("install", "-r", str(Path(args.apk).resolve()), timeout=180)
            command("shell", "pm", "grant", "ua.school.localmumble", "android.permission.POST_NOTIFICATIONS")
            command("shell", "am", "start", "-n", "ua.school.localmumble/.MainActivity")
            time.sleep(3)
            tap("passwordInput")
            command("shell", "input", "text", "school-test")
            command("shell", "input", "keyevent", "4")
            tap("startButton")
            deadline = time.monotonic() + 45
            while time.monotonic() < deadline:
                text = command("logcat", "-d", "-s", "LocalMumble:I", "AndroidRuntime:E").decode(errors="replace")
                if "ANDROID_SERVER_READY" in text:
                    break
                if "FATAL EXCEPTION" in text:
                    raise AssertionError(text)
                time.sleep(1)
            else:
                raise AssertionError("Installed app did not start the native server: " + text)
            print("PASS installed APK / foreground service / native process launch", flush=True)
            command("forward", "tcp:64738", "tcp:64738")
            tests(64738, "school-test", skip_udp=True)

            # Automatic in-app UDP probe must have a real response, not just a live PID.
            command("shell", "input", "swipe", "500", "700", "500", "2100", "400")
            time.sleep(4)
            participant = find("participantsText")
            assert participant is not None and "—" not in participant.attrib["text"]
            print("PASS same-phone loopback UDP probe", flush=True)
            (output / "running.png").write_bytes(command("exec-out", "screencap", "-p"))

            # Keep running when another app is in the foreground.
            command("shell", "input", "keyevent", "3")
            time.sleep(3)
            tests(64738, "school-test", skip_udp=True)
            print("PASS server continues with activity in background", flush=True)
            command("shell", "am", "start", "-n", "ua.school.localmumble/.MainActivity")
            tap("stopButton")
            time.sleep(4)
            tap("startButton")
            time.sleep(4)
            tests(64738, "school-test", skip_udp=True)
            print("PASS stop / restart / reused TLS certificate", flush=True)
            (output / "logcat.txt").write_bytes(command("logcat", "-d", "-s", "LocalMumble:I", "AndroidRuntime:E"))
            print("ALL ANDROID SMOKE TESTS PASSED", flush=True)
        finally:
            command("emu", "kill", check=False, timeout=10)
            try:
                emulator.wait(timeout=15)
            except subprocess.TimeoutExpired:
                emulator.kill()


if __name__ == "__main__":
    main()
