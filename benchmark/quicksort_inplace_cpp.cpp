// Hand-written C++ port of the in-place Franko quicksort (10M benchmark).
#include <algorithm>
#include <chrono>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <vector>

using u32 = std::uint32_t;
using u64 = std::uint64_t;
using i64 = std::int64_t;

static constexpr u32 NETWORK_LIMIT = 8;
static constexpr u32 SMALL_SORT_LIMIT = 24;
static constexpr u32 DNF_THRESHOLD = 70;
static constexpr u32 MHAT_MIN_ITEMS = 100000;
static constexpr u32 MHAT_SAMPLE_MIN = 100;
static constexpr u32 MHAT_SAMPLE_MAX = 20000;
static constexpr u32 PROBE22_MIN_ITEMS = 6941;
static constexpr u32 PROBE16_MIN_ITEMS = 4000;
static constexpr u32 PROBE64_MIN_ITEMS = 58603;
static constexpr u32 PROBE49_MIN_ITEMS = 34364;
static constexpr u32 PROBE37_MIN_ITEMS = 19604;
static constexpr u32 PROBE29_MIN_ITEMS = 12050;

static constexpr u32 RANDOM_PIVOTS[128] = {
    0x5694C774u, 0xBCF2DC90u, 0xC22D6ECFu, 0x82DF89C3u, 0x2E5D3BB4u, 0x11754AC0u, 0x68E420EEu, 0xD2794F96u,
    0x5B22CDE9u, 0xA49A1665u, 0x59D5FEF7u, 0x596C9D24u, 0xB5AA244Fu, 0xBC390961u, 0x747F2BF2u, 0x4546145Du,
    0xBB3A57E1u, 0xFD0D1D76u, 0xE36237A5u, 0x64E4AB28u, 0x8407E185u, 0xA4462908u, 0x637D4682u, 0x1DB262CAu,
    0x0FFC45EEu, 0xA04A36C8u, 0x5A3139A4u, 0x14732B44u, 0x020EE9DFu, 0x30FA1FB5u, 0xEEB9A7A8u, 0xCA05052Fu,
    0xE66313B3u, 0x0594024Du, 0xB16A9E7Du, 0xCB06A3E8u, 0x1B72E31Au, 0x4AC319B5u, 0x550F2307u, 0x4610C2CCu,
    0x38015908u, 0xD57934ACu, 0x8E2BC2E8u, 0xF3FC112Cu, 0x21781428u, 0x903FDB8Cu, 0x03B709D8u, 0x5C6A595Fu,
    0x0917E134u, 0x496E7177u, 0x746C2637u, 0x5D2660A8u, 0x025C93DEu, 0x8BD36979u, 0x5BCFBA15u, 0x1B21A66Cu,
    0x9380C385u, 0x93955A50u, 0xBAE7A676u, 0xC954CF68u, 0x2B7A649Fu, 0x2098B166u, 0xB5786F09u, 0xB18B96F8u,
    0xAF582D70u, 0xD40BB203u, 0x9E673610u, 0x95CA46C8u, 0x5279D564u, 0x2A160FF2u, 0xC340384Bu, 0xCDA8C1C4u,
    0xEC728A93u, 0x36D973A8u, 0x950644C7u, 0x1A47EB14u, 0x251CB0BAu, 0xD1F7EC0Eu, 0xB1D9FEDDu, 0x2B7E4DF2u,
    0x0FB500C1u, 0x237B49EEu, 0x43798CCCu, 0x7FC34DBFu, 0xD2C9B7E1u, 0x4D45C92Cu, 0xC9336211u, 0x3E63582Bu,
    0x8D725577u, 0x1BFF2619u, 0x530C4B22u, 0xC3454468u, 0xD4447108u, 0x47FE1542u, 0x395548CEu, 0x5CB5A4E7u,
    0x41460FA7u, 0x135AAB36u, 0x3D8F178Du, 0xC1AAACE6u, 0x8DA93957u, 0x99178372u, 0x6C0083B2u, 0xFA32566Eu,
    0xBAAA83E8u, 0xA4D6C6EEu, 0x72440145u, 0x6CD399A0u, 0x2A24EF25u, 0x0346380Bu, 0x8ADADA48u, 0x5E4D312Eu,
    0x39B14C81u, 0xFC5266E7u, 0x07DB9769u, 0xE95E4B04u, 0xAFCD0D88u, 0xE1D42113u, 0x8DD9B701u, 0xDE39B842u,
    0x3E81243Fu, 0x5B02B41Bu, 0x391FFABDu, 0x2751AF9Fu, 0x4071A3F8u, 0xC4EC845Au, 0xD80970E2u, 0x3CBFCC9Du
};

static u32 pivot_cursor = 0;
static u32 use_dnf = 0;
static i64 sample_pivot = 0;
static u32 sample_pivot_pos = 0;

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
    }
}

// Read-only sortedness check.
static bool is_sorted(const i64* v, u32 lo, u32 hi) {
    for (u32 i = lo + 1; i <= hi; ++i) {
        if (v[i] < v[i - 1]) {
            return false;
        }
    }
    return true;
}

struct Bounds {
    u32 lt;
    u32 gt;
};

static inline u32 next_random_word() {
    u32 r = RANDOM_PIVOTS[pivot_cursor & 127];
    ++pivot_cursor;
    return r;
}

static inline u32 get_random_pivot_pos(u32 lo, u32 hi) {
    u32 count = hi - lo + 1;
    u32 offset = static_cast<u32>(
        (static_cast<u64>(next_random_word()) * count) >> 32);
    return lo + offset;
}

static __attribute__((noinline)) u32
median3_partition_lt_4k(i64* v, u32 lo, u32 hi) {
    u32 mid = lo + (hi - lo) / 2;
    if (v[lo] < v[mid]) {
        std::swap(v[lo], v[mid]);
    }
    if (v[hi] < v[lo]) {
        std::swap(v[hi], v[lo]);
    }
    if (v[lo] < v[mid]) {
        std::swap(v[lo], v[mid]);
    }
    i64 pivot = v[lo];
    u32 i = lo;
    u32 j = hi;
    while (true) {
        while (v[i] < pivot) {
            ++i;
        }
        while (pivot < v[j]) {
            --j;
        }
        if (i >= j) {
            return j;
        }
        std::swap(v[i], v[j]);
        ++i;
        --j;
    }
}

static Bounds partition_dnf_kernel(i64* v, u32 lo, u32 hi, i64 pivot) {
    u32 lt = lo;
    u32 i = lo;
    u32 gt = hi;
    while (i <= gt) {
        i64 key = v[i];
        if (key < pivot) {
            std::swap(v[lt], v[i]);
            ++lt;
            ++i;
        } else if (pivot < key) {
            std::swap(v[i], v[gt]);
            --gt;
        } else {
            ++i;
        }
    }
    return {lt, gt};
}

static u32 partition_lomuto_kernel(i64* v, u32 lo, u32 hi,
                                   u32 pivot_pos) {
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

static void insertion_sort_values(i64* v, u32 lo, u32 hi) {
    for (u32 i = lo + 1; i <= hi; ++i) {
        i64 key = v[i];
        u32 j = i;
        while (j > lo && key < v[j - 1]) {
            v[j] = v[j - 1];
            --j;
        }
        v[j] = key;
    }
}

static void sift_down_values(i64* v, u32 lo, u32 root, u32 end) {
    while (root * 2 + 1 <= end) {
        u32 child = root * 2 + 1;
        if (child + 1 <= end && v[lo + child] < v[lo + child + 1]) {
            ++child;
        }
        if (v[lo + root] < v[lo + child]) {
            std::swap(v[lo + root], v[lo + child]);
            root = child;
        } else {
            return;
        }
    }
}

static void heap_sort_values(i64* v, u32 lo, u32 n) {
    if (n < 2) {
        return;
    }
    if (n <= SMALL_SORT_LIMIT) {
        if (n <= NETWORK_LIMIT) {
            sort_network(v, lo, n);
        } else {
            insertion_sort_values(v, lo, lo + n - 1);
        }
        return;
    }
    u32 start = n / 2;
    while (start > 0) {
        --start;
        sift_down_values(v, lo, start, n - 1);
    }
    u32 end = n - 1;
    while (end > 0) {
        std::swap(v[lo], v[lo + end]);
        --end;
        sift_down_values(v, lo, 0, end);
    }
}

static void sift_down_indices(u32* order, const i64* values, u32 lo,
                              u32 root, u32 end) {
    while (root * 2 + 1 <= end) {
        u32 child = root * 2 + 1;
        if (child + 1 <= end &&
            values[order[lo + child]] < values[order[lo + child + 1]]) {
            ++child;
        }
        if (values[order[lo + root]] < values[order[lo + child]]) {
            std::swap(order[lo + root], order[lo + child]);
            root = child;
        } else {
            return;
        }
    }
}

static void heap_sort_indices(u32* order, const i64* values, u32 lo, u32 n) {
    if (n < 2) {
        return;
    }
    u32 start = n / 2;
    while (start > 0) {
        --start;
        sift_down_indices(order, values, lo, start, n - 1);
    }
    u32 end = n - 1;
    while (end > 0) {
        std::swap(order[lo], order[lo + end]);
        --end;
        sift_down_indices(order, values, lo, 0, end);
    }
}

static void insertion_sort_indices(u32* order, const i64* values,
                                   u32 lo, u32 hi) {
    for (u32 i = lo + 1; i <= hi; ++i) {
        u32 key = order[i];
        i64 key_value = values[key];
        u32 j = i;
        while (j > lo && key_value < values[order[j - 1]]) {
            order[j] = order[j - 1];
            --j;
        }
        order[j] = key;
    }
}

static u32 floor_log2(u32 n) {
    u32 r = 0;
    if (n >= 0x10000u) { n >>= 16; r += 16; }
    if (n >= 0x100u)   { n >>= 8;  r += 8; }
    if (n >= 0x10u)    { n >>= 4;  r += 4; }
    if (n >= 0x4u)     { n >>= 2;  r += 2; }
    if (n >= 0x2u)     { n >>= 1;  r += 1; }
    return r;
}

// Fixed-size randomized static probe: one random offset per equal segment.
static bool sample_has_duplicates(const i64* v, u32 count, u32 sample_size) {
    u32 sample[64];
    u32 step = count / sample_size;
    if (step == 0) {
        step = 1;
    }
    for (u32 j = 0; j < sample_size; ++j) {
        u32 block_start = j * step;
        u32 remaining = count - block_start;
        u32 span = step < remaining ? step : remaining;
        u32 r = next_random_word();
        r ^= j * 0x9E3779B9u;
        r ^= r >> 16;
        u64 scaled = static_cast<u64>(r) * static_cast<u64>(span);
        u32 offset = static_cast<u32>(scaled >> 32);
        sample[j] = block_start + offset;
    }
    for (u32 i = 1; i < sample_size; ++i) {
        u32 key = sample[i];
        i64 key_value = v[key];
        u32 j = i;
        while (j > 0 && key_value < v[sample[j - 1]]) {
            sample[j] = sample[j - 1];
            --j;
        }
        sample[j] = key;
    }
    sample_pivot_pos = sample[sample_size / 2];
    sample_pivot = v[sample_pivot_pos];
    for (u32 i = 1; i < sample_size; ++i) {
        i64 a = v[sample[i - 1]];
        i64 b = v[sample[i]];
        if (!(a < b) && !(b < a)) {
            return true;
        }
    }
    return false;
}

// Equality using only operator<.
static inline bool equivalent(i64 a, i64 b) {
    return !(a < b) && !(b < a);
}

// Estimate the average duplicate-group size from a 0.1% sample.
// Returns true when DNF is preferred, false for Lomuto. The sample uses
// RANDOM_PIVOTS for cheap stratified offsets.
static bool estimate_use_dnf(const i64* v, u32 count) {
    u32 k = count / 1000;
    if (k < MHAT_SAMPLE_MIN) {
        k = MHAT_SAMPLE_MIN;
    } else if (k > MHAT_SAMPLE_MAX) {
        k = MHAT_SAMPLE_MAX;
    }

    u32* sample = new u32[k];
    u32 step = count / k;
    if (step == 0) {
        step = 1;
    }
    for (u32 j = 0; j < k; ++j) {
        u32 block_start = j * step;
        u32 remaining = count - block_start;
        u32 span = step < remaining ? step : remaining;

        u32 r = next_random_word();
        r ^= j * 0x9E3779B9u;
        r ^= r >> 16;
        u64 scaled = static_cast<u64>(r) * static_cast<u64>(span);
        u32 offset = static_cast<u32>(scaled >> 32);
        sample[j] = block_start + offset;
    }

    heap_sort_indices(sample, v, 0, k);
    sample_pivot_pos = sample[k / 2];
    sample_pivot = v[sample_pivot_pos];

    u64 collisions = 0;
    u32 run = 1;
    for (u32 j = 1; j < k; ++j) {
        if (equivalent(v[sample[j]], v[sample[j - 1]])) {
            ++run;
        } else {
            collisions += static_cast<u64>(run) * (run - 1) / 2;
            run = 1;
        }
    }
    collisions += static_cast<u64>(run) * (run - 1) / 2;

    u64 pairs = static_cast<u64>(k) * (k - 1) / 2;
    bool result = collisions * static_cast<u64>(count) >
                  static_cast<u64>(DNF_THRESHOLD) * pairs;
    delete[] sample;
    return result;
}

static void quicksort_lt_4k_rec(i64* v, u32 lo, u32 hi, u32 depth) {
    while (lo < hi) {
        u32 count = hi - lo + 1;
        if (count <= SMALL_SORT_LIMIT) {
            if (count <= NETWORK_LIMIT) {
                sort_network(v, lo, count);
            } else {
                insertion_sort_values(v, lo, hi);
            }
            return;
        }
        if (depth == 0) {
            heap_sort_values(v, lo, count);
            return;
        }
        u32 split = median3_partition_lt_4k(v, lo, hi);
        if (split >= hi) {
            heap_sort_values(v, lo, count);
            return;
        }
        u32 left_count = split - lo + 1;
        u32 right_count = hi - split;
        if (left_count > right_count) {
            if (right_count > 0) {
                quicksort_lt_4k_rec(v, split + 1, hi, depth - 1);
            }
            hi = split;
        } else {
            if (left_count > 0) {
                quicksort_lt_4k_rec(v, lo, split, depth - 1);
            }
            lo = split + 1;
        }
        --depth;
    }
}

static void quicksort_ge_4k_dnf_rec(i64* v, u32 lo, u32 hi, u32 depth) {
    while (lo < hi) {
        u32 count = hi - lo + 1;
        if (count <= SMALL_SORT_LIMIT) {
            if (count <= NETWORK_LIMIT) {
                sort_network(v, lo, count);
            } else {
                insertion_sort_values(v, lo, hi);
            }
            return;
        }
        if (depth == 0) {
            heap_sort_values(v, lo, count);
            return;
        }
        Bounds bounds = partition_dnf_kernel(v, lo, hi,
                                           v[get_random_pivot_pos(lo, hi)]);
        u32 left_count = bounds.lt > lo ? bounds.lt - lo : 0;
        u32 right_count = bounds.gt < hi ? hi - bounds.gt : 0;
        if (left_count > right_count) {
            if (right_count > 0) {
                quicksort_ge_4k_dnf_rec(v, bounds.gt + 1, hi, depth - 1);
            }
            --depth;
            hi = bounds.lt - 1;
        } else {
            if (left_count > 0) {
                quicksort_ge_4k_dnf_rec(v, lo, bounds.lt - 1, depth - 1);
            }
            if (right_count == 0) {
                return;
            }
            --depth;
            lo = bounds.gt + 1;
        }
    }
}

static void quicksort_ge_4k_dnf(i64* v, u32 lo, u32 hi, u32 depth) {
    if (lo >= hi) {
        return;
    }
    u32 count = hi - lo + 1;
    if (count <= SMALL_SORT_LIMIT) {
        if (count <= NETWORK_LIMIT) {
            sort_network(v, lo, count);
        } else {
            insertion_sort_values(v, lo, hi);
        }
        return;
    }
    if (depth == 0) {
        heap_sort_values(v, lo, count);
        return;
    }
    Bounds bounds = partition_dnf_kernel(v, lo, hi, sample_pivot);
    u32 left_count = bounds.lt > lo ? bounds.lt - lo : 0;
    u32 right_count = bounds.gt < hi ? hi - bounds.gt : 0;
    if (left_count > 0) {
        quicksort_ge_4k_dnf_rec(v, lo, bounds.lt - 1, depth - 1);
    }
    if (right_count > 0) {
        quicksort_ge_4k_dnf_rec(v, bounds.gt + 1, hi, depth - 1);
    }
}

static void quicksort_ge_4k_lomuto_rec(i64* v, u32 lo, u32 hi, u32 depth) {
    while (lo < hi) {
        u32 count = hi - lo + 1;
        if (count <= SMALL_SORT_LIMIT) {
            if (count <= NETWORK_LIMIT) {
                sort_network(v, lo, count);
            } else {
                insertion_sort_values(v, lo, hi);
            }
            return;
        }
        if (depth == 0) {
            heap_sort_values(v, lo, count);
            return;
        }
        u32 pivot = partition_lomuto_kernel(
            v, lo, hi, get_random_pivot_pos(lo, hi));
        u32 left_count = pivot - lo;
        u32 right_count = hi - pivot;
        if (left_count > right_count) {
            if (right_count > 0) {
                quicksort_ge_4k_lomuto_rec(v, pivot + 1, hi, depth - 1);
            }
            --depth;
            hi = pivot - 1;
        } else {
            if (left_count > 0) {
                quicksort_ge_4k_lomuto_rec(v, lo, pivot - 1, depth - 1);
            }
            --depth;
            lo = pivot + 1;
        }
    }
}

static void quicksort_ge_4k_lomuto(i64* v, u32 lo, u32 hi, u32 depth) {
    if (lo >= hi) {
        return;
    }
    u32 count = hi - lo + 1;
    if (count <= SMALL_SORT_LIMIT) {
        if (count <= NETWORK_LIMIT) {
            sort_network(v, lo, count);
        } else {
            insertion_sort_values(v, lo, hi);
        }
        return;
    }
    if (depth == 0) {
        heap_sort_values(v, lo, count);
        return;
    }
    u32 pivot = partition_lomuto_kernel(v, lo, hi, sample_pivot_pos);
    u32 left_count = pivot - lo;
    u32 right_count = hi - pivot;
    if (left_count > 0) {
        quicksort_ge_4k_lomuto_rec(v, lo, pivot - 1, depth - 1);
    }
    if (right_count > 0) {
        quicksort_ge_4k_lomuto_rec(v, pivot + 1, hi, depth - 1);
    }
}

int main(int argc, char** argv) {
    int dataset = 0;
    if (argc > 1) {
        char mode = argv[1][0];
        if (mode == '5') {
            dataset = 5;
        } else if (mode == '4') {
            dataset = 4;
        } else if (mode == '3') {
            dataset = 3;
        } else if (mode == '2') {
            dataset = 2;
        } else if (mode == '1') {
            dataset = 1;
        }
    }

    u32 count = 10000000;
    if (argc > 2) {
        count = static_cast<u32>(std::strtoul(argv[2], nullptr, 10));
        if (count == 0) {
            count = 10000000;
        }
    }
    u32 repeat = 1;
    if (argc > 3) {
        repeat = static_cast<u32>(std::strtoul(argv[3], nullptr, 10));
        if (repeat == 0) {
            repeat = 1;
        }
    }

    u32 pct_mod = 1;
    if (dataset == 1) {
        pct_mod = count / 10;
    } else if (dataset == 2) {
        pct_mod = count / 100;
    } else if (dataset == 4) {
        pct_mod = count / 200;
    } else if (dataset == 5) {
        pct_mod = count / 400;
    }
    if (pct_mod == 0) {
        pct_mod = 1;
    }

    std::vector<i64> values(count);

    bool sorted_ok = true;
    u64 total_checksum = 0;
    std::chrono::nanoseconds sort_total{0};
    for (u32 rep = 0; rep < repeat; ++rep) {
        u64 state = 0x9E3779B97F4A7C15ULL;
        for (u32 i = 0; i < count; ++i) {
            state += 0x9E3779B97F4A7C15ULL;
            u64 z = state;
            z = (z ^ (z >> 30)) * 0xBF58476D1CE4E5B9ULL;
            z = (z ^ (z >> 27)) * 0x94D049BB133111EBULL;
            z = z ^ (z >> 31);
            if (dataset == 3) {
                values[i] = static_cast<i64>(z % 10);
            } else if (dataset == 1 || dataset == 2 ||
                       dataset == 4 || dataset == 5) {
                values[i] = static_cast<i64>(z % pct_mod);
            } else {
                values[i] = static_cast<i64>(z & 0x7FFFFFFFFFFFFFFFULL);
            }
        }

        const auto sort_start = std::chrono::steady_clock::now();
        bool already_sorted = is_sorted(values.data(), 0, count - 1);
        if (!already_sorted) {
            pivot_cursor = 0;
            u32 depth = 2 * floor_log2(count);
            if (count >= PROBE16_MIN_ITEMS) {
                if (count >= MHAT_MIN_ITEMS) {
                    use_dnf = estimate_use_dnf(values.data(), count) ? 1 : 0;
                } else if (count >= PROBE64_MIN_ITEMS) {
                    use_dnf = sample_has_duplicates(values.data(), count, 64) ? 1 : 0;
                } else if (count >= PROBE49_MIN_ITEMS) {
                    use_dnf = sample_has_duplicates(values.data(), count, 49) ? 1 : 0;
                } else if (count >= PROBE37_MIN_ITEMS) {
                    use_dnf = sample_has_duplicates(values.data(), count, 37) ? 1 : 0;
                } else if (count >= PROBE29_MIN_ITEMS) {
                    use_dnf = sample_has_duplicates(values.data(), count, 29) ? 1 : 0;
                } else if (count >= PROBE22_MIN_ITEMS) {
                    use_dnf = sample_has_duplicates(values.data(), count, 22) ? 1 : 0;
                } else {
                    use_dnf = sample_has_duplicates(values.data(), count, 16) ? 1 : 0;
                }
                if (use_dnf != 0) {
                    quicksort_ge_4k_dnf(values.data(), 0, count - 1, depth);
                } else {
                    quicksort_ge_4k_lomuto(values.data(), 0, count - 1, depth);
                }
            } else {
                use_dnf = 0;
                quicksort_lt_4k_rec(values.data(), 0, count - 1, depth);
            }
        }

        sort_total += std::chrono::duration_cast<std::chrono::nanoseconds>(
                          std::chrono::steady_clock::now() - sort_start);

        u64 checksum = 0;
        for (u32 i = 0; i < count; ++i) {
            if (i != 0 && values[i] < values[i - 1]) {
                sorted_ok = false;
            }
            checksum += static_cast<u64>(values[i]);
        }
        total_checksum += checksum;
    }

    const auto elapsed_ns = sort_total.count();
    std::printf("sorted %s\nchecksum %llu\n", sorted_ok ? "ok" : "FAILED",
                static_cast<unsigned long long>(total_checksum));
    std::printf("elapsed_ns %lld\n", static_cast<long long>(elapsed_ns));
    return 0;
}
