#pragma once

#include <QObject>
#include <QString>

#include <memory>

class QQmlEngine;
class QTranslator;

class TranslationManager final : public QObject
{
    Q_OBJECT
    Q_PROPERTY(QString language READ language NOTIFY languageChanged)
    Q_PROPERTY(QString languagePreference READ languagePreference NOTIFY languagePreferenceChanged)

public:
    explicit TranslationManager(QQmlEngine *engine, QObject *parent = nullptr);
    ~TranslationManager() override;

    QString language() const;
    QString languagePreference() const;

    Q_INVOKABLE bool setLanguage(const QString &preference);

signals:
    void languageChanged();
    void languagePreferenceChanged();

private:
    static QString systemLanguage();
    bool applyPreference(const QString &preference, bool persist);

    QQmlEngine *m_engine = nullptr;
    std::unique_ptr<QTranslator> m_translator;
    QString m_language = QStringLiteral("en");
    QString m_languagePreference = QStringLiteral("system");
    bool m_translatorInstalled = false;
};
