#include "HyprlandGeometry.h"

#include <algorithm>
#include <cmath>
#include <limits>

namespace {

constexpr float kMinimumPower = 2.0F;
constexpr float kMaximumPower = 16.0F;
constexpr float kHyprlandSmoothing = 3.14159265358979323846F / 5.34665792551F;

float clampPower(float power) noexcept
{
    if (!std::isfinite(power))
        return kMinimumPower;
    return std::clamp(power, kMinimumPower, kMaximumPower);
}

float smoothstep(float edge0, float edge1, float value) noexcept
{
    const float t = std::clamp((value - edge0) / (edge1 - edge0), 0.0F, 1.0F);
    return t * t * (3.0F - 2.0F * t);
}

QString number(qreal value)
{
    return QString::number(value, 'f', 3);
}

} // namespace

float OmaApp::Geometry::superellipseDistance(float x, float y, float power) noexcept
{
    if (!std::isfinite(x) || !std::isfinite(y))
        return std::numeric_limits<float>::infinity();

    const float p = clampPower(power);
    const float sum = std::pow(std::abs(x), p) + std::pow(std::abs(y), p);
    return std::pow(sum, 1.0F / p);
}

float OmaApp::Geometry::roundingCoverage(float x, float y, float radius, float power, float smoothing) noexcept
{
    if (!std::isfinite(x) || !std::isfinite(y))
        return 0.0F;

    const float safeRadius = std::max(0.0F, std::isfinite(radius) ? radius : 0.0F);
    const float safeSmoothing = std::max(
        0.0001F,
        std::isfinite(smoothing) && smoothing > 0.0F ? smoothing : kHyprlandSmoothing);

    if (x + y <= safeRadius)
        return 1.0F;

    const float distance = superellipseDistance(x, y, power);
    const float normalized = (distance - safeRadius + safeSmoothing) / (2.0F * safeSmoothing);
    return 1.0F - smoothstep(0.0F, 1.0F, normalized);
}

QString OmaApp::Geometry::roundedRectPath(
    float width,
    float height,
    float radius,
    float power,
    int segmentsPerCorner)
{
    const qreal w = std::max(0.0F, std::isfinite(width) ? width : 0.0F);
    const qreal h = std::max(0.0F, std::isfinite(height) ? height : 0.0F);
    const qreal r = std::clamp(
        std::isfinite(radius) ? static_cast<qreal>(radius) : 0.0,
        0.0,
        std::min(w, h) / 2.0);
    const qreal p = clampPower(power);
    const int segments = std::clamp(segmentsPerCorner, 8, 64);

    if (w <= 0.0 || h <= 0.0)
        return {};
    if (r <= 0.0)
        return QStringLiteral("M 0 0 L %1 0 L %1 %2 L 0 %2 Z").arg(number(w), number(h));

    QString path = QStringLiteral("M %1 0 L %2 0").arg(number(r), number(w - r));
    const auto appendCorner = [&path, r, p, segments](qreal centerX, qreal centerY, qreal start, qreal end) {
        for (int index = 1; index <= segments; ++index) {
            const qreal angle = start + (end - start) * static_cast<qreal>(index) / segments;
            const qreal cosine = std::cos(angle);
            const qreal sine = std::sin(angle);
            const qreal exponent = 2.0 / p;
            const qreal x = centerX + std::copysign(std::pow(std::abs(cosine), exponent), cosine) * r;
            const qreal y = centerY + std::copysign(std::pow(std::abs(sine), exponent), sine) * r;
            path += QStringLiteral(" L %1 %2").arg(number(x), number(y));
        }
    };

    constexpr qreal pi = 3.14159265358979323846;
    appendCorner(w - r, r, -pi / 2.0, 0.0);
    path += QStringLiteral(" L %1 %2").arg(number(w), number(h - r));
    appendCorner(w - r, h - r, 0.0, pi / 2.0);
    path += QStringLiteral(" L %1 %2").arg(number(r), number(h));
    appendCorner(r, h - r, pi / 2.0, pi);
    path += QStringLiteral(" L 0 %1").arg(number(r));
    appendCorner(r, r, pi, 3.0 * pi / 2.0);
    path += QStringLiteral(" Z");
    return path;
}

QString HyprlandGeometry::roundedRectPath(
    qreal width,
    qreal height,
    qreal radius,
    qreal power,
    int segmentsPerCorner) const
{
    return OmaApp::Geometry::roundedRectPath(
        static_cast<float>(width),
        static_cast<float>(height),
        static_cast<float>(radius),
        static_cast<float>(power),
        segmentsPerCorner);
}
