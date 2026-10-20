#include "LauncherModel.h"

#include <QJsonArray>
#include <QJsonDocument>
#include <QJsonObject>
#include <QRegularExpression>
#include <QVariant>
#include <algorithm>
#include <utility>

#ifdef Q_OS_ANDROID
#include <QJniObject>
#include <QtCore/qcoreapplication_platform.h>
#endif

LauncherModel::LauncherModel(QObject *parent)
    : QAbstractListModel(parent)
{
#ifdef Q_OS_ANDROID
    m_platformSupported = true;
#endif
}

int LauncherModel::rowCount(const QModelIndex &parent) const
{
    return parent.isValid() ? 0 : m_visibleRows.size();
}

QVariant LauncherModel::data(const QModelIndex &index, int role) const
{
    if (!index.isValid() || index.row() < 0 || index.row() >= m_visibleRows.size())
        return {};

    const Entry &entry = m_entries.at(m_visibleRows.at(index.row()));
    switch (role) {
    case Qt::DisplayRole:
    case NameRole:
        return entry.name;
    case PackageNameRole:
        return entry.packageName;
    case ActivityNameRole:
        return entry.activityName;
    default:
        return {};
    }
}

QHash<int, QByteArray> LauncherModel::roleNames() const
{
    return {
        {NameRole, "name"},
        {PackageNameRole, "packageName"},
        {ActivityNameRole, "activityName"}
    };
}

int LauncherModel::count() const
{
    return m_visibleRows.size();
}

bool LauncherModel::platformSupported() const
{
    return m_platformSupported;
}

QString LauncherModel::filter() const
{
    return m_filter;
}

void LauncherModel::setFilter(const QString &filter)
{
    const QString normalized = filter.left(160);
    if (m_filter == normalized)
        return;

    beginResetModel();
    m_filter = normalized;
    rebuildVisibleRows();
    endResetModel();
    emit filterChanged();
    emit countChanged();
}

void LauncherModel::refresh()
{
#ifdef Q_OS_ANDROID
    const QJniObject context = QNativeInterface::QAndroidApplication::context();
    const QJniObject json = QJniObject::callStaticObjectMethod(
        "app/omaapp/LauncherBridge",
        "listLauncherApps",
        "(Landroid/content/Context;)Ljava/lang/String;",
        context.object<jobject>());

    const QByteArray payload = json.toString().toUtf8();
    QJsonParseError parseError;
    const QJsonDocument document = QJsonDocument::fromJson(payload, &parseError);
    QList<Entry> nextEntries;

    if (parseError.error == QJsonParseError::NoError && document.isArray()) {
        const QRegularExpression packagePattern(QStringLiteral("^[A-Za-z0-9_.]{1,200}$"));
        for (const QJsonValue &value : document.array()) {
            if (!value.isObject())
                continue;

            const QJsonObject object = value.toObject();
            const QString name = object.value(QStringLiteral("name")).toString().trimmed();
            const QString packageName = object.value(QStringLiteral("packageName")).toString();
            const QString activityName = object.value(QStringLiteral("activityName")).toString();

            if (name.isEmpty() || name.size() > 160
                || !packagePattern.match(packageName).hasMatch()
                || activityName.isEmpty() || activityName.size() > 255
                || !activityName.startsWith(packageName)) {
                continue;
            }

            nextEntries.append({name, packageName, activityName});
        }
    }

    std::sort(nextEntries.begin(), nextEntries.end(), [](const Entry &left, const Entry &right) {
        const int order = QString::compare(left.name, right.name, Qt::CaseInsensitive);
        return order == 0 ? left.packageName < right.packageName : order < 0;
    });

    beginResetModel();
    m_entries = std::move(nextEntries);
    rebuildVisibleRows();
    endResetModel();
    emit countChanged();
#else
    beginResetModel();
    m_entries.clear();
    rebuildVisibleRows();
    endResetModel();
    emit countChanged();
#endif
}

void LauncherModel::launch(int row)
{
#ifdef Q_OS_ANDROID
    if (row < 0 || row >= m_visibleRows.size())
        return;

    const Entry &entry = m_entries.at(m_visibleRows.at(row));
    const QJniObject context = QNativeInterface::QAndroidApplication::context();
    const QJniObject packageName = QJniObject::fromString(entry.packageName);
    const QJniObject activityName = QJniObject::fromString(entry.activityName);
    QJniObject::callStaticMethod<jboolean>(
        "app/omaapp/LauncherBridge",
        "launchApp",
        "(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;)Z",
        context.object<jobject>(),
        packageName.object<jstring>(),
        activityName.object<jstring>());
#else
    Q_UNUSED(row)
#endif
}

void LauncherModel::rebuildVisibleRows()
{
    m_visibleRows.clear();
    const QString needle = m_filter.trimmed();
    for (qsizetype index = 0; index < m_entries.size(); ++index) {
        if (needle.isEmpty() || m_entries.at(index).name.contains(needle, Qt::CaseInsensitive))
            m_visibleRows.append(static_cast<int>(index));
    }
}
