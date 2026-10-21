#version 440

layout(location = 0) in vec2 vTexCoord;
layout(location = 0) out vec4 fragColor;

layout(std140, binding = 0) uniform buf {
    mat4 qt_Matrix;
    float qt_Opacity;
    float devicePixelRatio;
    vec2 itemSize;
    float radius;
    float roundingPower;
    float borderWidth;
    float borderGradientAngle;
    vec4 fillColor;
    int borderStopCount;
    vec4 borderStops[10];
} ubuf;

const float PI = 3.14159265358979323846;
const float SMOOTHING_CONSTANT = PI / 5.34665792551;

float superellipseDistance(vec2 corner, float power)
{
    vec2 positiveCorner = max(corner, vec2(0.0));
    return pow(pow(positiveCorner.x, power) + pow(positiveCorner.y, power), 1.0 / power);
}

float roundedCoverage(vec2 point, vec2 size, float cornerRadius, float power)
{
    if (cornerRadius <= 0.0)
        return 1.0;

    float r = min(cornerRadius, min(size.x, size.y) * 0.5);
    vec2 corner = abs(point - size * 0.5) - (size * 0.5 - r);
    if (min(corner.x, corner.y) <= 0.0)
        return 1.0;

    float distance = superellipseDistance(corner, power);
    float antialiasWidth = max(SMOOTHING_CONSTANT / max(ubuf.devicePixelRatio, 1.0),
                               fwidth(distance) * 0.5);
    return 1.0 - smoothstep(r - antialiasWidth, r + antialiasWidth, distance);
}

vec3 okLabToGamma22(vec3 lab)
{
    float l_ = lab.x + lab.y * 0.3963377774 + lab.z * 0.2158037573;
    float m_ = lab.x + lab.y * (-0.1055613458) + lab.z * (-0.0638541728);
    float s_ = lab.x + lab.y * (-0.0894841775) + lab.z * (-1.2914855480);
    float l = l_ * l_ * l_;
    float m = m_ * m_ * m_;
    float s = s_ * s_ * s_;
    vec3 linearRgb = vec3(
        l * 4.0767416621 + m * -3.3077115913 + s * 0.2309699292,
        l * -1.2684380046 + m * 2.6097574011 + s * -0.3413193965,
        l * -0.0041960863 + m * -0.7034186147 + s * 1.7076147010);
    return pow(clamp(linearRgb, 0.0, 1.0), vec3(1.0 / 2.2));
}

vec4 gradientColor(vec2 coordinate)
{
    int count = clamp(ubuf.borderStopCount, 1, 10);
    float angle = radians(mod(ubuf.borderGradientAngle, 360.0));
    float finalAngle = 0.0;
    if (angle > 4.71238898038) {
        coordinate.y = 1.0 - coordinate.y;
        finalAngle = 6.28318530718 - angle;
    } else if (angle > 3.14159265359) {
        coordinate = vec2(1.0) - coordinate;
        finalAngle = angle - 3.14159265359;
    } else if (angle > 1.57079632679) {
        coordinate.x = 1.0 - coordinate.x;
        finalAngle = 3.14159265359 - angle;
    } else {
        finalAngle = angle;
    }

    if (count == 1)
        return vec4(okLabToGamma22(ubuf.borderStops[0].rgb), ubuf.borderStops[0].a);

    float sine = sin(finalAngle);
    float progress = (coordinate.y * sine + coordinate.x * (1.0 - sine)) * float(count - 1);
    float boundedProgress = clamp(progress, 0.0, float(count - 1));
    int lowerIndex = int(floor(boundedProgress));
    int upperIndex = min(lowerIndex + 1, count - 1);
    vec4 lower = ubuf.borderStops[lowerIndex];
    vec4 upper = ubuf.borderStops[upperIndex];
    vec4 lab = mix(lower, upper, boundedProgress - float(lowerIndex));
    return vec4(okLabToGamma22(lab.rgb), lab.a);
}

void main()
{
    vec2 size = max(ubuf.itemSize, vec2(0.0));
    vec2 position = vTexCoord * size;
    float stroke = min(max(ubuf.borderWidth, 0.0), min(size.x, size.y) * 0.5);
    float outerRadiusCorrection = stroke * (sqrt(2.0) - 1.0)
            * max(2.0 - ubuf.roundingPower, 0.0);
    float outerRadius = ubuf.radius + stroke - outerRadiusCorrection;
    float outerCoverage = roundedCoverage(position, size, outerRadius, ubuf.roundingPower);

    float innerCoverage = outerCoverage;
    if (stroke > 0.0) {
        float inset = stroke;
        vec2 innerSize = max(size - vec2(2.0 * inset), vec2(0.0));
        if (innerSize.x <= 0.0 || innerSize.y <= 0.0) {
            innerCoverage = 0.0;
        } else {
            innerCoverage = roundedCoverage(position - vec2(inset), innerSize,
                                            ubuf.radius, ubuf.roundingPower);
        }
    }

    float borderCoverage = max(outerCoverage - innerCoverage, 0.0);
    vec4 border = gradientColor(vTexCoord);
    float fillAlpha = innerCoverage * ubuf.fillColor.a;
    float borderAlpha = borderCoverage * border.a;
    float alpha = borderAlpha + fillAlpha * (1.0 - borderAlpha);
    vec3 premultipliedRgb = border.rgb * borderAlpha
            + ubuf.fillColor.rgb * fillAlpha * (1.0 - borderAlpha);
    fragColor = vec4(premultipliedRgb * ubuf.qt_Opacity, alpha * ubuf.qt_Opacity);
}
