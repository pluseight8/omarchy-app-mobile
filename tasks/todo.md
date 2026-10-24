# OmaApp task status

## Completed in this development iteration

- [x] Add offline English/Russian Qt Linguist catalogs, Android system-language detection, a persistent Settings selector, localized native messages, and CI checks for missing or unfinished translations.
- [x] Refresh the launcher after protected Android package lifecycle broadcasts, coalesce broadcast bursts, and refresh on Activity resume; verify app discovery with an installed and removed Android launcher fixture.
- [x] Add a test-only Android `AppWidgetHost` proof using system binding confirmation, a live `AppWidgetHostView`, a real provider click, and widget ID cleanup. Keep the Qt/QML layout limitation documented.
- [x] Add plugin DNS-SD advertisement and foreground Android discovery of an already QR-trusted Omarchy host. Require the saved certificate pin and token before an endpoint address can change.
- [x] Add opt-in system-respecting Android haptic feedback, persisted in launcher settings and backups, with emulator assertions for dispatch and restart persistence.
- [x] Localize OWL catalog titles and descriptions, expose them to accessibility, refresh labels on language changes, and keep OWL and backup actions readable on phone-width layouts.
- [x] Use concise Russian bottom-tab text with full accessible names.
- [x] Guard OWL renderer bindings during QML Loader initialization, and refresh widgets when their model arrives or they become visible again.
- [x] Test graceful cancellation of a plugin event long poll and a response write when the TLS client disconnects.
- [x] Update English and Russian READMEs, changelog, task status, pairing guide, AppWidgetHost findings, and checked-in OpenGL/Vulkan emulator screenshots.

## Remaining work and validation limits

- [ ] Pass CI after the OWL refresh change: desktop, arm64 APK, OpenGL, Vulkan, OWL image rendering, loader regression, and confirmed process-death checks.
- [ ] Test Android NSD discovery, IP changes, reconnect, and wrong-certificate rejection end to end against a live Omarchy host.
- [ ] Integrate ordinary Android widgets into saved Home layout only after a supported native-to-QML placement and lifecycle bridge is designed and tested. The current `AppWidgetHost` overlay is only a proof of concept.
- [ ] Test installation, HOME selection, app discovery, camera QR scan, TLS pairing, theme sync, clipboard/files, offline mode, battery use, and UI performance on a physical Pixel 10 with GrapheneOS.
- [ ] Review and merge the current PR; publish an Alpha pre-release only with the owner's permanent signing secrets.

## Local verification

- `python3 -m unittest discover -s scripts/tests -q`: 113 tests pass.
- `python3 scripts/ci/check_translations.py`: 291 Russian source strings validated.
- Changed OWL QML passes `qmllint`; shell syntax checks and `git diff --check` pass.
- CI [#220](https://github.com/pluseight8/omarchy-app-mobile/actions/runs/40130375435) passed desktop and arm64 builds but both Android graphics backends found the installed Canvas sampler blank. CI [#221](https://github.com/pluseight8/omarchy-app-mobile/actions/runs/40131445004) is verifying the refresh change on both backends.
- This workspace lacks CMake, Qt ShaderTools, and the Android/JDK toolchain. No local APK, full CMake build, or emulator run is claimed.
- No physical phone is attached. Pixel 10 / GrapheneOS compatibility and battery behavior remain unverified.
