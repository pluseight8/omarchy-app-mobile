#version 440

// Port of Hyprland's rounded-window and border coverage math to Qt's
// cross-API ShaderEffect pipeline. See THIRD_PARTY.md for the source notice.
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
    int borderStopCount;
    vec4 borderStop0;
    vec4 borderStop1;
    vec4 borderStop2;
    vec4 borderStop3;
    vec4 borderStop4;
    vec4 borderStop5;
    vec4 borderStop6;
    vec4 borderStop7;
    vec4 borderStop8;
    vec4 borderStop9;
};

const float PI = 3.14159265358979323846;
const float SMOOTHING_CONSTANT = PI / 5.34665792551;

float superellipseDistance(vec2 cornerPosition, float power) {
    // Clamp the flat-edge coordinate before pow(). Hyprland only evaluates
    // the curved corner, but fractional powers otherwise make negative
    // intermediate coordinates undefined on some mobile shader compilers.
    vec2 corner = max(cornerPosition, vec2(0.0));
    return pow(pow(corner.x, power) + pow(corner.y, power), 1.0 / power);
}

float roundedCoverage(vec2 position, vec2 size, float cornerRadius, float power) {
    if (cornerRadius <= 0.0)
        return 1.0;

    vec2 pixCoord = abs(position - size * 0.5);
    pixCoord -= size * 0.5 - cornerRadius;
    // Match Hyprland's pixel-center adjustment and smoothing band.
    pixCoord += vec2(1.0) / size;

    if (pixCoord.x + pixCoord.y <= cornerRadius)
        return 1.0;

    float distance = superellipseDistance(pixCoord, power);
    if (distance > cornerRadius + SMOOTHING_CONSTANT)
        return 0.0;

    float normalized = (distance - cornerRadius + SMOOTHING_CONSTANT)
        / (SMOOTHING_CONSTANT * 2.0);
    return 1.0 - smoothstep(0.0, 1.0, normalized);
}

float borderCoverage(vec2 position, vec2 size, float cornerRadius, float outerRadius,
                     float thickness, float power) {
    if (thickness <= 0.0)
        return 0.0;

    float additionalAlpha = 1.0;
    bool done = false;
    if (cornerRadius > 0.0) {
        vec2 centerDistance = abs(position - size * 0.5);
        vec2 pixCoord = centerDistance - (size * 0.5 - cornerRadius);
        vec2 pixCoordOuter = centerDistance - (size * 0.5 - outerRadius);
        // Keep Hyprland's pixel-center treatment. Both edge curves are
        // evaluated in the same outer-border coordinate system.
        pixCoord += vec2(1.0) / size;
        pixCoordOuter += vec2(1.0) / size;

        if (min(pixCoord.x, pixCoord.y) > 0.0) {
            float innerDistance = superellipseDistance(pixCoord, power);
            float outerDistance = superellipseDistance(pixCoordOuter, power);
            float halfThickness = thickness * 0.5;

            // This is Hyprland's getBorder() corner coverage logic, including
            // its independent inner and outer radii. Keeping the three
            // regions separate avoids the diagonal wedge produced by
            // subtracting two rounded masks.
            if (innerDistance < cornerRadius - halfThickness) {
                float normalized = (innerDistance - cornerRadius + thickness + SMOOTHING_CONSTANT)
                    / (SMOOTHING_CONSTANT * 2.0);
                additionalAlpha *= smoothstep(0.0, 1.0, normalized);
                done = true;
            } else if (min(pixCoordOuter.x, pixCoordOuter.y) > 0.0) {
                float normalized = (outerDistance - outerRadius + SMOOTHING_CONSTANT)
                    / (SMOOTHING_CONSTANT * 2.0);
                additionalAlpha *= 1.0 - smoothstep(0.0, 1.0, normalized);
                done = true;
            } else if (outerDistance < outerRadius - halfThickness) {
                additionalAlpha = 1.0;
                done = true;
            }
        }
    }

    if (!done) {
        // Hyprland uses the untransformed box coordinates on the straight
        // edges and keeps the full border alpha up to the configured width.
        float distanceToStraightEdge = min(
            min(position.y, size.y - position.y),
            min(position.x, size.x - position.x));
        if (distanceToStraightEdge > thickness)
            return 0.0;
    }

    return additionalAlpha;
}

float hyprlandGradientPosition(vec2 coordinate, float degrees) {
    float normalizedDegrees = mod(mod(degrees, 360.0) + 360.0, 360.0);
    float angle = normalizedDegrees * PI / 180.0;
    float finalAngle;

    // Port getOkColorForCoordArray1's coordinate handling so 0..360 degrees
    // retain the same orientation as Hyprland, including diagonal angles.
    if (angle > 4.71) {
        coordinate.y = 1.0 - coordinate.y;
        finalAngle = 6.28 - angle;
    } else if (angle > 3.14) {
        coordinate = vec2(1.0) - coordinate;
        finalAngle = angle - 3.14;
    } else if (angle > 1.57) {
        coordinate.x = 1.0 - coordinate.x;
        finalAngle = 3.14 - angle;
    } else {
        finalAngle = angle;
    }

    float sine = sin(finalAngle);
    return clamp(coordinate.y * sine + coordinate.x * (1.0 - sine), 0.0, 1.0);
}

vec4 gradientStop(int index) {
    if (index <= 0) return borderStop0;
    if (index == 1) return borderStop1;
    if (index == 2) return borderStop2;
    if (index == 3) return borderStop3;
    if (index == 4) return borderStop4;
    if (index == 5) return borderStop5;
    if (index == 6) return borderStop6;
    if (index == 7) return borderStop7;
    if (index == 8) return borderStop8;
    return borderStop9;
}

vec4 premultipliedSrgbToOkLabA(vec4 color) {
    float alpha = clamp(color.a, 0.0, 1.0);
    vec3 srgb = alpha > 0.000001 ? clamp(color.rgb / alpha, 0.0, 1.0) : vec3(0.0);
    vec3 linear = pow(srgb, vec3(2.2));
    float l = pow(dot(linear, vec3(0.4122214708, 0.5363325363, 0.0514459929)), 1.0 / 3.0);
    float m = pow(dot(linear, vec3(0.2119034982, 0.6806995451, 0.1073969566)), 1.0 / 3.0);
    float s = pow(dot(linear, vec3(0.0883024619, 0.2817188376, 0.6299787005)), 1.0 / 3.0);
    return vec4(
        0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
        1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
        0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
        alpha);
}

vec4 okLabAToPremultipliedSrgb(vec4 lab) {
    float l_ = lab.x + lab.y * 0.3963377774 + lab.z * 0.2158037573;
    float m_ = lab.x - lab.y * 0.1055613458 - lab.z * 0.0638541728;
    float s_ = lab.x - lab.y * 0.0894841775 - lab.z * 1.2914855480;
    float l = l_ * l_ * l_;
    float m = m_ * m_ * m_;
    float s = s_ * s_ * s_;
    vec3 linear = vec3(
        l * 4.0767416621 - m * 3.3077115913 + s * 0.2309699292,
        -l * 1.2684380046 + m * 2.6097574011 - s * 0.3413193965,
        -l * 0.0041960863 - m * 0.7034186147 + s * 1.7076147010);
    vec3 srgb = pow(clamp(linear, 0.0, 1.0), vec3(1.0 / 2.2));
    return vec4(srgb * lab.a, lab.a);
}

vec4 hyprlandGradientColor(vec2 coordinate, float degrees, int stopCount) {
    int count = clamp(stopCount, 1, 10);
    if (count == 1)
        return borderStop0;

    float progress = hyprlandGradientPosition(coordinate, degrees) * float(count - 1);
    if (progress >= float(count - 1))
        return gradientStop(count - 1);

    int lowerIndex = int(floor(progress));
    int upperIndex = min(lowerIndex + 1, count - 1);
    float fraction = progress - float(lowerIndex);
    vec4 lower = premultipliedSrgbToOkLabA(gradientStop(lowerIndex));
    vec4 upper = premultipliedSrgbToOkLabA(gradientStop(upperIndex));
    return okLabAToPremultipliedSrgb(mix(lower, upper, fraction));
}

void main() {
    vec2 size = max(itemSize, vec2(1.0));
    float power = clamp(roundingPower, 1.0, 10.0);
    float outerRadius = clamp(radius, 0.0, min(size.x, size.y) * 0.5);
    float stroke = clamp(borderWidth, 0.0, min(size.x, size.y) * 0.5);
    // CHyprBorderDecoration::draw() applies this correction before passing
    // radiusOuter to border.glsl. It matters for powers below 2 and is zero
    // for the common circular/squircle powers >= 2.
    float correctedOuterRadius = max(0.0, outerRadius - stroke * (sqrt(2.0) - 1.0)
        * max(2.0 - power, 0.0));
    vec2 pixel = qt_TexCoord0 * size;

    float innerCoverage = 0.0;
    vec2 innerSize = size - vec2(stroke * 2.0);
    if (innerSize.x > 0.0 && innerSize.y > 0.0
        && pixel.x >= stroke && pixel.y >= stroke
        && pixel.x <= size.x - stroke && pixel.y <= size.y - stroke) {
        float innerRadius = max(0.0, outerRadius - stroke);
        innerCoverage = roundedCoverage(pixel - vec2(stroke), innerSize, innerRadius, power);
    }

    float borderMask = borderCoverage(pixel, size, outerRadius, correctedOuterRadius,
                                      stroke, power);
    vec4 borderColor = hyprlandGradientColor(qt_TexCoord0, borderGradientAngle,
                                             borderStopCount);

    // QColor uniforms arrive premultiplied in ShaderEffect. Keep that form
    // when applying coverage and source-over compositing translucent borders.
    vec4 coveredBorder = borderColor * borderMask;
    vec4 coveredFill = fillColor * innerCoverage;
    vec4 color = coveredBorder + coveredFill * (1.0 - coveredBorder.a);
    color *= qt_Opacity;

    // Both masks follow the same outer superellipse, so pixels beyond it are
    // transparent and fractional edge coverage is applied only once.
    fragColor = color;
}
