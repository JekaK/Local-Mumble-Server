# Third-party notices / Сторонні компоненти

Version 2.0.0-beta2 contains an original Kotlin server implementation. Murmur,
uMurmur, Mbed TLS, libconfig and protobuf-c are not bundled or used by this version.
Their sources and the version-1 APK were removed from the current repository tree.
Historical commits retain the older implementation.

| Component | Use | License |
| --- | --- | --- |
| Kotlin standard library 2.1.20 | Runtime language library | Apache-2.0 |
| JetBrains annotations | Kotlin transitive annotations | Apache-2.0 |
| AndroidX Compose (BOM 2025.04.01), Material 3, Fragment 1.8.6, Lifecycle 2.8.7 and transitive AndroidX libraries | Compose UI, Fragment hosting and lifecycle/ViewModel integration | Apache-2.0 |
| AndroidX graphics-path 1.0.1 | Native path helper brought in by Compose; unrelated to the voice server | Apache-2.0 |
| kotlinx.coroutines 1.10.1 | StateFlow and structured concurrency; coroutines-test is test-only | Apache-2.0 |
| Mumble OCB2 algorithm adaptation | Kotlin implementation in `server-core/.../Ocb2.kt`; no native code | BSD-3-Clause |
| Mumble OCB2 test vectors | Independent published vectors in Kotlin tests | BSD-3-Clause |
| JUnit 4.13.2 / Hamcrest | Tests only, not packaged in the APK | EPL-1.0 / BSD-3-Clause |
| Gradle wrapper 8.11.1 | Build tooling, not a runtime server | Apache-2.0 |

The Mumble OCB2 adaptation follows
[CryptStateOCB2.cpp](https://github.com/mumble-voip/mumble/blob/master/src/crypto/CryptStateOCB2.cpp),
including the XEX* counter-cryptanalysis. Test vectors follow
[TestCrypt.cpp](https://github.com/mumble-voip/mumble/blob/master/src/tests/TestCrypt/TestCrypt.cpp).
The BSD notice is preserved in [licenses/MUMBLE-BSD-3-Clause.txt](licenses/MUMBLE-BSD-3-Clause.txt)
and in the APK's `assets/licenses/mumble-bsd.txt`.

Protocol fields were reimplemented from
[Mumble.proto](https://github.com/mumble-voip/mumble/blob/master/src/Mumble.proto).
No generated Java/C protobuf classes or protobuf runtime are included.

Android supplies the TLS, AES primitive, sockets, RSA signing and certificate APIs.
No external crypto provider or native server executable is bundled.

The root [Apache-2.0](LICENSE) license applies to original server-core code.
The imported Android wrapper and its Kotlin adaptation retain [MIT](LICENSE-MIT).
The Kotlin OCB2 adaptation retains the Mumble BSD notice. Applicable runtime
license texts are also shipped in `app/src/main/assets/licenses/`.

Українською: поточна версія використовує власний Kotlin-сервер. Код Murmur/uMurmur
і попередні native-залежності не входять до APK. Збережено BSD-повідомлення для
Kotlin-адаптації алгоритму OCB2 та Apache-2.0 для бібліотеки Kotlin; Android-оболонка
зберігає MIT. Сумісність із протоколом Mumble не означає використання його готового
сервера або схвалення цього проєкту розробниками Mumble.
