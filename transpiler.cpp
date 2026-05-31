#include <iostream>
#include <cstdlib>

// Simple slab
struct Slab {
    char* base;
};

// Global slabs (like runtime)
Slab slab4, slab8, slab16, slab32, slab64, slab128;

// Initialize slabs
void init() {
    slab4.base   = (char*)malloc(2048);
    slab8.base   = (char*)malloc(4096);
    slab16.base  = (char*)malloc(8192);
    slab32.base  = (char*)malloc(16384);
    slab64.base  = (char*)malloc(32768);
    slab128.base = (char*)malloc(65536);
}

// Cleanup
void cleanup() {
    free(slab4.base);
    free(slab8.base);
    free(slab16.base);
    free(slab32.base);
    free(slab64.base);
    free(slab128.base);
}

int main() {
    init();

    // =========================
    // Transpiled program:
    // int x = 1;
    // x++;
    // cout << x;
    // =========================

    // x assigned to slab4 offset 0
    *(int*)(slab4.base + 0) = 1;

    // x++
    (*(int*)(slab4.base + 0))++;

    // print
    std::cout << *(int*)(slab4.base + 0) << std::endl;


    // =========================
    // More variables
    // int y = 10;
    // int z = 20;
    // cout << y + z;
    // =========================

    // y -> slab4 offset 4
    *(int*)(slab4.base + 4) = 10;

    // z -> slab4 offset 8
    *(int*)(slab4.base + 8) = 20;

    *(int*)(slab4.base + 12) = *(int*)(slab4.base + 4) + *(int*)(slab4.base + 8);
    std::cout << *(int*)(slab4.base + 12) << std::endl;


    // =========================
    // Struct example (fits in 16B slab)
    // =========================

    struct Pair { int a, b; };

    // Pair p -> slab16 offset 0
    *(int*)(slab16.base + 0) = 5;   // p.a
    *(int*)(slab16.base + 4) = 7;   // p.b

    std::cout 
        << *(int*)(slab16.base + 0) + 
           *(int*)(slab16.base + 4)
        << std::endl;


    cleanup();
    return 0;
}