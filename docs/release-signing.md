# Release signing setup

The first tag workflow intentionally creates a GitHub Pre-release and stops if the release key is missing. Never replace these values with a debug keystore.

Create a long-lived owner-controlled Android signing keystore outside the repository. For example:

    keytool -genkeypair -v -keystore omaapp-release.jks -alias omaapp -keyalg RSA -keysize 4096 -validity 10000

Back up that keystore securely. A lost signing key prevents future APKs from updating existing installs.

Configure these GitHub Actions secrets in the repository:

- OMAAPP_KEYSTORE_BASE64 — base64-encoded contents of the keystore.
- OMAAPP_KEY_ALIAS — alias used when creating the key.
- OMAAPP_KEYSTORE_PASSWORD — keystore password.
- OMAAPP_KEY_PASSWORD — private-key password.

Do not commit the keystore or print any secret in workflow logs. The existing release workflow decodes it into a temporary runner file, signs the arm64 APK, verifies its signature, then builds same-key x86_64 update-test APKs, installs the older package on an Android emulator, upgrades it to the tagged version, and verifies persisted launcher settings. It creates the GitHub Release only after these checks.

The repository owner must configure and verify the signing key before pushing a version tag. The workflow publishes `OmaApp-vX.Y.Z-arm64-v8a.apk` only after signature and emulator install/update checks pass. The owner should still install the published arm64 APK on a Pixel 10 with GrapheneOS and follow [the device guide](GRAPHENEOS.md).
