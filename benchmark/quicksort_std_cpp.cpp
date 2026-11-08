// Plain C++ std::sort reference for the 10M quicksort benchmark.
#include <algorithm>
#include <cstdint>
#include <cstdio>
#include <vector>

int main() {
    const std::uint32_t count = 10000000;
    std::vector<std::int64_t> values(count);

    std::uint64_t state = 0x9E3779B97F4A7C15ULL;
    for (std::uint32_t i = 0; i < count; ++i) {
        state += 0x9E3779B97F4A7C15ULL;
        std::uint64_t z = state;
        z = (z ^ (z >> 30)) * 0xBF58476D1CE4E5B9ULL;
        z = (z ^ (z >> 27)) * 0x94D049BB133111EBULL;
        z = z ^ (z >> 31);
        values[i] = static_cast<std::int64_t>(z & 0x7FFFFFFFFFFFFFFFULL);
    }

    std::sort(values.begin(), values.end());

    bool sorted_ok = true;
    std::uint64_t checksum = 0;
    for (std::uint32_t i = 0; i < count; ++i) {
        if (i != 0 && values[i] < values[i - 1]) {
            sorted_ok = false;
        }
        checksum += static_cast<std::uint64_t>(values[i]);
    }

    std::printf("sorted %s\nchecksum %llu\n", sorted_ok ? "ok" : "FAILED",
                static_cast<unsigned long long>(checksum));
    return 0;
}
