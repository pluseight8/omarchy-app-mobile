# Installation

## Android development APK

GitHub Actions builds an arm64 debug APK as the `OmaApp-ci-debug-arm64` artifact. Download it from a successful CI run and install it on an arm64 Android device with `adb install -r <apk>`. The debug artifact is for development; do not use it as an update path for a signed release.

The current workspace does not have Android SDK tools, `adb`, or an emulator. The CI APK has not been installed or launched here. GrapheneOS and Pixel compatibility remain unverified.

## Signed test release

The tag workflow builds an arm64 release APK, signs it with the repository owner's key, verifies the signature, attaches a plugin archive and checksums, and publishes a GitHub **Pre-release**. The APK asset follows `OmaApp-vX.Y.Z-arm64-v8a.apk`.

Before creating a version tag, the repository owner must configure `OMAAPP_KEYSTORE_BASE64`, `OMAAPP_KEY_ALIAS`, `OMAAPP_KEYSTORE_PASSWORD`, and `OMAAPP_KEY_PASSWORD` in GitHub Actions secrets. See [release signing](release-signing.md). The workflow fails before publication if any secret is missing; it never creates or uses a temporary release key.

## Omarchy plugin

On an Omarchy computer, clone the repository and run:

```sh
./omarchy-plugin/install.sh
```

The installer requires Omarchy, Python 3.11+ and OpenSSL. It validates the manifest, stages the single plugin in the user's plugin directory, and enables it. The plugin launches its included Python helper under the widget lifecycle. The helper opens HTTPS only when the user requests a QR code or when saved trusted devices need to reconnect.

Open the OmaApp widget, choose **Pair Android device**, scan the QR in OmaApp, and approve the phone in the widget. For network requirements and the supported theme fields, see [pairing](PAIRING.md).

To remove the plugin, run `./omarchy-plugin/uninstall.sh`. It disables the plugin and keeps a timestamped source backup. The trusted-device database and TLS private key live separately in `~/.local/state/omarchy/omaapp`; revoke saved phones in the widget before permanently deleting that state directory.
