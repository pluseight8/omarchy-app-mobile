import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]


class ReleaseArtifactTests(unittest.TestCase):
    def test_prerelease_version_code_is_cmake_driven_and_regressed(self):
        cmake = (ROOT / "CMakeLists.txt").read_text(encoding="utf-8")
        version_test = (ROOT / "cmake/tests/AndroidVersionCodeTest.cmake").read_text(encoding="utf-8")
        version_printer = (ROOT / "cmake/PrintAndroidVersionCode.cmake").read_text(encoding="utf-8")
        self.assertIn("omaapp_compute_android_version_code(", cmake)
        self.assertIn("omaapp_compute_android_version_code(", version_printer)
        self.assertIn("NAME android_version_code_tests", cmake)
        self.assertIn('assert_version_code("0.1.0-alpha.1" 1000001)', version_test)
        self.assertIn('assert_version_code("0.1.0-alpha.2" 1000002)', version_test)
        self.assertIn('assert_version_code("0.1.0-alpha.3000" 1003000)', version_test)
        self.assertIn('assert_version_code("0.1.0-beta.1" 1003001)', version_test)
        self.assertIn('assert_version_code("0.1.0-rc.3000" 1009000)', version_test)
        self.assertIn('assert_version_code("0.1.0" 1009999)', version_test)
        self.assertIn("first_alpha_code LESS second_alpha_code", version_test)

    def test_open_source_licenses_are_embedded_and_user_accessible(self):
        cmake = (ROOT / "CMakeLists.txt").read_text(encoding="utf-8")
        main_cpp = (ROOT / "app/native/main.cpp").read_text(encoding="utf-8")
        main_qml = (ROOT / "app/qml/Main.qml").read_text(encoding="utf-8")
        for required_path in (
            "app/licenses/NOTICE.md",
            "app/licenses/LGPL-3.0.txt",
            "app/licenses/Apache-2.0.txt",
            "app/android/kotlin/dev/omalauncher/widgets/lang/LICENSE-MIT",
        ):
            self.assertTrue((ROOT / required_path).is_file(), required_path)
            self.assertIn(required_path, cmake)
        self.assertIn('root->setProperty("openSourceNoticesText"', main_cpp)
        self.assertIn("text: window.openSourceNoticesText", main_qml)
        self.assertIn("Open-source notices and licenses", main_qml)

    def test_release_workflow_publishes_verified_build_inventory_and_source(self):
        workflow = (ROOT / ".github/workflows/release.yml").read_text(encoding="utf-8")
        self.assertIn("version: 6.10.3", workflow)
        self.assertIn("$QT_ROOT_DIR/sbom", workflow)
        self.assertIn("OmaApp-APK-Native-Libraries.txt", workflow)
        self.assertIn("SIGNING-CERT-SHA256.txt", workflow)
        self.assertIn("OmaApp-Qt-6.10.3-Source.tar.xz", workflow)
        self.assertIn("PrintAndroidVersionCode.cmake", workflow)
        self.assertIn("APK versionCode mismatch", workflow)
        self.assertIn("CMAKE_BUILD_TYPE=Release", (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8"))
        self.assertIn("cbc81e726b0ff3c0cdb0219bf74545e91cec013c4a8503c20f93f83d73dff5d2", workflow)
        self.assertIn("sha256sum --check", workflow)
        self.assertIn("SHA256SUMS.txt", workflow)
        self.assertIn('" verify --verbose --print-certs', workflow)


if __name__ == "__main__":
    unittest.main()
