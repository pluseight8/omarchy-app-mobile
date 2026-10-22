#include "LauncherModel.h"
#include "PairingModel.h"
#include "shared/geometry/HyprlandGeometry.h"
#include "shared/geometry/HyprlandSurfaceItem.h"
#include "OwlModel.h"

#include <QDebug>
#include <QGuiApplication>
#include <QQmlApplicationEngine>
#include <QQuickWindow>
#include <QSGRendererInterface>
#include <QQuickStyle>
#include <QtQml/qqml.h>

int main(int argc, char *argv[])
{
#if defined(OMAAPP_TEST_RHI_BACKEND_OPENGL)
    qWarning().noquote() << "OmaApp startup: native main entered";
    QQuickWindow::setGraphicsApi(QSGRendererInterface::OpenGL);
    qputenv("QSG_INFO", "1");
#elif defined(OMAAPP_TEST_RHI_BACKEND_VULKAN)
    qWarning().noquote() << "OmaApp startup: native main entered";
    QQuickWindow::setGraphicsApi(QSGRendererInterface::Vulkan);
    qputenv("QSG_INFO", "1");
#endif

    QQuickStyle::setStyle(QStringLiteral("Basic"));
#if defined(OMAAPP_TEST_RHI_BACKEND_OPENGL) || defined(OMAAPP_TEST_RHI_BACKEND_VULKAN)
    qWarning().noquote() << "OmaApp startup: constructing QGuiApplication";
#endif
    QGuiApplication app(argc, argv);
#if defined(OMAAPP_TEST_RHI_BACKEND_OPENGL)
    qWarning().noquote() << "OmaApp startup: QGuiApplication ready";
    qWarning().noquote() << "OmaApp configured Qt Quick graphics API: OpenGL";
#elif defined(OMAAPP_TEST_RHI_BACKEND_VULKAN)
    qWarning().noquote() << "OmaApp startup: QGuiApplication ready";
    qWarning().noquote() << "OmaApp configured Qt Quick graphics API: Vulkan";
#endif
    app.setApplicationName(QStringLiteral("OmaApp"));
    app.setOrganizationName(QStringLiteral("OmaApp"));

    HyprlandGeometry geometry;
    qmlRegisterType<LauncherModel>("OmaApp.Native", 1, 0, "LauncherModel");
    qmlRegisterType<PairingModel>("OmaApp.Native", 1, 0, "PairingModel");
    qmlRegisterType<OwlModel>("OmaApp.Native", 1, 0, "OwlModel");
    qmlRegisterType<HyprlandSurfaceItem>("OmaApp.Native", 1, 0, "HyprlandSurfaceItem");
    qmlRegisterSingletonInstance("OmaApp.Native", 1, 0, "Geometry", &geometry);

    QQmlApplicationEngine engine;
#if defined(OMAAPP_TEST_RHI_BACKEND_OPENGL) || defined(OMAAPP_TEST_RHI_BACKEND_VULKAN)
    qWarning().noquote() << "OmaApp startup: loading QML module";
#endif
    QObject::connect(
        &engine,
        &QQmlApplicationEngine::objectCreationFailed,
        &app,
        [] { QCoreApplication::exit(EXIT_FAILURE); },
        Qt::QueuedConnection);
    engine.loadFromModule("OmaApp", "Main");

#if defined(OMAAPP_TEST_RHI_BACKEND_OPENGL) || defined(OMAAPP_TEST_RHI_BACKEND_VULKAN)
    qWarning().noquote() << "OmaApp startup: QML module load returned"
                         << engine.rootObjects().size() << "root objects";
    if (!engine.rootObjects().isEmpty()) {
        if (auto *window = qobject_cast<QQuickWindow *>(engine.rootObjects().constFirst())) {
            const auto logGraphicsApi = [window] {
                const auto api = window->rendererInterface()->graphicsApi();
                const char *name = "Unknown";
                switch (api) {
                case QSGRendererInterface::OpenGL: name = "OpenGL"; break;
                case QSGRendererInterface::Vulkan: name = "Vulkan"; break;
                case QSGRendererInterface::Metal: name = "Metal"; break;
                case QSGRendererInterface::Direct3D11: name = "Direct3D11"; break;
                case QSGRendererInterface::Direct3D12: name = "Direct3D12"; break;
                case QSGRendererInterface::Software: name = "Software"; break;
                default: break;
                }
                qWarning().noquote() << "OmaApp initialized Qt Quick graphics API:" << name;
            };
            if (window->isSceneGraphInitialized()) {
                logGraphicsApi();
            } else {
                QObject::connect(window, &QQuickWindow::sceneGraphInitialized, window,
                                 logGraphicsApi, Qt::DirectConnection);
            }
        }
    }
    qWarning().noquote() << "OmaApp startup: entering event loop";
#endif

    return app.exec();
}
