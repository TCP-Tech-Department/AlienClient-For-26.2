#version 330

// 26.2 后处理：实体/手部描边（Shader.Solid）
// In      = minecraft:entity_outline（原版发光实体链产出的柔化轮廓）
// Hands   = alien:hands（Alien 自己重绘的手部剪影）
uniform sampler2D InSampler;
uniform sampler2D HandsSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform AlienConfig {
    vec3 Color;
    float Alpha;
    float Radius;
    float Divider;
    float MaxSample;
    float Quality;
    float MinAlpha;
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
    float hands = texture(HandsSampler, texCoord).a;
    float alpha = max(glow(), hands * Alpha);
    fragColor = vec4(Color, alpha);
}
