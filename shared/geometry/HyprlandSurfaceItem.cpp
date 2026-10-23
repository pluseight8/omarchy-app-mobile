#include "HyprlandSurfaceItem.h"

#include <QByteArray>
#include <QMatrix4x4>
#include <QSGGeometry>
#include <QSGGeometryNode>
#include <QSGMaterial>
#include <QSGMaterialShader>
#include <QSGRendererInterface>
#include <QSGTexture>
#include <QSizeF>
#include <QtMath>

#include <algorithm>
#include <array>
#include <cmath>
#include <cstring>
#include <utility>

namespace {
constexpr int uniformBufferSize = 288;
constexpr int maximumGradientStops = 10;

struct LabA
{
    float l;
    float a;
    float b;
    float alpha;
};

struct SurfaceAppearance
{
    QSizeF size;
    qreal devicePixelRatio = 1.0;
    qreal radius = 0.0;
    qreal roundingPower = 2.0;
    qreal borderWidth = 0.0;
    qreal borderGradientAngle = 0.0;
    QColor fillColor = Qt::transparent;
    std::array<LabA, maximumGradientStops> gradientStops{};
    int gradientStopCount = 0;
};

qreal clampUnit(qreal value)
{
    return std::clamp(value, qreal(0.0), qreal(1.0));
}

LabA toOkLabA(const QColor &input)
{
    const QColor color = input.toRgb();
    const qreal red = std::pow(clampUnit(color.redF()), 2.2);
    const qreal green = std::pow(clampUnit(color.greenF()), 2.2);
    const qreal blue = std::pow(clampUnit(color.blueF()), 2.2);
    const qreal l = std::cbrt(0.4122214708 * red + 0.5363325363 * green + 0.0514459929 * blue);
    const qreal m = std::cbrt(0.2119034982 * red + 0.6806995451 * green + 0.1073969566 * blue);
    const qreal s = std::cbrt(0.0883020379 * red + 0.2817188376 * green + 0.6299787005 * blue);
    return {
        float(0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s),
        float(1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s),
        float(0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s),
        float(clampUnit(color.alphaF())),
    };
}

std::array<QColor, maximumGradientStops> effectiveGradient(const QVariantList &inputStops,
                                                            const QColor &borderStart,
                                                            const QColor &borderEnd,
                                                            int *count)
{
    std::array<QColor, maximumGradientStops> colors{};
    int colorCount = 0;
    for (const QVariant &value : inputStops) {
        const QColor color = value.value<QColor>();
        if (!color.isValid())
            continue;
        colors[colorCount++] = color;
        if (colorCount == maximumGradientStops)
            break;
    }
    if (colorCount == 0) {
        colors[0] = borderStart;
        colorCount = 1;
        if (borderEnd != borderStart) {
            colors[1] = borderEnd;
            colorCount = 2;
        }
    }
    *count = colorCount;
    return colors;
}

class SurfaceMaterial final : public QSGMaterial
{
public:
    SurfaceMaterial()
    {
        setFlag(Blending, true);
        setFlag(NoBatching, true);
    }

    QSGMaterialType *type() const override
    {
        static QSGMaterialType materialType;
        return &materialType;
    }

    int compare(const QSGMaterial *other) const override
    {
        Q_UNUSED(other)
        return 0;
    }

    QSGMaterialShader *createShader(QSGRendererInterface::RenderMode) const override;

    SurfaceAppearance appearance;
    bool uniformsDirty = true;
};

class SurfaceShader final : public QSGMaterialShader
{
public:
    SurfaceShader()
    {
        setShaderFileName(VertexStage, QStringLiteral(":/shaders/hyprland_surface.vert.qsb"));
        setShaderFileName(FragmentStage, QStringLiteral(":/shaders/hyprland_surface.frag.qsb"));
    }

    bool updateUniformData(RenderState &state, QSGMaterial *newMaterial, QSGMaterial *oldMaterial) override
    {
        QByteArray *buffer = state.uniformData();
        Q_ASSERT(buffer && buffer->size() >= uniformBufferSize);
        bool changed = false;
        if (state.isMatrixDirty()) {
            const QMatrix4x4 matrix = state.combinedMatrix();
            std::memcpy(buffer->data(), matrix.constData(), 64);
            changed = true;
        }
        if (state.isOpacityDirty()) {
            const float opacity = float(state.opacity());
            std::memcpy(buffer->data() + 64, &opacity, sizeof(opacity));
            changed = true;
        }

        auto *material = static_cast<SurfaceMaterial *>(newMaterial);
        if (oldMaterial != newMaterial || material->uniformsDirty) {
            const SurfaceAppearance &appearance = material->appearance;
            const float devicePixelRatio = float(appearance.devicePixelRatio);
            const float itemSize[] = {float(appearance.size.width()), float(appearance.size.height())};
            const float radius = float(appearance.radius);
            const float roundingPower = float(appearance.roundingPower);
            const float borderWidth = float(appearance.borderWidth);
            const float borderGradientAngle = float(appearance.borderGradientAngle);
            const float fillColor[] = {
                float(appearance.fillColor.redF()), float(appearance.fillColor.greenF()),
                float(appearance.fillColor.blueF()), float(appearance.fillColor.alphaF()),
            };
            const int stopCount = appearance.gradientStopCount;

            std::memcpy(buffer->data() + 68, &devicePixelRatio, sizeof(devicePixelRatio));
            std::memcpy(buffer->data() + 72, itemSize, sizeof(itemSize));
            std::memcpy(buffer->data() + 80, &radius, sizeof(radius));
            std::memcpy(buffer->data() + 84, &roundingPower, sizeof(roundingPower));
            std::memcpy(buffer->data() + 88, &borderWidth, sizeof(borderWidth));
            std::memcpy(buffer->data() + 92, &borderGradientAngle, sizeof(borderGradientAngle));
            std::memcpy(buffer->data() + 96, fillColor, sizeof(fillColor));
            std::memcpy(buffer->data() + 112, &stopCount, sizeof(stopCount));
            for (int index = 0; index < maximumGradientStops; ++index) {
                const LabA stop = appearance.gradientStops.at(size_t(index));
                const float values[] = {stop.l, stop.a, stop.b, stop.alpha};
                std::memcpy(buffer->data() + 128 + index * 16, values, sizeof(values));
            }
            material->uniformsDirty = false;
            changed = true;
        }
        return changed;
    }
};

QSGMaterialShader *SurfaceMaterial::createShader(QSGRendererInterface::RenderMode) const
{
    return new SurfaceShader;
}

class SurfaceNode final : public QSGGeometryNode
{
public:
    SurfaceNode()
    {
        auto *geometry = new QSGGeometry(QSGGeometry::defaultAttributes_TexturedPoint2D(), 4);
        QSGGeometry::updateTexturedRectGeometry(geometry, QRectF(), QRectF(0, 0, 1, 1));
        setGeometry(geometry);
        setFlag(OwnsGeometry, true);

        auto *surfaceMaterial = new SurfaceMaterial;
        setMaterial(surfaceMaterial);
        setFlag(OwnsMaterial, true);
    }

    void updateSurface(const QRectF &bounds, SurfaceAppearance appearance)
    {
        QSGGeometry::updateTexturedRectGeometry(geometry(), bounds, QRectF(0, 0, 1, 1));
        markDirty(DirtyGeometry);

        auto *surfaceMaterial = static_cast<SurfaceMaterial *>(material());
        surfaceMaterial->appearance = std::move(appearance);
        surfaceMaterial->uniformsDirty = true;
        markDirty(DirtyMaterial);
    }
};

float boundedFloat(qreal value, qreal fallback, qreal minimum, qreal maximum)
{
    if (!std::isfinite(value))
        value = fallback;
    return float(std::clamp(value, minimum, maximum));
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

QSGNode *HyprlandSurfaceItem::updatePaintNode(QSGNode *oldNode, UpdatePaintNodeData *)
{
    if (width() <= 0.0 || height() <= 0.0) {
        delete oldNode;
        return nullptr;
    }

    auto *node = static_cast<SurfaceNode *>(oldNode);
    if (!node)
        node = new SurfaceNode;

    SurfaceAppearance appearance;
    appearance.size = QSizeF(width(), height());
    appearance.devicePixelRatio = boundedFloat(m_pixelRatio, 1.0, 1.0, 8.0);
    const qreal halfSmallestSide = qMin(width(), height()) * 0.5;
    appearance.radius = boundedFloat(m_radius, 0.0, 0.0, halfSmallestSide);
    appearance.roundingPower = boundedFloat(m_roundingPower, 2.0, 1.0, 10.0);
    appearance.borderWidth = boundedFloat(m_borderWidth, 0.0, 0.0, halfSmallestSide);
    qreal angle = std::isfinite(m_borderGradientAngle) ? std::fmod(m_borderGradientAngle, 360.0) : 0.0;
    if (angle < 0.0)
        angle += 360.0;
    appearance.borderGradientAngle = angle;
    appearance.fillColor = m_fillColor.isValid() ? m_fillColor.toRgb() : QColor(Qt::transparent);

    int stopCount = 0;
    const auto stops = effectiveGradient(m_borderStops, m_borderStart, m_borderEnd, &stopCount);
    if (m_borderStops.isEmpty()) {
        if (m_borderStart.isValid()) {
            appearance.gradientStops[0] = toOkLabA(m_borderStart);
            stopCount = 1;
        }
        if (m_borderEnd.isValid() && (m_borderEnd != m_borderStart || stopCount == 0)) {
            appearance.gradientStops[stopCount++] = toOkLabA(m_borderEnd);
        }
    } else {
        for (int index = 0; index < stopCount; ++index)
            appearance.gradientStops[size_t(index)] = toOkLabA(stops[size_t(index)]);
    }
    if (stopCount == 0) {
        appearance.gradientStops[0] = toOkLabA(Qt::transparent);
        stopCount = 1;
    }
    appearance.gradientStopCount = stopCount;

    node->updateSurface(QRectF(0, 0, width(), height()), std::move(appearance));
    return node;
}
