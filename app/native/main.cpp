#include "LauncherModel.h"
#include "PairingModel.h"
#include "shared/geometry/HyprlandGeometry.h"
#include "shared/geometry/HyprlandSurfaceItem.h"
#include "OwlModel.h"
#include "TranslationManager.h"

#include <QDebug>
#include <QFile>
#include <QGuiApplication>
#include <QQmlContext>
#include <QQmlApplicationEngine>
#include <QQuickWindow>
#include <QSGRendererInterface>
#include <QQuickStyle>
#include <QMetaObject>
#include <QPointer>
#include <QStringList>
#include <QtQml/qqml.h>

#ifdef Q_OS_ANDROID
#include <jni.h>
#endif

#ifdef Q_OS_ANDROID
namespace {
QPointer<LauncherModel> launcherModel;
}

extern "C" JNIEXPORT void JNICALL
Java_app_omaapp_OmaAppActivity_refreshLauncherApps(JNIEnv *, jclass)
{
    if (launcherModel)
        QMetaObject::invokeMethod(launcherModel.data(), "refresh", Qt::QueuedConnection);
}

extern "C" JNIEXPORT jstring JNICALL
Java_app_omaapp_NativeStrings_translate(JNIEnv *environment, jclass, jstring source)
{
    if (source == nullptr)
        return environment->NewStringUTF("");

    const jchar *characters = environment->GetStringChars(source, nullptr);
    if (characters == nullptr)
        return nullptr;
    const QString sourceText(reinterpret_cast<const QChar *>(characters),
                             environment->GetStringLength(source));
    environment->ReleaseStringChars(source, characters);

    const QByteArray sourceUtf8 = sourceText.toUtf8();
    const QString translated = QCoreApplication::translate("Main", sourceUtf8.constData());
    return environment->NewString(reinterpret_cast<const jchar *>(translated.utf16()),
                                  translated.size());
}
#endif

namespace {

QString openSourceNotices()
{
    const QStringList resources = {
        QStringLiteral(":/licenses/app/licenses/NOTICE.md"),
        QStringLiteral(":/licenses/LICENSE"),
        QStringLiteral(":/licenses/THIRD_PARTY.md"),
        QStringLiteral(":/licenses/app/licenses/LGPL-3.0.txt"),
        QStringLiteral(":/licenses/app/licenses/Apache-2.0.txt"),
        QStringLiteral(":/licenses/app/android/kotlin/dev/omalauncher/widgets/lang/LICENSE-MIT")
    };
    QStringList sections;
    for (const auto &resource : resources) {
        QFile file(resource);
        if (!file.open(QIODevice::ReadOnly)) {
            qCritical().noquote() << "Unable to load open-source notice resource:" << resource;
            continue;
        }
        sections.append(QString::fromUtf8(file.readAll()).trimmed());
    }
    return sections.join(QStringLiteral("\n\n────────────────────────────────────────\n\n"));
}

} // namespace

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

    static constexpr const char *androidUiStrings[] = {
        QT_TRANSLATE_NOOP("Main", "Scan the temporary OmaApp pairing QR code"),
        QT_TRANSLATE_NOOP("Main", "Cancel"),
        QT_TRANSLATE_NOOP("Main", "QR scanned. Return to OmaApp to review the computer."),
        QT_TRANSLATE_NOOP("Main", "Could not securely store the pairing code. Please scan again."),
        QT_TRANSLATE_NOOP("Main", "Camera permission is needed to scan the pairing QR code."),
        QT_TRANSLATE_NOOP("Main", "Files · %1"),
        QT_TRANSLATE_NOOP("Main", "Files move through your pinned local connection. The computer saves uploads in Downloads/OmaApp."),
        QT_TRANSLATE_NOOP("Main", "Send a file to computer"),
        QT_TRANSLATE_NOOP("Main", "Save a computer file to phone"),
        QT_TRANSLATE_NOOP("Main", "Enable Send files to phone or Receive files from phone in the Omarchy plugin."),
        QT_TRANSLATE_NOOP("Main", "Close"),
        QT_TRANSLATE_NOOP("Main", "Loading files shared from the computer…"),
        QT_TRANSLATE_NOOP("Main", "No files are shared from Downloads/OmaApp on this computer."),
        QT_TRANSLATE_NOOP("Main", "Choose a file to save to the phone."),
        QT_TRANSLATE_NOOP("Main", "Computer files"),
        QT_TRANSLATE_NOOP("Main", "Choose a named file between 1 byte and 100 MiB."),
        QT_TRANSLATE_NOOP("Main", "Sending %1…"),
        QT_TRANSLATE_NOOP("Main", "Saving %1 to the selected location…"),
        QT_TRANSLATE_NOOP("Main", "Computer rejected the file request (HTTP %1)."),
        QT_TRANSLATE_NOOP("Main", "Android could not open the selected destination."),
        QT_TRANSLATE_NOOP("Main", "Saved %1 to the selected phone location."),
        QT_TRANSLATE_NOOP("Main", "Computer file transfer failed. Check pairing and the network.")
    };
    Q_UNUSED(androidUiStrings);

    HyprlandGeometry geometry;
    qmlRegisterType<LauncherModel>("OmaApp.Native", 1, 0, "LauncherModel");
    qmlRegisterType<PairingModel>("OmaApp.Native", 1, 0, "PairingModel");
    qmlRegisterType<OwlModel>("OmaApp.Native", 1, 0, "OwlModel");
    qmlRegisterType<HyprlandSurfaceItem>("OmaApp.Native", 1, 0, "HyprlandSurfaceItem");
    qmlRegisterSingletonInstance("OmaApp.Native", 1, 0, "Geometry", &geometry);

    QQmlApplicationEngine engine;
    TranslationManager translations(&engine);
    engine.rootContext()->setContextProperty(QStringLiteral("appTranslations"), &translations);
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
    if (!engine.rootObjects().isEmpty()) {
        QObject *root = engine.rootObjects().constFirst();
        root->setProperty("openSourceNoticesText", openSourceNotices());
#ifdef Q_OS_ANDROID
        launcherModel = root->findChild<LauncherModel *>(QStringLiteral("omaappLauncherModel"));
        if (!launcherModel)
            qWarning() << "OmaApp could not attach Android launcher lifecycle refresh";
#endif
    }

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
