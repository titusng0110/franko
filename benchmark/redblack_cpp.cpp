// C++ reference for the Franko red-black benchmark
// (benchmark/redblack_franko.fr). std::set is itself a red-black tree,
// so this is the same algorithm in hand-written C++.
//
// Same workload, mirrored program flow: insert 2,000,000 int32 keys in
// the -999999, 1000000, -999998, 999999, ... pattern, walk in order
// accumulating count + sum (mirrors the Franko audit walk), erase all
// keys in the same order, print empty + size. std::set manages its own
// nodes, so there is no pool to mimic.
//
// Build (static libstdc++/libgcc, like the Franko builds; ASan links
// dynamic since -static-libasan does not exist on every toolchain):
//   g++ -O1 -fsanitize=address -g -fno-omit-frame-pointer -static-libstdc++ -static-libgcc redblack_cpp.cpp -o redblack_cpp_O1-asan.out
//   g++ -O1 -std=c++14 -static-libstdc++ -static-libgcc redblack_cpp.cpp -o redblack_cpp_O1.out
//   g++ -O3 -std=c++14 -static-libstdc++ -static-libgcc redblack_cpp.cpp -o redblack_cpp_O3.out

#include <cstdint>
#include <cstdio>
#include <set>

static const uint32_t SIZE = 2000000;

static int32_t keyAt(int32_t i) {
    if (i % 2 == 0) {
        return -999999 + i / 2;
    }
    return 1000000 - i / 2;
}

int main() {
    std::set<int32_t> tree;

    for (uint32_t i = 0; i < SIZE; ++i) {
        tree.insert(keyAt(static_cast<int32_t>(i)));
    }

    // In-order audit walk: strictly increasing, count, sum.
    uint32_t count = 0;
    int64_t sum = 0;
    int32_t prev = -1000000;
    int bad = 0;
    for (int32_t k : tree) {
        if (k <= prev) {
            bad = 1;
        }
        prev = k;
        ++count;
        sum += k;
    }
    printf("count=\n%u\n", count);
    printf("sum=\n%lld\n", static_cast<long long>(sum));
    printf("bad=\n%d\n", bad);

    for (uint32_t i = 0; i < SIZE; ++i) {
        tree.erase(keyAt(static_cast<int32_t>(i)));
    }
    printf("empty=\n%d\n", tree.empty() ? 1 : 0);
    printf("size=\n%u\n", static_cast<unsigned>(tree.size()));
    return 0;
}
