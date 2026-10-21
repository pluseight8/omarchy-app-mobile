#include "LauncherModel.h"

#include <QDebug>
#include <QJsonArray>
#include <QJsonDocument>
#include <QJsonObject>
#include <QRegularExpression>
#include <QSettings>
#include <QUuid>
#include <QVariant>
#include <algorithm>
#include <utility>

namespace {
constexpr qsizetype maximumFolders = 24;
constexpr qsizetype maximumFolderItems = 128;

QString favoriteKey(const QString &packageName, const QString &activityName)
{
    return packageName + QLatin1Char('/') + activityName;
}

bool validLauncherTarget(const QString &packageName, const QString &activityName)
{
    static const QRegularExpression packagePattern(QStringLiteral("^[A-Za-z0-9_.]{1,200}$"));
    return packagePattern.match(packageName).hasMatch() && !activityName.isEmpty() &&
           activityName.size() <= 255 && activityName.startsWith(packageName);
}

bool validFolderName(const QString &name)
{
    return !name.isEmpty() && name.size() <= 40 &&
           std::all_of(name.cbegin(), name.cend(),
                       [](QChar character) { return character.isPrint(); });
}
} // namespace

#ifdef Q_OS_ANDROID
#include <QJniObject>
#include <QtCore/qcoreapplication_platform.h>
#endif

LauncherModel::LauncherModel(QObject *parent) : QAbstractListModel(parent)
{
#ifdef Q_OS_ANDROID
    m_platformSupported = true;
#endif
    QSettings settings;
    m_favoritePackages = settings.value(QStringLiteral("launcher/favorites")).toStringList();
    m_gridColumns = qBound(3, settings.value(QStringLiteral("launcher/gridColumns"), 4).toInt(), 5);
    loadFolders();
    qInfo().noquote()
        << QStringLiteral("OmaApp: loaded %1 pinned apps").arg(m_favoritePackages.size());
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
    return {{NameRole, "name"},
            {PackageNameRole, "packageName"},
            {ActivityNameRole, "activityName"},
            {IconUrlRole, "iconUrl"},
            {FavoriteRole, "favorite"}};
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

QVariantList LauncherModel::folders() const
{
    QVariantList result;
    result.reserve(m_folders.size());
    for (const Folder &folder : m_folders) {
        qsizetype installedCount = 0;
        for (const QString &key : folder.activities) {
            if (std::any_of(m_entries.cbegin(), m_entries.cend(), [&key](const Entry &entry) {
                    return favoriteKey(entry.packageName, entry.activityName) == key;
                })) {
                ++installedCount;
            }
        }
        result.append(QVariantMap{
            {QStringLiteral("id"), folder.id},
            {QStringLiteral("name"), folder.name},
            {QStringLiteral("appCount"), installedCount},
        });
    }
    return result;
}

int LauncherModel::gridColumns() const
{
    return m_gridColumns;
}

void LauncherModel::setGridColumns(int columns)
{
    const int boundedColumns = qBound(3, columns, 5);
    if (m_gridColumns == boundedColumns)
        return;
    m_gridColumns = boundedColumns;
    QSettings settings;
    settings.setValue(QStringLiteral("launcher/gridColumns"), m_gridColumns);
    settings.sync();
    if (settings.status() != QSettings::NoError)
        qWarning() << "OmaApp: failed to save launcher grid columns" << settings.status();
    emit gridColumnsChanged();
}

QVariantList LauncherModel::availableApps(const QString &folderId, const QString &filter) const
{
    const auto folder =
        std::find_if(m_folders.cbegin(), m_folders.cend(),
                     [&folderId](const Folder &item) { return item.id == folderId; });
    if (folder == m_folders.cend())
        return {};

    const QString needle = filter.trimmed().left(160);
    QVariantList result;
    result.reserve(m_entries.size());
    for (const Entry &entry : m_entries) {
        if (!needle.isEmpty() && !entry.name.contains(needle, Qt::CaseInsensitive))
            continue;
        result.append(QVariantMap{
            {QStringLiteral("name"), entry.name},
            {QStringLiteral("packageName"), entry.packageName},
            {QStringLiteral("activityName"), entry.activityName},
            {QStringLiteral("iconUrl"), launcherIconUrl(entry)},
            {QStringLiteral("inFolder"),
             folder->activities.contains(favoriteKey(entry.packageName, entry.activityName))},
        });
    }
    return result;
}

QVariantList LauncherModel::folderApps(const QString &folderId) const
{
    const auto folder =
        std::find_if(m_folders.cbegin(), m_folders.cend(),
                     [&folderId](const Folder &item) { return item.id == folderId; });
    if (folder == m_folders.cend())
        return {};

    QVariantList result;
    result.reserve(folder->activities.size());
    for (const QString &key : folder->activities) {
        const auto entry =
            std::find_if(m_entries.cbegin(), m_entries.cend(), [&key](const Entry &item) {
                return favoriteKey(item.packageName, item.activityName) == key;
            });
        if (entry == m_entries.cend())
            continue;
        result.append(QVariantMap{
            {QStringLiteral("name"), entry->name},
            {QStringLiteral("packageName"), entry->packageName},
            {QStringLiteral("activityName"), entry->activityName},
            {QStringLiteral("iconUrl"), launcherIconUrl(*entry)},
        });
    }
    return result;
}

QString LauncherModel::createFolder(const QString &name)
{
    const QString normalized = name.trimmed();
    if (!validFolderName(normalized) || m_folders.size() >= maximumFolders)
        return {};
    if (std::any_of(m_folders.cbegin(), m_folders.cend(), [&normalized](const Folder &folder) {
            return folder.name.compare(normalized, Qt::CaseInsensitive) == 0;
        })) {
        return {};
    }

    const QString id = QUuid::createUuid().toString(QUuid::WithoutBraces);
    m_folders.append({id, normalized, {}});
    saveFolders();
    emit foldersChanged();
    return id;
}

bool LauncherModel::renameFolder(const QString &folderId, const QString &name)
{
    const QString normalized = name.trimmed();
    if (!validFolderName(normalized))
        return false;
    for (qsizetype index = 0; index < m_folders.size(); ++index) {
        Folder &folder = m_folders[index];
        if (folder.id != folderId)
            continue;
        if (folder.name == normalized)
            return true;
        if (std::any_of(m_folders.cbegin(), m_folders.cend(),
                        [&normalized, &folderId](const Folder &other) {
                            return other.id != folderId &&
                                   other.name.compare(normalized, Qt::CaseInsensitive) == 0;
                        })) {
            return false;
        }
        folder.name = normalized;
        saveFolders();
        emit foldersChanged();
        return true;
    }
    return false;
}

bool LauncherModel::deleteFolder(const QString &folderId)
{
    const auto folder =
        std::find_if(m_folders.begin(), m_folders.end(),
                     [&folderId](const Folder &item) { return item.id == folderId; });
    if (folder == m_folders.end())
        return false;
    m_folders.erase(folder);
    saveFolders();
    emit foldersChanged();
    return true;
}

bool LauncherModel::setFolderContains(const QString &folderId, const QString &packageName,
                                      const QString &activityName, bool contains)
{
    if (!validLauncherTarget(packageName, activityName))
        return false;

    auto folder = std::find_if(m_folders.begin(), m_folders.end(),
                               [&folderId](const Folder &item) { return item.id == folderId; });
    if (folder == m_folders.end())
        return false;

    const QString key = favoriteKey(packageName, activityName);
    const bool alreadyContains = folder->activities.contains(key);
    if (alreadyContains == contains)
        return true;
    if (contains) {
        const bool installed = std::any_of(m_entries.cbegin(), m_entries.cend(),
                                           [&packageName, &activityName](const Entry &entry) {
                                               return entry.packageName == packageName &&
                                                      entry.activityName == activityName;
                                           });
        if (!installed || folder->activities.size() >= maximumFolderItems)
            return false;
        folder->activities.append(key);
    } else {
        folder->activities.removeAll(key);
    }
    saveFolders();
    emit foldersChanged();
    return true;
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
        "app/omaapp/LauncherBridge", "listLauncherApps",
        "(Landroid/content/Context;)Ljava/lang/String;", context.object<jobject>());

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

            if (name.isEmpty() || name.size() > 160 ||
                !packagePattern.match(packageName).hasMatch() || activityName.isEmpty() ||
                activityName.size() > 255 || !activityName.startsWith(packageName)) {
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
    qInfo().noquote()
        << QStringLiteral("OmaApp: enumerated %1 launcher activities").arg(m_entries.size());
#endif
    emit countChanged();
    emit favoritesChanged();
    emit foldersChanged();
#else
    beginResetModel();
    m_entries.clear();
    m_iconUrls.clear();
    rebuildVisibleRows();
    endResetModel();
    emit countChanged();
    emit favoritesChanged();
    emit foldersChanged();
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
    if (!packagePattern.match(packageName).hasMatch() || activityName.size() > 255 ||
        !activityName.startsWith(packageName)) {
        return false;
    }
    const QJniObject context = QNativeInterface::QAndroidApplication::context();
    const QJniObject packageObject = QJniObject::fromString(packageName);
    const QJniObject activityObject = QJniObject::fromString(activityName);
    const bool started = QJniObject::callStaticMethod<jboolean>(
        "app/omaapp/LauncherBridge", "launchApp",
        "(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;)Z",
        context.object<jobject>(), packageObject.object<jstring>(),
        activityObject.object<jstring>());
    qInfo().noquote() << QStringLiteral("OmaApp: launch activity %1 %2")
                             .arg(started ? QStringLiteral("started") : QStringLiteral("rejected"))
                             .arg(favoriteKey(packageName, activityName));
    return started;
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
    qInfo().noquote()
        << QStringLiteral("OmaApp: pinned app count %1").arg(m_favoritePackages.size());
    emit favoritesChanged();
}

void LauncherModel::removeFavorite(const QString &packageName, const QString &activityName)
{
    const QRegularExpression packagePattern(QStringLiteral("^[A-Za-z0-9_.]{1,200}$"));
    if (!packagePattern.match(packageName).hasMatch() || activityName.size() > 255 ||
        !activityName.startsWith(packageName)) {
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
    settings.sync();
    if (settings.status() != QSettings::NoError)
        qWarning() << "OmaApp: failed to save pinned apps" << settings.status();
}

void LauncherModel::loadFolders()
{
    QSettings settings;
    const QByteArray payload = settings.value(QStringLiteral("launcher/folders")).toByteArray();
    if (payload.isEmpty())
        return;

    QJsonParseError error;
    const QJsonDocument document = QJsonDocument::fromJson(payload, &error);
    if (error.error != QJsonParseError::NoError || !document.isArray()) {
        qWarning().noquote() << QStringLiteral("OmaApp: ignoring invalid saved folders: %1")
                                    .arg(error.errorString());
        return;
    }

    for (const QJsonValue &value : document.array()) {
        if (!value.isObject() || m_folders.size() >= maximumFolders)
            continue;
        const QJsonObject object = value.toObject();
        const QString rawId = object.value(QStringLiteral("id")).toString();
        const QUuid uuid = QUuid::fromString(rawId);
        const QString id = uuid.toString(QUuid::WithoutBraces);
        const QString name = object.value(QStringLiteral("name")).toString().trimmed();
        const QJsonValue appsValue = object.value(QStringLiteral("apps"));
        if (uuid.isNull() || id.compare(rawId, Qt::CaseInsensitive) != 0 ||
            !validFolderName(name) || !appsValue.isArray()) {
            continue;
        }
        if (std::any_of(m_folders.cbegin(), m_folders.cend(), [&id, &name](const Folder &folder) {
                return folder.id == id || folder.name.compare(name, Qt::CaseInsensitive) == 0;
            })) {
            continue;
        }

        Folder folder{id, name, {}};
        const QJsonArray apps = appsValue.toArray();
        const qsizetype appLimit = std::min<qsizetype>(apps.size(), maximumFolderItems);
        for (qsizetype index = 0; index < appLimit; ++index) {
            if (!apps.at(index).isObject())
                continue;
            const QJsonObject app = apps.at(index).toObject();
            const QString packageName = app.value(QStringLiteral("packageName")).toString();
            const QString activityName = app.value(QStringLiteral("activityName")).toString();
            const QString key = favoriteKey(packageName, activityName);
            if (validLauncherTarget(packageName, activityName) &&
                !folder.activities.contains(key)) {
                folder.activities.append(key);
            }
        }
        m_folders.append(std::move(folder));
    }
}

void LauncherModel::saveFolders()
{
    QJsonArray array;
    for (const Folder &folder : m_folders) {
        QJsonArray apps;
        for (const QString &key : folder.activities) {
            const qsizetype separator = key.indexOf(QLatin1Char('/'));
            if (separator <= 0 || separator == key.size() - 1)
                continue;
            apps.append(QJsonObject{
                {QStringLiteral("packageName"), key.left(separator)},
                {QStringLiteral("activityName"), key.mid(separator + 1)},
            });
        }
        array.append(QJsonObject{
            {QStringLiteral("id"), folder.id},
            {QStringLiteral("name"), folder.name},
            {QStringLiteral("apps"), apps},
        });
    }

    QSettings settings;
    settings.setValue(QStringLiteral("launcher/folders"),
                      QJsonDocument(array).toJson(QJsonDocument::Compact));
    settings.sync();
    if (settings.status() != QSettings::NoError)
        qWarning() << "OmaApp: failed to save folders" << settings.status();
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
        "(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;)Ljava/"
        "lang/String;",
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
