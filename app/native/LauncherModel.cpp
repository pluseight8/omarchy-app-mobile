#include "LauncherModel.h"

#include <QDebug>
#include <QGuiApplication>
#include <QClipboard>
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
constexpr int maximumHomePages = 5;
constexpr qsizetype maximumBackupBytes = 2 * 1024 * 1024;
constexpr qsizetype maximumFavoriteApps = 512;
const QStringList homeWidgetIds{QStringLiteral("clock"), QStringLiteral("computer"),
                                QStringLiteral("media")};
const QStringList gestureIds{QStringLiteral("homeSwipe"), QStringLiteral("sectionSwipe"),
                             QStringLiteral("longPressPin"), QStringLiteral("longPressEdit")};

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
#if defined(OMAAPP_TEST_RHI_BACKEND_OPENGL) || defined(OMAAPP_TEST_RHI_BACKEND_VULKAN)
    qWarning().noquote() << "OmaApp startup: LauncherModel construction entered";
#endif
#ifdef Q_OS_ANDROID
    m_platformSupported = true;
#endif
    QSettings settings;
    m_favoritePackages = settings.value(QStringLiteral("launcher/favorites")).toStringList();
    m_gridColumns = qBound(3, settings.value(QStringLiteral("launcher/gridColumns"), 4).toInt(), 5);
    m_homePageCount = qBound(1, settings.value(QStringLiteral("launcher/homePageCount"), 1).toInt(),
                             maximumHomePages);
    m_favoritePages = settings.value(QStringLiteral("launcher/favoritePages")).toMap();
    for (auto it = m_favoritePages.begin(); it != m_favoritePages.end();) {
        const int page = it.value().toInt();
        if (!m_favoritePackages.contains(it.key()) || page < 0 || page >= m_homePageCount)
            it = m_favoritePages.erase(it);
        else
            ++it;
    }
    m_homeWidgets = {{QStringLiteral("clock"), true}, {QStringLiteral("computer"), true},
                     {QStringLiteral("media"), true}};
    const QVariantMap savedWidgets = settings.value(QStringLiteral("launcher/homeWidgets")).toMap();
    for (const QString &widgetId : homeWidgetIds) {
        const auto saved = savedWidgets.constFind(widgetId);
        if (saved != savedWidgets.cend() && saved.value().metaType().id() == QMetaType::Bool)
            m_homeWidgets.insert(widgetId, saved.value().toBool());
    }
    m_gestures = {{QStringLiteral("homeSwipe"), true}, {QStringLiteral("sectionSwipe"), true},
                  {QStringLiteral("longPressPin"), true}, {QStringLiteral("longPressEdit"), true}};
    const QVariantMap savedGestures = settings.value(QStringLiteral("launcher/gestures")).toMap();
    for (const QString &gestureId : gestureIds) {
        const auto saved = savedGestures.constFind(gestureId);
        if (saved != savedGestures.cend() && saved.value().metaType().id() == QMetaType::Bool)
            m_gestures.insert(gestureId, saved.value().toBool());
    }
    m_hapticFeedbackEnabled = settings.value(QStringLiteral("launcher/hapticFeedbackEnabled"),
                                             false).toBool();
    loadFolders();
    qInfo().noquote()
        << QStringLiteral("OmaApp: loaded %1 pinned apps").arg(m_favoritePackages.size());
#if defined(OMAAPP_TEST_RHI_BACKEND_OPENGL) || defined(OMAAPP_TEST_RHI_BACKEND_VULKAN)
    qWarning().noquote() << "OmaApp startup: LauncherModel construction returned";
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

int LauncherModel::homePageCount() const
{
    return m_homePageCount;
}

QVariantMap LauncherModel::homeWidgets() const
{
    return m_homeWidgets;
}

QVariantMap LauncherModel::gestures() const
{
    return m_gestures;
}

bool LauncherModel::hapticFeedbackEnabled() const
{
    return m_hapticFeedbackEnabled;
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

void LauncherModel::setHomePageCount(int count)
{
    const int boundedCount = qBound(1, count, maximumHomePages);
    if (m_homePageCount == boundedCount)
        return;
    m_homePageCount = boundedCount;
    for (auto it = m_favoritePages.begin(); it != m_favoritePages.end(); ++it)
        it.value() = qBound(0, it.value().toInt(), m_homePageCount - 1);

    QSettings settings;
    settings.setValue(QStringLiteral("launcher/homePageCount"), m_homePageCount);
    settings.sync();
    if (settings.status() != QSettings::NoError)
        qWarning() << "OmaApp: failed to save home page count" << settings.status();
    saveFavoritePages();
    emit homePagesChanged();
}

QVariantList LauncherModel::homePageApps(int page) const
{
    if (page < 0 || page >= m_homePageCount)
        return {};
    QVariantList result;
    for (const QString &key : m_favoritePackages) {
        if (m_favoritePages.value(key, 0).toInt() != page)
            continue;
        const auto entry = std::find_if(m_entries.cbegin(), m_entries.cend(), [&key](const Entry &item) {
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

bool LauncherModel::setFavoritePage(const QString &packageName, const QString &activityName, int page)
{
    if (page < 0 || page >= m_homePageCount || !validLauncherTarget(packageName, activityName))
        return false;
    const QString key = favoriteKey(packageName, activityName);
    if (!m_favoritePackages.contains(key))
        return false;
    if (m_favoritePages.value(key, 0).toInt() == page)
        return true;
    m_favoritePages.insert(key, page);
    saveFavoritePages();
    emit homePagesChanged();
    return true;
}

bool LauncherModel::placeFavorite(const QString &packageName, const QString &activityName,
                                  int page, int targetIndex)
{
    if (page < 0 || page >= m_homePageCount || !validLauncherTarget(packageName, activityName))
        return false;

    const QString key = favoriteKey(packageName, activityName);
    const bool alreadyPinned = m_favoritePackages.contains(key);
    const bool installed = std::any_of(m_entries.cbegin(), m_entries.cend(),
                                       [&packageName, &activityName](const Entry &entry) {
                                           return entry.packageName == packageName &&
                                                  entry.activityName == activityName;
                                       });
    if ((!alreadyPinned && !installed) ||
        (!alreadyPinned && m_favoritePackages.size() >= maximumFavoriteApps)) {
        return false;
    }

    QStringList pageKeys;
    pageKeys.reserve(m_favoritePackages.size());
    for (const QString &favorite : m_favoritePackages) {
        if (favorite != key && m_favoritePages.value(favorite, 0).toInt() == page)
            pageKeys.append(favorite);
    }
    const int pageIndex = qBound(0, targetIndex, pageKeys.size());

    m_favoritePackages.removeAll(key);
    int insertionIndex = m_favoritePackages.size();
    if (pageIndex < pageKeys.size()) {
        insertionIndex = m_favoritePackages.indexOf(pageKeys.at(pageIndex));
    } else if (!pageKeys.isEmpty()) {
        insertionIndex = m_favoritePackages.indexOf(pageKeys.constLast()) + 1;
    }
    m_favoritePackages.insert(qBound(0, insertionIndex, m_favoritePackages.size()), key);
    m_favoritePages.insert(key, page);
    saveFavorites();
    saveFavoritePages();
    notifyFavoriteRowsChanged();
    emit favoritesChanged();
    emit homePagesChanged();
#ifdef Q_OS_ANDROID
    syncDynamicShortcuts();
#endif
    return true;
}

void LauncherModel::setHomeWidgetEnabled(const QString &widgetId, bool enabled)
{
    if (!homeWidgetIds.contains(widgetId) || m_homeWidgets.value(widgetId).toBool() == enabled)
        return;
    m_homeWidgets.insert(widgetId, enabled);
    QSettings settings;
    settings.setValue(QStringLiteral("launcher/homeWidgets"), m_homeWidgets);
    settings.sync();
    if (settings.status() != QSettings::NoError)
        qWarning() << "OmaApp: failed to save home widgets" << settings.status();
    emit homeWidgetsChanged();
}

bool LauncherModel::setGestureEnabled(const QString &gestureId, bool enabled)
{
    if (!gestureIds.contains(gestureId) || m_gestures.value(gestureId).toBool() == enabled)
        return gestureIds.contains(gestureId);
    QVariantMap updated = m_gestures;
    updated.insert(gestureId, enabled);
    QSettings settings;
    settings.setValue(QStringLiteral("launcher/gestures"), updated);
    settings.sync();
    if (settings.status() != QSettings::NoError) {
        qWarning() << "OmaApp: failed to save launcher gestures" << settings.status();
        return false;
    }
    m_gestures = std::move(updated);
    emit gesturesChanged();
    return true;
}

bool LauncherModel::setHapticFeedbackEnabled(bool enabled)
{
    if (m_hapticFeedbackEnabled == enabled)
        return true;
    QSettings settings;
    settings.setValue(QStringLiteral("launcher/hapticFeedbackEnabled"), enabled);
    settings.sync();
    if (settings.status() != QSettings::NoError) {
        qWarning() << "OmaApp: failed to save haptic feedback preference" << settings.status();
        return false;
    }
    m_hapticFeedbackEnabled = enabled;
    emit hapticFeedbackEnabledChanged();
    return true;
}

void LauncherModel::performHapticFeedback()
{
    if (!m_hapticFeedbackEnabled)
        return;
#ifdef Q_OS_ANDROID
    if (!QNativeInterface::QAndroidApplication::isActivityContext())
        return;
    QNativeInterface::QAndroidApplication::runOnAndroidMainThread([] {
        const QJniObject activity = QNativeInterface::QAndroidApplication::context();
        if (!activity.isValid())
            return QVariant();
        const QJniObject window = activity.callObjectMethod("getWindow",
                                                            "()Landroid/view/Window;");
        if (!window.isValid())
            return QVariant();
        const QJniObject decorView = window.callObjectMethod("getDecorView",
                                                             "()Landroid/view/View;");
        if (!decorView.isValid())
            return QVariant();
        const jint keyboardTap = QJniObject::getStaticField<jint>(
            "android/view/HapticFeedbackConstants", "KEYBOARD_TAP");
        decorView.callMethod<jboolean>("performHapticFeedback", "(I)Z", keyboardTap);
        qInfo() << "OmaApp: Android haptic feedback request dispatched";
        return QVariant();
    });
#endif
}

QString LauncherModel::exportLayout() const
{
    QJsonArray favorites;
    for (const QString &key : m_favoritePackages) {
        const qsizetype separator = key.indexOf(QLatin1Char('/'));
        if (separator <= 0 || separator == key.size() - 1)
            continue;
        favorites.append(QJsonObject{
            {QStringLiteral("packageName"), key.left(separator)},
            {QStringLiteral("activityName"), key.mid(separator + 1)},
            {QStringLiteral("page"), m_favoritePages.value(key, 0).toInt()},
        });
    }

    QJsonArray folders;
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
        folders.append(QJsonObject{
            {QStringLiteral("name"), folder.name},
            {QStringLiteral("apps"), apps},
        });
    }

    const QJsonObject payload{
        {QStringLiteral("schema"), 1},
        {QStringLiteral("gridColumns"), m_gridColumns},
        {QStringLiteral("homePageCount"), m_homePageCount},
        {QStringLiteral("favorites"), favorites},
        {QStringLiteral("folders"), folders},
        {QStringLiteral("homeWidgets"), QJsonObject::fromVariantMap(m_homeWidgets)},
        {QStringLiteral("gestures"), QJsonObject::fromVariantMap(m_gestures)},
        {QStringLiteral("hapticFeedbackEnabled"), m_hapticFeedbackEnabled},
    };
    return QString::fromUtf8(QJsonDocument(payload).toJson(QJsonDocument::Compact));
}

bool LauncherModel::importLayout(const QString &json)
{
    const QByteArray payload = json.toUtf8();
    if (payload.isEmpty() || payload.size() > maximumBackupBytes)
        return false;

    QJsonParseError error;
    const QJsonDocument document = QJsonDocument::fromJson(payload, &error);
    if (error.error != QJsonParseError::NoError || !document.isObject())
        return false;
    const QJsonObject object = document.object();
    const QJsonValue schema = object.value(QStringLiteral("schema"));
    const QJsonValue gridValue = object.value(QStringLiteral("gridColumns"));
    const QJsonValue pagesValue = object.value(QStringLiteral("homePageCount"));
    const QJsonValue favoritesValue = object.value(QStringLiteral("favorites"));
    const QJsonValue foldersValue = object.value(QStringLiteral("folders"));
    const QJsonValue widgetsValue = object.value(QStringLiteral("homeWidgets"));
    const QJsonValue gesturesValue = object.value(QStringLiteral("gestures"));
    const QJsonValue hapticFeedbackValue = object.value(QStringLiteral("hapticFeedbackEnabled"));
    if (!schema.isDouble() || schema.toInt(-1) != 1 || !gridValue.isDouble() ||
        !pagesValue.isDouble() || !favoritesValue.isArray() || !foldersValue.isArray() ||
        !widgetsValue.isObject() || !gesturesValue.isObject() ||
        (!hapticFeedbackValue.isUndefined() && !hapticFeedbackValue.isBool())) {
        return false;
    }

    const int grid = gridValue.toInt(-1);
    const int pageCount = pagesValue.toInt(-1);
    const QJsonArray favoriteArray = favoritesValue.toArray();
    const QJsonArray folderArray = foldersValue.toArray();
    if (grid < 3 || grid > 5 || pageCount < 1 || pageCount > maximumHomePages ||
        favoriteArray.size() > maximumFavoriteApps || folderArray.size() > maximumFolders) {
        return false;
    }

    QStringList favoriteKeys;
    QVariantMap pages;
    for (const QJsonValue &value : favoriteArray) {
        if (!value.isObject())
            return false;
        const QJsonObject favorite = value.toObject();
        const QString packageName = favorite.value(QStringLiteral("packageName")).toString();
        const QString activityName = favorite.value(QStringLiteral("activityName")).toString();
        const QJsonValue pageValue = favorite.value(QStringLiteral("page"));
        if (!validLauncherTarget(packageName, activityName) || !pageValue.isDouble() ||
            pageValue.toInt(-1) < 0 || pageValue.toInt(-1) >= pageCount) {
            return false;
        }
        const QString key = favoriteKey(packageName, activityName);
        if (favoriteKeys.contains(key))
            return false;
        favoriteKeys.append(key);
        pages.insert(key, pageValue.toInt());
    }

    QList<Folder> folders;
    QStringList folderNames;
    for (const QJsonValue &value : folderArray) {
        if (!value.isObject())
            return false;
        const QJsonObject folderObject = value.toObject();
        const QString name = folderObject.value(QStringLiteral("name")).toString().trimmed();
        const QJsonValue appsValue = folderObject.value(QStringLiteral("apps"));
        if (!validFolderName(name) || !appsValue.isArray() ||
            appsValue.toArray().size() > maximumFolderItems) {
            return false;
        }
        if (std::any_of(folderNames.cbegin(), folderNames.cend(), [&name](const QString &other) {
                return other.compare(name, Qt::CaseInsensitive) == 0;
            })) {
            return false;
        }
        folderNames.append(name);
        Folder folder{QUuid::createUuid().toString(QUuid::WithoutBraces), name, {}};
        for (const QJsonValue &appValue : appsValue.toArray()) {
            if (!appValue.isObject())
                return false;
            const QJsonObject app = appValue.toObject();
            const QString packageName = app.value(QStringLiteral("packageName")).toString();
            const QString activityName = app.value(QStringLiteral("activityName")).toString();
            if (!validLauncherTarget(packageName, activityName))
                return false;
            const QString key = favoriteKey(packageName, activityName);
            if (!folder.activities.contains(key))
                folder.activities.append(key);
        }
        folders.append(std::move(folder));
    }

    QVariantMap widgets;
    const QJsonObject widgetObject = widgetsValue.toObject();
    for (const QString &widgetId : homeWidgetIds) {
        const QJsonValue enabled = widgetObject.value(widgetId);
        if (!enabled.isUndefined() && !enabled.isBool())
            return false;
        widgets.insert(widgetId, enabled.isUndefined() ? true : enabled.toBool());
    }

    QVariantMap gestures;
    const QJsonObject gestureObject = gesturesValue.toObject();
    for (const QString &gestureId : gestureIds) {
        const QJsonValue enabled = gestureObject.value(gestureId);
        if (!enabled.isUndefined() && !enabled.isBool())
            return false;
        gestures.insert(gestureId, enabled.isUndefined() ? true : enabled.toBool());
    }
    const bool hapticFeedbackEnabled = hapticFeedbackValue.isUndefined()
                                          ? false
                                          : hapticFeedbackValue.toBool();

    QSettings settings;
    settings.setValue(QStringLiteral("launcher/gridColumns"), grid);
    settings.setValue(QStringLiteral("launcher/homePageCount"), pageCount);
    settings.setValue(QStringLiteral("launcher/favorites"), favoriteKeys);
    settings.setValue(QStringLiteral("launcher/favoritePages"), pages);
    settings.setValue(QStringLiteral("launcher/folders"), QJsonDocument([&folders] {
        QJsonArray result;
        for (const Folder &folder : folders) {
            QJsonArray apps;
            for (const QString &key : folder.activities) {
                const qsizetype separator = key.indexOf(QLatin1Char('/'));
                apps.append(QJsonObject{
                    {QStringLiteral("packageName"), key.left(separator)},
                    {QStringLiteral("activityName"), key.mid(separator + 1)},
                });
            }
            result.append(QJsonObject{
                {QStringLiteral("id"), folder.id},
                {QStringLiteral("name"), folder.name},
                {QStringLiteral("apps"), apps},
            });
        }
        return result;
    }()).toJson(QJsonDocument::Compact));
    settings.setValue(QStringLiteral("launcher/homeWidgets"), widgets);
    settings.setValue(QStringLiteral("launcher/gestures"), gestures);
    settings.setValue(QStringLiteral("launcher/hapticFeedbackEnabled"), hapticFeedbackEnabled);
    settings.sync();
    if (settings.status() != QSettings::NoError) {
        qWarning() << "OmaApp: failed to restore launcher layout" << settings.status();
        return false;
    }

    m_gridColumns = grid;
    m_homePageCount = pageCount;
    m_favoritePackages = std::move(favoriteKeys);
    m_favoritePages = std::move(pages);
    m_folders = std::move(folders);
    m_homeWidgets = std::move(widgets);
    m_gestures = std::move(gestures);
    m_hapticFeedbackEnabled = hapticFeedbackEnabled;
    emit gridColumnsChanged();
    emit homePagesChanged();
    emit favoritesChanged();
    emit foldersChanged();
    emit homeWidgetsChanged();
    emit gesturesChanged();
    emit hapticFeedbackEnabledChanged();
    notifyFavoriteRowsChanged();
#ifdef Q_OS_ANDROID
    syncDynamicShortcuts();
#endif
    return true;
}

bool LauncherModel::copyLayoutBackup(const QString &owlWidgetBackup) const
{
    QClipboard *clipboard = QGuiApplication::clipboard();
    if (!clipboard)
        return false;
    QJsonObject payload = QJsonDocument::fromJson(exportLayout().toUtf8()).object();
    if (!owlWidgetBackup.isEmpty()) {
        const QJsonDocument widgetDocument = QJsonDocument::fromJson(owlWidgetBackup.toUtf8());
        if (!widgetDocument.isObject())
            return false;
        payload.insert(QStringLiteral("owlWidgets"), widgetDocument.object());
    }
    const QString backup = QString::fromUtf8(QJsonDocument(payload).toJson(QJsonDocument::Compact));
    if (backup.toUtf8().size() > maximumBackupBytes)
        return false;
    clipboard->setText(backup, QClipboard::Clipboard);
    return true;
}

QString LauncherModel::clipboardText() const
{
    QClipboard *clipboard = QGuiApplication::clipboard();
    return clipboard ? clipboard->text(QClipboard::Clipboard).left(maximumBackupBytes) : QString();
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
#if defined(OMAAPP_TEST_RHI_BACKEND_OPENGL) || defined(OMAAPP_TEST_RHI_BACKEND_VULKAN)
    qWarning().noquote() << "OmaApp startup: launcher refresh entered";
#endif
    const QJniObject context = QNativeInterface::QAndroidApplication::context();
    const QJniObject json = QJniObject::callStaticObjectMethod(
        "app/omaapp/LauncherBridge", "listLauncherApps",
        "(Landroid/content/Context;)Ljava/lang/String;", context.object<jobject>());

    const QByteArray payload = json.toString().toUtf8();
#if defined(OMAAPP_TEST_RHI_BACKEND_OPENGL) || defined(OMAAPP_TEST_RHI_BACKEND_VULKAN)
    qWarning().noquote() << "OmaApp startup: launcher query returned" << payload.size()
                         << "bytes";
#endif
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
    emit homePagesChanged();
    emit foldersChanged();
#ifdef Q_OS_ANDROID
    syncDynamicShortcuts();
#endif
#else
    beginResetModel();
    m_entries.clear();
    m_iconUrls.clear();
    rebuildVisibleRows();
    endResetModel();
    emit countChanged();
    emit favoritesChanged();
    emit homePagesChanged();
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

void LauncherModel::toggleFavorite(int row, int homePage)
{
    if (row < 0 || row >= m_visibleRows.size())
        return;
    const Entry &entry = m_entries.at(m_visibleRows.at(row));
    const QString key = favoriteKey(entry.packageName, entry.activityName);
    if (m_favoritePackages.contains(key)) {
        m_favoritePackages.removeAll(key);
        m_favoritePages.remove(key);
    } else {
        m_favoritePackages.append(key);
        m_favoritePages.insert(key, qBound(0, homePage, m_homePageCount - 1));
    }
    saveFavorites();
    saveFavoritePages();
    notifyFavoriteRowsChanged();
    qInfo().noquote()
        << QStringLiteral("OmaApp: pinned app count %1").arg(m_favoritePackages.size());
    emit favoritesChanged();
    emit homePagesChanged();
#ifdef Q_OS_ANDROID
    syncDynamicShortcuts();
#endif
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
    m_favoritePages.remove(key);
    saveFavorites();
    saveFavoritePages();
    notifyFavoriteRowsChanged();
    emit favoritesChanged();
    emit homePagesChanged();
#ifdef Q_OS_ANDROID
    syncDynamicShortcuts();
#endif
}

void LauncherModel::saveFavorites()
{
    QSettings settings;
    settings.setValue(QStringLiteral("launcher/favorites"), m_favoritePackages);
    settings.sync();
    if (settings.status() != QSettings::NoError)
        qWarning() << "OmaApp: failed to save pinned apps" << settings.status();
}

void LauncherModel::saveFavoritePages()
{
    QSettings settings;
    settings.setValue(QStringLiteral("launcher/favoritePages"), m_favoritePages);
    settings.sync();
    if (settings.status() != QSettings::NoError)
        qWarning() << "OmaApp: failed to save home app pages" << settings.status();
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

void LauncherModel::syncDynamicShortcuts() const
{
#ifdef Q_OS_ANDROID
    const QJniObject context = QNativeInterface::QAndroidApplication::context();
    const QByteArray favoritesJson = QJsonDocument::fromVariant(favorites())
                                        .toJson(QJsonDocument::Compact);
    const QJniObject favorites = QJniObject::fromString(QString::fromUtf8(favoritesJson));
    const jint published = QJniObject::callStaticMethod<jint>(
        "app/omaapp/LauncherBridge", "syncDynamicShortcuts",
        "(Landroid/content/Context;Ljava/lang/String;)I",
        context.object<jobject>(), favorites.object<jstring>());
    qInfo().noquote() << QStringLiteral("OmaApp: published %1 Android app shortcuts")
                             .arg(published);
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
