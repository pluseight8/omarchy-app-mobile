#include "HyprlandSurfaceMesh.h"

#include <QtMath>
#include <algorithm>
#include <cmath>

namespace HyprlandSurfaceMesh {
namespace {
constexpr qreal pi = 3.14159265358979323846;
constexpr int maximumStops = 10;
constexpr int maximumArcSegments = 256;
constexpr int maximumEdgeSegments = 2048;
constexpr qreal maximumTessellationErrorPixels = 0.2;

struct LabA
{
    qreal l;
    qreal a;
    qreal b;
    qreal alpha;
};

qreal clampUnit(qreal value)
{
    return std::clamp(value, qreal(0.0), qreal(1.0));
}

LabA toLabA(const QColor &color)
{
    const qreal red = std::pow(clampUnit(color.redF()), 2.2);
    const qreal green = std::pow(clampUnit(color.greenF()), 2.2);
    const qreal blue = std::pow(clampUnit(color.blueF()), 2.2);
    const qreal l = std::cbrt(0.4122214708 * red + 0.5363325363 * green + 0.0514459929 * blue);
    const qreal m = std::cbrt(0.2119034982 * red + 0.6806995451 * green + 0.1073969566 * blue);
    const qreal s = std::cbrt(0.0883020379 * red + 0.2817188376 * green + 0.6299787005 * blue);
    return {0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
            clampUnit(color.alphaF())};
}

QColor fromLabA(const LabA &lab)
{
    const qreal l_ = lab.l + lab.a * 0.3963377774 + lab.b * 0.2158037573;
    const qreal m_ = lab.l - lab.a * 0.1055613458 - lab.b * 0.0638541728;
    const qreal s_ = lab.l - lab.a * 0.0894841775 - lab.b * 1.2914855480;
    const qreal l = l_ * l_ * l_;
    const qreal m = m_ * m_ * m_;
    const qreal s = s_ * s_ * s_;
    const qreal red = std::pow(clampUnit(l * 4.0767416621 - m * 3.3077115913 + s * 0.2309699292),
                               1.0 / 2.2);
    const qreal green = std::pow(clampUnit(-l * 1.2684380046 + m * 2.6097574011 - s * 0.3413193965),
                                 1.0 / 2.2);
    const qreal blue = std::pow(clampUnit(-l * 0.0041960863 - m * 0.7034186147 + s * 1.7076147010),
                                1.0 / 2.2);
    return QColor::fromRgbF(red, green, blue, clampUnit(lab.alpha));
}

QVector<QColor> effectiveStops(const Parameters &parameters)
{
    QVector<QColor> stops;
    if (parameters.borderStops.isEmpty()) {
        stops = {parameters.borderStart, parameters.borderEnd};
    } else {
        stops.reserve(std::min(int(parameters.borderStops.size()), maximumStops));
        for (const QColor &color : parameters.borderStops) {
            if (stops.size() == maximumStops)
                break;
            stops.append(color);
        }
    }
    if (stops.isEmpty())
        stops.append(Qt::transparent);
    return stops;
}

QPointF unitSuperellipse(qreal t, int corner, qreal power)
{
    const qreal sinPower = std::pow(std::max<qreal>(0.0, std::sin(t)), 2.0 / power);
    const qreal cosPower = std::pow(std::max<qreal>(0.0, std::cos(t)), 2.0 / power);
    switch (corner) {
    case 0: return {sinPower, cosPower};                         // top-left, top to left
    case 1: return {sinPower, cosPower};                         // top-right, top to right
    case 2: return {cosPower, sinPower};                         // bottom-right, right to bottom
    default: return {sinPower, cosPower};                        // bottom-left, bottom to left
    }
}

QPointF cornerCenter(const QSizeF &size, qreal radius, int corner)
{
    switch (corner) {
    case 0: return {radius, radius};
    case 1: return {size.width() - radius, radius};
    case 2: return {size.width() - radius, size.height() - radius};
    default: return {radius, size.height() - radius};
    }
}

QPointF contourPoint(const QSizeF &size, qreal radius, qreal t, int corner, qreal power)
{
    const QPointF unit = unitSuperellipse(t, corner, power);
    const QPointF center = cornerCenter(size, radius, corner);
    switch (corner) {
    case 0: return {center.x() - radius * unit.x(), center.y() - radius * unit.y()};
    case 1: return {center.x() + radius * unit.x(), center.y() - radius * unit.y()};
    case 2: return {center.x() + radius * unit.x(), center.y() + radius * unit.y()};
    default: return {center.x() - radius * unit.x(), center.y() + radius * unit.y()};
    }
}

QPointF arcPoint(qreal t, qreal radius, qreal power)
{
    return unitSuperellipse(t, 1, power) * radius;
}

qreal distanceToSegment(const QPointF &point, const QPointF &a, const QPointF &b)
{
    const QPointF edge = b - a;
    const qreal lengthSquared = QPointF::dotProduct(edge, edge);
    if (lengthSquared <= 0.000001)
        return std::hypot(point.x() - a.x(), point.y() - a.y());
    const qreal projection = std::clamp(QPointF::dotProduct(point - a, edge) / lengthSquared,
                                        qreal(0.0), qreal(1.0));
    const QPointF closest = a + edge * projection;
    return std::hypot(point.x() - closest.x(), point.y() - closest.y());
}

void appendAdaptiveArc(qreal begin, qreal end, qreal radius, qreal power, qreal pixelRatio,
                       int depth, QVector<qreal> *parameters)
{
    const qreal middle = (begin + end) * 0.5;
    const qreal error = distanceToSegment(arcPoint(middle, radius, power),
                                          arcPoint(begin, radius, power),
                                          arcPoint(end, radius, power)) * pixelRatio;
    if (error <= maximumTessellationErrorPixels || depth >= 20
        || parameters->size() >= maximumArcSegments) {
        parameters->append(end);
        return;
    }

    appendAdaptiveArc(begin, middle, radius, power, pixelRatio, depth + 1, parameters);
    appendAdaptiveArc(middle, end, radius, power, pixelRatio, depth + 1, parameters);
}

QPointF normalizedNormal(const QPointF &point)
{
    const qreal length = std::hypot(point.x(), point.y());
    return length > 0.000001 ? point / length : QPointF(0.0, -1.0);
}

QVector<QPointF> makeContour(const QSizeF &size, qreal radius, qreal power,
                             const QVector<qreal> &arcParameters, int edgeSegments)
{
    const qreal width = size.width();
    const qreal height = size.height();
    QVector<QPointF> points;
    points.reserve(4 * (arcParameters.size() - 1 + edgeSegments));

    // Clockwise from the top edge. Flat edges are subdivided too, so a long
    // angled gradient remains smooth across the border.
    for (int i = 0; i <= edgeSegments; ++i) {
        const qreal t = qreal(i) / edgeSegments;
        points.append({radius + (width - 2.0 * radius) * t, 0.0});
    }
    for (qsizetype i = 1; i < arcParameters.size(); ++i)
        points.append(contourPoint(size, radius, arcParameters.at(i), 1, power));

    for (int i = 1; i <= edgeSegments; ++i) {
        const qreal t = qreal(i) / edgeSegments;
        points.append({width, radius + (height - 2.0 * radius) * t});
    }
    for (qsizetype i = 1; i < arcParameters.size(); ++i)
        points.append(contourPoint(size, radius, arcParameters.at(i), 2, power));

    for (int i = 1; i <= edgeSegments; ++i) {
        const qreal t = qreal(i) / edgeSegments;
        points.append({width - radius - (width - 2.0 * radius) * t, height});
    }
    for (qsizetype i = 1; i < arcParameters.size(); ++i)
        points.append(contourPoint(size, radius, arcParameters.at(i), 3, power));

    for (int i = 1; i <= edgeSegments; ++i) {
        const qreal t = qreal(i) / edgeSegments;
        points.append({0.0, height - radius - (height - 2.0 * radius) * t});
    }
    // The top-left arc runs from the left edge back toward the top edge.
    for (qsizetype i = arcParameters.size() - 2; i >= 0; --i)
        points.append(contourPoint(size, radius, arcParameters.at(i), 0, power));

    if (points.size() > 1 && std::hypot(points.constFirst().x() - points.constLast().x(),
                                       points.constFirst().y() - points.constLast().y()) <= 0.000001)
        points.removeLast();

    return points;
}

QVector<QPointF> contourNormals(const QVector<QPointF> &contour)
{
    QVector<QPointF> normals;
    normals.reserve(contour.size());
    for (qsizetype i = 0; i < contour.size(); ++i) {
        qsizetype previousIndex = (i + contour.size() - 1) % contour.size();
        qsizetype nextIndex = (i + 1) % contour.size();
        while (previousIndex != i && (contour[previousIndex] - contour[i]).manhattanLength() <= 0.000001)
            previousIndex = (previousIndex + contour.size() - 1) % contour.size();
        while (nextIndex != i && (contour[nextIndex] - contour[i]).manhattanLength() <= 0.000001)
            nextIndex = (nextIndex + 1) % contour.size();
        const QPointF tangent = contour[nextIndex] - contour[previousIndex];
        // The contour is clockwise in Qt's y-down coordinates, so (dy,-dx)
        // points outwards.
        normals.append(normalizedNormal({tangent.y(), -tangent.x()}));
    }
    return normals;
}

QPointF normalizePoint(const QPointF &point, const QSizeF &size)
{
    return {size.width() > 0.0 ? point.x() / size.width() : 0.0,
            size.height() > 0.0 ? point.y() / size.height() : 0.0};
}

void appendVertex(QVector<Vertex> *vertices, const QPointF &position, const QColor &color,
                  qreal coverage = 1.0)
{
    vertices->append({position, color, clampUnit(coverage)});
}

void appendTriangle(QVector<Vertex> *vertices,
                    const QPointF &a, const QColor &colorA, qreal coverageA,
                    const QPointF &b, const QColor &colorB, qreal coverageB,
                    const QPointF &c, const QColor &colorC, qreal coverageC)
{
    appendVertex(vertices, a, colorA, coverageA);
    appendVertex(vertices, b, colorB, coverageB);
    appendVertex(vertices, c, colorC, coverageC);
}

} // namespace

QColor gradientColor(const QVector<QColor> &stops, const QPointF &normalizedPoint,
                     qreal angleDegrees)
{
    if (stops.size() <= 1)
        return stops.isEmpty() ? QColor(Qt::transparent) : stops.constFirst();

    if (!std::isfinite(normalizedPoint.x()) || !std::isfinite(normalizedPoint.y()))
        return stops.constFirst();
    if (!std::isfinite(angleDegrees))
        angleDegrees = 0.0;
    qreal degrees = std::fmod(std::fmod(angleDegrees, 360.0) + 360.0, 360.0);
    qreal angle = degrees * pi / 180.0;
    QPointF coordinate = normalizedPoint;
    qreal finalAngle = 0.0;
    if (angle > 4.71) {
        coordinate.setY(1.0 - coordinate.y());
        finalAngle = 6.28 - angle;
    } else if (angle > 3.14) {
        coordinate = QPointF(1.0 - coordinate.x(), 1.0 - coordinate.y());
        finalAngle = angle - 3.14;
    } else if (angle > 1.57) {
        coordinate.setX(1.0 - coordinate.x());
        finalAngle = 3.14 - angle;
    } else {
        finalAngle = angle;
    }

    const qreal sine = std::sin(finalAngle);
    const qreal position = clampUnit(coordinate.y() * sine + coordinate.x() * (1.0 - sine));
    const qreal progress = position * (stops.size() - 1);
    const int stopCount = int(stops.size());
    if (progress <= 0.0)
        return stops.constFirst();
    if (progress >= stopCount - 1)
        return stops.constLast();
    const int lowerIndex = std::min(int(std::floor(progress)), stopCount - 1);
    const int upperIndex = std::min(lowerIndex + 1, stopCount - 1);
    const qreal fraction = progress >= stops.size() - 1 ? 0.0 : progress - lowerIndex;
    if (fraction <= 0.0)
        return stops.at(lowerIndex);
    const LabA lower = toLabA(stops.at(lowerIndex));
    const LabA upper = toLabA(stops.at(upperIndex));
    return fromLabA({lower.l + (upper.l - lower.l) * fraction,
                     lower.a + (upper.a - lower.a) * fraction,
                     lower.b + (upper.b - lower.b) * fraction,
                     lower.alpha + (upper.alpha - lower.alpha) * fraction});
}

Mesh build(const Parameters &input)
{
    Mesh result;
    const qreal width = input.size.width();
    const qreal height = input.size.height();
    if (!std::isfinite(width) || !std::isfinite(height) || width <= 0.0 || height <= 0.0)
        return result;

    const qreal pixelRatio = std::clamp(std::isfinite(input.pixelRatio) ? input.pixelRatio : qreal(1.0),
                                        qreal(0.25), qreal(16.0));
    const qreal maxRadius = std::min(width, height) * 0.5;
    const qreal requestedStroke = std::isfinite(input.borderWidth) ? input.borderWidth : 0.0;
    const qreal stroke = std::clamp(requestedStroke, qreal(0.0), maxRadius);
    const qreal requestedRadius = std::isfinite(input.radius) ? input.radius : 0.0;
    const qreal innerRadius = std::clamp(requestedRadius, qreal(0.0),
                                         std::min(width, height) * 0.5 - stroke);
    const qreal requestedPower = std::isfinite(input.roundingPower) ? input.roundingPower : 2.0;
    const qreal power = std::clamp(requestedPower, qreal(1.0), qreal(10.0));
    const qreal gradientAngle = std::isfinite(input.borderGradientAngle)
        ? input.borderGradientAngle : 0.0;
    const qreal outerRadius = innerRadius + stroke;
    const qreal correctedOuterRadius = std::max(
        qreal(0.0), outerRadius - stroke * (std::sqrt(2.0) - 1.0) * std::max(2.0 - power, 0.0));
    const qreal aaWidth = 1.0 / pixelRatio;
    QVector<qreal> arcParameters{0.0};
    appendAdaptiveArc(0.0, pi * 0.5, correctedOuterRadius, power, pixelRatio, 0, &arcParameters);
    const qreal longestEdge = std::max(width, height) - 2.0 * correctedOuterRadius;
    const qreal requestedEdgeSegments = std::ceil(std::max<qreal>(0.0, longestEdge * pixelRatio) / 8.0);
    const int edgeSegments = int(std::clamp(requestedEdgeSegments, qreal(1.0),
                                            qreal(maximumEdgeSegments)));

    const QSizeF outerSize(width, height);
    const QSizeF innerSize(std::max<qreal>(0.0, width - 2.0 * stroke),
                           std::max<qreal>(0.0, height - 2.0 * stroke));
    const QVector<QPointF> outer = makeContour(outerSize, correctedOuterRadius, power,
                                               arcParameters, edgeSegments);
    if (stroke > 0.0) {
        result.innerBoundary = makeContour(innerSize, innerRadius, power, arcParameters, edgeSegments);
        for (QPointF &point : result.innerBoundary)
            point += QPointF(stroke, stroke);
    } else {
        result.innerBoundary = outer;
    }
    result.outerBoundary = outer;

    if (outer.isEmpty() || result.innerBoundary.size() != outer.size())
        return {};

    const QVector<QPointF> normals = contourNormals(outer);
    QVector<QPointF> fringe;
    fringe.reserve(outer.size());
    for (qsizetype i = 0; i < outer.size(); ++i)
        fringe.append(outer.at(i) + normals.at(i) * aaWidth);

    const QVector<QColor> stops = effectiveStops(input);
    const QColor fill = input.fillColor;
    const QPointF center(width * 0.5, height * 0.5);

    for (qsizetype i = 0; i < result.innerBoundary.size(); ++i) {
        const qsizetype next = (i + 1) % result.innerBoundary.size();
        appendTriangle(&result.triangles, center, fill, 1.0,
                       result.innerBoundary.at(i), fill, 1.0,
                       result.innerBoundary.at(next), fill, 1.0);
    }

    if (stroke > 0.0) {
        for (qsizetype i = 0; i < outer.size(); ++i) {
            const qsizetype next = (i + 1) % outer.size();
            const QPointF outerA = outer.at(i);
            const QPointF outerB = outer.at(next);
            const QPointF innerA = result.innerBoundary.at(i);
            const QPointF innerB = result.innerBoundary.at(next);
            const QColor colorOuter = gradientColor(stops, normalizePoint(outerA, outerSize),
                                                    gradientAngle);
            const QColor colorInner = gradientColor(stops, normalizePoint(innerA, outerSize),
                                                    gradientAngle);
            appendTriangle(&result.triangles, innerA, colorInner, 1.0,
                           outerA, colorOuter, 1.0,
                           outerB, gradientColor(stops, normalizePoint(outerB, outerSize),
                                                 gradientAngle), 1.0);
            appendTriangle(&result.triangles, innerA, colorInner, 1.0,
                           outerB, gradientColor(stops, normalizePoint(outerB, outerSize),
                                                 gradientAngle), 1.0,
                           innerB, gradientColor(stops, normalizePoint(innerB, outerSize),
                                                 gradientAngle), 1.0);
        }

        for (qsizetype i = 0; i < outer.size(); ++i) {
            const qsizetype next = (i + 1) % outer.size();
            const QColor colorA = gradientColor(stops, normalizePoint(outer.at(i), outerSize),
                                                gradientAngle);
            const QColor colorB = gradientColor(stops, normalizePoint(outer.at(next), outerSize),
                                                gradientAngle);
            appendTriangle(&result.triangles, outer.at(i), colorA, 1.0,
                           fringe.at(i), colorA, 0.0,
                           fringe.at(next), colorB, 0.0);
            appendTriangle(&result.triangles, outer.at(i), colorA, 1.0,
                           fringe.at(next), colorB, 0.0,
                           outer.at(next), colorB, 1.0);
        }
    } else {
        for (qsizetype i = 0; i < outer.size(); ++i) {
            const qsizetype next = (i + 1) % outer.size();
            appendTriangle(&result.triangles, outer.at(i), fill, 1.0,
                           fringe.at(i), fill, 0.0,
                           fringe.at(next), fill, 0.0);
            appendTriangle(&result.triangles, outer.at(i), fill, 1.0,
                           fringe.at(next), fill, 0.0,
                           outer.at(next), fill, 1.0);
        }
    }

    return result;
}

} // namespace HyprlandSurfaceMesh
