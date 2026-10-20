#version 440

// Rounding mask math adapted from Hyprland's rounding.glsl. See THIRD_PARTY.md.
layout(location = 0) in vec2 qt_TexCoord0;
layout(location = 0) out vec4 fragColor;

layout(std140, binding = 0) uniform buf {
    mat4 qt_Matrix;
    float qt_Opacity;
    vec2 itemSize;
    float radius;
    float roundingPower;
    float borderWidth;
    float borderGradientAngle;
    vec4 fillColor;
    vec4 borderStart;
    vec4 borderEnd;
};

const float SMOOTHING_CONSTANT = 3.14159265358979323846 / 5.34665792551;

float hyprlandCoverage(vec2 position, vec2 size, float cornerRadius, float power) {
    if (cornerRadius <= 0.0)
        return 1.0;

    vec2 pixCoord = abs(position - size * 0.5);
    pixCoord -= size * 0.5 - cornerRadius;
    // Keep the same pixel-center offset and smoothing band as Hyprland.
    pixCoord += vec2(1.0) / size;

    if (pixCoord.x + pixCoord.y <= cornerRadius)
        return 1.0;

    float distance = pow(
        pow(pixCoord.x, power) + pow(pixCoord.y, power),
        1.0 / power);
    if (distance > cornerRadius + SMOOTHING_CONSTANT)
        return 0.0;

    float normalized = (distance - cornerRadius + SMOOTHING_CONSTANT)
        / (SMOOTHING_CONSTANT * 2.0);
    return 1.0 - smoothstep(0.0, 1.0, normalized);
}

void main() {
    vec2 size = max(itemSize, vec2(1.0));
    float power = clamp(roundingPower, 1.0, 10.0);
    float outerRadius = clamp(radius, 0.0, min(size.x, size.y) * 0.5);
    float stroke = clamp(borderWidth, 0.0, min(size.x, size.y) * 0.5);

    vec2 pixel = qt_TexCoord0 * size;
    float outerCoverage = hyprlandCoverage(pixel, size, outerRadius, power);

    float innerCoverage = 0.0;
    vec2 innerSize = size - vec2(stroke * 2.0);
    if (innerSize.x > 0.0 && innerSize.y > 0.0
        && pixel.x >= stroke && pixel.y >= stroke
        && pixel.x <= size.x - stroke && pixel.y <= size.y - stroke) {
        vec2 innerPixel = pixel - vec2(stroke);
        float innerRadius = max(0.0, outerRadius - stroke);
        innerCoverage = hyprlandCoverage(innerPixel, innerSize, innerRadius, power);
    }

    float borderCoverage = max(0.0, outerCoverage - innerCoverage);
    float angle = radians(borderGradientAngle);
    float finalAngle;
    vec2 gradientCoord = qt_TexCoord0;
    if (angle > 4.71) {
        gradientCoord.y = 1.0 - gradientCoord.y;
        finalAngle = 6.28 - angle;
    } else if (angle > 3.14) {
        gradientCoord = vec2(1.0) - gradientCoord;
        finalAngle = angle - 3.14;
    } else if (angle > 1.57) {
        gradientCoord.x = 1.0 - gradientCoord.x;
        finalAngle = 3.14 - angle;
    } else {
        finalAngle = angle;
    }
    float gradientPosition = clamp(
        gradientCoord.y * sin(finalAngle) + gradientCoord.x * (1.0 - sin(finalAngle)),
        0.0,
        1.0);
    vec4 borderColor = mix(borderStart, borderEnd, gradientPosition);

    // QColor uniforms are premultiplied by Qt; coverage preserves that form.
    fragColor = (fillColor * innerCoverage + borderColor * borderCoverage) * qt_Opacity;
}
