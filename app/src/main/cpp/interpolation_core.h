#pragma once

#include <cstdint>
#include <vector>

namespace framegen {

/**
 * Educational CPU baseline for x2 interpolation, NOT a real-time GPU implementation.
 * Input/output: packed 0xAARRGGBB. Motion vectors use per-block SAD search.
 * t=0 returns A and t=1 returns B, including alpha.
 */
std::vector<uint32_t> interpolate(
    const uint32_t* a, const uint32_t* b, int width, int height,
    float t, int block_size = 8, int search_radius = 8);

}  // namespace framegen
