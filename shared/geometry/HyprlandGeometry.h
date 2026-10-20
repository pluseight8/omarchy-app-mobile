#pragma once

#include <QObject>
#include <QString>
#include <QtGlobal>

namespace OmaApp::Geometry {

float superellipseDistance(float x, float y, float power) noexcept;
float roundingCoverage(float x, float y, float radius, float power, float smoothing) noexcept;
QString roundedRectPath(float width, float height, float radius, float power, int segmentsPerCorner = 24);

} // namespace OmaApp::Geometry

class HyprlandGeometry final : public QObject
{
    Q_OBJECT

public:
    using QObject::QObject;

    Q_INVOKABLE QString roundedRectPath(
        qreal width,
        qreal height,
        qreal radius,
        qreal power,
        int segmentsPerCorner = 24) const;
};
