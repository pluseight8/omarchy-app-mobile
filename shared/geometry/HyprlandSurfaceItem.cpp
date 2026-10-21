#include "HyprlandSurfaceItem.h"

#include <QSGGeometry>
#include <QSGGeometryNode>
#include <QSGVertexColorMaterial>
#include <QtMath>
#include <utility>

namespace {
uchar premultipliedChannel(qreal channel, qreal alpha)
{
    return static_cast<uchar>(qBound(0, qRound(qBound<qreal>(0.0, channel, 1.0)
                                                * qBound<qreal>(0.0, alpha, 1.0) * 255.0), 255));
}

QSGGeometryNode *createNode(const HyprlandSurfaceMesh::Mesh &mesh)
{
    if (mesh.triangles.isEmpty())
        return nullptr;

    auto *node = new QSGGeometryNode;
    auto *geometry = new QSGGeometry(QSGGeometry::defaultAttributes_ColoredPoint2D(),
                                     static_cast<int>(mesh.triangles.size()));
    geometry->setDrawingMode(QSGGeometry::DrawTriangles);
    geometry->setVertexDataPattern(QSGGeometry::DynamicPattern);
    auto *vertices = geometry->vertexDataAsColoredPoint2D();
    for (qsizetype i = 0; i < mesh.triangles.size(); ++i) {
        const auto &vertex = mesh.triangles.at(i);
        const qreal alpha = vertex.color.alphaF() * vertex.coverage;
        vertices[i].set(float(vertex.position.x()), float(vertex.position.y()),
                        premultipliedChannel(vertex.color.redF(), alpha),
                        premultipliedChannel(vertex.color.greenF(), alpha),
                        premultipliedChannel(vertex.color.blueF(), alpha),
                        static_cast<uchar>(qBound(0, qRound(alpha * 255.0), 255)));
    }
    node->setGeometry(geometry);
    node->setFlag(QSGNode::OwnsGeometry);

    auto *material = new QSGVertexColorMaterial;
    material->setFlag(QSGMaterial::Blending, true);
    node->setMaterial(material);
    node->setFlag(QSGNode::OwnsMaterial);
    return node;
}

void updateNode(QSGGeometryNode *node, const HyprlandSurfaceMesh::Mesh &mesh)
{
    auto *geometry = node->geometry();
    geometry->allocate(static_cast<int>(mesh.triangles.size()));
    auto *vertices = geometry->vertexDataAsColoredPoint2D();
    for (qsizetype i = 0; i < mesh.triangles.size(); ++i) {
        const auto &vertex = mesh.triangles.at(i);
        const qreal alpha = vertex.color.alphaF() * vertex.coverage;
        vertices[i].set(float(vertex.position.x()), float(vertex.position.y()),
                        premultipliedChannel(vertex.color.redF(), alpha),
                        premultipliedChannel(vertex.color.greenF(), alpha),
                        premultipliedChannel(vertex.color.blueF(), alpha),
                        static_cast<uchar>(qBound(0, qRound(alpha * 255.0), 255)));
    }
    node->markDirty(QSGNode::DirtyGeometry);
}
} // namespace

HyprlandSurfaceItem::HyprlandSurfaceItem(QQuickItem *parent)
    : QQuickItem(parent)
{
    setFlag(ItemHasContents, true);
}

#define OMAAPP_SET_SURFACE_PROPERTY(type, name, member) \
    void HyprlandSurfaceItem::set##name(type value) \
    { \
        if (member == value) \
            return; \
        member = std::move(value); \
        emit appearanceChanged(); \
        update(); \
    }

OMAAPP_SET_SURFACE_PROPERTY(const QColor &, FillColor, m_fillColor)
OMAAPP_SET_SURFACE_PROPERTY(const QColor &, BorderStart, m_borderStart)
OMAAPP_SET_SURFACE_PROPERTY(const QColor &, BorderEnd, m_borderEnd)
OMAAPP_SET_SURFACE_PROPERTY(const QVariantList &, BorderStops, m_borderStops)
OMAAPP_SET_SURFACE_PROPERTY(qreal, BorderGradientAngle, m_borderGradientAngle)
OMAAPP_SET_SURFACE_PROPERTY(qreal, Radius, m_radius)
OMAAPP_SET_SURFACE_PROPERTY(qreal, RoundingPower, m_roundingPower)
OMAAPP_SET_SURFACE_PROPERTY(qreal, BorderWidth, m_borderWidth)
OMAAPP_SET_SURFACE_PROPERTY(qreal, PixelRatio, m_pixelRatio)

#undef OMAAPP_SET_SURFACE_PROPERTY

void HyprlandSurfaceItem::geometryChange(const QRectF &newGeometry, const QRectF &oldGeometry)
{
    QQuickItem::geometryChange(newGeometry, oldGeometry);
    update();
}

HyprlandSurfaceMesh::Parameters HyprlandSurfaceItem::parameters() const
{
    HyprlandSurfaceMesh::Parameters result;
    result.size = QSizeF(width(), height());
    result.pixelRatio = m_pixelRatio;
    result.radius = m_radius;
    result.roundingPower = m_roundingPower;
    result.borderWidth = m_borderWidth;
    result.borderGradientAngle = m_borderGradientAngle;
    result.fillColor = m_fillColor;
    result.borderStart = m_borderStart;
    result.borderEnd = m_borderEnd;
    result.borderStops.reserve(qMin(m_borderStops.size(), 10));
    for (const QVariant &value : m_borderStops) {
        if (result.borderStops.size() == 10)
            break;
        const QColor color = value.value<QColor>();
        if (color.isValid())
            result.borderStops.append(color);
    }
    return result;
}

QSGNode *HyprlandSurfaceItem::updatePaintNode(QSGNode *oldNode, UpdatePaintNodeData *)
{
    const auto mesh = HyprlandSurfaceMesh::build(parameters());
    if (mesh.triangles.isEmpty()) {
        delete oldNode;
        return nullptr;
    }

    if (!oldNode)
        return createNode(mesh);

    updateNode(static_cast<QSGGeometryNode *>(oldNode), mesh);
    return oldNode;
}
