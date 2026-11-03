#version 330

// 26.2 区域模糊（BlurManager / alien:blurarea）
// 纵向 + 横向两趟，只有落在 Area[0..Count-1] 归一化矩形内的像素才模糊。
uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform BlurConfig {
    vec2 BlurDir;
    float Radius;
};

layout(std140) uniform BlurAreaConfig {
    vec4 Area[8];
    float Count;
};

in vec2 texCoord;

out vec4 fragColor;

float SCurve(float x) {
    x = x * 2.0 - 1.0;
    return -x * abs(x) * 0.5 + x + 0.5;
}

bool insideArea(vec2 uv) {
    if (Count < 0.5) {
        return true;
    }
    for (int i = 0; i < 8; i++) {
        if (float(i) >= Count) {
            break;
        }
        vec4 area = Area[i];
        if (uv.x >= area.x && uv.x <= area.z && uv.y >= area.y && uv.y <= area.w) {
            return true;
        }
    }
    return false;
}

void main() {
    vec4 original = texture(InSampler, texCoord);
    if (!insideArea(texCoord) || Radius < 0.5) {
        fragColor = original;
        return;
    }

    vec2 sampleStep = (1.0 / InSize) * BlurDir;
    float radius = max(Radius, 0.5);
    float divisor = 0.0;
    vec4 sum = vec4(0.0);
    for (float a = -radius; a <= radius; a += 1.0) {
        float weight = SCurve(1.0 - abs(a) / (radius + 1.0));
        sum += texture(InSampler, texCoord + sampleStep * a) * weight;
        divisor += weight;
    }

    fragColor = vec4((sum / max(divisor, 0.0001)).rgb, 1.0);
}
