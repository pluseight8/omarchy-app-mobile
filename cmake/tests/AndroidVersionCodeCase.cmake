include("${REPO_SOURCE_DIR}/cmake/AndroidVersionCode.cmake")
omaapp_compute_android_version_code("${VERSION_NAME}" "${EXPECTED_CORE}" result)
message(STATUS "Unexpected valid Android version code: ${result}")
