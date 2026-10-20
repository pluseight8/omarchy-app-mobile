#pragma once

#include <QAbstractListModel>
#include <QList>
#include <QString>

class LauncherModel final : public QAbstractListModel
{
    Q_OBJECT
    Q_PROPERTY(int count READ count NOTIFY countChanged)
    Q_PROPERTY(bool platformSupported READ platformSupported CONSTANT)
    Q_PROPERTY(QString filter READ filter WRITE setFilter NOTIFY filterChanged)

public:
    enum Role {
        NameRole = Qt::UserRole + 1,
        PackageNameRole,
        ActivityNameRole
    };
    Q_ENUM(Role)

    explicit LauncherModel(QObject *parent = nullptr);

    int rowCount(const QModelIndex &parent = QModelIndex()) const override;
    QVariant data(const QModelIndex &index, int role = Qt::DisplayRole) const override;
    QHash<int, QByteArray> roleNames() const override;

    int count() const;
    bool platformSupported() const;
    QString filter() const;
    void setFilter(const QString &filter);

    Q_INVOKABLE void refresh();
    Q_INVOKABLE void launch(int row);

signals:
    void countChanged();
    void filterChanged();

private:
    struct Entry {
        QString name;
        QString packageName;
        QString activityName;
    };

    QList<Entry> m_entries;
    QList<int> m_visibleRows;
    QString m_filter;
    bool m_platformSupported = false;

    void rebuildVisibleRows();
};
