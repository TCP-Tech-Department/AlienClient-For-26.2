#version 330

// 26.2 后处理：烟雾噪声描边（Shader.Smoke）
uniform sampler2D InSampler;
uniform sampler2D HandsSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform AlienConfig {
    vec4 First;
    vec3 Second;
    vec3 Third;
    float Alpha;
    float Radius;
    float Divider;
    float MaxSample;
    float Quality;
    float Time;
    int Octaves;
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

float random(vec2 st) {
    return fract(sin(dot(st.xy, vec2(12.9898, 78.233))) * 43758.5453123);
}

float noise(vec2 st) {
    vec2 i = floor(st);
    vec2 f = fract(st);
    float a = random(i);
    float b = random(i + vec2(1.0, 0.0));
    float c = random(i + vec2(0.0, 1.0));
    float d = random(i + vec2(1.0, 1.0));
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
}

float fbm(vec2 st) {
    float v = 0.0;
    float a = 0.5;
    vec2 shift = vec2(100.0);
    mat2 rot = mat2(cos(0.5), sin(0.5), -sin(0.5), cos(0.5));
    for (int i = 0; i < Octaves; i++) {
        v += a * noise(st);
        st = rot * st * 2.0 + shift;
        a *= 0.5;
    }
    return v;
}

vec3 smokeColor() {
    vec2 st = gl_FragCoord.xy / OutSize * 3.0;
    vec2 q = vec2(0.0);
    q.x = fbm(st);
    q.y = fbm(st + vec2(1.0));
    vec2 r = vec2(0.0);
    r.x = fbm(st + 1.0 * q + vec2(1.7, 9.2) + 0.15 * Time);
    r.y = fbm(st + 1.0 * q + vec2(8.3, 2.8) + 0.126 * Time);
    float f = fbm(st + r);
    vec3 color = First.rgb;
    color = mix(color, Second, clamp(length(q), 0.0, 1.0));
    color = mix(color, Third, clamp(length(r.x), 0.0, 1.0));
    return (f * f * f + 0.6 * f * f + 0.5 * f) * color;
}

void main() {
    float hands = texture(HandsSampler, texCoord).a;
    float alpha = max(glow(), hands * Alpha);
    fragColor = vec4(smokeColor(), alpha);
}
