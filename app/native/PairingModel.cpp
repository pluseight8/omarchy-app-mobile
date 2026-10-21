#include "PairingModel.h"

#include <QJsonDocument>
#include <QJsonObject>
#include <QTimer>
#include <QUrl>

#ifdef Q_OS_ANDROID
#include <QJniObject>
#include <QtCore/qcoreapplication_platform.h>
#endif

PairingModel::PairingModel(QObject *parent)
    : QObject(parent)
{
#ifdef Q_OS_ANDROID
    m_platformSupported = true;
#endif
    m_pollTimer = new QTimer(this);
    m_pollTimer->setInterval(750);
    connect(m_pollTimer, &QTimer::timeout, this, &PairingModel::poll);
    m_pollTimer->start();
    poll();
}

bool PairingModel::platformSupported() const { return m_platformSupported; }
QString PairingModel::phase() const { return m_phase; }
QString PairingModel::hostName() const { return m_hostName; }
QString PairingModel::error() const { return m_error; }
bool PairingModel::connected() const { return m_connected; }
bool PairingModel::hasPairedDevice() const { return m_hasPairedDevice; }
bool PairingModel::hasPendingRequest() const { return m_hasPendingRequest; }
QVariantMap PairingModel::snapshot() const { return m_snapshot; }
QVariantMap PairingModel::features() const { return m_features; }
QString PairingModel::featureMessage() const { return m_featureMessage; }
QString PairingModel::wallpaperUrl() const { return m_wallpaperUrl; }
QString PairingModel::candidateHostName() const { return m_candidateHostName; }
QString PairingModel::candidateFingerprint() const { return m_candidateFingerprint; }
bool PairingModel::hasCandidate() const { return !m_candidateQr.isEmpty(); }

void PairingModel::startScan()
{
#ifdef Q_OS_ANDROID
    const QJniObject context = QNativeInterface::QAndroidApplication::context();
    QJniObject::callStaticMethod<void>(
        "app/omaapp/PairingBridge", "startQrScanner",
        "(Landroid/content/Context;)V", context.object<jobject>());
#else
    m_error = tr("QR pairing is available in the Android app.");
    emit stateChanged();
#endif
}

void PairingModel::approveCandidate()
{
    if (m_candidateQr.isEmpty())
        return;
#ifdef Q_OS_ANDROID
    const QJniObject context = QNativeInterface::QAndroidApplication::context();
    const QJniObject qr = QJniObject::fromString(m_candidateQr);
    QJniObject::callStaticMethod<void>(
        "app/omaapp/PairingBridge", "beginPairing",
        "(Landroid/content/Context;Ljava/lang/String;)V",
        context.object<jobject>(), qr.object<jstring>());
#endif
    discardCandidate();
}

void PairingModel::discardCandidate()
{
    if (m_candidateQr.isEmpty())
        return;
    m_candidateQr.clear();
    m_candidateHostName.clear();
    m_candidateFingerprint.clear();
    emit candidateChanged();
}

void PairingModel::cancelPairing()
{
#ifdef Q_OS_ANDROID
    const QJniObject context = QNativeInterface::QAndroidApplication::context();
    QJniObject::callStaticMethod<void>(
        "app/omaapp/PairingBridge", "cancelPairing",
        "(Landroid/content/Context;)V", context.object<jobject>());
#endif
}

void PairingModel::revoke()
{
#ifdef Q_OS_ANDROID
    const QJniObject context = QNativeInterface::QAndroidApplication::context();
    QJniObject::callStaticMethod<void>(
        "app/omaapp/PairingBridge", "revoke",
        "(Landroid/content/Context;)V", context.object<jobject>());
#endif
}

void PairingModel::forget()
{
#ifdef Q_OS_ANDROID
    const QJniObject context = QNativeInterface::QAndroidApplication::context();
    QJniObject::callStaticMethod<void>(
        "app/omaapp/PairingBridge", "forget",
        "(Landroid/content/Context;)V", context.object<jobject>());
#endif
}

void PairingModel::pushClipboard()
{
#ifdef Q_OS_ANDROID
    const QJniObject context = QNativeInterface::QAndroidApplication::context();
    QJniObject::callStaticMethod<void>(
        "app/omaapp/PairingBridge", "pushClipboard", "(Landroid/content/Context;)V",
        context.object<jobject>());
#endif
}

void PairingModel::pullClipboard()
{
#ifdef Q_OS_ANDROID
    const QJniObject context = QNativeInterface::QAndroidApplication::context();
    QJniObject::callStaticMethod<void>(
        "app/omaapp/PairingBridge", "pullClipboard", "(Landroid/content/Context;)V",
        context.object<jobject>());
#endif
}

void PairingModel::startFileTransfer()
{
#ifdef Q_OS_ANDROID
    const QJniObject context = QNativeInterface::QAndroidApplication::context();
    QJniObject::callStaticMethod<void>(
        "app/omaapp/PairingBridge", "startFileTransfer", "(Landroid/content/Context;)V",
        context.object<jobject>());
#endif
}

void PairingModel::activateWorkspace(int workspaceId)
{
#ifdef Q_OS_ANDROID
    if (workspaceId < 0 || workspaceId > 1000)
        return;
    const QJniObject context = QNativeInterface::QAndroidApplication::context();
    QJniObject::callStaticMethod<void>(
        "app/omaapp/PairingBridge", "activateWorkspace", "(Landroid/content/Context;I)V",
        context.object<jobject>(), workspaceId);
#endif
}

void PairingModel::mediaAction(const QString &player, const QString &action)
{
#ifdef Q_OS_ANDROID
    const QJniObject context = QNativeInterface::QAndroidApplication::context();
    const QJniObject playerName = QJniObject::fromString(player);
    const QJniObject command = QJniObject::fromString(action);
    QJniObject::callStaticMethod<void>(
        "app/omaapp/PairingBridge", "mediaAction",
        "(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;)V",
        context.object<jobject>(), playerName.object<jstring>(), command.object<jstring>());
#endif
}

void PairingModel::executeCommand(const QString &commandId)
{
#ifdef Q_OS_ANDROID
    if (commandId.size() > 48)
        return;
    const QJniObject context = QNativeInterface::QAndroidApplication::context();
    const QJniObject id = QJniObject::fromString(commandId);
    QJniObject::callStaticMethod<void>(
        "app/omaapp/PairingBridge", "executeCommand",
        "(Landroid/content/Context;Ljava/lang/String;)V",
        context.object<jobject>(), id.object<jstring>());
#endif
}

void PairingModel::poll()
{
#ifdef Q_OS_ANDROID
    const QJniObject context = QNativeInterface::QAndroidApplication::context();
    QJniObject::callStaticMethod<void>(
        "app/omaapp/PairingBridge", "refresh",
        "(Landroid/content/Context;)V", context.object<jobject>());

    const QJniObject scanned = QJniObject::callStaticObjectMethod(
        "app/omaapp/PairingBridge", "consumeScannedQr",
        "(Landroid/content/Context;)Ljava/lang/String;", context.object<jobject>());
    const QString qr = scanned.toString();
    if (!qr.isEmpty())
        setCandidate(qr);

    const QJniObject state = QJniObject::callStaticObjectMethod(
        "app/omaapp/PairingBridge", "stateJson",
        "(Landroid/content/Context;)Ljava/lang/String;", context.object<jobject>());
    const QJsonDocument document = QJsonDocument::fromJson(state.toString().toUtf8());
    if (!document.isObject())
        return;

    const QJsonObject object = document.object();
    const QJsonObject snapshotObject = object.value(QStringLiteral("snapshot")).toObject();
    const QVariantMap nextFeatures = object.value(QStringLiteral("features")).toObject().toVariantMap();
    const QString nextFeatureMessage = object.value(QStringLiteral("featureMessage")).toString();
    const QString wallpaperPath = object.value(QStringLiteral("wallpaperPath")).toString();
    const QString wallpaperUrl = wallpaperPath.isEmpty()
            ? QString()
            : QUrl::fromLocalFile(wallpaperPath).toString();
    const QVariantMap nextSnapshot = snapshotObject.toVariantMap();
    const QString nextPhase = object.value(QStringLiteral("phase")).toString(QStringLiteral("unpaired"));
    const QString nextHost = object.value(QStringLiteral("hostName")).toString();
    const QString nextError = object.value(QStringLiteral("lastError")).toString();
    const bool nextConnected = object.value(QStringLiteral("connected")).toBool();
    const bool nextPaired = object.value(QStringLiteral("hasPairedDevice")).toBool();
    const bool nextPending = object.value(QStringLiteral("hasPendingRequest")).toBool();

    if (m_phase != nextPhase || m_hostName != nextHost || m_error != nextError
        || m_connected != nextConnected || m_hasPairedDevice != nextPaired
        || m_hasPendingRequest != nextPending || m_snapshot != nextSnapshot
        || m_features != nextFeatures || m_featureMessage != nextFeatureMessage
        || m_wallpaperUrl != wallpaperUrl) {
        m_phase = nextPhase;
        m_hostName = nextHost;
        m_error = nextError;
        m_connected = nextConnected;
        m_hasPairedDevice = nextPaired;
        m_hasPendingRequest = nextPending;
        m_snapshot = nextSnapshot;
        m_features = nextFeatures;
        m_featureMessage = nextFeatureMessage;
        m_wallpaperUrl = wallpaperUrl;
        emit stateChanged();
    }
#endif
}

void PairingModel::setCandidate(const QString &qr)
{
    if (qr == m_candidateQr)
        return;
    const QJsonDocument document = QJsonDocument::fromJson(qr.toUtf8());
    if (!document.isObject())
        return;
    const QJsonObject object = document.object();
    if (object.value(QStringLiteral("schema")).toInt() != 1)
        return;

    m_candidateQr = qr;
    m_candidateHostName = object.value(QStringLiteral("hostName")).toString();
    m_candidateFingerprint = object.value(QStringLiteral("fingerprint")).toString();
    emit candidateChanged();
}
