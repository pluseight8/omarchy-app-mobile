#include "OwlModel.h"

#include <QFile>
#include <QJsonArray>
#include <QJsonDocument>
#include <QJsonObject>
#include <QRegularExpression>
#include <QSettings>
#include <QUuid>
#include <algorithm>
#if defined(Q_OS_ANDROID)
#include <QJniObject>
#endif

namespace {
constexpr qsizetype maximumWidgetSourceLength = 64 * 1024;
constexpr qsizetype maximumCustomWidgets = 20;
constexpr qsizetype maximumWidgetBackupBytes = 2 * 1024 * 1024;

QString errorJson(const QString &message)
{
    return QString::fromUtf8(QJsonDocument(QJsonObject{{QStringLiteral("error"), message}})
                                 .toJson(QJsonDocument::Compact));
}
} // namespace

const QList<OwlModel::BuiltinWidget> OwlModel::s_builtins{
    {QStringLiteral("clock"), QStringLiteral("Omarchy clock"),
     QStringLiteral("A local clock and date, rendered with the OWL text and layout nodes."),
     QStringLiteral(":/owl/widgets/clock.owl")},
    {QStringLiteral("canvas"), QStringLiteral("Canvas sampler"),
     QStringLiteral("A small live canvas using OWL shapes, expressions, and Omarchy colors."),
     QStringLiteral(":/owl/widgets/canvas.owl")},
};

OwlModel::OwlModel(QObject *parent)
    : QObject(parent)
{
}

QStringList OwlModel::installedIds() const
{
    QSettings settings;
    const QStringList saved = settings.value(QStringLiteral("launcher/owlInstalled")).toStringList();
    QStringList installed;
    for (const QString &id : saved) {
        if (!installed.contains(id) && !source(id).isEmpty())
            installed.append(id);
    }
    return installed;
}

QVariantMap OwlModel::customSources() const
{
    QSettings settings;
    return settings.value(QStringLiteral("launcher/owlSources")).toMap();
}

QVariantList OwlModel::widgetStore() const
{
    const QStringList installed = installedIds();
    QVariantList result;
    result.reserve(s_builtins.size());
    for (const BuiltinWidget &widget : s_builtins) {
        QString name = widget.name;
        QString description = widget.description;
        if (widget.id == QStringLiteral("clock")) {
            name = tr("Omarchy clock");
            description = tr("A local clock and date, rendered with the OWL text and layout nodes.");
        } else if (widget.id == QStringLiteral("canvas")) {
            name = tr("Canvas sampler");
            description = tr("A small live canvas using OWL shapes, expressions, and Omarchy colors.");
        }
        result.append(QVariantMap{
            {QStringLiteral("id"), widget.id},
            {QStringLiteral("name"), name},
            {QStringLiteral("description"), description},
            {QStringLiteral("installed"), installed.contains(widget.id)},
            {QStringLiteral("editable"), false},
        });
    }

    const QVariantMap custom = customSources();
    for (auto it = custom.cbegin(); it != custom.cend(); ++it) {
        const QString id = it.key();
        if (!isCustomId(id))
            continue;
        result.append(QVariantMap{
            {QStringLiteral("id"), id},
            {QStringLiteral("name"), customName(it.value().toString(), tr("Custom widget"))},
            {QStringLiteral("description"), tr("Saved on this device; available offline.")},
            {QStringLiteral("installed"), installed.contains(id)},
            {QStringLiteral("editable"), true},
        });
    }
    return result;
}

void OwlModel::refreshLocalizedText()
{
    emit widgetsChanged();
}

QVariantList OwlModel::installedWidgets() const
{
    const QStringList installed = installedIds();
    const QVariantList store = widgetStore();
    QVariantList result;
    result.reserve(installed.size());
    for (const QString &id : installed) {
        const auto found = std::find_if(store.cbegin(), store.cend(), [&id](const QVariant &entry) {
            return entry.toMap().value(QStringLiteral("id")).toString() == id;
        });
        if (found != store.cend())
            result.append(*found);
    }
    return result;
}

QString OwlModel::source(const QString &widgetId) const
{
    if (isCustomId(widgetId))
        return customSources().value(widgetId).toString();
    for (const BuiltinWidget &widget : s_builtins) {
        if (widget.id == widgetId)
            return readResource(widget.resource);
    }
    return {};
}

QString OwlModel::renderWidget(const QString &widgetId, qreal width, qreal height,
                               const QString &themeJson) const
{
    const QString widgetSource = source(widgetId);
    if (widgetSource.isEmpty())
        return errorJson(QStringLiteral("Widget source is missing."));
    return render(widgetSource, width, height, themeJson);
}

QString OwlModel::renderSource(const QString &sourceText, qreal width, qreal height,
                               const QString &themeJson) const
{
    return render(sourceText, width, height, themeJson);
}

QString OwlModel::render(const QString &sourceText, qreal width, qreal height,
                         const QString &themeJson) const
{
    if (sourceText.size() > maximumWidgetSourceLength)
        return errorJson(QStringLiteral("Widget source exceeds 64 KB."));
#if defined(Q_OS_ANDROID)
    const QJniObject jniSource = QJniObject::fromString(sourceText);
    const QJniObject jniTheme = QJniObject::fromString(themeJson);
    const QJniObject rendered = QJniObject::callStaticObjectMethod(
        "app/omaapp/OwlBridge", "render", "(Ljava/lang/String;DDLjava/lang/String;)Ljava/lang/String;",
        jniSource.object<jstring>(), static_cast<jdouble>(width), static_cast<jdouble>(height),
        jniTheme.object<jstring>());
    if (!rendered.isValid())
        return errorJson(QStringLiteral("The embedded OWL runtime could not be reached."));
    return rendered.toString();
#else
    Q_UNUSED(width)
    Q_UNUSED(height)
    Q_UNUSED(themeJson)
    return errorJson(QStringLiteral("The OWL runtime is bundled in the Android app."));
#endif
}

bool OwlModel::setWidgetInstalled(const QString &widgetId, bool installed)
{
    if (source(widgetId).isEmpty())
        return false;
    QStringList ids = installedIds();
    const bool alreadyInstalled = ids.contains(widgetId);
    if (installed == alreadyInstalled)
        return true;
    if (installed)
        ids.append(widgetId);
    else
        ids.removeAll(widgetId);
    if (!saveStore(ids, customSources()))
        return false;
    emit widgetsChanged();
    return true;
}

bool OwlModel::moveWidget(const QString &widgetId, int destinationIndex)
{
    QStringList ids = installedIds();
    const int sourceIndex = ids.indexOf(widgetId);
    if (sourceIndex < 0 || destinationIndex < 0 || destinationIndex >= ids.size())
        return false;
    if (sourceIndex == destinationIndex)
        return true;
    ids.removeAt(sourceIndex);
    ids.insert(destinationIndex, widgetId);
    if (!saveStore(ids, customSources()))
        return false;
    emit widgetsChanged();
    return true;
}

QString OwlModel::createWidget(const QString &name)
{
    if (customSources().size() >= maximumCustomWidgets)
        return {};
    const QString id = QStringLiteral("user-") + QUuid::createUuid().toString(QUuid::WithoutBraces);
    QString safeName = name.trimmed().isEmpty() ? QStringLiteral("My widget") : name.trimmed();
    safeName.replace(QLatin1Char('"'), QLatin1Char('\''));
    safeName.replace(QLatin1Char('\\'), QLatin1Char('/'));
    safeName.replace(QLatin1Char('\n'), QLatin1Char(' '));
    safeName.replace(QLatin1Char('\r'), QLatin1Char(' '));
    if (safeName.size() > 80)
        safeName.truncate(80);
    const QString initial = QStringLiteral("widget \"%1\" {\n  meta { owl: 1 }\n  size: 2x1\n\n  Text { text: \"Hello from OWL\"; style: title; color: accent }\n}\n")
                               .arg(safeName);
    QVariantMap custom = customSources();
    custom.insert(id, initial);
    QStringList installed = installedIds();
    installed.append(id);
    if (!saveStore(installed, custom))
        return {};
    emit widgetsChanged();
    return id;
}

QString OwlModel::copyWidgetForEditing(const QString &widgetId)
{
    const QString widgetSource = source(widgetId);
    if (widgetSource.isEmpty() || customSources().size() >= maximumCustomWidgets)
        return {};
    const QString id = QStringLiteral("user-") + QUuid::createUuid().toString(QUuid::WithoutBraces);
    QVariantMap custom = customSources();
    custom.insert(id, widgetSource);
    QStringList installed = installedIds();
    installed.append(id);
    if (!saveStore(installed, custom))
        return {};
    emit widgetsChanged();
    return id;
}

bool OwlModel::saveWidget(const QString &widgetId, const QString &sourceText)
{
    if (!isCustomId(widgetId) || sourceText.size() > maximumWidgetSourceLength)
        return false;
    QVariantMap custom = customSources();
    if (!custom.contains(widgetId))
        return false;
    custom.insert(widgetId, sourceText);
    if (!saveStore(installedIds(), custom))
        return false;
    emit widgetsChanged();
    return true;
}

bool OwlModel::removeWidget(const QString &widgetId)
{
    if (!isCustomId(widgetId))
        return false;
    QVariantMap custom = customSources();
    if (!custom.remove(widgetId))
        return false;
    QStringList installed = installedIds();
    installed.removeAll(widgetId);
    if (!saveStore(installed, custom))
        return false;
    emit widgetsChanged();
    return true;
}

QString OwlModel::exportWidgetBackup() const
{
    QJsonArray installed;
    for (const QString &id : installedIds())
        installed.append(id);

    QJsonObject sources;
    const QVariantMap custom = customSources();
    for (auto it = custom.cbegin(); it != custom.cend(); ++it) {
        if (isCustomId(it.key()))
            sources.insert(it.key(), it.value().toString());
    }
    return QString::fromUtf8(QJsonDocument(QJsonObject{
        {QStringLiteral("schema"), 1},
        {QStringLiteral("installed"), installed},
        {QStringLiteral("sources"), sources},
    }).toJson(QJsonDocument::Compact));
}

bool OwlModel::canImportWidgetBackup(const QString &json) const
{
    const QByteArray payload = json.toUtf8();
    if (payload.isEmpty() || payload.size() > maximumWidgetBackupBytes)
        return false;

    QJsonParseError error;
    const QJsonDocument document = QJsonDocument::fromJson(payload, &error);
    if (error.error != QJsonParseError::NoError || !document.isObject())
        return false;
    const QJsonObject object = document.object();
    const QJsonValue schema = object.value(QStringLiteral("schema"));
    const QJsonValue installedValue = object.value(QStringLiteral("installed"));
    const QJsonValue sourcesValue = object.value(QStringLiteral("sources"));
    if (!schema.isDouble() || schema.toInt(-1) != 1 || !installedValue.isArray()
        || !sourcesValue.isObject()) {
        return false;
    }

    const QJsonObject sources = sourcesValue.toObject();
    if (sources.size() > maximumCustomWidgets)
        return false;
    qsizetype sourceBytes = 0;
    for (auto it = sources.constBegin(); it != sources.constEnd(); ++it) {
        if (!isCustomId(it.key()) || !it.value().isString())
            return false;
        const QString sourceText = it.value().toString();
        const qsizetype currentSourceBytes = sourceText.toUtf8().size();
        sourceBytes += currentSourceBytes;
        if (currentSourceBytes > maximumWidgetSourceLength || sourceBytes > maximumWidgetBackupBytes)
            return false;
    }

    QStringList installed;
    const QJsonArray installedArray = installedValue.toArray();
    if (installedArray.size() > maximumCustomWidgets + s_builtins.size())
        return false;
    for (const QJsonValue &value : installedArray) {
        if (!value.isString())
            return false;
        const QString id = value.toString();
        const bool isBuiltin = std::any_of(s_builtins.cbegin(), s_builtins.cend(),
                                           [&id](const BuiltinWidget &widget) {
                                               return widget.id == id;
                                           });
        if ((!isBuiltin && !sources.contains(id)) || installed.contains(id))
            return false;
        installed.append(id);
    }
    return true;
}

bool OwlModel::importWidgetBackup(const QString &json)
{
    if (!canImportWidgetBackup(json))
        return false;

    const QJsonObject object = QJsonDocument::fromJson(json.toUtf8()).object();
    QStringList installed;
    for (const QJsonValue &value : object.value(QStringLiteral("installed")).toArray())
        installed.append(value.toString());
    QVariantMap custom;
    const QJsonObject sources = object.value(QStringLiteral("sources")).toObject();
    for (auto it = sources.constBegin(); it != sources.constEnd(); ++it)
        custom.insert(it.key(), it.value().toString());

    if (!saveStore(installed, custom))
        return false;
    emit widgetsChanged();
    return true;
}

bool OwlModel::isCustomId(const QString &widgetId) const
{
    static const QRegularExpression pattern(QStringLiteral("^user-[0-9a-fA-F-]{36}$"));
    return pattern.match(widgetId).hasMatch();
}

QString OwlModel::customName(const QString &sourceText, const QString &fallback) const
{
    static const QRegularExpression pattern(QStringLiteral("^\\s*widget\\s+\"([^\"\\n]{1,80})\""));
    const QRegularExpressionMatch match = pattern.match(sourceText);
    return match.hasMatch() ? match.captured(1) : fallback;
}

QString OwlModel::readResource(const QString &path) const
{
    QFile file(path);
    if (!file.open(QIODevice::ReadOnly | QIODevice::Text))
        return {};
    return QString::fromUtf8(file.readAll());
}

bool OwlModel::saveStore(const QStringList &installed, const QVariantMap &custom) const
{
    QSettings settings;
    settings.setValue(QStringLiteral("launcher/owlInstalled"), installed);
    settings.setValue(QStringLiteral("launcher/owlSources"), custom);
    settings.sync();
    return settings.status() == QSettings::NoError;
}
