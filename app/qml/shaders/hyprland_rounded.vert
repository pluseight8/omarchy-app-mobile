#version 440

// Match Qt Quick's ShaderEffect rectangle vertex path. The uniform block has
// the same name, binding and member layout as the fragment stage so OpenGL
// links one shared block instead of two blocks at binding zero.
layout(location = 0) in vec4 qt_Vertex;
layout(location = 1) in vec2 qt_MultiTexCoord0;

layout(location = 0) out vec2 qt_TexCoord0;

layout(std140, binding = 0) uniform buf {
    mat4 qt_Matrix;
    float qt_Opacity;
    vec2 itemSize;
    float radius;
    float roundingPower;
    float borderWidth;
    float borderGradientAngle;
    vec4 fillColor;
    float borderStopCount;
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

void main() {
    qt_TexCoord0 = qt_MultiTexCoord0;
    gl_Position = qt_Matrix * qt_Vertex;
}
