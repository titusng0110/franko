// @@common
#include "FrankoRuntime.hpp"

#if defined(__x86_64__) || defined(_M_X64)
#include <immintrin.h>
#elif defined(__aarch64__)
#include <arm_neon.h>
#if defined(__ARM_FEATURE_CRC32)
#include <arm_acle.h>
#endif
#elif defined(_M_ARM64)
#include <arm64_neon.h>
#endif
// Target ceiling: x86-64 Alder Lake and newer (AVX2, VAES; no AVX-512
// tiers — neither ceiling machine has it), ARM Apple M3 (ARMv8.6-A with
// AES/PMULL/CRC32/DotProd, 128-bit NEON; no I8MM, no SVE). The surface
// only exposes instructions the M3 actually implements, so there is no
// USDOT/I8MM wrapper. Older machines fall back to the 128-bit and scalar
// paths in each block below.
// @@end





// @@simd::x86::xor_si256
namespace NS_simd {
namespace NS_x86 {
FRANKO_INLINE void FUNC_xor_si256_7243e039(Franko_Fat_Pointer<uint8_t> PARAM_target, Franko_Fat_Pointer<uint8_t> PARAM_source, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint8_t* target = PARAM_target.data;
    uint32_t target_length = PARAM_target.count;
    if (target == nullptr && PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = PARAM_source.data;
    uint32_t source_length = PARAM_source.count;
    if (source == nullptr && PARAM_count != 0) {
        franko_panic("simd: null source");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > source_length || PARAM_count > source_length - PARAM_src_start) {
        franko_panic("simd: source range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return;
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (target == source && PARAM_dst_start != PARAM_src_start) {
        franko_panic("simd: overlapping target and source ranges");
    }
#endif
    target += PARAM_dst_start;
    source += PARAM_src_start;
    uint32_t i = 0;
#if defined(__AVX2__)
    for (; i + 32 <= PARAM_count; i += 32) {
        const __m256i left = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(target + i));
        const __m256i right = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(source + i));
        _mm256_storeu_si256(reinterpret_cast<__m256i*>(target + i), _mm256_xor_si256(left, right));
    }
#endif
    for (; i + 16 <= PARAM_count; i += 16) {
        const __m128i left = _mm_loadu_si128(reinterpret_cast<const __m128i*>(target + i));
        const __m128i right = _mm_loadu_si128(reinterpret_cast<const __m128i*>(source + i));
        _mm_storeu_si128(reinterpret_cast<__m128i*>(target + i), _mm_xor_si128(left, right));
    }
    for (; i < PARAM_count; ++i) {
        target[i] = static_cast<uint8_t>(target[i] ^ source[i]);
    }
}
}
}
// @@end

// @@simd::x86::and_si256
namespace NS_simd {
namespace NS_x86 {
FRANKO_INLINE void FUNC_and_si256_7243e039(Franko_Fat_Pointer<uint8_t> PARAM_target, Franko_Fat_Pointer<uint8_t> PARAM_source, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint8_t* target = PARAM_target.data;
    uint32_t target_length = PARAM_target.count;
    if (target == nullptr && PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = PARAM_source.data;
    uint32_t source_length = PARAM_source.count;
    if (source == nullptr && PARAM_count != 0) {
        franko_panic("simd: null source");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > source_length || PARAM_count > source_length - PARAM_src_start) {
        franko_panic("simd: source range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return;
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (target == source && PARAM_dst_start != PARAM_src_start) {
        franko_panic("simd: overlapping target and source ranges");
    }
#endif
    target += PARAM_dst_start;
    source += PARAM_src_start;
    uint32_t i = 0;
#if defined(__AVX2__)
    for (; i + 32 <= PARAM_count; i += 32) {
        const __m256i left = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(target + i));
        const __m256i right = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(source + i));
        _mm256_storeu_si256(reinterpret_cast<__m256i*>(target + i), _mm256_and_si256(left, right));
    }
#endif
    for (; i + 16 <= PARAM_count; i += 16) {
        const __m128i left = _mm_loadu_si128(reinterpret_cast<const __m128i*>(target + i));
        const __m128i right = _mm_loadu_si128(reinterpret_cast<const __m128i*>(source + i));
        _mm_storeu_si128(reinterpret_cast<__m128i*>(target + i), _mm_and_si128(left, right));
    }
    for (; i < PARAM_count; ++i) {
        target[i] = static_cast<uint8_t>(target[i] & source[i]);
    }
}
}
}
// @@end

// @@simd::x86::or_si256
namespace NS_simd {
namespace NS_x86 {
FRANKO_INLINE void FUNC_or_si256_7243e039(Franko_Fat_Pointer<uint8_t> PARAM_target, Franko_Fat_Pointer<uint8_t> PARAM_source, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint8_t* target = PARAM_target.data;
    uint32_t target_length = PARAM_target.count;
    if (target == nullptr && PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = PARAM_source.data;
    uint32_t source_length = PARAM_source.count;
    if (source == nullptr && PARAM_count != 0) {
        franko_panic("simd: null source");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > source_length || PARAM_count > source_length - PARAM_src_start) {
        franko_panic("simd: source range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return;
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (target == source && PARAM_dst_start != PARAM_src_start) {
        franko_panic("simd: overlapping target and source ranges");
    }
#endif
    target += PARAM_dst_start;
    source += PARAM_src_start;
    uint32_t i = 0;
#if defined(__AVX2__)
    for (; i + 32 <= PARAM_count; i += 32) {
        const __m256i left = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(target + i));
        const __m256i right = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(source + i));
        _mm256_storeu_si256(reinterpret_cast<__m256i*>(target + i), _mm256_or_si256(left, right));
    }
#endif
    for (; i + 16 <= PARAM_count; i += 16) {
        const __m128i left = _mm_loadu_si128(reinterpret_cast<const __m128i*>(target + i));
        const __m128i right = _mm_loadu_si128(reinterpret_cast<const __m128i*>(source + i));
        _mm_storeu_si128(reinterpret_cast<__m128i*>(target + i), _mm_or_si128(left, right));
    }
    for (; i < PARAM_count; ++i) {
        target[i] = static_cast<uint8_t>(target[i] | source[i]);
    }
}
}
}
// @@end

// @@simd::x86::andnot_si256
namespace NS_simd {
namespace NS_x86 {
FRANKO_INLINE void FUNC_andnot_si256_7243e039(Franko_Fat_Pointer<uint8_t> PARAM_target, Franko_Fat_Pointer<uint8_t> PARAM_source, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint8_t* target = PARAM_target.data;
    uint32_t target_length = PARAM_target.count;
    if (target == nullptr && PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = PARAM_source.data;
    uint32_t source_length = PARAM_source.count;
    if (source == nullptr && PARAM_count != 0) {
        franko_panic("simd: null source");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > source_length || PARAM_count > source_length - PARAM_src_start) {
        franko_panic("simd: source range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return;
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (target == source && PARAM_dst_start != PARAM_src_start) {
        franko_panic("simd: overlapping target and source ranges");
    }
#endif
    target += PARAM_dst_start;
    source += PARAM_src_start;
    uint32_t i = 0;
#if defined(__AVX2__)
    for (; i + 32 <= PARAM_count; i += 32) {
        const __m256i left = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(target + i));
        const __m256i right = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(source + i));
        _mm256_storeu_si256(reinterpret_cast<__m256i*>(target + i), _mm256_andnot_si256(left, right));
    }
#endif
    for (; i + 16 <= PARAM_count; i += 16) {
        const __m128i left = _mm_loadu_si128(reinterpret_cast<const __m128i*>(target + i));
        const __m128i right = _mm_loadu_si128(reinterpret_cast<const __m128i*>(source + i));
        _mm_storeu_si128(reinterpret_cast<__m128i*>(target + i), _mm_andnot_si128(left, right));
    }
    for (; i < PARAM_count; ++i) {
        target[i] = static_cast<uint8_t>((~target[i]) & source[i]);
    }
}
}
}
// @@end

// @@simd::x86::adds_epu8
namespace NS_simd {
namespace NS_x86 {
FRANKO_INLINE void FUNC_adds_epu8_7243e039(Franko_Fat_Pointer<uint8_t> PARAM_target, Franko_Fat_Pointer<uint8_t> PARAM_source, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint8_t* target = PARAM_target.data;
    uint32_t target_length = PARAM_target.count;
    if (target == nullptr && PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = PARAM_source.data;
    uint32_t source_length = PARAM_source.count;
    if (source == nullptr && PARAM_count != 0) {
        franko_panic("simd: null source");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > source_length || PARAM_count > source_length - PARAM_src_start) {
        franko_panic("simd: source range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return;
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (target == source && PARAM_dst_start != PARAM_src_start) {
        franko_panic("simd: overlapping target and source ranges");
    }
#endif
    target += PARAM_dst_start;
    source += PARAM_src_start;
    uint32_t i = 0;
#if defined(__AVX2__)
    for (; i + 32 <= PARAM_count; i += 32) {
        const __m256i left = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(target + i));
        const __m256i right = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(source + i));
        _mm256_storeu_si256(reinterpret_cast<__m256i*>(target + i), _mm256_adds_epu8(left, right));
    }
#endif
    for (; i + 16 <= PARAM_count; i += 16) {
        const __m128i left = _mm_loadu_si128(reinterpret_cast<const __m128i*>(target + i));
        const __m128i right = _mm_loadu_si128(reinterpret_cast<const __m128i*>(source + i));
        _mm_storeu_si128(reinterpret_cast<__m128i*>(target + i), _mm_adds_epu8(left, right));
    }
    for (; i < PARAM_count; ++i) {
        const uint32_t sum = static_cast<uint32_t>(target[i]) + source[i];
        target[i] = sum > 255 ? 255 : static_cast<uint8_t>(sum);
    }
}
}
}
// @@end

// @@simd::x86::subs_epu8
namespace NS_simd {
namespace NS_x86 {
FRANKO_INLINE void FUNC_subs_epu8_7243e039(Franko_Fat_Pointer<uint8_t> PARAM_target, Franko_Fat_Pointer<uint8_t> PARAM_source, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint8_t* target = PARAM_target.data;
    uint32_t target_length = PARAM_target.count;
    if (target == nullptr && PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = PARAM_source.data;
    uint32_t source_length = PARAM_source.count;
    if (source == nullptr && PARAM_count != 0) {
        franko_panic("simd: null source");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > source_length || PARAM_count > source_length - PARAM_src_start) {
        franko_panic("simd: source range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return;
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (target == source && PARAM_dst_start != PARAM_src_start) {
        franko_panic("simd: overlapping target and source ranges");
    }
#endif
    target += PARAM_dst_start;
    source += PARAM_src_start;
    uint32_t i = 0;
#if defined(__AVX2__)
    for (; i + 32 <= PARAM_count; i += 32) {
        const __m256i left = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(target + i));
        const __m256i right = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(source + i));
        _mm256_storeu_si256(reinterpret_cast<__m256i*>(target + i), _mm256_subs_epu8(left, right));
    }
#endif
    for (; i + 16 <= PARAM_count; i += 16) {
        const __m128i left = _mm_loadu_si128(reinterpret_cast<const __m128i*>(target + i));
        const __m128i right = _mm_loadu_si128(reinterpret_cast<const __m128i*>(source + i));
        _mm_storeu_si128(reinterpret_cast<__m128i*>(target + i), _mm_subs_epu8(left, right));
    }
    for (; i < PARAM_count; ++i) {
        target[i] = target[i] > source[i] ? static_cast<uint8_t>(target[i] - source[i]) : 0;
    }
}
}
}
// @@end

// @@simd::x86::max_epu8
namespace NS_simd {
namespace NS_x86 {
FRANKO_INLINE void FUNC_max_epu8_7243e039(Franko_Fat_Pointer<uint8_t> PARAM_target, Franko_Fat_Pointer<uint8_t> PARAM_source, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint8_t* target = PARAM_target.data;
    uint32_t target_length = PARAM_target.count;
    if (target == nullptr && PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = PARAM_source.data;
    uint32_t source_length = PARAM_source.count;
    if (source == nullptr && PARAM_count != 0) {
        franko_panic("simd: null source");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > source_length || PARAM_count > source_length - PARAM_src_start) {
        franko_panic("simd: source range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return;
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (target == source && PARAM_dst_start != PARAM_src_start) {
        franko_panic("simd: overlapping target and source ranges");
    }
#endif
    target += PARAM_dst_start;
    source += PARAM_src_start;
    uint32_t i = 0;
#if defined(__AVX2__)
    for (; i + 32 <= PARAM_count; i += 32) {
        const __m256i left = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(target + i));
        const __m256i right = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(source + i));
        _mm256_storeu_si256(reinterpret_cast<__m256i*>(target + i), _mm256_max_epu8(left, right));
    }
#endif
    for (; i + 16 <= PARAM_count; i += 16) {
        const __m128i left = _mm_loadu_si128(reinterpret_cast<const __m128i*>(target + i));
        const __m128i right = _mm_loadu_si128(reinterpret_cast<const __m128i*>(source + i));
        _mm_storeu_si128(reinterpret_cast<__m128i*>(target + i), _mm_max_epu8(left, right));
    }
    for (; i < PARAM_count; ++i) {
        target[i] = target[i] > source[i] ? target[i] : source[i];
    }
}
}
}
// @@end

// @@simd::x86::min_epu8
namespace NS_simd {
namespace NS_x86 {
FRANKO_INLINE void FUNC_min_epu8_7243e039(Franko_Fat_Pointer<uint8_t> PARAM_target, Franko_Fat_Pointer<uint8_t> PARAM_source, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint8_t* target = PARAM_target.data;
    uint32_t target_length = PARAM_target.count;
    if (target == nullptr && PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = PARAM_source.data;
    uint32_t source_length = PARAM_source.count;
    if (source == nullptr && PARAM_count != 0) {
        franko_panic("simd: null source");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > source_length || PARAM_count > source_length - PARAM_src_start) {
        franko_panic("simd: source range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return;
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (target == source && PARAM_dst_start != PARAM_src_start) {
        franko_panic("simd: overlapping target and source ranges");
    }
#endif
    target += PARAM_dst_start;
    source += PARAM_src_start;
    uint32_t i = 0;
#if defined(__AVX2__)
    for (; i + 32 <= PARAM_count; i += 32) {
        const __m256i left = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(target + i));
        const __m256i right = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(source + i));
        _mm256_storeu_si256(reinterpret_cast<__m256i*>(target + i), _mm256_min_epu8(left, right));
    }
#endif
    for (; i + 16 <= PARAM_count; i += 16) {
        const __m128i left = _mm_loadu_si128(reinterpret_cast<const __m128i*>(target + i));
        const __m128i right = _mm_loadu_si128(reinterpret_cast<const __m128i*>(source + i));
        _mm_storeu_si128(reinterpret_cast<__m128i*>(target + i), _mm_min_epu8(left, right));
    }
    for (; i < PARAM_count; ++i) {
        target[i] = target[i] < source[i] ? target[i] : source[i];
    }
}
}
}
// @@end

// @@simd::arm::veorq_u8
namespace NS_simd {
namespace NS_arm {
FRANKO_INLINE void FUNC_veorq_u8_7243e039(Franko_Fat_Pointer<uint8_t> PARAM_target, Franko_Fat_Pointer<uint8_t> PARAM_source, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint8_t* target = PARAM_target.data;
    uint32_t target_length = PARAM_target.count;
    if (target == nullptr && PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = PARAM_source.data;
    uint32_t source_length = PARAM_source.count;
    if (source == nullptr && PARAM_count != 0) {
        franko_panic("simd: null source");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > source_length || PARAM_count > source_length - PARAM_src_start) {
        franko_panic("simd: source range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return;
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (target == source && PARAM_dst_start != PARAM_src_start) {
        franko_panic("simd: overlapping target and source ranges");
    }
#endif
    target += PARAM_dst_start;
    source += PARAM_src_start;
    uint32_t i = 0;
    for (; i + 16 <= PARAM_count; i += 16) {
        const uint8x16_t left = vld1q_u8(target + i);
        const uint8x16_t right = vld1q_u8(source + i);
        vst1q_u8(target + i, veorq_u8(left, right));
    }
    for (; i < PARAM_count; ++i) {
        target[i] = static_cast<uint8_t>(target[i] ^ source[i]);
    }
}
}
}
// @@end

// @@simd::arm::vandq_u8
namespace NS_simd {
namespace NS_arm {
FRANKO_INLINE void FUNC_vandq_u8_7243e039(Franko_Fat_Pointer<uint8_t> PARAM_target, Franko_Fat_Pointer<uint8_t> PARAM_source, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint8_t* target = PARAM_target.data;
    uint32_t target_length = PARAM_target.count;
    if (target == nullptr && PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = PARAM_source.data;
    uint32_t source_length = PARAM_source.count;
    if (source == nullptr && PARAM_count != 0) {
        franko_panic("simd: null source");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > source_length || PARAM_count > source_length - PARAM_src_start) {
        franko_panic("simd: source range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return;
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (target == source && PARAM_dst_start != PARAM_src_start) {
        franko_panic("simd: overlapping target and source ranges");
    }
#endif
    target += PARAM_dst_start;
    source += PARAM_src_start;
    uint32_t i = 0;
    for (; i + 16 <= PARAM_count; i += 16) {
        const uint8x16_t left = vld1q_u8(target + i);
        const uint8x16_t right = vld1q_u8(source + i);
        vst1q_u8(target + i, vandq_u8(left, right));
    }
    for (; i < PARAM_count; ++i) {
        target[i] = static_cast<uint8_t>(target[i] & source[i]);
    }
}
}
}
// @@end

// @@simd::arm::vorrq_u8
namespace NS_simd {
namespace NS_arm {
FRANKO_INLINE void FUNC_vorrq_u8_7243e039(Franko_Fat_Pointer<uint8_t> PARAM_target, Franko_Fat_Pointer<uint8_t> PARAM_source, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint8_t* target = PARAM_target.data;
    uint32_t target_length = PARAM_target.count;
    if (target == nullptr && PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = PARAM_source.data;
    uint32_t source_length = PARAM_source.count;
    if (source == nullptr && PARAM_count != 0) {
        franko_panic("simd: null source");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > source_length || PARAM_count > source_length - PARAM_src_start) {
        franko_panic("simd: source range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return;
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (target == source && PARAM_dst_start != PARAM_src_start) {
        franko_panic("simd: overlapping target and source ranges");
    }
#endif
    target += PARAM_dst_start;
    source += PARAM_src_start;
    uint32_t i = 0;
    for (; i + 16 <= PARAM_count; i += 16) {
        const uint8x16_t left = vld1q_u8(target + i);
        const uint8x16_t right = vld1q_u8(source + i);
        vst1q_u8(target + i, vorrq_u8(left, right));
    }
    for (; i < PARAM_count; ++i) {
        target[i] = static_cast<uint8_t>(target[i] | source[i]);
    }
}
}
}
// @@end

// @@simd::arm::vbicq_u8
namespace NS_simd {
namespace NS_arm {
FRANKO_INLINE void FUNC_vbicq_u8_7243e039(Franko_Fat_Pointer<uint8_t> PARAM_target, Franko_Fat_Pointer<uint8_t> PARAM_source, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint8_t* target = PARAM_target.data;
    uint32_t target_length = PARAM_target.count;
    if (target == nullptr && PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = PARAM_source.data;
    uint32_t source_length = PARAM_source.count;
    if (source == nullptr && PARAM_count != 0) {
        franko_panic("simd: null source");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > source_length || PARAM_count > source_length - PARAM_src_start) {
        franko_panic("simd: source range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return;
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (target == source && PARAM_dst_start != PARAM_src_start) {
        franko_panic("simd: overlapping target and source ranges");
    }
#endif
    target += PARAM_dst_start;
    source += PARAM_src_start;
    uint32_t i = 0;
    for (; i + 16 <= PARAM_count; i += 16) {
        const uint8x16_t left = vld1q_u8(target + i);
        const uint8x16_t right = vld1q_u8(source + i);
        vst1q_u8(target + i, vbicq_u8(left, right));
    }
    for (; i < PARAM_count; ++i) {
        target[i] = static_cast<uint8_t>(target[i] & (~source[i]));
    }
}
}
}
// @@end

// @@simd::arm::vqaddq_u8
namespace NS_simd {
namespace NS_arm {
FRANKO_INLINE void FUNC_vqaddq_u8_7243e039(Franko_Fat_Pointer<uint8_t> PARAM_target, Franko_Fat_Pointer<uint8_t> PARAM_source, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint8_t* target = PARAM_target.data;
    uint32_t target_length = PARAM_target.count;
    if (target == nullptr && PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = PARAM_source.data;
    uint32_t source_length = PARAM_source.count;
    if (source == nullptr && PARAM_count != 0) {
        franko_panic("simd: null source");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > source_length || PARAM_count > source_length - PARAM_src_start) {
        franko_panic("simd: source range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return;
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (target == source && PARAM_dst_start != PARAM_src_start) {
        franko_panic("simd: overlapping target and source ranges");
    }
#endif
    target += PARAM_dst_start;
    source += PARAM_src_start;
    uint32_t i = 0;
    for (; i + 16 <= PARAM_count; i += 16) {
        const uint8x16_t left = vld1q_u8(target + i);
        const uint8x16_t right = vld1q_u8(source + i);
        vst1q_u8(target + i, vqaddq_u8(left, right));
    }
    for (; i < PARAM_count; ++i) {
        const uint32_t sum = static_cast<uint32_t>(target[i]) + source[i];
        target[i] = sum > 255 ? 255 : static_cast<uint8_t>(sum);
    }
}
}
}
// @@end

// @@simd::arm::vqsubq_u8
namespace NS_simd {
namespace NS_arm {
FRANKO_INLINE void FUNC_vqsubq_u8_7243e039(Franko_Fat_Pointer<uint8_t> PARAM_target, Franko_Fat_Pointer<uint8_t> PARAM_source, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint8_t* target = PARAM_target.data;
    uint32_t target_length = PARAM_target.count;
    if (target == nullptr && PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = PARAM_source.data;
    uint32_t source_length = PARAM_source.count;
    if (source == nullptr && PARAM_count != 0) {
        franko_panic("simd: null source");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > source_length || PARAM_count > source_length - PARAM_src_start) {
        franko_panic("simd: source range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return;
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (target == source && PARAM_dst_start != PARAM_src_start) {
        franko_panic("simd: overlapping target and source ranges");
    }
#endif
    target += PARAM_dst_start;
    source += PARAM_src_start;
    uint32_t i = 0;
    for (; i + 16 <= PARAM_count; i += 16) {
        const uint8x16_t left = vld1q_u8(target + i);
        const uint8x16_t right = vld1q_u8(source + i);
        vst1q_u8(target + i, vqsubq_u8(left, right));
    }
    for (; i < PARAM_count; ++i) {
        target[i] = target[i] > source[i] ? static_cast<uint8_t>(target[i] - source[i]) : 0;
    }
}
}
}
// @@end

// @@simd::arm::vmaxq_u8
namespace NS_simd {
namespace NS_arm {
FRANKO_INLINE void FUNC_vmaxq_u8_7243e039(Franko_Fat_Pointer<uint8_t> PARAM_target, Franko_Fat_Pointer<uint8_t> PARAM_source, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint8_t* target = PARAM_target.data;
    uint32_t target_length = PARAM_target.count;
    if (target == nullptr && PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = PARAM_source.data;
    uint32_t source_length = PARAM_source.count;
    if (source == nullptr && PARAM_count != 0) {
        franko_panic("simd: null source");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > source_length || PARAM_count > source_length - PARAM_src_start) {
        franko_panic("simd: source range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return;
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (target == source && PARAM_dst_start != PARAM_src_start) {
        franko_panic("simd: overlapping target and source ranges");
    }
#endif
    target += PARAM_dst_start;
    source += PARAM_src_start;
    uint32_t i = 0;
    for (; i + 16 <= PARAM_count; i += 16) {
        const uint8x16_t left = vld1q_u8(target + i);
        const uint8x16_t right = vld1q_u8(source + i);
        vst1q_u8(target + i, vmaxq_u8(left, right));
    }
    for (; i < PARAM_count; ++i) {
        target[i] = target[i] > source[i] ? target[i] : source[i];
    }
}
}
}
// @@end

// @@simd::arm::vminq_u8
namespace NS_simd {
namespace NS_arm {
FRANKO_INLINE void FUNC_vminq_u8_7243e039(Franko_Fat_Pointer<uint8_t> PARAM_target, Franko_Fat_Pointer<uint8_t> PARAM_source, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint8_t* target = PARAM_target.data;
    uint32_t target_length = PARAM_target.count;
    if (target == nullptr && PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = PARAM_source.data;
    uint32_t source_length = PARAM_source.count;
    if (source == nullptr && PARAM_count != 0) {
        franko_panic("simd: null source");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > source_length || PARAM_count > source_length - PARAM_src_start) {
        franko_panic("simd: source range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return;
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (target == source && PARAM_dst_start != PARAM_src_start) {
        franko_panic("simd: overlapping target and source ranges");
    }
#endif
    target += PARAM_dst_start;
    source += PARAM_src_start;
    uint32_t i = 0;
    for (; i + 16 <= PARAM_count; i += 16) {
        const uint8x16_t left = vld1q_u8(target + i);
        const uint8x16_t right = vld1q_u8(source + i);
        vst1q_u8(target + i, vminq_u8(left, right));
    }
    for (; i < PARAM_count; ++i) {
        target[i] = target[i] < source[i] ? target[i] : source[i];
    }
}
}
}
// @@end

// @@simd::x86::add_epi8
namespace NS_simd {
namespace NS_x86 {
FRANKO_INLINE void FUNC_add_epi8_7243e039(Franko_Fat_Pointer<uint8_t> PARAM_target, Franko_Fat_Pointer<uint8_t> PARAM_source, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint8_t* target = PARAM_target.data;
    uint32_t target_length = PARAM_target.count;
    if (target == nullptr && PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = PARAM_source.data;
    uint32_t source_length = PARAM_source.count;
    if (source == nullptr && PARAM_count != 0) {
        franko_panic("simd: null source");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > source_length || PARAM_count > source_length - PARAM_src_start) {
        franko_panic("simd: source range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return;
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (target == source && PARAM_dst_start != PARAM_src_start) {
        franko_panic("simd: overlapping target and source ranges");
    }
#endif
    target += PARAM_dst_start;
    source += PARAM_src_start;
    uint32_t i = 0;
#if defined(__AVX2__)
    for (; i + 32 <= PARAM_count; i += 32) {
        const __m256i left = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(target + i));
        const __m256i right = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(source + i));
        _mm256_storeu_si256(reinterpret_cast<__m256i*>(target + i), _mm256_add_epi8(left, right));
    }
#endif
    for (; i + 16 <= PARAM_count; i += 16) {
        const __m128i left = _mm_loadu_si128(reinterpret_cast<const __m128i*>(target + i));
        const __m128i right = _mm_loadu_si128(reinterpret_cast<const __m128i*>(source + i));
        _mm_storeu_si128(reinterpret_cast<__m128i*>(target + i), _mm_add_epi8(left, right));
    }
    for (; i < PARAM_count; ++i) {
        target[i] = static_cast<uint8_t>(target[i] + source[i]);
    }
}
}
}
// @@end

// @@simd::x86::sub_epi8
namespace NS_simd {
namespace NS_x86 {
FRANKO_INLINE void FUNC_sub_epi8_7243e039(Franko_Fat_Pointer<uint8_t> PARAM_target, Franko_Fat_Pointer<uint8_t> PARAM_source, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint8_t* target = PARAM_target.data;
    uint32_t target_length = PARAM_target.count;
    if (target == nullptr && PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = PARAM_source.data;
    uint32_t source_length = PARAM_source.count;
    if (source == nullptr && PARAM_count != 0) {
        franko_panic("simd: null source");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > source_length || PARAM_count > source_length - PARAM_src_start) {
        franko_panic("simd: source range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return;
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (target == source && PARAM_dst_start != PARAM_src_start) {
        franko_panic("simd: overlapping target and source ranges");
    }
#endif
    target += PARAM_dst_start;
    source += PARAM_src_start;
    uint32_t i = 0;
#if defined(__AVX2__)
    for (; i + 32 <= PARAM_count; i += 32) {
        const __m256i left = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(target + i));
        const __m256i right = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(source + i));
        _mm256_storeu_si256(reinterpret_cast<__m256i*>(target + i), _mm256_sub_epi8(left, right));
    }
#endif
    for (; i + 16 <= PARAM_count; i += 16) {
        const __m128i left = _mm_loadu_si128(reinterpret_cast<const __m128i*>(target + i));
        const __m128i right = _mm_loadu_si128(reinterpret_cast<const __m128i*>(source + i));
        _mm_storeu_si128(reinterpret_cast<__m128i*>(target + i), _mm_sub_epi8(left, right));
    }
    for (; i < PARAM_count; ++i) {
        target[i] = static_cast<uint8_t>(target[i] - source[i]);
    }
}
}
}
// @@end

// @@simd::x86::avg_epu8
namespace NS_simd {
namespace NS_x86 {
FRANKO_INLINE void FUNC_avg_epu8_7243e039(Franko_Fat_Pointer<uint8_t> PARAM_target, Franko_Fat_Pointer<uint8_t> PARAM_source, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint8_t* target = PARAM_target.data;
    uint32_t target_length = PARAM_target.count;
    if (target == nullptr && PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = PARAM_source.data;
    uint32_t source_length = PARAM_source.count;
    if (source == nullptr && PARAM_count != 0) {
        franko_panic("simd: null source");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > source_length || PARAM_count > source_length - PARAM_src_start) {
        franko_panic("simd: source range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return;
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (target == source && PARAM_dst_start != PARAM_src_start) {
        franko_panic("simd: overlapping target and source ranges");
    }
#endif
    target += PARAM_dst_start;
    source += PARAM_src_start;
    uint32_t i = 0;
#if defined(__AVX2__)
    for (; i + 32 <= PARAM_count; i += 32) {
        const __m256i left = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(target + i));
        const __m256i right = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(source + i));
        _mm256_storeu_si256(reinterpret_cast<__m256i*>(target + i), _mm256_avg_epu8(left, right));
    }
#endif
    for (; i + 16 <= PARAM_count; i += 16) {
        const __m128i left = _mm_loadu_si128(reinterpret_cast<const __m128i*>(target + i));
        const __m128i right = _mm_loadu_si128(reinterpret_cast<const __m128i*>(source + i));
        _mm_storeu_si128(reinterpret_cast<__m128i*>(target + i), _mm_avg_epu8(left, right));
    }
    for (; i < PARAM_count; ++i) {
        target[i] = static_cast<uint8_t>((static_cast<uint32_t>(target[i]) + source[i] + 1) >> 1);
    }
}
}
}
// @@end

// @@simd::x86::blendv_epi8
namespace NS_simd {
namespace NS_x86 {
FRANKO_INLINE void FUNC_blendv_epi8_bea41bc9(Franko_Fat_Pointer<uint8_t> PARAM_target, Franko_Fat_Pointer<uint8_t> PARAM_source, Franko_Fat_Pointer<uint8_t> PARAM_mask, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_mask_start, uint32_t PARAM_count)
{
    uint8_t* target = PARAM_target.data;
    uint32_t target_length = PARAM_target.count;
    if (target == nullptr && PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = PARAM_source.data;
    uint32_t source_length = PARAM_source.count;
    if (source == nullptr && PARAM_count != 0) {
        franko_panic("simd: null source");
    }
    const uint8_t* mask = PARAM_mask.data;
    uint32_t mask_length = PARAM_mask.count;
    if (mask == nullptr && PARAM_count != 0) {
        franko_panic("simd: null mask");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > source_length || PARAM_count > source_length - PARAM_src_start) {
        franko_panic("simd: source range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_mask_start > mask_length || PARAM_count > mask_length - PARAM_mask_start) {
        franko_panic("simd: mask range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return;
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (target == source && PARAM_dst_start != PARAM_src_start) {
        franko_panic("simd: overlapping target and source ranges");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (target == mask && PARAM_dst_start != PARAM_mask_start) {
        franko_panic("simd: overlapping target and mask ranges");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (source == mask && PARAM_src_start != PARAM_mask_start) {
        franko_panic("simd: overlapping source and mask ranges");
    }
#endif
    target += PARAM_dst_start;
    source += PARAM_src_start;
    mask += PARAM_mask_start;
    uint32_t i = 0;
#if defined(__AVX2__)
    for (; i + 32 <= PARAM_count; i += 32) {
        const __m256i left = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(target + i));
        const __m256i right = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(source + i));
        const __m256i selector = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(mask + i));
        _mm256_storeu_si256(reinterpret_cast<__m256i*>(target + i), _mm256_blendv_epi8(left, right, selector));
    }
#endif
#if defined(__SSE4_1__)
    for (; i + 16 <= PARAM_count; i += 16) {
        const __m128i left = _mm_loadu_si128(reinterpret_cast<const __m128i*>(target + i));
        const __m128i right = _mm_loadu_si128(reinterpret_cast<const __m128i*>(source + i));
        const __m128i selector = _mm_loadu_si128(reinterpret_cast<const __m128i*>(mask + i));
        _mm_storeu_si128(reinterpret_cast<__m128i*>(target + i), _mm_blendv_epi8(left, right, selector));
    }
#endif
    for (; i < PARAM_count; ++i) {
        target[i] = (mask[i] & 0x80) != 0 ? source[i] : target[i];
    }
}
}
}
// @@end

// @@simd::x86::alignr_epi8
namespace NS_simd {
namespace NS_x86 {
FRANKO_INLINE void FUNC_alignr_epi8_62d3997c(Franko_Fat_Pointer<uint8_t> PARAM_target, Franko_Fat_Pointer<uint8_t> PARAM_source, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count, uint32_t PARAM_shift)
{
    uint8_t* target = PARAM_target.data;
    uint32_t target_length = PARAM_target.count;
    if (target == nullptr && PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = PARAM_source.data;
    uint32_t source_length = PARAM_source.count;
    if (source == nullptr && PARAM_count != 0) {
        franko_panic("simd: null source");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > source_length || PARAM_count > source_length - PARAM_src_start) {
        franko_panic("simd: source range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return;
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (target == source && PARAM_dst_start != PARAM_src_start) {
        franko_panic("simd: overlapping target and source ranges");
    }
#endif
    target += PARAM_dst_start;
    source += PARAM_src_start;
    uint32_t i = 0;
    if (PARAM_shift < 16) {
#if defined(__AVX2__)
        switch (PARAM_shift) {
        case 0:
            for (; i + 32 <= PARAM_count; i += 32) {
                const __m256i high = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(target + i));
                const __m256i low = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(source + i));
                _mm256_storeu_si256(reinterpret_cast<__m256i*>(target + i), _mm256_alignr_epi8(high, low, 0));
            }
            break;
        case 1:
            for (; i + 32 <= PARAM_count; i += 32) {
                const __m256i high = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(target + i));
                const __m256i low = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(source + i));
                _mm256_storeu_si256(reinterpret_cast<__m256i*>(target + i), _mm256_alignr_epi8(high, low, 1));
            }
            break;
        case 2:
            for (; i + 32 <= PARAM_count; i += 32) {
                const __m256i high = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(target + i));
                const __m256i low = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(source + i));
                _mm256_storeu_si256(reinterpret_cast<__m256i*>(target + i), _mm256_alignr_epi8(high, low, 2));
            }
            break;
        case 3:
            for (; i + 32 <= PARAM_count; i += 32) {
                const __m256i high = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(target + i));
                const __m256i low = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(source + i));
                _mm256_storeu_si256(reinterpret_cast<__m256i*>(target + i), _mm256_alignr_epi8(high, low, 3));
            }
            break;
        case 4:
            for (; i + 32 <= PARAM_count; i += 32) {
                const __m256i high = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(target + i));
                const __m256i low = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(source + i));
                _mm256_storeu_si256(reinterpret_cast<__m256i*>(target + i), _mm256_alignr_epi8(high, low, 4));
            }
            break;
        case 5:
            for (; i + 32 <= PARAM_count; i += 32) {
                const __m256i high = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(target + i));
                const __m256i low = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(source + i));
                _mm256_storeu_si256(reinterpret_cast<__m256i*>(target + i), _mm256_alignr_epi8(high, low, 5));
            }
            break;
        case 6:
            for (; i + 32 <= PARAM_count; i += 32) {
                const __m256i high = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(target + i));
                const __m256i low = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(source + i));
                _mm256_storeu_si256(reinterpret_cast<__m256i*>(target + i), _mm256_alignr_epi8(high, low, 6));
            }
            break;
        case 7:
            for (; i + 32 <= PARAM_count; i += 32) {
                const __m256i high = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(target + i));
                const __m256i low = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(source + i));
                _mm256_storeu_si256(reinterpret_cast<__m256i*>(target + i), _mm256_alignr_epi8(high, low, 7));
            }
            break;
        case 8:
            for (; i + 32 <= PARAM_count; i += 32) {
                const __m256i high = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(target + i));
                const __m256i low = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(source + i));
                _mm256_storeu_si256(reinterpret_cast<__m256i*>(target + i), _mm256_alignr_epi8(high, low, 8));
            }
            break;
        case 9:
            for (; i + 32 <= PARAM_count; i += 32) {
                const __m256i high = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(target + i));
                const __m256i low = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(source + i));
                _mm256_storeu_si256(reinterpret_cast<__m256i*>(target + i), _mm256_alignr_epi8(high, low, 9));
            }
            break;
        case 10:
            for (; i + 32 <= PARAM_count; i += 32) {
                const __m256i high = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(target + i));
                const __m256i low = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(source + i));
                _mm256_storeu_si256(reinterpret_cast<__m256i*>(target + i), _mm256_alignr_epi8(high, low, 10));
            }
            break;
        case 11:
            for (; i + 32 <= PARAM_count; i += 32) {
                const __m256i high = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(target + i));
                const __m256i low = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(source + i));
                _mm256_storeu_si256(reinterpret_cast<__m256i*>(target + i), _mm256_alignr_epi8(high, low, 11));
            }
            break;
        case 12:
            for (; i + 32 <= PARAM_count; i += 32) {
                const __m256i high = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(target + i));
                const __m256i low = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(source + i));
                _mm256_storeu_si256(reinterpret_cast<__m256i*>(target + i), _mm256_alignr_epi8(high, low, 12));
            }
            break;
        case 13:
            for (; i + 32 <= PARAM_count; i += 32) {
                const __m256i high = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(target + i));
                const __m256i low = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(source + i));
                _mm256_storeu_si256(reinterpret_cast<__m256i*>(target + i), _mm256_alignr_epi8(high, low, 13));
            }
            break;
        case 14:
            for (; i + 32 <= PARAM_count; i += 32) {
                const __m256i high = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(target + i));
                const __m256i low = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(source + i));
                _mm256_storeu_si256(reinterpret_cast<__m256i*>(target + i), _mm256_alignr_epi8(high, low, 14));
            }
            break;
        case 15:
            for (; i + 32 <= PARAM_count; i += 32) {
                const __m256i high = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(target + i));
                const __m256i low = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(source + i));
                _mm256_storeu_si256(reinterpret_cast<__m256i*>(target + i), _mm256_alignr_epi8(high, low, 15));
            }
            break;
        }
#endif
#if defined(__SSSE3__)
        switch (PARAM_shift) {
        case 0:
            for (; i + 16 <= PARAM_count; i += 16) {
                const __m128i high = _mm_loadu_si128(reinterpret_cast<const __m128i*>(target + i));
                const __m128i low = _mm_loadu_si128(reinterpret_cast<const __m128i*>(source + i));
                _mm_storeu_si128(reinterpret_cast<__m128i*>(target + i), _mm_alignr_epi8(high, low, 0));
            }
            break;
        case 1:
            for (; i + 16 <= PARAM_count; i += 16) {
                const __m128i high = _mm_loadu_si128(reinterpret_cast<const __m128i*>(target + i));
                const __m128i low = _mm_loadu_si128(reinterpret_cast<const __m128i*>(source + i));
                _mm_storeu_si128(reinterpret_cast<__m128i*>(target + i), _mm_alignr_epi8(high, low, 1));
            }
            break;
        case 2:
            for (; i + 16 <= PARAM_count; i += 16) {
                const __m128i high = _mm_loadu_si128(reinterpret_cast<const __m128i*>(target + i));
                const __m128i low = _mm_loadu_si128(reinterpret_cast<const __m128i*>(source + i));
                _mm_storeu_si128(reinterpret_cast<__m128i*>(target + i), _mm_alignr_epi8(high, low, 2));
            }
            break;
        case 3:
            for (; i + 16 <= PARAM_count; i += 16) {
                const __m128i high = _mm_loadu_si128(reinterpret_cast<const __m128i*>(target + i));
                const __m128i low = _mm_loadu_si128(reinterpret_cast<const __m128i*>(source + i));
                _mm_storeu_si128(reinterpret_cast<__m128i*>(target + i), _mm_alignr_epi8(high, low, 3));
            }
            break;
        case 4:
            for (; i + 16 <= PARAM_count; i += 16) {
                const __m128i high = _mm_loadu_si128(reinterpret_cast<const __m128i*>(target + i));
                const __m128i low = _mm_loadu_si128(reinterpret_cast<const __m128i*>(source + i));
                _mm_storeu_si128(reinterpret_cast<__m128i*>(target + i), _mm_alignr_epi8(high, low, 4));
            }
            break;
        case 5:
            for (; i + 16 <= PARAM_count; i += 16) {
                const __m128i high = _mm_loadu_si128(reinterpret_cast<const __m128i*>(target + i));
                const __m128i low = _mm_loadu_si128(reinterpret_cast<const __m128i*>(source + i));
                _mm_storeu_si128(reinterpret_cast<__m128i*>(target + i), _mm_alignr_epi8(high, low, 5));
            }
            break;
        case 6:
            for (; i + 16 <= PARAM_count; i += 16) {
                const __m128i high = _mm_loadu_si128(reinterpret_cast<const __m128i*>(target + i));
                const __m128i low = _mm_loadu_si128(reinterpret_cast<const __m128i*>(source + i));
                _mm_storeu_si128(reinterpret_cast<__m128i*>(target + i), _mm_alignr_epi8(high, low, 6));
            }
            break;
        case 7:
            for (; i + 16 <= PARAM_count; i += 16) {
                const __m128i high = _mm_loadu_si128(reinterpret_cast<const __m128i*>(target + i));
                const __m128i low = _mm_loadu_si128(reinterpret_cast<const __m128i*>(source + i));
                _mm_storeu_si128(reinterpret_cast<__m128i*>(target + i), _mm_alignr_epi8(high, low, 7));
            }
            break;
        case 8:
            for (; i + 16 <= PARAM_count; i += 16) {
                const __m128i high = _mm_loadu_si128(reinterpret_cast<const __m128i*>(target + i));
                const __m128i low = _mm_loadu_si128(reinterpret_cast<const __m128i*>(source + i));
                _mm_storeu_si128(reinterpret_cast<__m128i*>(target + i), _mm_alignr_epi8(high, low, 8));
            }
            break;
        case 9:
            for (; i + 16 <= PARAM_count; i += 16) {
                const __m128i high = _mm_loadu_si128(reinterpret_cast<const __m128i*>(target + i));
                const __m128i low = _mm_loadu_si128(reinterpret_cast<const __m128i*>(source + i));
                _mm_storeu_si128(reinterpret_cast<__m128i*>(target + i), _mm_alignr_epi8(high, low, 9));
            }
            break;
        case 10:
            for (; i + 16 <= PARAM_count; i += 16) {
                const __m128i high = _mm_loadu_si128(reinterpret_cast<const __m128i*>(target + i));
                const __m128i low = _mm_loadu_si128(reinterpret_cast<const __m128i*>(source + i));
                _mm_storeu_si128(reinterpret_cast<__m128i*>(target + i), _mm_alignr_epi8(high, low, 10));
            }
            break;
        case 11:
            for (; i + 16 <= PARAM_count; i += 16) {
                const __m128i high = _mm_loadu_si128(reinterpret_cast<const __m128i*>(target + i));
                const __m128i low = _mm_loadu_si128(reinterpret_cast<const __m128i*>(source + i));
                _mm_storeu_si128(reinterpret_cast<__m128i*>(target + i), _mm_alignr_epi8(high, low, 11));
            }
            break;
        case 12:
            for (; i + 16 <= PARAM_count; i += 16) {
                const __m128i high = _mm_loadu_si128(reinterpret_cast<const __m128i*>(target + i));
                const __m128i low = _mm_loadu_si128(reinterpret_cast<const __m128i*>(source + i));
                _mm_storeu_si128(reinterpret_cast<__m128i*>(target + i), _mm_alignr_epi8(high, low, 12));
            }
            break;
        case 13:
            for (; i + 16 <= PARAM_count; i += 16) {
                const __m128i high = _mm_loadu_si128(reinterpret_cast<const __m128i*>(target + i));
                const __m128i low = _mm_loadu_si128(reinterpret_cast<const __m128i*>(source + i));
                _mm_storeu_si128(reinterpret_cast<__m128i*>(target + i), _mm_alignr_epi8(high, low, 13));
            }
            break;
        case 14:
            for (; i + 16 <= PARAM_count; i += 16) {
                const __m128i high = _mm_loadu_si128(reinterpret_cast<const __m128i*>(target + i));
                const __m128i low = _mm_loadu_si128(reinterpret_cast<const __m128i*>(source + i));
                _mm_storeu_si128(reinterpret_cast<__m128i*>(target + i), _mm_alignr_epi8(high, low, 14));
            }
            break;
        case 15:
            for (; i + 16 <= PARAM_count; i += 16) {
                const __m128i high = _mm_loadu_si128(reinterpret_cast<const __m128i*>(target + i));
                const __m128i low = _mm_loadu_si128(reinterpret_cast<const __m128i*>(source + i));
                _mm_storeu_si128(reinterpret_cast<__m128i*>(target + i), _mm_alignr_epi8(high, low, 15));
            }
            break;
        }
#else
        for (uint32_t j = 0; j + 16 <= PARAM_count; j += 16) {
            uint8_t block[16];
            std::memcpy(block, target + j, 16);
            for (uint32_t lane = 0; lane < 16; ++lane) {
                const uint32_t offset = lane + PARAM_shift;
                target[j + lane] = offset < 16 ? source[j + offset] : block[offset - 16];
            }
        }
        i = PARAM_count - (PARAM_count % 16);
#endif
        if (i < PARAM_count) {
            const uint32_t available = PARAM_count - i;
            uint8_t block[16] = {0};
            std::memcpy(block, target + i, available);
            for (uint32_t lane = 0; lane < available; ++lane) {
                const uint32_t offset = lane + PARAM_shift;
                if (offset < 16) {
                    const uint32_t index = i + offset;
                    target[i + lane] = index < PARAM_count ? source[index] : 0;
                } else {
                    target[i + lane] = block[offset - 16];
                }
            }
        }
    } else {
        for (; i < PARAM_count; ++i) {
            target[i] = 0;
        }
    }
}
}
}
// @@end

// @@simd::x86::unpacklo_epi8
namespace NS_simd {
namespace NS_x86 {
FRANKO_INLINE void FUNC_unpacklo_epi8_7243e039(Franko_Fat_Pointer<uint8_t> PARAM_target, Franko_Fat_Pointer<uint8_t> PARAM_source, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint8_t* target = PARAM_target.data;
    uint32_t target_length = PARAM_target.count;
    if (target == nullptr && PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = PARAM_source.data;
    uint32_t source_length = PARAM_source.count;
    if (source == nullptr && PARAM_count != 0) {
        franko_panic("simd: null source");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > source_length || PARAM_count > source_length - PARAM_src_start) {
        franko_panic("simd: source range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return;
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (target == source && PARAM_dst_start != PARAM_src_start) {
        franko_panic("simd: overlapping target and source ranges");
    }
#endif
    target += PARAM_dst_start;
    source += PARAM_src_start;
    uint32_t i = 0;
#if defined(__AVX2__)
    for (; i + 32 <= PARAM_count; i += 32) {
        const __m256i left = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(target + i));
        const __m256i right = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(source + i));
        _mm256_storeu_si256(reinterpret_cast<__m256i*>(target + i), _mm256_unpacklo_epi8(left, right));
    }
#endif
    for (; i + 16 <= PARAM_count; i += 16) {
        const __m128i left = _mm_loadu_si128(reinterpret_cast<const __m128i*>(target + i));
        const __m128i right = _mm_loadu_si128(reinterpret_cast<const __m128i*>(source + i));
        _mm_storeu_si128(reinterpret_cast<__m128i*>(target + i), _mm_unpacklo_epi8(left, right));
    }
    if (i < PARAM_count) {
        const uint32_t chunk = i;
        uint8_t block[16] = {0};
        std::memcpy(block, target + chunk, PARAM_count - chunk);
        for (; i < PARAM_count; ++i) {
            const uint32_t lane = i - chunk;
            if (lane % 2 == 0) {
                target[i] = block[lane / 2];
            } else {
                target[i] = source[chunk + lane / 2];
            }
        }
    }
}
}
}
// @@end

// @@simd::x86::unpackhi_epi8
namespace NS_simd {
namespace NS_x86 {
FRANKO_INLINE void FUNC_unpackhi_epi8_7243e039(Franko_Fat_Pointer<uint8_t> PARAM_target, Franko_Fat_Pointer<uint8_t> PARAM_source, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint8_t* target = PARAM_target.data;
    uint32_t target_length = PARAM_target.count;
    if (target == nullptr && PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = PARAM_source.data;
    uint32_t source_length = PARAM_source.count;
    if (source == nullptr && PARAM_count != 0) {
        franko_panic("simd: null source");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > source_length || PARAM_count > source_length - PARAM_src_start) {
        franko_panic("simd: source range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return;
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (target == source && PARAM_dst_start != PARAM_src_start) {
        franko_panic("simd: overlapping target and source ranges");
    }
#endif
    target += PARAM_dst_start;
    source += PARAM_src_start;
    uint32_t i = 0;
#if defined(__AVX2__)
    for (; i + 32 <= PARAM_count; i += 32) {
        const __m256i left = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(target + i));
        const __m256i right = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(source + i));
        _mm256_storeu_si256(reinterpret_cast<__m256i*>(target + i), _mm256_unpackhi_epi8(left, right));
    }
#endif
    for (; i + 16 <= PARAM_count; i += 16) {
        const __m128i left = _mm_loadu_si128(reinterpret_cast<const __m128i*>(target + i));
        const __m128i right = _mm_loadu_si128(reinterpret_cast<const __m128i*>(source + i));
        _mm_storeu_si128(reinterpret_cast<__m128i*>(target + i), _mm_unpackhi_epi8(left, right));
    }
    if (i < PARAM_count) {
        const uint32_t chunk = i;
        const uint32_t available = PARAM_count - chunk;
        uint8_t block[16] = {0};
        std::memcpy(block, target + chunk, available);
        for (; i < PARAM_count; ++i) {
            const uint32_t lane = i - chunk;
            const uint32_t input_lane = 8 + lane / 2;
            if (lane % 2 == 0) {
                target[i] = block[input_lane];
            } else {
                target[i] = input_lane < available ? source[chunk + input_lane] : 0;
            }
        }
    }
}
}
}
// @@end

// @@simd::x86::packus_epi16
namespace NS_simd {
namespace NS_x86 {
FRANKO_INLINE void FUNC_packus_epi16_ed547b8f(Franko_Fat_Pointer<uint8_t> PARAM_target, Franko_Fat_Pointer<int16_t> PARAM_source, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint8_t* target = PARAM_target.data;
    uint32_t target_length = PARAM_target.count;
    if (target == nullptr && PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const int16_t* source = PARAM_source.data;
    uint32_t source_length = PARAM_source.count;
    if (source == nullptr && PARAM_count != 0) {
        franko_panic("simd: null source");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > source_length || PARAM_count > source_length - PARAM_src_start) {
        franko_panic("simd: source range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return;
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (static_cast<const void*>(target) == static_cast<const void*>(source)
            && PARAM_dst_start != PARAM_src_start) {
        franko_panic("simd: overlapping target and source ranges");
    }
#endif
    target += PARAM_dst_start;
    source += PARAM_src_start;
    uint32_t i = 0;
#if defined(__AVX2__)
    for (; i + 16 <= PARAM_count; i += 16) {
        const __m256i input = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(source + i));
        const __m256i packed = _mm256_packus_epi16(input, _mm256_setzero_si256());
        const __m256i compact = _mm256_permute4x64_epi64(packed, 0xD8);
        _mm_storeu_si128(reinterpret_cast<__m128i*>(target + i), _mm256_castsi256_si128(compact));
    }
#endif
    for (; i + 8 <= PARAM_count; i += 8) {
        const __m128i input = _mm_loadu_si128(reinterpret_cast<const __m128i*>(source + i));
        const __m128i packed = _mm_packus_epi16(input, input);
        _mm_storel_epi64(reinterpret_cast<__m128i*>(target + i), packed);
    }
    for (; i < PARAM_count; ++i) {
        const int32_t value = source[i];
        target[i] = static_cast<uint8_t>(value < 0 ? 0 : value > 255 ? 255 : value);
    }
}
}
}
// @@end

// @@simd::x86::sad_epu8
namespace NS_simd {
namespace NS_x86 {
FRANKO_INLINE uint64_t FUNC_sad_epu8_4812cf2a(Franko_Fat_Pointer<uint8_t> PARAM_left, Franko_Fat_Pointer<uint8_t> PARAM_right, uint32_t PARAM_left_start, uint32_t PARAM_right_start, uint32_t PARAM_count)
{
    const uint8_t* left = PARAM_left.data;
    uint32_t left_length = PARAM_left.count;
    if (left == nullptr && PARAM_count != 0) {
        franko_panic("simd: null left");
    }
    const uint8_t* right = PARAM_right.data;
    uint32_t right_length = PARAM_right.count;
    if (right == nullptr && PARAM_count != 0) {
        franko_panic("simd: null right");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_left_start > left_length || PARAM_count > left_length - PARAM_left_start) {
        franko_panic("simd: left range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_right_start > right_length || PARAM_count > right_length - PARAM_right_start) {
        franko_panic("simd: right range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return 0;
    }
    left += PARAM_left_start;
    right += PARAM_right_start;
    uint64_t result = 0;
    uint32_t i = 0;
#if defined(__AVX2__)
    for (; i + 32 <= PARAM_count; i += 32) {
        const __m256i left_wide = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(left + i));
        const __m256i right_wide = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(right + i));
        const __m256i wide = _mm256_sad_epu8(left_wide, right_wide);
        uint64_t sums[4];
        _mm256_storeu_si256(reinterpret_cast<__m256i*>(sums), wide);
        result += sums[0] + sums[1] + sums[2] + sums[3];
    }
#endif
    for (; i + 16 <= PARAM_count; i += 16) {
        const __m128i left_bytes = _mm_loadu_si128(reinterpret_cast<const __m128i*>(left + i));
        const __m128i right_bytes = _mm_loadu_si128(reinterpret_cast<const __m128i*>(right + i));
        const __m128i block = _mm_sad_epu8(left_bytes, right_bytes);
        uint64_t sums[2];
        _mm_storeu_si128(reinterpret_cast<__m128i*>(sums), block);
        result += sums[0] + sums[1];
    }
    for (; i < PARAM_count; ++i) {
        const int32_t difference = static_cast<int32_t>(left[i]) - static_cast<int32_t>(right[i]);
        result += difference < 0 ? static_cast<uint64_t>(-difference) : static_cast<uint64_t>(difference);
    }
    return result;
}
}
}
// @@end

// @@simd::x86::abs_epi8
namespace NS_simd {
namespace NS_x86 {
FRANKO_INLINE void FUNC_abs_epi8_6c5347dd(Franko_Fat_Pointer<int8_t> PARAM_target, Franko_Fat_Pointer<int8_t> PARAM_source, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    int8_t* target = PARAM_target.data;
    uint32_t target_length = PARAM_target.count;
    if (target == nullptr && PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const int8_t* source = PARAM_source.data;
    uint32_t source_length = PARAM_source.count;
    if (source == nullptr && PARAM_count != 0) {
        franko_panic("simd: null source");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > source_length || PARAM_count > source_length - PARAM_src_start) {
        franko_panic("simd: source range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return;
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (target == source && PARAM_dst_start != PARAM_src_start) {
        franko_panic("simd: overlapping target and source ranges");
    }
#endif
    target += PARAM_dst_start;
    source += PARAM_src_start;
    uint32_t i = 0;
#if defined(__AVX2__)
    for (; i + 32 <= PARAM_count; i += 32) {
        const __m256i input = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(source + i));
        _mm256_storeu_si256(reinterpret_cast<__m256i*>(target + i), _mm256_abs_epi8(input));
    }
#endif
#if defined(__SSSE3__)
    for (; i + 16 <= PARAM_count; i += 16) {
        const __m128i input = _mm_loadu_si128(reinterpret_cast<const __m128i*>(source + i));
        _mm_storeu_si128(reinterpret_cast<__m128i*>(target + i), _mm_abs_epi8(input));
    }
#endif
    for (; i < PARAM_count; ++i) {
        const int32_t value = source[i];
        target[i] = static_cast<int8_t>(value < 0 ? static_cast<uint8_t>(-value) : static_cast<uint8_t>(value));
    }
}
}
}
// @@end

// @@simd::x86::shuffle_epi8
namespace NS_simd {
namespace NS_x86 {
FRANKO_INLINE void FUNC_shuffle_epi8_7243e039(Franko_Fat_Pointer<uint8_t> PARAM_target, Franko_Fat_Pointer<uint8_t> PARAM_indices, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint8_t* target = PARAM_target.data;
    uint32_t target_length = PARAM_target.count;
    if (target == nullptr && PARAM_count != 0) {
        franko_panic("simd: null target");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
    const uint8_t* indices = PARAM_indices.data;
    uint32_t indices_length = PARAM_indices.count;
    if (indices == nullptr && PARAM_count != 0) {
        franko_panic("simd: null indices");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > indices_length || PARAM_count > indices_length - PARAM_src_start) {
        franko_panic("simd: indices range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return;
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (target == indices && PARAM_dst_start != PARAM_src_start) {
        franko_panic("simd: overlapping target and indices ranges");
    }
#endif
    target += PARAM_dst_start;
    indices += PARAM_src_start;
    uint32_t i = 0;
#if defined(__AVX2__)
    for (; i + 32 <= PARAM_count; i += 32) {
        const __m256i bytes = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(target + i));
        const __m256i control = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(indices + i));
        _mm256_storeu_si256(reinterpret_cast<__m256i*>(target + i), _mm256_shuffle_epi8(bytes, control));
    }
#endif
#if defined(__SSSE3__)
    for (; i + 16 <= PARAM_count; i += 16) {
        const __m128i bytes = _mm_loadu_si128(reinterpret_cast<const __m128i*>(target + i));
        const __m128i control = _mm_loadu_si128(reinterpret_cast<const __m128i*>(indices + i));
        _mm_storeu_si128(reinterpret_cast<__m128i*>(target + i), _mm_shuffle_epi8(bytes, control));
    }
#endif
    for (; i + 16 <= PARAM_count; i += 16) {
        uint8_t block[16];
        std::memcpy(block, target + i, 16);
        for (uint32_t j = 0; j < 16; ++j) {
            const uint8_t index = indices[i + j];
            target[i + j] = (index & 0x80u) != 0 ? 0 : block[index & 0x0Fu];
        }
    }
}
}
}
// @@end

// @@simd::x86::aesenc_si256
namespace NS_simd {
namespace NS_x86 {
FRANKO_INLINE int32_t FUNC_aesenc_si256_697d55d9(Franko_Fat_Pointer<uint8_t> PARAM_target, Franko_Fat_Pointer<uint8_t> PARAM_round_key, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint8_t* target = PARAM_target.data;
    uint32_t target_length = PARAM_target.count;
    if (target == nullptr && PARAM_count != 0) {
        franko_panic("simd: null target");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
    const uint8_t* round_key = PARAM_round_key.data;
    uint32_t round_key_length = PARAM_round_key.count;
    if (round_key == nullptr && PARAM_count != 0) {
        franko_panic("simd: null round_key");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > round_key_length
            || (PARAM_count != 0 && 16 > round_key_length - PARAM_src_start)) {
        franko_panic("simd: round key range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return 0;
    }
    target += PARAM_dst_start;
    round_key += PARAM_src_start;
#if defined(__VAES__) && defined(__AVX2__)
    const __m256i wide_key = _mm256_broadcastsi128_si256(_mm_loadu_si128(reinterpret_cast<const __m128i*>(round_key)));
    uint32_t i = 0;
    for (; i + 32 <= PARAM_count; i += 32) {
        const __m256i wide = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(target + i));
        _mm256_storeu_si256(reinterpret_cast<__m256i*>(target + i), _mm256_aesenc_epi128(wide, wide_key));
    }
    for (; i + 16 <= PARAM_count; i += 16) {
        const __m128i block = _mm_loadu_si128(reinterpret_cast<const __m128i*>(target + i));
        _mm_storeu_si128(reinterpret_cast<__m128i*>(target + i), _mm_aesenc_si128(block, _mm256_castsi256_si128(wide_key)));
    }
    return 0;
#elif defined(__AES__) || defined(_MSC_VER)
    const __m128i key = _mm_loadu_si128(reinterpret_cast<const __m128i*>(round_key));
    for (uint32_t i = 0; i + 16 <= PARAM_count; i += 16) {
        const __m128i block = _mm_loadu_si128(reinterpret_cast<const __m128i*>(target + i));
        _mm_storeu_si128(reinterpret_cast<__m128i*>(target + i), _mm_aesenc_si128(block, key));
    }
    return 0;
#else
    return 1;
#endif
}
}
}
// @@end

// @@simd::x86::aesdec_si256
namespace NS_simd {
namespace NS_x86 {
FRANKO_INLINE int32_t FUNC_aesdec_si256_697d55d9(Franko_Fat_Pointer<uint8_t> PARAM_target, Franko_Fat_Pointer<uint8_t> PARAM_round_key, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint8_t* target = PARAM_target.data;
    uint32_t target_length = PARAM_target.count;
    if (target == nullptr && PARAM_count != 0) {
        franko_panic("simd: null target");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
    const uint8_t* round_key = PARAM_round_key.data;
    uint32_t round_key_length = PARAM_round_key.count;
    if (round_key == nullptr && PARAM_count != 0) {
        franko_panic("simd: null round_key");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > round_key_length
            || (PARAM_count != 0 && 16 > round_key_length - PARAM_src_start)) {
        franko_panic("simd: round key range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return 0;
    }
    target += PARAM_dst_start;
    round_key += PARAM_src_start;
#if defined(__VAES__) && defined(__AVX2__)
    const __m256i wide_key = _mm256_broadcastsi128_si256(_mm_loadu_si128(reinterpret_cast<const __m128i*>(round_key)));
    uint32_t i = 0;
    for (; i + 32 <= PARAM_count; i += 32) {
        const __m256i wide = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(target + i));
        _mm256_storeu_si256(reinterpret_cast<__m256i*>(target + i), _mm256_aesdec_epi128(wide, wide_key));
    }
    for (; i + 16 <= PARAM_count; i += 16) {
        const __m128i block = _mm_loadu_si128(reinterpret_cast<const __m128i*>(target + i));
        _mm_storeu_si128(reinterpret_cast<__m128i*>(target + i), _mm_aesdec_si128(block, _mm256_castsi256_si128(wide_key)));
    }
    return 0;
#elif defined(__AES__) || defined(_MSC_VER)
    const __m128i key = _mm_loadu_si128(reinterpret_cast<const __m128i*>(round_key));
    for (uint32_t i = 0; i + 16 <= PARAM_count; i += 16) {
        const __m128i block = _mm_loadu_si128(reinterpret_cast<const __m128i*>(target + i));
        _mm_storeu_si128(reinterpret_cast<__m128i*>(target + i), _mm_aesdec_si128(block, key));
    }
    return 0;
#else
    return 1;
#endif
}
}
}
// @@end




// @@simd::arm::vaddq_u8
namespace NS_simd {
namespace NS_arm {
FRANKO_INLINE void FUNC_vaddq_u8_7243e039(Franko_Fat_Pointer<uint8_t> PARAM_target, Franko_Fat_Pointer<uint8_t> PARAM_source, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint8_t* target = PARAM_target.data;
    uint32_t target_length = PARAM_target.count;
    if (target == nullptr && PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = PARAM_source.data;
    uint32_t source_length = PARAM_source.count;
    if (source == nullptr && PARAM_count != 0) {
        franko_panic("simd: null source");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > source_length || PARAM_count > source_length - PARAM_src_start) {
        franko_panic("simd: source range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return;
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (target == source && PARAM_dst_start != PARAM_src_start) {
        franko_panic("simd: overlapping target and source ranges");
    }
#endif
    target += PARAM_dst_start;
    source += PARAM_src_start;
    uint32_t i = 0;
    for (; i + 16 <= PARAM_count; i += 16) {
        const uint8x16_t left = vld1q_u8(target + i);
        const uint8x16_t right = vld1q_u8(source + i);
        vst1q_u8(target + i, vaddq_u8(left, right));
    }
    for (; i < PARAM_count; ++i) {
        target[i] = static_cast<uint8_t>(target[i] + source[i]);
    }
}
}
}
// @@end

// @@simd::arm::vsubq_u8
namespace NS_simd {
namespace NS_arm {
FRANKO_INLINE void FUNC_vsubq_u8_7243e039(Franko_Fat_Pointer<uint8_t> PARAM_target, Franko_Fat_Pointer<uint8_t> PARAM_source, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint8_t* target = PARAM_target.data;
    uint32_t target_length = PARAM_target.count;
    if (target == nullptr && PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = PARAM_source.data;
    uint32_t source_length = PARAM_source.count;
    if (source == nullptr && PARAM_count != 0) {
        franko_panic("simd: null source");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > source_length || PARAM_count > source_length - PARAM_src_start) {
        franko_panic("simd: source range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return;
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (target == source && PARAM_dst_start != PARAM_src_start) {
        franko_panic("simd: overlapping target and source ranges");
    }
#endif
    target += PARAM_dst_start;
    source += PARAM_src_start;
    uint32_t i = 0;
    for (; i + 16 <= PARAM_count; i += 16) {
        const uint8x16_t left = vld1q_u8(target + i);
        const uint8x16_t right = vld1q_u8(source + i);
        vst1q_u8(target + i, vsubq_u8(left, right));
    }
    for (; i < PARAM_count; ++i) {
        target[i] = static_cast<uint8_t>(target[i] - source[i]);
    }
}
}
}
// @@end

// @@simd::arm::vrhaddq_u8
namespace NS_simd {
namespace NS_arm {
FRANKO_INLINE void FUNC_vrhaddq_u8_7243e039(Franko_Fat_Pointer<uint8_t> PARAM_target, Franko_Fat_Pointer<uint8_t> PARAM_source, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint8_t* target = PARAM_target.data;
    uint32_t target_length = PARAM_target.count;
    if (target == nullptr && PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = PARAM_source.data;
    uint32_t source_length = PARAM_source.count;
    if (source == nullptr && PARAM_count != 0) {
        franko_panic("simd: null source");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > source_length || PARAM_count > source_length - PARAM_src_start) {
        franko_panic("simd: source range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return;
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (target == source && PARAM_dst_start != PARAM_src_start) {
        franko_panic("simd: overlapping target and source ranges");
    }
#endif
    target += PARAM_dst_start;
    source += PARAM_src_start;
    uint32_t i = 0;
    for (; i + 16 <= PARAM_count; i += 16) {
        const uint8x16_t left = vld1q_u8(target + i);
        const uint8x16_t right = vld1q_u8(source + i);
        vst1q_u8(target + i, vrhaddq_u8(left, right));
    }
    for (; i < PARAM_count; ++i) {
        target[i] = static_cast<uint8_t>((static_cast<uint32_t>(target[i]) + source[i] + 1) >> 1);
    }
}
}
}
// @@end

// @@simd::arm::vbslq_u8
namespace NS_simd {
namespace NS_arm {
FRANKO_INLINE void FUNC_vbslq_u8_bea41bc9(Franko_Fat_Pointer<uint8_t> PARAM_target, Franko_Fat_Pointer<uint8_t> PARAM_source, Franko_Fat_Pointer<uint8_t> PARAM_mask, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_mask_start, uint32_t PARAM_count)
{
    uint8_t* target = PARAM_target.data;
    uint32_t target_length = PARAM_target.count;
    if (target == nullptr && PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = PARAM_source.data;
    uint32_t source_length = PARAM_source.count;
    if (source == nullptr && PARAM_count != 0) {
        franko_panic("simd: null source");
    }
    const uint8_t* mask = PARAM_mask.data;
    uint32_t mask_length = PARAM_mask.count;
    if (mask == nullptr && PARAM_count != 0) {
        franko_panic("simd: null mask");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > source_length || PARAM_count > source_length - PARAM_src_start) {
        franko_panic("simd: source range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_mask_start > mask_length || PARAM_count > mask_length - PARAM_mask_start) {
        franko_panic("simd: mask range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return;
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (target == source && PARAM_dst_start != PARAM_src_start) {
        franko_panic("simd: overlapping target and source ranges");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (target == mask && PARAM_dst_start != PARAM_mask_start) {
        franko_panic("simd: overlapping target and mask ranges");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (source == mask && PARAM_src_start != PARAM_mask_start) {
        franko_panic("simd: overlapping source and mask ranges");
    }
#endif
    target += PARAM_dst_start;
    source += PARAM_src_start;
    mask += PARAM_mask_start;
    uint32_t i = 0;
    for (; i + 16 <= PARAM_count; i += 16) {
        const uint8x16_t left = vld1q_u8(target + i);
        const uint8x16_t right = vld1q_u8(source + i);
        const uint8x16_t selector = vld1q_u8(mask + i);
        vst1q_u8(target + i, vbslq_u8(selector, left, right));
    }
    for (; i < PARAM_count; ++i) {
        target[i] = static_cast<uint8_t>((target[i] & mask[i]) | (source[i] & static_cast<uint8_t>(~mask[i])));
    }
}
}
}
// @@end

// @@simd::arm::vextq_u8
namespace NS_simd {
namespace NS_arm {
FRANKO_INLINE void FUNC_vextq_u8_62d3997c(Franko_Fat_Pointer<uint8_t> PARAM_target, Franko_Fat_Pointer<uint8_t> PARAM_source, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count, uint32_t PARAM_shift)
{
    uint8_t* target = PARAM_target.data;
    uint32_t target_length = PARAM_target.count;
    if (target == nullptr && PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = PARAM_source.data;
    uint32_t source_length = PARAM_source.count;
    if (source == nullptr && PARAM_count != 0) {
        franko_panic("simd: null source");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > source_length || PARAM_count > source_length - PARAM_src_start) {
        franko_panic("simd: source range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return;
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (target == source && PARAM_dst_start != PARAM_src_start) {
        franko_panic("simd: overlapping target and source ranges");
    }
#endif
    target += PARAM_dst_start;
    source += PARAM_src_start;
    uint32_t i = 0;
    if (PARAM_shift < 16) {
        switch (PARAM_shift) {
        case 0:
            for (; i + 16 <= PARAM_count; i += 16) {
                const uint8x16_t low = vld1q_u8(target + i);
                const uint8x16_t high = vld1q_u8(source + i);
                vst1q_u8(target + i, vextq_u8(low, high, 0));
            }
            break;
        case 1:
            for (; i + 16 <= PARAM_count; i += 16) {
                const uint8x16_t low = vld1q_u8(target + i);
                const uint8x16_t high = vld1q_u8(source + i);
                vst1q_u8(target + i, vextq_u8(low, high, 1));
            }
            break;
        case 2:
            for (; i + 16 <= PARAM_count; i += 16) {
                const uint8x16_t low = vld1q_u8(target + i);
                const uint8x16_t high = vld1q_u8(source + i);
                vst1q_u8(target + i, vextq_u8(low, high, 2));
            }
            break;
        case 3:
            for (; i + 16 <= PARAM_count; i += 16) {
                const uint8x16_t low = vld1q_u8(target + i);
                const uint8x16_t high = vld1q_u8(source + i);
                vst1q_u8(target + i, vextq_u8(low, high, 3));
            }
            break;
        case 4:
            for (; i + 16 <= PARAM_count; i += 16) {
                const uint8x16_t low = vld1q_u8(target + i);
                const uint8x16_t high = vld1q_u8(source + i);
                vst1q_u8(target + i, vextq_u8(low, high, 4));
            }
            break;
        case 5:
            for (; i + 16 <= PARAM_count; i += 16) {
                const uint8x16_t low = vld1q_u8(target + i);
                const uint8x16_t high = vld1q_u8(source + i);
                vst1q_u8(target + i, vextq_u8(low, high, 5));
            }
            break;
        case 6:
            for (; i + 16 <= PARAM_count; i += 16) {
                const uint8x16_t low = vld1q_u8(target + i);
                const uint8x16_t high = vld1q_u8(source + i);
                vst1q_u8(target + i, vextq_u8(low, high, 6));
            }
            break;
        case 7:
            for (; i + 16 <= PARAM_count; i += 16) {
                const uint8x16_t low = vld1q_u8(target + i);
                const uint8x16_t high = vld1q_u8(source + i);
                vst1q_u8(target + i, vextq_u8(low, high, 7));
            }
            break;
        case 8:
            for (; i + 16 <= PARAM_count; i += 16) {
                const uint8x16_t low = vld1q_u8(target + i);
                const uint8x16_t high = vld1q_u8(source + i);
                vst1q_u8(target + i, vextq_u8(low, high, 8));
            }
            break;
        case 9:
            for (; i + 16 <= PARAM_count; i += 16) {
                const uint8x16_t low = vld1q_u8(target + i);
                const uint8x16_t high = vld1q_u8(source + i);
                vst1q_u8(target + i, vextq_u8(low, high, 9));
            }
            break;
        case 10:
            for (; i + 16 <= PARAM_count; i += 16) {
                const uint8x16_t low = vld1q_u8(target + i);
                const uint8x16_t high = vld1q_u8(source + i);
                vst1q_u8(target + i, vextq_u8(low, high, 10));
            }
            break;
        case 11:
            for (; i + 16 <= PARAM_count; i += 16) {
                const uint8x16_t low = vld1q_u8(target + i);
                const uint8x16_t high = vld1q_u8(source + i);
                vst1q_u8(target + i, vextq_u8(low, high, 11));
            }
            break;
        case 12:
            for (; i + 16 <= PARAM_count; i += 16) {
                const uint8x16_t low = vld1q_u8(target + i);
                const uint8x16_t high = vld1q_u8(source + i);
                vst1q_u8(target + i, vextq_u8(low, high, 12));
            }
            break;
        case 13:
            for (; i + 16 <= PARAM_count; i += 16) {
                const uint8x16_t low = vld1q_u8(target + i);
                const uint8x16_t high = vld1q_u8(source + i);
                vst1q_u8(target + i, vextq_u8(low, high, 13));
            }
            break;
        case 14:
            for (; i + 16 <= PARAM_count; i += 16) {
                const uint8x16_t low = vld1q_u8(target + i);
                const uint8x16_t high = vld1q_u8(source + i);
                vst1q_u8(target + i, vextq_u8(low, high, 14));
            }
            break;
        case 15:
            for (; i + 16 <= PARAM_count; i += 16) {
                const uint8x16_t low = vld1q_u8(target + i);
                const uint8x16_t high = vld1q_u8(source + i);
                vst1q_u8(target + i, vextq_u8(low, high, 15));
            }
            break;
        }
        for (; i < PARAM_count; ++i) {
            const uint32_t offset = (i % 16) + PARAM_shift;
            if (offset < 16) {
                const uint32_t index = i - (i % 16) + offset;
                target[i] = index < PARAM_count ? target[index] : 0;
            } else {
                target[i] = source[i - (i % 16) + offset - 16];
            }
        }
    } else {
        for (; i < PARAM_count; ++i) {
            target[i] = 0;
        }
    }
}
}
}
// @@end

// @@simd::arm::vzip1q_u8
namespace NS_simd {
namespace NS_arm {
FRANKO_INLINE void FUNC_vzip1q_u8_7243e039(Franko_Fat_Pointer<uint8_t> PARAM_target, Franko_Fat_Pointer<uint8_t> PARAM_source, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint8_t* target = PARAM_target.data;
    uint32_t target_length = PARAM_target.count;
    if (target == nullptr && PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = PARAM_source.data;
    uint32_t source_length = PARAM_source.count;
    if (source == nullptr && PARAM_count != 0) {
        franko_panic("simd: null source");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > source_length || PARAM_count > source_length - PARAM_src_start) {
        franko_panic("simd: source range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return;
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (target == source && PARAM_dst_start != PARAM_src_start) {
        franko_panic("simd: overlapping target and source ranges");
    }
#endif
    target += PARAM_dst_start;
    source += PARAM_src_start;
    uint32_t i = 0;
    for (; i + 16 <= PARAM_count; i += 16) {
        const uint8x16_t left = vld1q_u8(target + i);
        const uint8x16_t right = vld1q_u8(source + i);
        vst1q_u8(target + i, vzip1q_u8(left, right));
    }
    if (i < PARAM_count) {
        const uint32_t chunk = i;
        uint8_t block[16] = {0};
        std::memcpy(block, target + chunk, PARAM_count - chunk);
        for (; i < PARAM_count; ++i) {
            const uint32_t lane = i - chunk;
            if (lane % 2 == 0) {
                target[i] = block[lane / 2];
            } else {
                target[i] = source[chunk + lane / 2];
            }
        }
    }
}
}
}
// @@end

// @@simd::arm::vzip2q_u8
namespace NS_simd {
namespace NS_arm {
FRANKO_INLINE void FUNC_vzip2q_u8_7243e039(Franko_Fat_Pointer<uint8_t> PARAM_target, Franko_Fat_Pointer<uint8_t> PARAM_source, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint8_t* target = PARAM_target.data;
    uint32_t target_length = PARAM_target.count;
    if (target == nullptr && PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = PARAM_source.data;
    uint32_t source_length = PARAM_source.count;
    if (source == nullptr && PARAM_count != 0) {
        franko_panic("simd: null source");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > source_length || PARAM_count > source_length - PARAM_src_start) {
        franko_panic("simd: source range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return;
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (target == source && PARAM_dst_start != PARAM_src_start) {
        franko_panic("simd: overlapping target and source ranges");
    }
#endif
    target += PARAM_dst_start;
    source += PARAM_src_start;
    uint32_t i = 0;
    for (; i + 16 <= PARAM_count; i += 16) {
        const uint8x16_t left = vld1q_u8(target + i);
        const uint8x16_t right = vld1q_u8(source + i);
        vst1q_u8(target + i, vzip2q_u8(left, right));
    }
    if (i < PARAM_count) {
        const uint32_t chunk = i;
        const uint32_t available = PARAM_count - chunk;
        uint8_t block[16] = {0};
        std::memcpy(block, target + chunk, available);
        for (; i < PARAM_count; ++i) {
            const uint32_t lane = i - chunk;
            const uint32_t input_lane = 8 + lane / 2;
            if (lane % 2 == 0) {
                target[i] = block[input_lane];
            } else {
                target[i] = input_lane < available ? source[chunk + input_lane] : 0;
            }
        }
    }
}
}
}
// @@end

// @@simd::arm::vqmovun_s16
namespace NS_simd {
namespace NS_arm {
FRANKO_INLINE void FUNC_vqmovun_s16_ed547b8f(Franko_Fat_Pointer<uint8_t> PARAM_target, Franko_Fat_Pointer<int16_t> PARAM_source, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint8_t* target = PARAM_target.data;
    uint32_t target_length = PARAM_target.count;
    if (target == nullptr && PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const int16_t* source = PARAM_source.data;
    uint32_t source_length = PARAM_source.count;
    if (source == nullptr && PARAM_count != 0) {
        franko_panic("simd: null source");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > source_length || PARAM_count > source_length - PARAM_src_start) {
        franko_panic("simd: source range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return;
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (static_cast<const void*>(target) == static_cast<const void*>(source)
            && PARAM_dst_start != PARAM_src_start) {
        franko_panic("simd: overlapping target and source ranges");
    }
#endif
    target += PARAM_dst_start;
    source += PARAM_src_start;
    uint32_t i = 0;
    for (; i + 8 <= PARAM_count; i += 8) {
        const int16x8_t input = vld1q_s16(source + i);
        vst1_u8(target + i, vqmovun_s16(input));
    }
    for (; i < PARAM_count; ++i) {
        const int32_t value = source[i];
        target[i] = static_cast<uint8_t>(value < 0 ? 0 : value > 255 ? 255 : value);
    }
}
}
}
// @@end

// @@simd::arm::vabdq_u8
namespace NS_simd {
namespace NS_arm {
FRANKO_INLINE void FUNC_vabdq_u8_7243e039(Franko_Fat_Pointer<uint8_t> PARAM_target, Franko_Fat_Pointer<uint8_t> PARAM_source, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint8_t* target = PARAM_target.data;
    uint32_t target_length = PARAM_target.count;
    if (target == nullptr && PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = PARAM_source.data;
    uint32_t source_length = PARAM_source.count;
    if (source == nullptr && PARAM_count != 0) {
        franko_panic("simd: null source");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > source_length || PARAM_count > source_length - PARAM_src_start) {
        franko_panic("simd: source range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return;
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (target == source && PARAM_dst_start != PARAM_src_start) {
        franko_panic("simd: overlapping target and source ranges");
    }
#endif
    target += PARAM_dst_start;
    source += PARAM_src_start;
    uint32_t i = 0;
    for (; i + 16 <= PARAM_count; i += 16) {
        const uint8x16_t left = vld1q_u8(target + i);
        const uint8x16_t right = vld1q_u8(source + i);
        vst1q_u8(target + i, vabdq_u8(left, right));
    }
    for (; i < PARAM_count; ++i) {
        target[i] = target[i] > source[i] ? static_cast<uint8_t>(target[i] - source[i]) : static_cast<uint8_t>(source[i] - target[i]);
    }
}
}
}
// @@end

// @@simd::arm::vpaddlq_u16
namespace NS_simd {
namespace NS_arm {
FRANKO_INLINE void FUNC_vpaddlq_u16_8c3520d3(Franko_Fat_Pointer<uint32_t> PARAM_target, Franko_Fat_Pointer<uint16_t> PARAM_source, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint32_t* target = PARAM_target.data;
    uint32_t target_length = PARAM_target.count;
    if (target == nullptr && PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint16_t* source = PARAM_source.data;
    uint32_t source_length = PARAM_source.count;
    if (source == nullptr && PARAM_count != 0) {
        franko_panic("simd: null source");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > source_length || PARAM_count > (source_length - PARAM_src_start) / 2) {
        franko_panic("simd: source range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return;
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (static_cast<const void*>(target) == static_cast<const void*>(source)
            && PARAM_dst_start != PARAM_src_start) {
        franko_panic("simd: overlapping target and source ranges");
    }
#endif
    target += PARAM_dst_start;
    source += PARAM_src_start;
    uint32_t i = 0;
    for (; i + 4 <= PARAM_count; i += 4) {
        const uint16x8_t input = vld1q_u16(source + 2 * i);
        vst1q_u32(target + i, vpaddlq_u16(input));
    }
    for (; i < PARAM_count; ++i) {
        target[i] = static_cast<uint32_t>(source[2 * i]) + static_cast<uint32_t>(source[2 * i + 1]);
    }
}
}
}
// @@end

// @@simd::arm::vabsq_s8
namespace NS_simd {
namespace NS_arm {
FRANKO_INLINE void FUNC_vabsq_s8_6c5347dd(Franko_Fat_Pointer<int8_t> PARAM_target, Franko_Fat_Pointer<int8_t> PARAM_source, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    int8_t* target = PARAM_target.data;
    uint32_t target_length = PARAM_target.count;
    if (target == nullptr && PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const int8_t* source = PARAM_source.data;
    uint32_t source_length = PARAM_source.count;
    if (source == nullptr && PARAM_count != 0) {
        franko_panic("simd: null source");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > source_length || PARAM_count > source_length - PARAM_src_start) {
        franko_panic("simd: source range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return;
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (target == source && PARAM_dst_start != PARAM_src_start) {
        franko_panic("simd: overlapping target and source ranges");
    }
#endif
    target += PARAM_dst_start;
    source += PARAM_src_start;
    uint32_t i = 0;
    for (; i + 16 <= PARAM_count; i += 16) {
        const int8x16_t input = vld1q_s8(source + i);
        vst1q_s8(target + i, vabsq_s8(input));
    }
    for (; i < PARAM_count; ++i) {
        const int32_t value = source[i];
        target[i] = static_cast<int8_t>(value < 0 ? static_cast<uint8_t>(-value) : static_cast<uint8_t>(value));
    }
}
}
}
// @@end

// @@simd::arm::vqtbl1q_u8
namespace NS_simd {
namespace NS_arm {
FRANKO_INLINE void FUNC_vqtbl1q_u8_7243e039(Franko_Fat_Pointer<uint8_t> PARAM_target, Franko_Fat_Pointer<uint8_t> PARAM_indices, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint8_t* target = PARAM_target.data;
    uint32_t target_length = PARAM_target.count;
    if (target == nullptr && PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* indices = PARAM_indices.data;
    uint32_t indices_length = PARAM_indices.count;
    if (indices == nullptr && PARAM_count != 0) {
        franko_panic("simd: null indices");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > indices_length || PARAM_count > indices_length - PARAM_src_start) {
        franko_panic("simd: indices range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return;
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (target == indices && PARAM_dst_start != PARAM_src_start) {
        franko_panic("simd: overlapping target and indices ranges");
    }
#endif
    target += PARAM_dst_start;
    indices += PARAM_src_start;
    for (uint32_t i = 0; i + 16 <= PARAM_count; i += 16) {
        const uint8x16_t table = vld1q_u8(target + i);
        const uint8x16_t control = vld1q_u8(indices + i);
        vst1q_u8(target + i, vqtbl1q_u8(table, control));
    }
}
}
}
// @@end

// @@simd::arm::vaeseq_u8
namespace NS_simd {
namespace NS_arm {
FRANKO_INLINE int32_t FUNC_vaeseq_u8_697d55d9(Franko_Fat_Pointer<uint8_t> PARAM_target, Franko_Fat_Pointer<uint8_t> PARAM_round_key, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint8_t* target = PARAM_target.data;
    uint32_t target_length = PARAM_target.count;
    if (target == nullptr && PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* round_key = PARAM_round_key.data;
    uint32_t round_key_length = PARAM_round_key.count;
    if (round_key == nullptr && PARAM_count != 0) {
        franko_panic("simd: null round key");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > round_key_length
            || (PARAM_count != 0 && 16 > round_key_length - PARAM_src_start)) {
        franko_panic("simd: round key range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return 0;
    }
    target += PARAM_dst_start;
    round_key += PARAM_src_start;
#if defined(__ARM_FEATURE_CRYPTO) || defined(__ARM_FEATURE_AES)
    const uint8x16_t key = vld1q_u8(round_key);
    for (uint32_t i = 0; i + 16 <= PARAM_count; i += 16) {
        const uint8x16_t block = vld1q_u8(target + i);
        vst1q_u8(target + i, vaeseq_u8(block, key));
    }
    return 0;
#else
    return 1;
#endif
}
}
}
// @@end

// @@simd::arm::vaesdq_u8
namespace NS_simd {
namespace NS_arm {
FRANKO_INLINE int32_t FUNC_vaesdq_u8_697d55d9(Franko_Fat_Pointer<uint8_t> PARAM_target, Franko_Fat_Pointer<uint8_t> PARAM_round_key, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint8_t* target = PARAM_target.data;
    uint32_t target_length = PARAM_target.count;
    if (target == nullptr && PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* round_key = PARAM_round_key.data;
    uint32_t round_key_length = PARAM_round_key.count;
    if (round_key == nullptr && PARAM_count != 0) {
        franko_panic("simd: null round key");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > round_key_length
            || (PARAM_count != 0 && 16 > round_key_length - PARAM_src_start)) {
        franko_panic("simd: round key range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return 0;
    }
    target += PARAM_dst_start;
    round_key += PARAM_src_start;
#if defined(__ARM_FEATURE_CRYPTO) || defined(__ARM_FEATURE_AES)
    const uint8x16_t key = vld1q_u8(round_key);
    for (uint32_t i = 0; i + 16 <= PARAM_count; i += 16) {
        const uint8x16_t block = vld1q_u8(target + i);
        vst1q_u8(target + i, vaesdq_u8(block, key));
    }
    return 0;
#else
    return 1;
#endif
}
}
}
// @@end

// @@simd::x86::cmpeq_epi8
namespace NS_simd {
namespace NS_x86 {
FRANKO_INLINE void FUNC_cmpeq_epi8_7243e039(Franko_Fat_Pointer<uint8_t> PARAM_target, Franko_Fat_Pointer<uint8_t> PARAM_source, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint8_t* target = PARAM_target.data;
    uint32_t target_length = PARAM_target.count;
    if (target == nullptr && PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = PARAM_source.data;
    uint32_t source_length = PARAM_source.count;
    if (source == nullptr && PARAM_count != 0) {
        franko_panic("simd: null source");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > source_length || PARAM_count > source_length - PARAM_src_start) {
        franko_panic("simd: source range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return;
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (target == source && PARAM_dst_start != PARAM_src_start) {
        franko_panic("simd: overlapping target and source ranges");
    }
#endif
    target += PARAM_dst_start;
    source += PARAM_src_start;
    uint32_t i = 0;
#if defined(__AVX2__)
    for (; i + 32 <= PARAM_count; i += 32) {
        const __m256i a = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(target + i));
        const __m256i b = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(source + i));
        _mm256_storeu_si256(reinterpret_cast<__m256i*>(target + i), _mm256_cmpeq_epi8(a, b));
    }
#endif
    for (; i + 16 <= PARAM_count; i += 16) {
        const __m128i a = _mm_loadu_si128(reinterpret_cast<const __m128i*>(target + i));
        const __m128i b = _mm_loadu_si128(reinterpret_cast<const __m128i*>(source + i));
        _mm_storeu_si128(reinterpret_cast<__m128i*>(target + i), _mm_cmpeq_epi8(a, b));
    }
    for (; i < PARAM_count; ++i) {
        target[i] = static_cast<uint8_t>(target[i] == source[i] ? 0xFF : 0x00);
    }
}
}
}
// @@end

// @@simd::x86::movemask_epi8
namespace NS_simd {
namespace NS_x86 {
FRANKO_INLINE uint64_t FUNC_movemask_epi8_dd946ac2(Franko_Fat_Pointer<uint8_t> PARAM_source, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    const uint8_t* source = PARAM_source.data;
    uint32_t source_length = PARAM_source.count;
    if (source == nullptr && PARAM_count != 0) {
        franko_panic("simd: null source");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > source_length || PARAM_count > source_length - PARAM_src_start) {
        franko_panic("simd: source range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return 0;
    }
    if (PARAM_count > 64) {
        franko_panic("simd: movemask count exceeds 64");
    }
    source += PARAM_src_start;
    uint64_t result = 0;
    uint32_t shift = 0;
    uint32_t i = 0;
#if defined(__AVX2__)
    for (; i + 32 <= PARAM_count; i += 32) {
        const __m256i chunk = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(source + i));
        result |= static_cast<uint64_t>(static_cast<uint32_t>(_mm256_movemask_epi8(chunk))) << shift;
        shift += 32;
    }
#endif
    for (; i + 16 <= PARAM_count; i += 16) {
        const __m128i chunk = _mm_loadu_si128(reinterpret_cast<const __m128i*>(source + i));
        result |= static_cast<uint64_t>(static_cast<uint32_t>(_mm_movemask_epi8(chunk))) << shift;
        shift += 16;
    }
    for (; i < PARAM_count; ++i) {
        result |= static_cast<uint64_t>((source[i] >> 7) & 1u) << shift;
        ++shift;
    }
    return result;
}
}
}
// @@end

// @@simd::x86::crc32_u8
namespace NS_simd {
namespace NS_x86 {
FRANKO_INLINE uint32_t FUNC_crc32_u8_19ca3df8(uint32_t PARAM_crc, Franko_Fat_Pointer<uint8_t> PARAM_data, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint32_t crc = PARAM_crc;
    const uint8_t* data = PARAM_data.data;
    uint32_t data_length = PARAM_data.count;
    if (data == nullptr && PARAM_count != 0) {
        franko_panic("simd: null data");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > data_length || PARAM_count > data_length - PARAM_src_start) {
        franko_panic("simd: data range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return crc;
    }
    data += PARAM_src_start;
    uint32_t i = 0;
#if defined(__SSE4_2__)
    for (; i + 8 <= PARAM_count; i += 8) {
        uint64_t chunk;
        std::memcpy(&chunk, data + i, 8);
        crc = _mm_crc32_u64(crc, chunk);
    }
#endif
    for (; i < PARAM_count; ++i) {
        crc ^= data[i];
        for (uint32_t k = 0; k < 8; ++k) {
            crc = (crc >> 1) ^ (0x82F63B78u & (0u - (crc & 1u)));
        }
    }
    return crc;
}
}
}
// @@end

// @@simd::x86::cvtepu8_epi16
namespace NS_simd {
namespace NS_x86 {
FRANKO_INLINE void FUNC_cvtepu8_epi16_b3d94c16(Franko_Fat_Pointer<uint16_t> PARAM_target, Franko_Fat_Pointer<uint8_t> PARAM_source, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint16_t* target = PARAM_target.data;
    uint32_t target_length = PARAM_target.count;
    if (target == nullptr && PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = PARAM_source.data;
    uint32_t source_length = PARAM_source.count;
    if (source == nullptr && PARAM_count != 0) {
        franko_panic("simd: null source");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > source_length || PARAM_count > source_length - PARAM_src_start) {
        franko_panic("simd: source range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return;
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (static_cast<const void*>(target) == static_cast<const void*>(source)
            && PARAM_dst_start != PARAM_src_start) {
        franko_panic("simd: overlapping target and source ranges");
    }
#endif
    target += PARAM_dst_start;
    source += PARAM_src_start;
    uint32_t i = 0;
#if defined(__AVX2__)
    for (; i + 16 <= PARAM_count; i += 16) {
        const __m128i chunk = _mm_loadu_si128(reinterpret_cast<const __m128i*>(source + i));
        _mm256_storeu_si256(reinterpret_cast<__m256i*>(target + i), _mm256_cvtepu8_epi16(chunk));
    }
#endif
#if defined(__SSE4_1__)
    for (; i + 8 <= PARAM_count; i += 8) {
        const __m128i chunk = _mm_loadl_epi64(reinterpret_cast<const __m128i*>(source + i));
        _mm_storeu_si128(reinterpret_cast<__m128i*>(target + i), _mm_cvtepu8_epi16(chunk));
    }
#endif
    for (; i < PARAM_count; ++i) {
        target[i] = source[i];
    }
}
}
}
// @@end

// @@simd::x86::dpbusd_epi32
namespace NS_simd {
namespace NS_x86 {
FRANKO_INLINE void FUNC_dpbusd_epi32_7a76aa9d(
    Franko_Fat_Pointer<uint32_t> PARAM_acc,
    Franko_Fat_Pointer<uint8_t>  PARAM_a,
    Franko_Fat_Pointer<int8_t>   PARAM_b,
    uint32_t PARAM_acc_start,
    uint32_t PARAM_a_start,
    uint32_t PARAM_b_start,
    uint32_t PARAM_count)
{
    uint32_t* acc = PARAM_acc.data;
    uint32_t acc_length = PARAM_acc.count;
    if (acc == nullptr && PARAM_count != 0) {
        franko_panic("simd: null acc");
    }

    const uint8_t* a = PARAM_a.data;
    uint32_t a_length = PARAM_a.count;
    if (a == nullptr && PARAM_count != 0) {
        franko_panic("simd: null a");
    }

    const int8_t* b = PARAM_b.data;
    uint32_t b_length = PARAM_b.count;
    if (b == nullptr && PARAM_count != 0) {
        franko_panic("simd: null b");
    }

#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_acc_start > acc_length || PARAM_count > acc_length - PARAM_acc_start) {
        franko_panic("simd: acc range out of bounds");
    }
    if (PARAM_a_start > a_length || PARAM_count > (a_length - PARAM_a_start) / 4) {
        franko_panic("simd: a range out of bounds");
    }
    if (PARAM_b_start > b_length || PARAM_count > (b_length - PARAM_b_start) / 4) {
        franko_panic("simd: b range out of bounds");
    }
#endif

    if (PARAM_count == 0) {
        return;
    }

#if !FRANKO_BOUNDS_UNCHECKED
    if (static_cast<const void*>(acc) == static_cast<const void*>(a) && (PARAM_acc_start * 4 != PARAM_a_start)) {
        franko_panic("simd: overlapping acc and a ranges");
    }
    if (static_cast<const void*>(acc) == static_cast<const void*>(b) && (PARAM_acc_start * 4 != PARAM_b_start)) {
        franko_panic("simd: overlapping acc and b ranges");
    }
    if (static_cast<const void*>(a) == static_cast<const void*>(b) && (PARAM_a_start != PARAM_b_start)) {
        franko_panic("simd: overlapping a and b ranges");
    }
#endif

    acc += PARAM_acc_start;
    a   += PARAM_a_start;
    b   += PARAM_b_start;
    uint32_t i = 0;

    // =========================================================================
    // 256-BIT LOOP (8 lanes at a time)
    // =========================================================================

#if defined(__AVX2__) && defined(__AVXVNNI__)
    // Tier 1: AVX2 + VNNI
    for (; i + 8 <= PARAM_count; i += 8) {
        const __m256i sum = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(acc + i));
        const __m256i x   = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(a + 4 * i));
        const __m256i y   = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(b + 4 * i));
        _mm256_storeu_si256(reinterpret_cast<__m256i*>(acc + i), _mm256_dpbusd_epi32(sum, x, y));
    }

#elif defined(__AVX2__)
    // Tier 2: AVX2 Only
    const __m256i ones256 = _mm256_set1_epi16(1);
    for (; i + 8 <= PARAM_count; i += 8) {
        const __m256i sum = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(acc + i));
        const __m256i x   = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(a + 4 * i));
        const __m256i y   = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(b + 4 * i));

        const __m256i dot16 = _mm256_maddubs_epi16(x, y);
        const __m256i dot32 = _mm256_madd_epi16(dot16, ones256);
        _mm256_storeu_si256(reinterpret_cast<__m256i*>(acc + i), _mm256_add_epi32(sum, dot32));
    }
#endif

    // =========================================================================
    // 128-BIT LOOP (4 lanes at a time)
    // Tier 3: AVX Only (Sandy/Ivy Bridge) & 4-lane cleanup for Tiers 1 and 2
    // =========================================================================

#if defined(__AVX__)
    const __m128i ones128 = _mm_set1_epi16(1);
    for (; i + 4 <= PARAM_count; i += 4) {
        const __m128i sum = _mm_loadu_si128(reinterpret_cast<const __m128i*>(acc + i));
        const __m128i x   = _mm_loadu_si128(reinterpret_cast<const __m128i*>(a + 4 * i));
        const __m128i y   = _mm_loadu_si128(reinterpret_cast<const __m128i*>(b + 4 * i));

        const __m128i dot16 = _mm_maddubs_epi16(x, y);
        const __m128i dot32 = _mm_madd_epi16(dot16, ones128);
        _mm_storeu_si128(reinterpret_cast<__m128i*>(acc + i), _mm_add_epi32(sum, dot32));
    }
#endif

    // =========================================================================
    // SCALAR LOOP
    // Tier 4: Remaining 0..3 lanes, or pre-AVX fallback
    // =========================================================================

    for (; i < PARAM_count; ++i) {
        int32_t sum = static_cast<int32_t>(acc[i]);
        for (uint32_t k = 0; k < 4; ++k) {
            const int32_t x = static_cast<int32_t>(a[4 * i + k]);
            const int32_t y = static_cast<int32_t>(b[4 * i + k]);
            sum += x * y;
        }
        acc[i] = static_cast<uint32_t>(sum);
    }
}
}
}
// @@end

// @@simd::x86::ptest_epi8
namespace NS_simd {
namespace NS_x86 {
FRANKO_INLINE uint32_t FUNC_ptest_epi8_480a58a9(Franko_Fat_Pointer<uint8_t> PARAM_a, Franko_Fat_Pointer<uint8_t> PARAM_b, uint32_t PARAM_a_start, uint32_t PARAM_b_start, uint32_t PARAM_count)
{
    const uint8_t* a = PARAM_a.data;
    uint32_t a_length = PARAM_a.count;
    if (a == nullptr && PARAM_count != 0) {
        franko_panic("simd: null a");
    }
    const uint8_t* b = PARAM_b.data;
    uint32_t b_length = PARAM_b.count;
    if (b == nullptr && PARAM_count != 0) {
        franko_panic("simd: null b");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_a_start > a_length || PARAM_count > a_length - PARAM_a_start) {
        franko_panic("simd: a range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_b_start > b_length || PARAM_count > b_length - PARAM_b_start) {
        franko_panic("simd: b range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return 1;
    }
    a += PARAM_a_start;
    b += PARAM_b_start;
    uint32_t i = 0;
#if defined(__AVX2__)
    for (; i + 32 <= PARAM_count; i += 32) {
        const __m256i x = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(a + i));
        const __m256i y = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(b + i));
        if (_mm256_testz_si256(x, y) == 0) {
            return 0;
        }
    }
#endif
#if defined(__SSE4_1__)
    for (; i + 16 <= PARAM_count; i += 16) {
        const __m128i x = _mm_loadu_si128(reinterpret_cast<const __m128i*>(a + i));
        const __m128i y = _mm_loadu_si128(reinterpret_cast<const __m128i*>(b + i));
        if (_mm_testz_si128(x, y) == 0) {
            return 0;
        }
    }
#endif
    for (; i < PARAM_count; ++i) {
        if ((a[i] & b[i]) != 0) {
            return 0;
        }
    }
    return 1;
}
}
}
// @@end

// @@simd::x86::clmulepi64_epi64
namespace NS_simd {
namespace NS_x86 {
FRANKO_INLINE void FUNC_clmulepi64_epi64_b95c3315(Franko_Fat_Pointer<uint64_t> PARAM_target, Franko_Fat_Pointer<uint64_t> PARAM_a, Franko_Fat_Pointer<uint64_t> PARAM_b, uint32_t PARAM_dst_start, uint32_t PARAM_a_start, uint32_t PARAM_b_start, uint32_t PARAM_count)
{
    uint64_t* target = PARAM_target.data;
    uint32_t target_length = PARAM_target.count;
    if (target == nullptr && PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint64_t* a = PARAM_a.data;
    uint32_t a_length = PARAM_a.count;
    if (a == nullptr && PARAM_count != 0) {
        franko_panic("simd: null a");
    }
    const uint64_t* b = PARAM_b.data;
    uint32_t b_length = PARAM_b.count;
    if (b == nullptr && PARAM_count != 0) {
        franko_panic("simd: null b");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > (target_length - PARAM_dst_start) / 2) {
        franko_panic("simd: target range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_a_start > a_length || PARAM_count > a_length - PARAM_a_start) {
        franko_panic("simd: a range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_b_start > b_length || PARAM_count > b_length - PARAM_b_start) {
        franko_panic("simd: b range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return;
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (target == a && PARAM_dst_start != PARAM_a_start) {
        franko_panic("simd: overlapping target and a ranges");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (target == b && PARAM_dst_start != PARAM_b_start) {
        franko_panic("simd: overlapping target and b ranges");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (static_cast<const void*>(a) == static_cast<const void*>(b) && PARAM_a_start != PARAM_b_start) {
        franko_panic("simd: overlapping a and b ranges");
    }
#endif
    target += PARAM_dst_start;
    a += PARAM_a_start;
    b += PARAM_b_start;
    uint32_t i = 0;
#if defined(__VPCLMULQDQ__) && defined(__AVX2__)
    for (; i + 4 <= PARAM_count; i += 4) {
        const __m256i x = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(a + i));
        const __m256i y = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(b + i));
        const __m256i even = _mm256_clmulepi64_epi128(x, y, 0x00);
        const __m256i odd = _mm256_clmulepi64_epi128(x, y, 0x11);
        _mm_storeu_si128(reinterpret_cast<__m128i*>(target + 2 * i), _mm256_castsi256_si128(even));
        _mm_storeu_si128(reinterpret_cast<__m128i*>(target + 2 * i + 2), _mm256_castsi256_si128(odd));
        _mm_storeu_si128(reinterpret_cast<__m128i*>(target + 2 * i + 4), _mm256_extracti128_si256(even, 1));
        _mm_storeu_si128(reinterpret_cast<__m128i*>(target + 2 * i + 6), _mm256_extracti128_si256(odd, 1));
    }
#endif
#if defined(__PCLMUL__)
    for (; i + 1 <= PARAM_count; i += 1) {
        const __m128i x = _mm_loadl_epi64(reinterpret_cast<const __m128i*>(a + i));
        const __m128i y = _mm_loadl_epi64(reinterpret_cast<const __m128i*>(b + i));
        _mm_storeu_si128(reinterpret_cast<__m128i*>(target + 2 * i), _mm_clmulepi64_si128(x, y, 0));
    }
#endif
    for (; i < PARAM_count; ++i) {
        const uint64_t word_a = a[i];
        const uint64_t word_b = b[i];
        uint64_t lo = 0;
        uint64_t hi = 0;
        for (uint32_t k = 0; k < 64; ++k) {
            hi = (hi << 1) | (lo >> 63);
            lo <<= 1;
            if (((word_a >> (63 - k)) & 1u) != 0u) {
                lo ^= word_b;
            }
        }
        target[2 * i] = lo;
        target[2 * i + 1] = hi;
    }
}
}
}
// @@end

// @@simd::x86::stream_si256
namespace NS_simd {
namespace NS_x86 {
FRANKO_INLINE void FUNC_stream_si256_7243e039(Franko_Fat_Pointer<uint8_t> PARAM_target, Franko_Fat_Pointer<uint8_t> PARAM_source, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint8_t* target = PARAM_target.data;
    uint32_t target_length = PARAM_target.count;
    if (target == nullptr && PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = PARAM_source.data;
    uint32_t source_length = PARAM_source.count;
    if (source == nullptr && PARAM_count != 0) {
        franko_panic("simd: null source");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > source_length || PARAM_count > source_length - PARAM_src_start) {
        franko_panic("simd: source range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return;
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (target == source && PARAM_dst_start != PARAM_src_start) {
        franko_panic("simd: overlapping target and source ranges");
    }
#endif
    target += PARAM_dst_start;
    source += PARAM_src_start;
    uint32_t i = 0;
#if defined(__AVX2__)
    while (i < PARAM_count && (reinterpret_cast<uint64_t>(target + i) & 31u) != 0u) {
        target[i] = source[i];
        ++i;
    }
    for (; i + 32 <= PARAM_count; i += 32) {
        _mm256_stream_si256(reinterpret_cast<__m256i*>(target + i), _mm256_loadu_si256(reinterpret_cast<const __m256i*>(source + i)));
    }
#endif
    while (i < PARAM_count && (reinterpret_cast<uint64_t>(target + i) & 15u) != 0u) {
        target[i] = source[i];
        ++i;
    }
    for (; i + 16 <= PARAM_count; i += 16) {
        _mm_stream_si128(reinterpret_cast<__m128i*>(target + i), _mm_loadu_si128(reinterpret_cast<const __m128i*>(source + i)));
    }
    for (; i < PARAM_count; ++i) {
        target[i] = source[i];
    }
}
}
}
// @@end

// @@simd::arm::vceqq_u8
namespace NS_simd {
namespace NS_arm {
FRANKO_INLINE void FUNC_vceqq_u8_7243e039(Franko_Fat_Pointer<uint8_t> PARAM_target, Franko_Fat_Pointer<uint8_t> PARAM_source, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint8_t* target = PARAM_target.data;
    uint32_t target_length = PARAM_target.count;
    if (target == nullptr && PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = PARAM_source.data;
    uint32_t source_length = PARAM_source.count;
    if (source == nullptr && PARAM_count != 0) {
        franko_panic("simd: null source");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > source_length || PARAM_count > source_length - PARAM_src_start) {
        franko_panic("simd: source range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return;
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (target == source && PARAM_dst_start != PARAM_src_start) {
        franko_panic("simd: overlapping target and source ranges");
    }
#endif
    target += PARAM_dst_start;
    source += PARAM_src_start;
    uint32_t i = 0;
    for (; i + 16 <= PARAM_count; i += 16) {
        const uint8x16_t a = vld1q_u8(target + i);
        const uint8x16_t b = vld1q_u8(source + i);
        vst1q_u8(target + i, vceqq_u8(a, b));
    }
    for (; i < PARAM_count; ++i) {
        target[i] = static_cast<uint8_t>(target[i] == source[i] ? 0xFF : 0x00);
    }
}
}
}
// @@end

// @@simd::arm::crc32b_u8
namespace NS_simd {
namespace NS_arm {
FRANKO_INLINE uint32_t FUNC_crc32b_u8_19ca3df8(uint32_t PARAM_crc, Franko_Fat_Pointer<uint8_t> PARAM_data, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint32_t crc = PARAM_crc;
    const uint8_t* data = PARAM_data.data;
    uint32_t data_length = PARAM_data.count;
    if (data == nullptr && PARAM_count != 0) {
        franko_panic("simd: null data");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > data_length || PARAM_count > data_length - PARAM_src_start) {
        franko_panic("simd: data range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return crc;
    }
    data += PARAM_src_start;
    uint32_t i = 0;
#if defined(__ARM_FEATURE_CRC32)
    for (; i + 8 <= PARAM_count; i += 8) {
        uint64_t chunk;
        std::memcpy(&chunk, data + i, 8);
        crc = __crc32d(crc, chunk);
    }
#endif
    for (; i < PARAM_count; ++i) {
        crc ^= data[i];
        for (uint32_t k = 0; k < 8; ++k) {
            crc = (crc >> 1) ^ (0xEDB88320u & (0u - (crc & 1u)));
        }
    }
    return crc;
}
}
}
// @@end

// @@simd::arm::crc32cb_u8
namespace NS_simd {
namespace NS_arm {
FRANKO_INLINE uint32_t FUNC_crc32cb_u8_19ca3df8(uint32_t PARAM_crc, Franko_Fat_Pointer<uint8_t> PARAM_data, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint32_t crc = PARAM_crc;
    const uint8_t* data = PARAM_data.data;
    uint32_t data_length = PARAM_data.count;
    if (data == nullptr && PARAM_count != 0) {
        franko_panic("simd: null data");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > data_length || PARAM_count > data_length - PARAM_src_start) {
        franko_panic("simd: data range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return crc;
    }
    data += PARAM_src_start;
    uint32_t i = 0;
#if defined(__ARM_FEATURE_CRC32)
    for (; i + 8 <= PARAM_count; i += 8) {
        uint64_t chunk;
        std::memcpy(&chunk, data + i, 8);
        crc = __crc32cd(crc, chunk);
    }
#endif
    for (; i < PARAM_count; ++i) {
        crc ^= data[i];
        for (uint32_t k = 0; k < 8; ++k) {
            crc = (crc >> 1) ^ (0x82F63B78u & (0u - (crc & 1u)));
        }
    }
    return crc;
}
}
}
// @@end

// @@simd::arm::vmovl_u8
namespace NS_simd {
namespace NS_arm {
FRANKO_INLINE void FUNC_vmovl_u8_b3d94c16(Franko_Fat_Pointer<uint16_t> PARAM_target, Franko_Fat_Pointer<uint8_t> PARAM_source, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint16_t* target = PARAM_target.data;
    uint32_t target_length = PARAM_target.count;
    if (target == nullptr && PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = PARAM_source.data;
    uint32_t source_length = PARAM_source.count;
    if (source == nullptr && PARAM_count != 0) {
        franko_panic("simd: null source");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > source_length || PARAM_count > source_length - PARAM_src_start) {
        franko_panic("simd: source range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return;
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (static_cast<const void*>(target) == static_cast<const void*>(source)
            && PARAM_dst_start != PARAM_src_start) {
        franko_panic("simd: overlapping target and source ranges");
    }
#endif
    target += PARAM_dst_start;
    source += PARAM_src_start;
    uint32_t i = 0;
    for (; i + 16 <= PARAM_count; i += 16) {
        const uint8x16_t chunk = vld1q_u8(source + i);
        vst1q_u16(target + i, vmovl_u8(vget_low_u8(chunk)));
        vst1q_u16(target + i + 8, vmovl_u8(vget_high_u8(chunk)));
    }
    for (; i < PARAM_count; ++i) {
        target[i] = source[i];
    }
}
}
}
// @@end

// @@simd::arm::vcntq_u8
namespace NS_simd {
namespace NS_arm {
FRANKO_INLINE void FUNC_vcntq_u8_7243e039(Franko_Fat_Pointer<uint8_t> PARAM_target, Franko_Fat_Pointer<uint8_t> PARAM_source, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint8_t* target = PARAM_target.data;
    uint32_t target_length = PARAM_target.count;
    if (target == nullptr && PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = PARAM_source.data;
    uint32_t source_length = PARAM_source.count;
    if (source == nullptr && PARAM_count != 0) {
        franko_panic("simd: null source");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > source_length || PARAM_count > source_length - PARAM_src_start) {
        franko_panic("simd: source range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return;
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (target == source && PARAM_dst_start != PARAM_src_start) {
        franko_panic("simd: overlapping target and source ranges");
    }
#endif
    target += PARAM_dst_start;
    source += PARAM_src_start;
    uint32_t i = 0;
    for (; i + 16 <= PARAM_count; i += 16) {
        vst1q_u8(target + i, vcntq_u8(vld1q_u8(source + i)));
    }
    for (; i < PARAM_count; ++i) {
        uint8_t v = source[i];
        v = static_cast<uint8_t>(v - ((v >> 1) & 0x55u));
        v = static_cast<uint8_t>((v & 0x33u) + ((v >> 2) & 0x33u));
        target[i] = static_cast<uint8_t>((v + (v >> 4)) & 0x0Fu);
    }
}
}
}
// @@end

// @@simd::arm::vmull_p64
namespace NS_simd {
namespace NS_arm {
FRANKO_INLINE void FUNC_vmull_p64_b95c3315(Franko_Fat_Pointer<uint64_t> PARAM_target, Franko_Fat_Pointer<uint64_t> PARAM_a, Franko_Fat_Pointer<uint64_t> PARAM_b, uint32_t PARAM_dst_start, uint32_t PARAM_a_start, uint32_t PARAM_b_start, uint32_t PARAM_count)
{
    uint64_t* target = PARAM_target.data;
    uint32_t target_length = PARAM_target.count;
    if (target == nullptr && PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint64_t* a = PARAM_a.data;
    uint32_t a_length = PARAM_a.count;
    if (a == nullptr && PARAM_count != 0) {
        franko_panic("simd: null a");
    }
    const uint64_t* b = PARAM_b.data;
    uint32_t b_length = PARAM_b.count;
    if (b == nullptr && PARAM_count != 0) {
        franko_panic("simd: null b");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > (target_length - PARAM_dst_start) / 2) {
        franko_panic("simd: target range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_a_start > a_length || PARAM_count > a_length - PARAM_a_start) {
        franko_panic("simd: a range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_b_start > b_length || PARAM_count > b_length - PARAM_b_start) {
        franko_panic("simd: b range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return;
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (target == a && PARAM_dst_start != PARAM_a_start) {
        franko_panic("simd: overlapping target and a ranges");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (target == b && PARAM_dst_start != PARAM_b_start) {
        franko_panic("simd: overlapping target and b ranges");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (static_cast<const void*>(a) == static_cast<const void*>(b) && PARAM_a_start != PARAM_b_start) {
        franko_panic("simd: overlapping a and b ranges");
    }
#endif
    target += PARAM_dst_start;
    a += PARAM_a_start;
    b += PARAM_b_start;
    uint32_t i = 0;
#if defined(__ARM_FEATURE_CRYPTO)
    for (; i + 1 <= PARAM_count; i += 1) {
        vst1q_u64(target + 2 * i, vreinterpretq_u64_p128(vmull_p64(a[i], b[i])));
    }
#endif
    for (; i < PARAM_count; ++i) {
        const uint64_t word_a = a[i];
        const uint64_t word_b = b[i];
        uint64_t lo = 0;
        uint64_t hi = 0;
        for (uint32_t k = 0; k < 64; ++k) {
            hi = (hi << 1) | (lo >> 63);
            lo <<= 1;
            if (((word_a >> (63 - k)) & 1u) != 0u) {
                lo ^= word_b;
            }
        }
        target[2 * i] = lo;
        target[2 * i + 1] = hi;
    }
}
}
}
// @@end

// @@simd::arm::vst1q_u8
namespace NS_simd {
namespace NS_arm {
FRANKO_INLINE void FUNC_vst1q_u8_7243e039(Franko_Fat_Pointer<uint8_t> PARAM_target, Franko_Fat_Pointer<uint8_t> PARAM_source, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint8_t* target = PARAM_target.data;
    uint32_t target_length = PARAM_target.count;
    if (target == nullptr && PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = PARAM_source.data;
    uint32_t source_length = PARAM_source.count;
    if (source == nullptr && PARAM_count != 0) {
        franko_panic("simd: null source");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_src_start > source_length || PARAM_count > source_length - PARAM_src_start) {
        franko_panic("simd: source range out of bounds");
    }
#endif
    if (PARAM_count == 0) {
        return;
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (target == source && PARAM_dst_start != PARAM_src_start) {
        franko_panic("simd: overlapping target and source ranges");
    }
#endif
    target += PARAM_dst_start;
    source += PARAM_src_start;
    uint32_t i = 0;
    for (; i + 16 <= PARAM_count; i += 16) {
        vst1q_u8(target + i, vld1q_u8(source + i));
    }
    for (; i < PARAM_count; ++i) {
        target[i] = source[i];
    }
}
}
}
// @@end
