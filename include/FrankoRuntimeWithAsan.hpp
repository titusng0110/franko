#pragma once

#include <algorithm> // for std::search (portable memmem fallback)
#include <cstddef>   // for size_t
#include <cstdint>   // for uint8_t, uint32_t, int32_t
#include <cstdlib>   // for std::malloc, std::calloc, std::realloc, std::free
                    // (ASan build: system allocator so sanitizer instrumentation
                    // applies) and _byteswap_* (MSVC byte_swap fallback)
#include <cstring>   // for std::memcpy, std::memset, std::memmove
#include <limits>    // for std::numeric_limits (franko_unsafe_cast bounds)
#include <new>       // for placement new
#include <stdexcept> // for std::runtime_error
#include <string>    // for std::string (franko_byte_arrays::fail message assembly)
#include <type_traits> // for std::is_integral, std::is_signed, std::make_unsigned

[[noreturn]] inline void franko_panic(const char* message) {
    throw std::runtime_error(message);
}

// ============================================================
// franko_unsafe_cast<To>(v): runtime-checked integer conversion
// ============================================================
//
// Emitted for Franko unsafe_cast<T>(expr) conversions, which may lose
// data (narrowing, signedness changes, ...). Value-preserving widening
// conversions use plain cast<T>(expr) and compile to a static_cast
// instead, so they never reach this.
//
// Throws via franko_panic when v lies outside To's range; otherwise
// returns static_cast<To>(v).
//
// The range test dispatches on the signedness combination so no
// branch ever compares signed and unsigned values (warning-clean
// under -Wall -Wextra -Wpedantic -Wshadow).
template <typename To, typename From, bool FromSigned = std::is_signed<From>::value, bool ToSigned = std::is_signed<To>::value>
struct franko_unsafe_cast_range;

// Signed source, signed target: direct comparison promotes safely.
template <typename To, typename From>
struct franko_unsafe_cast_range<To, From, true, true> {
    static bool below_min(From v) { return v < std::numeric_limits<To>::min(); }
    static bool above_max(From v) { return v > std::numeric_limits<To>::max(); }
};

// Unsigned source, unsigned target: v >= 0 == min(To), so only the
// upper bound can fail. Both sides unsigned: no sign warnings.
template <typename To, typename From>
struct franko_unsafe_cast_range<To, From, false, false> {
    static bool below_min(From) { return false; }
    static bool above_max(From v) { return v > std::numeric_limits<To>::max(); }
};

// Signed source, unsigned target: negatives always fail; the upper
// bound compares non-negative values as unsigned.
template <typename To, typename From>
struct franko_unsafe_cast_range<To, From, true, false> {
    static bool below_min(From v) { return v < 0; }
    static bool above_max(From v) {
        if (v < 0) {
            return false;
        }

        typedef typename std::make_unsigned<From>::type unsigned_from;
        return static_cast<unsigned_from>(v) > std::numeric_limits<To>::max();
    }
};

// Unsigned source, signed target: v >= 0 can never fall below
// min(To); the upper bound compares as unsigned against max(To).
template <typename To, typename From>
struct franko_unsafe_cast_range<To, From, false, true> {
    static bool below_min(From) { return false; }
    static bool above_max(From v) {
        typedef typename std::make_unsigned<To>::type unsigned_to;
        return v > static_cast<unsigned_to>(std::numeric_limits<To>::max());
    }
};

template <typename To, typename From>
To franko_unsafe_cast(From v) {
    static_assert(std::is_integral<To>::value, "franko_unsafe_cast requires an integral target type");
    static_assert(std::is_integral<From>::value, "franko_unsafe_cast requires an integral source type");
    static_assert(!std::is_same<To, bool>::value, "franko_unsafe_cast does not support bool");
    static_assert(!std::is_same<From, bool>::value, "franko_unsafe_cast does not support bool");

    if (franko_unsafe_cast_range<To, From>::below_min(v)
            || franko_unsafe_cast_range<To, From>::above_max(v)) {
        franko_panic("franko_unsafe_cast: value out of range for target type");
    }

    return static_cast<To>(v);
}

// ============================================================
// Byte-array and bit intrinsics (shared kernels)
// ============================================================
//
// These implement the Franko byte-array intrinsics: memchr / memmem,
// the read_*_{LE,BE} / write_*_{LE,BE} endian accessors,
// read_bit / write_bit / flip_bit, the bitwise_*_mask and
// shift_bits_* mutators, and count_set_bits. memcmp is not here: it
// lives inside the array structs next to memset / memcpy / memmove.
//
// Design:
//
// * The kernels are free templates over (pointer, length) pairs, so
//   Franko_Static_Array and Franko_Dynamic_Array share one
//   implementation and each array member stays a thin delegate that
//   supplies its class tag (for panic messages) and its length.
// * The members on the two array types are SFINAE-restricted with
//   std::enable_if: every intrinsic here exists only when the element
//   type is uint8_t (Franko's byte). This mirrors the Franko-level
//   restriction the compiler enforces; the C++ layer is the backstop.
// * All kernels are portable C++14 written so GCC/Clang -O3 can
//   auto-vectorize them. Hand-written SIMD paths can replace the
//   bodies later without changing any signature.
//
// Bit numbering (read_bit / write_bit / flip_bit and the bit-string
// shifts): bit i lives in byte i / 8 at bit position i % 8 counted
// from the LSB. A bit index must satisfy bitIndex < 8 * length.
//
// Bounds violations panic via franko_byte_arrays::fail with the existing
// panic message style ("<class> <intrinsic> <problem>").
// ============================================================

namespace franko_byte_arrays {

// Assembles "<class> <intrinsic> <problem>" and panics. Only runs on
// the failure path, so the std::string is cold-path only.
inline void fail(const char* cls, const char* op, const char* what) {
    std::string message(cls);
    message += ' ';
    message += op;
    message += ' ';
    message += what;
    franko_panic(message.c_str());
}

// Returned by memchr / memmem when there is no match. Every real
// array holds at most 0xFFFFFFFE elements at a matching index, so
// this is unambiguous.
constexpr uint32_t MEM_NOT_FOUND = 0xFFFFFFFFu;

// ------------------------------------------------------------
// memchr / memmem: search a byte range of the receiver. Both return
// the absolute element index of the first match, or MEM_NOT_FOUND.
// An empty needle matches at the range start.
// ------------------------------------------------------------

inline uint32_t memchr_impl(const char* cls, const char* op, const uint8_t* data, uint32_t count, uint32_t start, uint32_t range, uint8_t value) {
    if (data == nullptr)
        fail(cls, op, "on uninitialized array");

    if (start > count)
        fail(cls, op, "start out of bounds");

    if (range > count - start)
        fail(cls, op, "range out of bounds");

    if (range == 0) {
        return MEM_NOT_FOUND;
    }

    const void* hit = std::memchr(data + start, value, static_cast<size_t>(range));

    if (hit == nullptr) {
        return MEM_NOT_FOUND;
    }

    return static_cast<uint32_t>(static_cast<const uint8_t*>(hit) - data);
}

// memmem is a GNU extension (also in POSIX-2008 and on macOS/BSD),
// present in glibc whether linked statically or dynamically.
// Toolchains without it (MSVC) fall back to std::search below.
// Defined to 0 externally to test the fallback path.
#ifndef FRANKO_HAS_MEMMEM
#if defined(__GLIBC__) || defined(__APPLE__) || defined(__FreeBSD__) \
        || defined(__OpenBSD__) || defined(__NetBSD__)
#define FRANKO_HAS_MEMMEM 1
#else
#define FRANKO_HAS_MEMMEM 0
#endif
#endif

inline uint32_t memmem_impl(const char* cls, const char* op, const uint8_t* data, uint32_t count, uint32_t start, uint32_t range, const uint8_t* needle, uint32_t needleCount) {
    if (data == nullptr)
        fail(cls, op, "on uninitialized array");

    if (needle == nullptr)
        fail(cls, op, "from uninitialized source array");

    if (start > count)
        fail(cls, op, "start out of bounds");

    if (range > count - start)
        fail(cls, op, "range out of bounds");

    if (needleCount == 0) {
        return start;
    }

    if (needleCount > range) {
        return MEM_NOT_FOUND;
    }

#if FRANKO_HAS_MEMMEM
    const void* hit = ::memmem(data + start, static_cast<size_t>(range), needle, static_cast<size_t>(needleCount));

    if (hit == nullptr) {
        return MEM_NOT_FOUND;
    }

    return static_cast<uint32_t>(static_cast<const uint8_t*>(hit) - data);
#else
    // Portable fallback (MSVC and friends): std::search is standard
    // C++ and, unlike strstr, is binary-safe (no NUL termination is
    // assumed, embedded NUL bytes are ordinary data).
    const uint8_t* begin = data + start;
    const uint8_t* end = begin + range;
    const uint8_t* hit = std::search(begin, end, needle, needle + needleCount);

    if (hit == end) {
        return MEM_NOT_FOUND;
    }

    return static_cast<uint32_t>(hit - data);
#endif
}

// ------------------------------------------------------------
// Endian accessors: read_*_{LE,BE} assemble sizeof(R) bytes at a
// byte offset into R; write_*_{LE,BE} store R's bytes. Signed
// results are reconstructed two's-complement, so no implementation
// defined unsigned-to-signed conversion ever happens.
//
// Each accessor is one native-width load/store (std::memcpy keeps it
// aliasing-safe and unaligned-safe; compilers fold it to a single
// instruction) plus a single byte swap when host and requested byte
// order differ (__builtin_bswap* on GCC/Clang, _byteswap_* on MSVC).
// ------------------------------------------------------------

// All supported targets are little-endian in practice; the macro
// below also covers big-endian hosts via the GCC/Clang predefined.
#if defined(__BYTE_ORDER__) && defined(__ORDER_BIG_ENDIAN__) && __BYTE_ORDER__ == __ORDER_BIG_ENDIAN__
constexpr bool HOST_LITTLE_ENDIAN = false;
#else
constexpr bool HOST_LITTLE_ENDIAN = true;
#endif

inline uint16_t byte_swap16(uint16_t v) {
#if defined(_MSC_VER)
    return _byteswap_ushort(v);
#elif defined(__GNUC__) || defined(__clang__)
    return __builtin_bswap16(v);
#else
    return static_cast<uint16_t>((v >> 8) | (v << 8));
#endif
}

inline uint32_t byte_swap32(uint32_t v) {
#if defined(_MSC_VER)
    return _byteswap_ulong(v);
#elif defined(__GNUC__) || defined(__clang__)
    return __builtin_bswap32(v);
#else
    return (v >> 24) | ((v >> 8) & 0x0000FF00u) | ((v << 8) & 0x00FF0000u) | (v << 24);
#endif
}

inline uint64_t byte_swap64(uint64_t v) {
#if defined(_MSC_VER)
    return _byteswap_uint64(v);
#elif defined(__GNUC__) || defined(__clang__)
    return __builtin_bswap64(v);
#else
    return (static_cast<uint64_t>(byte_swap32(static_cast<uint32_t>(v))) << 32) | byte_swap32(static_cast<uint32_t>(v >> 32));
#endif
}

template <typename U>
U byte_swap(U v) {
    if (sizeof(U) == 2) return static_cast<U>(byte_swap16(static_cast<uint16_t>(v)));
    if (sizeof(U) == 4) return static_cast<U>(byte_swap32(static_cast<uint32_t>(v)));
    return static_cast<U>(byte_swap64(static_cast<uint64_t>(v)));
}

template <typename R>
typename std::enable_if<std::is_signed<R>::value, R>::type bits_to_int(uint64_t bits) {
    typedef typename std::make_unsigned<R>::type U;

    const U u = static_cast<U>(bits);
    const U signBit = static_cast<U>(1) << (sizeof(U) * 8 - 1);

    if (u < signBit) {
        return static_cast<R>(u);
    }

    // Two's-complement reconstruction: -1 - ~u is exact for every
    // negative bit pattern and every intermediate value fits R.
    return static_cast<R>(-1) - static_cast<R>(static_cast<U>(~u));
}

template <typename R>
typename std::enable_if<!std::is_signed<R>::value, R>::type bits_to_int(uint64_t bits) {
    return static_cast<R>(bits);
}

template <typename R, bool LittleEndian>
R read_int_impl(const char* cls, const char* op, const uint8_t* data, uint32_t count, uint32_t offset) {
    const uint32_t width = static_cast<uint32_t>(sizeof(R));

    if (data == nullptr)
        fail(cls, op, "on uninitialized array");

    if (offset > count || width > count - offset)
        fail(cls, op, "offset out of bounds");

    typedef typename std::make_unsigned<R>::type U;

    U bits;
    std::memcpy(&bits, data + offset, sizeof(U));

    if (LittleEndian != HOST_LITTLE_ENDIAN) {
        bits = byte_swap(bits);
    }

    return bits_to_int<R>(bits);
}

template <typename R, bool LittleEndian>
void write_int_impl(const char* cls, const char* op, uint8_t* data, uint32_t count, uint32_t offset, R value) {
    const uint32_t width = static_cast<uint32_t>(sizeof(R));

    if (data == nullptr)
        fail(cls, op, "on uninitialized array");

    if (offset > count || width > count - offset)
        fail(cls, op, "offset out of bounds");

    typedef typename std::make_unsigned<R>::type U;

    // Signed-to-unsigned conversion is well defined (modulo wrap) and
    // yields exactly R's bit pattern.
    U bits = static_cast<U>(value);

    if (LittleEndian != HOST_LITTLE_ENDIAN) {
        bits = byte_swap(bits);
    }

    std::memcpy(data + offset, &bits, sizeof(U));
}

// ------------------------------------------------------------
// Single-bit accessors. Bit i lives in byte i / 8 at bit position
// i % 8 counted from the LSB; valid indices are [0, 8 * count).
// ------------------------------------------------------------

inline void check_bit_index(const char* cls, const char* op, const void* data, uint32_t count, uint64_t bitIndex) {
    if (data == nullptr)
        fail(cls, op, "on uninitialized array");

    if (bitIndex >= static_cast<uint64_t>(count) * 8)
        fail(cls, op, "bit index out of bounds");
}

inline uint8_t read_bit_impl(const char* cls, const char* op, const uint8_t* data, uint32_t count, uint64_t bitIndex) {
    check_bit_index(cls, op, data, count, bitIndex);

    return static_cast<uint8_t>((data[bitIndex >> 3] >> (bitIndex & 7)) & 1u);
}

inline void write_bit_impl(const char* cls, const char* op, uint8_t* data, uint32_t count, uint64_t bitIndex, uint8_t value) {
    check_bit_index(cls, op, data, count, bitIndex);

    const uint8_t mask = static_cast<uint8_t>(1u << (bitIndex & 7));

    if ((value & 1u) != 0) {
        data[bitIndex >> 3] |= mask;
    } else {
        data[bitIndex >> 3] &= static_cast<uint8_t>(~mask);
    }
}

inline void flip_bit_impl(const char* cls, const char* op, uint8_t* data, uint32_t count, uint64_t bitIndex) {
    check_bit_index(cls, op, data, count, bitIndex);

    data[bitIndex >> 3] ^= static_cast<uint8_t>(1u << (bitIndex & 7));
}

// ------------------------------------------------------------
// Whole-array bitwise mask mutators: a[i] = ~a[i] / a[i] ^= mask /
// a[i] &= mask / a[i] |= mask.
// ------------------------------------------------------------

inline void bitwise_not_impl(const char* cls, const char* op, uint8_t* data, uint32_t count) {
    if (data == nullptr)
        fail(cls, op, "on uninitialized array");

    for (uint32_t i = 0; i < count; ++i) {
        data[i] = static_cast<uint8_t>(~data[i]);
    }
}

inline void bitwise_xor_impl(const char* cls, const char* op, uint8_t* data, uint32_t count, uint8_t mask) {
    if (data == nullptr)
        fail(cls, op, "on uninitialized array");

    for (uint32_t i = 0; i < count; ++i) {
        data[i] ^= mask;
    }
}

inline void bitwise_and_impl(const char* cls, const char* op, uint8_t* data, uint32_t count, uint8_t mask) {
    if (data == nullptr)
        fail(cls, op, "on uninitialized array");

    for (uint32_t i = 0; i < count; ++i) {
        data[i] &= mask;
    }
}

inline void bitwise_or_impl(const char* cls, const char* op, uint8_t* data, uint32_t count, uint8_t mask) {
    if (data == nullptr)
        fail(cls, op, "on uninitialized array");

    for (uint32_t i = 0; i < count; ++i) {
        data[i] |= mask;
    }
}

// ------------------------------------------------------------
// Whole-array bit-string shifts, in place. The array is one bit
// string in LSB-first order within each byte (bit i is bit i % 8 of
// byte i / 8). Left: new bit i takes old bit i - bitCount (0 when
// negative). Right: new bit i takes old bit i + bitCount (0 past the
// end). Shifting by >= 8 * count bits zeroes the array; that is
// defined behavior, not an error.
// ------------------------------------------------------------

inline void shift_bits_left_impl(const char* cls, const char* op, uint8_t* data, uint32_t count, uint64_t bitCount) {
    if (data == nullptr)
        fail(cls, op, "on uninitialized array");

    const uint64_t totalBits = static_cast<uint64_t>(count) * 8;

    if (bitCount >= totalBits) {
        if (count > 0) {
            std::memset(data, 0, static_cast<size_t>(count));
        }

        return;
    }

    const uint32_t byteShift = static_cast<uint32_t>(bitCount >> 3);
    const uint32_t bitShift = static_cast<uint32_t>(bitCount & 7);

    // Whole-byte part: plain memmove, zero the vacated prefix.
    if (byteShift > 0) {
        std::memmove(data + byteShift, data, static_cast<size_t>(count - byteShift));
        std::memset(data, 0, static_cast<size_t>(byteShift));
    }

    if (bitShift == 0) {
        return;
    }

    // Sub-byte part over the byte-shifted buffer: new bit i takes old
    // bit i - bitShift. Run 64 bits at a time over the contiguous block
    // (unaligned-safe word loads), descending so every word still holds
    // old data: new_word = (word << bitShift) | (below >> (64 - bitShift)).
    const uint32_t words = count / 8;

    // Partial top bytes first (they read into the last full word).
    for (uint32_t i = count; i-- > words * 8; ) {
        uint32_t v = static_cast<uint32_t>(data[i]) << bitShift;

        if (i > 0) {
            v |= static_cast<uint32_t>(data[i - 1]) >> (8 - bitShift);
        }

        data[i] = static_cast<uint8_t>(v);
    }

    for (uint32_t p = words; p-- > 0; ) {
        uint64_t word;
        uint64_t below;

        std::memcpy(&word, data + p * 8, 8);

        if (p > 0) {
            std::memcpy(&below, data + (p - 1) * 8, 8);
        } else {
            below = 0;
        }

        const uint64_t v = (word << bitShift) | (below >> (64 - bitShift));
        std::memcpy(data + p * 8, &v, 8);
    }
}

inline void shift_bits_right_impl(const char* cls, const char* op, uint8_t* data, uint32_t count, uint64_t bitCount) {
    if (data == nullptr)
        fail(cls, op, "on uninitialized array");

    const uint64_t totalBits = static_cast<uint64_t>(count) * 8;

    if (bitCount >= totalBits) {
        if (count > 0) {
            std::memset(data, 0, static_cast<size_t>(count));
        }

        return;
    }

    const uint32_t byteShift = static_cast<uint32_t>(bitCount >> 3);
    const uint32_t bitShift = static_cast<uint32_t>(bitCount & 7);

    // Whole-byte part: plain memmove, zero the vacated suffix.
    if (byteShift > 0) {
        std::memmove(data, data + byteShift, static_cast<size_t>(count - byteShift));
        std::memset(data + (count - byteShift), 0, static_cast<size_t>(byteShift));
    }

    if (bitShift == 0) {
        return;
    }

    // Sub-byte part over the byte-shifted buffer: new bit i takes old
    // bit i + bitShift. Run 64 bits at a time over the contiguous block
    // (unaligned-safe word loads), ascending so every word still holds
    // old data: new_word = (word >> bitShift) | (above << (64 - bitShift)).
    const uint32_t rem = count % 8;
    const uint32_t words = count / 8;

    // Partial low bytes first (they read into the lowest full word).
    for (uint32_t i = 0; i < rem; ++i) {
        uint32_t v = static_cast<uint32_t>(data[i]) >> bitShift;

        if (i + 1 < count) {
            v |= static_cast<uint32_t>(data[i + 1]) << (8 - bitShift);
        }

        data[i] = static_cast<uint8_t>(v);
    }

    for (uint32_t j = 0; j < words; ++j) {
        const uint32_t pos = rem + 8 * j;
        uint64_t word;
        uint64_t above;

        std::memcpy(&word, data + pos, 8);

        if (j + 1 < words) {
            std::memcpy(&above, data + pos + 8, 8);
        } else {
            above = 0;
        }

        const uint64_t v = (word >> bitShift) | (above << (64 - bitShift));
        std::memcpy(data + pos, &v, 8);
    }
}

inline uint64_t count_set_bits_impl(const char* cls, const char* op, const uint8_t* data, uint32_t count) {
    if (data == nullptr)
        fail(cls, op, "on uninitialized array");

    uint64_t total = 0;

    for (uint32_t i = 0; i < count; ++i) {
        // Classic SWAR popcount of one byte, branch-free and
        // auto-vectorizable.
        uint32_t v = data[i];
        v = v - ((v >> 1) & 0x55u);
        v = (v & 0x33u) + ((v >> 2) & 0x33u);
        v = (v + (v >> 4)) & 0x0Fu;

        total += v;
    }

    return total;
}

} // namespace franko_byte_arrays

template <typename T, uint32_t N>
struct Franko_Static_Array;

template <typename T>
struct Franko_Dynamic_Array;

// ============================================================
// Franko_Static_Array<T, N>
// ============================================================

template <typename T, uint32_t N>
struct Franko_Static_Array {
    T data[N];

    const T& operator[](uint32_t index) const {
        if (index >= N)
            franko_panic("Franko_Static_Array index out of bounds");

        return data[index];
    }

    T& operator[](uint32_t index) {
        if (index >= N)
            franko_panic("Franko_Static_Array index out of bounds");

        return data[index];
    }

    void memset(uint8_t byteValue) {
        if (N > 0) {
            std::memset(data, byteValue, sizeof(T) * static_cast<size_t>(N));
        }
    }

    void memset(uint8_t byteValue, uint32_t start, uint32_t elementCount) {
        if (start > N)
            franko_panic("Franko_Static_Array memset start out of bounds");

        if (elementCount > N - start)
            franko_panic("Franko_Static_Array memset range out of bounds");

        if (elementCount > 0) {
            std::memset(data + start, byteValue, sizeof(T) * static_cast<size_t>(elementCount));
        }
    }

    template <uint32_t M>
    void memcpy(const Franko_Static_Array<T, M>* other) {
        if (other == nullptr)
            franko_panic("Franko_Static_Array memcpy source pointer is null");

        const uint32_t count = (N < M) ? N : M;

        if (count > 0) {
            std::memcpy(data, other->data, sizeof(T) * static_cast<size_t>(count));
        }
    }

    void memcpy(const Franko_Dynamic_Array<T>* other) {
        if (other == nullptr)
            franko_panic("Franko_Static_Array memcpy source pointer is null");

        if (other->data == nullptr)
            franko_panic("Franko_Static_Array memcpy from uninitialized dynamic array");

        const uint32_t count = (N < other->count) ? N : other->count;

        if (count > 0) {
            std::memcpy(data, other->data, sizeof(T) * static_cast<size_t>(count));
        }
    }

    template <uint32_t M>
    void memcpy(const Franko_Static_Array<T, M>* other, uint32_t dstStart, uint32_t srcStart, uint32_t elementCount) {
        if (other == nullptr)
            franko_panic("Franko_Static_Array memcpy source pointer is null");

        if (dstStart > N)
            franko_panic("Franko_Static_Array memcpy destination start out of bounds");

        if (srcStart > M)
            franko_panic("Franko_Static_Array memcpy source start out of bounds");

        if (elementCount > N - dstStart)
            franko_panic("Franko_Static_Array memcpy destination range out of bounds");

        if (elementCount > M - srcStart)
            franko_panic("Franko_Static_Array memcpy source range out of bounds");

        if (elementCount > 0) {
            std::memcpy(data + dstStart, other->data + srcStart, sizeof(T) * static_cast<size_t>(elementCount));
        }
    }

    void memcpy(const Franko_Dynamic_Array<T>* other, uint32_t dstStart, uint32_t srcStart, uint32_t elementCount) {
        if (other == nullptr)
            franko_panic("Franko_Static_Array memcpy source pointer is null");

        if (other->data == nullptr)
            franko_panic("Franko_Static_Array memcpy from uninitialized dynamic array");

        if (dstStart > N)
            franko_panic("Franko_Static_Array memcpy destination start out of bounds");

        if (srcStart > other->count)
            franko_panic("Franko_Static_Array memcpy source start out of bounds");

        if (elementCount > N - dstStart)
            franko_panic("Franko_Static_Array memcpy destination range out of bounds");

        if (elementCount > other->count - srcStart)
            franko_panic("Franko_Static_Array memcpy source range out of bounds");

        if (elementCount > 0) {
            std::memcpy(data + dstStart, other->data + srcStart, sizeof(T) * static_cast<size_t>(elementCount));
        }
    }

    void memmove(uint32_t dstStart, uint32_t srcStart, uint32_t elementCount) {
        if (dstStart > N)
            franko_panic("Franko_Static_Array memmove destination start out of bounds");

        if (srcStart > N)
            franko_panic("Franko_Static_Array memmove source start out of bounds");

        if (elementCount > N - dstStart)
            franko_panic("Franko_Static_Array memmove destination range out of bounds");

        if (elementCount > N - srcStart)
            franko_panic("Franko_Static_Array memmove source range out of bounds");

        if (elementCount > 0) {
            std::memmove(data + dstStart, data + srcStart, sizeof(T) * static_cast<size_t>(elementCount));
        }
    }

    // memcmp: lexicographic raw-byte comparison, C semantics (the sign
    // of the first differing byte; only the sign is meaningful).
    // Restricted to trivially copyable element types (the C++ twin of
    // Franko's memcpyable rule). The whole form is lexicographic across
    // lengths: on an equal shared prefix the shorter array is smaller.
    // The ranged form compares exactly `elementCount` elements.

    template <uint32_t M, typename U = T, typename std::enable_if<std::is_trivially_copyable<U>::value, int>::type = 0>
    int32_t memcmp(const Franko_Static_Array<T, M>* other) const {
        if (other == nullptr)
            franko_panic("Franko_Static_Array memcmp source pointer is null");

        const uint32_t shared = (N < M) ? N : M;

        if (shared > 0) {
            const int result = std::memcmp(data, other->data, sizeof(T) * static_cast<size_t>(shared));

            if (result != 0) {
                return result;
            }
        }

        if (N < M) {
            return -1;
        }

        if (N > M) {
            return 1;
        }

        return 0;
    }

    template <typename U = T, typename std::enable_if<std::is_trivially_copyable<U>::value, int>::type = 0>
    int32_t memcmp(const Franko_Dynamic_Array<T>* other) const {
        if (other == nullptr)
            franko_panic("Franko_Static_Array memcmp source pointer is null");

        if (other->data == nullptr)
            franko_panic("Franko_Static_Array memcmp from uninitialized dynamic array");

        const uint32_t shared = (N < other->count) ? N : other->count;

        if (shared > 0) {
            const int result = std::memcmp(data, other->data, sizeof(T) * static_cast<size_t>(shared));

            if (result != 0) {
                return result;
            }
        }

        if (N < other->count) {
            return -1;
        }

        if (N > other->count) {
            return 1;
        }

        return 0;
    }

    template <uint32_t M, typename U = T, typename std::enable_if<std::is_trivially_copyable<U>::value, int>::type = 0>
    int32_t memcmp(const Franko_Static_Array<T, M>* other, uint32_t dstStart, uint32_t srcStart, uint32_t elementCount) const {
        if (other == nullptr)
            franko_panic("Franko_Static_Array memcmp source pointer is null");

        if (dstStart > N)
            franko_panic("Franko_Static_Array memcmp destination start out of bounds");

        if (srcStart > M)
            franko_panic("Franko_Static_Array memcmp source start out of bounds");

        if (elementCount > N - dstStart)
            franko_panic("Franko_Static_Array memcmp destination range out of bounds");

        if (elementCount > M - srcStart)
            franko_panic("Franko_Static_Array memcmp source range out of bounds");

        if (elementCount > 0) {
            return std::memcmp(data + dstStart, other->data + srcStart, sizeof(T) * static_cast<size_t>(elementCount));
        }

        return 0;
    }

    template <typename U = T, typename std::enable_if<std::is_trivially_copyable<U>::value, int>::type = 0>
    int32_t memcmp(const Franko_Dynamic_Array<T>* other, uint32_t dstStart, uint32_t srcStart, uint32_t elementCount) const {
        if (other == nullptr)
            franko_panic("Franko_Static_Array memcmp source pointer is null");

        if (other->data == nullptr)
            franko_panic("Franko_Static_Array memcmp from uninitialized dynamic array");

        if (dstStart > N)
            franko_panic("Franko_Static_Array memcmp destination start out of bounds");

        if (srcStart > other->count)
            franko_panic("Franko_Static_Array memcmp source start out of bounds");

        if (elementCount > N - dstStart)
            franko_panic("Franko_Static_Array memcmp destination range out of bounds");

        if (elementCount > other->count - srcStart)
            franko_panic("Franko_Static_Array memcmp source range out of bounds");

        if (elementCount > 0) {
            return std::memcmp(data + dstStart, other->data + srcStart, sizeof(T) * static_cast<size_t>(elementCount));
        }

        return 0;
    }

    // ==========================================================
    // Byte-array and bit intrinsics (see franko_byte_arrays above)
    // ==========================================================

    // ---- memchr (byte element types) ----

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    uint32_t memchr(uint8_t value) const {
        return franko_byte_arrays::memchr_impl("Franko_Static_Array", "memchr", data, N, 0, N, value);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    uint32_t memchr(uint8_t value, uint32_t start, uint32_t elementCount) const {
        return franko_byte_arrays::memchr_impl("Franko_Static_Array", "memchr", data, N, start, elementCount, value);
    }

    // ---- memmem (byte element types) ----

    template <uint32_t M, typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    uint32_t memmem(const Franko_Static_Array<T, M>* needle) const {
        if (needle == nullptr)
            franko_byte_arrays::fail("Franko_Static_Array", "memmem", "source pointer is null");

        return franko_byte_arrays::memmem_impl("Franko_Static_Array", "memmem", data, N, 0, N, needle->data, M);
    }

    template <uint32_t M, typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    uint32_t memmem(const Franko_Static_Array<T, M>* needle, uint32_t start, uint32_t elementCount) const {
        if (needle == nullptr)
            franko_byte_arrays::fail("Franko_Static_Array", "memmem", "source pointer is null");

        return franko_byte_arrays::memmem_impl("Franko_Static_Array", "memmem", data, N, start, elementCount, needle->data, M);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    uint32_t memmem(const Franko_Dynamic_Array<T>* needle) const {
        if (needle == nullptr)
            franko_byte_arrays::fail("Franko_Static_Array", "memmem", "source pointer is null");

        return franko_byte_arrays::memmem_impl("Franko_Static_Array", "memmem", data, N, 0, N, needle->data, needle->count);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    uint32_t memmem(const Franko_Dynamic_Array<T>* needle, uint32_t start, uint32_t elementCount) const {
        if (needle == nullptr)
            franko_byte_arrays::fail("Franko_Static_Array", "memmem", "source pointer is null");

        return franko_byte_arrays::memmem_impl("Franko_Static_Array", "memmem", data, N, start, elementCount, needle->data, needle->count);
    }

    // ---- Endian accessors (byte element types) ----

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    uint16_t read_uint16_LE(uint32_t offset) const {
        return franko_byte_arrays::read_int_impl<uint16_t, true>("Franko_Static_Array", "read_uint16_LE", data, N, offset);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    uint16_t read_uint16_BE(uint32_t offset) const {
        return franko_byte_arrays::read_int_impl<uint16_t, false>("Franko_Static_Array", "read_uint16_BE", data, N, offset);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    void write_uint16_LE(uint32_t offset, uint16_t value) {
        franko_byte_arrays::write_int_impl<uint16_t, true>("Franko_Static_Array", "write_uint16_LE", data, N, offset, value);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    void write_uint16_BE(uint32_t offset, uint16_t value) {
        franko_byte_arrays::write_int_impl<uint16_t, false>("Franko_Static_Array", "write_uint16_BE", data, N, offset, value);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    uint32_t read_uint32_LE(uint32_t offset) const {
        return franko_byte_arrays::read_int_impl<uint32_t, true>("Franko_Static_Array", "read_uint32_LE", data, N, offset);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    uint32_t read_uint32_BE(uint32_t offset) const {
        return franko_byte_arrays::read_int_impl<uint32_t, false>("Franko_Static_Array", "read_uint32_BE", data, N, offset);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    void write_uint32_LE(uint32_t offset, uint32_t value) {
        franko_byte_arrays::write_int_impl<uint32_t, true>("Franko_Static_Array", "write_uint32_LE", data, N, offset, value);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    void write_uint32_BE(uint32_t offset, uint32_t value) {
        franko_byte_arrays::write_int_impl<uint32_t, false>("Franko_Static_Array", "write_uint32_BE", data, N, offset, value);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    uint64_t read_uint64_LE(uint32_t offset) const {
        return franko_byte_arrays::read_int_impl<uint64_t, true>("Franko_Static_Array", "read_uint64_LE", data, N, offset);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    uint64_t read_uint64_BE(uint32_t offset) const {
        return franko_byte_arrays::read_int_impl<uint64_t, false>("Franko_Static_Array", "read_uint64_BE", data, N, offset);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    void write_uint64_LE(uint32_t offset, uint64_t value) {
        franko_byte_arrays::write_int_impl<uint64_t, true>("Franko_Static_Array", "write_uint64_LE", data, N, offset, value);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    void write_uint64_BE(uint32_t offset, uint64_t value) {
        franko_byte_arrays::write_int_impl<uint64_t, false>("Franko_Static_Array", "write_uint64_BE", data, N, offset, value);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    int16_t read_int16_LE(uint32_t offset) const {
        return franko_byte_arrays::read_int_impl<int16_t, true>("Franko_Static_Array", "read_int16_LE", data, N, offset);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    int16_t read_int16_BE(uint32_t offset) const {
        return franko_byte_arrays::read_int_impl<int16_t, false>("Franko_Static_Array", "read_int16_BE", data, N, offset);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    void write_int16_LE(uint32_t offset, int16_t value) {
        franko_byte_arrays::write_int_impl<int16_t, true>("Franko_Static_Array", "write_int16_LE", data, N, offset, value);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    void write_int16_BE(uint32_t offset, int16_t value) {
        franko_byte_arrays::write_int_impl<int16_t, false>("Franko_Static_Array", "write_int16_BE", data, N, offset, value);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    int32_t read_int32_LE(uint32_t offset) const {
        return franko_byte_arrays::read_int_impl<int32_t, true>("Franko_Static_Array", "read_int32_LE", data, N, offset);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    int32_t read_int32_BE(uint32_t offset) const {
        return franko_byte_arrays::read_int_impl<int32_t, false>("Franko_Static_Array", "read_int32_BE", data, N, offset);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    void write_int32_LE(uint32_t offset, int32_t value) {
        franko_byte_arrays::write_int_impl<int32_t, true>("Franko_Static_Array", "write_int32_LE", data, N, offset, value);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    void write_int32_BE(uint32_t offset, int32_t value) {
        franko_byte_arrays::write_int_impl<int32_t, false>("Franko_Static_Array", "write_int32_BE", data, N, offset, value);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    int64_t read_int64_LE(uint32_t offset) const {
        return franko_byte_arrays::read_int_impl<int64_t, true>("Franko_Static_Array", "read_int64_LE", data, N, offset);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    int64_t read_int64_BE(uint32_t offset) const {
        return franko_byte_arrays::read_int_impl<int64_t, false>("Franko_Static_Array", "read_int64_BE", data, N, offset);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    void write_int64_LE(uint32_t offset, int64_t value) {
        franko_byte_arrays::write_int_impl<int64_t, true>("Franko_Static_Array", "write_int64_LE", data, N, offset, value);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    void write_int64_BE(uint32_t offset, int64_t value) {
        franko_byte_arrays::write_int_impl<int64_t, false>("Franko_Static_Array", "write_int64_BE", data, N, offset, value);
    }

    // ---- Single-bit accessors (byte element types) ----

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    uint8_t read_bit(uint64_t bitIndex) const {
        return franko_byte_arrays::read_bit_impl("Franko_Static_Array", "read_bit", data, N, bitIndex);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    void write_bit(uint64_t bitIndex, uint8_t value) {
        franko_byte_arrays::write_bit_impl("Franko_Static_Array", "write_bit", data, N, bitIndex, value);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    void flip_bit(uint64_t bitIndex) {
        franko_byte_arrays::flip_bit_impl("Franko_Static_Array", "flip_bit", data, N, bitIndex);
    }

    // ---- Whole-array bitwise mask mutators (byte element types) ----

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    void bitwise_not_mask() {
        franko_byte_arrays::bitwise_not_impl("Franko_Static_Array", "bitwise_not_mask", data, N);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    void bitwise_xor_mask(uint8_t mask) {
        franko_byte_arrays::bitwise_xor_impl("Franko_Static_Array", "bitwise_xor_mask", data, N, mask);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    void bitwise_and_mask(uint8_t mask) {
        franko_byte_arrays::bitwise_and_impl("Franko_Static_Array", "bitwise_and_mask", data, N, mask);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    void bitwise_or_mask(uint8_t mask) {
        franko_byte_arrays::bitwise_or_impl("Franko_Static_Array", "bitwise_or_mask", data, N, mask);
    }

    // ---- Whole-array bit-string shifts and popcount (byte element types) ----

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    void shift_bits_left(uint64_t bitCount) {
        franko_byte_arrays::shift_bits_left_impl("Franko_Static_Array", "shift_bits_left", data, N, bitCount);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    void shift_bits_right(uint64_t bitCount) {
        franko_byte_arrays::shift_bits_right_impl("Franko_Static_Array", "shift_bits_right", data, N, bitCount);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    uint64_t count_set_bits() const {
        return franko_byte_arrays::count_set_bits_impl("Franko_Static_Array", "count_set_bits", data, N);
    }
};

// ============================================================
// Franko_Dynamic_Array<T>
// ============================================================

template <typename T>
struct Franko_Dynamic_Array {
    uint32_t count = 0;
    T* data = nullptr;
    // False for every real array. The compiler sets it true on the hidden
    // borrowed headers it synthesizes for static-to-dynamic array pointer
    // view decays: their data points at storage owned by a
    // Franko_Static_Array, so destruction must not free it.
    bool borrowed = false;

    uint32_t length() const { return count; }

    ~Franko_Dynamic_Array() {
        if (!borrowed && data != nullptr) {
            destroy_elements(data, count);
            std::free(data);
            data = nullptr;
            count = 0;
        }
    }

    int32_t init(uint32_t n) {
        if (data != nullptr)
            franko_panic("Franko_Dynamic_Array already initialized");

        if (n == 0)
            franko_panic("Cannot allocate 0 memory for Franko_Dynamic_Array");

        T* newData = static_cast<T*>(std::malloc(sizeof(T) * static_cast<size_t>(n)));

        if (!newData)
            return -1;

        // Default-construct elements that need construction. Trivially
        // constructible element types keep raw uninitialized storage.
        // Runs before publishing data/count, since element construction
        // for runtime types is non-throwing.
        construct_elements(newData, n);

        data = newData;
        count = n;

        return 0;
    }

    int32_t init_zero(uint32_t n) {
        if (data != nullptr)
            franko_panic("Franko_Dynamic_Array already initialized");

        if (n == 0)
            franko_panic("Cannot allocate 0 memory for Franko_Dynamic_Array");

        T* newData = static_cast<T*>(std::calloc(static_cast<size_t>(n), sizeof(T)));

        if (!newData)
            return -1;

        // Default-construct over the zeroed bytes so members with default
        // member initializers get their defaults while the rest stay 0.
        construct_elements(newData, n);

        data = newData;
        count = n;

        return 0;
    }

    int32_t resize(uint32_t n) {
        if (data == nullptr)
            franko_panic("Franko_Dynamic_Array not initialized");

        if (n == 0)
            franko_panic("Cannot allocate 0 memory for Franko_Dynamic_Array");

        const uint32_t oldCount = count;

        if (n < oldCount) {
            // Destroy the dropped tail while its storage is still alive:
            // realloc may release the tail first, so destroying afterwards
            // would run destructors on freed memory.
            destroy_elements(data + n, oldCount - n);
        }

        T* newData = static_cast<T*>(std::realloc(data, sizeof(T) * static_cast<size_t>(n)));

        if (!newData) {
            // The buffer is unchanged on realloc failure, but a shrinking
            // resize already destroyed its dropped tail: narrow count so
            // the destroyed elements are never destroyed again.
            count = (n < oldCount) ? n : oldCount;
            return -1;
        }

        // std::realloc byte-moves existing elements, which is fine for the
        // runtime's trivially-relocatable element types. Only the grown
        // tail needs lifetime handling (the dropped tail was destroyed
        // above, before the realloc).
        if (n > oldCount)
            construct_elements(newData + oldCount, n - oldCount);

        data = newData;
        count = n;

        return 0;
    }

    void uninit() {
        if (data == nullptr)
            franko_panic("Franko_Dynamic_Array already uninitialized");

        destroy_elements(data, count);
        std::free(data);
        data = nullptr;
        count = 0;
    }

    const T& operator[](uint32_t index) const {
        if (data == nullptr)
            franko_panic("Franko_Dynamic_Array not initialized");

        if (index >= count)
            franko_panic("Franko_Dynamic_Array index out of bounds");

        return data[index];
    }

    T& operator[](uint32_t index) {
        if (data == nullptr)
            franko_panic("Franko_Dynamic_Array not initialized");

        if (index >= count)
            franko_panic("Franko_Dynamic_Array index out of bounds");

        return data[index];
    }

    void memset(uint8_t byteValue) {
        if (data == nullptr)
            franko_panic("Franko_Dynamic_Array memset on uninitialized array");

        if (count > 0) {
            std::memset(data, byteValue, sizeof(T) * static_cast<size_t>(count));
        }
    }

    void memset(uint8_t byteValue, uint32_t start, uint32_t elementCount) {
        if (data == nullptr)
            franko_panic("Franko_Dynamic_Array memset on uninitialized array");

        if (start > count)
            franko_panic("Franko_Dynamic_Array memset start out of bounds");

        if (elementCount > count - start)
            franko_panic("Franko_Dynamic_Array memset range out of bounds");

        if (elementCount > 0) {
            std::memset(data + start, byteValue, sizeof(T) * static_cast<size_t>(elementCount));
        }
    }

    void memcpy(const Franko_Dynamic_Array<T>* other) {
        if (data == nullptr)
            franko_panic("Franko_Dynamic_Array memcpy on uninitialized target array");

        if (other == nullptr)
            franko_panic("Franko_Dynamic_Array memcpy source pointer is null");

        if (other->data == nullptr)
            franko_panic("Franko_Dynamic_Array memcpy from uninitialized source array");

        const uint32_t copyCount = (count < other->count) ? count : other->count;

        if (copyCount > 0) {
            std::memcpy(data, other->data, sizeof(T) * static_cast<size_t>(copyCount));
        }
    }

    template <uint32_t M>
    void memcpy(const Franko_Static_Array<T, M>* other) {
        if (data == nullptr)
            franko_panic("Franko_Dynamic_Array memcpy on uninitialized target array");

        if (other == nullptr)
            franko_panic("Franko_Dynamic_Array memcpy source pointer is null");

        const uint32_t copyCount = (count < M) ? count : M;

        if (copyCount > 0) {
            std::memcpy(data, other->data, sizeof(T) * static_cast<size_t>(copyCount));
        }
    }

    void memcpy(const Franko_Dynamic_Array<T>* other, uint32_t dstStart, uint32_t srcStart, uint32_t elementCount) {
        if (data == nullptr)
            franko_panic("Franko_Dynamic_Array memcpy on uninitialized target array");

        if (other == nullptr)
            franko_panic("Franko_Dynamic_Array memcpy source pointer is null");

        if (other->data == nullptr)
            franko_panic("Franko_Dynamic_Array memcpy from uninitialized source array");

        if (dstStart > count)
            franko_panic("Franko_Dynamic_Array memcpy destination start out of bounds");

        if (srcStart > other->count)
            franko_panic("Franko_Dynamic_Array memcpy source start out of bounds");

        if (elementCount > count - dstStart)
            franko_panic("Franko_Dynamic_Array memcpy destination range out of bounds");

        if (elementCount > other->count - srcStart)
            franko_panic("Franko_Dynamic_Array memcpy source range out of bounds");

        if (elementCount > 0) {
            std::memcpy(data + dstStart, other->data + srcStart, sizeof(T) * static_cast<size_t>(elementCount));
        }
    }

    template <uint32_t M>
    void memcpy(const Franko_Static_Array<T, M>* other, uint32_t dstStart, uint32_t srcStart, uint32_t elementCount) {
        if (data == nullptr)
            franko_panic("Franko_Dynamic_Array memcpy on uninitialized target array");

        if (other == nullptr)
            franko_panic("Franko_Dynamic_Array memcpy source pointer is null");

        if (dstStart > count)
            franko_panic("Franko_Dynamic_Array memcpy destination start out of bounds");

        if (srcStart > M)
            franko_panic("Franko_Dynamic_Array memcpy source start out of bounds");

        if (elementCount > count - dstStart)
            franko_panic("Franko_Dynamic_Array memcpy destination range out of bounds");

        if (elementCount > M - srcStart)
            franko_panic("Franko_Dynamic_Array memcpy source range out of bounds");

        if (elementCount > 0) {
            std::memcpy(data + dstStart, other->data + srcStart, sizeof(T) * static_cast<size_t>(elementCount));
        }
    }

    void memmove(uint32_t dstStart, uint32_t srcStart, uint32_t elementCount) {
        if (data == nullptr)
            franko_panic("Franko_Dynamic_Array memmove on uninitialized array");

        if (dstStart > count)
            franko_panic("Franko_Dynamic_Array memmove destination start out of bounds");

        if (srcStart > count)
            franko_panic("Franko_Dynamic_Array memmove source start out of bounds");

        if (elementCount > count - dstStart)
            franko_panic("Franko_Dynamic_Array memmove destination range out of bounds");

        if (elementCount > count - srcStart)
            franko_panic("Franko_Dynamic_Array memmove source range out of bounds");

        if (elementCount > 0) {
            std::memmove(data + dstStart, data + srcStart, sizeof(T) * static_cast<size_t>(elementCount));
        }
    }

    // memcmp: lexicographic raw-byte comparison, C semantics (the sign
    // of the first differing byte; only the sign is meaningful).
    // Restricted to trivially copyable element types (the C++ twin of
    // Franko's memcpyable rule). The whole form is lexicographic across
    // lengths: on an equal shared prefix the shorter array is smaller.
    // The ranged form compares exactly `elementCount` elements.

    template <uint32_t M, typename U = T, typename std::enable_if<std::is_trivially_copyable<U>::value, int>::type = 0>
    int32_t memcmp(const Franko_Static_Array<T, M>* other) const {
        if (data == nullptr)
            franko_panic("Franko_Dynamic_Array memcmp on uninitialized array");

        if (other == nullptr)
            franko_panic("Franko_Dynamic_Array memcmp source pointer is null");

        const uint32_t shared = (count < M) ? count : M;

        if (shared > 0) {
            const int result = std::memcmp(data, other->data, sizeof(T) * static_cast<size_t>(shared));

            if (result != 0) {
                return result;
            }
        }

        if (count < M) {
            return -1;
        }

        if (count > M) {
            return 1;
        }

        return 0;
    }

    template <typename U = T, typename std::enable_if<std::is_trivially_copyable<U>::value, int>::type = 0>
    int32_t memcmp(const Franko_Dynamic_Array<T>* other) const {
        if (data == nullptr)
            franko_panic("Franko_Dynamic_Array memcmp on uninitialized array");

        if (other == nullptr)
            franko_panic("Franko_Dynamic_Array memcmp source pointer is null");

        if (other->data == nullptr)
            franko_panic("Franko_Dynamic_Array memcmp from uninitialized source array");

        const uint32_t shared = (count < other->count) ? count : other->count;

        if (shared > 0) {
            const int result = std::memcmp(data, other->data, sizeof(T) * static_cast<size_t>(shared));

            if (result != 0) {
                return result;
            }
        }

        if (count < other->count) {
            return -1;
        }

        if (count > other->count) {
            return 1;
        }

        return 0;
    }

    template <uint32_t M, typename U = T, typename std::enable_if<std::is_trivially_copyable<U>::value, int>::type = 0>
    int32_t memcmp(const Franko_Static_Array<T, M>* other, uint32_t dstStart, uint32_t srcStart, uint32_t elementCount) const {
        if (data == nullptr)
            franko_panic("Franko_Dynamic_Array memcmp on uninitialized array");

        if (other == nullptr)
            franko_panic("Franko_Dynamic_Array memcmp source pointer is null");

        if (dstStart > count)
            franko_panic("Franko_Dynamic_Array memcmp destination start out of bounds");

        if (srcStart > M)
            franko_panic("Franko_Dynamic_Array memcmp source start out of bounds");

        if (elementCount > count - dstStart)
            franko_panic("Franko_Dynamic_Array memcmp destination range out of bounds");

        if (elementCount > M - srcStart)
            franko_panic("Franko_Dynamic_Array memcmp source range out of bounds");

        if (elementCount > 0) {
            return std::memcmp(data + dstStart, other->data + srcStart, sizeof(T) * static_cast<size_t>(elementCount));
        }

        return 0;
    }

    template <typename U = T, typename std::enable_if<std::is_trivially_copyable<U>::value, int>::type = 0>
    int32_t memcmp(const Franko_Dynamic_Array<T>* other, uint32_t dstStart, uint32_t srcStart, uint32_t elementCount) const {
        if (data == nullptr)
            franko_panic("Franko_Dynamic_Array memcmp on uninitialized array");

        if (other == nullptr)
            franko_panic("Franko_Dynamic_Array memcmp source pointer is null");

        if (other->data == nullptr)
            franko_panic("Franko_Dynamic_Array memcmp from uninitialized source array");

        if (dstStart > count)
            franko_panic("Franko_Dynamic_Array memcmp destination start out of bounds");

        if (srcStart > other->count)
            franko_panic("Franko_Dynamic_Array memcmp source start out of bounds");

        if (elementCount > count - dstStart)
            franko_panic("Franko_Dynamic_Array memcmp destination range out of bounds");

        if (elementCount > other->count - srcStart)
            franko_panic("Franko_Dynamic_Array memcmp source range out of bounds");

        if (elementCount > 0) {
            return std::memcmp(data + dstStart, other->data + srcStart, sizeof(T) * static_cast<size_t>(elementCount));
        }

        return 0;
    }

    // ==========================================================
    // Byte-array and bit intrinsics (see franko_byte_arrays above).
    // Like every other dynamic-array operation except length(),
    // these panic on an uninitialized array.
    // ==========================================================

    // ---- memchr (byte element types) ----

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    uint32_t memchr(uint8_t value) const {
        return franko_byte_arrays::memchr_impl("Franko_Dynamic_Array", "memchr", data, count, 0, count, value);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    uint32_t memchr(uint8_t value, uint32_t start, uint32_t elementCount) const {
        return franko_byte_arrays::memchr_impl("Franko_Dynamic_Array", "memchr", data, count, start, elementCount, value);
    }

    // ---- memmem (byte element types) ----

    template <uint32_t M, typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    uint32_t memmem(const Franko_Static_Array<T, M>* needle) const {
        if (needle == nullptr)
            franko_byte_arrays::fail("Franko_Dynamic_Array", "memmem", "source pointer is null");

        return franko_byte_arrays::memmem_impl("Franko_Dynamic_Array", "memmem", data, count, 0, count, needle->data, M);
    }

    template <uint32_t M, typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    uint32_t memmem(const Franko_Static_Array<T, M>* needle, uint32_t start, uint32_t elementCount) const {
        if (needle == nullptr)
            franko_byte_arrays::fail("Franko_Dynamic_Array", "memmem", "source pointer is null");

        return franko_byte_arrays::memmem_impl("Franko_Dynamic_Array", "memmem", data, count, start, elementCount, needle->data, M);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    uint32_t memmem(const Franko_Dynamic_Array<T>* needle) const {
        if (needle == nullptr)
            franko_byte_arrays::fail("Franko_Dynamic_Array", "memmem", "source pointer is null");

        return franko_byte_arrays::memmem_impl("Franko_Dynamic_Array", "memmem", data, count, 0, count, needle->data, needle->count);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    uint32_t memmem(const Franko_Dynamic_Array<T>* needle, uint32_t start, uint32_t elementCount) const {
        if (needle == nullptr)
            franko_byte_arrays::fail("Franko_Dynamic_Array", "memmem", "source pointer is null");

        return franko_byte_arrays::memmem_impl("Franko_Dynamic_Array", "memmem", data, count, start, elementCount, needle->data, needle->count);
    }

    // ---- Endian accessors (byte element types) ----

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    uint16_t read_uint16_LE(uint32_t offset) const {
        return franko_byte_arrays::read_int_impl<uint16_t, true>("Franko_Dynamic_Array", "read_uint16_LE", data, count, offset);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    uint16_t read_uint16_BE(uint32_t offset) const {
        return franko_byte_arrays::read_int_impl<uint16_t, false>("Franko_Dynamic_Array", "read_uint16_BE", data, count, offset);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    void write_uint16_LE(uint32_t offset, uint16_t value) {
        franko_byte_arrays::write_int_impl<uint16_t, true>("Franko_Dynamic_Array", "write_uint16_LE", data, count, offset, value);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    void write_uint16_BE(uint32_t offset, uint16_t value) {
        franko_byte_arrays::write_int_impl<uint16_t, false>("Franko_Dynamic_Array", "write_uint16_BE", data, count, offset, value);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    uint32_t read_uint32_LE(uint32_t offset) const {
        return franko_byte_arrays::read_int_impl<uint32_t, true>("Franko_Dynamic_Array", "read_uint32_LE", data, count, offset);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    uint32_t read_uint32_BE(uint32_t offset) const {
        return franko_byte_arrays::read_int_impl<uint32_t, false>("Franko_Dynamic_Array", "read_uint32_BE", data, count, offset);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    void write_uint32_LE(uint32_t offset, uint32_t value) {
        franko_byte_arrays::write_int_impl<uint32_t, true>("Franko_Dynamic_Array", "write_uint32_LE", data, count, offset, value);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    void write_uint32_BE(uint32_t offset, uint32_t value) {
        franko_byte_arrays::write_int_impl<uint32_t, false>("Franko_Dynamic_Array", "write_uint32_BE", data, count, offset, value);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    uint64_t read_uint64_LE(uint32_t offset) const {
        return franko_byte_arrays::read_int_impl<uint64_t, true>("Franko_Dynamic_Array", "read_uint64_LE", data, count, offset);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    uint64_t read_uint64_BE(uint32_t offset) const {
        return franko_byte_arrays::read_int_impl<uint64_t, false>("Franko_Dynamic_Array", "read_uint64_BE", data, count, offset);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    void write_uint64_LE(uint32_t offset, uint64_t value) {
        franko_byte_arrays::write_int_impl<uint64_t, true>("Franko_Dynamic_Array", "write_uint64_LE", data, count, offset, value);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    void write_uint64_BE(uint32_t offset, uint64_t value) {
        franko_byte_arrays::write_int_impl<uint64_t, false>("Franko_Dynamic_Array", "write_uint64_BE", data, count, offset, value);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    int16_t read_int16_LE(uint32_t offset) const {
        return franko_byte_arrays::read_int_impl<int16_t, true>("Franko_Dynamic_Array", "read_int16_LE", data, count, offset);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    int16_t read_int16_BE(uint32_t offset) const {
        return franko_byte_arrays::read_int_impl<int16_t, false>("Franko_Dynamic_Array", "read_int16_BE", data, count, offset);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    void write_int16_LE(uint32_t offset, int16_t value) {
        franko_byte_arrays::write_int_impl<int16_t, true>("Franko_Dynamic_Array", "write_int16_LE", data, count, offset, value);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    void write_int16_BE(uint32_t offset, int16_t value) {
        franko_byte_arrays::write_int_impl<int16_t, false>("Franko_Dynamic_Array", "write_int16_BE", data, count, offset, value);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    int32_t read_int32_LE(uint32_t offset) const {
        return franko_byte_arrays::read_int_impl<int32_t, true>("Franko_Dynamic_Array", "read_int32_LE", data, count, offset);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    int32_t read_int32_BE(uint32_t offset) const {
        return franko_byte_arrays::read_int_impl<int32_t, false>("Franko_Dynamic_Array", "read_int32_BE", data, count, offset);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    void write_int32_LE(uint32_t offset, int32_t value) {
        franko_byte_arrays::write_int_impl<int32_t, true>("Franko_Dynamic_Array", "write_int32_LE", data, count, offset, value);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    void write_int32_BE(uint32_t offset, int32_t value) {
        franko_byte_arrays::write_int_impl<int32_t, false>("Franko_Dynamic_Array", "write_int32_BE", data, count, offset, value);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    int64_t read_int64_LE(uint32_t offset) const {
        return franko_byte_arrays::read_int_impl<int64_t, true>("Franko_Dynamic_Array", "read_int64_LE", data, count, offset);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    int64_t read_int64_BE(uint32_t offset) const {
        return franko_byte_arrays::read_int_impl<int64_t, false>("Franko_Dynamic_Array", "read_int64_BE", data, count, offset);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    void write_int64_LE(uint32_t offset, int64_t value) {
        franko_byte_arrays::write_int_impl<int64_t, true>("Franko_Dynamic_Array", "write_int64_LE", data, count, offset, value);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    void write_int64_BE(uint32_t offset, int64_t value) {
        franko_byte_arrays::write_int_impl<int64_t, false>("Franko_Dynamic_Array", "write_int64_BE", data, count, offset, value);
    }

    // ---- Single-bit accessors (byte element types) ----

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    uint8_t read_bit(uint64_t bitIndex) const {
        return franko_byte_arrays::read_bit_impl("Franko_Dynamic_Array", "read_bit", data, count, bitIndex);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    void write_bit(uint64_t bitIndex, uint8_t value) {
        franko_byte_arrays::write_bit_impl("Franko_Dynamic_Array", "write_bit", data, count, bitIndex, value);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    void flip_bit(uint64_t bitIndex) {
        franko_byte_arrays::flip_bit_impl("Franko_Dynamic_Array", "flip_bit", data, count, bitIndex);
    }

    // ---- Whole-array bitwise mask mutators (byte element types) ----

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    void bitwise_not_mask() {
        franko_byte_arrays::bitwise_not_impl("Franko_Dynamic_Array", "bitwise_not_mask", data, count);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    void bitwise_xor_mask(uint8_t mask) {
        franko_byte_arrays::bitwise_xor_impl("Franko_Dynamic_Array", "bitwise_xor_mask", data, count, mask);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    void bitwise_and_mask(uint8_t mask) {
        franko_byte_arrays::bitwise_and_impl("Franko_Dynamic_Array", "bitwise_and_mask", data, count, mask);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    void bitwise_or_mask(uint8_t mask) {
        franko_byte_arrays::bitwise_or_impl("Franko_Dynamic_Array", "bitwise_or_mask", data, count, mask);
    }

    // ---- Whole-array bit-string shifts and popcount (byte element types) ----

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    void shift_bits_left(uint64_t bitCount) {
        franko_byte_arrays::shift_bits_left_impl("Franko_Dynamic_Array", "shift_bits_left", data, count, bitCount);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    void shift_bits_right(uint64_t bitCount) {
        franko_byte_arrays::shift_bits_right_impl("Franko_Dynamic_Array", "shift_bits_right", data, count, bitCount);
    }

    template <typename U = T, typename std::enable_if<std::is_same<U, uint8_t>::value, int>::type = 0>
    uint64_t count_set_bits() const {
        return franko_byte_arrays::count_set_bits_impl("Franko_Dynamic_Array", "count_set_bits", data, count);
    }

private:
    static void construct_elements(T* p, uint32_t n) {
        if (!std::is_trivially_default_constructible<T>::value) {
            for (uint32_t i = 0; i < n; ++i)
                new (p + i) T();
        }
    }

    static void destroy_elements(T* p, uint32_t n) {
        if (!std::is_trivially_destructible<T>::value) {
            for (uint32_t i = 0; i < n; ++i)
                (p + i)->~T();
        }
    }
};
