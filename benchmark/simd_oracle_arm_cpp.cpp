// SIMD bridge oracle (ARM64): cross-checks import/franko/simd.ffi.cpp (ARM)
// against independent references (scalar models written from the documented
// NEON/FIPS semantics). DEV-ONLY: never linked into the product, never a
// Franko API. This is the ARM counterpart of simd_oracle_x86_cpp.cpp.
//
// Build (from the repo root, after extracting the ARM-only blocks):
//   python3 - <<'EOF'
//   import re
//   text = open('import/franko/simd.ffi.cpp').read()
//   markers = [(m.group(1).strip(), m.start())
//              for m in re.finditer(r'// @@([^\n]*)\n', text)]
//   markers.append(('EOF', len(text)))
//   out = []
//   for idx, (name, s) in enumerate(markers[:-1]):
//       e = markers[idx + 1][1]
//       if name == 'common' or name == 'end' or name.startswith('simd::arm::'):
//           out.append(text[s:e])
//   open('benchmark/simd_arm_only.inc', 'w').write(''.join(out))
//   EOF
//   g++-16 -O2 -std=c++23 -mcpu=apple-m3+crypto -I include \
//       benchmark/simd_oracle_arm_cpp.cpp -o /tmp/simd_oracle_arm
//   /tmp/simd_oracle_arm   # exit 0 with "0 failures" on success
//
// Use Homebrew GCC/libstdc++: Apple clang's libc++ ships a partial
// <experimental/simd> without the arithmetic operators this oracle needs.
// A second build without +crypto exercises the scalar AES/PMULL fallbacks:
//   g++-16 -O2 -std=c++23 -mcpu=apple-m3 -I include \
//       benchmark/simd_oracle_arm_cpp.cpp -o /tmp/simd_oracle_arm_fallback
//
// The bridge is pulled in by inclusion (ARM blocks only, same selection the
// Franko compiler performs); only valid inputs are fed. Counts 0..200 with
// unaligned starts are covered. Bytewise logic/arithmetic is cross-checked
// with std::experimental::simd (16-lane fixed_size_simd), the same oracle
// style as the x86 harness. AES is checked against a scalar AES round model
// (FIPS-197 S-box, ARM state layout) using random keys.

#include "FrankoRuntime.hpp"
#include "simd_arm_only.inc"

#include <experimental/simd>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <random>
#include <vector>

namespace stdx = std::experimental;
using u8x16 = stdx::fixed_size_simd<std::uint8_t, 16>;

static std::mt19937 rng(0xA5A5BEEF);
static int failures = 0;
static int checks = 0;

static void check(bool ok, const char* what, std::uint32_t n,
                  std::uint32_t s) {
    ++checks;
    if (!ok) {
        ++failures;
        std::printf("MISMATCH %s n=%u start=%u\n", what, n, s);
    }
}

static std::uint8_t rnd_byte() { return (std::uint8_t)rng(); }

// Byte-array references written from the documented semantics.
//   0 xor   1 and    2 or     3 bic      4 sat-add   5 sat-sub
//   6 max   7 min    8 add    9 sub     10 rhadd    11 abs-diff
//  12 cmpeq
static void ref_byteop(std::uint8_t* dst, const std::uint8_t* a,
                       const std::uint8_t* b, std::uint32_t n, int op) {
    std::uint32_t i = 0;
    // Simple bytewise ops go through GCC libstdc++'s data-parallel types
    // instead of a hand-written scalar loop; the saturating/min/max/branch
    // ops stay scalar because stdx has no saturating arithmetic.
    if (op <= 3 || op == 8 || op == 9) {
        for (; i + 16 <= n; i += 16) {
            u8x16 x(a + i, stdx::element_aligned_tag());
            u8x16 y(b + i, stdx::element_aligned_tag());
            u8x16 r(0);
            switch (op) {
            case 0: r = x ^ y; break;
            case 1: r = x & y; break;
            case 2: r = x | y; break;
            case 3: r = x & (~y); break;
            case 8: r = x + y; break;
            default: r = x - y; break;
            }
            for (int k = 0; k < 16; ++k) dst[i + k] = (std::uint8_t)r[k];
        }
    }
    for (; i < n; ++i) {
        unsigned x = a[i], y = b[i];
        switch (op) {
        case 0: dst[i] = (std::uint8_t)(x ^ y); break;
        case 1: dst[i] = (std::uint8_t)(x & y); break;
        case 2: dst[i] = (std::uint8_t)(x | y); break;
        case 3: dst[i] = (std::uint8_t)(x & ~y); break;
        case 4: dst[i] = (std::uint8_t)(x + y > 255 ? 255 : x + y); break;
        case 5: dst[i] = (std::uint8_t)(x > y ? x - y : 0); break;
        case 6: dst[i] = (std::uint8_t)(x > y ? x : y); break;
        case 7: dst[i] = (std::uint8_t)(x < y ? x : y); break;
        case 8: dst[i] = (std::uint8_t)(x + y); break;
        case 9: dst[i] = (std::uint8_t)(x - y); break;
        case 10: dst[i] = (std::uint8_t)((x + y + 1) >> 1); break;
        case 11: dst[i] = (std::uint8_t)(x > y ? x - y : y - x); break;
        default: dst[i] = (std::uint8_t)(x == y ? 0xFF : 0x00); break;
        }
    }
}

// AES round models: FIPS-197 S-box, standard column-major state layout
// (byte i -> state[i % 4][i / 4]), which is what AESE/AESD operate on.
// AESE = AddRoundKey, SubBytes, ShiftRows.
// AESD = AddRoundKey, InvShiftRows, InvSubBytes.
static const std::uint8_t aes_sbox[256] = {
    0x63,0x7c,0x77,0x7b,0xf2,0x6b,0x6f,0xc5,0x30,0x01,0x67,0x2b,0xfe,0xd7,0xab,0x76,
    0xca,0x82,0xc9,0x7d,0xfa,0x59,0x47,0xf0,0xad,0xd4,0xa2,0xaf,0x9c,0xa4,0x72,0xc0,
    0xb7,0xfd,0x93,0x26,0x36,0x3f,0xf7,0xcc,0x34,0xa5,0xe5,0xf1,0x71,0xd8,0x31,0x15,
    0x04,0xc7,0x23,0xc3,0x18,0x96,0x05,0x9a,0x07,0x12,0x80,0xe2,0xeb,0x27,0xb2,0x75,
    0x09,0x83,0x2c,0x1a,0x1b,0x6e,0x5a,0xa0,0x52,0x3b,0xd6,0xb3,0x29,0xe3,0x2f,0x84,
    0x53,0xd1,0x00,0xed,0x20,0xfc,0xb1,0x5b,0x6a,0xcb,0xbe,0x39,0x4a,0x4c,0x58,0xcf,
    0xd0,0xef,0xaa,0xfb,0x43,0x4d,0x33,0x85,0x45,0xf9,0x02,0x7f,0x50,0x3c,0x9f,0xa8,
    0x51,0xa3,0x40,0x8f,0x92,0x9d,0x38,0xf5,0xbc,0xb6,0xda,0x21,0x10,0xff,0xf3,0xd2,
    0xcd,0x0c,0x13,0xec,0x5f,0x97,0x44,0x17,0xc4,0xa7,0x7e,0x3d,0x64,0x5d,0x19,0x73,
    0x60,0x81,0x4f,0xdc,0x22,0x2a,0x90,0x88,0x46,0xee,0xb8,0x14,0xde,0x5e,0x0b,0xdb,
    0xe0,0x32,0x3a,0x0a,0x49,0x06,0x24,0x5c,0xc2,0xd3,0xac,0x62,0x91,0x95,0xe4,0x79,
    0xe7,0xc8,0x37,0x6d,0x8d,0xd5,0x4e,0xa9,0x6c,0x56,0xf4,0xea,0x65,0x7a,0xae,0x08,
    0xba,0x78,0x25,0x2e,0x1c,0xa6,0xb4,0xc6,0xe8,0xdd,0x74,0x1f,0x4b,0xbd,0x8b,0x8a,
    0x70,0x3e,0xb5,0x66,0x48,0x03,0xf6,0x0e,0x61,0x35,0x57,0xb9,0x86,0xc1,0x1d,0x9e,
    0xe1,0xf8,0x98,0x11,0x69,0xd9,0x8e,0x94,0x9b,0x1e,0x87,0xe9,0xce,0x55,0x28,0xdf,
    0x8c,0xa1,0x89,0x0d,0xbf,0xe6,0x42,0x68,0x41,0x99,0x2d,0x0f,0xb0,0x54,0xbb,0x16};

static const std::uint8_t aes_inv_sbox[256] = {
    0x52,0x09,0x6a,0xd5,0x30,0x36,0xa5,0x38,0xbf,0x40,0xa3,0x9e,0x81,0xf3,0xd7,0xfb,
    0x7c,0xe3,0x39,0x82,0x9b,0x2f,0xff,0x87,0x34,0x8e,0x43,0x44,0xc4,0xde,0xe9,0xcb,
    0x54,0x7b,0x94,0x32,0xa6,0xc2,0x23,0x3d,0xee,0x4c,0x95,0x0b,0x42,0xfa,0xc3,0x4e,
    0x08,0x2e,0xa1,0x66,0x28,0xd9,0x24,0xb2,0x76,0x5b,0xa2,0x49,0x6d,0x8b,0xd1,0x25,
    0x72,0xf8,0xf6,0x64,0x86,0x68,0x98,0x16,0xd4,0xa4,0x5c,0xcc,0x5d,0x65,0xb6,0x92,
    0x6c,0x70,0x48,0x50,0xfd,0xed,0xb9,0xda,0x5e,0x15,0x46,0x57,0xa7,0x8d,0x9d,0x84,
    0x90,0xd8,0xab,0x00,0x8c,0xbc,0xd3,0x0a,0xf7,0xe4,0x58,0x05,0xb8,0xb3,0x45,0x06,
    0xd0,0x2c,0x1e,0x8f,0xca,0x3f,0x0f,0x02,0xc1,0xaf,0xbd,0x03,0x01,0x13,0x8a,0x6b,
    0x3a,0x91,0x11,0x41,0x4f,0x67,0xdc,0xea,0x97,0xf2,0xcf,0xce,0xf0,0xb4,0xe6,0x73,
    0x96,0xac,0x74,0x22,0xe7,0xad,0x35,0x85,0xe2,0xf9,0x37,0xe8,0x1c,0x75,0xdf,0x6e,
    0x47,0xf1,0x1a,0x71,0x1d,0x29,0xc5,0x89,0x6f,0xb7,0x62,0x0e,0xaa,0x18,0xbe,0x1b,
    0xfc,0x56,0x3e,0x4b,0xc6,0xd2,0x79,0x20,0x9a,0xdb,0xc0,0xfe,0x78,0xcd,0x5a,0xf4,
    0x1f,0xdd,0xa8,0x33,0x88,0x07,0xc7,0x31,0xb1,0x12,0x10,0x59,0x27,0x80,0xec,0x5f,
    0x60,0x51,0x7f,0xa9,0x19,0xb5,0x4a,0x0d,0x2d,0xe5,0x7a,0x9f,0x93,0xc9,0x9c,0xef,
    0xa0,0xe0,0x3b,0x4d,0xae,0x2a,0xf5,0xb0,0xc8,0xeb,0xbb,0x3c,0x83,0x53,0x99,0x61,
    0x17,0x2b,0x04,0x7e,0xba,0x77,0xd6,0x26,0xe1,0x69,0x14,0x63,0x55,0x21,0x0c,0x7d};

static void ref_aese(std::uint8_t* dst, const std::uint8_t* in,
                     const std::uint8_t* key) {
    std::uint8_t st[16], out[16];
    for (int i = 0; i < 16; ++i) st[i] = aes_sbox[in[i] ^ key[i]];
    for (int r = 0; r < 4; ++r)
        for (int c = 0; c < 4; ++c)
            out[4 * c + r] = st[4 * ((c + r) % 4) + r];
    std::memcpy(dst, out, 16);
}

static void ref_aesd(std::uint8_t* dst, const std::uint8_t* in,
                     const std::uint8_t* key) {
    std::uint8_t st[16], out[16];
    for (int i = 0; i < 16; ++i) st[i] = aes_inv_sbox[in[i] ^ key[i]];
    for (int r = 0; r < 4; ++r)
        for (int c = 0; c < 4; ++c)
            out[4 * c + r] = st[4 * ((c + 4 - r) % 4) + r];
    std::memcpy(dst, out, 16);
}

int main() {
    setvbuf(stdout, nullptr, _IONBF, 0);
    constexpr std::uint32_t BUF = 256;
    std::vector<std::uint8_t> A(BUF), B(BUF), M(BUF), T1(BUF), T2(BUF);
    for (auto& v : A) v = rnd_byte();
    for (auto& v : B) v = rnd_byte();
    for (auto& v : M) v = rnd_byte();
    const std::uint32_t counts[] = {0, 1, 7, 8, 15, 16, 17, 24, 31, 32, 33,
                                    37, 48, 63, 64, 65, 96, 100, 128, 200};
    const std::uint32_t starts[] = {0, 1, 3, 7};

    using ByteFn = void (*)(Franko_Fat_Pointer<std::uint8_t>,
                            Franko_Fat_Pointer<std::uint8_t>,
                            std::uint32_t, std::uint32_t, std::uint32_t);

    auto run_byteop = [&](const char* name, int op, ByteFn bridge) {
        for (std::uint32_t n : counts) {
            for (std::uint32_t s : starts) {
                if (s + n > BUF) continue;
                std::memcpy(T1.data(), A.data(), BUF);
                std::memcpy(T2.data(), A.data(), BUF);
                bridge(franko_make_fat_pointer(T1.data(), BUF),
                       franko_make_fat_pointer(B.data(), BUF), s, s, n);
                ref_byteop(T2.data() + s, A.data() + s, B.data() + s, n, op);
                check(std::memcmp(T1.data(), T2.data(), BUF) == 0, name, n, s);
                // Distinct destination/source offsets.
                const std::uint32_t src = s + 5;
                if (n == 64 && src + n <= BUF) {
                    std::memcpy(T1.data(), A.data(), BUF);
                    std::memcpy(T2.data(), A.data(), BUF);
                    bridge(franko_make_fat_pointer(T1.data(), BUF),
                           franko_make_fat_pointer(B.data(), BUF), s, src, n);
                    ref_byteop(T2.data() + s, A.data() + s, B.data() + src,
                               n, op);
                    check(std::memcmp(T1.data(), T2.data(), BUF) == 0, name,
                          n, s);
                }
            }
        }
    };

    run_byteop("veorq", 0, [](auto t, auto s, auto a, auto b, auto c) {
        NS_simd::NS_arm::FUNC_veorq_u8_7243e039(t, s, a, b, c);
    });
    run_byteop("vandq", 1, [](auto t, auto s, auto a, auto b, auto c) {
        NS_simd::NS_arm::FUNC_vandq_u8_7243e039(t, s, a, b, c);
    });
    run_byteop("vorrq", 2, [](auto t, auto s, auto a, auto b, auto c) {
        NS_simd::NS_arm::FUNC_vorrq_u8_7243e039(t, s, a, b, c);
    });
    run_byteop("vbicq", 3, [](auto t, auto s, auto a, auto b, auto c) {
        NS_simd::NS_arm::FUNC_vbicq_u8_7243e039(t, s, a, b, c);
    });
    run_byteop("vqaddq", 4, [](auto t, auto s, auto a, auto b, auto c) {
        NS_simd::NS_arm::FUNC_vqaddq_u8_7243e039(t, s, a, b, c);
    });
    run_byteop("vqsubq", 5, [](auto t, auto s, auto a, auto b, auto c) {
        NS_simd::NS_arm::FUNC_vqsubq_u8_7243e039(t, s, a, b, c);
    });
    run_byteop("vmaxq", 6, [](auto t, auto s, auto a, auto b, auto c) {
        NS_simd::NS_arm::FUNC_vmaxq_u8_7243e039(t, s, a, b, c);
    });
    run_byteop("vminq", 7, [](auto t, auto s, auto a, auto b, auto c) {
        NS_simd::NS_arm::FUNC_vminq_u8_7243e039(t, s, a, b, c);
    });
    run_byteop("vaddq", 8, [](auto t, auto s, auto a, auto b, auto c) {
        NS_simd::NS_arm::FUNC_vaddq_u8_7243e039(t, s, a, b, c);
    });
    run_byteop("vsubq", 9, [](auto t, auto s, auto a, auto b, auto c) {
        NS_simd::NS_arm::FUNC_vsubq_u8_7243e039(t, s, a, b, c);
    });
    run_byteop("vrhaddq", 10, [](auto t, auto s, auto a, auto b, auto c) {
        NS_simd::NS_arm::FUNC_vrhaddq_u8_7243e039(t, s, a, b, c);
    });
    run_byteop("vabdq", 11, [](auto t, auto s, auto a, auto b, auto c) {
        NS_simd::NS_arm::FUNC_vabdq_u8_7243e039(t, s, a, b, c);
    });
    run_byteop("vceqq", 12, [](auto t, auto s, auto a, auto b, auto c) {
        NS_simd::NS_arm::FUNC_vceqq_u8_7243e039(t, s, a, b, c);
    });

    // vbslq: exact NEON select, target & mask | source & ~mask.
    for (std::uint32_t n : counts) {
        for (std::uint32_t s : starts) {
            if (s + n > BUF) continue;
            std::memcpy(T1.data(), A.data(), BUF);
            std::memcpy(T2.data(), A.data(), BUF);
            NS_simd::NS_arm::FUNC_vbslq_u8_bea41bc9(
                franko_make_fat_pointer(T1.data(), BUF),
                franko_make_fat_pointer(B.data(), BUF),
                franko_make_fat_pointer(M.data(), BUF), s, s, s, n);
            for (std::uint32_t i = 0; i < n; ++i) {
                const std::uint8_t m = M[s + i];
                T2[s + i] = (std::uint8_t)((A[s + i] & m) |
                                           (B[s + i] & ~m));
            }
            check(std::memcmp(T1.data(), T2.data(), BUF) == 0, "vbslq", n, s);
        }
    }

    // vextq: target is the low vector, source the high vector; partial
    // final chunks treat the low-side bytes outside count as zero.
    for (std::uint32_t sh : {0u, 1u, 2u, 3u, 7u, 14u, 15u, 16u, 17u, 31u}) {
        for (std::uint32_t n : counts) {
            for (std::uint32_t s : starts) {
                if (s + n > BUF) continue;
                std::memcpy(T1.data(), A.data(), BUF);
                std::memcpy(T2.data(), A.data(), BUF);
                NS_simd::NS_arm::FUNC_vextq_u8_62d3997c(
                    franko_make_fat_pointer(T1.data(), BUF),
                    franko_make_fat_pointer(B.data(), BUF), s, s, n, sh);
                if (sh >= 16) {
                    std::memset(T2.data() + s, 0, n);
                } else {
                    for (std::uint32_t i = 0; i < n; ++i) {
                        const std::uint32_t base = i - (i % 16);
                        const std::uint32_t off = (i % 16) + sh;
                        if (off < 16) {
                            const std::uint32_t idx = base + off;
                            T2[s + i] = idx < n ? A[s + idx] : 0;
                        } else {
                            T2[s + i] = B[s + base + off - 16];
                        }
                    }
                }
                check(std::memcmp(T1.data(), T2.data(), BUF) == 0, "vextq",
                      n, s);
            }
        }
    }

    // vzip1/vzip2: interleave low/high eight bytes of each chunk; a
    // partial final chunk treats out-of-range bytes as zero.
    auto ref_zip = [&](std::uint8_t* dst, const std::uint8_t* a,
                       const std::uint8_t* b, std::uint32_t n, bool high) {
        for (std::uint32_t base = 0; base < n; base += 16) {
            const std::uint32_t lim = n - base < 16 ? n - base : 16;
            for (std::uint32_t j = 0; j < lim; ++j) {
                const std::uint32_t lane = (high ? 8u : 0u) + j / 2;
                if (j % 2 == 0) {
                    dst[base + j] = lane < lim ? a[base + lane] : 0;
                } else {
                    dst[base + j] = lane < lim ? b[base + lane] : 0;
                }
            }
        }
    };
    for (std::uint32_t n : counts) {
        for (std::uint32_t s : starts) {
            if (s + n > BUF) continue;
            std::memcpy(T1.data(), A.data(), BUF);
            std::memcpy(T2.data(), A.data(), BUF);
            NS_simd::NS_arm::FUNC_vzip1q_u8_7243e039(
                franko_make_fat_pointer(T1.data(), BUF),
                franko_make_fat_pointer(B.data(), BUF), s, s, n);
            ref_zip(T2.data() + s, A.data() + s, B.data() + s, n, false);
            check(std::memcmp(T1.data(), T2.data(), BUF) == 0, "vzip1", n, s);

            std::memcpy(T1.data(), A.data(), BUF);
            std::memcpy(T2.data(), A.data(), BUF);
            NS_simd::NS_arm::FUNC_vzip2q_u8_7243e039(
                franko_make_fat_pointer(T1.data(), BUF),
                franko_make_fat_pointer(B.data(), BUF), s, s, n);
            ref_zip(T2.data() + s, A.data() + s, B.data() + s, n, true);
            check(std::memcmp(T1.data(), T2.data(), BUF) == 0, "vzip2", n, s);
        }
    }

    // vqmovun_s16: signed 16 -> saturating unsigned 8, target write-only.
    {
        std::vector<std::int16_t> S16(BUF), W16(BUF);
        for (auto& v : S16) v = (std::int16_t)rng();
        for (std::uint32_t n : counts) {
            for (std::uint32_t s : starts) {
                if (s + n > BUF) continue;
                std::memset(T1.data(), 0xAA, BUF);
                std::memset(T2.data(), 0xAA, BUF);
                NS_simd::NS_arm::FUNC_vqmovun_s16_ed547b8f(
                    franko_make_fat_pointer(T1.data(), BUF),
                    franko_make_fat_pointer(S16.data(), BUF), s, s, n);
                for (std::uint32_t i = 0; i < n; ++i) {
                    const std::int32_t v = S16[s + i];
                    T2[s + i] = (std::uint8_t)(v < 0 ? 0 : v > 255 ? 255 : v);
                }
                check(std::memcmp(T1.data(), T2.data(), BUF) == 0,
                      "vqmovun", n, s);
            }
        }
    }

    // vpaddlq_u16: pairwise widening sum, target write-only.
    {
        std::vector<std::uint16_t> S(BUF * 2);
        std::vector<std::uint32_t> W1(BUF), W2(BUF);
        for (auto& v : S) v = (std::uint16_t)rng();
        for (std::uint32_t n : counts) {
            for (std::uint32_t s : starts) {
                if (s + n > BUF) continue;
                std::fill(W1.begin(), W1.end(), 0xBEEFu);
                std::fill(W2.begin(), W2.end(), 0xBEEFu);
                NS_simd::NS_arm::FUNC_vpaddlq_u16_8c3520d3(
                    franko_make_fat_pointer(W1.data(), BUF),
                    franko_make_fat_pointer(S.data(), BUF * 2), s, s, n);
                for (std::uint32_t i = 0; i < n; ++i) {
                    W2[s + i] = (std::uint32_t)S[s + 2 * i] +
                                (std::uint32_t)S[s + 2 * i + 1];
                }
                check(std::memcmp(W1.data(), W2.data(), BUF * 4) == 0,
                      "vpaddl", n, s);
            }
        }
    }

    // vabsq_s8: -128 stays -128, target write-only.
    {
        std::vector<std::int8_t> SA(BUF);
        for (auto& v : SA) v = (std::int8_t)rng();
        for (std::uint32_t n : counts) {
            for (std::uint32_t s : starts) {
                if (s + n > BUF) continue;
                std::memset(T1.data(), 0x5A, BUF);
                std::memset(T2.data(), 0x5A, BUF);
                NS_simd::NS_arm::FUNC_vabsq_s8_6c5347dd(
                    franko_make_fat_pointer(
                        reinterpret_cast<std::int8_t*>(T1.data()), BUF),
                    franko_make_fat_pointer(SA.data(), BUF), s, s, n);
                for (std::uint32_t i = 0; i < n; ++i) {
                    const std::int32_t v = SA[s + i];
                    T2[s + i] = (std::uint8_t)(v < 0 ? (std::uint8_t)(-v)
                                                     : (std::uint8_t)v);
                }
                check(std::memcmp(T1.data(), T2.data(), BUF) == 0,
                      "vabsq", n, s);
            }
        }
    }

    // vqtbl1q_u8: 16-byte table lookup; only complete blocks are written.
    for (std::uint32_t n : counts) {
        for (std::uint32_t s : starts) {
            if (s + n > BUF) continue;
            std::memcpy(T1.data(), A.data(), BUF);
            std::memcpy(T2.data(), A.data(), BUF);
            NS_simd::NS_arm::FUNC_vqtbl1q_u8_7243e039(
                franko_make_fat_pointer(T1.data(), BUF),
                franko_make_fat_pointer(M.data(), BUF), s, s, n);
            for (std::uint32_t base = 0; base + 16 <= n; base += 16) {
                for (std::uint32_t j = 0; j < 16; ++j) {
                    const std::uint32_t ctrl = M[s + base + j];
                    T2[s + base + j] = ctrl < 16 ? A[s + base + ctrl] : 0;
                }
            }
            check(std::memcmp(T1.data(), T2.data(), BUF) == 0, "vqtbl1", n, s);
        }
    }

    // AES: compare each complete block against the scalar FIPS-197 model;
    // trailing bytes and the status code follow the documented contract.
    auto ref_aes_blocks = [&](std::uint8_t* dst, const std::uint8_t* src,
                              const std::uint8_t* key, std::uint32_t n,
                              bool decrypt) {
        for (std::uint32_t b = 0; b + 16 <= n; b += 16) {
            if (decrypt) {
                ref_aesd(dst + b, src + b, key);
            } else {
                ref_aese(dst + b, src + b, key);
            }
        }
    };
    {
#if defined(__ARM_FEATURE_CRYPTO) || defined(__ARM_FEATURE_AES)
        constexpr bool aes_hw = true;
#else
        constexpr bool aes_hw = false;
#endif
        std::vector<std::uint8_t> K(BUF), X(BUF);
        for (auto& v : K) v = rnd_byte();
        for (auto& v : X) v = rnd_byte();
        const std::uint32_t aes_counts[] = {0, 16, 24, 31, 32, 33, 48};
        for (std::uint32_t n : aes_counts) {
            for (std::uint32_t s : starts) {
                if (s + n > BUF || 16 + s > BUF) continue;
                for (int dec = 0; dec < 2; ++dec) {
                    std::memcpy(T1.data(), X.data(), BUF);
                    std::memcpy(T2.data(), X.data(), BUF);
                    const int status = dec
                        ? NS_simd::NS_arm::FUNC_vaesdq_u8_697d55d9(
                              franko_make_fat_pointer(T1.data(), BUF),
                              franko_make_fat_pointer(K.data(), BUF), s, s, n)
                        : NS_simd::NS_arm::FUNC_vaeseq_u8_697d55d9(
                              franko_make_fat_pointer(T1.data(), BUF),
                              franko_make_fat_pointer(K.data(), BUF), s, s, n);
                    if (!aes_hw) {
                        // Documented fallback: nonzero status, target
                        // untouched. count == 0 is a no-op that returns 0.
                        check(n == 0 ? status == 0 : status != 0,
                              dec ? "vaesd-unavailable" : "vaese-unavailable",
                              n, s);
                        check(std::memcmp(T1.data(), X.data(), BUF) == 0,
                              dec ? "vaesd-untouched" : "vaese-untouched", n, s);
                        continue;
                    }
                    check(status == 0, dec ? "vaesd-status" : "vaese-status",
                          n, s);
                    check(std::memcmp(T1.data(), X.data(), s) == 0,
                          dec ? "vaesd-prefix" : "vaese-prefix", n, s);
                    ref_aes_blocks(T2.data() + s, X.data() + s, K.data() + s,
                                   n, dec != 0);
                    check(std::memcmp(T1.data(), T2.data(), BUF) == 0,
                          dec ? "vaesd" : "vaese", n, s);
                    // Block independence across unaligned starts.
                    for (std::uint32_t b = 0; b + 16 <= n; b += 16) {
                        std::vector<std::uint8_t> one(16);
                        std::memcpy(one.data(), X.data() + s + b, 16);
                        const int r = dec
                            ? NS_simd::NS_arm::FUNC_vaesdq_u8_697d55d9(
                                  franko_make_fat_pointer(one.data(), 16),
                                  franko_make_fat_pointer(K.data(), BUF),
                                  0, s, 16)
                            : NS_simd::NS_arm::FUNC_vaeseq_u8_697d55d9(
                                  franko_make_fat_pointer(one.data(), 16),
                                  franko_make_fat_pointer(K.data(), BUF),
                                  0, s, 16);
                        check(r == 0 &&
                                  std::memcmp(one.data(), X.data() + s + b,
                                              16) != 0,
                              dec ? "vaesd-block" : "vaese-block", n, s);
                    }
                }
            }
        }
    }

    // CRC: scalar reflected-polynomial models (ISO and Castagnoli).
    auto ref_crc = [](std::uint32_t crc, const std::uint8_t* data,
                      std::uint32_t n, std::uint32_t poly) {
        for (std::uint32_t i = 0; i < n; ++i) {
            crc ^= data[i];
            for (int k = 0; k < 8; ++k)
                crc = (crc >> 1) ^ (poly & (0u - (crc & 1u)));
        }
        return crc;
    };
    for (std::uint32_t init : {0u, 0xFFFFFFFFu, 0x12345678u}) {
        for (std::uint32_t n : counts) {
            for (std::uint32_t s : starts) {
                if (s + n > BUF) continue;
                std::uint32_t got = NS_simd::NS_arm::FUNC_crc32b_u8_19ca3df8(
                    init, franko_make_fat_pointer(A.data(), BUF), s, n);
                std::uint32_t want = ref_crc(init, A.data() + s, n, 0xEDB88320u);
                check(got == want, "crc32b", n, s);
                got = NS_simd::NS_arm::FUNC_crc32cb_u8_19ca3df8(
                    init, franko_make_fat_pointer(A.data(), BUF), s, n);
                want = ref_crc(init, A.data() + s, n, 0x82F63B78u);
                check(got == want, "crc32cb", n, s);
            }
        }
    }

    // vmovl_u8: zero-extend bytes to uint16, target write-only.
    {
        std::vector<std::uint16_t> W1(BUF), W2(BUF);
        for (std::uint32_t n : counts) {
            for (std::uint32_t s : starts) {
                if (s + n > BUF) continue;
                std::fill(W1.begin(), W1.end(), (std::uint16_t)0xBEEF);
                std::fill(W2.begin(), W2.end(), (std::uint16_t)0xBEEF);
                NS_simd::NS_arm::FUNC_vmovl_u8_b3d94c16(
                    franko_make_fat_pointer(W1.data(), BUF),
                    franko_make_fat_pointer(A.data(), BUF), s, s, n);
                for (std::uint32_t i = 0; i < n; ++i)
                    W2[s + i] = A[s + i];
                check(std::memcmp(W1.data(), W2.data(), BUF * 2) == 0,
                      "vmovl", n, s);
            }
        }
    }

    // vcntq_u8: per-byte population count, target write-only.
    for (std::uint32_t n : counts) {
        for (std::uint32_t s : starts) {
            if (s + n > BUF) continue;
            std::memset(T1.data(), 0x5A, BUF);
            std::memset(T2.data(), 0x5A, BUF);
            NS_simd::NS_arm::FUNC_vcntq_u8_7243e039(
                franko_make_fat_pointer(T1.data(), BUF),
                franko_make_fat_pointer(A.data(), BUF), s, s, n);
            for (std::uint32_t i = 0; i < n; ++i) {
                unsigned v = A[s + i], c = 0;
                while (v != 0) { c += v & 1u; v >>= 1; }
                T2[s + i] = (std::uint8_t)c;
            }
            check(std::memcmp(T1.data(), T2.data(), BUF) == 0, "vcntq", n, s);
        }
    }

    // vmull_p64: 64x64 -> 128-bit carryless product, low word then high.
    {
        const std::uint32_t CN = 64;
        std::vector<std::uint64_t> VA(CN), VB(CN), H1(2 * 128), H2(2 * 128);
        for (std::uint32_t i = 0; i < CN; ++i) {
            VA[i] = ((std::uint64_t)rng() << 32) | rng();
            VB[i] = ((std::uint64_t)rng() << 32) | rng();
        }
        const std::uint32_t cn[] = {0, 1, 2, 3, 4, 5, 7, 8, 9, 15, 16, 17,
                                    31, 32};
        for (std::uint32_t n : cn) {
            for (std::uint32_t s : {0u, 1u, 3u}) {
                if (s + n > CN) continue;
                std::fill(H1.begin(), H1.end(), 0xABABABABABABABABull);
                std::fill(H2.begin(), H2.end(), 0xABABABABABABABABull);
                NS_simd::NS_arm::FUNC_vmull_p64_b95c3315(
                    franko_make_fat_pointer(H1.data(), 2 * 128),
                    franko_make_fat_pointer(VA.data(), CN),
                    franko_make_fat_pointer(VB.data(), CN), s, s, s, n);
                for (std::uint32_t i = 0; i < n; ++i) {
                    unsigned __int128 acc = 0;
                    for (int k = 0; k < 64; ++k)
                        if ((VA[s + i] >> k) & 1u)
                            acc ^= (unsigned __int128)VB[s + i] << k;
                    H2[s + 2 * i] = (std::uint64_t)acc;
                    H2[s + 2 * i + 1] = (std::uint64_t)(acc >> 64);
                }
                check(std::memcmp(H1.data(), H2.data(), 2 * 128 * 8) == 0,
                      "vmull", n, s);
            }
        }
    }

    // vst1q_u8: plain vector copy with a scalar tail; source untouched.
    for (std::uint32_t n : counts) {
        for (std::uint32_t s : starts) {
            if (s + n > BUF) continue;
            std::memset(T1.data(), 0xAA, BUF);
            NS_simd::NS_arm::FUNC_vst1q_u8_7243e039(
                franko_make_fat_pointer(T1.data(), BUF),
                franko_make_fat_pointer(B.data(), BUF), s, s, n);
            bool ok = std::memcmp(T1.data() + s, B.data() + s, n) == 0;
            for (std::uint32_t k = 0; ok && k < s; ++k) ok = T1[k] == 0xAA;
            for (std::uint32_t k = s + n; ok && k < BUF; ++k)
                ok = T1[k] == 0xAA;
            check(ok, "vst1q", n, s);
        }
    }

    std::printf("%d checks, %d failures\n", checks, failures);
    return failures != 0;
}
