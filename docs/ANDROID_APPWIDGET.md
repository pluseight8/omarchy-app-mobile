# Android AppWidgetHost integration status

OmaApp currently uses its portable OWL widgets inside Qt Quick. Ordinary Android
widgets need the Android `AppWidgetHost`, an allocated widget ID, the system
binding confirmation, and an interactive `AppWidgetHostView` backed by
`RemoteViews`.

The Android emulator CI now injects a test-only `AppWidgetProvider` into the
debug build. It asks Android to bind that real provider, creates an
`AppWidgetHostView`, overlays it on the running Qt Quick Activity, captures the
live view, sends a touch through the native view, checks that the provider
receives it, and releases the widget ID. The provider and its manifest entries
are copied in by `scripts/ci/prepare_android_pairing_smoke.py`; they are not
part of the product APK or release workflow. The smoke is run for both Android
OpenGL and Vulkan builds.

This proof tests Android's real binding and view lifecycle at the Qt Activity
boundary. It does not yet make Android widgets user-configurable on Home. The
overlay lives above the Qt Quick surface, outside QML's item tree, so it does
not inherit QML clipping, page transitions, layout, or drag gestures. The
production launcher still needs a small native-to-QML placement bridge before
it can save a widget's Home position, resize it, restore IDs, or manage widget
configuration. The UI must keep the system binding confirmation and never bind
a provider without the user's approval.

Qt documents two Android integration models: Qt for Android owns the full
application UI, while Qt Quick for Android embeds QML as an Android `View` in a
native application. OmaApp uses Qt for Android. Its current proof uses a
`QtActivity` content overlay; Qt does not document this as a QML child-item API.
See [Qt for Android](https://doc.qt.io/qt-6/android.html),
[Qt Quick for Android](https://doc.qt.io/qt-6/qtquick-for-android.html), and
Android's [App Widget host guide](https://developer.android.com/develop/ui/views/appwidgets/host).

The proof is considered successful only after the emulator CI completes and
uploads `appwidget-host-poc.png`. Physical device behavior, GrapheneOS policy,
resizing, configuration activities, Home persistence, and coexistence with OWL
widgets remain to be verified before this becomes a supported user feature.
