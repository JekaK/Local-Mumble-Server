# Перевірка 2.0.0-beta1 / Validation

Дата / Date: **2026-10-06**. APK: **LocalMumbleServer-2.0.0-beta1.apk**.
Це бета з debug-підписом / This is a debug-signed beta.

## Українською

Поточний застосунок і серверна логіка переписані на Kotlin. uMurmur, його
виконуваний файл, C/C++-джерела, Mbed TLS, libconfig, protobuf-c, NDK- та
CMake-збірка видалені з поточної версії.

### Фактично виконано

| Перевірка | Результат |
| --- | --- |
| `:server-core:test` | **18 тестів, 0 помилок і 0 невдалих тестів** |
| `:server-core:installDist` | Kotlin-ядро зібране для незалежної перевірки клієнтом |
| `:app:assembleDebug` | Android APK зібраний |
| `:app:lintDebug` | 0 помилок; 31 попередження оформлення, доступності та локалізації UI |
| Незалежний Python-клієнт протоколу | TLS-вхід, пароль, учасники, ping, Opus-пакети TLS та OCB2 UDP пройшли |
| `apksigner verify` | Підпис APK v2 перевірений |
| `zipalign -c 4` | Вирівнювання APK перевірене |
| Перевірка вмісту APK | Немає `lib/` чи `.so`; ліцензійні повідомлення присутні |
| README обома мовами | Локальні посилання, внутрішні якорі й блоки коду перевірені |

Незалежний клієнт використовував іншу реалізацію wire-протоколу й AES/OCB2 для
коротких пакетів. Це одноразова додаткова перевірка; регресійні тести в репозиторії
написані на Kotlin.

### Що охоплюють Kotlin-тести

- Вхід двох клієнтів, UTF-8-назва каналу, пароль, зайняте ім’я без урахування
  регістру, заповнений сервер, лічильник і keep-alive.
- Передавання Opus-пакетів в обидва боки через TLS та зашифрований UDP,
  loopback-голос і повернення до TLS після тунельованого пакета.
- CELT зі справжнім signed bitstream ID, вибір спільного кодека та чітка
  відмова за відсутності сумісного кодека.
- Self-mute і self-deafen, відхилення спроби змінити стан іншого учасника.
- Ресинхронізація client nonce, відповідь на запит server nonce.
- Невідомі повідомлення, обрізаний голосовий пакет, надмірний розмір
  керівного кадру й повідомлення про відключення.
- Збереження TLS-ідентичності після перезапуску, звільнення TCP/UDP-портів,
  20 послідовних підключень/відключень без вичерпання слотів.
- Граничні Mumble varint, protobuf, UTF-8 і некоректні вхідні дані.
- Опубліковані незалежні OCB2-вектори, довжини 0–1000 байтів,
  втрата/запізнення/повторення/підміна пакетів і перехід nonce через 255.
- XEX* counter-cryptanalysis: модифікація критичного блока й відхилення
  незалежно побудованого підробленого пакета.

### Артефакт і середовище

- APK: **948801 байт**, Android 8.0+ (minSdk 26), targetSdk 35.
- SHA-256: `81eb76b9a4c86970f93f44fd7bdd45eaa0daaa1d158822fbbecae93bedb214cb`.
- JDK 17, Gradle 8.11.1, AGP 8.9.1, Kotlin 2.1.20,
  Android SDK 35 / Build Tools 35.0.0.
- Одна збірка APK для архітектур, що підтримуються Android: native ABI немає.
- Debug-сертифікат APK відрізняється від 1.0.0-beta1. Для переходу потрібно
  видалити попередній серверний застосунок; його дані очистяться.

### Що ще не перевірено

**Встановлення й запуск цієї Kotlin-версії на фізичному Android не виконувалися
в цьому середовищі. Реальний Android/iPhone-клієнт, мікрофон, динамік,
затримка звуку, енергозбереження та урок із багатьма телефонами не тестувалися.**

У середовищі немає KVM чи підключеного Android-пристрою; успішного
емуляторного/instrumentation-прогону не заявляється.

Повідомлення власника про успішний запуск старої версії стосувалося
1.0.0-beta1 з native-рушієм. Воно не є перевіркою нової Kotlin-версії.
Попередня причина iPhone “Connecting” не встановлена; нові перевірки кодека
та журнал допомагають діагностувати її, але не доводять її усунення.

Тести перевіряють передавання закодованих пакетів. Вони не декодують голос і
не вимірюють акустичну затримку чи якість звуку.

## English

The Android app and server logic are Kotlin. The current version removes
uMurmur, its executable, the vendored C/C++ dependencies and NDK/CMake tasks.

Completed checks:

| Check | Result |
| --- | --- |
| Kotlin unit/socket suite | **18 tests; 0 failures; 0 errors** |
| Android debug build | Passed |
| Android lint | 0 errors; 31 UI/accessibility/localization warnings |
| Independent protocol client | TLS login, rejection, participant count, ping, bidirectional Opus TLS and encrypted UDP passed |
| APK v2 signature / ZIP alignment | Verified |
| APK payload | No native libraries; required runtime notices present |
| Documentation | Both languages, local links, anchors and code fences checked |

The suite covers codec negotiation, legacy signed CELT IDs, self-mute/deafen,
voice loopback, UDP replay/tamper/late/loss behavior, TLS fallback, nonce
resynchronization, malformed frames, certificate persistence, socket shutdown
and repeated connections. OCB2 is checked against published independent vectors
and an independently constructed XEX* forgery.

Final APK: **948801 bytes**, minSdk 26, targetSdk 35.
SHA-256: `81eb76b9a4c86970f93f44fd7bdd45eaa0daaa1d158822fbbecae93bedb214cb`.

The debug signing certificate differs from the version-1 APK; uninstall the
old server app before installing this version. Uninstalling clears app data.

**This Kotlin APK has not been installed/run on a physical Android device in
this environment. Android/iPhone microphone-to-speaker audio, real latency,
screen-off behavior and classroom capacity remain unverified.** There is no
successful emulator/instrumentation run to report. The earlier user-reported
startup belonged to the old native version and does not validate this rewrite.
The earlier iPhone “Connecting” issue is not claimed fixed without device logs
and a successful device test.
