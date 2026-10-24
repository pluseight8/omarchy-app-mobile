# OmaApp task status

## Completed in this development iteration

- [x] Add offline English/Russian Qt Linguist translations, Android system-language detection, a persistent Settings selector, Java-native UI translation, and CI checks for missing or unfinished catalog entries.
- [x] Refresh the launcher after protected Android package add/change/replace/remove broadcasts, coalesce broadcast bursts, and refresh on Activity resume.
- [x] Verify launcher discovery on emulator with a real CI-only Android launcher fixture that is installed and removed while OmaApp remains Home.
- [x] Add an emulator proof of concept using Android `AppWidgetHost`, the system binding flow, a live `AppWidgetHostView`, and a real click sent to its provider. Keep the provider test-only and document the current Qt/QML boundary.
- [x] Add plugin DNS-SD advertisement with the current private IPv4, service port, protocol, and public TLS fingerprint; do not advertise pairing secrets or device tokens.
- [x] Add foreground Android DNS-SD discovery that starts only after QR pairing, rejects untrusted fingerprints, checks the pinned TLS certificate and token before changing the saved address, and releases discovery resources on pause.
- [x] Add opt-in Android haptic feedback through `View.performHapticFeedback`; persist the preference in launcher settings and layout backups and add emulator assertions for dispatch and Activity restart persistence.
- [x] Add Retry and update pairing/install docs for local-network discovery and fallback QR reconnect.
- [x] Add regression tests for DNS-SD contents/query filtering/goodbye records, Android trust gates, launcher package events, translation completeness, and test-only widget wiring.
- [x] Update English and Russian READMEs, the changelog, task status, pairing guide, and AppWidgetHost findings.

## Remaining work

- [ ] Finish CI after fixing the OpenGL smoke's first-run app choice and the Vulkan translation helper's captured tap coordinates; inspect screenshots and the AppWidgetHost artifact before calling emulator checks complete.
- [ ] Integrate ordinary Android widgets into saved Home layout only after a supported Qt-to-native placement/lifecycle bridge is designed and tested. Current status is an overlay proof of concept, not a product feature.
- [ ] Test installation, language switching, app discovery, QR pairing, theme sync, DNS-SD reconnect, clipboard/files, and idle behavior on a physical Pixel 10 with GrapheneOS.
- [ ] Review the current PR, address CI/review feedback, and keep the APK signing key owner-managed. Do not publish a signed release until the existing release workflow succeeds with the permanent secrets.

## Local verification limits

- `python3 -m unittest discover -s scripts/tests -q`: 101 tests pass. Russian catalog check validates 280 source strings; shell syntax, Python bytecode compilation, Android version-code regression test, and `git diff --check` pass. CI #194 passed desktop and arm64 builds; OpenGL and Vulkan emulator smokes exposed the two harness issues listed above.
- The workspace lacks JDK/Android SDK/NDK and the Qt ShaderTools development package, so no local Android APK, emulator, full desktop CMake build, or screenshot run is claimed.
- No physical phone is attached. Pixel 10 / GrapheneOS compatibility and battery behavior are unverified.
