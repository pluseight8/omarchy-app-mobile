# Changelog

## Unreleased

- Added the first Qt Quick launcher shell and Android package template.
- Added Android launcher-app enumeration and launch through a Java bridge.
- Added a Qt ShaderEffect for Hyprland-derived rounded surfaces, inner masks, and gradient borders.
- Added the first Omarchy status-widget package and installer.
- Added desktop and Android CI workflows with release signing gated on repository secrets.
- Added the first Android `AppWidgetHost` integration inside the OmaApp APK, with system-picker binding, provider configuration, live QML placement/clipping, saved widget IDs/order, lifecycle recovery, and explicit removal.
- Added a test-only interactive `RemoteViews` provider fixture for emulator verification; the fixture and its signing key are not part of the product APK or release. CI [#270](https://github.com/pluseight8/omarchy-app-mobile/actions/runs/40212669660) passed binding, interaction, process restoration, and ID removal on OpenGL and Vulkan.

Android widget drag/resize, multi-page placement, layout backup/restore, and physical-device verification remain in progress. This changelog does not announce a signed production release.
