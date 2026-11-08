// Hand-written C++ port of the in-place Franko quicksort (10M benchmark).
#include <algorithm>
#include <cstdint>
#include <cstdio>
#include <vector>

using u32 = std::uint32_t;
using u64 = std::uint64_t;
using i64 = std::int64_t;

static constexpr u32 NETWORK_LIMIT = 9;

static constexpr u32 RANDOM_PIVOTS[64] = {
    0x5694C774u, 0xBCF2DC90u, 0xC22D6ECFu, 0x82DF89C3u, 0x2E5D3BB4u, 0x11754AC0u, 0x68E420EEu, 0xD2794F96u,
    0x5B22CDE9u, 0xA49A1665u, 0x59D5FEF7u, 0x596C9D24u, 0xB5AA244Fu, 0xBC390961u, 0x747F2BF2u, 0x4546145Du,
    0xBB3A57E1u, 0xFD0D1D76u, 0xE36237A5u, 0x64E4AB28u, 0x8407E185u, 0xA4462908u, 0x637D4682u, 0x1DB262CAu,
    0x0FFC45EEu, 0xA04A36C8u, 0x5A3139A4u, 0x14732B44u, 0x020EE9DFu, 0x30FA1FB5u, 0xEEB9A7A8u, 0xCA05052Fu,
    0xE66313B3u, 0x0594024Du, 0xB16A9E7Du, 0xCB06A3E8u, 0x1B72E31Au, 0x4AC319B5u, 0x550F2307u, 0x4610C2CCu,
    0x38015908u, 0xD57934ACu, 0x8E2BC2E8u, 0xF3FC112Cu, 0x21781428u, 0x903FDB8Cu, 0x03B709D8u, 0x5C6A595Fu,
    0x0917E134u, 0x496E7177u, 0x746C2637u, 0x5D2660A8u, 0x025C93DEu, 0x8BD36979u, 0x5BCFBA15u, 0x1B21A66Cu,
    0x9380C385u, 0x93955A50u, 0xBAE7A676u, 0xC954CF68u, 0x2B7A649Fu, 0x2098B166u, 0xB5786F09u, 0xB18B96F8u
};

static u32 pivot_cursor = 0;

static void sort_network(i64* v, u32 lo, u32 n) {
    if (n < 2) {
        return;
    }

    if (n == 2) {
        // layer 1
        if (v[lo + 1] < v[lo + 0]) {
            std::swap(v[lo + 0], v[lo + 1]);
        }
    } else if (n == 3) {
        // layer 1
        if (v[lo + 2] < v[lo + 0]) {
            std::swap(v[lo + 0], v[lo + 2]);
        }
        // layer 2
        if (v[lo + 1] < v[lo + 0]) {
            std::swap(v[lo + 0], v[lo + 1]);
        }
        // layer 3
        if (v[lo + 2] < v[lo + 1]) {
            std::swap(v[lo + 1], v[lo + 2]);
        }
    } else if (n == 4) {
        // layer 1
        if (v[lo + 2] < v[lo + 0]) {
            std::swap(v[lo + 0], v[lo + 2]);
        }
        if (v[lo + 3] < v[lo + 1]) {
            std::swap(v[lo + 1], v[lo + 3]);
        }
        // layer 2
        if (v[lo + 1] < v[lo + 0]) {
            std::swap(v[lo + 0], v[lo + 1]);
        }
        if (v[lo + 3] < v[lo + 2]) {
            std::swap(v[lo + 2], v[lo + 3]);
        }
        // layer 3
        if (v[lo + 2] < v[lo + 1]) {
            std::swap(v[lo + 1], v[lo + 2]);
        }
    } else if (n == 5) {
        // layer 1
        if (v[lo + 3] < v[lo + 0]) {
            std::swap(v[lo + 0], v[lo + 3]);
        }
        if (v[lo + 4] < v[lo + 1]) {
            std::swap(v[lo + 1], v[lo + 4]);
        }
        // layer 2
        if (v[lo + 2] < v[lo + 0]) {
            std::swap(v[lo + 0], v[lo + 2]);
        }
        if (v[lo + 3] < v[lo + 1]) {
            std::swap(v[lo + 1], v[lo + 3]);
        }
        // layer 3
        if (v[lo + 1] < v[lo + 0]) {
            std::swap(v[lo + 0], v[lo + 1]);
        }
        if (v[lo + 4] < v[lo + 2]) {
            std::swap(v[lo + 2], v[lo + 4]);
        }
        // layer 4
        if (v[lo + 2] < v[lo + 1]) {
            std::swap(v[lo + 1], v[lo + 2]);
        }
        if (v[lo + 4] < v[lo + 3]) {
            std::swap(v[lo + 3], v[lo + 4]);
        }
        // layer 5
        if (v[lo + 3] < v[lo + 2]) {
            std::swap(v[lo + 2], v[lo + 3]);
        }
    } else if (n == 6) {
        // layer 1
        if (v[lo + 5] < v[lo + 0]) {
            std::swap(v[lo + 0], v[lo + 5]);
        }
        if (v[lo + 3] < v[lo + 1]) {
            std::swap(v[lo + 1], v[lo + 3]);
        }
        if (v[lo + 4] < v[lo + 2]) {
            std::swap(v[lo + 2], v[lo + 4]);
        }
        // layer 2
        if (v[lo + 2] < v[lo + 1]) {
            std::swap(v[lo + 1], v[lo + 2]);
        }
        if (v[lo + 4] < v[lo + 3]) {
            std::swap(v[lo + 3], v[lo + 4]);
        }
        // layer 3
        if (v[lo + 3] < v[lo + 0]) {
            std::swap(v[lo + 0], v[lo + 3]);
        }
        if (v[lo + 5] < v[lo + 2]) {
            std::swap(v[lo + 2], v[lo + 5]);
        }
        // layer 4
        if (v[lo + 1] < v[lo + 0]) {
            std::swap(v[lo + 0], v[lo + 1]);
        }
        if (v[lo + 3] < v[lo + 2]) {
            std::swap(v[lo + 2], v[lo + 3]);
        }
        if (v[lo + 5] < v[lo + 4]) {
            std::swap(v[lo + 4], v[lo + 5]);
        }
        // layer 5
        if (v[lo + 2] < v[lo + 1]) {
            std::swap(v[lo + 1], v[lo + 2]);
        }
        if (v[lo + 4] < v[lo + 3]) {
            std::swap(v[lo + 3], v[lo + 4]);
        }
    } else if (n == 7) {
        // layer 1
        if (v[lo + 6] < v[lo + 0]) {
            std::swap(v[lo + 0], v[lo + 6]);
        }
        if (v[lo + 3] < v[lo + 2]) {
            std::swap(v[lo + 2], v[lo + 3]);
        }
        if (v[lo + 5] < v[lo + 4]) {
            std::swap(v[lo + 4], v[lo + 5]);
        }
        // layer 2
        if (v[lo + 2] < v[lo + 0]) {
            std::swap(v[lo + 0], v[lo + 2]);
        }
        if (v[lo + 4] < v[lo + 1]) {
            std::swap(v[lo + 1], v[lo + 4]);
        }
        if (v[lo + 6] < v[lo + 3]) {
            std::swap(v[lo + 3], v[lo + 6]);
        }
        // layer 3
        if (v[lo + 1] < v[lo + 0]) {
            std::swap(v[lo + 0], v[lo + 1]);
        }
        if (v[lo + 5] < v[lo + 2]) {
            std::swap(v[lo + 2], v[lo + 5]);
        }
        if (v[lo + 4] < v[lo + 3]) {
            std::swap(v[lo + 3], v[lo + 4]);
        }
        // layer 4
        if (v[lo + 2] < v[lo + 1]) {
            std::swap(v[lo + 1], v[lo + 2]);
        }
        if (v[lo + 6] < v[lo + 4]) {
            std::swap(v[lo + 4], v[lo + 6]);
        }
        // layer 5
        if (v[lo + 3] < v[lo + 2]) {
            std::swap(v[lo + 2], v[lo + 3]);
        }
        if (v[lo + 5] < v[lo + 4]) {
            std::swap(v[lo + 4], v[lo + 5]);
        }
        // layer 6
        if (v[lo + 2] < v[lo + 1]) {
            std::swap(v[lo + 1], v[lo + 2]);
        }
        if (v[lo + 4] < v[lo + 3]) {
            std::swap(v[lo + 3], v[lo + 4]);
        }
        if (v[lo + 6] < v[lo + 5]) {
            std::swap(v[lo + 5], v[lo + 6]);
        }
    } else if (n == 8) {
        // layer 1
        if (v[lo + 2] < v[lo + 0]) {
            std::swap(v[lo + 0], v[lo + 2]);
        }
        if (v[lo + 3] < v[lo + 1]) {
            std::swap(v[lo + 1], v[lo + 3]);
        }
        if (v[lo + 6] < v[lo + 4]) {
            std::swap(v[lo + 4], v[lo + 6]);
        }
        if (v[lo + 7] < v[lo + 5]) {
            std::swap(v[lo + 5], v[lo + 7]);
        }
        // layer 2
        if (v[lo + 4] < v[lo + 0]) {
            std::swap(v[lo + 0], v[lo + 4]);
        }
        if (v[lo + 5] < v[lo + 1]) {
            std::swap(v[lo + 1], v[lo + 5]);
        }
        if (v[lo + 6] < v[lo + 2]) {
            std::swap(v[lo + 2], v[lo + 6]);
        }
        if (v[lo + 7] < v[lo + 3]) {
            std::swap(v[lo + 3], v[lo + 7]);
        }
        // layer 3
        if (v[lo + 1] < v[lo + 0]) {
            std::swap(v[lo + 0], v[lo + 1]);
        }
        if (v[lo + 3] < v[lo + 2]) {
            std::swap(v[lo + 2], v[lo + 3]);
        }
        if (v[lo + 5] < v[lo + 4]) {
            std::swap(v[lo + 4], v[lo + 5]);
        }
        if (v[lo + 7] < v[lo + 6]) {
            std::swap(v[lo + 6], v[lo + 7]);
        }
        // layer 4
        if (v[lo + 4] < v[lo + 2]) {
            std::swap(v[lo + 2], v[lo + 4]);
        }
        if (v[lo + 5] < v[lo + 3]) {
            std::swap(v[lo + 3], v[lo + 5]);
        }
        // layer 5
        if (v[lo + 4] < v[lo + 1]) {
            std::swap(v[lo + 1], v[lo + 4]);
        }
        if (v[lo + 6] < v[lo + 3]) {
            std::swap(v[lo + 3], v[lo + 6]);
        }
        // layer 6
        if (v[lo + 2] < v[lo + 1]) {
            std::swap(v[lo + 1], v[lo + 2]);
        }
        if (v[lo + 4] < v[lo + 3]) {
            std::swap(v[lo + 3], v[lo + 4]);
        }
        if (v[lo + 6] < v[lo + 5]) {
            std::swap(v[lo + 5], v[lo + 6]);
        }
    } else if (n == 9) {
        // layer 1
        if (v[lo + 3] < v[lo + 0]) {
            std::swap(v[lo + 0], v[lo + 3]);
        }
        if (v[lo + 7] < v[lo + 1]) {
            std::swap(v[lo + 1], v[lo + 7]);
        }
        if (v[lo + 5] < v[lo + 2]) {
            std::swap(v[lo + 2], v[lo + 5]);
        }
        if (v[lo + 8] < v[lo + 4]) {
            std::swap(v[lo + 4], v[lo + 8]);
        }
        // layer 2
        if (v[lo + 7] < v[lo + 0]) {
            std::swap(v[lo + 0], v[lo + 7]);
        }
        if (v[lo + 4] < v[lo + 2]) {
            std::swap(v[lo + 2], v[lo + 4]);
        }
        if (v[lo + 8] < v[lo + 3]) {
            std::swap(v[lo + 3], v[lo + 8]);
        }
        if (v[lo + 6] < v[lo + 5]) {
            std::swap(v[lo + 5], v[lo + 6]);
        }
        // layer 3
        if (v[lo + 2] < v[lo + 0]) {
            std::swap(v[lo + 0], v[lo + 2]);
        }
        if (v[lo + 3] < v[lo + 1]) {
            std::swap(v[lo + 1], v[lo + 3]);
        }
        if (v[lo + 5] < v[lo + 4]) {
            std::swap(v[lo + 4], v[lo + 5]);
        }
        if (v[lo + 8] < v[lo + 7]) {
            std::swap(v[lo + 7], v[lo + 8]);
        }
        // layer 4
        if (v[lo + 4] < v[lo + 1]) {
            std::swap(v[lo + 1], v[lo + 4]);
        }
        if (v[lo + 6] < v[lo + 3]) {
            std::swap(v[lo + 3], v[lo + 6]);
        }
        if (v[lo + 7] < v[lo + 5]) {
            std::swap(v[lo + 5], v[lo + 7]);
        }
        // layer 5
        if (v[lo + 1] < v[lo + 0]) {
            std::swap(v[lo + 0], v[lo + 1]);
        }
        if (v[lo + 4] < v[lo + 2]) {
            std::swap(v[lo + 2], v[lo + 4]);
        }
        if (v[lo + 5] < v[lo + 3]) {
            std::swap(v[lo + 3], v[lo + 5]);
        }
        if (v[lo + 8] < v[lo + 6]) {
            std::swap(v[lo + 6], v[lo + 8]);
        }
        // layer 6
        if (v[lo + 3] < v[lo + 2]) {
            std::swap(v[lo + 2], v[lo + 3]);
        }
        if (v[lo + 5] < v[lo + 4]) {
            std::swap(v[lo + 4], v[lo + 5]);
        }
        if (v[lo + 7] < v[lo + 6]) {
            std::swap(v[lo + 6], v[lo + 7]);
        }
        // layer 7
        if (v[lo + 2] < v[lo + 1]) {
            std::swap(v[lo + 1], v[lo + 2]);
        }
        if (v[lo + 4] < v[lo + 3]) {
            std::swap(v[lo + 3], v[lo + 4]);
        }
        if (v[lo + 6] < v[lo + 5]) {
            std::swap(v[lo + 5], v[lo + 6]);
        }
    }
}

static bool bubble_pass(i64* v, u32 lo, u32 hi) {
    bool swapped = false;
    for (u32 i = lo; i < hi; ++i) {
        if (v[i + 1] < v[i]) {
            std::swap(v[i], v[i + 1]);
            swapped = true;
        }
    }
    return !swapped;
}

static u32 partition(i64* v, u32 lo, u32 hi) {
    u32 count = hi - lo + 1;
    u32 sample = RANDOM_PIVOTS[pivot_cursor & 63];
    u64 scaled = static_cast<u64>(sample) * static_cast<u64>(count);
    u32 pivot_pos = lo + static_cast<u32>(scaled >> 32);
    ++pivot_cursor;

    std::swap(v[pivot_pos], v[hi]);

    i64 pivot_key = v[hi];
    u32 store = lo;
    for (u32 i = lo; i < hi; ++i) {
        if (v[i] < pivot_key) {
            std::swap(v[store], v[i]);
            ++store;
        }
    }
    std::swap(v[store], v[hi]);
    return store;
}

static void quicksort(i64* v, u32 lo, u32 hi) {
    u32 count = hi - lo + 1;
    if (count <= NETWORK_LIMIT) {
        sort_network(v, lo, count);
        return;
    }

    u32 pivot = partition(v, lo, hi);
    if (pivot > lo) {
        quicksort(v, lo, pivot - 1);
    }
    if (pivot < hi) {
        quicksort(v, pivot + 1, hi);
    }
}

int main() {
    const u32 count = 10000000;
    std::vector<i64> values(count);

    u64 state = 0x9E3779B97F4A7C15ULL;
    for (u32 i = 0; i < count; ++i) {
        state += 0x9E3779B97F4A7C15ULL;
        u64 z = state;
        z = (z ^ (z >> 30)) * 0xBF58476D1CE4E5B9ULL;
        z = (z ^ (z >> 27)) * 0x94D049BB133111EBULL;
        z = z ^ (z >> 31);
        values[i] = static_cast<i64>(z & 0x7FFFFFFFFFFFFFFFULL);
    }

    pivot_cursor = 0;
    bool already_sorted = bubble_pass(values.data(), 0, count - 1);
    if (!already_sorted) {
        quicksort(values.data(), 0, count - 2);
    }

    bool sorted_ok = true;
    u64 checksum = 0;
    for (u32 i = 0; i < count; ++i) {
        if (i != 0 && values[i] < values[i - 1]) {
            sorted_ok = false;
        }
        checksum += static_cast<u64>(values[i]);
    }

    std::printf("sorted %s\nchecksum %llu\n", sorted_ok ? "ok" : "FAILED",
                static_cast<unsigned long long>(checksum));
    return 0;
}
