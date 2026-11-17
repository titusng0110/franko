// @@common
#include "FrankoRuntime.hpp"

#if defined(__x86_64__) || defined(_M_X64)
#include <immintrin.h>
#elif defined(__aarch64__) || defined(_M_ARM64)
#include <arm_neon.h>
#endif
// @@end

// @@simd::x86::cmpeq_epi8_mask
namespace NS_simd {
namespace NS_x86 {
FRANKO_INLINE uint64_t FUNC_cmpeq_epi8_mask_97c839ba(Franko_Fat_Pointer<uint8_t> PARAM_target, uint8_t PARAM_value, uint32_t PARAM_start, uint32_t PARAM_count)
{
    if (PARAM_count > 64) {
        franko_panic("simd: mask count must be at most 64");
    }
    const uint8_t* data = nullptr;
    uint32_t length = 0;
    if (PARAM_target.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_target.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        data = array->data;
        length = array->count;
    } else if (PARAM_target.mode == 2) {
        data = static_cast<const uint8_t*>(PARAM_target.data);
        length = PARAM_target.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    #if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_start > length || PARAM_count > length - PARAM_start) {
        franko_panic("simd: target range out of bounds");
    }
    #endif
    if (PARAM_count == 0) {
        return 0;
    }
    data += PARAM_start;
    const __m128i needle = _mm_set1_epi8(static_cast<char>(PARAM_value));
    uint64_t mask = 0;
    uint32_t i = 0;
    for (; i + 16 <= PARAM_count; i += 16) {
        const __m128i bytes = _mm_loadu_si128(reinterpret_cast<const __m128i*>(data + i));
        const __m128i result = _mm_cmpeq_epi8(bytes, needle);
        mask |= static_cast<uint64_t>(static_cast<uint32_t>(_mm_movemask_epi8(result))) << i;
    }
    for (; i < PARAM_count; ++i) {
        if (data[i] == PARAM_value) {
            mask |= static_cast<uint64_t>(1) << i;
        }
    }
    return mask;
}
}
}
// @@end

// @@simd::x86::cmpgt_epi8_mask
namespace NS_simd {
namespace NS_x86 {
FRANKO_INLINE uint64_t FUNC_cmpgt_epi8_mask_97c839ba(Franko_Fat_Pointer<uint8_t> PARAM_target, uint8_t PARAM_value, uint32_t PARAM_start, uint32_t PARAM_count)
{
    if (PARAM_count > 64) {
        franko_panic("simd: mask count must be at most 64");
    }
    const uint8_t* data = nullptr;
    uint32_t length = 0;
    if (PARAM_target.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_target.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        data = array->data;
        length = array->count;
    } else if (PARAM_target.mode == 2) {
        data = static_cast<const uint8_t*>(PARAM_target.data);
        length = PARAM_target.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    #if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_start > length || PARAM_count > length - PARAM_start) {
        franko_panic("simd: target range out of bounds");
    }
    #endif
    if (PARAM_count == 0) {
        return 0;
    }
    data += PARAM_start;
    const __m128i needle = _mm_set1_epi8(static_cast<char>(PARAM_value));
    uint64_t mask = 0;
    uint32_t i = 0;
    for (; i + 16 <= PARAM_count; i += 16) {
        const __m128i bytes = _mm_loadu_si128(reinterpret_cast<const __m128i*>(data + i));
        const __m128i result = _mm_cmpgt_epi8(bytes, needle);
        mask |= static_cast<uint64_t>(static_cast<uint32_t>(_mm_movemask_epi8(result))) << i;
    }
    for (; i < PARAM_count; ++i) {
        if (static_cast<int8_t>(data[i]) > static_cast<int8_t>(PARAM_value)) {
            mask |= static_cast<uint64_t>(1) << i;
        }
    }
    return mask;
}
}
}
// @@end

// @@simd::x86::cmplt_epi8_mask
namespace NS_simd {
namespace NS_x86 {
FRANKO_INLINE uint64_t FUNC_cmplt_epi8_mask_97c839ba(Franko_Fat_Pointer<uint8_t> PARAM_target, uint8_t PARAM_value, uint32_t PARAM_start, uint32_t PARAM_count)
{
    if (PARAM_count > 64) {
        franko_panic("simd: mask count must be at most 64");
    }
    const uint8_t* data = nullptr;
    uint32_t length = 0;
    if (PARAM_target.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_target.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        data = array->data;
        length = array->count;
    } else if (PARAM_target.mode == 2) {
        data = static_cast<const uint8_t*>(PARAM_target.data);
        length = PARAM_target.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    #if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_start > length || PARAM_count > length - PARAM_start) {
        franko_panic("simd: target range out of bounds");
    }
    #endif
    if (PARAM_count == 0) {
        return 0;
    }
    data += PARAM_start;
    const __m128i needle = _mm_set1_epi8(static_cast<char>(PARAM_value));
    uint64_t mask = 0;
    uint32_t i = 0;
    for (; i + 16 <= PARAM_count; i += 16) {
        const __m128i bytes = _mm_loadu_si128(reinterpret_cast<const __m128i*>(data + i));
        const __m128i result = _mm_cmplt_epi8(bytes, needle);
        mask |= static_cast<uint64_t>(static_cast<uint32_t>(_mm_movemask_epi8(result))) << i;
    }
    for (; i < PARAM_count; ++i) {
        if (static_cast<int8_t>(data[i]) < static_cast<int8_t>(PARAM_value)) {
            mask |= static_cast<uint64_t>(1) << i;
        }
    }
    return mask;
}
}
}
// @@end

// @@simd::x86::cmpestrm_mask
namespace NS_simd {
namespace NS_x86 {
FRANKO_INLINE uint64_t FUNC_cmpestrm_mask_3cc67f9b(Franko_Fat_Pointer<uint8_t> PARAM_target, Franko_Fat_Pointer<uint8_t> PARAM_charset, uint32_t PARAM_start, uint32_t PARAM_count)
{
    if (PARAM_count > 64) {
        franko_panic("simd: mask count must be at most 64");
    }
    const uint8_t* data = nullptr;
    uint32_t length = 0;
    if (PARAM_target.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_target.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        data = array->data;
        length = array->count;
    } else if (PARAM_target.mode == 2) {
        data = static_cast<const uint8_t*>(PARAM_target.data);
        length = PARAM_target.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    #if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_start > length || PARAM_count > length - PARAM_start) {
        franko_panic("simd: target range out of bounds");
    }
    #endif
    const uint8_t* charset = nullptr;
    uint32_t charset_length = 0;
    if (PARAM_charset.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_charset.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        charset = array->data;
        charset_length = array->count;
    } else if (PARAM_charset.mode == 2) {
        charset = static_cast<const uint8_t*>(PARAM_charset.data);
        charset_length = PARAM_charset.count;
    }
    if (PARAM_count == 0) {
        return 0;
    }
    data += PARAM_start;
    uint64_t mask = 0;
    uint32_t i = 0;
#if defined(__SSE4_2__)
    if (charset_length <= 16) {
        alignas(16) uint8_t padded[16] = {};
        for (uint32_t j = 0; j < charset_length; ++j) {
            padded[j] = charset[j];
        }
        const __m128i chars = _mm_loadu_si128(reinterpret_cast<const __m128i*>(padded));
        for (; i + 16 <= PARAM_count; i += 16) {
            const __m128i bytes = _mm_loadu_si128(reinterpret_cast<const __m128i*>(data + i));
            const __m128i result = _mm_cmpestrm(chars, static_cast<int>(charset_length), bytes, 16, _SIDD_UBYTE_OPS | _SIDD_CMP_EQUAL_ANY | _SIDD_BIT_MASK);
            mask |= static_cast<uint64_t>(static_cast<uint32_t>(_mm_cvtsi128_si32(result)) & 0xFFFFu) << i;
        }
    }
#endif
    for (; i < PARAM_count; ++i) {
        bool found = false;
        for (uint32_t j = 0; j < charset_length; ++j) {
            if (charset[j] == data[i]) {
                found = true;
                break;
            }
        }
        if (found) {
            mask |= static_cast<uint64_t>(1) << i;
        }
    }
    return mask;
}
}
}
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

// @@simd::arm::vceqq_u8_mask
namespace NS_simd {
namespace NS_arm {
FRANKO_INLINE uint64_t FUNC_vceqq_u8_mask_97c839ba(Franko_Fat_Pointer<uint8_t> PARAM_target, uint8_t PARAM_value, uint32_t PARAM_start, uint32_t PARAM_count)
{
    if (PARAM_count > 64) {
        franko_panic("simd: mask count must be at most 64");
    }
    const uint8_t* data = nullptr;
    uint32_t length = 0;
    if (PARAM_target.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_target.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        data = array->data;
        length = array->count;
    } else if (PARAM_target.mode == 2) {
        data = static_cast<const uint8_t*>(PARAM_target.data);
        length = PARAM_target.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    #if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_start > length || PARAM_count > length - PARAM_start) {
        franko_panic("simd: target range out of bounds");
    }
    #endif
    if (PARAM_count == 0) {
        return 0;
    }
    data += PARAM_start;
    const uint8x16_t needle = vdupq_n_u8(PARAM_value);
    uint64_t mask = 0;
    uint32_t i = 0;
    for (; i + 16 <= PARAM_count; i += 16) {
        const uint8x16_t bytes = vld1q_u8(data + i);
        const uint8x16_t result = vceqq_u8(bytes, needle);
        uint8_t lanes[16];
        vst1q_u8(lanes, result);
        for (uint32_t j = 0; j < 16; ++j) {
            if (lanes[j] != 0) {
                mask |= static_cast<uint64_t>(1) << (i + j);
            }
        }
    }
    for (; i < PARAM_count; ++i) {
        if (data[i] == PARAM_value) {
            mask |= static_cast<uint64_t>(1) << i;
        }
    }
    return mask;
}
}
}
// @@end

// @@simd::arm::vcgtq_u8_mask
namespace NS_simd {
namespace NS_arm {
FRANKO_INLINE uint64_t FUNC_vcgtq_u8_mask_97c839ba(Franko_Fat_Pointer<uint8_t> PARAM_target, uint8_t PARAM_value, uint32_t PARAM_start, uint32_t PARAM_count)
{
    if (PARAM_count > 64) {
        franko_panic("simd: mask count must be at most 64");
    }
    const uint8_t* data = nullptr;
    uint32_t length = 0;
    if (PARAM_target.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_target.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        data = array->data;
        length = array->count;
    } else if (PARAM_target.mode == 2) {
        data = static_cast<const uint8_t*>(PARAM_target.data);
        length = PARAM_target.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    #if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_start > length || PARAM_count > length - PARAM_start) {
        franko_panic("simd: target range out of bounds");
    }
    #endif
    if (PARAM_count == 0) {
        return 0;
    }
    data += PARAM_start;
    const uint8x16_t needle = vdupq_n_u8(PARAM_value);
    uint64_t mask = 0;
    uint32_t i = 0;
    for (; i + 16 <= PARAM_count; i += 16) {
        const uint8x16_t bytes = vld1q_u8(data + i);
        const uint8x16_t result = vcgtq_u8(bytes, needle);
        uint8_t lanes[16];
        vst1q_u8(lanes, result);
        for (uint32_t j = 0; j < 16; ++j) {
            if (lanes[j] != 0) {
                mask |= static_cast<uint64_t>(1) << (i + j);
            }
        }
    }
    for (; i < PARAM_count; ++i) {
        if (data[i] > PARAM_value) {
            mask |= static_cast<uint64_t>(1) << i;
        }
    }
    return mask;
}
}
}
// @@end

// @@simd::arm::vcltq_u8_mask
namespace NS_simd {
namespace NS_arm {
FRANKO_INLINE uint64_t FUNC_vcltq_u8_mask_97c839ba(Franko_Fat_Pointer<uint8_t> PARAM_target, uint8_t PARAM_value, uint32_t PARAM_start, uint32_t PARAM_count)
{
    if (PARAM_count > 64) {
        franko_panic("simd: mask count must be at most 64");
    }
    const uint8_t* data = nullptr;
    uint32_t length = 0;
    if (PARAM_target.mode == 3) {
        Franko_Dynamic_Array<uint8_t>* array = static_cast<Franko_Dynamic_Array<uint8_t>*>(PARAM_target.data);
        if (array == nullptr || (array->data == nullptr && array->count != 0)) {
            franko_panic("simd: uninitialized dynamic array");
        }
        data = array->data;
        length = array->count;
    } else if (PARAM_target.mode == 2) {
        data = static_cast<const uint8_t*>(PARAM_target.data);
        length = PARAM_target.count;
    } else if (PARAM_count != 0) {
        franko_panic("simd: null target");
    }
    #if !FRANKO_BOUNDS_UNCHECKED
    if (PARAM_start > length || PARAM_count > length - PARAM_start) {
        franko_panic("simd: target range out of bounds");
    }
    #endif
    if (PARAM_count == 0) {
        return 0;
    }
    data += PARAM_start;
    const uint8x16_t needle = vdupq_n_u8(PARAM_value);
    uint64_t mask = 0;
    uint32_t i = 0;
    for (; i + 16 <= PARAM_count; i += 16) {
        const uint8x16_t bytes = vld1q_u8(data + i);
        const uint8x16_t result = vcltq_u8(bytes, needle);
        uint8_t lanes[16];
        vst1q_u8(lanes, result);
        for (uint32_t j = 0; j < 16; ++j) {
            if (lanes[j] != 0) {
                mask |= static_cast<uint64_t>(1) << (i + j);
            }
        }
    }
    for (; i < PARAM_count; ++i) {
        if (data[i] < PARAM_value) {
            mask |= static_cast<uint64_t>(1) << i;
        }
    }
    return mask;
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
