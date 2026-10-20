cmake_minimum_required(VERSION 3.24)

if(NOT DEFINED OMAAPP_VERSION OR OMAAPP_VERSION STREQUAL "")
    set(OMAAPP_VERSION "0.1.0")
endif()

project(OmaApp VERSION "${OMAAPP_VERSION}" LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_AUTOMOC ON)

find_package(Qt6 6.11 REQUIRED COMPONENTS Core Gui Qml Quick QuickControls2 ShaderTools)
qt_standard_project_setup(REQUIRES 6.11)

qt_add_executable(omaapp
    app/native/main.cpp
    app/native/LauncherModel.cpp
    app/native/LauncherModel.h
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

# Qt 6's cross-API shader pipeline turns Vulkan-style GLSL into the QSB
# package consumed by OpenGL, Vulkan, Metal, and Direct3D scene graph backends.
qt_add_shaders(omaapp omaapp_rounding_shaders
    PREFIX "/shaders"
    BASE app/qml/shaders
    FILES
        app/qml/shaders/hyprland_rounded.frag
)

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
if(BUILD_TESTING)
    find_package(Qt6 6.6 REQUIRED COMPONENTS Test)
    qt_add_executable(geometry_tests
        shared/geometry/tests/tst_geometry.cpp
        shared/geometry/HyprlandGeometry.cpp
    )
    target_link_libraries(geometry_tests PRIVATE Qt6::Core Qt6::Test)
    add_test(NAME geometry_tests COMMAND geometry_tests)
endif()
