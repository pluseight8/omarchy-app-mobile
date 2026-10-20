#include "../HyprlandGeometry.h"

#include <QtTest>
#include <cmath>

class HyprlandGeometryTest final : public QObject
{
    Q_OBJECT

private slots:
    void circlePowerUsesEuclideanNorm()
    {
        const float actual = OmaApp::Geometry::superellipseDistance(3.0F, 4.0F, 2.0F);
        QVERIFY(std::abs(actual - 5.0F) < 0.001F);
    }

    void straightCornerRegionRemainsOpaque()
    {
        const float coverage = OmaApp::Geometry::roundingCoverage(4.0F, 8.0F, 20.0F, 4.0F, 0.587F);
        QCOMPARE(coverage, 1.0F);
    }

    void smoothingBandFadesOutsideEdge()
    {
        const float radius = 20.0F;
        const float power = 2.0F;
        const float smoothing = 0.587F;
        const float onEdge = OmaApp::Geometry::roundingCoverage(radius / std::sqrt(2.0F), radius / std::sqrt(2.0F), radius, power, smoothing);
        const float outside = OmaApp::Geometry::roundingCoverage(16.0F, 16.0F, radius, power, smoothing);
        QVERIFY(onEdge > 0.45F && onEdge < 0.55F);
        QCOMPARE(outside, 0.0F);
    }

    void roundingPowerChangesSilhouette()
    {
        const float circle = OmaApp::Geometry::roundingCoverage(16.0F, 16.0F, 20.0F, 2.0F, 0.587F);
        const float squircle = OmaApp::Geometry::roundingCoverage(16.0F, 16.0F, 20.0F, 4.0F, 0.587F);
        QVERIFY(circle < 0.01F);
        QVERIFY(squircle > 0.99F);
    }

    void pathIsClosedAndHandlesZeroRadius()
    {
        const QString rounded = OmaApp::Geometry::roundedRectPath(100.0F, 60.0F, 18.0F, 4.0F);
        QVERIFY(rounded.startsWith(QStringLiteral("M ")));
        QVERIFY(rounded.endsWith(QStringLiteral(" Z")));
        QVERIFY(!rounded.contains(QStringLiteral("nan"), Qt::CaseInsensitive));

        const QString square = OmaApp::Geometry::roundedRectPath(100.0F, 60.0F, 0.0F, 4.0F);
        QCOMPARE(square, QStringLiteral("M 0 0 L 100.000 0 L 100.000 60.000 L 0 60.000 Z"));
    }
};

QTEST_APPLESS_MAIN(HyprlandGeometryTest)
#include "tst_geometry.moc"
