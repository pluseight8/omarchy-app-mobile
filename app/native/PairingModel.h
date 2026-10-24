#pragma once

#include <QObject>
#include <QVariantMap>

class QTimer;

class PairingModel : public QObject
{
    Q_OBJECT
    Q_PROPERTY(bool platformSupported READ platformSupported CONSTANT)
    Q_PROPERTY(QString phase READ phase NOTIFY stateChanged)
    Q_PROPERTY(QString hostName READ hostName NOTIFY stateChanged)
    Q_PROPERTY(QString error READ error NOTIFY stateChanged)
    Q_PROPERTY(bool connected READ connected NOTIFY stateChanged)
    Q_PROPERTY(bool hasPairedDevice READ hasPairedDevice NOTIFY stateChanged)
    Q_PROPERTY(bool hasPendingRequest READ hasPendingRequest NOTIFY stateChanged)
    Q_PROPERTY(QVariantMap snapshot READ snapshot NOTIFY stateChanged)
    Q_PROPERTY(QVariantMap features READ features NOTIFY stateChanged)
    Q_PROPERTY(QString featureMessage READ featureMessage NOTIFY stateChanged)
    Q_PROPERTY(QString wallpaperUrl READ wallpaperUrl NOTIFY stateChanged)
    Q_PROPERTY(QString candidateHostName READ candidateHostName NOTIFY candidateChanged)
    Q_PROPERTY(QString candidateFingerprint READ candidateFingerprint NOTIFY candidateChanged)
    Q_PROPERTY(bool hasCandidate READ hasCandidate NOTIFY candidateChanged)

public:
    explicit PairingModel(QObject *parent = nullptr);

    bool platformSupported() const;
    QString phase() const;
    QString hostName() const;
    QString error() const;
    bool connected() const;
    bool hasPairedDevice() const;
    bool hasPendingRequest() const;
    QVariantMap snapshot() const;
    QVariantMap features() const;
    QString featureMessage() const;
    QString wallpaperUrl() const;
    QString candidateHostName() const;
    QString candidateFingerprint() const;
    bool hasCandidate() const;

    Q_INVOKABLE void startScan();
    Q_INVOKABLE void retry();
    Q_INVOKABLE void approveCandidate();
    Q_INVOKABLE void discardCandidate();
    Q_INVOKABLE void cancelPairing();
    Q_INVOKABLE void revoke();
    Q_INVOKABLE void forget();
    Q_INVOKABLE void pushClipboard();
    Q_INVOKABLE void pullClipboard();
    Q_INVOKABLE void startFileTransfer();
    Q_INVOKABLE void activateWorkspace(int workspaceId);
    Q_INVOKABLE void mediaAction(const QString &player, const QString &action);
    Q_INVOKABLE void executeCommand(const QString &commandId);

signals:
    void stateChanged();
    void candidateChanged();

private:
    bool m_platformSupported = false;
    QString m_phase = QStringLiteral("unpaired");
    QString m_hostName;
    QString m_error;
    bool m_connected = false;
    bool m_hasPairedDevice = false;
    bool m_hasPendingRequest = false;
    QVariantMap m_snapshot;
    QVariantMap m_features;
    QString m_featureMessage;
    QString m_wallpaperUrl;
    QString m_candidateQr;
    QString m_candidateHostName;
    QString m_candidateFingerprint;
    QTimer *m_pollTimer = nullptr;

    void poll();
    void setCandidate(const QString &qr);
};
