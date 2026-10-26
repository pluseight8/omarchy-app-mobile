# Android AppWidgetHost integration status

`AndroidWidgetHost` runs inside the existing OmaApp APK. The Home screen opens
Android's system widget picker, launches provider configuration when required,
and overlays the provider's real `AppWidgetHostView` over its Qt Quick slot.
Native bounds are supplied in density-independent units and clipped to the
visible page viewport. Activity start/stop pairs call `startListening()` /
`stopListening()`.

Widget configuration is versioned (schema 3). It records provider identity, a
local Android widget ID, Home page, 24 dp snapped x/y coordinates, display
order, provider minimum size, and selected width/height. Unversioned arrays and
schema 2 records migrate to non-overlapping grid positions. Removal commits the
new configuration before releasing the Android ID and rolls back metadata if
ID release fails. Missing providers and portable restores remain visible as
placeholders; backup schema 2 contains provider/layout metadata but never
Android binding IDs or grants. Schema 1 backups still import. Rebinding runs
through the system picker and only accepts the provider component saved in the
placeholder.

Each Home page has a separate Android-widget grid. A drag handle moves a widget
between grid positions; holding it near either horizontal edge advances to the
adjacent Home page so it can be dropped there. Placements snap to 24 dp and the
host rejects overlaps. Accessible controls nudge a widget vertically or move it
to another page. The last grid, page, or size change can be undone briefly, and
removal asks for confirmation. Undo applies to QML position and size controls
and the direct native resize grip. Width and height controls honor provider
minimums, and a visible native corner grip resizes both dimensions while
leaving the rest of RemoteViews touchable. Resize changes update Android widget
options. App icons and OWL
widgets remain in their existing Home areas; they do not yet share one grid
with Android widgets.

Combined layout backup restore validates OWL and Android widget sections before
applying them and writes a durable recovery snapshot before changing launcher
or OWL settings. Android stages placeholder records while retaining old widget
IDs; only the committed Qt journal lets Android release those IDs. A restart
rolls back an unfinished prepare or retries cleanup for a committed restore.
Desktop tests exercise both journal outcomes across model recreation. Emulator
tests verify rollback preserves a live widget ID, recovery after force-stop
during Android prepare, and commit releases the replaced ID.

The OpenGL and Vulkan emulator smoke uses a separate test-only APK containing a
real interactive `AppWidgetProvider` and configuration Activity. It binds via
the system picker, verifies a `RemoteViews` click, places the widget on another
page and at another grid coordinate, migrates a schema 2 layout, rejects
off-grid/out-of-bounds placements, exercises the QML position/undo controls,
resizes it through the Android corner grip, checks `AppWidgetManager` size
options, force-stops and cold-starts OmaApp, and verifies the widget remains
interactive. It exports a backup, confirms the binding ID is absent, removes
the widget through the confirmation dialog, restores a placeholder, rebinds
through Android's picker to a fresh ID, checks preserved geometry, and removes
the rebound widget. The smoke stores Home and placeholder screenshots as CI
artifacts. The fixture package and signing key are generated only in CI;
neither is part of the product APK or release workflow.

After a cold emulator launch and an eight-second settling period, CI also saves
`runtime-metrics.json` with ActivityManager launch timings, app PSS, process CPU,
and the main/UI thread CPU sample from Android's thread-aware `top` output. These
are reference measurements from a short idle sample, not performance budgets;
they do not measure sustained load or frame pacing.

The host uses Android's picker rather than silently invoking
`bindAppWidgetIdIfAllowed`; choosing a provider there is the user's bind action.
Persisted provider identity is checked against `AppWidgetManager` before
restoring a saved ID. Untracked allocated IDs are released during recovery.
Invalid, non-finite, or oversized frame data is rejected before changing native
views. Importing a backup does not install a missing provider; the user must
install it and approve a fresh binding in the Android picker.

CI verification is emulator-only. Pixel/GrapheneOS behavior, provider
compatibility beyond the CI fixture, rotation, live density changes, Android
system gestures/insets, TalkBack focus, sustained memory use, and frame
performance still need physical-device or broader provider testing.
Android-widget positions are saved in density-independent coordinates;
migration after a display size change may require a manual move if a provider no
longer fits the viewport.

Qt documents the Android integration options in [Qt for Android](https://doc.qt.io/qt-6/android.html)
and [Qt Quick for Android](https://doc.qt.io/qt-6/qtquick-for-android.html).
Android documents the [App Widget host guide](https://developer.android.com/develop/ui/views/appwidgets/host),
[`AppWidgetHost`](https://developer.android.com/reference/android/appwidget/AppWidgetHost), and
[`AppWidgetManager`](https://developer.android.com/reference/android/appwidget/AppWidgetManager)
APIs.
