// Plain C++ std::sort reference for the 10M quicksort benchmark.
#include <algorithm>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <vector>

int main(int argc, char** argv) {
    int dataset = 0;
    if (argc > 1) {
        char mode = argv[1][0];
        if (mode == '3') {
            dataset = 3;
        } else if (mode == '2') {
            dataset = 2;
        } else if (mode == '1') {
            dataset = 1;
        }
    }

    std::uint32_t count = 10000000;
    if (argc > 2) {
        count = static_cast<std::uint32_t>(std::strtoul(argv[2], nullptr, 10));
        if (count == 0) {
            count = 10000000;
        }
    }
    std::uint32_t repeat = 1;
    if (argc > 3) {
        repeat = static_cast<std::uint32_t>(std::strtoul(argv[3], nullptr, 10));
        if (repeat == 0) {
            repeat = 1;
        }
    }

    std::vector<std::int64_t> values(count);

    bool sorted_ok = true;
    std::uint64_t total_checksum = 0;
    for (std::uint32_t rep = 0; rep < repeat; ++rep) {
        std::uint64_t state = 0x9E3779B97F4A7C15ULL;
        for (std::uint32_t i = 0; i < count; ++i) {
            state += 0x9E3779B97F4A7C15ULL;
            std::uint64_t z = state;
            z = (z ^ (z >> 30)) * 0xBF58476D1CE4E5B9ULL;
            z = (z ^ (z >> 27)) * 0x94D049BB133111EBULL;
            z = z ^ (z >> 31);
            if (dataset == 3) {
                values[i] = static_cast<std::int64_t>(z % 10);
            } else if (dataset == 2) {
                values[i] = static_cast<std::int64_t>(z % 100000);
            } else if (dataset == 1) {
                values[i] = static_cast<std::int64_t>(z % 1000000);
            } else {
                values[i] = static_cast<std::int64_t>(z & 0x7FFFFFFFFFFFFFFFULL);
            }
        }

        std::sort(values.begin(), values.end());

        std::uint64_t checksum = 0;
        for (std::uint32_t i = 0; i < count; ++i) {
            if (i != 0 && values[i] < values[i - 1]) {
                sorted_ok = false;
            }
            checksum += static_cast<std::uint64_t>(values[i]);
        }
        total_checksum += checksum;
    }

    std::printf("sorted %s\nchecksum %llu\n", sorted_ok ? "ok" : "FAILED",
                static_cast<unsigned long long>(total_checksum));
    return 0;
}
