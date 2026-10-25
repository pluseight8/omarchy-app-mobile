# Android AppWidgetHost integration status

OmaApp now has the first `AndroidWidgetHost` integration in the same Android
APK. The Home screen opens Android's system widget picker, which allocates and binds a
provider after the user selects it. OmaApp starts a provider configuration
activity when one is declared, creates a real `AppWidgetHostView`, and overlays
it above its Qt Quick Activity in the matching Home slot. The bridge sends the
slot's density-independent bounds and visible QML viewport to Android so the
native view is clipped while the Home page scrolls or moves. A fixed host ID and
provider IDs/order are saved locally; Activity start/stop pairs call
`startListening()` / `stopListening()`. Cancelled, interrupted, stale, and
removed widget IDs are released.

The emulator CI builds a separate test-only APK containing a real interactive
`AppWidgetProvider` and a configuration Activity. It selects that provider
through Android's system picker, completes the provider configuration result,
checks its live `RemoteViews`, clicks its button, checks the updated state after
Activity recreation, clicks it again, then force-stops and restarts OmaApp and
checks the provider's updated state returns. Finally it removes the widget and
checks that Android releases the ID. This test runs in the OpenGL and Vulkan
emulator jobs. The fixture package, resources, and short-lived fixture signing
key are generated only in CI; they are not included in the product APK or
release workflow.

Current placement is an ordered vertical stack on the first Home page. Android
provider configuration and size options are supported, but user drag/reorder,
resize handles, per-page placement, layout backup/restore, and virtualization
are not implemented yet. The native view is clipped to the QML scroll/page
viewport, but its z-order is above the Qt Quick scene; cross-page gesture
arbitration and screen-reader behavior still need device testing. Ordinary OWL
widgets continue to render in their existing Qt Quick list below the Android
widget section.

The host uses Android's picker rather than silently invoking
`bindAppWidgetIdIfAllowed`; choosing a provider in the system picker is the
user's explicit bind action. Persisted provider identity is checked against
`AppWidgetManager` before restoring a saved ID, and IDs not represented in
OmaApp's saved state are released during recovery. The bridge rejects invalid,
non-finite, or oversized bounds before changing native views.

This initial integration is emulator-verified only when the
`android_widget_host_smoke.sh` setup, restart, and removal stages pass for both
graphics backends. Physical device behavior, GrapheneOS policy, drag/resize,
multi-page placement, widget backup, cross-page gesture arbitration, and
screen-reader interaction remain to be verified before broader support.
Rotation, density changes, Android system gestures/insets, memory use, and
frame performance have not been measured yet.

Qt documents the Android integration options in [Qt for Android](https://doc.qt.io/qt-6/android.html)
and [Qt Quick for Android](https://doc.qt.io/qt-6/qtquick-for-android.html).
Android documents the [App Widget host guide](https://developer.android.com/develop/ui/views/appwidgets/host),
[`AppWidgetHost`](https://developer.android.com/reference/android/appwidget/AppWidgetHost), and
[`AppWidgetManager`](https://developer.android.com/reference/android/appwidget/AppWidgetManager)
APIs.
