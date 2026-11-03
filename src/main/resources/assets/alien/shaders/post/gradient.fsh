#version 330

// 26.2 后处理：四色渐变描边（Shader.Gradient）
uniform sampler2D InSampler;
uniform sampler2D HandsSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform AlienConfig {
    vec3 Rgb0;
    vec3 Rgb1;
    vec3 Rgb2;
    vec3 Rgb3;
    float Alpha;
    float Step;
    float Radius;
    float Divider;
    float MaxSample;
    float Quality;
    float Time;
};

in vec2 texCoord;

out vec4 fragColor;

float mask(vec2 uv) {
    return max(texture(InSampler, uv).a, texture(HandsSampler, uv).a);
}

float glow() {
    vec2 texel = (1.0 / InSize) * max(Radius * Quality, 0.0001);
    float sum = 0.0;
    for (float x = -Radius; x < Radius; x += 1.0) {
        for (float y = -Radius; y < Radius; y += 1.0) {
            if (mask(texCoord + texel * vec2(x, y)) > 0.002) {
                sum += Divider > 0.0 ? max(0.0, (MaxSample - distance(vec2(x, y), vec2(0.0))) / Divider) : 1.0;
            }
        }
    }
    return clamp(sum, 0.0, 1.0);
}

vec3 gradientColor() {
    vec2 fc = gl_FragCoord.xy;
    float stepValue = max(Step, 0.0001);
    float distance0 = sqrt(fc.x * fc.x + fc.y * fc.y) + Time;
    float distance1 = sqrt((fc.x - OutSize.x) * (fc.x - OutSize.x) + fc.y * fc.y) + Time;
    float distance2 = sqrt((fc.x - OutSize.y) * (fc.x - OutSize.y) + (fc.y - OutSize.y) * (fc.y - OutSize.y)) + Time;

    distance0 = sin(distance0 / stepValue + sin(fc.y / stepValue)) * 0.5 + 0.5;
    distance1 = sin(distance1 / stepValue + sin(fc.x / stepValue)) * 0.5 + 0.5;
    distance2 = sin(distance2 / stepValue + sin(fc.y / stepValue + fc.x / stepValue)) * 0.5 + 0.5;

    float ripple = sin(distance0 * 10.0 + stepValue * 10000.0) * 0.05;
    float swirl = cos((fc.x - OutSize.x * 0.5) * 0.05 + (fc.y - OutSize.y * 0.5) * 0.05 + stepValue) * 0.05;

    distance0 += ripple + swirl;
    distance1 += ripple - swirl;
    distance2 += ripple + swirl;

    float inverse = 1.0 - distance0;
    float r = Rgb0.r * distance0 + Rgb1.r * inverse + Rgb2.r * distance1 + Rgb3.r * distance2;
    float g = Rgb0.g * distance0 + Rgb1.g * inverse + Rgb2.g * distance1 + Rgb3.g * distance2;
    float b = Rgb0.b * distance0 + Rgb1.b * inverse + Rgb2.b * distance1 + Rgb3.b * distance2;
    return vec3(r, g, b);
}

void main() {
    float hands = texture(HandsSampler, texCoord).a;
    float alpha = max(glow(), hands * Alpha);
    fragColor = vec4(gradientColor(), alpha);
}
