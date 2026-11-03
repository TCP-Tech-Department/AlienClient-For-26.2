#version 330

// 26.2 后处理：飘雪描边（Shader.Snow）
uniform sampler2D InSampler;
uniform sampler2D HandsSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform AlienConfig {
    vec4 Color;
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

float snow(vec2 uv, float scale) {
    float w = 1.0 - smoothstep(0.0, 1.0, -uv.y * (scale / 10.0));
    if (w < 0.1) {
        return 0.0;
    }
    uv += Time / scale;
    uv.y += Time * 2.0 / scale;
    uv.x += sin(uv.y + Time * 0.5) / scale;
    uv *= scale;
    vec2 s = floor(uv);
    vec2 f = fract(uv);
    vec2 p = vec2(0.0);
    p = 0.5 + 0.35 * sin(11.0 * fract(sin((s + p + scale) * mat2(7, 3, 6, 5)) * 5.0)) - f;
    float d = length(p);
    float k = min(d, 3.0);
    k = smoothstep(0.0, k, sin(f.x + f.y) * 0.01);
    return k * w;
}

void main() {
    vec2 uv = (gl_FragCoord.xy * 2.0 - OutSize) / min(OutSize.x, OutSize.y);
    float c = 1.0 - smoothstep(0.3, 1.0, clamp(uv.y * 0.3 + 0.8, 0.0, 0.75));
    c += snow(uv, 10.0);
    c += snow(uv, 8.0);
    c += snow(uv, 6.0);
    c += snow(uv, 5.0);
    vec3 finalColor = vec3(c) * Color.rgb;
    float hands = texture(HandsSampler, texCoord).a;
    float alpha = max(glow(), hands * Color.a);
    fragColor = vec4(finalColor, alpha);
}
