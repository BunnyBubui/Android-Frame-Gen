#include "interpolation_core.h"
#include <cassert>
#include <cstdint>
#include <iostream>
#include <stdexcept>
#include <vector>

int main() {
    constexpr int w = 64, h = 48;
    std::vector<uint32_t> a(w*h, 0xFF121824U), b(w*h, 0xFF121824U);
    // Static backgrounds / identical frames should be exact.
    const auto same = framegen::interpolate(a.data(), a.data(), w, h, .5f, 8, 8);
    assert(same == a);
    for (int y=12; y<28; ++y) {
        for (int x=12; x<28; ++x) {
            a[y*w+x] = 0xFF40CD63U ^ static_cast<uint32_t>((x*3+y*17)&31);
            b[y*w+(x+4)] = a[y*w+x];
        }
    }
    assert(framegen::interpolate(a.data(), b.data(), w, h, 0.f) == a);
    assert(framegen::interpolate(a.data(), b.data(), w, h, 1.f) == b);
    const auto mid = framegen::interpolate(a.data(), b.data(), w, h, .5f, 8, 8);
    assert(mid.size() == a.size());
    int shifted = 0;
    for (int y=12; y<28; ++y) for (int x=12; x<32; ++x) {
        if (mid[y*w+x] != 0xFF121824U) ++shifted;
    }
    assert(shifted > 100);
    bool threw = false;
    try { framegen::interpolate(a.data(), b.data(), -1, h, .5f, 8, 8); }
    catch (const std::invalid_argument&) { threw = true; }
    assert(threw);
    threw = false;
    try { framegen::interpolate(a.data(), b.data(), w, h, .5f, 8, 64); }
    catch (const std::invalid_argument&) { threw = true; }
    assert(threw);
    std::cout << "PASS: identity, endpoints, motion sample and invalid input guards\n";
}
