# Release signing setup

The release workflow intentionally stops if the release key is missing. Never replace these values with a debug keystore.

Create a long-lived owner-controlled Android signing keystore outside the repository. For example:

    keytool -genkeypair -v -keystore omaapp-release.jks -alias omaapp -keyalg RSA -keysize 4096 -validity 10000

Back up that keystore securely. A lost signing key prevents future APKs from updating existing installs.

Configure these GitHub Actions secrets in the repository:

- OMAAPP_KEYSTORE_BASE64 — base64-encoded contents of the keystore.
- OMAAPP_KEY_ALIAS — alias used when creating the key.
- OMAAPP_KEYSTORE_PASSWORD — keystore password.
- OMAAPP_KEY_PASSWORD — private-key password.

Do not commit the keystore or print any secret in workflow logs. The release workflow decodes it into a temporary runner file, signs the APK, verifies the signature, and only then creates a GitHub Release.

The first signing-key setup and update-over-install check must be performed by the repository owner before publishing a release.
