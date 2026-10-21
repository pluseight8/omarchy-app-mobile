#include "../LauncherModel.h"

#include <QCoreApplication>
#include <QSettings>
#include <QTemporaryDir>
#include <QtTest>

class LauncherModelTest : public QObject
{
    Q_OBJECT

private slots:
    void initTestCase()
    {
        QSettings::setDefaultFormat(QSettings::IniFormat);
        QSettings::setPath(QSettings::IniFormat, QSettings::UserScope, m_settingsDirectory.path());
        QCoreApplication::setOrganizationName(QStringLiteral("OmaAppTests"));
        QCoreApplication::setApplicationName(QStringLiteral("LauncherModelTests"));
    }

    void init()
    {
        QSettings settings;
        settings.clear();
        settings.sync();
    }

    void foldersPersistAcrossModelInstances()
    {
        QString folderId;
        {
            LauncherModel model;
            folderId = model.createFolder(QStringLiteral("  Media  "));
            QVERIFY(!folderId.isEmpty());
            QCOMPARE(model.folders().size(), 1);
            QCOMPARE(model.folders().first().toMap().value(QStringLiteral("name")).toString(),
                     QStringLiteral("Media"));
        }

        LauncherModel restored;
        QCOMPARE(restored.folders().size(), 1);
        QCOMPARE(restored.folders().first().toMap().value(QStringLiteral("id")).toString(),
                 folderId);
        QVERIFY(restored.renameFolder(folderId, QStringLiteral("Entertainment")));

        LauncherModel renamed;
        QCOMPARE(renamed.folders().first().toMap().value(QStringLiteral("name")).toString(),
                 QStringLiteral("Entertainment"));
        QVERIFY(renamed.deleteFolder(folderId));
        LauncherModel deleted;
        QVERIFY(deleted.folders().isEmpty());
    }

    void duplicateAndInvalidFolderNamesAreRejected()
    {
        LauncherModel model;
        QVERIFY(model.createFolder(QStringLiteral("Work")).size() > 0);
        QVERIFY(model.createFolder(QStringLiteral(" work ")).isEmpty());
        QVERIFY(model.createFolder(QString()).isEmpty());
        QVERIFY(model.createFolder(QStringLiteral(" ")).isEmpty());
        QVERIFY(model.createFolder(QStringLiteral("bad\nname")).isEmpty());
        QVERIFY(model.createFolder(QString(41, QLatin1Char('x'))).isEmpty());
        QVERIFY(!model.renameFolder(
            model.folders().first().toMap().value(QStringLiteral("id")).toString(),
            QStringLiteral(" ")));
    }

    void folderCountIsBounded()
    {
        LauncherModel model;
        for (int index = 0; index < 24; ++index)
            QVERIFY(!model.createFolder(QStringLiteral("Folder %1").arg(index)).isEmpty());
        QCOMPARE(model.folders().size(), 24);
        QVERIFY(model.createFolder(QStringLiteral("One too many")).isEmpty());
    }

    void invalidSavedFolderDataIsIgnored()
    {
        QSettings settings;
        settings.setValue(QStringLiteral("launcher/folders"), QByteArray("not-json"));
        settings.sync();

        LauncherModel model;
        QVERIFY(model.folders().isEmpty());
        QVERIFY(!model.createFolder(QStringLiteral("Fresh start")).isEmpty());
    }

    void gridColumnPreferencePersistsWithinSupportedRange()
    {
        LauncherModel model;
        QCOMPARE(model.gridColumns(), 4);
        model.setGridColumns(3);
        QCOMPARE(model.gridColumns(), 3);

        LauncherModel restored;
        QCOMPARE(restored.gridColumns(), 3);
        restored.setGridColumns(99);
        QCOMPARE(restored.gridColumns(), 5);
        restored.setGridColumns(-1);
        QCOMPARE(restored.gridColumns(), 3);
    }

    void homePageCountAndWidgetPreferencesPersistWithinSupportedRange()
    {
        {
            LauncherModel model;
            QCOMPARE(model.homePageCount(), 1);
            QCOMPARE(model.homeWidgets().value(QStringLiteral("clock")).toBool(), true);
            model.setHomePageCount(3);
            model.setHomeWidgetEnabled(QStringLiteral("clock"), false);
            model.setHomeWidgetEnabled(QStringLiteral("unknown-widget"), false);
        }

        LauncherModel restored;
        QCOMPARE(restored.homePageCount(), 3);
        QCOMPARE(restored.homeWidgets().value(QStringLiteral("clock")).toBool(), false);
        QCOMPARE(restored.homeWidgets().value(QStringLiteral("computer")).toBool(), true);
        QCOMPARE(restored.homeWidgets().value(QStringLiteral("unknown-widget")).isValid(), false);
        restored.setHomePageCount(99);
        QCOMPARE(restored.homePageCount(), 5);
        restored.setHomePageCount(-1);
        QCOMPARE(restored.homePageCount(), 1);
        QVERIFY(restored.homePageApps(-1).isEmpty());
        QVERIFY(restored.homePageApps(1).isEmpty());
    }

    void onlyPinnedAppsCanMoveBetweenHomePages()
    {
        LauncherModel model;
        model.setHomePageCount(2);
        QVERIFY(!model.setFavoritePage(QStringLiteral("com.example.app"),
                                       QStringLiteral("com.example.app.Main"), 1));
        QVERIFY(!model.setFavoritePage(QStringLiteral("com.example.app"),
                                       QStringLiteral("com.example.app.Main"), -1));
    }

    void refreshingInstalledAppsUpdatesHomePageModels()
    {
        LauncherModel model;
        QSignalSpy homePagesChanged(&model, &LauncherModel::homePagesChanged);
        QVERIFY(homePagesChanged.isValid());
        model.refresh();
        QCOMPARE(homePagesChanged.count(), 1);
    }

private:
    QTemporaryDir m_settingsDirectory;
};

QTEST_GUILESS_MAIN(LauncherModelTest)
#include "tst_launchermodel.moc"
