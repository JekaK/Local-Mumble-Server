# Перевірка збірки 1.0.0-beta1

Дата: 2026-10-06. Це тестова, debug-підписана збірка, не production-реліз.

## Пройдено

- `assembleDebug lintDebug`: успішне завершення, 0 помилок lint.
  Є 31 попередження, переважно щодо українських рядків без string resources;
  також щодо autofill, accessibility labels, зайвого фону й невикористаного кольору.
- Перевірка APK Signature Scheme v2 через `apksigner verify`: успішна.
- APK містить ARM64, ARMv7, x86-64; minimum SDK 26, target SDK 35.
- ARM64 executable має PIE, Android dynamic linker і 16 KiB ELF LOAD alignment.
- Сервер із тих самих native-джерел зібраний і протестований на Linux:
  TLS, неправильний пароль, два клієнти, унікальні імена, канал «Клас»,
  UDP status/nonce, лічильник, CryptSetup та keep-alive.
- Opus-пакети передаються в обидва боки через TLS і зашифрований UDP;
  тест перевіряє OCB2 authentication. Graceful shutdown пройдено.

Це автоматичні тести протоколу, не прослуховування реального аудіо.

## Ще не підтверджено

- Автоматичний Android smoke test встановлення, запуску native executable,
  foreground service та UI.
  У середовищі без KVM емулятор Android 15 не завершив завантаження в межах
  тестового часу. Це блокер середовища; тест APK не зарахований як успішний.
- Фізичний Android-телефон: доступ учнів через хотспот, його клієнтський ліміт,
  ізоляція пристроїв, енергозбереження, нагрів та одночасна робота Mumla.
- Реальна затримка від мікрофона до навушників і навантаження повного класу.

## Зворотний зв'язок із фізичного пристрою

2026-10-06 автор проєкту повідомив, що встановив APK і запустив сервер
на фізичному Android-пристрої без помилки. Модель телефона, версія Android,
журнал та перевірка реального аудіо не надані. Це підтвердження запуску
від користувача, а не завершений Android smoke test.

Окремо повідомлено, що iPhone-клієнт залишається на «Connecting».
Причина не встановлена: даних журналу спроби підключення ще немає.
Сумісність iPhone не вважається підтвердженою.

## Перевірка на телефоні перед уроком

1. Встановити APK, запустити сервер з паролем і дочекатися «Сервер працює».
2. На тому самому телефоні підключити Mumla до `127.0.0.1:64738`.
3. З другого телефона через хотспот підключитися до IP/шлюзу телефона-хоста.
4. У навушниках перевірити голос в обидва боки й затримку.
5. Відкрити Mumla, погасити екран і перевірити, що сервер не зупинився.
6. Зупинити/запустити сервер, повторити підключення.
7. Поступово додати решту учнів. Не вважати параметр «30 учасників»
   гарантією, що хотспот підтримує 30 пристроїв.

Автотести та інструкції збирання включені у вихідний код. Паролі, ключі
сертифікатів із телефона та release signing keys в архів не включаються.

---

# Validation — English

Version: **1.0.0-beta1**. Date: **2026-10-06**. This is a debug-signed beta,
not a production release.

## Passed automated checks

- `assembleDebug lintDebug` completed successfully: 0 lint errors and 31
  warnings. Warnings concern hardcoded Ukrainian strings, autofill,
  accessibility labels, background overdraw and an unused color.
- APK Signature Scheme v2 verification passed with `apksigner verify`.
- APK packaging contains ARM64, ARMv7 and x86-64; minimum SDK 26, target SDK 35.
- The ARM64 executable uses PIE, the Android dynamic linker and 16 KiB ELF
  LOAD alignment.
- The native server compiled from the same source passed Linux protocol tests:
  TLS, incorrect password rejection, two clients, duplicate username
  rejection, the Unicode **Клас** channel, UDP status/nonce, participant count,
  CryptSetup and authenticated keep-alive.
- Opus packets were forwarded in both directions through TLS and encrypted
  UDP, with OCB2 authentication checked. Graceful shutdown passed.

These are packet transport checks, not recorded audio playback or measured
microphone-to-headphone latency.

## Physical-device report

On 2026-10-06 the project owner reported installing the APK and starting the
server on a physical Android device without an error. Device model, Android
version, logs and end-to-end audio results were not supplied. This is a
user-reported startup result, not a completed Android smoke test.

The owner also reported an iPhone client remaining at **Connecting**. No
connection log is available and its cause has not been identified. iPhone
interoperability is not considered validated.

## Not yet validated

- The automated Android installation/service/UI smoke test: the unaccelerated
  Android 15 emulator did not finish booting within six minutes. The run was
  blocked before APK installation, not counted as a pass or an app crash.
- Host-to-student access through a physical hotspot, client capacity,
  isolation rules, battery policies, heating and concurrent Mumla use.
- Real audio delay and sustained full-class load.

## Physical-device acceptance check

1. Install the APK, start the password-protected server and wait for its
   running status.
2. Connect Mumla on the host to `127.0.0.1:64738`.
3. Join the hotspot from a second device and connect to the host IP/gateway.
4. Use headphones to test voice in both directions and observe actual delay.
5. Switch to Mumla and turn off the host's screen; verify the server continues.
6. Stop/restart the server and repeat the connection.
7. Add devices gradually. A 30-user server setting is not a promise of
   30-device hotspot capacity.

Source, dependency licenses and test scripts are included in this repository.
Device passwords, generated certificate/private-key files and release signing
keys are not included.
