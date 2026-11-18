// SIMD bridge oracle (x86): cross-checks import/franko/simd.ffi.cpp (x86)
// against independent references (std::experimental::simd where natural,
// scalar otherwise). DEV-ONLY: never linked into the product, never a
// Franko API. To write the ARM counterpart on a Mac, mirror this file:
// extract the simd::arm blocks instead and compare against NEON references.
//
// Build (from the repo root, after extracting the x86-only blocks):
//   python3 - <<'EOF'
//   import re
//   text = open('import/franko/simd.ffi.cpp').read()
//   markers = [(m.group(1).strip(), m.start())
//              for m in re.finditer(r'// @@([^\n]*)\n', text)]
//   markers.append(('EOF', len(text)))
//   out = []
//   for idx, (name, s) in enumerate(markers[:-1]):
//       e = markers[idx + 1][1]
//       if name == 'common' or name == 'end' or name.startswith('simd::x86::'):
//           out.append(text[s:e])
//   open('benchmark/simd_x86_only.inc', 'w').write(''.join(out))
//   EOF
//   g++ -O2 -std=c++23 -march=native -fsanitize=address -I include \
//       benchmark/simd_oracle_x86_cpp.cpp -o /tmp/simd_oracle_x86
//   /tmp/simd_oracle_x86   # exit 0 with "0 failures" on success
//
// The bridge is pulled in by inclusion (x86 blocks only, same selection the
// Franko compiler performs); only valid inputs are fed. Counts 0..200 with
// unaligned starts are covered, plus an AES tier-agreement check (VAES 32B
// path vs two AES-NI 16B paths) and diffusion checks.

#include "FrankoRuntime.hpp"
#include "simd_x86_only.inc"

#include <experimental/simd>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <random>
#include <vector>

namespace stdx = std::experimental;
using u8x32 = stdx::fixed_size_simd<std::uint8_t, 32>;
using u8x16 = stdx::fixed_size_simd<std::uint8_t, 16>;

static std::mt19937 rng(0xC0FFEE);
static int failures = 0;
static int checks = 0;

static void check(bool ok, const char* what, std::uint32_t count) {
    ++checks;
    if (!ok) {
        ++failures;
        std::printf("MISMATCH %s count=%u\n", what, count);
    }
}

static std::uint8_t rnd_byte() { return (std::uint8_t)rng(); }

// Reference models (written from the documented semantics, not the bridge).
static void ref_simple(std::uint8_t* dst, const std::uint8_t* a,
                       const std::uint8_t* b, std::uint32_t n, int op) {
    std::uint32_t i = 0;
    if (op == 6) {
        // rounded average needs a 9-bit intermediate; do it scalar.
        for (; i < n; ++i) dst[i] = (std::uint8_t)((a[i] + b[i] + 1) >> 1);
        return;
    }
    for (; i + 32 <= n; i += 32) {
        u8x32 x(a + i, stdx::element_aligned_tag());
        u8x32 y(b + i, stdx::element_aligned_tag());
        u8x32 r(0);
        switch (op) {
        case 0: r = x ^ y; break;
        case 1: r = x & y; break;
        case 2: r = x | y; break;
        case 3: r = (~x) & y; break;
        case 4: r = x + y; break;
        case 5: r = x - y; break;
        default: break;
        }
        for (int k = 0; k < 32; ++k) dst[i + k] = (std::uint8_t)r[k];
    }
    for (; i < n; ++i) {
        switch (op) {
        case 0: dst[i] = (std::uint8_t)(a[i] ^ b[i]); break;
        case 1: dst[i] = (std::uint8_t)(a[i] & b[i]); break;
        case 2: dst[i] = (std::uint8_t)(a[i] | b[i]); break;
        case 3: dst[i] = (std::uint8_t)((~a[i]) & b[i]); break;
        case 4: dst[i] = (std::uint8_t)(a[i] + b[i]); break;
        case 5: dst[i] = (std::uint8_t)(a[i] - b[i]); break;
        default: break;
        }
    }
}

int main() {
    setvbuf(stdout, nullptr, _IONBF, 0);
    constexpr std::uint32_t BUF = 256;
    std::vector<std::uint8_t> A(BUF), B(BUF), M(BUF), T1(BUF), T2(BUF);
    std::vector<std::int8_t> S(BUF);
    for (auto& v : A) v = rnd_byte();
    for (auto& v : B) v = rnd_byte();
    for (auto& v : S) v = (std::int8_t)(rnd_byte() & 0xFF);
    const std::uint32_t counts[] = {0, 1, 7, 8, 15, 16, 17, 24, 31, 32, 33,
                                    37, 48, 63, 64, 65, 96, 100, 128, 200};
    const std::uint32_t starts[] = {0, 1, 3, 7};

    auto run_simple = [&](const char* name, int op,
                          auto bridge, bool inplace_b) {
        for (std::uint32_t n : counts) {
            for (std::uint32_t s : starts) {
                if (s + n > BUF) continue;
                std::memcpy(T1.data(), A.data(), BUF);
                std::memcpy(T2.data(), A.data(), BUF);
                bridge(franko_make_fat_pointer(T1.data(), BUF),
                       franko_make_fat_pointer(B.data(), BUF), s, s, n);
                ref_simple(T2.data() + s, A.data() + s, B.data() + s, n, op);
                check(std::memcmp(T1.data(), T2.data(), BUF) == 0, name, n);
                (void)inplace_b;
            }
        }
    };

    run_simple("xor", 0, [](auto t, auto s, auto a, auto b, auto c) {
        NS_simd::NS_x86::FUNC_xor_si256_7243e039(t, s, a, b, c);
    }, true);
    run_simple("and", 1, [](auto t, auto s, auto a, auto b, auto c) {
        NS_simd::NS_x86::FUNC_and_si256_7243e039(t, s, a, b, c);
    }, true);
    run_simple("or", 2, [](auto t, auto s, auto a, auto b, auto c) {
        NS_simd::NS_x86::FUNC_or_si256_7243e039(t, s, a, b, c);
    }, true);
    run_simple("andnot", 3, [](auto t, auto s, auto a, auto b, auto c) {
        NS_simd::NS_x86::FUNC_andnot_si256_7243e039(t, s, a, b, c);
    }, true);
    run_simple("add", 4, [](auto t, auto s, auto a, auto b, auto c) {
        NS_simd::NS_x86::FUNC_add_epi8_7243e039(t, s, a, b, c);
    }, true);
    run_simple("sub", 5, [](auto t, auto s, auto a, auto b, auto c) {
        NS_simd::NS_x86::FUNC_sub_epi8_7243e039(t, s, a, b, c);
    }, true);
    run_simple("avg", 6, [](auto t, auto s, auto a, auto b, auto c) {
        NS_simd::NS_x86::FUNC_avg_epu8_7243e039(t, s, a, b, c);
    }, true);

    // saturating / min / max / cmpeq (scalar refs: independent by construction)
    auto ref_sat = [&](std::uint8_t* dst, const std::uint8_t* a,
                       const std::uint8_t* b, std::uint32_t n, int op) {
        for (std::uint32_t i = 0; i < n; ++i) {
            unsigned x = a[i], y = b[i];
            if (op == 0) dst[i] = (std::uint8_t)(x + y > 255 ? 255 : x + y);
            else if (op == 1) dst[i] = (std::uint8_t)(x > y ? x - y : 0);
            else if (op == 2) dst[i] = x > y ? a[i] : b[i];
            else if (op == 3) dst[i] = x < y ? a[i] : b[i];
            else dst[i] = (std::uint8_t)(a[i] == b[i] ? 0xFF : 0x00);
        }
    };
    auto run_sat = [&](const char* name, int op, auto bridge) {
        for (std::uint32_t n : counts) {
            for (std::uint32_t s : starts) {
                if (s + n > BUF) continue;
                std::memcpy(T1.data(), A.data(), BUF);
                std::memcpy(T2.data(), A.data(), BUF);
                bridge(franko_make_fat_pointer(T1.data(), BUF),
                       franko_make_fat_pointer(B.data(), BUF), s, s, n);
                ref_sat(T2.data() + s, A.data() + s, B.data() + s, n, op);
                check(std::memcmp(T1.data(), T2.data(), BUF) == 0, name, n);
            }
        }
    };
    run_sat("adds", 0, [](auto t, auto s, auto a, auto b, auto c) {
        NS_simd::NS_x86::FUNC_adds_epu8_7243e039(t, s, a, b, c);
    });
    run_sat("subs", 1, [](auto t, auto s, auto a, auto b, auto c) {
        NS_simd::NS_x86::FUNC_subs_epu8_7243e039(t, s, a, b, c);
    });
    run_sat("max", 2, [](auto t, auto s, auto a, auto b, auto c) {
        NS_simd::NS_x86::FUNC_max_epu8_7243e039(t, s, a, b, c);
    });
    run_sat("min", 3, [](auto t, auto s, auto a, auto b, auto c) {
        NS_simd::NS_x86::FUNC_min_epu8_7243e039(t, s, a, b, c);
    });
    run_sat("cmpeq", 4, [](auto t, auto s, auto a, auto b, auto c) {
        NS_simd::NS_x86::FUNC_cmpeq_epi8_7243e039(t, s, a, b, c);
    });

    // blendv / unpack / packus / shuffle (scalar refs)
    auto ref_rearr = [&](std::uint8_t* dst, const std::uint8_t* a,
                         const std::uint8_t* b, std::uint32_t n, int op,
                         const std::uint8_t* m, std::uint32_t sh) {
        for (std::uint32_t base = 0; base < n; base += 16) {
            std::uint8_t block[16] = {0};
            std::uint32_t lim = n - base < 16 ? n - base : 16;
            std::memcpy(block, a + base, lim);
            for (std::uint32_t j = 0; j < lim; ++j) {
                if (op == 0) dst[base + j] = (m[base + j] & 0x80) ? b[base + j] : block[j];
                else if (op == 1) { // unpacklo
                    dst[base + j] = (j % 2 == 0) ? block[j / 2] : b[base + j / 2];
                } else if (op == 2) { // unpackhi
                    std::uint32_t lane = 8 + j / 2;
                    if (j % 2 == 0) dst[base + j] = block[lane];
                    else dst[base + j] = (lane < lim) ? b[base + lane] : 0;
                } else if (op == 3 && lim == 16) { // shuffle: full blocks only
                    std::uint8_t c = m[base + j];
                    dst[base + j] = (c & 0x80) ? 0 : block[c & 0x0F];
                }
            }
            if (op == 4) { // alignr shift sh, in-lane window
                std::uint8_t out[16] = {0};
                for (std::uint32_t j = 0; j < lim; ++j) {
                    std::uint32_t off = j + sh;
                    if (off < 16) {
                        std::uint32_t idx = base + off;
                        out[j] = idx < n ? b[idx] : 0;
                    } else out[j] = block[off - 16];
                }
                std::memcpy(dst + base, out, lim);
            }
        }
    };
    std::vector<std::uint8_t> MK(BUF);
    for (auto& v : MK) v = rnd_byte();
    // note: bridge sigs differ per op; explicit calls below
    for (std::uint32_t n : counts) {
        for (std::uint32_t s : starts) {
            if (s + n > BUF) continue;
            // blendv
            std::memcpy(T1.data(), A.data(), BUF);
            std::memcpy(T2.data(), A.data(), BUF);
            NS_simd::NS_x86::FUNC_blendv_epi8_bea41bc9(
                franko_make_fat_pointer(T1.data(), BUF),
                franko_make_fat_pointer(B.data(), BUF),
                franko_make_fat_pointer(MK.data(), BUF), s, s, s, n);
            ref_rearr(T2.data() + s, A.data() + s, B.data() + s, n, 0,
                      MK.data() + s, 0);
            check(std::memcmp(T1.data(), T2.data(), BUF) == 0, "blendv", n);
            // shuffle
            std::memcpy(T1.data(), A.data(), BUF);
            std::memcpy(T2.data(), A.data(), BUF);
            NS_simd::NS_x86::FUNC_shuffle_epi8_7243e039(
                franko_make_fat_pointer(T1.data(), BUF),
                franko_make_fat_pointer(MK.data(), BUF), s, s, n);
            ref_rearr(T2.data() + s, A.data() + s, B.data() + s, n, 3,
                      MK.data() + s, 0);
            check(std::memcmp(T1.data(), T2.data(), BUF) == 0, "shuffle", n);
            // alignr shifts incl. edge cases
            for (std::uint32_t sh : {0u, 1u, 3u, 7u, 15u, 16u, 17u, 31u}) {
                std::memcpy(T1.data(), A.data(), BUF);
                std::memcpy(T2.data(), A.data(), BUF);
                NS_simd::NS_x86::FUNC_alignr_epi8_62d3997c(
                    franko_make_fat_pointer(T1.data(), BUF),
                    franko_make_fat_pointer(B.data(), BUF), s, s, n, sh);
                if (sh < 16) {
                    ref_rearr(T2.data() + s, A.data() + s, B.data() + s, n, 4,
                              MK.data() + s, sh);
                } else {
                    std::memset(T2.data() + s, 0, n);
                }
                check(std::memcmp(T1.data(), T2.data(), BUF) == 0, "alignr", n);
            }
            // unpacklo / unpackhi
            std::memcpy(T1.data(), A.data(), BUF);
            std::memcpy(T2.data(), A.data(), BUF);
            NS_simd::NS_x86::FUNC_unpacklo_epi8_7243e039(
                franko_make_fat_pointer(T1.data(), BUF),
                franko_make_fat_pointer(B.data(), BUF), s, s, n);
            ref_rearr(T2.data() + s, A.data() + s, B.data() + s, n, 1,
                      MK.data() + s, 0);
            check(std::memcmp(T1.data(), T2.data(), BUF) == 0, "unpacklo", n);
            std::memcpy(T1.data(), A.data(), BUF);
            std::memcpy(T2.data(), A.data(), BUF);
            NS_simd::NS_x86::FUNC_unpackhi_epi8_7243e039(
                franko_make_fat_pointer(T1.data(), BUF),
                franko_make_fat_pointer(B.data(), BUF), s, s, n);
            ref_rearr(T2.data() + s, A.data() + s, B.data() + s, n, 2,
                      MK.data() + s, 0);
            check(std::memcmp(T1.data(), T2.data(), BUF) == 0, "unpackhi", n);
        }
    }

    // movemask (scalar ref; u64-capped at 64)
    for (std::uint32_t n : counts) {
        if (n > 64) continue;
        for (std::uint32_t s : starts) {
            if (s + n > BUF) continue;
            std::uint64_t got =
                NS_simd::NS_x86::FUNC_movemask_epi8_dd946ac2(
                    franko_make_fat_pointer(B.data(), BUF), s, n);
            std::uint64_t want = 0;
            for (std::uint32_t k = 0; k < n; ++k)
                want |= (std::uint64_t)((B[s + k] >> 7) & 1u) << k;
            check(got == want, "movemask", n);
        }
    }

    // crc32c (scalar ref, Castagnoli)
    for (std::uint32_t n : counts) {
        for (std::uint32_t s : starts) {
            if (s + n > BUF) continue;
            std::uint32_t got = NS_simd::NS_x86::FUNC_crc32_u8_19ca3df8(
                0, franko_make_fat_pointer(A.data(), BUF), s, n);
            std::uint32_t want = 0;
            for (std::uint32_t k = 0; k < n; ++k) {
                want ^= A[s + k];
                for (int j = 0; j < 8; ++j)
                    want = (want >> 1) ^ (0x82F63B78u & (0u - (want & 1u)));
            }
            check(got == want, "crc32", n);
        }
    }

    // widen u8->u16 (scalar ref)
    {
        std::vector<std::uint16_t> W1(BUF), W2(BUF);
        for (std::uint32_t n : counts) {
            for (std::uint32_t s : starts) {
                if (s + n > BUF) continue;
                std::fill(W1.begin(), W1.end(), (std::uint16_t)0xBEEF);
                std::fill(W2.begin(), W2.end(), (std::uint16_t)0xBEEF);
                NS_simd::NS_x86::FUNC_cvtepu8_epi16_b3d94c16(
                    franko_make_fat_pointer(W1.data(), BUF),
                    franko_make_fat_pointer(A.data(), BUF), 0, s, n);
                for (std::uint32_t k = 0; k < n; ++k) W2[k] = A[s + k];
                check(std::memcmp(W1.data(), W2.data(), BUF * 2) == 0, "cvtepu8", n);
            }
        }
    }

    // dpbusd (scalar ref, wrapping)
    {
        std::vector<std::uint32_t> D1(64, 1000000), D2(64, 1000000);
        std::vector<std::int8_t> SB(BUF);
        for (std::uint32_t i = 0; i < BUF; ++i) SB[i] = (std::int8_t)(B[i]);
        const std::uint32_t dn[] = {0, 1, 2, 3, 4, 5, 7, 8, 9, 15, 16, 17, 31, 32};
        for (std::uint32_t n : dn) {
            for (std::uint32_t s : {0u, 1u}) {
                if (4 * n + s > BUF || n > 64) continue;
                std::fill(D1.begin(), D1.end(), 1000000u);
                std::fill(D2.begin(), D2.end(), 1000000u);
                NS_simd::NS_x86::FUNC_dpbusd_epi32_7a76aa9d(
                    franko_make_fat_pointer(D1.data(), 64),
                    franko_make_fat_pointer(A.data(), BUF),
                    franko_make_fat_pointer(
                        reinterpret_cast<std::int8_t*>(SB.data()), BUF),
                    0, s, s, n);
                for (std::uint32_t i = 0; i < n; ++i) {
                    std::int64_t sum = 1000000;
                    for (std::uint32_t k = 0; k < 4; ++k)
                        sum += (std::int64_t)A[s + 4 * i + k] * (std::int64_t)SB[s + 4 * i + k];
                    D2[i] = (std::uint32_t)(sum & 0xFFFFFFFF);
                }
                check(std::memcmp(D1.data(), D2.data(), 64 * 4) == 0, "dpbusd", n);
            }
        }
    }

    // ptest (scalar ref)
    std::vector<std::uint8_t> Z(BUF, 0);
    for (std::uint32_t n : counts) {
        for (std::uint32_t s : starts) {
            if (s + n > BUF) continue;
            std::uint32_t got = NS_simd::NS_x86::FUNC_ptest_epi8_480a58a9(
                franko_make_fat_pointer(A.data(), BUF),
                franko_make_fat_pointer(B.data(), BUF), s, s, n);
            std::uint32_t want = 1;
            for (std::uint32_t k = 0; k < n; ++k)
                if (A[s + k] & B[s + k]) { want = 0; break; }
            check(got == want, "ptest-hit", n);
            got = NS_simd::NS_x86::FUNC_ptest_epi8_480a58a9(
                franko_make_fat_pointer(A.data(), BUF),
                franko_make_fat_pointer(Z.data(), BUF), s, s, n);
            check(got == 1u, "ptest-zero", n);
        }
    }

    // clmul (scalar __uint128_t ref: different algorithm than bridge)
    {
        std::vector<std::uint64_t> H1(2 * 32, 0), H2(2 * 32, 0), VA(32), VB(32);
        for (std::uint32_t i = 0; i < 32; ++i) {
            VA[i] = ((std::uint64_t)rng() << 32) | rng();
            VB[i] = ((std::uint64_t)rng() << 32) | rng();
        }
        const std::uint32_t cn[] = {0, 1, 2, 3, 4, 5, 7, 8, 9, 15, 16, 17, 31, 32};
        for (std::uint32_t n : cn) {
            for (std::uint32_t s : {0u, 1u}) {
                if (s + n > 32) continue;
                std::fill(H1.begin(), H1.end(), 0ull);
                std::fill(H2.begin(), H2.end(), 0ull);
                NS_simd::NS_x86::FUNC_clmulepi64_epi64_b95c3315(
                    franko_make_fat_pointer(H1.data(), 64),
                    franko_make_fat_pointer(VA.data(), 32),
                    franko_make_fat_pointer(VB.data(), 32), 0, s, s, n);
                for (std::uint32_t i = 0; i < n; ++i) {
                    unsigned __int128 acc = 0;
                    for (int k = 0; k < 64; ++k)
                        if ((VA[s + i] >> k) & 1u)
                            acc ^= (unsigned __int128)VB[s + i] << k;
                    H2[2 * i] = (std::uint64_t)acc;
                    H2[2 * i + 1] = (std::uint64_t)(acc >> 64);
                }
                check(std::memcmp(H1.data(), H2.data(), 64 * 8) == 0, "clmul", n);
            }
        }
    }

    // stream copy (memcpy ref)
    for (std::uint32_t n : counts) {
        for (std::uint32_t s : starts) {
            if (s + n > BUF) continue;
            std::memset(T1.data(), 0xAA, BUF);
            NS_simd::NS_x86::FUNC_stream_si256_7243e039(
                franko_make_fat_pointer(T1.data(), BUF),
                franko_make_fat_pointer(B.data(), BUF), s, s, n);
            bool ok = std::memcmp(T1.data() + s, B.data() + s, n) == 0;
            for (std::uint32_t k = 0; ok && k < s; ++k) ok = T1[k] == 0xAA;
            for (std::uint32_t k = s + n; ok && k < BUF; ++k) ok = T1[k] == 0xAA;
            check(ok, "stream", n);
        }
    }

    // packus (scalar clamp ref)
    for (std::uint32_t n : counts) {
        for (std::uint32_t s : starts) {
            if (s + n > BUF) continue;
            static std::vector<std::int16_t> W(BUF);
            for (std::uint32_t k = 0; k < BUF; ++k)
                W[k] = (std::int16_t)((std::int32_t)rng() % 600 - 200);
            std::memcpy(T1.data(), A.data(), BUF);
            std::memcpy(T2.data(), A.data(), BUF);
            NS_simd::NS_x86::FUNC_packus_epi16_ed547b8f(
                franko_make_fat_pointer(T1.data(), BUF),
                franko_make_fat_pointer(W.data(), BUF), s, s, n);
            for (std::uint32_t k = 0; k < n; ++k) {
                int v = W[s + k];
                T2[s + k] = (std::uint8_t)(v < 0 ? 0 : v > 255 ? 255 : v);
            }
            check(std::memcmp(T1.data(), T2.data(), BUF) == 0, "packus", n);
        }
    }

    // sad (scalar sum ref)
    for (std::uint32_t n : counts) {
        for (std::uint32_t s : starts) {
            if (s + n > BUF) continue;
            std::uint64_t got = NS_simd::NS_x86::FUNC_sad_epu8_4812cf2a(
                franko_make_fat_pointer(A.data(), BUF),
                franko_make_fat_pointer(B.data(), BUF), s, s, n);
            std::uint64_t want = 0;
            for (std::uint32_t k = 0; k < n; ++k) {
                int d = (int)A[s + k] - (int)B[s + k];
                want += (std::uint64_t)(d < 0 ? -d : d);
            }
            check(got == want, "sad", n);
        }
    }

    // abs (scalar ref, -128 stays -128)
    {
        std::vector<std::int8_t> SA(BUF), TA(BUF), TB(BUF);
        for (auto& v : SA) v = (std::int8_t)rng();
        for (std::uint32_t n : counts) {
            for (std::uint32_t s : starts) {
                if (s + n > BUF) continue;
                std::memcpy(TA.data(), SA.data(), BUF);
                std::memcpy(TB.data(), SA.data(), BUF);
                NS_simd::NS_x86::FUNC_abs_epi8_6c5347dd(
                    franko_make_fat_pointer(
                        reinterpret_cast<std::int8_t*>(TA.data()), BUF),
                    franko_make_fat_pointer(
                        reinterpret_cast<std::int8_t*>(SA.data()), BUF),
                    s, s, n);
                for (std::uint32_t k = 0; k < n; ++k) {
                    int v = SA[s + k];
                    TB[s + k] = (std::int8_t)(v < 0 ? (v == -128 ? -128 : -v) : v);
                }
                check(std::memcmp(TA.data(), TB.data(), BUF) == 0, "abs", n);
            }
        }
    }

    // AES: VAES 32B path must equal two AES-NI 16B paths (tier agreement),
    // and output must differ from input (diffusion). No round-trip identity
    // check: a single AES round is not self-inverting with the same key
    // (real AES needs the inverse key schedule), so that premise is invalid.
    for (std::uint32_t n : {16u, 32u, 48u}) {
        std::vector<std::uint8_t> K(16), X(BUF), Y1(BUF), Y2(BUF);
        for (auto& v : K) v = rnd_byte();
        for (auto& v : X) v = rnd_byte();
        std::memcpy(Y1.data(), X.data(), BUF);
        std::memcpy(Y2.data(), X.data(), BUF);
        auto kf = franko_make_fat_pointer(K.data(), 16u);
#if defined(__AES__) || defined(__VAES__)
        int re = NS_simd::NS_x86::FUNC_aesenc_si256_697d55d9(
            franko_make_fat_pointer(Y1.data(), BUF), kf, 0, 0, n);
        check(re == 0, "aes-status", n);
        check(std::memcmp(X.data(), Y1.data(), n) != 0, "aes-diffuses", n);
        int r1 = NS_simd::NS_x86::FUNC_aesenc_si256_697d55d9(
            franko_make_fat_pointer(Y2.data(), BUF), kf, 0, 0, 16);
        int r2 = 0;
        if (n > 16)
            r2 = NS_simd::NS_x86::FUNC_aesenc_si256_697d55d9(
                franko_make_fat_pointer(Y2.data(), BUF), kf, 16, 0, n - 16);
        check(r1 == 0 && r2 == 0, "aes-half-status", n);
        check(std::memcmp(Y1.data(), Y2.data(), n > 32 ? 32 : n) == 0,
              "aes-tier-agree", n);
        int rd = NS_simd::NS_x86::FUNC_aesdec_si256_697d55d9(
            franko_make_fat_pointer(Y1.data(), BUF), kf, 0, 0, n);
        check(rd == 0, "aesdec-status", n);
#else
        // No AES instruction set: the bridge must refuse and leave untouched.
        int re = NS_simd::NS_x86::FUNC_aesenc_si256_697d55d9(
            franko_make_fat_pointer(Y1.data(), BUF), kf, 0, 0, n);
        int rd = NS_simd::NS_x86::FUNC_aesdec_si256_697d55d9(
            franko_make_fat_pointer(Y1.data(), BUF), kf, 0, 0, 16);
        check(re != 0 && rd != 0, "aes-unavailable", n);
        check(std::memcmp(X.data(), Y1.data(), BUF) == 0, "aes-untouched", n);
#endif
    }

    std::printf("%d checks, %d failures\n", checks, failures);
    return failures != 0;
}
