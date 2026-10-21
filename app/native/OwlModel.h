#pragma once

#include <QObject>
#include <QString>
#include <QVariantList>
#include <QVariantMap>

class OwlModel : public QObject
{
    Q_OBJECT
    Q_PROPERTY(QVariantList widgetStore READ widgetStore NOTIFY widgetsChanged)
    Q_PROPERTY(QVariantList installedWidgets READ installedWidgets NOTIFY widgetsChanged)

public:
    explicit OwlModel(QObject *parent = nullptr);

    QVariantList widgetStore() const;
    QVariantList installedWidgets() const;

    Q_INVOKABLE QString source(const QString &widgetId) const;
    Q_INVOKABLE QString renderWidget(const QString &widgetId, qreal width, qreal height,
                                     const QString &themeJson) const;
    Q_INVOKABLE QString renderSource(const QString &sourceText, qreal width, qreal height,
                                     const QString &themeJson) const;
    Q_INVOKABLE bool setWidgetInstalled(const QString &widgetId, bool installed);
    Q_INVOKABLE bool moveWidget(const QString &widgetId, int destinationIndex);
    Q_INVOKABLE QString createWidget(const QString &name = QString());
    Q_INVOKABLE QString copyWidgetForEditing(const QString &widgetId);
    Q_INVOKABLE bool saveWidget(const QString &widgetId, const QString &sourceText);
    Q_INVOKABLE bool removeWidget(const QString &widgetId);
    Q_INVOKABLE QString exportWidgetBackup() const;
    Q_INVOKABLE bool canImportWidgetBackup(const QString &json) const;
    Q_INVOKABLE bool importWidgetBackup(const QString &json);

signals:
    void widgetsChanged();

private:
    struct BuiltinWidget {
        QString id;
        QString name;
        QString description;
        QString resource;
    };

    QStringList installedIds() const;
    QVariantMap customSources() const;
    bool isCustomId(const QString &widgetId) const;
    QString customName(const QString &sourceText, const QString &fallback) const;
    QString render(const QString &sourceText, qreal width, qreal height,
                   const QString &themeJson) const;
    QString readResource(const QString &path) const;
    void saveStore(const QStringList &installed, const QVariantMap &custom) const;

    static const QList<BuiltinWidget> s_builtins;
};
