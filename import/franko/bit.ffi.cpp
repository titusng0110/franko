// @@common
#include "FrankoRuntime.hpp"
#if defined(_MSC_VER)
#include <intrin.h>
#endif
// @@end

// @@bit::ctz32
namespace NS_bit {
FRANKO_INLINE uint32_t FUNC_ctz32_88223deb(uint32_t PARAM_value)
{
    if (PARAM_value == 0) {
        return 32;
    }
#if defined(_MSC_VER)
    unsigned long index;
    _BitScanForward(&index, PARAM_value);
    return static_cast<uint32_t>(index);
#else
    return static_cast<uint32_t>(__builtin_ctz(PARAM_value));
#endif
}
}
// @@end

// @@bit::ctz64
namespace NS_bit {
FRANKO_INLINE uint32_t FUNC_ctz64_72968362(uint64_t PARAM_value)
{
    if (PARAM_value == 0) {
        return 64;
    }
#if defined(_MSC_VER)
    unsigned long index;
    _BitScanForward64(&index, PARAM_value);
    return static_cast<uint32_t>(index);
#else
    return static_cast<uint32_t>(__builtin_ctzll(PARAM_value));
#endif
}
}
// @@end

// @@bit::clz32
namespace NS_bit {
FRANKO_INLINE uint32_t FUNC_clz32_88223deb(uint32_t PARAM_value)
{
    if (PARAM_value == 0) {
        return 32;
    }
#if defined(_MSC_VER)
    unsigned long index;
    _BitScanReverse(&index, PARAM_value);
    return 31u - static_cast<uint32_t>(index);
#else
    return static_cast<uint32_t>(__builtin_clz(PARAM_value));
#endif
}
}
// @@end

// @@bit::clz64
namespace NS_bit {
FRANKO_INLINE uint32_t FUNC_clz64_72968362(uint64_t PARAM_value)
{
    if (PARAM_value == 0) {
        return 64;
    }
#if defined(_MSC_VER)
    unsigned long index;
    _BitScanReverse64(&index, PARAM_value);
    return 63u - static_cast<uint32_t>(index);
#else
    return static_cast<uint32_t>(__builtin_clzll(PARAM_value));
#endif
}
}
// @@end

// @@bit::popcount32
namespace NS_bit {
FRANKO_INLINE uint32_t FUNC_popcount32_88223deb(uint32_t PARAM_value)
{
#if defined(_MSC_VER)
#if defined(_M_ARM64)
    uint32_t count = 0;
    while (PARAM_value != 0) {
        PARAM_value &= PARAM_value - 1;
        ++count;
    }
    return count;
#else
    return static_cast<uint32_t>(__popcnt(PARAM_value));
#endif
#else
    return static_cast<uint32_t>(__builtin_popcount(PARAM_value));
#endif
}
}
// @@end

// @@bit::popcount64
namespace NS_bit {
FRANKO_INLINE uint32_t FUNC_popcount64_72968362(uint64_t PARAM_value)
{
#if defined(_MSC_VER)
#if defined(_M_ARM64)
    uint32_t count = 0;
    while (PARAM_value != 0) {
        PARAM_value &= PARAM_value - 1;
        ++count;
    }
    return count;
#else
    return static_cast<uint32_t>(__popcnt64(PARAM_value));
#endif
#else
    return static_cast<uint32_t>(__builtin_popcountll(PARAM_value));
#endif
}
}
// @@end

// @@bit::bswap32
namespace NS_bit {
FRANKO_INLINE uint32_t FUNC_bswap32_88223deb(uint32_t PARAM_value)
{
#if defined(_MSC_VER)
    return static_cast<uint32_t>(_byteswap_ulong(PARAM_value));
#else
    return __builtin_bswap32(PARAM_value);
#endif
}
}
// @@end

// @@bit::bswap64
namespace NS_bit {
FRANKO_INLINE uint64_t FUNC_bswap64_728e4a0d(uint64_t PARAM_value)
{
#if defined(_MSC_VER)
    return static_cast<uint64_t>(_byteswap_uint64(PARAM_value));
#else
    return __builtin_bswap64(PARAM_value);
#endif
}
}
// @@end
