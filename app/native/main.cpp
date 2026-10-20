#include "LauncherModel.h"
#include "PairingModel.h"
#include "shared/geometry/HyprlandGeometry.h"

#include <QGuiApplication>
#include <QQmlApplicationEngine>
#include <QQuickStyle>
#include <QtQml/qqml.h>

int main(int argc, char *argv[])
{
    QQuickStyle::setStyle(QStringLiteral("Basic"));
    QGuiApplication app(argc, argv);
    app.setApplicationName(QStringLiteral("OmaApp"));
    app.setOrganizationName(QStringLiteral("OmaApp"));

    HyprlandGeometry geometry;
    qmlRegisterType<LauncherModel>("OmaApp.Native", 1, 0, "LauncherModel");
    qmlRegisterType<PairingModel>("OmaApp.Native", 1, 0, "PairingModel");
    qmlRegisterSingletonInstance("OmaApp.Native", 1, 0, "Geometry", &geometry);

    QQmlApplicationEngine engine;
    QObject::connect(
        &engine,
        &QQmlApplicationEngine::objectCreationFailed,
        &app,
        [] { QCoreApplication::exit(EXIT_FAILURE); },
        Qt::QueuedConnection);
    engine.loadFromModule("OmaApp", "Main");

    return app.exec();
}
