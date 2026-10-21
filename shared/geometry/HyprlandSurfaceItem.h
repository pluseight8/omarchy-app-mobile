#pragma once

#include <QColor>
#include <QQuickItem>
#include <QVariantList>

class HyprlandSurfaceItem : public QQuickItem
{
    Q_OBJECT
    Q_PROPERTY(QColor fillColor READ fillColor WRITE setFillColor NOTIFY appearanceChanged)
    Q_PROPERTY(QColor borderStart READ borderStart WRITE setBorderStart NOTIFY appearanceChanged)
    Q_PROPERTY(QColor borderEnd READ borderEnd WRITE setBorderEnd NOTIFY appearanceChanged)
    Q_PROPERTY(QVariantList borderStops READ borderStops WRITE setBorderStops NOTIFY appearanceChanged)
    Q_PROPERTY(qreal borderGradientAngle READ borderGradientAngle WRITE setBorderGradientAngle
                   NOTIFY appearanceChanged)
    Q_PROPERTY(qreal radius READ radius WRITE setRadius NOTIFY appearanceChanged)
    Q_PROPERTY(qreal roundingPower READ roundingPower WRITE setRoundingPower NOTIFY appearanceChanged)
    Q_PROPERTY(qreal borderWidth READ borderWidth WRITE setBorderWidth NOTIFY appearanceChanged)
    Q_PROPERTY(qreal pixelRatio READ pixelRatio WRITE setPixelRatio NOTIFY appearanceChanged)

public:
    explicit HyprlandSurfaceItem(QQuickItem *parent = nullptr);

    QColor fillColor() const { return m_fillColor; }
    QColor borderStart() const { return m_borderStart; }
    QColor borderEnd() const { return m_borderEnd; }
    QVariantList borderStops() const { return m_borderStops; }
    qreal borderGradientAngle() const { return m_borderGradientAngle; }
    qreal radius() const { return m_radius; }
    qreal roundingPower() const { return m_roundingPower; }
    qreal borderWidth() const { return m_borderWidth; }
    qreal pixelRatio() const { return m_pixelRatio; }

    void setFillColor(const QColor &value);
    void setBorderStart(const QColor &value);
    void setBorderEnd(const QColor &value);
    void setBorderStops(const QVariantList &value);
    void setBorderGradientAngle(qreal value);
    void setRadius(qreal value);
    void setRoundingPower(qreal value);
    void setBorderWidth(qreal value);
    void setPixelRatio(qreal value);

signals:
    void appearanceChanged();

protected:
    void geometryChange(const QRectF &newGeometry, const QRectF &oldGeometry) override;
    QSGNode *updatePaintNode(QSGNode *oldNode, UpdatePaintNodeData *) override;

private:
    QColor m_fillColor = Qt::transparent;
    QColor m_borderStart = Qt::transparent;
    QColor m_borderEnd = Qt::transparent;
    QVariantList m_borderStops;
    qreal m_borderGradientAngle = 0.0;
    qreal m_radius = 0.0;
    qreal m_roundingPower = 2.0;
    qreal m_borderWidth = 0.0;
    qreal m_pixelRatio = 1.0;
};
