// C++ control for the Franko red-black benchmark
// (benchmark/redblack_franko.fr).
//
// Same algorithm and same storage design as the Franko program: one
// contiguous std::vector<Node> pool, 32-bit slot indices, NIL =
// 0xFFFFFFFF, a NextAllocator free list, iterative CLRS insert/delete,
// and the same in-order audit (order, count, sum, red-red,
// black-height). The only intended difference is the implementation
// language / code generator.
//
// This is the control that separates "Franko's array pool is a better
// data structure than std::set" from "Franko generates better code
// than a C++ compiler". It prints exactly the same summary as the
// Franko binary, so output equality is a direct correctness check.
//
// Build (portable and native, release and ASan):
//   g++ -O1 -fsanitize=address -g -fno-omit-frame-pointer -std=c++14 \
//       -static-libstdc++ -static-libgcc redblack_cpp_pool.cpp -o redblack_cpp_pool_O1-asan.out
//   g++ -O3 -std=c++14 -static-libstdc++ -static-libgcc \
//       redblack_cpp_pool.cpp -o redblack_cpp_pool_O3.out
//   add -march=native for the native builds.

#include <cstdint>
#include <cstdio>
#include <vector>

static constexpr uint32_t SIZE = 2000000;
static constexpr uint32_t NIL = 0xFFFFFFFFu;
static constexpr uint32_t ALLOCATED = 0xFFFFFFFEu;

struct Node {
    int32_t key = 0;
    uint8_t red = 0;
    uint32_t left = NIL;
    uint32_t right = NIL;
    uint32_t parent = NIL;
};

struct NextAllocator {
    uint32_t capacity = 0;
    uint32_t used = 0;
    uint32_t head = NIL;
    std::vector<uint32_t> next;

    int init(uint32_t c) {
        if (capacity != 0 || c == 0) {
            return -1;
        }
        next.resize(c);
        for (uint32_t i = 0; i < c; ++i) {
            next[i] = i + 1;
        }
        next[c - 1] = NIL;
        capacity = c;
        used = 0;
        head = 0;
        return 0;
    }

    void uninit() {
        capacity = 0;
        used = 0;
        head = NIL;
        std::vector<uint32_t>().swap(next);
    }

    uint32_t new_index() {
        if (capacity == 0 || head == NIL) {
            return NIL;
        }
        uint32_t idx = head;
        head = next[idx];
        next[idx] = ALLOCATED;
        ++used;
        return idx;
    }

    int free_index(uint32_t idx) {
        if (!exist(idx)) {
            return -1;
        }
        next[idx] = head;
        head = idx;
        --used;
        return 0;
    }

    bool exist(uint32_t idx) const {
        if (capacity == 0 || idx >= capacity) {
            return false;
        }
        return next[idx] == ALLOCATED;
    }
};

static std::vector<Node> nodes;
static NextAllocator pool;
static uint32_t root = NIL;
static int32_t gPrev = 0;
static uint32_t gCount = 0;
static int64_t gSum = 0;
static int32_t gBad = 0;

static int32_t keyAt(int32_t i) {
    if (i % 2 == 0) {
        return -999999 + i / 2;
    }
    return 1000000 - i / 2;
}

static uint8_t isBlack(uint32_t n) {
    if (n == NIL) {
        return 1;
    }
    if (nodes[n].red == 0) {
        return 1;
    }
    return 0;
}

static void rotateLeft(uint32_t x) {
    if (x == NIL) {
        return;
    }
    uint32_t y = nodes[x].right;
    if (y == NIL) {
        return;
    }
    nodes[x].right = nodes[y].left;
    if (nodes[y].left != NIL) {
        nodes[nodes[y].left].parent = x;
    }
    nodes[y].parent = nodes[x].parent;
    if (nodes[x].parent == NIL) {
        root = y;
    } else if (x == nodes[nodes[x].parent].left) {
        nodes[nodes[x].parent].left = y;
    } else {
        nodes[nodes[x].parent].right = y;
    }
    nodes[y].left = x;
    nodes[x].parent = y;
}

static void rotateRight(uint32_t x) {
    if (x == NIL) {
        return;
    }
    uint32_t y = nodes[x].left;
    if (y == NIL) {
        return;
    }
    nodes[x].left = nodes[y].right;
    if (nodes[y].right != NIL) {
        nodes[nodes[y].right].parent = x;
    }
    nodes[y].parent = nodes[x].parent;
    if (nodes[x].parent == NIL) {
        root = y;
    } else if (x == nodes[nodes[x].parent].right) {
        nodes[nodes[x].parent].right = y;
    } else {
        nodes[nodes[x].parent].left = y;
    }
    nodes[y].right = x;
    nodes[x].parent = y;
}

static void insertFixup(uint32_t z) {
    while (z != root && nodes[nodes[z].parent].red != 0) {
        if (nodes[z].parent == nodes[nodes[nodes[z].parent].parent].left) {
            uint32_t aunt = nodes[nodes[nodes[z].parent].parent].right;
            if (isBlack(aunt) == 0) {
                nodes[nodes[z].parent].red = 0;
                nodes[aunt].red = 0;
                nodes[nodes[nodes[z].parent].parent].red = 1;
                z = nodes[nodes[z].parent].parent;
            } else {
                if (z == nodes[nodes[z].parent].right) {
                    z = nodes[z].parent;
                    rotateLeft(z);
                }
                nodes[nodes[z].parent].red = 0;
                nodes[nodes[nodes[z].parent].parent].red = 1;
                rotateRight(nodes[nodes[z].parent].parent);
            }
        } else {
            uint32_t aunt = nodes[nodes[nodes[z].parent].parent].left;
            if (isBlack(aunt) == 0) {
                nodes[nodes[z].parent].red = 0;
                nodes[aunt].red = 0;
                nodes[nodes[nodes[z].parent].parent].red = 1;
                z = nodes[nodes[z].parent].parent;
            } else {
                if (z == nodes[nodes[z].parent].left) {
                    z = nodes[z].parent;
                    rotateRight(z);
                }
                nodes[nodes[z].parent].red = 0;
                nodes[nodes[nodes[z].parent].parent].red = 1;
                rotateLeft(nodes[nodes[z].parent].parent);
            }
        }
    }
    if (root != NIL) {
        nodes[root].red = 0;
    }
}

static void rbInsert(int32_t key) {
    uint32_t z = pool.new_index();
    nodes[z].key = key;
    nodes[z].red = 1;
    nodes[z].left = NIL;
    nodes[z].right = NIL;
    nodes[z].parent = NIL;

    uint32_t y = NIL;
    uint32_t x = root;
    while (x != NIL) {
        y = x;
        if (nodes[z].key < nodes[x].key) {
            x = nodes[x].left;
        } else {
            x = nodes[x].right;
        }
    }

    nodes[z].parent = y;
    if (y == NIL) {
        root = z;
    } else if (nodes[z].key < nodes[y].key) {
        nodes[y].left = z;
    } else {
        nodes[y].right = z;
    }

    insertFixup(z);
}

static void transplant(uint32_t u, uint32_t v) {
    uint32_t up = nodes[u].parent;
    if (up == NIL) {
        root = v;
    } else if (u == nodes[up].left) {
        nodes[up].left = v;
    } else {
        nodes[up].right = v;
    }
    if (v != NIL) {
        nodes[v].parent = up;
    }
}

static uint32_t minNode(uint32_t n) {
    while (nodes[n].left != NIL) {
        n = nodes[n].left;
    }
    return n;
}

static void deleteFixup(uint32_t x, uint32_t p) {
    while (x != root && isBlack(x) != 0) {
        if (x == nodes[p].left) {
            uint32_t w = nodes[p].right;
            uint32_t wl = NIL;
            uint32_t wr = NIL;
            if (w != NIL) {
                wl = nodes[w].left;
                wr = nodes[w].right;
            }
            if (isBlack(w) == 0) {
                nodes[w].red = 0;
                nodes[p].red = 1;
                rotateLeft(p);
                w = nodes[p].right;
                wl = NIL;
                wr = NIL;
                if (w != NIL) {
                    wl = nodes[w].left;
                    wr = nodes[w].right;
                }
            }
            if (isBlack(wl) != 0 && isBlack(wr) != 0) {
                if (w != NIL) {
                    nodes[w].red = 1;
                }
                x = p;
                p = nodes[x].parent;
            } else {
                if (isBlack(wr) != 0) {
                    nodes[wl].red = 0;
                    nodes[w].red = 1;
                    rotateRight(w);
                    w = nodes[p].right;
                    wr = nodes[w].right;
                }
                if (nodes[p].red != 0) {
                    nodes[w].red = 1;
                } else {
                    nodes[w].red = 0;
                }
                nodes[p].red = 0;
                nodes[wr].red = 0;
                rotateLeft(p);
                x = root;
            }
        } else {
            uint32_t w = nodes[p].left;
            uint32_t wl = NIL;
            uint32_t wr = NIL;
            if (w != NIL) {
                wl = nodes[w].left;
                wr = nodes[w].right;
            }
            if (isBlack(w) == 0) {
                nodes[w].red = 0;
                nodes[p].red = 1;
                rotateRight(p);
                w = nodes[p].left;
                wl = NIL;
                wr = NIL;
                if (w != NIL) {
                    wl = nodes[w].left;
                    wr = nodes[w].right;
                }
            }
            if (isBlack(wl) != 0 && isBlack(wr) != 0) {
                if (w != NIL) {
                    nodes[w].red = 1;
                }
                x = p;
                p = nodes[x].parent;
            } else {
                if (isBlack(wl) != 0) {
                    nodes[wr].red = 0;
                    nodes[w].red = 1;
                    rotateLeft(w);
                    w = nodes[p].left;
                    wl = nodes[w].left;
                }
                if (nodes[p].red != 0) {
                    nodes[w].red = 1;
                } else {
                    nodes[w].red = 0;
                }
                nodes[p].red = 0;
                nodes[wl].red = 0;
                rotateRight(p);
                x = root;
            }
        }
    }
    if (x != NIL) {
        nodes[x].red = 0;
    }
}

static void deleteNode(uint32_t z) {
    uint8_t yWasBlack = 1;
    if (nodes[z].red != 0) {
        yWasBlack = 0;
    }
    uint32_t x = NIL;
    uint32_t xParent = NIL;
    if (nodes[z].left == NIL) {
        x = nodes[z].right;
        xParent = nodes[z].parent;
        transplant(z, nodes[z].right);
        pool.free_index(z);
    } else if (nodes[z].right == NIL) {
        x = nodes[z].left;
        xParent = nodes[z].parent;
        transplant(z, nodes[z].left);
        pool.free_index(z);
    } else {
        uint32_t y = minNode(nodes[z].right);
        yWasBlack = 1;
        if (nodes[y].red != 0) {
            yWasBlack = 0;
        }
        x = nodes[y].right;
        uint32_t yParent = nodes[y].parent;
        if (yParent == z) {
            xParent = y;
        } else {
            transplant(y, nodes[y].right);
            nodes[y].right = nodes[z].right;
            nodes[nodes[z].right].parent = y;
            xParent = yParent;
        }
        transplant(z, y);
        nodes[y].left = nodes[z].left;
        nodes[nodes[z].left].parent = y;
        nodes[y].red = nodes[z].red;
        pool.free_index(z);
    }
    if (yWasBlack != 0) {
        deleteFixup(x, xParent);
    }
}

static uint32_t rbDelete(uint32_t n, int32_t key) {
    uint32_t z = n;
    while (z != NIL) {
        if (key == nodes[z].key) {
            deleteNode(z);
            return root;
        }
        if (key < nodes[z].key) {
            z = nodes[z].left;
        } else {
            z = nodes[z].right;
        }
    }
    return root;
}

static int32_t audit(uint32_t n) {
    if (n == NIL) {
        return 0;
    }
    int32_t lh = audit(nodes[n].left);
    if (nodes[n].key <= gPrev) {
        gBad = 1;
    }
    gPrev = nodes[n].key;
    ++gCount;
    gSum += static_cast<int64_t>(nodes[n].key);
    int32_t rh = audit(nodes[n].right);
    if (lh < 0 || rh < 0 || lh != rh) {
        return -1;
    }
    if (nodes[n].red != 0) {
        if (nodes[n].left != NIL && nodes[nodes[n].left].red != 0) {
            return -1;
        }
        if (nodes[n].right != NIL && nodes[nodes[n].right].red != 0) {
            return -1;
        }
        return lh;
    }
    return lh + 1;
}

int main() {
    nodes.resize(SIZE);
    if (pool.init(SIZE) != 0) {
        return 1;
    }

    for (uint32_t i = 0; i < SIZE; ++i) {
        rbInsert(keyAt(static_cast<int32_t>(i)));
    }

    gPrev = -1000000;
    gCount = 0;
    gSum = 0;
    gBad = 0;
    int32_t bh = audit(root);
    std::printf("count=\n%u\n", gCount);
    std::printf("sum=\n%lld\n", static_cast<long long>(gSum));
    std::printf("bad=\n%d\n", gBad);
    std::printf("bh=\n%d\n", bh);

    for (uint32_t i = 0; i < SIZE; ++i) {
        root = rbDelete(root, keyAt(static_cast<int32_t>(i)));
    }

    std::printf("empty=\n%d\n", root == NIL ? 1 : 0);
    std::printf("used=\n%u\n", pool.used);

    std::vector<Node>().swap(nodes);
    pool.uninit();
    return 0;
}
