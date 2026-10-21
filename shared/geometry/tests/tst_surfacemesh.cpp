#include "shared/geometry/HyprlandSurfaceMesh.h"

#include <QTest>
#include <algorithm>
#include <cmath>
#include <limits>

class SurfaceMeshTests final : public QObject
{
    Q_OBJECT

private slots:
    void roundingPowerCreatesExactLpContour_data();
    void roundingPowerCreatesExactLpContour();
    void physicalPixelRatioRefinesTheMesh();
    void highPowerCurveStaysWithinSubpixelError();
    void arbitraryGradientAngleKeepsExpectedEndpoints();
    void borderAndAntialiasStayWithinOnePhysicalPixel();
    void invalidScalarValuesAreSafelyClamped();
};

void SurfaceMeshTests::roundingPowerCreatesExactLpContour_data()
{
    QTest::addColumn<qreal>("power");
    for (qreal power : {1.0, 1.25, 1.7, 2.0, 2.75, 4.0, 8.0, 10.0})
        QTest::newRow(QByteArray::number(power).constData()) << power;
}

void SurfaceMeshTests::roundingPowerCreatesExactLpContour()
{
    QFETCH(qreal, power);
    HyprlandSurfaceMesh::Parameters parameters;
    parameters.size = QSizeF(320.0, 180.0);
    parameters.radius = 36.0;
    parameters.borderWidth = 4.0;
    parameters.roundingPower = power;
    parameters.pixelRatio = 2.5;

    const auto mesh = HyprlandSurfaceMesh::build(parameters);
    QVERIFY(mesh.outerBoundary.size() > 100);
    const qreal rounding = parameters.radius + parameters.borderWidth
        - parameters.borderWidth * (std::sqrt(2.0) - 1.0) * std::max(2.0 - power, 0.0);
    const qreal tolerance = 1e-8;
    int arcPoints = 0;
    for (const QPointF &point : mesh.outerBoundary) {
        qreal x = 0.0;
        qreal y = 0.0;
        bool inCorner = false;
        if (point.x() <= rounding && point.y() <= rounding) {
            x = rounding - point.x();
            y = rounding - point.y();
            inCorner = true;
        } else if (point.x() >= parameters.size.width() - rounding && point.y() <= rounding) {
            x = point.x() - (parameters.size.width() - rounding);
            y = rounding - point.y();
            inCorner = true;
        } else if (point.x() >= parameters.size.width() - rounding
                   && point.y() >= parameters.size.height() - rounding) {
            x = point.x() - (parameters.size.width() - rounding);
            y = point.y() - (parameters.size.height() - rounding);
            inCorner = true;
        } else if (point.x() <= rounding && point.y() >= parameters.size.height() - rounding) {
            x = rounding - point.x();
            y = point.y() - (parameters.size.height() - rounding);
            inCorner = true;
        }
        if (!inCorner)
            continue;

        const qreal contour = std::pow(x / rounding, power) + std::pow(y / rounding, power);
        QVERIFY2(std::abs(contour - 1.0) <= tolerance,
                 qPrintable(QStringLiteral("rounding_power %1 contour error %2 at (%3,%4)")
                                .arg(power).arg(contour - 1.0, 0, 'g', 12)
                                .arg(point.x()).arg(point.y())));
        ++arcPoints;
    }
    QVERIFY(arcPoints > 0);
}

void SurfaceMeshTests::physicalPixelRatioRefinesTheMesh()
{
    HyprlandSurfaceMesh::Parameters parameters;
    parameters.size = QSizeF(320.0, 180.0);
    parameters.radius = 30.0;
    parameters.borderWidth = 3.0;
    parameters.roundingPower = 3.0;
    parameters.pixelRatio = 1.0;
    const auto lowDensity = HyprlandSurfaceMesh::build(parameters);
    parameters.pixelRatio = 3.0;
    const auto highDensity = HyprlandSurfaceMesh::build(parameters);

    QVERIFY(highDensity.outerBoundary.size() > lowDensity.outerBoundary.size());
    QVERIFY(highDensity.triangles.size() > lowDensity.triangles.size());
}

void SurfaceMeshTests::highPowerCurveStaysWithinSubpixelError()
{
    HyprlandSurfaceMesh::Parameters parameters;
    parameters.size = QSizeF(320.0, 180.0);
    parameters.pixelRatio = 3.0;
    parameters.radius = 34.0;
    parameters.roundingPower = 10.0;
    parameters.borderWidth = 4.0;
    const auto mesh = HyprlandSurfaceMesh::build(parameters);
    const qreal radius = parameters.radius + parameters.borderWidth;
    qreal maximumError = 0.0;
    constexpr qreal halfPi = 1.57079632679489661923;
    constexpr int samples = 2048;
    for (int i = 0; i <= samples; ++i) {
        const qreal t = (halfPi * i) / samples;
        const QPointF exact(parameters.size.width() - radius
                                + radius * std::pow(std::sin(t), 2.0 / parameters.roundingPower),
                            radius - radius * std::pow(std::cos(t), 2.0 / parameters.roundingPower));
        qreal nearest = std::numeric_limits<qreal>::max();
        for (qsizetype j = 0; j < mesh.outerBoundary.size(); ++j) {
            const QPointF &a = mesh.outerBoundary.at(j);
            const QPointF &b = mesh.outerBoundary.at((j + 1) % mesh.outerBoundary.size());
            const QPointF edge = b - a;
            const qreal lengthSquared = QPointF::dotProduct(edge, edge);
            const qreal projection = lengthSquared > 0.0
                ? std::clamp(QPointF::dotProduct(exact - a, edge) / lengthSquared, 0.0, 1.0)
                : 0.0;
            const QPointF closest = a + edge * projection;
            nearest = std::min(nearest, std::hypot(exact.x() - closest.x(), exact.y() - closest.y()));
        }
        maximumError = std::max(maximumError, nearest * parameters.pixelRatio);
    }
    QVERIFY2(maximumError <= 0.25,
             qPrintable(QStringLiteral("rounding_power 10 tessellation error is %1 physical pixels")
                            .arg(maximumError, 0, 'f', 4)));
}

void SurfaceMeshTests::arbitraryGradientAngleKeepsExpectedEndpoints()
{
    const QVector<QColor> stops{QColor(255, 0, 0), QColor(0, 0, 255)};
    QCOMPARE(HyprlandSurfaceMesh::gradientColor(stops, QPointF(1.0, 0.0), 135.0), stops.first());
    QCOMPARE(HyprlandSurfaceMesh::gradientColor(stops, QPointF(0.0, 1.0), 135.0), stops.last());

    const QColor middle = HyprlandSurfaceMesh::gradientColor(stops, QPointF(0.5, 0.5), 37.0);
    QVERIFY(middle.isValid());
    QVERIFY(middle.alpha() == 255);
    QVERIFY(middle.red() > 0);
    QVERIFY(middle.blue() > 0);
}

void SurfaceMeshTests::borderAndAntialiasStayWithinOnePhysicalPixel()
{
    HyprlandSurfaceMesh::Parameters parameters;
    parameters.size = QSizeF(200.0, 120.0);
    parameters.pixelRatio = 2.0;
    parameters.radius = 24.0;
    parameters.roundingPower = 2.6;
    parameters.borderWidth = 5.0;
    parameters.fillColor = QColor(20, 40, 60, 128);
    parameters.borderStart = QColor(255, 0, 0, 160);
    parameters.borderEnd = QColor(0, 0, 255, 192);
    parameters.borderGradientAngle = 213.0;

    const auto mesh = HyprlandSurfaceMesh::build(parameters);
    const qreal fringe = 1.0 / parameters.pixelRatio;
    for (const auto &vertex : mesh.triangles) {
        QVERIFY(vertex.position.x() >= -fringe - 1e-7);
        QVERIFY(vertex.position.y() >= -fringe - 1e-7);
        QVERIFY(vertex.position.x() <= parameters.size.width() + fringe + 1e-7);
        QVERIFY(vertex.position.y() <= parameters.size.height() + fringe + 1e-7);
        QVERIFY(vertex.coverage >= 0.0 && vertex.coverage <= 1.0);
    }
    QVERIFY(mesh.triangles.size() % 3 == 0);

    parameters.radius = 0.0;
    const auto squareInterior = HyprlandSurfaceMesh::build(parameters);
    QVERIFY(!squareInterior.triangles.isEmpty());
    QCOMPARE(squareInterior.outerBoundary.size(), squareInterior.innerBoundary.size());

    parameters.size = QSizeF(200.0, 80.0);
    parameters.borderWidth = 40.0;
    const auto fullBorder = HyprlandSurfaceMesh::build(parameters);
    QVERIFY(!fullBorder.triangles.isEmpty());
    QCOMPARE(fullBorder.outerBoundary.size(), fullBorder.innerBoundary.size());
    for (const QPointF &point : fullBorder.innerBoundary)
        QCOMPARE(point.y(), 40.0);
}

void SurfaceMeshTests::invalidScalarValuesAreSafelyClamped()
{
    HyprlandSurfaceMesh::Parameters parameters;
    parameters.size = QSizeF(200.0, 120.0);
    parameters.pixelRatio = qQNaN();
    parameters.radius = qQNaN();
    parameters.roundingPower = qQNaN();
    parameters.borderWidth = qQNaN();
    parameters.borderGradientAngle = qQNaN();
    parameters.fillColor = Qt::white;
    parameters.borderStart = Qt::red;
    parameters.borderEnd = Qt::blue;

    const auto mesh = HyprlandSurfaceMesh::build(parameters);
    QVERIFY(!mesh.triangles.isEmpty());
    for (const auto &vertex : mesh.triangles) {
        QVERIFY(std::isfinite(vertex.position.x()));
        QVERIFY(std::isfinite(vertex.position.y()));
        QVERIFY(std::isfinite(vertex.coverage));
    }
}

QTEST_APPLESS_MAIN(SurfaceMeshTests)
#include "tst_surfacemesh.moc"
