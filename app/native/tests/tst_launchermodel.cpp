#include "../LauncherModel.h"

#include <QCoreApplication>
#include <QJsonArray>
#include <QJsonDocument>
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

    void gesturePreferencesPersistAndRejectUnknownActions()
    {
        {
            LauncherModel model;
            QCOMPARE(model.gestures().value(QStringLiteral("homeSwipe")).toBool(), true);
            QVERIFY(model.setGestureEnabled(QStringLiteral("homeSwipe"), false));
            QVERIFY(model.setGestureEnabled(QStringLiteral("longPressPin"), false));
            QVERIFY(!model.setGestureEnabled(QStringLiteral("executeCommand"), true));
        }

        LauncherModel restored;
        QCOMPARE(restored.gestures().value(QStringLiteral("homeSwipe")).toBool(), false);
        QCOMPARE(restored.gestures().value(QStringLiteral("longPressPin")).toBool(), false);
        QCOMPARE(restored.gestures().value(QStringLiteral("longPressEdit")).toBool(), true);
    }

    void layoutBackupRoundTripsOfflinePreferencesAndValidatesBeforeApplying()
    {
        LauncherModel model;
        const QByteArray source = R"json({
            "schema": 1,
            "gridColumns": 5,
            "homePageCount": 2,
            "favorites": [{
                "packageName": "com.example.notes",
                "activityName": "com.example.notes.MainActivity",
                "page": 1
            }],
            "folders": [{
                "name": "Work",
                "apps": [{
                    "packageName": "com.example.notes",
                    "activityName": "com.example.notes.MainActivity"
                }]
            }],
            "homeWidgets": {"clock": true, "computer": false, "media": true},
            "gestures": {"homeSwipe": false, "sectionSwipe": true,
                          "longPressPin": false, "longPressEdit": true}
        })json";
        QVERIFY(model.importLayout(QString::fromUtf8(source)));
        QCOMPARE(model.gridColumns(), 5);
        QCOMPARE(model.homePageCount(), 2);
        QCOMPARE(model.homeWidgets().value(QStringLiteral("computer")).toBool(), false);
        QCOMPARE(model.gestures().value(QStringLiteral("homeSwipe")).toBool(), false);
        QCOMPARE(model.gestures().value(QStringLiteral("longPressPin")).toBool(), false);
        QCOMPARE(model.folders().size(), 1);
        QCOMPARE(model.folders().first().toMap().value(QStringLiteral("name")).toString(),
                 QStringLiteral("Work"));

        const QString backup = model.exportLayout();
        QVERIFY(backup.size() > 0);
        model.setGridColumns(3);
        model.setHomePageCount(1);
        QVERIFY(model.setGestureEnabled(QStringLiteral("homeSwipe"), true));
        QVERIFY(model.importLayout(backup));
        QCOMPARE(model.gridColumns(), 5);
        QCOMPARE(model.homePageCount(), 2);
        QCOMPARE(model.gestures().value(QStringLiteral("homeSwipe")).toBool(), false);
        QCOMPARE(model.folders().size(), 1);

        const QString unchangedBackup = model.exportLayout();
        QVERIFY(!model.importLayout(QStringLiteral("not-json")));
        QVERIFY(!model.importLayout(QStringLiteral("{\"schema\":2}")));
        QVERIFY(!model.importLayout(QStringLiteral(
            "{\"schema\":1,\"gridColumns\":99,\"homePageCount\":1,"
            "\"favorites\":[],\"folders\":[],\"homeWidgets\":{},\"gestures\":{}}")));
        QCOMPARE(model.exportLayout(), unchangedBackup);
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

    void layoutFavoritesCanBeReorderedAndMovedAcrossPages()
    {
        LauncherModel model;
        model.setHomePageCount(2);
        const QByteArray source = R"json({
            "schema": 1,
            "gridColumns": 4,
            "homePageCount": 2,
            "favorites": [
                {"packageName":"com.example.first","activityName":"com.example.first.Main","page":0},
                {"packageName":"com.example.second","activityName":"com.example.second.Main","page":0},
                {"packageName":"com.example.third","activityName":"com.example.third.Main","page":1}
            ],
            "folders": [],
            "homeWidgets": {"clock":true,"computer":true,"media":true},
            "gestures": {"homeSwipe":true,"sectionSwipe":true,"longPressPin":true,"longPressEdit":true}
        })json";
        QVERIFY(model.importLayout(QString::fromUtf8(source)));

        QVERIFY(model.placeFavorite(QStringLiteral("com.example.second"),
                                    QStringLiteral("com.example.second.Main"), 0, 0));
        QJsonArray favorites = QJsonDocument::fromJson(model.exportLayout().toUtf8())
                                   .object().value(QStringLiteral("favorites")).toArray();
        QCOMPARE(favorites.first().toObject().value(QStringLiteral("packageName")).toString(),
                 QStringLiteral("com.example.second"));
        QVERIFY(model.placeFavorite(QStringLiteral("com.example.first"),
                                    QStringLiteral("com.example.first.Main"), 1, 0));
        favorites = QJsonDocument::fromJson(model.exportLayout().toUtf8())
                        .object().value(QStringLiteral("favorites")).toArray();
        QCOMPARE(favorites.at(1).toObject().value(QStringLiteral("packageName")).toString(),
                 QStringLiteral("com.example.first"));
        QCOMPARE(favorites.at(1).toObject().value(QStringLiteral("page")).toInt(), 1);
        QVERIFY(!model.placeFavorite(QStringLiteral("com.unknown.app"),
                                     QStringLiteral("com.unknown.app.Main"), 0, 0));
        QVERIFY(!model.placeFavorite(QStringLiteral("com.example.first"),
                                     QStringLiteral("com.example.first.Main"), 2, 0));
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
