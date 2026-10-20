# GrapheneOS compatibility

GrapheneOS remains a target, but OmaApp has not been installed or tested on GrapheneOS or a Pixel device. A debug APK is built by GitHub Actions; that does not establish runtime compatibility.

The Android manifest registers OmaApp as a selectable HOME app and declares only the camera permission for on-demand QR scanning plus the normal internet permission for pairing and theme sync. The app does not request root, privileged APIs, Shizuku, device-owner status, notification access, or an accessibility service. It queries launcher activities through Android's package visibility declarations rather than requesting access to every installed package.

The QR scanner uses CameraX and a bundled ML Kit barcode model, so scanning does not require a Google Play Services model download. Verify camera permission, launcher enumeration, app launch, HOME selection, TLS pinning, Keystore persistence, reconnect, and offline cache on a GrapheneOS device before claiming support.
