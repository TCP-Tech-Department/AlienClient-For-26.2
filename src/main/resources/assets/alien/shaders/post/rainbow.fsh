#version 330

// 26.2 后处理：彩虹描边（Shader.Rainbow）
uniform sampler2D InSampler;
uniform sampler2D HandsSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform AlienConfig {
    float Alpha;
    float Time;
    float Pad0;
    float Pad1;
};

in vec2 texCoord;

out vec4 fragColor;

float mask(vec2 uv) {
    return max(texture(InSampler, uv).a, texture(HandsSampler, uv).a);
}

float glow() {
    vec2 texel = (1.0 / InSize) * 2.0;
    float sum = 0.0;
    for (float x = -2.0; x < 2.0; x += 1.0) {
        for (float y = -2.0; y < 2.0; y += 1.0) {
            if (mask(texCoord + texel * vec2(x, y)) > 0.002) {
                sum += max(0.0, (10.0 - distance(vec2(x, y), vec2(0.0))) / 150.0);
            }
        }
    }
    return clamp(sum, 0.0, 1.0);
}

void main() {
    vec2 uv = gl_FragCoord.xy / OutSize;
    vec3 offset = -3.1416 * vec3(0.0, 0.5, 1.0);

    float g = uv.y + Time;
    vec3 col = 0.5 + 0.5 * -sin(g) * cos(g + offset);

    col.g += 0.25;
    col = 0.5 + (col * 2.0 - 1.0);
    col.gb *= vec2(0.75, 0.9);
    col = 0.125 + 0.75 * col;

    float hands = texture(HandsSampler, texCoord).a;
    float alpha = max(glow(), hands * Alpha);
    fragColor = vec4(col, alpha);
}
