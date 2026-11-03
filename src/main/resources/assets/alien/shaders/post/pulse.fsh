#version 330

// 26.2 后处理：脉冲描边（Shader.Pulse）
uniform sampler2D InSampler;
uniform sampler2D HandsSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform AlienConfig {
    vec3 Color;
    vec3 Color2;
    float Alpha;
    float Radius;
    float Divider;
    float MaxSample;
    float Quality;
    float MinAlpha;
    float Time;
    float Size;
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
    return clamp(sum, 0.0, 1.0) * clamp(MinAlpha, 0.0, 1.0);
}

void main() {
    float gradient = 0.5 + 0.5 * sin(Time + texCoord.x * Size);
    vec3 animated = mix(Color, Color2, clamp(gradient, 0.0, 1.0));
    float hands = texture(HandsSampler, texCoord).a;
    float alpha = max(glow(), hands * Alpha);
    fragColor = vec4(animated, alpha);
}
