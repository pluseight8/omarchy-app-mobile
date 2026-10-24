# Changelog

## Unreleased

- Added English and Russian Qt Linguist catalogs, system-language detection, an in-app language selector, and a CI check for missing translations.
- Refresh launcher activities after protected Android package install, replace, and removal events; exercise the behavior by installing and removing an Android launcher fixture in emulator CI.
- Add a CI-only proof of concept for binding and hosting a real Android AppWidget over the Qt activity. User-managed widgets are not yet part of persisted Home layouts.
- Add DNS-SD discovery for the already QR-trusted Omarchy plugin. Endpoint changes require the pinned TLS certificate and the saved device token; retry remains available when multicast discovery is blocked.
- Extend plugin packet tests for DNS-SD records, malformed queries, record expiry, and the absence of pairing credentials in advertisements.
- Add optional Android haptic feedback for Home app launches and editing actions. It uses `View.performHapticFeedback`, is disabled by default, follows system touch-feedback settings, and is persisted in launcher settings and layout backups.
- Localize built-in OWL widget names and descriptions, expose those labels to accessibility, refresh the widget store when the app language changes, and clear stale translated status messages. Stack OWL actions and backup controls at phone width so translated labels and titles stay readable.
- Use compact Russian labels in the phone tab bar while retaining the full accessible page names. Add a regression test for a TLS client that closes while an HTTP response is being written; cancelled feature and event requests no longer emit server tracebacks.
- Load root OWL nodes through the same guarded dynamic loader as nested nodes, so the renderer is not evaluated before its model bindings are ready. Android smoke treats OWL null-binding errors as failures.
- Make every OWL node renderer lookup tolerate the brief null state reported by Android QML initialization, including Canvas paint, color, image, and scalar helpers; malformed or not-yet-bound node data now uses safe local fallbacks while bindings settle.
- Keep the Android DNS-SD fixture and TLS forwarder alive through the first theme read from the post-restart endpoint. This tests discovery and pinned TLS fetch in one instrumentation process instead of reconnecting to an endpoint after its test server had already closed.
- Verify locale and haptic preferences after a confirmed Android process death, save startup screenshots and PID-filtered logs when Home is not accessible, and publish cold-start/PSS/idle-CPU metrics only after confirming a cold launch.

The local suite currently passes 112 Python tests, validates 291 Russian UI strings, and passes `qmllint` for the OWL renderer. Check the current desktop, arm64, OpenGL, and Vulkan results in [GitHub Actions](https://github.com/pluseight8/omarchy-app-mobile/actions/workflows/ci.yml). The checked-in Russian screenshots are from earlier successful emulator runs under [`docs/screenshots`](docs/screenshots/). Physical Pixel/GrapheneOS testing remains outstanding. A local full build is unavailable because this environment lacks CMake, Qt ShaderTools, and the Android/JDK toolchain.
