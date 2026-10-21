#pragma once

#include <QColor>
#include <QPointF>
#include <QSizeF>
#include <QVector>

namespace HyprlandSurfaceMesh {

struct Vertex
{
    QPointF position;
    QColor color;
    qreal coverage = 1.0;
};

struct Parameters
{
    QSizeF size;
    qreal pixelRatio = 1.0;
    qreal radius = 0.0;
    qreal roundingPower = 2.0;
    qreal borderWidth = 0.0;
    qreal borderGradientAngle = 0.0;
    QColor fillColor;
    QColor borderStart;
    QColor borderEnd;
    QVector<QColor> borderStops;
};

struct Mesh
{
    // Triangle-list vertices. Colors are straight-alpha QColor values; the
    // scene-graph adapter premultiplies them for QSGVertexColorMaterial.
    QVector<Vertex> triangles;
    QVector<QPointF> outerBoundary;
    QVector<QPointF> innerBoundary;
};

Mesh build(const Parameters &parameters);
QColor gradientColor(const QVector<QColor> &stops, const QPointF &normalizedPoint,
                     qreal angleDegrees);

} // namespace HyprlandSurfaceMesh
