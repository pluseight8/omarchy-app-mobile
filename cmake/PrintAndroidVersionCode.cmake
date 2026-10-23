if(NOT DEFINED OMAAPP_VERSION_NAME OR NOT DEFINED OMAAPP_VERSION)
    message(FATAL_ERROR "OMAAPP_VERSION_NAME and OMAAPP_VERSION are required")
endif()

include("${CMAKE_CURRENT_LIST_DIR}/AndroidVersionCode.cmake")
omaapp_compute_android_version_code(
    "${OMAAPP_VERSION_NAME}" "${OMAAPP_VERSION}" version_code)
message(STATUS "OMAAPP_ANDROID_VERSION_CODE=${version_code}")
