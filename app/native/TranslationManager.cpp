#include "TranslationManager.h"

#include <QCoreApplication>
#include <QDebug>
#include <QLocale>
#include <QQmlEngine>
#include <QSettings>
#include <QTranslator>

#include <utility>

TranslationManager::TranslationManager(QQmlEngine *engine, QObject *parent)
    : QObject(parent)
    , m_engine(engine)
{
    QSettings settings;
    const QString preference = settings.value(QStringLiteral("app/language"),
                                               QStringLiteral("system")).toString();
    if (!applyPreference(preference, false))
        applyPreference(QStringLiteral("system"), false);
}

TranslationManager::~TranslationManager()
{
    if (m_translatorInstalled && m_translator)
        QCoreApplication::removeTranslator(m_translator.get());
}

QString TranslationManager::language() const
{
    return m_language;
}

QString TranslationManager::languagePreference() const
{
    return m_languagePreference;
}

QString TranslationManager::systemLanguage()
{
    const auto uiLanguages = QLocale::system().uiLanguages();
    for (const QString &locale : uiLanguages) {
        if (locale.startsWith(QStringLiteral("ru"), Qt::CaseInsensitive))
            return QStringLiteral("ru");
    }
    return QLocale::system().name().startsWith(QStringLiteral("ru"), Qt::CaseInsensitive)
        ? QStringLiteral("ru")
        : QStringLiteral("en");
}

bool TranslationManager::setLanguage(const QString &preference)
{
    return applyPreference(preference, true);
}

bool TranslationManager::applyPreference(const QString &preference, bool persist)
{
    if (preference != QStringLiteral("system")
        && preference != QStringLiteral("en")
        && preference != QStringLiteral("ru")) {
        return false;
    }

    const QString effective = preference == QStringLiteral("system")
        ? systemLanguage()
        : preference;
    auto nextTranslator = std::make_unique<QTranslator>();
    bool installNextTranslator = effective == QStringLiteral("ru");
    if (installNextTranslator && !nextTranslator->load(QStringLiteral(":/i18n/omaapp_ru.qm"))) {
        qWarning() << "Russian translation catalog is unavailable; using English";
        if (persist && preference == QStringLiteral("ru"))
            return false;
        installNextTranslator = false;
    }

    if (persist) {
        QSettings settings;
        settings.setValue(QStringLiteral("app/language"), preference);
        settings.sync();
        if (settings.status() != QSettings::NoError) {
            qWarning() << "Unable to persist app language preference";
            return false;
        }
    }

    const bool languageChangedValue = m_language != (installNextTranslator ? effective : QStringLiteral("en"));
    const bool preferenceChangedValue = m_languagePreference != preference;
    auto previousTranslator = std::move(m_translator);
    const bool previousInstalled = m_translatorInstalled;
    m_translatorInstalled = false;

    if (installNextTranslator) {
        if (!QCoreApplication::installTranslator(nextTranslator.get())) {
            qWarning() << "Unable to install Russian translation catalog";
            m_translator = std::move(previousTranslator);
            m_translatorInstalled = previousInstalled;
            if (persist) {
                QSettings settings;
                settings.setValue(QStringLiteral("app/language"), m_languagePreference);
                settings.sync();
            }
            return false;
        }
        if (previousInstalled && previousTranslator)
            QCoreApplication::removeTranslator(previousTranslator.get());
        m_translator = std::move(nextTranslator);
        m_translatorInstalled = true;
    } else {
        if (previousInstalled && previousTranslator)
            QCoreApplication::removeTranslator(previousTranslator.get());
        m_translator = std::move(nextTranslator);
    }

    m_language = m_translatorInstalled ? effective : QStringLiteral("en");
    m_languagePreference = preference;

    if (languageChangedValue) {
        emit languageChanged();
        if (m_engine)
            m_engine->retranslate();
    }
    if (preferenceChangedValue)
        emit languagePreferenceChanged();
    return true;
}
