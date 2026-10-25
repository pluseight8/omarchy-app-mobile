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

## Remaining work and validation limits

- [x] Pass CI after switching OWL delegates to indexed numeric models: desktop, arm64 APK, OpenGL, Vulkan, rendered OWL assets, loader regression, and confirmed process-death checks.
- [ ] Test Android NSD discovery, IP changes, reconnect, and wrong-certificate rejection end to end against a live Omarchy host.
- [ ] Integrate ordinary Android widgets into saved Home layout only after a supported native-to-QML placement and lifecycle bridge is designed and tested. The current `AppWidgetHost` overlay is only a proof of concept.
- [ ] Test installation, HOME selection, app discovery, camera QR scan, TLS pairing, theme sync, clipboard/files, offline mode, battery use, and UI performance on a physical Pixel 10 with GrapheneOS.
- [ ] Review and merge the current PR; publish an Alpha pre-release only with the owner's permanent signing secrets.

## Local verification

- `python3 -m unittest discover -s scripts/tests -q`: 129 tests pass.
- `python3 scripts/ci/check_translations.py`: 292 Russian source strings validated.
- Shell syntax checks and git diff --check pass. Qt qmllint and a full Android/desktop build are unavailable locally. CI #231 passed desktop, arm64, OpenGL, and Vulkan for code commit 9e67ad6; CI #230's order-dependent assertion was fixed there.
- CI #228 attempt 1 and OpenGL in docs-only CI #232 both captured a black HOME screen. In #232 the Activity/process remained alive and Qt created an OpenGL context, but QML module loading did not report completion and Android logged no fatal/ANR. The root cause remains unknown. Commit fc4aad4 distinguishes QML creation stalls after RHI startup and captures a one-shot thread dump; CI #233 is pending.
- This workspace lacks CMake, Qt ShaderTools, and the Android/JDK toolchain. No local APK, full CMake build, or emulator run is claimed.
- No physical phone is attached. Pixel 10 / GrapheneOS compatibility and battery behavior remain unverified.
- Android DNS-SD remains verified with the test fixture and host-side Linux multicast tests only; it has not been exercised end to end with a live Omarchy host and phone.
