# GrapheneOS compatibility

GrapheneOS is a target, but this prototype has not been installed or tested on GrapheneOS or a Pixel device. No Google Play Services dependency is declared. That alone does not establish device compatibility.

The manifest exposes a standard Android HOME activity so the user can select OmaApp through Android's launcher selection UI. The app does not request root, privileged APIs, Shizuku, device-owner status, or an accessibility service. A working launcher APK and device smoke test are still required before claiming support.

The current app queries launcher activities through Android package visibility declarations. It does not query all installed packages. Future notification, widget, tile, and background features will need separate permissions and should remain opt-in.
