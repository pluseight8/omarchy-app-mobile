cmake_minimum_required(VERSION 3.24)

if(NOT DEFINED OMAAPP_VERSION OR OMAAPP_VERSION STREQUAL "")
    set(OMAAPP_VERSION "0.1.0")
endif()

project(OmaApp VERSION "${OMAAPP_VERSION}" LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_AUTOMOC ON)

find_package(Qt6 6.10 REQUIRED COMPONENTS Core Gui Qml Quick QuickControls2 ShaderTools)
qt_standard_project_setup(REQUIRES 6.10)

set(OMAAPP_TEST_RHI_BACKEND "" CACHE STRING
    "Force an Android graphics API for backend-specific CI renders (OpenGL or Vulkan)")
set_property(CACHE OMAAPP_TEST_RHI_BACKEND PROPERTY STRINGS "" OpenGL Vulkan)
if(NOT OMAAPP_TEST_RHI_BACKEND STREQUAL "" AND
   NOT OMAAPP_TEST_RHI_BACKEND STREQUAL "OpenGL" AND
   NOT OMAAPP_TEST_RHI_BACKEND STREQUAL "Vulkan")
    message(FATAL_ERROR "OMAAPP_TEST_RHI_BACKEND must be empty, OpenGL, or Vulkan")
endif()

qt_add_executable(omaapp
    app/native/main.cpp
    app/native/LauncherModel.cpp
    app/native/LauncherModel.h
    app/native/PairingModel.cpp
    app/native/PairingModel.h
    shared/geometry/HyprlandGeometry.cpp
    shared/geometry/HyprlandGeometry.h
)

set_source_files_properties(app/qml/Theme.qml PROPERTIES QT_QML_SINGLETON_TYPE TRUE)

qt_add_qml_module(omaapp
    URI OmaApp
    VERSION 1.0
    QML_FILES
        app/qml/Main.qml
        app/qml/Theme.qml
        app/qml/HyprlandSurface.qml
)

# Qt's ShaderEffect needs precompiled QSB on Android and every Qt 6 graphics
# API. qt_add_shaders packages SPIR-V plus the OpenGL ES, Vulkan, Metal, and
# Direct3D variants in one resource.
qt_add_shaders(omaapp omaapp_rounding_shaders
    PREFIX "/shaders"
    BASE app/qml/shaders
    FILES
        app/qml/shaders/hyprland_rounded.frag
)

if(OMAAPP_TEST_RHI_BACKEND STREQUAL "OpenGL")
    target_compile_definitions(omaapp PRIVATE OMAAPP_TEST_RHI_BACKEND_OPENGL)
elseif(OMAAPP_TEST_RHI_BACKEND STREQUAL "Vulkan")
    target_compile_definitions(omaapp PRIVATE OMAAPP_TEST_RHI_BACKEND_VULKAN)
endif()

target_link_libraries(omaapp PRIVATE
    Qt6::Core
    Qt6::Gui
    Qt6::Qml
    Qt6::Quick
    Qt6::QuickControls2
)

if(ANDROID)
    string(REPLACE "." ";" OMAAPP_VERSION_PARTS "${PROJECT_VERSION}")
    list(GET OMAAPP_VERSION_PARTS 0 OMAAPP_VERSION_MAJOR)
    list(GET OMAAPP_VERSION_PARTS 1 OMAAPP_VERSION_MINOR)
    list(GET OMAAPP_VERSION_PARTS 2 OMAAPP_VERSION_PATCH)
    math(EXPR OMAAPP_ANDROID_VERSION_CODE
        "${OMAAPP_VERSION_MAJOR} * 1000000 + ${OMAAPP_VERSION_MINOR} * 1000 + ${OMAAPP_VERSION_PATCH}")

    set_target_properties(omaapp PROPERTIES
        QT_ANDROID_PACKAGE_NAME "app.omaapp"
        QT_ANDROID_PACKAGE_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/app/android"
        QT_ANDROID_APP_NAME "OmaApp"
        QT_ANDROID_MIN_SDK_VERSION 29
        QT_ANDROID_TARGET_SDK_VERSION 36
        QT_ANDROID_VERSION_NAME "${PROJECT_VERSION}"
        QT_ANDROID_VERSION_CODE "${OMAAPP_ANDROID_VERSION_CODE}"
    )
endif()

include(CTest)
if(BUILD_TESTING AND NOT ANDROID)
    find_package(Qt6 6.6 REQUIRED COMPONENTS Test)
    qt_add_executable(geometry_tests
        shared/geometry/tests/tst_geometry.cpp
        shared/geometry/HyprlandGeometry.cpp
    )
    target_link_libraries(geometry_tests PRIVATE Qt6::Core Qt6::Test)
    add_test(NAME geometry_tests COMMAND geometry_tests)

    qt_add_executable(launcher_model_tests
        app/native/tests/tst_launchermodel.cpp
        app/native/LauncherModel.cpp
        app/native/LauncherModel.h
    )
    target_link_libraries(launcher_model_tests PRIVATE Qt6::Core Qt6::Gui Qt6::Test)
    add_test(NAME launcher_model_tests COMMAND launcher_model_tests)
endif()
