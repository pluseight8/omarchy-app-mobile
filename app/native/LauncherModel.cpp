#include "LauncherModel.h"

#include <QDebug>
#include <QJsonArray>
#include <QJsonDocument>
#include <QJsonObject>
#include <QRegularExpression>
#include <QSettings>
#include <QVariant>
#include <algorithm>
#include <utility>

namespace {
QString favoriteKey(const QString &packageName, const QString &activityName)
{
    return packageName + QLatin1Char('/') + activityName;
}
} // namespace

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
    QSettings settings;
    m_favoritePackages = settings.value(QStringLiteral("launcher/favorites")).toStringList();
    qInfo().noquote() << QStringLiteral("OmaApp: loaded %1 pinned apps")
                             .arg(m_favoritePackages.size());
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
    case IconUrlRole:
        return launcherIconUrl(entry);
    case FavoriteRole:
        return m_favoritePackages.contains(favoriteKey(entry.packageName, entry.activityName));
    default:
        return {};
    }
}

QHash<int, QByteArray> LauncherModel::roleNames() const
{
    return {
        {NameRole, "name"},
        {PackageNameRole, "packageName"},
        {ActivityNameRole, "activityName"},
        {IconUrlRole, "iconUrl"},
        {FavoriteRole, "favorite"}
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

QVariantList LauncherModel::favorites() const
{
    QVariantList result;
    for (const QString &key : m_favoritePackages) {
        for (const Entry &entry : m_entries) {
            if (favoriteKey(entry.packageName, entry.activityName) != key)
                continue;
            result.append(QVariantMap{
                {QStringLiteral("name"), entry.name},
                {QStringLiteral("packageName"), entry.packageName},
                {QStringLiteral("activityName"), entry.activityName},
                {QStringLiteral("iconUrl"), launcherIconUrl(entry)},
            });
            break;
        }
    }
    return result;
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
    m_iconUrls.clear();
    rebuildVisibleRows();
    endResetModel();
#ifdef Q_OS_ANDROID
    qInfo().noquote() << QStringLiteral("OmaApp: enumerated %1 launcher activities")
                             .arg(m_entries.size());
#endif
    emit countChanged();
    emit favoritesChanged();
#else
    beginResetModel();
    m_entries.clear();
    m_iconUrls.clear();
    rebuildVisibleRows();
    endResetModel();
    emit countChanged();
    emit favoritesChanged();
#endif
}

void LauncherModel::launch(int row)
{
#ifdef Q_OS_ANDROID
    if (row < 0 || row >= m_visibleRows.size())
        return;

    const Entry &entry = m_entries.at(m_visibleRows.at(row));
    launchActivity(entry.packageName, entry.activityName);
#else
    Q_UNUSED(row)
#endif
}

bool LauncherModel::launchPackage(const QString &packageName)
{
    for (const Entry &entry : m_entries) {
        if (entry.packageName != packageName)
            continue;
        return launchActivity(entry.packageName, entry.activityName);
    }
    return false;
}

bool LauncherModel::launchActivity(const QString &packageName, const QString &activityName)
{
#ifdef Q_OS_ANDROID
    const QRegularExpression packagePattern(QStringLiteral("^[A-Za-z0-9_.]{1,200}$"));
    if (!packagePattern.match(packageName).hasMatch() || activityName.size() > 255
        || !activityName.startsWith(packageName)) {
        return false;
    }
    const QJniObject context = QNativeInterface::QAndroidApplication::context();
    const QJniObject packageObject = QJniObject::fromString(packageName);
    const QJniObject activityObject = QJniObject::fromString(activityName);
    return QJniObject::callStaticMethod<jboolean>(
        "app/omaapp/LauncherBridge",
        "launchApp",
        "(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;)Z",
        context.object<jobject>(),
        packageObject.object<jstring>(),
        activityObject.object<jstring>());
#else
    Q_UNUSED(packageName)
    Q_UNUSED(activityName)
    return false;
#endif
}

void LauncherModel::toggleFavorite(int row)
{
    if (row < 0 || row >= m_visibleRows.size())
        return;
    const Entry &entry = m_entries.at(m_visibleRows.at(row));
    const QString key = favoriteKey(entry.packageName, entry.activityName);
    if (m_favoritePackages.contains(key))
        m_favoritePackages.removeAll(key);
    else
        m_favoritePackages.append(key);
    saveFavorites();
    notifyFavoriteRowsChanged();
    qInfo().noquote() << QStringLiteral("OmaApp: pinned app count %1")
                             .arg(m_favoritePackages.size());
    emit favoritesChanged();
}

void LauncherModel::removeFavorite(const QString &packageName, const QString &activityName)
{
    const QRegularExpression packagePattern(QStringLiteral("^[A-Za-z0-9_.]{1,200}$"));
    if (!packagePattern.match(packageName).hasMatch() || activityName.size() > 255
        || !activityName.startsWith(packageName)) {
        return;
    }
    const QString key = favoriteKey(packageName, activityName);
    if (!m_favoritePackages.contains(key))
        return;
    m_favoritePackages.removeAll(key);
    saveFavorites();
    notifyFavoriteRowsChanged();
    emit favoritesChanged();
}

void LauncherModel::saveFavorites()
{
    QSettings settings;
    settings.setValue(QStringLiteral("launcher/favorites"), m_favoritePackages);
}

void LauncherModel::notifyFavoriteRowsChanged()
{
    for (int row = 0; row < m_visibleRows.size(); ++row)
        emit dataChanged(index(row, 0), index(row, 0), {FavoriteRole});
}

QString LauncherModel::launcherIconUrl(const Entry &entry) const
{
    const QString key = entry.packageName + QLatin1Char('\x1f') + entry.activityName;
    const auto found = m_iconUrls.constFind(key);
    if (found != m_iconUrls.cend())
        return found.value();

    QString result;
#ifdef Q_OS_ANDROID
    const QJniObject context = QNativeInterface::QAndroidApplication::context();
    const QJniObject packageName = QJniObject::fromString(entry.packageName);
    const QJniObject activityName = QJniObject::fromString(entry.activityName);
    const QJniObject icon = QJniObject::callStaticObjectMethod(
        "app/omaapp/LauncherBridge", "iconUrl",
        "(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
        context.object<jobject>(), packageName.object<jstring>(), activityName.object<jstring>());
    result = icon.toString();
#endif
    m_iconUrls.insert(key, result);
    if (!result.isEmpty())
        qInfo().noquote() << QStringLiteral("OmaApp: cached launcher icon for %1").arg(entry.name);
    return result;
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
