# Перевірка 2.0.0-beta2 / Validation

Дата / Date: **2026-10-07**. APK: **LocalMumbleServer-2.0.0-beta2.apk**.
Debug-підпис / Debug-signed beta. Android 8.0+; minSdk 26, targetSdk 35, versionCode 3.

## Українською

MainActivity замінено контейнером для ServerFragment. Екран написаний на
Jetpack Compose / Material 3; XML-розмітку й старі drawable-компоненти форми видалено.
Логіка форми та команд винесена у ServerViewModel, дані — у ServerRepository.
Пакети: presentation, domain, data, service, di. Kotlin-ядро голосового сервера
залишилося без змін; Murmur/uMurmur не додавалися.

### Виконані перевірки

| Перевірка | Результат |
| --- | --- |
| `:server-core:test` | 18 тестів; 0 failures / errors |
| `:app:testDebugUnitTest` | 11 тестів; 0 failures / errors |
| `:app:assembleDebug` | APK зібраний |
| `:app:lintDebug` | 0 помилок; 1 попередження про новішу версію Compose BOM |
| `apksigner verify --verbose --print-certs` | Підпис APK v2 перевірений |
| `zipalign -c -P 16 4` | Вирівнювання ZIP та native-бібліотек перевірене |
| ELF LOAD alignment | AndroidX graphics-path для всіх 4 ABI має вирівнювання 16384 байти |
| Вміст APK | Єдині `.so` — AndroidX graphics-path для Compose; native-сервера немає |
| Документація | Українська й англійська інструкції та структура пакетів оновлені |

Нові тести охоплюють:

- Некоректні, переповнені й граничні числові параметри; пароль, Unicode та керівні символи.
- Узгодженість допустимих параметрів UI з ServerOptions голосового ядра.
- Блокування запуску при помилках форми й очищення помилки відредагованого поля.
- Запуск/зупинку, повторні натискання та очікування підтвердження команди сервісом.
- Помилку запуску, повторну спробу й timeout без зависання кнопок.
- Відновлення числової форми через SavedStateHandle; незбережений пароль не потрапляє в Bundle.
- Заборону редагування при активному сервері, реактивні журнал і лічильник.
- Припинення спостереження repository без підписників та відновлення після повернення.

18 серверних тестів перевіряють TLS-вхід, пароль/імена/місткість, Opus і CELT,
передавання через TLS та зашифрований UDP, mute/deafen, nonce resync, повторні
підключення, звільнення портів, protobuf/varint, незалежні OCB2-вектори та XEX* forgery.

Виправлено знайдену lint-помилку API 27 у темі, зберігши підтримку API 26.
Поточний Compose BOM зафіксовано на 2025.04.01; попередження про новішу версію
не приховується. Перша повна збірка завантажувала залежності; фінальна перевірка
тих самих Gradle-завдань успішно виконана також з `--offline`.

### APK та підпис

- Розмір: **10876744 байт**.
- SHA-256: `c81a20546f74db59c267d76a42a0b268ffd3b4a987849ee2346cbdea42356815`.
- Сертифікат підпису SHA-256: `a028d9a6f8ba2d404189e24480cc806fbf22298048f932c71a2137a706a31795`.
- Підпис відрізняється від опублікованого 2.0.0-beta1. Перед встановленням
  видаліть попередній серверний застосунок; це очистить його налаштування й TLS-ідентичність.
- JDK 17; Gradle 8.11.1; AGP 8.9.1; Kotlin / Compose compiler 2.1.20;
  Compose BOM 2025.04.01; Fragment 1.8.6; Lifecycle 2.8.7; SDK / Build Tools 35.

### Межі перевірки

**Фізичний Android, емулятор та instrumentation/UI-тести не запускалися.**
ViewModel перевірений JVM-тестами; реальний поворот екрана, системні діалоги,
клавіатура й вигляд Compose на телефоні ще не перевірені. Перевірка збереженого
стану відтворює відновлення SavedStateHandle, а не повний Android process-death.

Android/iPhone, мікрофон/динамік, затримка звуку, екран вимкнений та місткість класу
залишаються неперевіреними. Причина попереднього iPhone Connecting не встановлена.
Тести пересилають закодовані пакети й не вимірюють акустичну затримку.
Незалежний Python-клієнт перевіряв beta1; для beta2 його повторно не запускали.

## English

The Android UI now uses a Fragment-hosted Compose / Material 3 screen and a
ViewModel exposing immutable StateFlow state. Repository implementations own
Android preferences, service commands and network probes. The existing Kotlin
voice engine is unchanged. XML screen layouts and their old button/card drawables
were removed; Android manifests, styles, strings and the icon remain resources.

**29 tests passed: 18 core protocol/socket tests and 11 validation/ViewModel tests;
zero failures and errors.** Debug APK build passed. Lint reports **0 errors and
1 warning** about a newer Compose BOM; the current BOM is intentionally pinned.
Final verification of all four Gradle tasks also succeeded offline.

The new tests cover invalid/boundary settings and core compatibility, password
rules, field errors, start/stop acknowledgement, duplicate taps, startup failures,
retry/timeout handling, numeric saved-state restoration, in-memory unsaved
passwords, running-state form locking, reactive logs/counts, and stopping/resuming
repository observation with UI subscribers.

APK: **10876744 bytes**. SHA-256: `c81a20546f74db59c267d76a42a0b268ffd3b4a987849ee2346cbdea42356815`.
APK v2 signature and ZIP alignment (`zipalign -c -P 16 4`) passed. Compose brings
`libandroidx.graphics.path.so` for ARMv7, ARM64, x86 and x86_64; all ELF LOAD
segments are aligned to 16384 bytes. No native voice server is packaged.

The debug signing certificate differs from the published beta1. Uninstall the
previous server app first; its preferences and TLS identity will be cleared.

**No physical-device, emulator or instrumentation/UI run was performed.** JVM
ViewModel tests do not establish real Fragment/Compose rotation, Android process
death, keyboard/permission flows or visual behavior. Android/iPhone audio,
latency, screen-off behavior and classroom capacity remain unverified. The prior
iPhone Connecting issue is not claimed fixed. The earlier independent Python
protocol check belonged to beta1 and was not repeated for beta2.
