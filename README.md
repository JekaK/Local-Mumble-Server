# Local Mumble Server

**Turn an Android phone into an offline voice server for your classroom or local group.**

**English** · [Українська](README.uk.md)

[Download Android APK](https://github.com/JekaK/Local-Mumble-Server/raw/refs/heads/master/downloads/LocalMumbleServer-1.0.0-beta1.apk) · [Installation](#step-by-step-installation) · [Troubleshooting](#troubleshooting) · [Build from source](#build-from-source)

| Android | Version | Voice | Network |
| --- | --- | --- | --- |
| 8.0+ | 1.0.0-beta1 | Mumble-compatible, Opus | Local IPv4, TCP + UDP |

One Android phone hosts the server and creates a Wi-Fi hotspot. Other devices
join that network and connect using a Mumble-compatible voice client. The host
can talk and listen too, using a separate client on the same phone.

Internet, a computer, a separate router, root and Termux are not required during
use. Download the server and clients beforehand. The server app's current
interface is Ukrainian; this guide explains its controls in English.

> **Beta status:** server startup on a physical Android phone has been reported
> by the project owner. Automated native protocol tests pass. End-to-end audio,
> a full classroom and iPhone interoperability are not yet validated. An iPhone
> client remaining at “Connecting” is an open troubleshooting case.

## Why this project exists

Classroom voice communication should remain available when internet access is
missing or overloaded. This project keeps the voice server on the teacher's
phone and routes voice between devices on the same local network.

It is suitable for testing local voice sessions in classrooms, school shelters,
workshops and small groups. Local transport removes the dependency on an
external voice server; actual delay still depends on the phones, Wi-Fi, audio
client and headsets. There is no promised latency figure or guaranteed group
size.

## What is included

- A native **uMurmur** server bundled in the APK.
- Start/stop controls, configurable port, password, user limit and bandwidth.
- A default voice channel named **Клас** (“Class”).
- A foreground service with a persistent notification and stop action.
- Local IP display, copyable addresses and a UDP participant counter.
- A copyable server log containing the last 80 lines.
- A self-signed TLS certificate generated separately on each installation.
- Source code, dependency licenses and protocol tests.

The server app does not capture your microphone or record audio. A **separate
voice client is required on every speaking/listening device**, including the
host. This project uses uMurmur, not MumbleWay, and is not an official Mumble
release.

## Requirements

| Item | Requirement |
| --- | --- |
| Host phone | Android 8.0+; ARM64, ARMv7 or x86-64 |
| Local network | Android Wi-Fi hotspot, or an existing LAN |
| Hotspot behavior | Connected devices must be able to reach the host phone |
| Voice clients | Mumble-compatible clients with Opus support |
| Android client | [Mumla](https://mumla-app.gitlab.io/), available on [F-Droid](https://f-droid.org/packages/se.lublin.mumla/) |
| iPhone | Requires a compatible iOS client; currently unverified with this build |
| Internet | Needed beforehand for downloads; not needed for local voice sessions |

No measured minimum RAM or CPU requirement has been established. Start with
two devices and increase the load on the actual host phone.

**The server user limit does not override the hotspot device limit.** A setting
of 30 users cannot make a hotspot that allows 10 connected devices accept 30.
The host's own voice client also occupies a server slot.

## Choose the correct server address

| Where the voice client runs | Address to enter |
| --- | --- |
| On the Android phone hosting the server | `127.0.0.1` |
| On another device connected to that phone's hotspot | The host's hotspot IPv4 address |
| On another device in an existing LAN | The host Android phone's IPv4 address on that LAN |

Use port **64738**, unless you changed it in the server app. Enter the address
and port in their separate fields; do not add `http://` or `https://`.

`127.0.0.1` always means **this device**. Entering it on a student's phone
connects to the student's own phone, not the teacher's server.

For the host's hotspot, its IP can usually be found as **Gateway / Router** in
a connected device's Wi-Fi details. On an existing router-based LAN, that field
is the router's IP: use the server app's displayed host IP instead. Hotspot
addresses vary by device and may change after restarting the hotspot.

## Step-by-step installation

### 1. Prepare the devices before going offline

1. Download [LocalMumbleServer-1.0.0-beta1.apk](https://github.com/JekaK/Local-Mumble-Server/raw/refs/heads/master/downloads/LocalMumbleServer-1.0.0-beta1.apk)
   on the **Android host phone**.
2. Install [Mumla from F-Droid](https://f-droid.org/packages/se.lublin.mumla/)
   on Android phones that will speak or listen. This includes the host phone.
3. If iPhones will participate, validate their client with two devices before
   planning a session. See [the iPhone section](#iphone-connection-notes).
4. Have headphones available for devices in the same room to reduce acoustic
   feedback. Start with one host and one other device.

Students need the **client**, not the server APK.

### 2. Install the server APK on Android

1. Open the downloaded APK using the browser's downloads or a file manager.
2. If Android blocks it, allow **Install unknown apps** for that browser/file
   manager and open the APK again. Menu names vary by manufacturer.
3. Install and launch **Локальний Mumble сервер**.
4. Allow notifications when requested, so the running-server notification and
   stop action are visible.
5. Remove the browser/file manager's install permission after installation if
   it is no longer needed.

The supplied APK is a **debug-signed beta**, not a production-signed release.
You can verify its SHA-256 using [downloads/SHA256SUMS](downloads/SHA256SUMS).

### 3. Create the local Wi-Fi network

1. On the host, open Android's **Wi-Fi hotspot / Tethering** settings. The
   server's **Відкрити налаштування точки доступу** button opens system settings.
2. Give the hotspot a recognizable name, for example `Class-Voice`.
3. Set a Wi-Fi password and enable the hotspot.
4. Disable automatic hotspot shutdown if that setting is available.
5. Join the hotspot from the other device. If it reports **No internet**, keep
   the Wi-Fi connection: the session uses local traffic.
6. Mobile data is not needed for voice. Turn it off for an offline check if the
   phone permits its hotspot to remain active without mobile data.

The app opens hotspot settings; it does not create or configure the hotspot
automatically. An existing LAN is also usable if devices can reach the host.

### 4. Configure and start the server

Use these initial settings:

| App field | Initial value | Meaning |
| --- | --- | --- |
| Порт | `64738` | Same listening port for TCP and UDP |
| Максимум учасників | `30` | Server slots, including the host's client; not a capacity guarantee |
| Максимальний бітрейт… | `48000` | Per-user server bandwidth ceiling in bits/s; packet overhead also matters |
| Пароль сервера | Your chosen password | Separate from the Wi-Fi password |

1. Enter a server password and share it with the participants.
2. Tap **Запустити сервер** (“Start server”).
3. Wait for **Сервер працює** (“Server running”). First startup generates a
   certificate and may take longer than later starts.
4. Read the **Для учнів** (“For students”) address. If several are displayed,
   use the address belonging to the network the students actually joined.
5. If Android does not expose the hotspot IP, use the connected device's
   **Gateway / Router** value as explained above.

“Server running” confirms that the native server opened its TCP and UDP
listeners. It does not prove another phone can reach those listeners.

### 5. Connect the host's own voice client

Open Mumla on the Android host and add a server:

| Client field | Value |
| --- | --- |
| Label | `Class Voice` — any descriptive name |
| Address / Host | `127.0.0.1` |
| Port | `64738`, or your configured port |
| Username | `Teacher`, or another unique name |
| Password | The server password from step 4 |

Connect and enter **Клас**. If a certificate warning appears, accept the
certificate **only after checking you are connecting to your own server**.
The self-signed certificate is generated on the host and reused across server
restarts. A reinstall or deletion of app data can change it.

The server continues in its foreground service while Mumla is open. The server
itself does not request microphone permission; the voice client does.

### 6. Connect the students or other participants

1. Join `Class-Voice` using its **Wi-Fi password**.
2. Keep that Wi-Fi connection even if there is no internet.
3. In the voice client, add a server using the **host's hotspot IP** and port
   `64738`. Do not use `127.0.0.1` or the student's own IP.
4. Enter a unique username such as `Student01` and the **server password**.
5. Check the server certificate as above and connect to **Клас**.
6. Allow microphone access in the client if that participant needs to speak.
7. Test speech in both directions with headphones before adding more devices.

### 7. Validate the setup before a full session

- Start with two devices; confirm both can hear and transmit.
- Move the host close enough for a stable Wi-Fi signal.
- Test the host with Mumla in the foreground and the server app in the
  background. Also test with the host's screen off.
- If your phone offers **Unrestricted battery** mode, apply it to the server
  and voice client. Manufacturer power-saving policies still need testing.
- Add a few participants at a time and observe dropouts, delay and heating.
- Keep the host charged for the session; stop the server and hotspot afterwards.

For noisy rooms, use push-to-talk when the chosen client supports it. Acoustic
feedback from nearby speakers is different from network delay.

## iPhone connection notes

The server APK is Android-only. An iPhone can participate **only through a
compatible Mumble client**; working interoperability with this beta is not yet
confirmed.

The [Mumble app by Mikkel Krautz](https://apps.apple.com/us/app/mumble/id443472808)
lists version 1.3.1 from September 13, 2017. Its
[upstream repository](https://github.com/mumble-voip/mumble-iphoneos) explicitly
states that the app is unmaintained. Do not assume a current App Store listing
means compatibility with modern iOS or this server.

If the iPhone remains at **Connecting**:

1. Confirm it joined the host's hotspot, and use that hotspot's **Router** IP.
2. Check the port and server password.
3. Open **Settings → Privacy & Security → Local Network** and allow access for
   the client if it appears there. See [Apple's instructions](https://support.apple.com/en-us/102229).
4. Check whether the client is waiting for a server certificate confirmation.
5. After another connection attempt, inspect **Журнал сервера** on Android.
   Use the actual log error to distinguish a TLS failure, rejected login and
   codec incompatibility. “Connecting” alone cannot identify the cause.

The native server requires TLS 1.2 or newer and clients that advertise Opus
support. Those requirements are compatibility checks, not a diagnosis of the
reported iPhone issue. The cause remains unresolved without the connection log.

## Troubleshooting

| Symptom | Checks / action |
| --- | --- |
| APK cannot be installed | Android must be 8.0+ with a supported ABI; allow APK installation for the app opening the file and check the file downloaded completely |
| Server does not start | Open **Журнал сервера**; inspect the actual error; check port `1024–65535`, user limit `2–100` and bandwidth `8000–128000` |
| Host's client works, another phone stays at Connecting | Check its Wi-Fi, host IP and port; verify access to a network without internet, client local-network permissions and hotspot restrictions |
| `SSL handshake failed` | A TLS handshake failed; keep the numeric code and full log, and check client compatibility / certificate handling |
| `Your client does not support Opus` | The client did not advertise Opus; use a client/build that supports it |
| `Wrong server password` | Enter the server password, not the Wi-Fi password; reconnect after changing it |
| `Username already in use` | Give each client a unique username |
| IP is not shown | For the host's hotspot, use Gateway/Router on a connected device; on an existing LAN, use the host's LAN address |
| Connected, but no voice | Check the **Клас** channel, mute/deafen, microphone permission, push-to-talk and selected audio output |
| Audio cuts out or has long delay | Test two devices first; improve signal, compare client audio settings, reduce simultaneous transmitters and check power saving |
| Echo or whistling | Use headphones and push-to-talk; reduce nearby speaker volume |
| Stops after switching apps / locking the screen | Check battery restrictions for both server and client, hotspot auto-off and the persistent notification |
| More devices cannot join Wi-Fi | Check the phone's hotspot client limit; changing server slots cannot raise it |
| Participant counter shows a UDP error | The app's loopback UDP status query failed; inspect the server log; this is not an external-client connectivity test |

To collect a useful report, open **Журнал сервера → Копіювати** after reproducing
the problem. Include host model/Android version, client name/version, network
type, the exact error and whether `127.0.0.1` works on the host. Remove passwords
and personal names from public reports. An empty log is not proof that no TCP
connection reached the server: some early connection events are not logged at
the normal log level.

## Daily operation and data

- Stop the server from the app or its persistent notification.
- Stop, edit settings, then start again to change configuration.
- Device reboot does not automatically restart the server.
- The local log retains at most 80 lines; it is not an audio recording.
- The password, configuration, certificate and private key are kept in app
  private storage. App backup is disabled.
- The app contains no analytics or external-service requests. Android's
  `INTERNET` permission is needed for local TCP/UDP sockets too.
- Android builds accept private IPv4, loopback and link-local peers. This is
  not an authentication boundary: use trusted networks and a server password.
- No public hosting or router port forwarding is part of this setup.

## Build from source

Building needs a computer and internet for SDK/Gradle downloads. Running the
installed APK does not.

| Tool | Version used by this project |
| --- | --- |
| JDK | 17, full JDK including `javac` |
| Gradle | 8.11.1, supplied through the wrapper |
| Android Gradle Plugin | 8.9.1 |
| Android SDK Platform | 35 |
| Android SDK Build-Tools | 35.0.0 |
| Android NDK | 27.0.12077973 |
| SDK CMake | 3.22.1 |

1. Clone the repository:

   ```bash
   git clone https://github.com/JekaK/Local-Mumble-Server.git
   cd Local-Mumble-Server
   ```

2. Open the repository root in Android Studio and select JDK 17 for Gradle.
3. Install the SDK, Build-Tools, NDK and CMake versions above through SDK Manager.
4. Configure the SDK path through Android Studio, `ANDROID_HOME` or your local
   `local.properties`. Do not commit machine-specific SDK paths.
5. Build and run lint:

   ```bash
   ./gradlew assembleDebug lintDebug
   ```

   On Windows: `gradlew.bat assembleDebug lintDebug`.

6. Install `app/build/outputs/apk/debug/app-debug.apk` on a test phone. With USB
   debugging and Android platform-tools, the optional command is:

   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

Native dependencies are vendored. Gradle's `buildServer_*` tasks build all three
ABIs and package the native executable automatically. No additional Termux or
server installation is needed on the phone. For a production distribution,
configure and retain your own release signing key; none is included here.
Locally generated debug keys may differ from the supplied APK's key, so Android
may refuse an in-place update between those builds. Uninstalling clears app
settings and its TLS certificate.

## Verification

See [VALIDATION.md](VALIDATION.md) for recorded results and limitations.

The Linux native test can be reproduced separately:

```bash
cmake -S app/src/main/cpp -B app/build/host -DCMAKE_BUILD_TYPE=Release
cmake --build app/build/host --target umurmur_server -j 4
python3 tests/protocol_smoke.py --binary app/build/host/libumurmur_server.so
```

To include encrypted UDP voice checks, install `cryptography` in a Python
virtual environment:

```bash
python3 -m venv .venv
.venv/bin/python -m pip install cryptography
.venv/bin/python tests/protocol_smoke.py --binary app/build/host/libumurmur_server.so --udp-voice
```

These tests validate authentication, protocol messages and bidirectional Opus
packet transport. They do not measure microphone-to-headphone latency.
`tests/android_smoke.py` also exists; it requires an Android 15 x86-64 AVD named
`mumble_test`. Its run in the original environment was blocked by emulator boot
timeout, not counted as a pass.

## Project layout

| Path | Contents |
| --- | --- |
| `app/src/main/java/ua/school/localmumble/` | Activity, foreground service, configuration, IP detection and UDP probe |
| `app/src/main/cpp/umurmur/` | Native server with Android adaptations |
| `app/src/main/cpp/third_party/` | Mbed TLS, libconfig and protobuf-c source |
| `app/src/main/assets/licenses/` | Third-party notices included in the APK |
| `tests/` | Protocol, encrypted UDP and Android smoke tests |
| `downloads/` | Prebuilt beta APK and its SHA-256 |
| `README.uk.md` | Complete Ukrainian guide |

## License and upstream projects

The repository's existing [Apache 2.0 license](LICENSE) is retained. The imported
Android wrapper and test tools retain their [MIT license](LICENSE-MIT).
Vendored dependencies keep their individual licenses, including LGPL for
libconfig. [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) records their exact
source revisions, license texts and modifications.

Upstream: [uMurmur](https://github.com/umurmur/umurmur),
[Mumble](https://www.mumble.info/), [Mumla](https://mumla-app.gitlab.io/).
This independent project is not endorsed by those teams.
