# Installation

## Android

There is no installable OmaApp APK in Releases yet. The Android application and CI build are being brought up first. A release will require an owner-controlled signing key and a successful signed-APK verification; no debug or unsigned APK will be presented as a stable release.

When a CI build is available, select its `OmaApp-ci-debug-arm64` artifact for development testing. It is not a stable release and may not be signed for updates. For local builds, see the Android build commands in [README.md](../README.md).

## Omarchy plugin

On an Omarchy computer, clone this repository and run:

```sh
./omarchy-plugin/install.sh
```

The installer checks that `omarchy` is on `PATH`, validates the plugin manifest, stages the files in the user plugin directory, and enables the single OmaApp status widget. It does not install a listener or system service. To remove the widget, run `./omarchy-plugin/uninstall.sh`; the script disables it and preserves a timestamped backup of its source directory.

The plugin currently displays an unpaired status. Installing it does not establish a connection to an Android device.
