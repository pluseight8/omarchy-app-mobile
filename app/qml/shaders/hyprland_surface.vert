#version 440

layout(location = 0) in vec2 qt_Vertex;
layout(location = 1) in vec2 qt_MultiTexCoord0;
layout(location = 0) out vec2 vTexCoord;

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

void main()
{
    gl_Position = ubuf.qt_Matrix * vec4(qt_Vertex, 0.0, 1.0);
    vTexCoord = qt_MultiTexCoord0;
}
