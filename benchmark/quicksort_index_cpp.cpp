// Hand-written C++ port of the index-sorting Franko quicksort (10M benchmark).
#include <algorithm>
#include <cstdint>
#include <cstdio>
#include <vector>

using u32 = std::uint32_t;
using u64 = std::uint64_t;
using i64 = std::int64_t;

static constexpr u32 NETWORK_LIMIT = 5;
static constexpr u32 DNF_THRESHOLD = 30;

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

// Sort the n <= 5 index slots order[lo .. lo+n-1] by values[order[*]].
static void sort_network(u32* order, const i64* values, u32 lo, u32 n) {
    if (n < 2) {
        return;
    }

    if (n == 2) {
        // layer 1
        if (values[order[lo + 1]] < values[order[lo + 0]]) {
            std::swap(order[lo + 0], order[lo + 1]);
        }
    } else if (n == 3) {
        // layer 1
        if (values[order[lo + 2]] < values[order[lo + 0]]) {
            std::swap(order[lo + 0], order[lo + 2]);
        }
        // layer 2
        if (values[order[lo + 1]] < values[order[lo + 0]]) {
            std::swap(order[lo + 0], order[lo + 1]);
        }
        // layer 3
        if (values[order[lo + 2]] < values[order[lo + 1]]) {
            std::swap(order[lo + 1], order[lo + 2]);
        }
    } else if (n == 4) {
        // layer 1
        if (values[order[lo + 2]] < values[order[lo + 0]]) {
            std::swap(order[lo + 0], order[lo + 2]);
        }
        if (values[order[lo + 3]] < values[order[lo + 1]]) {
            std::swap(order[lo + 1], order[lo + 3]);
        }
        // layer 2
        if (values[order[lo + 1]] < values[order[lo + 0]]) {
            std::swap(order[lo + 0], order[lo + 1]);
        }
        if (values[order[lo + 3]] < values[order[lo + 2]]) {
            std::swap(order[lo + 2], order[lo + 3]);
        }
        // layer 3
        if (values[order[lo + 2]] < values[order[lo + 1]]) {
            std::swap(order[lo + 1], order[lo + 2]);
        }
    } else if (n == 5) {
        // layer 1
        if (values[order[lo + 3]] < values[order[lo + 0]]) {
            std::swap(order[lo + 0], order[lo + 3]);
        }
        if (values[order[lo + 4]] < values[order[lo + 1]]) {
            std::swap(order[lo + 1], order[lo + 4]);
        }
        // layer 2
        if (values[order[lo + 2]] < values[order[lo + 0]]) {
            std::swap(order[lo + 0], order[lo + 2]);
        }
        if (values[order[lo + 3]] < values[order[lo + 1]]) {
            std::swap(order[lo + 1], order[lo + 3]);
        }
        // layer 3
        if (values[order[lo + 1]] < values[order[lo + 0]]) {
            std::swap(order[lo + 0], order[lo + 1]);
        }
        if (values[order[lo + 4]] < values[order[lo + 2]]) {
            std::swap(order[lo + 2], order[lo + 4]);
        }
        // layer 4
        if (values[order[lo + 2]] < values[order[lo + 1]]) {
            std::swap(order[lo + 1], order[lo + 2]);
        }
        if (values[order[lo + 4]] < values[order[lo + 3]]) {
            std::swap(order[lo + 3], order[lo + 4]);
        }
        // layer 5
        if (values[order[lo + 3]] < values[order[lo + 2]]) {
            std::swap(order[lo + 2], order[lo + 3]);
        }
    }
}

// Read-only sortedness check over the index slots order[lo .. hi].
static bool is_sorted(const u32* order, const i64* values, u32 lo, u32 hi) {
    for (u32 i = lo + 1; i <= hi; ++i) {
        if (values[order[i]] < values[order[i - 1]]) {
            return false;
        }
    }
    return true;
}

struct Bounds {
    u32 lt;
    u32 gt;
};

static u32 partition_lomuto(u32* order, const i64* values, u32 lo, u32 hi) {
    u32 count = hi - lo + 1;
    u32 sample = RANDOM_PIVOTS[pivot_cursor & 127];
    u64 scaled = static_cast<u64>(sample) * static_cast<u64>(count);
    u32 pivot_pos = lo + static_cast<u32>(scaled >> 32);
    ++pivot_cursor;

    std::swap(order[pivot_pos], order[hi]);

    i64 pivot_key = values[order[hi]];
    u32 store = lo;
    for (u32 i = lo; i < hi; ++i) {
        if (values[order[i]] < pivot_key) {
            std::swap(order[store], order[i]);
            ++store;
        }
    }
    std::swap(order[store], order[hi]);
    return store;
}

// Dutch national flag partition over the index slots:
//   order[lo .. lt-1] has keys < pivot
//   order[lt .. gt]   has keys == pivot
//   order[gt+1 .. hi] has keys > pivot
// Only indices move; values is read-only. The equal block is final.
static Bounds partition_dnf(u32* order, const i64* values, u32 lo, u32 hi) {
    u32 count = hi - lo + 1;
    u32 sample = RANDOM_PIVOTS[pivot_cursor & 127];
    u64 scaled = static_cast<u64>(sample) * static_cast<u64>(count);
    u32 pivot_pos = lo + static_cast<u32>(scaled >> 32);
    ++pivot_cursor;

    i64 pivot = values[order[pivot_pos]];

    u32 lt = lo;
    u32 i = lo;
    u32 gt = hi;
    while (i <= gt) {
        i64 key = values[order[i]];
        if (key < pivot) {
            std::swap(order[lt], order[i]);
            ++lt;
            ++i;
        } else if (key > pivot) {
            std::swap(order[i], order[gt]);
            --gt;
        } else {
            ++i;
        }
    }
    return {lt, gt};
}

static void sift_down_indices(u32* sample, const i64* values, u32 root, u32 end) {
    while (root * 2 + 1 <= end) {
        u32 child = root * 2 + 1;
        if (child + 1 <= end && values[sample[child]] < values[sample[child + 1]]) {
            ++child;
        }
        if (values[sample[root]] < values[sample[child]]) {
            std::swap(sample[root], sample[child]);
            root = child;
        } else {
            return;
        }
    }
}

static void sort_sample_indices(u32* sample, const i64* values, u32 n) {
    if (n < 2) {
        return;
    }
    u32 start = n / 2;
    while (start > 0) {
        --start;
        sift_down_indices(sample, values, start, n - 1);
    }
    u32 end = n - 1;
    while (end > 0) {
        std::swap(sample[0], sample[end]);
        --end;
        sift_down_indices(sample, values, 0, end);
    }
}

// Estimate the average duplicate-group size from a 0.1% sample of the
// data keys. Returns true when DNF is preferred, false for Lomuto.
static bool estimate_use_dnf(const i64* values, u32 count) {
    u32 k = count / 1000;
    if (k < 1024) {
        k = 1024;
    }
    if (k > 65536) {
        k = 65536;
    }
    if (k > count) {
        k = count;
    }
    if (k < 2) {
        return false;
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

        u32 r = RANDOM_PIVOTS[j & 127];
        r ^= j * 0x9E3779B9u;
        r ^= r >> 16;
        u64 scaled = static_cast<u64>(r) * static_cast<u64>(span);
        u32 offset = static_cast<u32>(scaled >> 32);
        sample[j] = block_start + offset;
    }

    sort_sample_indices(sample, values, k);

    u64 collisions = 0;
    u32 run = 1;
    for (u32 j = 1; j < k; ++j) {
        if (values[sample[j]] == values[sample[j - 1]]) {
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

static void quicksort(u32* order, const i64* values, u32 lo, u32 hi) {
    if (lo >= hi) {
        return;
    }
    u32 count = hi - lo + 1;
    if (count <= NETWORK_LIMIT) {
        sort_network(order, values, lo, count);
        return;
    }

    if (use_dnf != 0) {
        Bounds bounds = partition_dnf(order, values, lo, hi);
        if (bounds.lt > lo) {
            quicksort(order, values, lo, bounds.lt - 1);
        }
        if (bounds.gt < hi) {
            quicksort(order, values, bounds.gt + 1, hi);
        }
    } else {
        u32 pivot = partition_lomuto(order, values, lo, hi);
        if (pivot > lo) {
            quicksort(order, values, lo, pivot - 1);
        }
        if (pivot < hi) {
            quicksort(order, values, pivot + 1, hi);
        }
    }
}

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

    const u32 count = 10000000;
    std::vector<i64> values(count);
    std::vector<u32> order(count);
    std::vector<i64> moved(count);

    u64 state = 0x9E3779B97F4A7C15ULL;
    for (u32 i = 0; i < count; ++i) {
        state += 0x9E3779B97F4A7C15ULL;
        u64 z = state;
        z = (z ^ (z >> 30)) * 0xBF58476D1CE4E5B9ULL;
        z = (z ^ (z >> 27)) * 0x94D049BB133111EBULL;
        z = z ^ (z >> 31);
        if (dataset == 3) {
            values[i] = static_cast<i64>(z % 10);
        } else if (dataset == 2) {
            values[i] = static_cast<i64>(z % 100000);
        } else if (dataset == 1) {
            values[i] = static_cast<i64>(z % 1000000);
        } else {
            values[i] = static_cast<i64>(z & 0x7FFFFFFFFFFFFFFFULL);
        }
        order[i] = i;
    }

    bool already_sorted = is_sorted(order.data(), values.data(), 0, count - 1);
    if (!already_sorted) {
        use_dnf = estimate_use_dnf(values.data(), count) ? 1 : 0;
        pivot_cursor = 0;
        quicksort(order.data(), values.data(), 0, count - 1);
    }

    for (u32 i = 0; i < count; ++i) {
        moved[i] = values[order[i]];
    }
    for (u32 i = 0; i < count; ++i) {
        values[i] = moved[i];
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
