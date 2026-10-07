# Перевірка 2.0.0-beta3 / Validation

Дата / Date: **2026-10-07**. APK: **LocalMumbleServer-2.0.0-beta3.apk**.
Debug-підпис / Debug-signed beta. Android 8.0+; minSdk 26, targetSdk 35, versionCode 4.

## Українською

### Виправлення iOS Connecting

Сервер раніше записував тип повідомлення (2 байти), довжину (4 байти) й тіло
окремими викликами SSLSocket OutputStream. Це розділяло заголовок на TLS-записи.
[Старий MumbleKit, `_dataReady`](https://github.com/mumble-voip/mumblekit/blob/b9085ec88d305ed28be89ceca14d53670769bcf9/src/MKConnection.m#L793-L830)
читає шестибайтовий заголовок одним викликом і відкидає короткий результат,
не накопичуючи байти для наступного читання. Така поведінка несумісна
з попереднім способом передавання кадрів нашим сервером.

До зміни ядра додано два регресійні тести з таким читачем через справжні
JVM TLS-сокети. **Обидва впали на старому коді:**
`expected:<6> but was:<2>` для TLS 1.2 та TLS 1.3.
Після серіалізації всього кадру в один ByteArray й одного запису в TLS-потік
**обидва тести пройшли**.

Тести перевіряють не лише заголовок: отримання CryptSetup, CodecVersion,
кореневого ChannelState, власного UserState перед ServerSync, завершення
авторизації, ping, голосовий loopback через TLS і відмову за неправильний пароль.
Формат протоколу, транспортне шифрування та узгодження кодеків не змінені.
Версію у повідомленні Version синхронізовано з APK: 2.0.0-beta3.

Лічильник учасників означає авторизовані серверні сесії. Він зростає до
підтвердження, що клієнт прочитав ServerSync; протокол не надає такого
підтвердження. Зелений статус означає відкриті TCP/UDP-сокети сервера.

### Виконані перевірки

| Перевірка | Результат |
| --- | --- |
| `:server-core:test` | 20 тестів; 0 failures / errors / skipped |
| `:app:testDebugUnitTest` | 11 тестів; 0 failures / errors / skipped |
| `:app:assembleDebug` | APK зібраний |
| `:app:lintDebug` | 0 помилок та попереджень у звіті lint |
| `apksigner verify --verbose --print-certs` | Підпис APK v2 перевірений |
| `zipalign -c -P 16 4` | Вирівнювання ZIP та native-бібліотек перевірене |
| ELF LOAD alignment | AndroidX graphics-path для всіх 4 ABI має вирівнювання щонайменше 16384 байти |
| Вміст APK | Єдині `.so` — AndroidX graphics-path; їхні байти ідентичні beta2; native-сервера немає |
| Версія APK / ядра | versionCode 4; 2.0.0-beta3; новий рядок Version знайдений у DEX |
| Документація | Українська й англійська інструкції та сценарій Connecting оновлені |

Окрім двох нових TLS-регресій, серверні тести перевіряють пароль/імена/місткість,
Opus і CELT, двостороннє передавання через TLS та зашифрований UDP, mute/deafen,
nonce resync, повторні підключення, звільнення портів, protobuf/varint,
незалежні OCB2-вектори та XEX* forgery. Android-тести перевіряють валідацію
та ViewModel: команди, підтвердження сервісу, повторні натискання, помилки,
timeout, SavedStateHandle й життєвий цикл підписки repository.

Усі чотири Gradle-завдання завершилися одним успішним запуском.
Залежності й архітектуру Fragment / Compose / ViewModel не змінювали.

### APK та підпис

- Розмір: **10431433 байт**.
- SHA-256: `514ba825760a98dd110dd900b31d3c0ffabcdc5fa9838df13d8bb8824b2f19df`.
- Сертифікат підпису SHA-256: `839d33c36b8a2a8129ea5657ca43f0b49ea7c1ce4567c8cea2a3a408f353e1a9`.
- Підпис відрізняється від опублікованих попередніх APK. Перед встановленням
  видаліть старий серверний застосунок; його налаштування й TLS-ідентичність очистяться.
  Окремий голосовий клієнт видаляти не потрібно.
- Ключ beta3 збережений окремо як `LocalMumbleServer-beta-signing.jks`
  для наступних оновлень; приватний ключ не включений у Git-репозиторій.

Збірка: JDK 17; Gradle 8.11.1; AGP 8.9.1; Kotlin / Compose compiler 2.1.20;
Compose BOM 2025.04.01; Fragment 1.8.6; Lifecycle 2.8.7; SDK / Build Tools 35.

### Межі перевірки

**Фізичні Android/iPhone, емулятор та instrumentation/UI-тести не запускалися.**
Регресія відтворює читання заголовка MumbleKit у JVM-клієнті; це не запуск
самого iOS-застосунку або Android TLS-провайдера. Виправлено відтворену
несумісність, але усунення конкретного зависання на телефоні користувача
ще не підтверджене тестом на його пристрої.

Мікрофон/динамік, затримка звуку, робота з вимкненим екраном, місткість класу,
реальні Fragment/Compose lifecycle та системні діалоги залишаються неперевіреними.
Тести пересилають закодовані пакети й не вимірюють акустичну затримку.
Незалежний Python-клієнт перевіряв beta1; у цій перевірці його не запускали.

## English

Beta3 fixes outgoing TLS framing that is incompatible with legacy iOS MumbleKit.
The old writer used separate writes for the two-byte type, four-byte length and
body. MumbleKit's linked header reader drops a read shorter than six bytes.
The server now serializes the complete frame and sends it in one TLS-stream write.
This avoids splitting the header between TLS records; it does not establish a
general guarantee that arbitrary stream clients can ignore partial reads.

**Before the patch, both new regression tests failed with
`expected:<6> but was:<2>` over TLS 1.2 and TLS 1.3. After the patch, both passed.**
They use real JVM TLS sockets and check channel/user state before ServerSync,
crypto and codec setup, authentication, ping, tunneled voice loopback and rejection.
The server's advertised release string now matches the APK version.

**31 tests passed: 20 core protocol/socket tests and 11 Android validation/ViewModel
unit tests; zero failures, errors or skipped tests.** All four Gradle tasks completed
in one successful run. APK build passed; the lint report contains no errors or
warnings. Existing Fragment/Compose/ViewModel architecture and dependency versions
are unchanged. APK v2 signature verification, 16 KiB ZIP/native alignment and ELF
LOAD alignment checks passed. The four native graphics helpers are byte-identical
to beta2; there is no native voice server.

APK: **10431433 bytes**. SHA-256:
`514ba825760a98dd110dd900b31d3c0ffabcdc5fa9838df13d8bb8824b2f19df`.
Signing certificate SHA-256:
`839d33c36b8a2a8129ea5657ca43f0b49ea7c1ce4567c8cea2a3a408f353e1a9`.
The debug signing key differs from earlier published APKs. Uninstall the old server
app first; this clears its preferences and TLS identity. The separate voice client
is unaffected. The beta3 key has a separate private backup named
`LocalMumbleServer-beta-signing.jks` for future updates; it is not in this repository.

**No physical Android/iPhone, emulator or instrumentation/UI test was run.**
The regression mimics the upstream header reader; it does not execute the iOS app
or Android TLS provider. This is a tested interoperability correction, not proof
that the user's specific iPhone hang is resolved. Real microphone-to-speaker audio,
latency, screen-off behavior, classroom capacity and Android UI lifecycle remain
unverified. The independent Python protocol check was not repeated.
