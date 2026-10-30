// C++ reference for the Franko AVL benchmark (benchmark/AVL_franko.fr).
//
// Same workload, mirrored program flow: insert 2,000,000 int32 keys into
// std::set in the -999999, 1000000, -999998, 999999, ... pattern, print
// the size, erase all keys in the same order, print empty + size.
// std::set manages its own nodes, so there is no pool to mimic - only
// the insert / verify / delete / verify flow.
//
// Build (static libstdc++/libgcc, like the Franko builds; no sanitizer,
// no jemalloc):
//   g++ -O0 -std=c++14 -static-libstdc++ -static-libgcc AVL_cpp.cpp -o AVL_cpp_O0.out
//   g++ -O3 -std=c++14 -static-libstdc++ -static-libgcc AVL_cpp.cpp -o AVL_cpp_O3.out

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
    printf("size=\n%u\n", static_cast<unsigned>(tree.size()));

    for (uint32_t i = 0; i < SIZE; ++i) {
        tree.erase(keyAt(static_cast<int32_t>(i)));
    }
    printf("empty=\n%d\n", tree.empty() ? 1 : 0);
    printf("size=\n%u\n", static_cast<unsigned>(tree.size()));
    return 0;
}
