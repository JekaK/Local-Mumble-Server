# Third-party code

| Component | Source revision | License |
| --- | --- | --- |
| uMurmur 0.5.1 | https://github.com/umurmur/umurmur — `0b03cf36fbaacc16d5ec719e5bb773fb192542d4` | BSD-3-Clause |
| Mbed TLS 3.6.7 | https://github.com/Mbed-TLS/mbedtls — `068ff080b369adfac81509f9b57b2afabaf82dc5` | Apache-2.0 (selected from dual license) |
| libconfig 1.8.2 | https://github.com/hyperrealm/libconfig — `a42cb47c1526a4f2ed025fcbb2289863375bc898` | LGPL-2.1-or-later |
| protobuf-c 1.5.2 | https://github.com/protobuf-c/protobuf-c — `4719fdd7760624388c2c5b9d6759eb6a47490626` | BSD-2-Clause |

Full license texts are included with the vendored source and under APK assets/licenses.
The complete source needed to rebuild and relink the statically linked libconfig
is supplied with this application. The Android wrapper is MIT-licensed and has no
restriction on reverse engineering for debugging modifications to libconfig.

Android-specific uMurmur changes: private IPv4 peer restriction, socket/error
handling, signal-safe shutdown flag, socket cleanup, parent-death signal, and
a readiness log line. Crypto and Mumble protocol behavior remain upstream.

uMurmur copyright holders include Martin Johansson, Thorvald Natvig and other
contributors. Source copyright headers have been retained. No claim is made
that this application is endorsed by the upstream projects.
