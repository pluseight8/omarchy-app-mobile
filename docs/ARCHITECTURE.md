# Architecture

OmaApp has two installable components: one Android APK and one Omarchy plugin. Shared C++/QML files and the Python helper inside the plugin are source modules, not additional products.

```text
Android APK
  ├── Qt Quick launcher, dock, app search, folders, devices, appearance, settings
  ├── C++ LauncherModel (app list, pins, folders) / PairingModel
  ├── Java LauncherBridge ── Android PackageManager / app launch
  ├── Java QR scanner ── CameraX + bundled ML Kit QR model
  ├── Java PairingBridge ── TLS certificate pin / Android Keystore token
  ├── Qt Quick scene-graph surface item ── QSB fragment shader (Lp contour, antialias, OkLabA border)
  └── Kotlin OWL bridge ── embedded compiler/runtime ── bounded offline QML Canvas renderer

Omarchy plugin
  ├── Quickshell bar widget ── QR, device approval, revocation, independent permissions
  └── bundled Python helper ── TLS pairing, event stream, appearance and opt-in PC features
```

The plugin helper stays inside the plugin folder and follows the widget lifecycle. It opens no listener until the user asks to pair or a saved trusted phone needs reconnection. The Android client pins the computer certificate from the QR and authenticates each device with a separate random token. The first pairing is approved on both devices; an already trusted phone can scan a new QR to update its local network address.

The theme path reads Omarchy's active `colors.toml`, `shell.toml`, and background symlink, plus Hyprland options and animations through `hyprctl`. Linux inotify watches theme files, and Hyprland's socket2 event stream reports configuration and workspace changes. The Android foreground client waits on the authenticated, bounded event endpoint and keeps a 30-second fallback refresh for events that cannot be observed. Theme and wallpaper snapshots stay in private app storage for offline use.

Optional features each have a device-scoped grant, disabled by default: two-way text clipboard, receive/send files, MPRIS controls, system metrics, Hyprland workspace switching, and explicitly configured user commands. Clipboard transfer is user-initiated from the foreground app. Files are limited to 100 MiB and use Android's system document picker; the helper confines PC downloads to `~/Downloads/OmaApp`. MPRIS actions are allowlisted; workspace IDs are validated; configured commands use an executable and argument vector with a timeout and never pass through a shell.

The Android app is Qt Quick, not a port of the Quickshell executable. Quickshell's UI modules depend on Linux/Wayland APIs and the plugin runs inside the existing shell process. The phone adapts compatible `Color`, `Style`, `Border` tokens and surface behavior to Qt Quick. `HyprlandSurface` is one custom scene-graph material whose QSB fragment shader evaluates the Lp superellipse per pixel, clips fill and border with one coverage function, and interpolates up to ten OkLabA border stops at any configured angle. It avoids tessellation and per-triangle seams; antialiasing scales to the device pixel ratio. Qt's RHI translates the same QSB shaders to OpenGL ES and Vulkan. No Quickshell binary/library is bundled in the APK.

Launcher favorites and folders are stored locally through `QSettings`. Folder membership references Android launcher activities by package and activity name, so an unavailable app stays out of the folder view until it is installed again. Folder edits are bounded, validated, and independent of the Omarchy pairing connection.

## Verified and remaining work

The active PR #16 (https://github.com/pluseight8/omarchy-app-mobile/pull/16) is the current review gate. CI #231 passed desktop, arm64, and Android x86_64 OpenGL/Vulkan for code commit 9e67ad6; CI #233 passed all four jobs for fc4aad4. CI #232 reproduced an OpenGL black HOME, and CI #234 failed both emulator jobs during HOME readiness while desktop/arm64 passed. The latest tests add stage classification, bounded native/Java backtraces, and a restored-Home assertion after process restart. At this update, 130 local Python tests and 292 Russian UI strings pass. Linux integration tests start the real helper over TLS, exercise persistence and revocation, and change a watched theme file to verify event delivery. The plugin was installed and restarted earlier inside a local Omarchy 4.0.4 / Quickshell 0.3.1 session; Android DNS-SD against that live host is not verified. Physical Pixel/GrapheneOS validation remains outstanding.

CI #228 attempt 1 and OpenGL CI #232 showed a temporary HOME black screen. In #232, Android kept the Activity and process alive, Qt logged OpenGL context creation, and no fatal/ANR appeared; however, QQmlApplicationEngine::loadFromModule() did not report completion or enter the event loop. CI #234 showed a separate OpenGL attempt where the Qt Android platform plugin started but the native-main marker was absent; its Vulkan smoke failed a post-restart HOME readiness check. These observations point to different startup stages and do not establish a root cause. On readiness failures, CI now records boot/process state, Activity/Window dumps, hierarchy, screenshot, thread list, bounded native and Java backtraces, and full logcat.

The port includes up to five Home pages, an editable dock, cached Android launcher icons, app search, persistent folders, dynamic Android shortcuts, adjustable grid, four independent gesture preferences, app drag-and-drop, local clock and optional computer/media cards. A Kotlin bridge includes the original OWL compiler/runtime, local widget store/editor, Canvas renderer, widget reordering and a combined layout/widget backup. The offline interpreter supports common layout/text nodes and bounded Canvas operations; it rejects guest/network/command/asset/interactive facilities that cannot run locally. Android apps, widget sources and saved layout remain available offline. There is no PRoot or Arch Linux ARM.
