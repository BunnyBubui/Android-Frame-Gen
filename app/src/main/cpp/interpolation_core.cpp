#include "interpolation_core.h"

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <limits>
#include <stdexcept>
#include <vector>

namespace framegen {
namespace {
inline int channel(uint32_t p, int shift) { return static_cast<int>((p >> shift) & 255U); }
inline uint8_t byte_at(uint32_t p, int shift) { return static_cast<uint8_t>((p >> shift) & 255U); }

struct Motion { int dx = 0, dy = 0; };

// Luma is cheaper than full RGB SSD; suitable for a first software reference.
inline int luma(uint32_t p) {
    return (77 * channel(p, 16) + 150 * channel(p, 8) + 29 * channel(p, 0)) >> 8;
}

Motion estimate(const uint32_t* a, const uint32_t* b, int width, int height,
                int x0, int y0, int bw, int bh, int search) {
    long long best_score = std::numeric_limits<long long>::max();
    Motion best{};
    for (int dy = -search; dy <= search; ++dy) {
        if (y0 + dy < 0 || y0 + dy + bh > height) continue;
        for (int dx = -search; dx <= search; ++dx) {
            if (x0 + dx < 0 || x0 + dx + bw > width) continue;
            long long score = 0;
            for (int y = 0; y < bh; ++y) {
                const int ra = (y0 + y) * width + x0;
                const int rb = (y0 + dy + y) * width + x0 + dx;
                for (int x = 0; x < bw; ++x) {
                    score += std::abs(luma(a[ra + x]) - luma(b[rb + x]));
                }
                if (score > best_score) break;
            }
            // Prefer smaller motion when multiple candidates match equally.
            if (score < best_score ||
                (score == best_score &&
                 dx * dx + dy * dy < best.dx * best.dx + best.dy * best.dy)) {
                best_score = score;
                best = {dx, dy};
            }
        }
    }
    return best;
}

void accumulate_bilinear(uint32_t pixel, float x, float y, int width, int height,
                         float contribution,
                         std::vector<float>& rgba, std::vector<float>& weights) {
    if (contribution <= 0.f) return;
    const int ix = static_cast<int>(std::floor(x));
    const int iy = static_cast<int>(std::floor(y));
    const float fx = x - ix;
    const float fy = y - iy;
    for (int dy = 0; dy <= 1; ++dy) {
        const int py = iy + dy;
        if (py < 0 || py >= height) continue;
        for (int dx = 0; dx <= 1; ++dx) {
            const int px = ix + dx;
            if (px < 0 || px >= width) continue;
            const float w = contribution * (dx == 0 ? 1.f - fx : fx) *
                                          (dy == 0 ? 1.f - fy : fy);
            if (w <= 0.f) continue;
            const auto at = static_cast<size_t>(py * width + px);
            weights[at] += w;
            const auto off = at * 4;
            rgba[off] += w * channel(pixel, 24);
            rgba[off + 1] += w * channel(pixel, 16);
            rgba[off + 2] += w * channel(pixel, 8);
            rgba[off + 3] += w * channel(pixel, 0);
        }
    }
}

uint32_t merge(uint32_t a, uint32_t b, float t) {
    uint32_t out = 0;
    for (int shift : {0, 8, 16, 24}) {
        const int c = static_cast<int>(std::lround((1.f-t)*byte_at(a, shift) + t*byte_at(b, shift)));
        out |= static_cast<uint32_t>(std::clamp(c, 0, 255)) << shift;
    }
    return out;
}
}  // namespace

std::vector<uint32_t> interpolate(const uint32_t* a, const uint32_t* b,
                                   int width, int height, float t,
                                   int block_size, int search_radius) {
    if (!a || !b || width <= 0 || height <= 0 || width > 1920 || height > 1080 ||
        block_size < 2 || block_size > 64 || search_radius < 0 || search_radius > 32 ||
        !std::isfinite(t)) {
        throw std::invalid_argument("Invalid interpolation parameters");
    }
    const size_t count = static_cast<size_t>(width) * static_cast<size_t>(height);
    if (t <= 0.f) return std::vector<uint32_t>(a, a + count);
    if (t >= 1.f) return std::vector<uint32_t>(b, b + count);

    std::vector<float> rgba(count * 4, 0.f);
    std::vector<float> weights(count, 0.f);
    for (int by = 0; by < height; by += block_size) {
        for (int bx = 0; bx < width; bx += block_size) {
            const int bh = std::min(block_size, height - by);
            const int bw = std::min(block_size, width - bx);
            const Motion motion = estimate(a, b, width, height, bx, by, bw, bh, search_radius);
            for (int y = by; y < by + bh; ++y) {
                for (int x = bx; x < bx + bw; ++x) {
                    const size_t src = static_cast<size_t>(y * width + x);
                    const int xb = x + motion.dx, yb = y + motion.dy;
                    // Forward-warp A and inverse-warp B toward the same halfway location.
                    const float xx = x + t * motion.dx;
                    const float yy = y + t * motion.dy;
                    accumulate_bilinear(a[src], xx, yy, width, height, 1.f - t, rgba, weights);
                    if (xb >= 0 && xb < width && yb >= 0 && yb < height) {
                        const size_t next = static_cast<size_t>(yb * width + xb);
                        accumulate_bilinear(b[next], xx, yy, width, height, t, rgba, weights);
                    }
                }
            }
        }
    }
    std::vector<uint32_t> out(count);
    for (size_t i = 0; i < count; ++i) {
        if (weights[i] <= 1e-4f) {
            // Newly uncovered pixels: conservative no-motion fallback.
            out[i] = merge(a[i], b[i], t);
        } else {
            uint32_t packed = 0;
            for (int k = 0; k < 4; ++k) {
                const int v = std::clamp(static_cast<int>(std::lround(rgba[i * 4 + k] / weights[i])), 0, 255);
                packed |= static_cast<uint32_t>(v) << ((3 - k) * 8);
            }
            out[i] = packed;
        }
    }
    return out;
}

}  // namespace framegen
