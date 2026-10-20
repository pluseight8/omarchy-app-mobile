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
    void setFilter(const QString &filter);

    Q_INVOKABLE void refresh();
    Q_INVOKABLE void launch(int row);
    Q_INVOKABLE bool launchPackage(const QString &packageName);
    Q_INVOKABLE bool launchActivity(const QString &packageName, const QString &activityName);
    Q_INVOKABLE void toggleFavorite(int row);
    Q_INVOKABLE void removeFavorite(const QString &packageName, const QString &activityName);

signals:
    void countChanged();
    void filterChanged();
    void favoritesChanged();

private:
    struct Entry {
        QString name;
        QString packageName;
        QString activityName;
    };

    QList<Entry> m_entries;
    QList<int> m_visibleRows;
    QString m_filter;
    QStringList m_favoritePackages;
    mutable QHash<QString, QString> m_iconUrls;
    bool m_platformSupported = false;

    void rebuildVisibleRows();
    void saveFavorites();
    void notifyFavoriteRowsChanged();
    QString launcherIconUrl(const Entry &entry) const;
};
