#pragma once

#include <QAbstractListModel>
#include <QHash>
#include <QList>
#include <QPointer>
#include <QString>
#include <QStringList>
#include <QVariantList>
#include <QVariantMap>

class LauncherModel : public QAbstractListModel
{
    Q_OBJECT
    Q_PROPERTY(int count READ count NOTIFY countChanged)
    Q_PROPERTY(bool platformSupported READ platformSupported CONSTANT)
    Q_PROPERTY(qreal systemFontScale READ systemFontScale NOTIFY systemFontScaleChanged)
    Q_PROPERTY(QString filter READ filter WRITE setFilter NOTIFY filterChanged)
    Q_PROPERTY(QVariantList favorites READ favorites NOTIFY favoritesChanged)
    Q_PROPERTY(QVariantList folders READ folders NOTIFY foldersChanged)
    Q_PROPERTY(int gridColumns READ gridColumns NOTIFY gridColumnsChanged)
    Q_PROPERTY(int homePageCount READ homePageCount NOTIFY homePagesChanged)
    Q_PROPERTY(QVariantMap homeWidgets READ homeWidgets NOTIFY homeWidgetsChanged)
    Q_PROPERTY(QVariantList androidWidgets READ androidWidgets NOTIFY androidWidgetsChanged)
    Q_PROPERTY(QVariantMap gestures READ gestures NOTIFY gesturesChanged)
    Q_PROPERTY(bool hapticFeedbackEnabled READ hapticFeedbackEnabled
               NOTIFY hapticFeedbackEnabledChanged)

public:
    enum Role {
        NameRole = Qt::UserRole + 1,
        PackageNameRole,
        ActivityNameRole,
        IconUrlRole,
        FavoriteRole
    };
    Q_ENUM(Role)

    explicit LauncherModel(QObject *parent = nullptr);
    ~LauncherModel() override;

    int rowCount(const QModelIndex &parent = QModelIndex()) const override;
    QVariant data(const QModelIndex &index, int role = Qt::DisplayRole) const override;
    QHash<int, QByteArray> roleNames() const override;

    int count() const;
    bool platformSupported() const;
    qreal systemFontScale() const;
    QString filter() const;
    QVariantList favorites() const;
    QVariantList folders() const;
    int gridColumns() const;
    int homePageCount() const;
    QVariantMap homeWidgets() const;
    QVariantList androidWidgets() const;
    QVariantMap gestures() const;
    bool hapticFeedbackEnabled() const;
    void setFilter(const QString &filter);

    Q_INVOKABLE void refresh();
    Q_INVOKABLE void refreshAndroidWidgets();
    Q_INVOKABLE bool addAndroidWidget();
    Q_INVOKABLE bool addAndroidWidgetOnPage(int page);
    Q_INVOKABLE bool removeAndroidWidget(int widgetId);
    Q_INVOKABLE bool removeAndroidWidgetRecord(const QString &recordId);
    Q_INVOKABLE bool rebindAndroidWidget(const QString &recordId);
    Q_INVOKABLE bool moveAndroidWidget(const QString &recordId, int page, int order);
    Q_INVOKABLE bool placeAndroidWidget(const QString &recordId, int page, int xDp, int yDp);
    Q_INVOKABLE bool resizeAndroidWidget(const QString &recordId, int widthDp, int heightDp);
    Q_INVOKABLE bool canRestoreAndroidWidgetBackup(const QString &json) const;
    Q_INVOKABLE bool prepareAndroidWidgetBackupRestore(const QString &json);
    Q_INVOKABLE bool commitAndroidWidgetBackupRestore();
    Q_INVOKABLE bool rollbackAndroidWidgetBackupRestore();
    Q_INVOKABLE bool recoverAndroidWidgetBackupRestore();
    Q_INVOKABLE bool restoreAndroidWidgetBackup(const QString &json);
    Q_INVOKABLE void setAndroidWidgetFrame(int widgetId, qreal x, qreal y,
                                           qreal width, qreal height, qreal clipX, qreal clipY,
                                           qreal clipWidth, qreal clipHeight, bool visible);
    Q_INVOKABLE void setGridColumns(int columns);
    Q_INVOKABLE void setHomePageCount(int count);
    Q_INVOKABLE QVariantList homePageApps(int page) const;
    Q_INVOKABLE bool setFavoritePage(const QString &packageName, const QString &activityName,
                                     int page);
    Q_INVOKABLE bool placeFavorite(const QString &packageName, const QString &activityName,
                                   int page, int index);
    Q_INVOKABLE void setHomeWidgetEnabled(const QString &widgetId, bool enabled);
    Q_INVOKABLE bool setGestureEnabled(const QString &gestureId, bool enabled);
    Q_INVOKABLE bool setHapticFeedbackEnabled(bool enabled);
    Q_INVOKABLE void performHapticFeedback();
    Q_INVOKABLE QString exportLayout() const;
    Q_INVOKABLE QString createLayoutBackup(const QString &owlWidgetBackup = QString()) const;
    Q_INVOKABLE bool importLayout(const QString &json);
    Q_INVOKABLE QString layoutRestoreJournalPhase() const;
    Q_INVOKABLE QString layoutRestoreJournalBackup() const;
    Q_INVOKABLE bool beginLayoutRestoreJournal(const QString &rollbackBackup);
    Q_INVOKABLE bool commitLayoutRestoreJournal();
    Q_INVOKABLE bool clearLayoutRestoreJournal();
    Q_INVOKABLE bool copyLayoutBackup(const QString &owlWidgetBackup = QString()) const;
    Q_INVOKABLE QString clipboardText() const;
    Q_INVOKABLE void launch(int row);
    Q_INVOKABLE bool launchPackage(const QString &packageName);
    Q_INVOKABLE bool launchActivity(const QString &packageName, const QString &activityName);
    Q_INVOKABLE void toggleFavorite(int row, int homePage);
    Q_INVOKABLE void removeFavorite(const QString &packageName, const QString &activityName);
    Q_INVOKABLE QVariantList availableApps(const QString &folderId, const QString &filter) const;
    Q_INVOKABLE QVariantList folderApps(const QString &folderId) const;
    Q_INVOKABLE QString createFolder(const QString &name);
    Q_INVOKABLE bool renameFolder(const QString &folderId, const QString &name);
    Q_INVOKABLE bool deleteFolder(const QString &folderId);
    Q_INVOKABLE bool setFolderContains(const QString &folderId, const QString &packageName,
                                       const QString &activityName, bool contains);

signals:
    void countChanged();
    void filterChanged();
    void favoritesChanged();
    void foldersChanged();
    void gridColumnsChanged();
    void homePagesChanged();
    void homeWidgetsChanged();
    void androidWidgetsChanged();
    void gesturesChanged();
    void hapticFeedbackEnabledChanged();
    void systemFontScaleChanged();
    void androidWidgetResizeFinished(const QString &recordId, int oldWidthDp, int oldHeightDp,
                                    int newWidthDp, int newHeightDp);

private:
    struct Entry {
        QString name;
        QString packageName;
        QString activityName;
    };

    struct Folder {
        QString id;
        QString name;
        QStringList activities;
    };

    QList<Entry> m_entries;
    QList<int> m_visibleRows;
    QString m_filter;
    QStringList m_favoritePackages;
    QVariantMap m_favoritePages;
    QList<Folder> m_folders;
    int m_gridColumns = 4;
    int m_homePageCount = 1;
    QVariantMap m_homeWidgets;
    QVariantList m_androidWidgets;
    QVariantMap m_gestures;
    bool m_hapticFeedbackEnabled = false;
    mutable QHash<QString, QString> m_iconUrls;
    bool m_platformSupported = false;
    qreal m_systemFontScale = 1.0;

    void rebuildVisibleRows();
    void saveFavorites();
    void saveFavoritePages();
    void loadFolders();
    void saveFolders();
    void notifyFavoriteRowsChanged();
    QString launcherIconUrl(const Entry &entry) const;
    void syncDynamicShortcuts() const;
};
