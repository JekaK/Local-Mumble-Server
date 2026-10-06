# Local Mumble Server

[Українська](README.uk.md) · **English**

An offline voice server hosted directly on an Android phone. Turn on the phone's Wi-Fi hotspot, start the server, and let nearby devices join through a Mumble-compatible client. The host can speak through a separate client on the same phone.

**Version 2 replaces the former native uMurmur engine with a Kotlin implementation.** The Android application and server logic are Kotlin. There is no Murmur/uMurmur executable, JNI library, NDK, CMake, Termux, root requirement, or runtime server download.

[Download APK](https://github.com/JekaK/Local-Mumble-Server/raw/refs/heads/master/downloads/LocalMumbleServer-2.0.0-beta1.apk) · [Installation](#step-by-step-installation) · [Troubleshooting](#troubleshooting) · [Validation report](VALIDATION.md)

| Android | Version | Runtime | Voice transport |
| --- | --- | --- | --- |
| 8.0+ | 2.0.0-beta1 | Kotlin + Android platform APIs | TLS 1.2+ and encrypted UDP |

> **Beta:** automated tests exercise the Kotlin server's wire protocol and voice-packet transport. They do not establish real microphone-to-speaker latency, classroom capacity, or compatibility with every iPhone client. Physical Android/iPhone interoperability still needs a device test. See [VALIDATION.md](VALIDATION.md) for the actual checks and limitations.

## How it works

1. Android's system hotspot creates the local network.
2. The foreground service runs the Kotlin server inside the app process.
3. The host's voice client connects to `127.0.0.1:64738`.
4. Other devices connect to the Android phone's hotspot address.
5. The server authenticates participants and relays encoded audio between them.

Mumble is the **client protocol** here. Murmur and uMurmur are server implementations; neither is part of this version. This is an independent, intentionally small single-channel implementation, not the official Mumble server.

The app **does not contain a microphone client**. Install a voice client separately on the host and every participant device.

## Requirements

| Item | Requirement |
| --- | --- |
| Host | Android 8.0 or newer with a working Wi-Fi hotspot |
| Architecture | No native ABI restriction; ARM and x86 devices use the same APK |
| Participants | Mumble-compatible clients using TLS 1.2+ |
| Codecs | Opus on all clients, or a common CELT bitstream version on all clients |
| Network | Local IPv4; TCP and UDP use the same server port |
| Internet | Needed to download apps/build dependencies; not needed for voice sessions |
| RAM/CPU | Minimum hardware and classroom capacity have not been measured |

Opus is preferred when every connected client supports it. Legacy CELT-only clients are admitted when the whole group has a shared CELT codec. A client with no common codec receives an explicit rejection. There is **no audio transcoding**.

The server speaks the widely implemented legacy Mumble UDP format. It advertises protocol compatibility with Mumble 1.2.4, while the application version is 2.0.0-beta1.

## Step-by-step installation

### 1. Download the applications before going offline

1. Download [LocalMumbleServer-2.0.0-beta1.apk](https://github.com/JekaK/Local-Mumble-Server/raw/refs/heads/master/downloads/LocalMumbleServer-2.0.0-beta1.apk).
2. On Android participant devices, install [Mumla](https://mumla-app.gitlab.io/) through [F-Droid](https://f-droid.org/packages/se.lublin.mumla/) or its official distribution.
3. On iPhone, install a Mumble-compatible client and test it with this server before the lesson.
4. Install a client on the host phone too if the host needs to speak.
5. Keep the APK locally; the server runs without an internet connection.

Verify the APK against [downloads/SHA256SUMS](downloads/SHA256SUMS). This repository publishes a **debug-signed beta**, not a Play Store release.

**Upgrading from 1.0.0-beta1:** this published APK has a different debug signing
certificate. Uninstall the old server app before installing version 2. This clears
its settings and TLS identity; your separate voice-client app is unaffected.

### 2. Install the APK on the Android host

1. Open the downloaded APK.
2. If prompted, allow the browser/file manager to install unknown apps.
3. Install and open **Локальний Mumble сервер**.
4. Allow notifications when prompted. The notification shows that the foreground service is active and provides a stop action.
5. If the phone's battery settings offer an unrestricted mode for the app, use it during a session.

The app requests network, foreground-service, wake-lock and notification permissions. It does not request microphone access.

### 3. Enable the phone's Wi-Fi hotspot

1. Tap **Відкрити налаштування точки доступу**, or open Android's system hotspot settings.
2. Choose a network name and a WPA2/WPA3 password.
3. Turn on the hotspot and disable automatic hotspot shutdown if available.
4. Connect participant devices to this Wi-Fi network.
5. Keep the connection when Android/iOS warns that this network has no internet.

Hotspot support and its device limit depend on the phone. Raising the app's user limit does not increase the operating system's hotspot limit.

### 4. Start the Kotlin server

1. Leave the port at **64738**, unless another app already uses it.
2. Set the participant limit; default: **30**, supported setting: 2–100.
3. Leave the voice-traffic limit at **48000 bits/s** initially.
4. Set an optional server password, independent of the hotspot password.
5. Tap **Запустити сервер**.
6. Wait for **Сервер працює**.

The first start generates a self-signed TLS identity in private app storage. The status becomes running only after both TCP and UDP sockets are bound.

The traffic setting is advertised to clients and enforced by a per-user token bucket. It includes an allowance for packet overhead; it is **not a guaranteed audio-codec bitrate**.

### 5. Connect the host's voice client

Create a connection in Mumla:

| Field | Value |
| --- | --- |
| Address | `127.0.0.1` |
| Port | `64738`, or the configured port |
| Username | A unique name, such as `Teacher` |
| Password | The optional server password |

Accept the local server's self-signed certificate if the client prompts. The channel is **Клас**. Use headphones and push-to-talk to reduce acoustic feedback. The host client occupies one participant slot.

### 6. Connect participant devices

1. Join the host phone's hotspot.
2. Read the address under **Для учнів** in the server app.
3. Create a client connection using that address, the configured port, a unique username, and the server password.
4. Accept the expected local certificate.
5. Confirm that the client enters **Клас** and displays other participants.

| Connection | Address |
| --- | --- |
| Client on the host phone | `127.0.0.1` |
| Client on the host's hotspot | The Android host's hotspot IP |
| Client on an existing router's Wi-Fi | The Android host's LAN IP, **not** the router's gateway |

If the hotspot IP is not listed, inspect Wi-Fi details on a participant device: its **Gateway/Router** address usually points to the hotspot phone. This shortcut applies to a phone hotspot, not to an unrelated router.

Do not use `127.0.0.1` on participant phones: it refers to that participant phone itself. The host IP may change after restarting the hotspot.

### 7. Verify the session before a lesson

1. Start with the host and one participant.
2. Confirm both directions of speech, not just the connected status.
3. Test with the screen off and the host client open.
4. Add participants gradually.
5. Repeat on an iPhone if iPhone participation is required.
6. Adjust the traffic limit and client audio settings only after the basic test works.

The foreground service and wake lock help keep the app running. Manufacturer power management may still interrupt it. The configured 100-user maximum is a setting, not a tested capacity claim.

## iPhone connection checks

The earlier iPhone report was a client staying at “Connecting”; its cause was not established. A Kotlin rewrite alone does not prove that issue is fixed.

- Allow the client's local-network permission in iOS settings; [Apple's instructions](https://support.apple.com/en-us/102229).
- Confirm the iPhone is connected to the host hotspot and uses the correct host IP/port.
- Accept the new server certificate. Version 2 uses a separate identity, so its fingerprint changes from version 1.
- Inspect **Журнал сервера**: it distinguishes a TCP/TLS connection, a successful login, and a rejection reason.
- A “No common audio codec” rejection requires clients with compatible codecs; the server cannot transcode between Opus and CELT.
- The [legacy Mumble iPhone app](https://apps.apple.com/us/app/mumble/id443472808) has an [unmaintained upstream](https://github.com/mumble-voip/mumble-iphoneos). Verify that specific app/device combination in practice.

No real iPhone compatibility or latency claim is made without a device test.

## Troubleshooting

| Symptom | Check or correction |
| --- | --- |
| Server does not start | Read the log; check port conflicts and configuration ranges |
| Client stays at Connecting | Host IP, port, hotspot membership, local-network permission, certificate and server log |
| TLS failure | Client must support TLS 1.2+; inspect its certificate prompt |
| Wrong server password | Enter the app's server password, not the hotspot password |
| Username already in use | Give each device a different username; comparison is case-insensitive |
| Server full | Disconnect unused clients or increase the participant setting |
| No common audio codec | Use Opus-compatible clients throughout the group, or clients sharing the same CELT version |
| Connected but no sound | Check push-to-talk, client microphone permission, mute/deafen and a two-device speech test |
| Delayed/interrupted voice | Check signal strength, hotspot capacity, power management and client audio settings |
| Stops with screen off | Check battery restrictions and automatic hotspot shutdown |
| Certificate changed | Expected once when switching from the old native version to this Kotlin version |
| APK update rejected | The installed APK and new APK may use different debug signing keys |

TCP tunneling is available when UDP is unavailable. A confirmed UDP endpoint is preferred; receiving a tunneled voice packet switches that client back to TLS. An inactive UDP endpoint expires after 15 seconds.

## Scope, privacy and limits

Implemented: one voice channel, unique guest usernames, optional server password, participant count, codec negotiation, TLS control and tunneled voice, OCB2-AES128 UDP voice, ping, nonce resynchronization, self-mute/self-deafen, traffic bounds and clean shutdown.

Not implemented: registered accounts, ACL administration, channel creation, kick/ban controls, text chat, whisper targets, recording, web access, audio transcoding, or a built-in microphone client.

The server forwards encoded voice and does not decode or record it. Configuration, server password and TLS identity stay in the Android app sandbox; Android backup is disabled. Logs contain connection/user information, not audio or server passwords. The TLS private key is stored in private app storage.

Only loopback, private and link-local IPv4 peers are accepted. This address filter is **not an authentication boundary**; protect the hotspot and set a server password.

## Build and test

Toolchain: **JDK 17**, **Gradle 8.11.1**, **AGP 8.9.1**, **Kotlin 2.1.20**, **Android SDK 35 / Build Tools 35.0.0**. No NDK or CMake is required.

```bash
git clone https://github.com/JekaK/Local-Mumble-Server.git
cd Local-Mumble-Server
./gradlew :server-core:test :app:assembleDebug :app:lintDebug
```

On Windows, use `gradlew.bat`. Set the Android SDK path in Android Studio or an untracked `local.properties` file. First-time dependency downloads need internet.

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

Run the identical Kotlin core on a development computer:

```bash
./gradlew :server-core:installDist
server-core/build/install/server-core/bin/server-core 64738 school-test
```

The third optional launcher argument sets its identity directory. This desktop launcher is for protocol testing; Android runs the core directly inside its foreground service.

A rebuild uses your machine's debug key. Updating an APK signed with another key fails; uninstalling clears app data and its certificate.

## Project layout and licenses

| Path | Purpose |
| --- | --- |
| `app/src/main/kotlin/` | Android activity, foreground service and network helpers |
| `server-core/src/main/kotlin/` | Kotlin server, protobuf wire codec, TLS identity and UDP transport |
| `server-core/src/test/kotlin/` | Unit and socket integration tests |
| `downloads/` | Current APK and SHA-256 checksum |
| `licenses/` | Mumble BSD notice for the Kotlin OCB2 adaptation |
| `VALIDATION.md` | Checks performed and remaining device-test limitations |

The root [Apache-2.0 license](LICENSE) applies to original server-core code. The imported Android wrapper and its Kotlin adaptation retain [MIT](LICENSE-MIT). The OCB2 adaptation retains Mumble's BSD notice. Kotlin's standard library uses Apache-2.0. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

There is no bundled native Mumble server. Protocol compatibility and a Kotlin adaptation of the transport encryption algorithm do not add Murmur or uMurmur to the app.
