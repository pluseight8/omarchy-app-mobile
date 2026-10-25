#include "../OwlModel.h"

#include <QCoreApplication>
#include <QSignalSpy>
#include <QSettings>
#include <QTemporaryDir>
#include <QtTest>

class OwlModelTest : public QObject
{
    Q_OBJECT

private slots:
    void languageChangeRefreshesLocalizedStoreData()
    {
        OwlModel model;
        QSignalSpy changed(&model, &OwlModel::widgetsChanged);

        model.refreshLocalizedText();

        QCOMPARE(changed.size(), 1);
    }

    void initTestCase()
    {
        QSettings::setDefaultFormat(QSettings::IniFormat);
        QSettings::setPath(QSettings::IniFormat, QSettings::UserScope, m_settingsDirectory.path());
        QCoreApplication::setOrganizationName(QStringLiteral("OmaAppTests"));
        QCoreApplication::setApplicationName(QStringLiteral("OwlModelTests"));
    }

    void init()
    {
        QSettings settings;
        settings.clear();
        settings.sync();
    }

    void widgetStoreIsLocalAndBuiltinsInstallAndRemove()
    {
        OwlModel model;
        QCOMPARE(model.widgetStore().size(), 2);
        QVERIFY(model.installedWidgets().isEmpty());

        QVERIFY(model.setWidgetInstalled(QStringLiteral("clock"), true));
        QCOMPARE(model.installedWidgets().size(), 1);
        QCOMPARE(model.installedWidgets().first().toMap().value(QStringLiteral("id")).toString(),
                 QStringLiteral("clock"));
        QVERIFY(model.setWidgetInstalled(QStringLiteral("clock"), false));
        QVERIFY(model.installedWidgets().isEmpty());

        QVERIFY(!model.setWidgetInstalled(QStringLiteral("unknown"), true));
        QVERIFY(!model.removeWidget(QStringLiteral("clock")));
    }

    void customWidgetsCanBeEditedRemovedAndRestored()
    {
        QString widgetId;
        {
            OwlModel model;
            widgetId = model.createWidget(QStringLiteral(" Work\nstatus "));
            QVERIFY(widgetId.startsWith(QStringLiteral("user-")));
            QCOMPARE(model.installedWidgets().size(), 1);
            QVERIFY(model.source(widgetId).contains(QStringLiteral("Work status")));

            const QString source = QStringLiteral(
                "widget \"Work status\" { meta { owl: 1 } Text { text: \"Ready\" } }");
            QVERIFY(model.saveWidget(widgetId, source));
            QCOMPARE(model.source(widgetId), source);
            QVERIFY(!model.saveWidget(widgetId, QString(64 * 1024 + 1, QLatin1Char('x'))));
        }

        OwlModel restored;
        QCOMPARE(restored.installedWidgets().size(), 1);
        QCOMPARE(restored.source(widgetId), QStringLiteral(
            "widget \"Work status\" { meta { owl: 1 } Text { text: \"Ready\" } }"));
        QVERIFY(restored.removeWidget(widgetId));
        QVERIFY(restored.installedWidgets().isEmpty());
        QVERIFY(restored.source(widgetId).isEmpty());
    }

    void builtInCopiesAreEditableCustomWidgets()
    {
        OwlModel model;
        const QString copyId = model.copyWidgetForEditing(QStringLiteral("clock"));
        QVERIFY(copyId.startsWith(QStringLiteral("user-")));
        QVERIFY(!model.source(copyId).isEmpty());
        QVERIFY(model.widgetStore().last().toMap().value(QStringLiteral("editable")).toBool());
        QVERIFY(model.removeWidget(copyId));
    }

    void installedWidgetOrderPersistsAfterDragReorder()
    {
        OwlModel model;
        QVERIFY(model.setWidgetInstalled(QStringLiteral("clock"), true));
        QVERIFY(model.setWidgetInstalled(QStringLiteral("canvas"), true));
        const QString customId = model.createWidget(QStringLiteral("Third"));
        QCOMPARE(model.installedWidgets().first().toMap().value(QStringLiteral("id")).toString(),
                 QStringLiteral("clock"));

        QVERIFY(model.moveWidget(customId, 0));
        QVERIFY(!model.moveWidget(customId, 3));
        OwlModel restored;
        const QVariantList reordered = restored.installedWidgets();
        QCOMPARE(reordered.size(), 3);
        QCOMPARE(reordered.at(0).toMap().value(QStringLiteral("id")).toString(), customId);
        QCOMPARE(reordered.at(1).toMap().value(QStringLiteral("id")).toString(),
                 QStringLiteral("clock"));
        QCOMPARE(reordered.at(2).toMap().value(QStringLiteral("id")).toString(),
                 QStringLiteral("canvas"));
    }

    void widgetBackupRestoresOfflineLibraryAndOrder()
    {
        OwlModel source;
        QVERIFY(source.setWidgetInstalled(QStringLiteral("canvas"), true));
        const QString customId = source.createWidget(QStringLiteral("Offline notes"));
        const QString customSource = QStringLiteral(
            "widget \"Offline notes\" { meta { owl: 1 } Text { text: \"Saved locally\" } }");
        QVERIFY(source.saveWidget(customId, customSource));
        QVERIFY(source.setWidgetInstalled(customId, false));
        const QString backup = source.exportWidgetBackup();
        QVERIFY(source.canImportWidgetBackup(backup));

        OwlModel restored;
        QVERIFY(restored.importWidgetBackup(backup));
        QCOMPARE(restored.installedWidgets().size(), 1);
        QCOMPARE(restored.installedWidgets().first().toMap().value(QStringLiteral("id")).toString(),
                 QStringLiteral("canvas"));
        QCOMPARE(restored.source(customId), customSource);
        const QVariantMap customEntry = restored.widgetStore().last().toMap();
        QCOMPARE(customEntry.value(QStringLiteral("id")).toString(), customId);
        QVERIFY(!customEntry.value(QStringLiteral("installed")).toBool());

        const QString beforeInvalidImport = restored.exportWidgetBackup();
        QVERIFY(!restored.canImportWidgetBackup(QStringLiteral(
            "{\"schema\":1,\"installed\":[\"clock\"],\"sources\":{\"user-invalid\":\"x\"}}")));
        QVERIFY(!restored.importWidgetBackup(QStringLiteral("not-json")));
        QCOMPARE(restored.exportWidgetBackup(), beforeInvalidImport);
    }

private:
    QTemporaryDir m_settingsDirectory;
};

QTEST_GUILESS_MAIN(OwlModelTest)
#include "tst_owlmodel.moc"
