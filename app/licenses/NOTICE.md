# Open-source notices for OmaApp

OmaApp combines the OmaApp application and the Omarchy plugin in one project. The Android APK contains Qt 6.10.3 for Android, AndroidX/CameraX and ML Kit components, Kotlin runtime code, and the offline OWL compiler/evaluator derived from Omalauncher. The matching license texts and project notices are embedded in this screen and included in the release notice bundle.

The release contains Qt's 6.10.3 Android arm64 SBOM and the exact native-library inventory extracted from the APK. The SBOM describes the Qt installation's modules and third-party components; the APK inventory records the native libraries actually packaged for the app.

Qt is dynamically linked in the APK. Qt ShaderTools and its `qsb` compiler are build-time tools used to precompile the QSB shader resource; the ShaderTools library and `qsb` executable are not shipped in the APK. The QSB shader is consumed through Qt Gui/Qt Quick at runtime.

The release's `OmaApp-Qt-6.10.3-Source.tar.xz` asset contains the corresponding upstream Qt 6.10.3 source package; SHA-256 `cbc81e726b0ff3c0cdb0219bf74545e91cec013c4a8503c20f93f83d73dff5d2` is verified during release packaging. Build inputs (Qt 6.10.3, Android NDK 27.2.12479018, Android API 36, and JDK 17) are pinned in the release workflow. The app and plugin source at the same release tag provide the application sources and build instructions. Android package signatures are enforced by the OS; installing a modified/relinked build signed with a different key requires uninstalling the official package first.

This notice identifies the known components selected by the build. Consult the per-release Qt SBOM and native-library inventory for the exact Qt and transitive components associated with that APK.
