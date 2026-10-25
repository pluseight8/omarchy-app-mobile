# Android AppWidgetHost integration status

`AndroidWidgetHost` runs inside the existing OmaApp APK. The Home screen opens
Android's system widget picker, launches provider configuration when required,
and overlays the provider's real `AppWidgetHostView` over its Qt Quick slot.
Native bounds are supplied in density-independent units and clipped to the
visible page viewport. Activity start/stop pairs call `startListening()` /
`stopListening()`.

Widget configuration is versioned. It records provider identity, a local Android
widget ID, Home page, order, provider minimum size, and selected width/height.
Legacy array state migrates to the versioned format. Removal commits the new
configuration before releasing the Android ID and rolls back metadata if ID
release fails. Missing providers and portable restores remain visible as
placeholders; backup JSON contains provider/layout metadata but never Android
binding IDs or grants. Rebinding runs through the system picker and only accepts
the provider component saved in the placeholder.

The Home UI supports per-page placement, page/order movement, drag-to-page-header,
accessible reorder controls, and width/height adjustment controls. Widgets are
currently arranged in an ordered vertical stack on each page; this is not yet a
freeform shared grid with edge resize handles. Resized slot bounds update Android
widget options, and native widget controls remain interactive.

The OpenGL and Vulkan emulator smoke uses a separate test-only APK containing a
real interactive `AppWidgetProvider` and configuration Activity. It binds via
the system picker, verifies a `RemoteViews` click, moves and resizes the saved
placement, checks `AppWidgetManager` size options, force-stops and cold-starts
OmaApp, and verifies the widget remains interactive. It exports a backup,
confirms the binding ID is absent, removes the widget, restores a placeholder,
rebinds through Android's picker to a fresh ID, checks preserved geometry, and
removes the rebound widget. The smoke stores Home and placeholder screenshots
as CI artifacts. The fixture package and signing key are generated only in CI;
neither is part of the product APK or release workflow.

The host uses Android's picker rather than silently invoking
`bindAppWidgetIdIfAllowed`; choosing a provider there is the user's bind action.
Persisted provider identity is checked against `AppWidgetManager` before
restoring a saved ID. Untracked allocated IDs are released during recovery.
Invalid, non-finite, or oversized frame data is rejected before changing native
views. Importing a backup does not install a missing provider; the user must
install it and approve a fresh binding in the Android picker.

CI verification is emulator-only. Pixel/GrapheneOS behavior, provider
compatibility beyond the CI fixture, rotation, density changes, Android system
gestures/insets, TalkBack focus, memory use, and frame performance still need
physical-device or broader provider testing. Freeform x/y positioning, shared
app/OWL/widget grid placement, and edge resize handles are not implemented.

Qt documents the Android integration options in [Qt for Android](https://doc.qt.io/qt-6/android.html)
and [Qt Quick for Android](https://doc.qt.io/qt-6/qtquick-for-android.html).
Android documents the [App Widget host guide](https://developer.android.com/develop/ui/views/appwidgets/host),
[`AppWidgetHost`](https://developer.android.com/reference/android/appwidget/AppWidgetHost), and
[`AppWidgetManager`](https://developer.android.com/reference/android/appwidget/AppWidgetManager)
APIs.
