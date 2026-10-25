# OmaApp task status

## Completed in this development iteration

- [x] Add offline English/Russian Qt Linguist catalogs, Android system-language detection, a persistent Settings selector, localized native messages, and CI checks for missing or unfinished translations.
- [x] Refresh the launcher after protected Android package lifecycle broadcasts, coalesce broadcast bursts, and refresh on Activity resume; verify app discovery with an installed and removed Android launcher fixture.
- [x] Add a test-only Android `AppWidgetHost` proof using system binding confirmation, a live `AppWidgetHostView`, a real provider click, and widget ID cleanup. Keep the Qt/QML layout limitation documented.
- [x] Add plugin DNS-SD advertisement and foreground Android discovery of an already QR-trusted Omarchy host. Require the saved certificate pin and token before an endpoint address can change.
- [x] Advertise active private IPv4 addresses on multi-interface hosts, refresh multicast memberships after address changes, use Android 14+ / Android 13 T extension 7+ NSD service-info updates with a legacy resolver fallback, and scope discovery callbacks to the current Activity generation.
- [x] Queue the latest trusted DNS-SD candidate until active network work completes instead of polling the operation lock four times per second; stop discovery when trust is revoked or forgotten.
- [x] Add opt-in system-respecting Android haptic feedback, persisted in launcher settings and backups, with emulator assertions for dispatch and restart persistence.
- [x] Localize OWL catalog titles and descriptions, expose them to accessibility, refresh labels on language changes, and keep OWL and backup actions readable on phone-width layouts.
- [x] Use concise Russian bottom-tab text with full accessible names.
- [x] Guard OWL renderer bindings during QML Loader initialization, and refresh widgets when their model arrives or they become visible again.
- [x] Expose OWL JSON-array entries through numeric Repeater models and indexed delegates, avoiding a silent empty QML renderer on Android.
- [x] Test graceful cancellation of a plugin event long poll and a response write when the TLS client disconnects.
- [x] Update English and Russian READMEs, changelog, task status, pairing guide, AppWidgetHost findings, and checked-in OpenGL/Vulkan emulator screenshots.
- [x] Apply Android accessibility text scale to shared Qt/QML font and spacing helpers; cover larger text and repeated locale switching in the Android smoke; add phone-safe widths to wrapping dialogs that emitted binding-loop warnings.
- [x] Retain boot, process, Activity, Window, Qt graphics, screenshot, hierarchy, and logcat evidence when emulator Home readiness times out.
- [x] Run English and Russian Android native-string checks only after selecting the corresponding locale; check persistence after instrumentation process teardown.
- [x] Refresh backend-specific Settings visual baselines for the visible OWL creation action while preserving the 1.5% difference threshold.

## Remaining work and validation limits

- [x] Pass a full CI run after the translation-order fix and refreshed Settings baselines. CI #251 passed Desktop, Android arm64, and Android OpenGL/Vulkan smoke jobs.
- [ ] Test Android NSD discovery, IP changes, reconnect, and wrong-certificate rejection end to end against a live Omarchy host.
- [ ] Integrate ordinary Android widgets into saved Home layout only after a supported native-to-QML placement and lifecycle bridge is designed and tested. The current `AppWidgetHost` overlay is only a proof of concept.
- [ ] Test installation, HOME selection, app discovery, camera QR scan, TLS pairing, theme sync, clipboard/files, offline mode, battery use, and UI performance on a physical Pixel 10 with GrapheneOS.
- [x] Resolve both addressed PR #16 review threads and leave the PR open and Ready for Review after CI #251; maintainers still need to review it before merge.
- [x] Open separate draft PR #17 for the AppWidgetHost proof. Production Android widget placement remains out of scope for this proof.
- [ ] Publish an Alpha pre-release only with the owner's permanent signing secrets.

## Local verification

- `python3 -m unittest discover -s scripts/tests -q`: 133 tests pass.
- `python3 scripts/ci/check_translations.py`: 292 Russian source strings validated.
- Shell syntax checks, `git diff --check`, and visual comparison of CI #251 OpenGL/Vulkan captures against the refreshed baselines pass. Qt qmllint and a full Android/desktop build are unavailable locally.
- CI [#251](https://github.com/pluseight8/omarchy-app-mobile/actions/runs/40193718934) passed all four jobs: Desktop, Android arm64, and Android x86_64 smoke on OpenGL and Vulkan. It published the [arm64 debug APK](https://github.com/pluseight8/omarchy-app-mobile/actions/runs/40193718934/artifacts/12598838389), [Vulkan smoke captures](https://github.com/pluseight8/omarchy-app-mobile/actions/runs/40193718934/artifacts/12599650182), and [OpenGL smoke captures](https://github.com/pluseight8/omarchy-app-mobile/actions/runs/40193718934/artifacts/12598844391). The APK is not production-signed. CI #249's OpenGL readiness failure happened before native main/QML and had no crash or ANR evidence; both backends launched in #250, so the earlier cause remains unconfirmed.
- This workspace lacks CMake, Qt ShaderTools, and the Android/JDK toolchain. No local APK, full CMake build, or emulator run is claimed.
- No physical phone is attached. Pixel 10 / GrapheneOS compatibility and battery behavior remain unverified.
- Android DNS-SD remains verified with the Android NSD fixture and host-side Linux multicast packet tests only; it has not been exercised end to end over physical Wi-Fi with a live Omarchy host and phone.
