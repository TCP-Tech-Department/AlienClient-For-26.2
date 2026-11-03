#version 330

// 26.2 后处理：流体噪声描边（Shader.Flow）
uniform sampler2D InSampler;
uniform sampler2D HandsSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform AlienConfig {
    float MixFactor;
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

float random(vec2 pos) {
    return fract(sin(dot(pos.xy, vec2(12.9898, 78.233))) * 43758.5453123);
}

float noise(vec2 pos) {
    vec2 i = floor(pos);
    vec2 f = fract(pos);
    float a = random(i + vec2(0.0, 0.0));
    float b = random(i + vec2(1.0, 0.0));
    float c = random(i + vec2(0.0, 1.0));
    float d = random(i + vec2(1.0, 1.0));
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
}

float fbm(vec2 pos) {
    float v = 0.0;
    float a = 0.5;
    vec2 shift = vec2(100.0);
    mat2 rot = mat2(cos(0.5), sin(0.5), -sin(0.5), cos(0.5));
    for (int i = 0; i < 16; i++) {
        v += a * noise(pos);
        pos = rot * pos * 2.0 + shift;
        a *= 0.5;
    }
    return v;
}

vec3 flowColor() {
    vec2 p = (gl_FragCoord.xy * 2.0 - OutSize) / min(OutSize.x, OutSize.y);
    float time2 = 3.0 * Time / 2.0;

    vec2 q = vec2(0.0);
    q.x = fbm(p + 0.00 * time2);
    q.y = fbm(p + vec2(1.0));
    vec2 r = vec2(0.0);
    r.x = fbm(p + 1.0 * q + vec2(1.7, 9.2) + 0.15 * time2);
    r.y = fbm(p + 1.0 * q + vec2(8.3, 2.8) + 0.126 * time2);
    float f = fbm(p + r);

    vec3 color = mix(
        vec3(0.101961, 0.866667, 0.319608),
        vec3(0.666667, 0.598039, 0.366667),
        clamp((f * f) * 4.0, 0.0, 1.0)
    );
    color = mix(color, vec3(0.345098, 0.066667, 0.831373), clamp(length(q), 0.0, 1.0));
    color = mix(color, vec3(0.1, -0.5, 0.1), clamp(length(r.x), 0.0, 1.0));
    return (f * f * f + 0.6 * f * f + 0.5 * f) * color;
}

void main() {
    float hands = texture(HandsSampler, texCoord).a;
    float alpha = max(glow(), hands * MixFactor);
    fragColor = vec4(flowColor(), alpha);
}
