// @@common
#include "FrankoRuntime.hpp"

#if defined(__x86_64__) || defined(_M_X64)
#include <immintrin.h>
#elif defined(__aarch64__)
#include <arm_neon.h>
#elif defined(_M_ARM64)
#include <arm64_neon.h>
#endif
// @@end





// @@simd::x86::xor_si128
namespace NS_simd {
namespace NS_x86 {
FRANKO_INLINE void FUNC_xor_si128_7243e039(Franko_Fat_Pointer<uint8_t> PARAM_target, Franko_Fat_Pointer<uint8_t> PARAM_source, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint8_t* target = nullptr;
    uint32_t target_length = 0;
    if (PARAM_target.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_target.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        target = array->data;
        target_length = array->count;
    } else if (PARAM_target.mode == 2) {
        target = static_cast<uint8_t*>(PARAM_target.data);
        target_length = PARAM_target.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = nullptr;
    uint32_t source_length = 0;
    if (PARAM_source.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_source.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        source = array->data;
        source_length = array->count;
    } else if (PARAM_source.mode == 2) {
        source = static_cast<const uint8_t*>(PARAM_source.data);
        source_length = PARAM_source.count;
    } else if (PARAM_count != 0) {
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

// @@simd::x86::and_si128
namespace NS_simd {
namespace NS_x86 {
FRANKO_INLINE void FUNC_and_si128_7243e039(Franko_Fat_Pointer<uint8_t> PARAM_target, Franko_Fat_Pointer<uint8_t> PARAM_source, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint8_t* target = nullptr;
    uint32_t target_length = 0;
    if (PARAM_target.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_target.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        target = array->data;
        target_length = array->count;
    } else if (PARAM_target.mode == 2) {
        target = static_cast<uint8_t*>(PARAM_target.data);
        target_length = PARAM_target.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = nullptr;
    uint32_t source_length = 0;
    if (PARAM_source.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_source.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        source = array->data;
        source_length = array->count;
    } else if (PARAM_source.mode == 2) {
        source = static_cast<const uint8_t*>(PARAM_source.data);
        source_length = PARAM_source.count;
    } else if (PARAM_count != 0) {
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

// @@simd::x86::or_si128
namespace NS_simd {
namespace NS_x86 {
FRANKO_INLINE void FUNC_or_si128_7243e039(Franko_Fat_Pointer<uint8_t> PARAM_target, Franko_Fat_Pointer<uint8_t> PARAM_source, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint8_t* target = nullptr;
    uint32_t target_length = 0;
    if (PARAM_target.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_target.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        target = array->data;
        target_length = array->count;
    } else if (PARAM_target.mode == 2) {
        target = static_cast<uint8_t*>(PARAM_target.data);
        target_length = PARAM_target.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = nullptr;
    uint32_t source_length = 0;
    if (PARAM_source.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_source.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        source = array->data;
        source_length = array->count;
    } else if (PARAM_source.mode == 2) {
        source = static_cast<const uint8_t*>(PARAM_source.data);
        source_length = PARAM_source.count;
    } else if (PARAM_count != 0) {
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

// @@simd::x86::andnot_si128
namespace NS_simd {
namespace NS_x86 {
FRANKO_INLINE void FUNC_andnot_si128_7243e039(Franko_Fat_Pointer<uint8_t> PARAM_target, Franko_Fat_Pointer<uint8_t> PARAM_source, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint8_t* target = nullptr;
    uint32_t target_length = 0;
    if (PARAM_target.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_target.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        target = array->data;
        target_length = array->count;
    } else if (PARAM_target.mode == 2) {
        target = static_cast<uint8_t*>(PARAM_target.data);
        target_length = PARAM_target.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = nullptr;
    uint32_t source_length = 0;
    if (PARAM_source.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_source.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        source = array->data;
        source_length = array->count;
    } else if (PARAM_source.mode == 2) {
        source = static_cast<const uint8_t*>(PARAM_source.data);
        source_length = PARAM_source.count;
    } else if (PARAM_count != 0) {
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
    uint8_t* target = nullptr;
    uint32_t target_length = 0;
    if (PARAM_target.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_target.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        target = array->data;
        target_length = array->count;
    } else if (PARAM_target.mode == 2) {
        target = static_cast<uint8_t*>(PARAM_target.data);
        target_length = PARAM_target.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = nullptr;
    uint32_t source_length = 0;
    if (PARAM_source.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_source.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        source = array->data;
        source_length = array->count;
    } else if (PARAM_source.mode == 2) {
        source = static_cast<const uint8_t*>(PARAM_source.data);
        source_length = PARAM_source.count;
    } else if (PARAM_count != 0) {
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
    uint8_t* target = nullptr;
    uint32_t target_length = 0;
    if (PARAM_target.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_target.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        target = array->data;
        target_length = array->count;
    } else if (PARAM_target.mode == 2) {
        target = static_cast<uint8_t*>(PARAM_target.data);
        target_length = PARAM_target.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = nullptr;
    uint32_t source_length = 0;
    if (PARAM_source.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_source.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        source = array->data;
        source_length = array->count;
    } else if (PARAM_source.mode == 2) {
        source = static_cast<const uint8_t*>(PARAM_source.data);
        source_length = PARAM_source.count;
    } else if (PARAM_count != 0) {
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
    uint8_t* target = nullptr;
    uint32_t target_length = 0;
    if (PARAM_target.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_target.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        target = array->data;
        target_length = array->count;
    } else if (PARAM_target.mode == 2) {
        target = static_cast<uint8_t*>(PARAM_target.data);
        target_length = PARAM_target.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = nullptr;
    uint32_t source_length = 0;
    if (PARAM_source.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_source.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        source = array->data;
        source_length = array->count;
    } else if (PARAM_source.mode == 2) {
        source = static_cast<const uint8_t*>(PARAM_source.data);
        source_length = PARAM_source.count;
    } else if (PARAM_count != 0) {
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
    uint8_t* target = nullptr;
    uint32_t target_length = 0;
    if (PARAM_target.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_target.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        target = array->data;
        target_length = array->count;
    } else if (PARAM_target.mode == 2) {
        target = static_cast<uint8_t*>(PARAM_target.data);
        target_length = PARAM_target.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = nullptr;
    uint32_t source_length = 0;
    if (PARAM_source.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_source.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        source = array->data;
        source_length = array->count;
    } else if (PARAM_source.mode == 2) {
        source = static_cast<const uint8_t*>(PARAM_source.data);
        source_length = PARAM_source.count;
    } else if (PARAM_count != 0) {
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
    uint8_t* target = nullptr;
    uint32_t target_length = 0;
    if (PARAM_target.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_target.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        target = array->data;
        target_length = array->count;
    } else if (PARAM_target.mode == 2) {
        target = static_cast<uint8_t*>(PARAM_target.data);
        target_length = PARAM_target.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = nullptr;
    uint32_t source_length = 0;
    if (PARAM_source.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_source.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        source = array->data;
        source_length = array->count;
    } else if (PARAM_source.mode == 2) {
        source = static_cast<const uint8_t*>(PARAM_source.data);
        source_length = PARAM_source.count;
    } else if (PARAM_count != 0) {
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
    uint8_t* target = nullptr;
    uint32_t target_length = 0;
    if (PARAM_target.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_target.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        target = array->data;
        target_length = array->count;
    } else if (PARAM_target.mode == 2) {
        target = static_cast<uint8_t*>(PARAM_target.data);
        target_length = PARAM_target.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = nullptr;
    uint32_t source_length = 0;
    if (PARAM_source.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_source.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        source = array->data;
        source_length = array->count;
    } else if (PARAM_source.mode == 2) {
        source = static_cast<const uint8_t*>(PARAM_source.data);
        source_length = PARAM_source.count;
    } else if (PARAM_count != 0) {
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
    uint8_t* target = nullptr;
    uint32_t target_length = 0;
    if (PARAM_target.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_target.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        target = array->data;
        target_length = array->count;
    } else if (PARAM_target.mode == 2) {
        target = static_cast<uint8_t*>(PARAM_target.data);
        target_length = PARAM_target.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = nullptr;
    uint32_t source_length = 0;
    if (PARAM_source.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_source.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        source = array->data;
        source_length = array->count;
    } else if (PARAM_source.mode == 2) {
        source = static_cast<const uint8_t*>(PARAM_source.data);
        source_length = PARAM_source.count;
    } else if (PARAM_count != 0) {
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
    uint8_t* target = nullptr;
    uint32_t target_length = 0;
    if (PARAM_target.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_target.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        target = array->data;
        target_length = array->count;
    } else if (PARAM_target.mode == 2) {
        target = static_cast<uint8_t*>(PARAM_target.data);
        target_length = PARAM_target.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = nullptr;
    uint32_t source_length = 0;
    if (PARAM_source.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_source.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        source = array->data;
        source_length = array->count;
    } else if (PARAM_source.mode == 2) {
        source = static_cast<const uint8_t*>(PARAM_source.data);
        source_length = PARAM_source.count;
    } else if (PARAM_count != 0) {
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
    uint8_t* target = nullptr;
    uint32_t target_length = 0;
    if (PARAM_target.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_target.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        target = array->data;
        target_length = array->count;
    } else if (PARAM_target.mode == 2) {
        target = static_cast<uint8_t*>(PARAM_target.data);
        target_length = PARAM_target.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = nullptr;
    uint32_t source_length = 0;
    if (PARAM_source.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_source.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        source = array->data;
        source_length = array->count;
    } else if (PARAM_source.mode == 2) {
        source = static_cast<const uint8_t*>(PARAM_source.data);
        source_length = PARAM_source.count;
    } else if (PARAM_count != 0) {
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
    uint8_t* target = nullptr;
    uint32_t target_length = 0;
    if (PARAM_target.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_target.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        target = array->data;
        target_length = array->count;
    } else if (PARAM_target.mode == 2) {
        target = static_cast<uint8_t*>(PARAM_target.data);
        target_length = PARAM_target.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = nullptr;
    uint32_t source_length = 0;
    if (PARAM_source.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_source.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        source = array->data;
        source_length = array->count;
    } else if (PARAM_source.mode == 2) {
        source = static_cast<const uint8_t*>(PARAM_source.data);
        source_length = PARAM_source.count;
    } else if (PARAM_count != 0) {
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
    uint8_t* target = nullptr;
    uint32_t target_length = 0;
    if (PARAM_target.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_target.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        target = array->data;
        target_length = array->count;
    } else if (PARAM_target.mode == 2) {
        target = static_cast<uint8_t*>(PARAM_target.data);
        target_length = PARAM_target.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = nullptr;
    uint32_t source_length = 0;
    if (PARAM_source.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_source.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        source = array->data;
        source_length = array->count;
    } else if (PARAM_source.mode == 2) {
        source = static_cast<const uint8_t*>(PARAM_source.data);
        source_length = PARAM_source.count;
    } else if (PARAM_count != 0) {
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
    uint8_t* target = nullptr;
    uint32_t target_length = 0;
    if (PARAM_target.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_target.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        target = array->data;
        target_length = array->count;
    } else if (PARAM_target.mode == 2) {
        target = static_cast<uint8_t*>(PARAM_target.data);
        target_length = PARAM_target.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = nullptr;
    uint32_t source_length = 0;
    if (PARAM_source.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_source.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        source = array->data;
        source_length = array->count;
    } else if (PARAM_source.mode == 2) {
        source = static_cast<const uint8_t*>(PARAM_source.data);
        source_length = PARAM_source.count;
    } else if (PARAM_count != 0) {
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
    uint8_t* target = nullptr;
    uint32_t target_length = 0;
    if (PARAM_target.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_target.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        target = array->data;
        target_length = array->count;
    } else if (PARAM_target.mode == 2) {
        target = static_cast<uint8_t*>(PARAM_target.data);
        target_length = PARAM_target.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = nullptr;
    uint32_t source_length = 0;
    if (PARAM_source.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_source.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        source = array->data;
        source_length = array->count;
    } else if (PARAM_source.mode == 2) {
        source = static_cast<const uint8_t*>(PARAM_source.data);
        source_length = PARAM_source.count;
    } else if (PARAM_count != 0) {
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
    uint8_t* target = nullptr;
    uint32_t target_length = 0;
    if (PARAM_target.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_target.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        target = array->data;
        target_length = array->count;
    } else if (PARAM_target.mode == 2) {
        target = static_cast<uint8_t*>(PARAM_target.data);
        target_length = PARAM_target.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = nullptr;
    uint32_t source_length = 0;
    if (PARAM_source.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_source.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        source = array->data;
        source_length = array->count;
    } else if (PARAM_source.mode == 2) {
        source = static_cast<const uint8_t*>(PARAM_source.data);
        source_length = PARAM_source.count;
    } else if (PARAM_count != 0) {
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
    uint8_t* target = nullptr;
    uint32_t target_length = 0;
    if (PARAM_target.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_target.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        target = array->data;
        target_length = array->count;
    } else if (PARAM_target.mode == 2) {
        target = static_cast<uint8_t*>(PARAM_target.data);
        target_length = PARAM_target.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = nullptr;
    uint32_t source_length = 0;
    if (PARAM_source.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_source.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        source = array->data;
        source_length = array->count;
    } else if (PARAM_source.mode == 2) {
        source = static_cast<const uint8_t*>(PARAM_source.data);
        source_length = PARAM_source.count;
    } else if (PARAM_count != 0) {
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
    uint8_t* target = nullptr;
    uint32_t target_length = 0;
    if (PARAM_target.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_target.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        target = array->data;
        target_length = array->count;
    } else if (PARAM_target.mode == 2) {
        target = static_cast<uint8_t*>(PARAM_target.data);
        target_length = PARAM_target.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = nullptr;
    uint32_t source_length = 0;
    if (PARAM_source.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_source.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        source = array->data;
        source_length = array->count;
    } else if (PARAM_source.mode == 2) {
        source = static_cast<const uint8_t*>(PARAM_source.data);
        source_length = PARAM_source.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null source");
    }
    const uint8_t* mask = nullptr;
    uint32_t mask_length = 0;
    if (PARAM_mask.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_mask.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        mask = array->data;
        mask_length = array->count;
    } else if (PARAM_mask.mode == 2) {
        mask = static_cast<const uint8_t*>(PARAM_mask.data);
        mask_length = PARAM_mask.count;
    } else if (PARAM_count != 0) {
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
    uint8_t* target = nullptr;
    uint32_t target_length = 0;
    if (PARAM_target.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_target.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        target = array->data;
        target_length = array->count;
    } else if (PARAM_target.mode == 2) {
        target = static_cast<uint8_t*>(PARAM_target.data);
        target_length = PARAM_target.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = nullptr;
    uint32_t source_length = 0;
    if (PARAM_source.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_source.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        source = array->data;
        source_length = array->count;
    } else if (PARAM_source.mode == 2) {
        source = static_cast<const uint8_t*>(PARAM_source.data);
        source_length = PARAM_source.count;
    } else if (PARAM_count != 0) {
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
    uint8_t* target = nullptr;
    uint32_t target_length = 0;
    if (PARAM_target.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_target.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        target = array->data;
        target_length = array->count;
    } else if (PARAM_target.mode == 2) {
        target = static_cast<uint8_t*>(PARAM_target.data);
        target_length = PARAM_target.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = nullptr;
    uint32_t source_length = 0;
    if (PARAM_source.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_source.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        source = array->data;
        source_length = array->count;
    } else if (PARAM_source.mode == 2) {
        source = static_cast<const uint8_t*>(PARAM_source.data);
        source_length = PARAM_source.count;
    } else if (PARAM_count != 0) {
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
    uint8_t* target = nullptr;
    uint32_t target_length = 0;
    if (PARAM_target.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_target.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        target = array->data;
        target_length = array->count;
    } else if (PARAM_target.mode == 2) {
        target = static_cast<uint8_t*>(PARAM_target.data);
        target_length = PARAM_target.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = nullptr;
    uint32_t source_length = 0;
    if (PARAM_source.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_source.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        source = array->data;
        source_length = array->count;
    } else if (PARAM_source.mode == 2) {
        source = static_cast<const uint8_t*>(PARAM_source.data);
        source_length = PARAM_source.count;
    } else if (PARAM_count != 0) {
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
    uint8_t* target = nullptr;
    uint32_t target_length = 0;
    if (PARAM_target.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_target.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        target = array->data;
        target_length = array->count;
    } else if (PARAM_target.mode == 2) {
        target = static_cast<uint8_t*>(PARAM_target.data);
        target_length = PARAM_target.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const int16_t* source = nullptr;
    uint32_t source_length = 0;
    if (PARAM_source.mode == 3) {
        Franko_Dynamic_Array<int16_t>* array = static_cast<Franko_Dynamic_Array<int16_t>*>(PARAM_source.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        source = array->data;
        source_length = array->count;
    } else if (PARAM_source.mode == 2) {
        source = static_cast<const int16_t*>(PARAM_source.data);
        source_length = PARAM_source.count;
    } else if (PARAM_count != 0) {
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
    const uint8_t* left = nullptr;
    uint32_t left_length = 0;
    if (PARAM_left.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_left.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        left = array->data;
        left_length = array->count;
    } else if (PARAM_left.mode == 2) {
        left = static_cast<const uint8_t*>(PARAM_left.data);
        left_length = PARAM_left.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null left");
    }
    const uint8_t* right = nullptr;
    uint32_t right_length = 0;
    if (PARAM_right.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_right.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        right = array->data;
        right_length = array->count;
    } else if (PARAM_right.mode == 2) {
        right = static_cast<const uint8_t*>(PARAM_right.data);
        right_length = PARAM_right.count;
    } else if (PARAM_count != 0) {
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
    int8_t* target = nullptr;
    uint32_t target_length = 0;
    if (PARAM_target.mode == 3) {
        Franko_Dynamic_Array<int8_t>* array = static_cast<Franko_Dynamic_Array<int8_t>*>(PARAM_target.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        target = array->data;
        target_length = array->count;
    } else if (PARAM_target.mode == 2) {
        target = static_cast<int8_t*>(PARAM_target.data);
        target_length = PARAM_target.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const int8_t* source = nullptr;
    uint32_t source_length = 0;
    if (PARAM_source.mode == 3) {
        Franko_Dynamic_Array<int8_t>* array = static_cast<Franko_Dynamic_Array<int8_t>*>(PARAM_source.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        source = array->data;
        source_length = array->count;
    } else if (PARAM_source.mode == 2) {
        source = static_cast<const int8_t*>(PARAM_source.data);
        source_length = PARAM_source.count;
    } else if (PARAM_count != 0) {
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
    uint8_t* target = nullptr;
    uint32_t target_length = 0;
    if (PARAM_target.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_target.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        target = array->data;
        target_length = array->count;
    } else if (PARAM_target.mode == 2) {
        target = static_cast<uint8_t*>(PARAM_target.data);
        target_length = PARAM_target.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null target");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
    const uint8_t* indices = nullptr;
    uint32_t indices_length = 0;
    if (PARAM_indices.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_indices.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        indices = array->data;
        indices_length = array->count;
    } else if (PARAM_indices.mode == 2) {
        indices = static_cast<const uint8_t*>(PARAM_indices.data);
        indices_length = PARAM_indices.count;
    } else if (PARAM_count != 0) {
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

// @@simd::x86::aesenc_si128
namespace NS_simd {
namespace NS_x86 {
FRANKO_INLINE int32_t FUNC_aesenc_si128_697d55d9(Franko_Fat_Pointer<uint8_t> PARAM_target, Franko_Fat_Pointer<uint8_t> PARAM_round_key, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint8_t* target = nullptr;
    uint32_t target_length = 0;
    if (PARAM_target.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_target.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        target = array->data;
        target_length = array->count;
    } else if (PARAM_target.mode == 2) {
        target = static_cast<uint8_t*>(PARAM_target.data);
        target_length = PARAM_target.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null target");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
    const uint8_t* round_key = nullptr;
    uint32_t round_key_length = 0;
    if (PARAM_round_key.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_round_key.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        round_key = array->data;
        round_key_length = array->count;
    } else if (PARAM_round_key.mode == 2) {
        round_key = static_cast<const uint8_t*>(PARAM_round_key.data);
        round_key_length = PARAM_round_key.count;
    } else if (PARAM_count != 0) {
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
#if defined(__AES__) || defined(_MSC_VER)
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

// @@simd::x86::aesdec_si128
namespace NS_simd {
namespace NS_x86 {
FRANKO_INLINE int32_t FUNC_aesdec_si128_697d55d9(Franko_Fat_Pointer<uint8_t> PARAM_target, Franko_Fat_Pointer<uint8_t> PARAM_round_key, uint32_t PARAM_dst_start, uint32_t PARAM_src_start, uint32_t PARAM_count)
{
    uint8_t* target = nullptr;
    uint32_t target_length = 0;
    if (PARAM_target.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_target.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        target = array->data;
        target_length = array->count;
    } else if (PARAM_target.mode == 2) {
        target = static_cast<uint8_t*>(PARAM_target.data);
        target_length = PARAM_target.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null target");
    }
#if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_dst_start > target_length || PARAM_count > target_length - PARAM_dst_start) {
        franko_panic("simd: target range out of bounds");
    }
#endif
    const uint8_t* round_key = nullptr;
    uint32_t round_key_length = 0;
    if (PARAM_round_key.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_round_key.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        round_key = array->data;
        round_key_length = array->count;
    } else if (PARAM_round_key.mode == 2) {
        round_key = static_cast<const uint8_t*>(PARAM_round_key.data);
        round_key_length = PARAM_round_key.count;
    } else if (PARAM_count != 0) {
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
#if defined(__AES__) || defined(_MSC_VER)
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
    uint8_t* target = nullptr;
    uint32_t target_length = 0;
    if (PARAM_target.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_target.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        target = array->data;
        target_length = array->count;
    } else if (PARAM_target.mode == 2) {
        target = static_cast<uint8_t*>(PARAM_target.data);
        target_length = PARAM_target.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = nullptr;
    uint32_t source_length = 0;
    if (PARAM_source.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_source.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        source = array->data;
        source_length = array->count;
    } else if (PARAM_source.mode == 2) {
        source = static_cast<const uint8_t*>(PARAM_source.data);
        source_length = PARAM_source.count;
    } else if (PARAM_count != 0) {
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
    uint8_t* target = nullptr;
    uint32_t target_length = 0;
    if (PARAM_target.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_target.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        target = array->data;
        target_length = array->count;
    } else if (PARAM_target.mode == 2) {
        target = static_cast<uint8_t*>(PARAM_target.data);
        target_length = PARAM_target.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = nullptr;
    uint32_t source_length = 0;
    if (PARAM_source.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_source.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        source = array->data;
        source_length = array->count;
    } else if (PARAM_source.mode == 2) {
        source = static_cast<const uint8_t*>(PARAM_source.data);
        source_length = PARAM_source.count;
    } else if (PARAM_count != 0) {
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
    uint8_t* target = nullptr;
    uint32_t target_length = 0;
    if (PARAM_target.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_target.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        target = array->data;
        target_length = array->count;
    } else if (PARAM_target.mode == 2) {
        target = static_cast<uint8_t*>(PARAM_target.data);
        target_length = PARAM_target.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = nullptr;
    uint32_t source_length = 0;
    if (PARAM_source.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_source.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        source = array->data;
        source_length = array->count;
    } else if (PARAM_source.mode == 2) {
        source = static_cast<const uint8_t*>(PARAM_source.data);
        source_length = PARAM_source.count;
    } else if (PARAM_count != 0) {
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
    uint8_t* target = nullptr;
    uint32_t target_length = 0;
    if (PARAM_target.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_target.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        target = array->data;
        target_length = array->count;
    } else if (PARAM_target.mode == 2) {
        target = static_cast<uint8_t*>(PARAM_target.data);
        target_length = PARAM_target.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = nullptr;
    uint32_t source_length = 0;
    if (PARAM_source.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_source.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        source = array->data;
        source_length = array->count;
    } else if (PARAM_source.mode == 2) {
        source = static_cast<const uint8_t*>(PARAM_source.data);
        source_length = PARAM_source.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null source");
    }
    const uint8_t* mask = nullptr;
    uint32_t mask_length = 0;
    if (PARAM_mask.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_mask.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        mask = array->data;
        mask_length = array->count;
    } else if (PARAM_mask.mode == 2) {
        mask = static_cast<const uint8_t*>(PARAM_mask.data);
        mask_length = PARAM_mask.count;
    } else if (PARAM_count != 0) {
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
    uint8_t* target = nullptr;
    uint32_t target_length = 0;
    if (PARAM_target.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_target.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        target = array->data;
        target_length = array->count;
    } else if (PARAM_target.mode == 2) {
        target = static_cast<uint8_t*>(PARAM_target.data);
        target_length = PARAM_target.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = nullptr;
    uint32_t source_length = 0;
    if (PARAM_source.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_source.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        source = array->data;
        source_length = array->count;
    } else if (PARAM_source.mode == 2) {
        source = static_cast<const uint8_t*>(PARAM_source.data);
        source_length = PARAM_source.count;
    } else if (PARAM_count != 0) {
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
    uint8_t* target = nullptr;
    uint32_t target_length = 0;
    if (PARAM_target.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_target.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        target = array->data;
        target_length = array->count;
    } else if (PARAM_target.mode == 2) {
        target = static_cast<uint8_t*>(PARAM_target.data);
        target_length = PARAM_target.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = nullptr;
    uint32_t source_length = 0;
    if (PARAM_source.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_source.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        source = array->data;
        source_length = array->count;
    } else if (PARAM_source.mode == 2) {
        source = static_cast<const uint8_t*>(PARAM_source.data);
        source_length = PARAM_source.count;
    } else if (PARAM_count != 0) {
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
    uint8_t* target = nullptr;
    uint32_t target_length = 0;
    if (PARAM_target.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_target.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        target = array->data;
        target_length = array->count;
    } else if (PARAM_target.mode == 2) {
        target = static_cast<uint8_t*>(PARAM_target.data);
        target_length = PARAM_target.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = nullptr;
    uint32_t source_length = 0;
    if (PARAM_source.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_source.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        source = array->data;
        source_length = array->count;
    } else if (PARAM_source.mode == 2) {
        source = static_cast<const uint8_t*>(PARAM_source.data);
        source_length = PARAM_source.count;
    } else if (PARAM_count != 0) {
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
    uint8_t* target = nullptr;
    uint32_t target_length = 0;
    if (PARAM_target.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_target.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        target = array->data;
        target_length = array->count;
    } else if (PARAM_target.mode == 2) {
        target = static_cast<uint8_t*>(PARAM_target.data);
        target_length = PARAM_target.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const int16_t* source = nullptr;
    uint32_t source_length = 0;
    if (PARAM_source.mode == 3) {
        Franko_Dynamic_Array<int16_t>* array = static_cast<Franko_Dynamic_Array<int16_t>*>(PARAM_source.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        source = array->data;
        source_length = array->count;
    } else if (PARAM_source.mode == 2) {
        source = static_cast<const int16_t*>(PARAM_source.data);
        source_length = PARAM_source.count;
    } else if (PARAM_count != 0) {
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
    uint8_t* target = nullptr;
    uint32_t target_length = 0;
    if (PARAM_target.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_target.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        target = array->data;
        target_length = array->count;
    } else if (PARAM_target.mode == 2) {
        target = static_cast<uint8_t*>(PARAM_target.data);
        target_length = PARAM_target.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* source = nullptr;
    uint32_t source_length = 0;
    if (PARAM_source.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_source.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        source = array->data;
        source_length = array->count;
    } else if (PARAM_source.mode == 2) {
        source = static_cast<const uint8_t*>(PARAM_source.data);
        source_length = PARAM_source.count;
    } else if (PARAM_count != 0) {
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
    uint32_t* target = nullptr;
    uint32_t target_length = 0;
    if (PARAM_target.mode == 3) {
        Franko_Dynamic_Array<uint32_t>* array = static_cast<Franko_Dynamic_Array<uint32_t>*>(PARAM_target.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        target = array->data;
        target_length = array->count;
    } else if (PARAM_target.mode == 2) {
        target = static_cast<uint32_t*>(PARAM_target.data);
        target_length = PARAM_target.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint16_t* source = nullptr;
    uint32_t source_length = 0;
    if (PARAM_source.mode == 3) {
        Franko_Dynamic_Array<uint16_t>* array = static_cast<Franko_Dynamic_Array<uint16_t>*>(PARAM_source.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        source = array->data;
        source_length = array->count;
    } else if (PARAM_source.mode == 2) {
        source = static_cast<const uint16_t*>(PARAM_source.data);
        source_length = PARAM_source.count;
    } else if (PARAM_count != 0) {
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
    int8_t* target = nullptr;
    uint32_t target_length = 0;
    if (PARAM_target.mode == 3) {
        Franko_Dynamic_Array<int8_t>* array = static_cast<Franko_Dynamic_Array<int8_t>*>(PARAM_target.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        target = array->data;
        target_length = array->count;
    } else if (PARAM_target.mode == 2) {
        target = static_cast<int8_t*>(PARAM_target.data);
        target_length = PARAM_target.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const int8_t* source = nullptr;
    uint32_t source_length = 0;
    if (PARAM_source.mode == 3) {
        Franko_Dynamic_Array<int8_t>* array = static_cast<Franko_Dynamic_Array<int8_t>*>(PARAM_source.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        source = array->data;
        source_length = array->count;
    } else if (PARAM_source.mode == 2) {
        source = static_cast<const int8_t*>(PARAM_source.data);
        source_length = PARAM_source.count;
    } else if (PARAM_count != 0) {
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
    uint8_t* target = nullptr;
    uint32_t target_length = 0;
    if (PARAM_target.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_target.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        target = array->data;
        target_length = array->count;
    } else if (PARAM_target.mode == 2) {
        target = static_cast<uint8_t*>(PARAM_target.data);
        target_length = PARAM_target.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* indices = nullptr;
    uint32_t indices_length = 0;
    if (PARAM_indices.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_indices.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        indices = array->data;
        indices_length = array->count;
    } else if (PARAM_indices.mode == 2) {
        indices = static_cast<const uint8_t*>(PARAM_indices.data);
        indices_length = PARAM_indices.count;
    } else if (PARAM_count != 0) {
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
    uint8_t* target = nullptr;
    uint32_t target_length = 0;
    if (PARAM_target.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_target.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        target = array->data;
        target_length = array->count;
    } else if (PARAM_target.mode == 2) {
        target = static_cast<uint8_t*>(PARAM_target.data);
        target_length = PARAM_target.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* round_key = nullptr;
    uint32_t round_key_length = 0;
    if (PARAM_round_key.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_round_key.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        round_key = array->data;
        round_key_length = array->count;
    } else if (PARAM_round_key.mode == 2) {
        round_key = static_cast<const uint8_t*>(PARAM_round_key.data);
        round_key_length = PARAM_round_key.count;
    } else if (PARAM_count != 0) {
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
    uint8_t* target = nullptr;
    uint32_t target_length = 0;
    if (PARAM_target.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_target.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        target = array->data;
        target_length = array->count;
    } else if (PARAM_target.mode == 2) {
        target = static_cast<uint8_t*>(PARAM_target.data);
        target_length = PARAM_target.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    const uint8_t* round_key = nullptr;
    uint32_t round_key_length = 0;
    if (PARAM_round_key.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_round_key.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        round_key = array->data;
        round_key_length = array->count;
    } else if (PARAM_round_key.mode == 2) {
        round_key = static_cast<const uint8_t*>(PARAM_round_key.data);
        round_key_length = PARAM_round_key.count;
    } else if (PARAM_count != 0) {
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
