#pragma once

#include <QAbstractListModel>
#include <QHash>
#include <QList>
#include <QString>
#include <QStringList>
#include <QVariantList>

class LauncherModel : public QAbstractListModel
{
    Q_OBJECT
    Q_PROPERTY(int count READ count NOTIFY countChanged)
    Q_PROPERTY(bool platformSupported READ platformSupported CONSTANT)
    Q_PROPERTY(QString filter READ filter WRITE setFilter NOTIFY filterChanged)
    Q_PROPERTY(QVariantList favorites READ favorites NOTIFY favoritesChanged)
    Q_PROPERTY(QVariantList folders READ folders NOTIFY foldersChanged)

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

    int rowCount(const QModelIndex &parent = QModelIndex()) const override;
    QVariant data(const QModelIndex &index, int role = Qt::DisplayRole) const override;
    QHash<int, QByteArray> roleNames() const override;

    int count() const;
    bool platformSupported() const;
    QString filter() const;
    QVariantList favorites() const;
    QVariantList folders() const;
    void setFilter(const QString &filter);

    Q_INVOKABLE void refresh();
    Q_INVOKABLE void launch(int row);
    Q_INVOKABLE bool launchPackage(const QString &packageName);
    Q_INVOKABLE bool launchActivity(const QString &packageName, const QString &activityName);
    Q_INVOKABLE void toggleFavorite(int row);
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
    QList<Folder> m_folders;
    mutable QHash<QString, QString> m_iconUrls;
    bool m_platformSupported = false;

    void rebuildVisibleRows();
    void saveFavorites();
    void loadFolders();
    void saveFolders();
    void notifyFavoriteRowsChanged();
    QString launcherIconUrl(const Entry &entry) const;
};
