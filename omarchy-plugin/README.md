# OmaApp Omarchy plugin

This is the Omarchy-side component of OmaApp. It currently installs one status widget that reports that pairing is not available yet. It does not open a network listener or execute remote commands.

## Install

From a checkout of the OmaApp repository, run:

    ./omarchy-plugin/install.sh

The script copies this plugin into the Omarchy user plugin directory, validates the manifest with Omarchy, and enables it. No separate server or system service is installed.

To remove it, run:

    ./omarchy-plugin/uninstall.sh

Uninstall disables the plugin and moves its source to a timestamped backup.

## Current limits

- Pairing, discovery, theme sync, file transfer, clipboard, notifications, terminal, and desktop access are not implemented.
- The widget is a status indicator only.
- The Android and plugin versions must match before a network protocol is enabled.

Omarchy third-party plugins run as QML inside the long-lived shell process. Read and review plugin source before enabling it.
