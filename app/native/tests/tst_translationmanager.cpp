#include "../TranslationManager.h"

#include <QCoreApplication>
#include <QQmlComponent>
#include <QQmlEngine>
#include <QSettings>
#include <QTemporaryDir>
#include <QtTest>

class TranslationManagerTest final : public QObject
{
    Q_OBJECT

private slots:
    void initTestCase()
    {
        QCoreApplication::setOrganizationName(QStringLiteral("OmaAppTests"));
        QCoreApplication::setApplicationName(QStringLiteral("TranslationManagerTests"));
        QSettings::setDefaultFormat(QSettings::IniFormat);
        QSettings::setPath(QSettings::IniFormat, QSettings::UserScope, m_settings.path());
        QSettings settings;
        settings.clear();
        settings.sync();
    }

    void init()
    {
        QSettings settings;
        settings.clear();
        settings.sync();
    }

    void defaultsToSystemLanguage()
    {
        QQmlEngine engine;
        TranslationManager translations(&engine);
        QCOMPARE(translations.languagePreference(), QStringLiteral("system"));
        QVERIFY(translations.language() == QStringLiteral("en")
                || translations.language() == QStringLiteral("ru"));
    }

    void selectsTheFirstSupportedSystemLanguageInPreferenceOrder()
    {
        QCOMPARE(TranslationManager::languageForSystemPreferences(
                     {QStringLiteral("en-US"), QStringLiteral("ru-RU")}, QStringLiteral("ru_RU")),
                 QStringLiteral("en"));
        QCOMPARE(TranslationManager::languageForSystemPreferences(
                     {QStringLiteral("de-DE"), QStringLiteral("ru-RU"), QStringLiteral("en-US")},
                     QStringLiteral("en_US")),
                 QStringLiteral("ru"));
        QCOMPARE(TranslationManager::languageForSystemPreferences(
                     {}, QStringLiteral("ru_RU")), QStringLiteral("ru"));
        QCOMPARE(TranslationManager::languageForSystemPreferences(
                     {QStringLiteral("de-DE")}, QStringLiteral("en_US")),
                 QStringLiteral("en"));
    }

    void switchesQmlAndPersistsPreference()
    {
        QSettings settings;
        settings.setValue(QStringLiteral("app/language"), QStringLiteral("en"));
        settings.sync();

        QQmlEngine engine;
        QQmlComponent component(&engine);
        component.setData(QByteArrayLiteral(
            "import QtQml\n"
            "QtObject { property string label: qsTranslate('Main', 'Home') }"), QUrl());
        std::unique_ptr<QObject> object(component.create());
        QVERIFY2(object != nullptr, qPrintable(component.errorString()));

        TranslationManager translations(&engine);
        QCOMPARE(object->property("label").toString(), QStringLiteral("Home"));
        QVERIFY(translations.setLanguage(QStringLiteral("ru")));
        QCOMPARE(object->property("label").toString(), QStringLiteral("Главный экран"));
        QCOMPARE(QCoreApplication::translate("Main", "Computer files"), QStringLiteral("Файлы компьютера"));
        QCOMPARE(translations.language(), QStringLiteral("ru"));

        settings.sync();
        QCOMPARE(settings.value(QStringLiteral("app/language")).toString(), QStringLiteral("ru"));

        QVERIFY(translations.setLanguage(QStringLiteral("en")));
        QCOMPARE(object->property("label").toString(), QStringLiteral("Home"));
    }

    void rejectsUnsupportedLanguage()
    {
        QQmlEngine engine;
        TranslationManager translations(&engine);
        const QString before = translations.languagePreference();
        QVERIFY(!translations.setLanguage(QStringLiteral("fr")));
        QCOMPARE(translations.languagePreference(), before);
    }

private:
    QTemporaryDir m_settings;
};

QTEST_GUILESS_MAIN(TranslationManagerTest)
#include "tst_translationmanager.moc"
