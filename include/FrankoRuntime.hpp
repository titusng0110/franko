#pragma once

// ============================================================
// Franko runtime
// ============================================================
//
// One runtime for all three build modes. Pointers whose pointee is not
// an array are emitted as Franko_Thin_Pointer<T>; pointers to arrays
// are Franko_Fat_Pointer<T>. The C++ compile flags select the build
// mode:
//
//   FRANKO_USE_JEMALLOC=1      bundled jemalloc instead of the system
//   FRANKO_BOUNDS_UNCHECKED=1  strip bounds/range/bit/offset checks
//
// | Build | FRANKO_USE_JEMALLOC | FRANKO_BOUNDS_UNCHECKED |
// |-------|---------------------|--------------------------|
// | ASan  | 0 (default)         | 0 (default)              |
// | -O    | 1                   | 0                        |
// | -Omax | 1                   | 1                        |
//
// What FRANKO_BOUNDS_UNCHECKED strips: static array indexing,
// fat-pointer operator[], memset/memcpy/memmove/memcmp ranges,
// memchr/memmem start/range, endian offset+width, bit indices and bit
// ranges, memset_* byte ranges, and unsafe_cast range checks. Null,
// uninitialized, lifecycle, and dynamic-array descriptor checks always
// stay.
// ============================================================

#ifndef FRANKO_USE_JEMALLOC
#define FRANKO_USE_JEMALLOC 0
#endif

#ifndef FRANKO_BOUNDS_UNCHECKED
#define FRANKO_BOUNDS_UNCHECKED 0
#endif

#if defined(_MSC_VER)
#define FRANKO_INLINE __forceinline
#define FRANKO_NOINLINE __declspec(noinline)
#else
#define FRANKO_INLINE __attribute__((always_inline)) inline
#define FRANKO_NOINLINE __attribute__((noinline))
#endif

#include <cstddef>   // for size_t, std::nullptr_t
#include <cstdint>   // for uint8_t, uint32_t, int32_t
#include <cstdlib>   // for std::malloc/calloc/realloc/free (non-jemalloc builds) _byteswap_* (MSVC byte_swap fallback) std::abort()
#include <cstring>   // for std::memcpy, std::memset, std::memmove, std::memcmp, std::memchr
#include <cstdio>    // for std::fputs, std::snprintf
#include <limits>    // for std::numeric_limits (franko_integer_unsafe_cast bounds)
#include <new>       // for placement new
#include <type_traits> // for std::is_signed, std::make_unsigned, std::enable_if, and trivial-construction traits

#if FRANKO_USE_JEMALLOC
#include <jemalloc/jemalloc.h> // for je_malloc, je_calloc, je_realloc, je_free
#endif

#if defined(__aarch64__)
#include <arm_neon.h> // for the byte-array fill and popcount fast paths
#elif defined(_M_ARM64)
#include <arm64_neon.h> // MSVC ARM64 NEON header
#elif defined(__x86_64__) || defined(_M_X64)
#include <immintrin.h> // for the SSE2 byte-array fill fast paths
#endif

[[noreturn]] inline void franko_panic(const char* message) {
    std::fputs("Franko runtime panic: ", stderr);
    std::fputs(message, stderr);
    std::fputs("\n", stderr);
    std::abort();
}

// ============================================================
// franko_borrow(value): borrow a temporary for the full expression
// ============================================================
//
// Emitted for compiler-generated operator operand borrows when the
// operand is not a storage-backed lvalue (a literal, a call result, or
// another temporary). Binding the value to the T&& parameter keeps it
// alive until the end of the full-expression containing the operator
// call, which is the C++ temporary-lifetime rule for reference-bound
// temporaries.
template <typename T>
typename std::remove_reference<T>::type* franko_borrow(T&& value) {
    return &value;
}

// ============================================================
// Franko_Thin_Pointer<T>
// ============================================================
//
// C++ representation of a pointer whose pointee is not an array:
// ptr<T>, mview<T>, and view<T> for scalar, struct, tuple, and pointer
// T. The wrapper has exactly one data field and default-constructs to
// null, so pointer variables, fields, tuple elements, and array
// elements are null-initialized.
template <typename T>
struct Franko_Thin_Pointer {
    T* data = nullptr;

    constexpr Franko_Thin_Pointer() : data(nullptr) {}
    constexpr Franko_Thin_Pointer(std::nullptr_t) : data(nullptr) {}
    constexpr explicit Franko_Thin_Pointer(T* value) : data(value) {}
};

template <typename T>
constexpr Franko_Thin_Pointer<T> franko_make_thin_pointer(T* data) {
    return Franko_Thin_Pointer<T>(data);
}

template <typename T>
constexpr bool operator==(const Franko_Thin_Pointer<T>& left, const Franko_Thin_Pointer<T>& right) {
    return left.data == right.data;
}

template <typename T>
constexpr bool operator!=(const Franko_Thin_Pointer<T>& left, const Franko_Thin_Pointer<T>& right) {
    return left.data != right.data;
}

template <typename T>
constexpr bool operator==(const Franko_Thin_Pointer<T>& pointer, std::nullptr_t) {
    return pointer.data == nullptr;
}

template <typename T>
constexpr bool operator!=(const Franko_Thin_Pointer<T>& pointer, std::nullptr_t) {
    return pointer.data != nullptr;
}

template <typename T>
constexpr bool operator==(std::nullptr_t, const Franko_Thin_Pointer<T>& pointer) {
    return pointer.data == nullptr;
}

template <typename T>
constexpr bool operator!=(std::nullptr_t, const Franko_Thin_Pointer<T>& pointer) {
    return pointer.data != nullptr;
}

// ============================================================
// franko_integer_unsafe_cast<To>(v): runtime-checked integer conversion
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
struct franko_integer_unsafe_cast_range;

// Signed source, signed target: direct comparison promotes safely.
template <typename To, typename From>
struct franko_integer_unsafe_cast_range<To, From, true, true> {
    static bool below_min(From v) { return v < std::numeric_limits<To>::min(); }
    static bool above_max(From v) { return v > std::numeric_limits<To>::max(); }
};

// Unsigned source, unsigned target: v >= 0 == min(To), so only the
// upper bound can fail. Both sides unsigned: no sign warnings.
template <typename To, typename From>
struct franko_integer_unsafe_cast_range<To, From, false, false> {
    static bool below_min(From) { return false; }
    static bool above_max(From v) { return v > std::numeric_limits<To>::max(); }
};

// Signed source, unsigned target: negatives always fail; the upper
// bound compares non-negative values as unsigned.
template <typename To, typename From>
struct franko_integer_unsafe_cast_range<To, From, true, false> {
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
struct franko_integer_unsafe_cast_range<To, From, false, true> {
    static bool below_min(From) { return false; }
    static bool above_max(From v) {
        typedef typename std::make_unsigned<To>::type unsigned_to;
        return v > static_cast<unsigned_to>(std::numeric_limits<To>::max());
    }
};

template <typename To, typename From>
To franko_integer_unsafe_cast(From v) {
#if !FRANKO_BOUNDS_UNCHECKED
    if (franko_integer_unsafe_cast_range<To, From>::below_min(v) || franko_integer_unsafe_cast_range<To, From>::above_max(v)) {
        franko_panic("franko_integer_unsafe_cast: value out of range for target type");
    }
#endif

    return static_cast<To>(v);
}

// ============================================================
// franko_element_lifecycle<T>
// ============================================================
//
// Franko_Dynamic_Array<T>'s element construction/destruction. The
// general case skips trivially constructible/destructible types; the
// T[N] specialization exists because (p + i)->~T() is ill-formed for
// raw array element types (Franko [[T, M]] dynamic arrays are valid).
template <typename T>
struct franko_element_lifecycle {
    static void construct(T* p, uint32_t n) {
        if (!std::is_trivially_default_constructible<T>::value) {
            for (uint32_t i = 0; i < n; ++i)
                new (p + i) T();
        }
    }

    static void destroy(T* p, uint32_t n) {
        if (!std::is_trivially_destructible<T>::value) {
            for (uint32_t i = 0; i < n; ++i)
                (p + i)->~T();
        }
    }
};

template <typename T, uint32_t N>
struct franko_element_lifecycle<T[N]> {
    static void construct(T (*p)[N], uint32_t n) {
        for (uint32_t i = 0; i < n; ++i)
            franko_element_lifecycle<T>::construct(p[i], N);
    }

    static void destroy(T (*p)[N], uint32_t n) {
        for (uint32_t i = 0; i < n; ++i)
            franko_element_lifecycle<T>::destroy(p[i], N);
    }
};

// A dynamic array is always wrapped in emitted code by a Franko_Fat_Pointer<T> with mode = 3. // malloc runtime checks are in here
template <typename T>
struct Franko_Dynamic_Array {
    T* data = nullptr;
    uint32_t count = 0;

    uint32_t length() const { return count; }

    ~Franko_Dynamic_Array() {
        if (data != nullptr) {
            franko_element_lifecycle<T>::destroy(data, count);
#if FRANKO_USE_JEMALLOC
            je_free(data);
#else
            std::free(data);
#endif
            count = 0;
            data = nullptr;
        }
    }

    int32_t init(uint32_t n) {
        if (data != nullptr)
            franko_panic("Franko dynamic array already initialized");
        if (n == 0)
            franko_panic("Cannot allocate 0 memory for Franko dynamic array");
#if FRANKO_USE_JEMALLOC
        T* newData = static_cast<T*>(je_malloc(sizeof(T) * static_cast<size_t>(n)));
#else
        T* newData = static_cast<T*>(std::malloc(sizeof(T) * static_cast<size_t>(n)));
#endif
        if (!newData)
            return -1;
        franko_element_lifecycle<T>::construct(newData, n);
        data = newData;
        count = n;
        return 0;
    }

    int32_t init_zero(uint32_t n) {
        if (data != nullptr)
            franko_panic("Franko dynamic array already initialized");
        if (n == 0)
            franko_panic("Cannot allocate 0 memory for Franko dynamic array");
#if FRANKO_USE_JEMALLOC
        T* newData = static_cast<T*>(je_calloc(static_cast<size_t>(n), sizeof(T)));
#else
        T* newData = static_cast<T*>(std::calloc(static_cast<size_t>(n), sizeof(T)));
#endif
        if (!newData)
            return -1;
        franko_element_lifecycle<T>::construct(newData, n);
        data = newData;
        count = n;
        return 0;
    }

    int32_t resize(uint32_t n) {
        if (data == nullptr)
            franko_panic("Franko dynamic array not initialized");
        if (n == 0)
            franko_panic("Cannot allocate 0 memory for Franko dynamic array");
        const uint32_t oldCount = count;
        if (n < oldCount)
            franko_element_lifecycle<T>::destroy(data + n, oldCount - n);
#if FRANKO_USE_JEMALLOC
        T* newData = static_cast<T*>(je_realloc(data, sizeof(T) * static_cast<size_t>(n)));
#else
        T* newData = static_cast<T*>(std::realloc(data, sizeof(T) * static_cast<size_t>(n)));
#endif
        if (!newData) {
            count = (n < oldCount) ? n : oldCount;
            return -1;
        }
        if (n > oldCount)
            franko_element_lifecycle<T>::construct(newData + oldCount, n - oldCount);
        data = newData;
        count = n;
        return 0;
    }

    void uninit() {
        if (data == nullptr)
            franko_panic("Franko dynamic array already uninitialized");
        franko_element_lifecycle<T>::destroy(data, count);
#if FRANKO_USE_JEMALLOC
        je_free(data);
#else
        std::free(data);
#endif
        data = nullptr;
        count = 0;
    }

    const T& operator[](uint32_t index) const {
        if (data == nullptr)
            franko_panic("Franko dynamic array not initialized");
#if !FRANKO_BOUNDS_UNCHECKED
        if (index >= count)
            franko_panic("Franko dynamic array index out of bounds");
#endif
        return data[index];
    }

    T& operator[](uint32_t index) {
        if (data == nullptr)
            franko_panic("Franko dynamic array not initialized");
#if !FRANKO_BOUNDS_UNCHECKED
        if (index >= count)
            franko_panic("Franko dynamic array index out of bounds");
#endif
        return data[index];
    }
};

// ============================================================
// Static array helpers
// ============================================================
// Static arrays are raw C++ arrays. franko_static_array_at is the
// checked element access emitted in ASan and -O builds; -Omax emits
// bare arr[i]. franko_static_array_ref rebuilds an array lvalue when a
// fat pointer to a static array is dereferenced.
template <typename T, uint32_t N>
inline T& franko_static_array_at(T (&data)[N], uint32_t index) {
#if !FRANKO_BOUNDS_UNCHECKED
    if (index >= N)
        franko_panic("Franko static array index out of bounds");
#endif
    return data[index];
}

template <typename T, uint32_t N>
T (&franko_static_array_ref(T* data))[N] {
    return *reinterpret_cast<T (*)[N]>(data);
}

// ============================================================
// Byte-array and bit intrinsics (shared kernels)
// ============================================================
//
// These implement the Franko byte-array intrinsics: memchr / memmem,
// the read_*_{LE,BE} / write_*_{LE,BE} endian accessors,
// read_bit / write_bit / flip_bit / swap_bits / reverse_bits, the
// memset_* byte fills, shift_bits_* mutators, and popcount. memcmp is not here: it
// lives inside Franko_Fat_Pointer next to memset / memcpy / memmove.
//
// Design:
//
// * The kernels are free templates over (pointer, length) pairs, so
//   Franko_Fat_Pointer<T> stays a small delegate that supplies the
//   compiled-in class tag (for panic messages) and its length.
// * The compiler enforces the byte-only rule before emitting any of
//   these members; the C++ layer does not duplicate that check.
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

// Assembles "<class> <intrinsic> <problem>" and panics. The message is
// truncated to fit a fixed 256-byte stack buffer. snprintf never writes
// past the buffer and always NUL-terminates. Only runs on the failure
// path.
inline void fail(const char* cls, const char* op, const char* what) {
    char message[256];
    std::snprintf(message, sizeof(message), "%s %s %s", cls, op, what);
    franko_panic(message);
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

#if !FRANKO_BOUNDS_UNCHECKED
    if (start > count)
        fail(cls, op, "start out of bounds");

    if (range > count - start)
        fail(cls, op, "range out of bounds");
#else
    (void)count;
#endif

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
// Toolchains without it (MSVC) fall back to a hand-written scan below.
// Defined to 0 externally to test the fallback path.
#ifndef FRANKO_HAS_MEMMEM
#if defined(__GLIBC__) || defined(__APPLE__) || defined(__FreeBSD__) || defined(__OpenBSD__) || defined(__NetBSD__)
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

#if !FRANKO_BOUNDS_UNCHECKED
    if (start > count)
        fail(cls, op, "start out of bounds");

    if (range > count - start)
        fail(cls, op, "range out of bounds");
#else
    (void)count;
#endif

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
    // Portable fallback (MSVC and friends): a hand-written sequential
    // scan. Unlike strstr, it is binary-safe: no NUL termination is
    // assumed and embedded NUL bytes are ordinary data.
    const uint8_t* begin = data + start;
    const uint8_t* end = begin + range;
    const uint8_t* needleEnd = needle + needleCount;
    const uint8_t* hit = end;

    for (const uint8_t* candidate = begin; candidate + needleCount <= end; ++candidate) {
        const uint8_t* h = candidate;
        const uint8_t* n = needle;
        while (n != needleEnd && *h == *n) {
            ++h;
            ++n;
        }
        if (n == needleEnd) {
            hit = candidate;
            break;
        }
    }

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
    if (data == nullptr)
        fail(cls, op, "on uninitialized array");

#if !FRANKO_BOUNDS_UNCHECKED
    const uint32_t width = static_cast<uint32_t>(sizeof(R));

    if (offset > count || width > count - offset)
        fail(cls, op, "offset out of bounds");
#else
    (void)count;
#endif

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
    if (data == nullptr)
        fail(cls, op, "on uninitialized array");

#if !FRANKO_BOUNDS_UNCHECKED
    const uint32_t width = static_cast<uint32_t>(sizeof(R));

    if (offset > count || width > count - offset)
        fail(cls, op, "offset out of bounds");
#else
    (void)count;
#endif

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

#if !FRANKO_BOUNDS_UNCHECKED
    if (bitIndex >= static_cast<uint64_t>(count) * 8)
        fail(cls, op, "bit index out of bounds");
#else
    (void)count;
    (void)bitIndex;
#endif
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

inline void swap_bits_impl(const char* cls, const char* op, uint8_t* data, uint32_t count, uint64_t firstBit, uint64_t secondBit) {
    check_bit_index(cls, op, data, count, firstBit);
    check_bit_index(cls, op, data, count, secondBit);

    if (firstBit == secondBit) {
        return;
    }

    const uint64_t firstByte = firstBit >> 3;
    const uint64_t secondByte = secondBit >> 3;
    const uint8_t firstMask = static_cast<uint8_t>(1u << (firstBit & 7));
    const uint8_t secondMask = static_cast<uint8_t>(1u << (secondBit & 7));
    const uint8_t firstValue = static_cast<uint8_t>(data[firstByte] & firstMask);
    const uint8_t secondValue = static_cast<uint8_t>(data[secondByte] & secondMask);

    if (firstValue != secondValue) {
        data[firstByte] ^= firstMask;
        data[secondByte] ^= secondMask;
    }
}

inline void reverse_bits_impl(const char* cls, const char* op, uint8_t* data, uint32_t count, uint64_t startBit, uint64_t bitCount) {
    if (data == nullptr)
        fail(cls, op, "on uninitialized array");

#if !FRANKO_BOUNDS_UNCHECKED
    const uint64_t totalBits = static_cast<uint64_t>(count) * 8;

    if (startBit > totalBits || bitCount > totalBits - startBit)
        fail(cls, op, "bit range out of bounds");
#else
    (void)count;
#endif

    if (bitCount < 2) {
        return;
    }

    uint64_t low = startBit;
    uint64_t high = startBit + bitCount - 1;

    while (low < high) {
        const uint8_t lowMask = static_cast<uint8_t>(1u << (low & 7));
        const uint8_t highMask = static_cast<uint8_t>(1u << (high & 7));
        const uint8_t lowValue = static_cast<uint8_t>(data[low >> 3] & lowMask);
        const uint8_t highValue = static_cast<uint8_t>(data[high >> 3] & highMask);

        if (lowValue != highValue) {
            data[low >> 3] ^= lowMask;
            data[high >> 3] ^= highMask;
        }

        ++low;
        --high;
    }
}

// Count zero bits starting at bitIndex and walking toward the array
// end. The bit at bitIndex is included, so a set bit returns zero.
inline uint64_t count_trailing_zeros_impl(const char* cls, const char* op, const uint8_t* data, uint32_t count, uint64_t bitIndex) {
    check_bit_index(cls, op, data, count, bitIndex);

    const uint64_t byteIndex = bitIndex >> 3;
    const uint32_t bitOffset = static_cast<uint32_t>(bitIndex & 7);
    uint32_t value = static_cast<uint32_t>(data[byteIndex]) >> bitOffset;

    if (value != 0) {
        uint32_t zeros = 0;

        while ((value & 1u) == 0) {
            ++zeros;
            value >>= 1;
        }

        return zeros;
    }

    uint64_t zeros = static_cast<uint64_t>(8 - bitOffset);

    for (uint64_t i = byteIndex + 1; i < count; ++i) {
        if (data[i] != 0) {
            uint32_t byteValue = data[i];
            uint32_t inByte = 0;

            while ((byteValue & 1u) == 0) {
                ++inByte;
                byteValue >>= 1;
            }

            return zeros + inByte;
        }

        zeros += 8;
    }

    return zeros;
}

// Count zero bits starting at bitIndex and walking toward the array
// start. The bit at bitIndex is included, so a set bit returns zero.
inline uint64_t count_leading_zeros_impl(const char* cls, const char* op, const uint8_t* data, uint32_t count, uint64_t bitIndex) {
    check_bit_index(cls, op, data, count, bitIndex);

    const uint64_t byteIndex = bitIndex >> 3;
    const uint32_t bitOffset = static_cast<uint32_t>(bitIndex & 7);
    const uint32_t mask = bitOffset == 7 ? 0xFFu : ((1u << (bitOffset + 1)) - 1u);
    uint32_t value = static_cast<uint32_t>(data[byteIndex]) & mask;

    if (value != 0) {
        uint32_t highest = 0;

        while ((value >> (highest + 1)) != 0) {
            ++highest;
        }

        return static_cast<uint64_t>(bitOffset - highest);
    }

    uint64_t zeros = static_cast<uint64_t>(bitOffset + 1);

    for (uint64_t i = byteIndex; i-- > 0; ) {
        if (data[i] != 0) {
            uint32_t byteValue = data[i];
            uint32_t highest = 0;

            while ((byteValue >> (highest + 1)) != 0) {
                ++highest;
            }

            return zeros + static_cast<uint64_t>(7 - highest);
        }

        zeros += 8;
    }

    return zeros;
}

// ------------------------------------------------------------
// Ranged and whole-array byte fills: a[i] = ~a[i] / a[i] ^= value /
// a[i] &= value / a[i] |= value. The range form checks start/count
// exactly like memchr; the whole-array form passes start = 0.
// ------------------------------------------------------------

inline void memset_not_impl(const char* cls, const char* op, uint8_t* data, uint32_t count, uint32_t start, uint32_t range) {
    if (data == nullptr)
        fail(cls, op, "on uninitialized array");

#if !FRANKO_BOUNDS_UNCHECKED
    if (start > count)
        fail(cls, op, "start out of bounds");

    if (range > count - start)
        fail(cls, op, "range out of bounds");
#else
    (void)count;
#endif

    uint8_t* bytes = data + start;

#if defined(__aarch64__) || defined(_M_ARM64)
    uint32_t i = 0;

    for (; i + 16 <= range; i += 16) {
        const uint8x16_t v = vld1q_u8(bytes + i);
        vst1q_u8(bytes + i, vmvnq_u8(v));
    }

    for (; i < range; ++i) {
        bytes[i] = static_cast<uint8_t>(~bytes[i]);
    }
#elif defined(__x86_64__) || defined(_M_X64)
    uint32_t i = 0;
    const __m128i ones = _mm_set1_epi8(-1);

    for (; i + 16 <= range; i += 16) {
        const __m128i v = _mm_loadu_si128(reinterpret_cast<const __m128i*>(bytes + i));
        _mm_storeu_si128(reinterpret_cast<__m128i*>(bytes + i), _mm_xor_si128(v, ones));
    }

    for (; i < range; ++i) {
        bytes[i] = static_cast<uint8_t>(~bytes[i]);
    }
#else
    for (uint32_t i = 0; i < range; ++i) {
        bytes[i] = static_cast<uint8_t>(~bytes[i]);
    }
#endif
}

inline void memset_xor_impl(const char* cls, const char* op, uint8_t* data, uint32_t count, uint32_t start, uint32_t range, uint8_t value) {
    if (data == nullptr)
        fail(cls, op, "on uninitialized array");

#if !FRANKO_BOUNDS_UNCHECKED
    if (start > count)
        fail(cls, op, "start out of bounds");

    if (range > count - start)
        fail(cls, op, "range out of bounds");
#else
    (void)count;
#endif

    uint8_t* bytes = data + start;

#if defined(__aarch64__) || defined(_M_ARM64)
    uint32_t i = 0;
    const uint8x16_t fill = vdupq_n_u8(value);

    for (; i + 16 <= range; i += 16) {
        const uint8x16_t v = vld1q_u8(bytes + i);
        vst1q_u8(bytes + i, veorq_u8(v, fill));
    }

    for (; i < range; ++i) {
        bytes[i] ^= value;
    }
#elif defined(__x86_64__) || defined(_M_X64)
    uint32_t i = 0;
    const __m128i fill = _mm_set1_epi8(static_cast<char>(value));

    for (; i + 16 <= range; i += 16) {
        const __m128i v = _mm_loadu_si128(reinterpret_cast<const __m128i*>(bytes + i));
        _mm_storeu_si128(reinterpret_cast<__m128i*>(bytes + i), _mm_xor_si128(v, fill));
    }

    for (; i < range; ++i) {
        bytes[i] ^= value;
    }
#else
    for (uint32_t i = 0; i < range; ++i) {
        bytes[i] ^= value;
    }
#endif
}

inline void memset_and_impl(const char* cls, const char* op, uint8_t* data, uint32_t count, uint32_t start, uint32_t range, uint8_t value) {
    if (data == nullptr)
        fail(cls, op, "on uninitialized array");

#if !FRANKO_BOUNDS_UNCHECKED
    if (start > count)
        fail(cls, op, "start out of bounds");

    if (range > count - start)
        fail(cls, op, "range out of bounds");
#else
    (void)count;
#endif

    uint8_t* bytes = data + start;

#if defined(__aarch64__) || defined(_M_ARM64)
    uint32_t i = 0;
    const uint8x16_t fill = vdupq_n_u8(value);

    for (; i + 16 <= range; i += 16) {
        const uint8x16_t v = vld1q_u8(bytes + i);
        vst1q_u8(bytes + i, vandq_u8(v, fill));
    }

    for (; i < range; ++i) {
        bytes[i] &= value;
    }
#elif defined(__x86_64__) || defined(_M_X64)
    uint32_t i = 0;
    const __m128i fill = _mm_set1_epi8(static_cast<char>(value));

    for (; i + 16 <= range; i += 16) {
        const __m128i v = _mm_loadu_si128(reinterpret_cast<const __m128i*>(bytes + i));
        _mm_storeu_si128(reinterpret_cast<__m128i*>(bytes + i), _mm_and_si128(v, fill));
    }

    for (; i < range; ++i) {
        bytes[i] &= value;
    }
#else
    for (uint32_t i = 0; i < range; ++i) {
        bytes[i] &= value;
    }
#endif
}

inline void memset_or_impl(const char* cls, const char* op, uint8_t* data, uint32_t count, uint32_t start, uint32_t range, uint8_t value) {
    if (data == nullptr)
        fail(cls, op, "on uninitialized array");

#if !FRANKO_BOUNDS_UNCHECKED
    if (start > count)
        fail(cls, op, "start out of bounds");

    if (range > count - start)
        fail(cls, op, "range out of bounds");
#else
    (void)count;
#endif

    uint8_t* bytes = data + start;

#if defined(__aarch64__) || defined(_M_ARM64)
    uint32_t i = 0;
    const uint8x16_t fill = vdupq_n_u8(value);

    for (; i + 16 <= range; i += 16) {
        const uint8x16_t v = vld1q_u8(bytes + i);
        vst1q_u8(bytes + i, vorrq_u8(v, fill));
    }

    for (; i < range; ++i) {
        bytes[i] |= value;
    }
#elif defined(__x86_64__) || defined(_M_X64)
    uint32_t i = 0;
    const __m128i fill = _mm_set1_epi8(static_cast<char>(value));

    for (; i + 16 <= range; i += 16) {
        const __m128i v = _mm_loadu_si128(reinterpret_cast<const __m128i*>(bytes + i));
        _mm_storeu_si128(reinterpret_cast<__m128i*>(bytes + i), _mm_or_si128(v, fill));
    }

    for (; i < range; ++i) {
        bytes[i] |= value;
    }
#else
    for (uint32_t i = 0; i < range; ++i) {
        bytes[i] |= value;
    }
#endif
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
    // A 16-byte SIMD version needs a cross-lane carry byte; this word pass
    // already moves eight bytes per iteration and is memory-bound on large
    // arrays, so it is not clearly slower than the extra carry work.
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
    // The SIMD trade-off is the same as in shift_bits_left_impl: a vector
    // version needs a cross-lane carry byte, while this pass already moves
    // eight bytes per iteration.
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

inline uint64_t popcount_impl(const char* cls, const char* op, const uint8_t* data, uint32_t count) {
    if (data == nullptr)
        fail(cls, op, "on uninitialized array");

    uint64_t total = 0;

#if defined(__aarch64__) || defined(_M_ARM64)
    uint32_t i = 0;

    for (; i + 16 <= count; i += 16) {
        const uint8x16_t bits = vcntq_u8(vld1q_u8(data + i));
        total += vaddvq_u8(bits);
    }

    for (; i < count; ++i) {
        uint32_t v = data[i];
        v = v - ((v >> 1) & 0x55u);
        v = (v & 0x33u) + ((v >> 2) & 0x33u);
        v = (v + (v >> 4)) & 0x0Fu;
        total += v;
    }
#else
    for (uint32_t i = 0; i < count; ++i) {
        // Classic SWAR popcount of one byte, branch-free and
        // auto-vectorizable.
        uint32_t v = data[i];
        v = v - ((v >> 1) & 0x55u);
        v = (v & 0x33u) + ((v >> 2) & 0x33u);
        v = (v + (v >> 4)) & 0x0Fu;

        total += v;
    }
#endif

    return total;
}

} // namespace franko_byte_arrays

/*  this fat pointer is the C++ representation of pointers to arrays (static and dynamic). Pointers to scalars, structs, tuples, and other pointers use Franko_Thin_Pointer<T> and never use this type. Functions are done without check because the compiler should have already checked access permissions to functions.
    Example:
    Franko code:
    [int, 5] imstatic
    imstatic[0] = 1
    view<[int,5]> v = view(imstatic)
    $(v)[0] // value is 1
    $(v).memset(0) // Compilation error: it is illegal to memset a view, as view is read only, but the compiler should have already checked that, and the C++ backend doesn't know and doesn't care
    $(v).length() // should be compiled to constant 5
    view<[int]> decayedV = v
    // in Franko view<[int]> and view<[int,5]> are different types, but they essentially only has the same functions as they are both read only i.e. const [] access and length()
    [uint64] imdynamic
    imdynamic.init(10)
    imdynamic[0] = 2
    mview<[uint64]> v2 = mview(imdynamic) // mview is a mutable view, so it can call all mutable functions but not memory allocation functions, but this is checked by compiler
    $(v2).memset(0xFF) // legal
    C++ emission:
    int32_t VAR_imstatic[5]
    Franko_Fat_Pointer<int32_t> VAR_v = franko_make_fat_pointer(VAR_imstatic, 5, 2)
    VAR_v[0] // value is 1
    // nothing is emitted for the illegal memset, as it is a compile error
    5; // VAR_v.length() is an error
    Franko_Fat_Pointer<int32_t> VAR_decayedV = v // it changes type in Franko, but the C++ backend doesnt know and doesn't care
    Franko_Dynamic_Array<uint64_t> VAR_imdynamic
    VAR_imdynamic.init(10)
    VAR_imdynamic[0] = 2
    Franko_Fat_Pointer<uint64_t> VAR_v2 = franko_make_dynamic_fat_pointer(&VAR_imdynamic) // the fat pointer points to the dynamic array, and mode = 3 means it is a dynamic array, note count is 0 because the fat pointer doesn't know the length of the dynamic array, but it can call functions on the dynamic array
    VAR_v2.memset(0xFF) // legal, the fat pointer points to a dynamic array, so it can call all functions of dynamic array
    STRUCT_CustomStruct VAR_imstruct
    Franko_Thin_Pointer<STRUCT_CustomStruct> VAR_v3 = franko_make_thin_pointer(&VAR_imstruct) // non-array pointers use the thin wrapper
    (*VAR_v3.data).myfieldvar // this is how the field access is compiled

    Therefore, basically Franko_Fat_Pointer provides the union of the static-array and dynamic-array intrinsic surface,
    and the compiler should have already checked that the function is legal to call on the fat pointer, so the C++ backend doesn't need to check for legality of function calls.
    However, there is a quirk. the new Franko_Dynamic_Array and the new static array does not have full capability, and need to make a temporary fat pointer to call the functions that are lost
    Example:
    Franko code:
    [int, 5] imstatic
    imstatic.memset(0) // legal, but the new static array doesn't have memset, so the compiler should emit a temporary fat pointer to call the function
    [byte] imdynamic
    imdynamic.init(10)
    imdynamic.memset(0) // legal, but the new dynamic array doesn't have memset, so the compiler should emit a temporary fat pointer to call the function

    C++ emission:
    int32_t VAR_imstatic[5]
    franko_make_fat_pointer(VAR_imstatic, 5, 2).memset(0) // the temporary fat pointer is created to call the function
    Franko_Dynamic_Array<uint8_t> VAR_imdynamic
    VAR_imdynamic.init(10)
    franko_make_dynamic_fat_pointer(&VAR_imdynamic).memset(0) // the temporary fat pointer is created to call the function
 */
template <typename T>
struct Franko_Fat_Pointer {
    void* data = nullptr; // mode 2: first element. mode 3: the Franko_Dynamic_Array<T> object. mode 0: null
    uint32_t count = 0;
    uint8_t mode = 0; // 0 = null, 2 = static array, 3 = dynamic array (non-array pointers use Franko_Thin_Pointer<T>)

    Franko_Fat_Pointer() = default;

    constexpr Franko_Fat_Pointer(std::nullptr_t)
        : data(nullptr), count(0), mode(0) {
    }

    constexpr Franko_Fat_Pointer(T* elements, uint32_t elementCount, uint8_t pointerMode)
        : data(elements), count(elementCount), mode(pointerMode) {
    }

    uint32_t length() const {
        if (mode == 2)
            return count;
        else if (mode == 3)
            return static_cast<Franko_Dynamic_Array<T>*>(data)->count;
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer length() called on non-array");
    }

    // compiler checks dictates that this function will only be called on a static array or dynamic array
    const T& operator[](uint32_t index) const {
        if (mode == 2) {
#if !FRANKO_BOUNDS_UNCHECKED
            if (index >= count)
                franko_panic("Franko static array index out of bounds");
#endif
            return static_cast<T*>(data)[index];
        } else if (mode == 3) {
            if (static_cast<Franko_Dynamic_Array<T>*>(data)->data == nullptr)
                franko_panic("Franko dynamic array is not initialized");
#if !FRANKO_BOUNDS_UNCHECKED
            if (index >= static_cast<Franko_Dynamic_Array<T>*>(data)->count)
                franko_panic("Franko dynamic array index out of bounds");
#endif
            return static_cast<Franko_Dynamic_Array<T>*>(data)->data[index];
        } else
            franko_panic("Internal compiler error: Franko_Fat_Pointer operator[] called on non-array");

    }

    T& operator[](uint32_t index) {
        if (mode == 2) {
#if !FRANKO_BOUNDS_UNCHECKED
            if (index >= count)
                franko_panic("Franko static array index out of bounds");
#endif
            return static_cast<T*>(data)[index];
        } else if (mode == 3) {
            if (static_cast<Franko_Dynamic_Array<T>*>(data)->data == nullptr)
                franko_panic("Franko dynamic array is not initialized");
#if !FRANKO_BOUNDS_UNCHECKED
            if (index >= static_cast<Franko_Dynamic_Array<T>*>(data)->count)
                franko_panic("Franko dynamic array index out of bounds");
#endif
            return static_cast<Franko_Dynamic_Array<T>*>(data)->data[index];
        } else
            franko_panic("Internal compiler error: Franko_Fat_Pointer operator[] called on non-array");
    }

    void memset(uint8_t byteValue) {
        if (mode == 2) {
            if (count > 0)
                std::memset(static_cast<T*>(data), byteValue, sizeof(T) * static_cast<size_t>(count));
        }
        else if (mode == 3) {
            if (static_cast<Franko_Dynamic_Array<T>*>(data)->data == nullptr)
                franko_panic("Franko dynamic array is not initialized");
            if (static_cast<Franko_Dynamic_Array<T>*>(data)->count > 0)
                std::memset(static_cast<Franko_Dynamic_Array<T>*>(data)->data, byteValue, sizeof(T) * static_cast<size_t>(static_cast<Franko_Dynamic_Array<T>*>(data)->count));
        }
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer memset called on non-array");
    }

    void memset(uint8_t byteValue, uint32_t start, uint32_t elementCount) {
        if (mode == 2) {
#if !FRANKO_BOUNDS_UNCHECKED
            if (start > count)
                franko_panic("Franko static array memset start out of bounds");
            if (elementCount > count - start)
                franko_panic("Franko static array memset range out of bounds");
#endif
            if (elementCount > 0)
                std::memset(static_cast<T*>(data) + start, byteValue, sizeof(T) * static_cast<size_t>(elementCount));
        }
        else if (mode == 3) {
            if (static_cast<Franko_Dynamic_Array<T>*>(data)->data == nullptr)
                franko_panic("Franko dynamic array is not initialized");
#if !FRANKO_BOUNDS_UNCHECKED
            if (start > static_cast<Franko_Dynamic_Array<T>*>(data)->count)
                franko_panic("Franko dynamic array memset start out of bounds");
            if (elementCount > static_cast<Franko_Dynamic_Array<T>*>(data)->count - start)
                franko_panic("Franko dynamic array memset range out of bounds");
#endif
            if (elementCount > 0)
                std::memset(static_cast<Franko_Dynamic_Array<T>*>(data)->data + start, byteValue, sizeof(T) * static_cast<size_t>(elementCount));
        }
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer memset called on non-array");
    }

    void memcpy(const Franko_Fat_Pointer<T> other) {
        if (mode == 2 && other.mode == 2) {
            const uint32_t copyCount = (count < other.count) ? count : other.count;
            if (copyCount > 0)
                std::memcpy(static_cast<T*>(data), static_cast<T*>(other.data), sizeof(T) * static_cast<size_t>(copyCount));
        }
        else if (mode == 2 && other.mode == 3) {
            if (static_cast<Franko_Dynamic_Array<T>*>(other.data)->data == nullptr)
                franko_panic("Franko dynamic array source is not initialized");
            const uint32_t copyCount = (count < static_cast<Franko_Dynamic_Array<T>*>(other.data)->count) ? count : static_cast<Franko_Dynamic_Array<T>*>(other.data)->count;
            if (copyCount > 0)
                std::memcpy(static_cast<T*>(data), static_cast<Franko_Dynamic_Array<T>*>(other.data)->data, sizeof(T) * static_cast<size_t>(copyCount));
        }
        else if (mode == 3 && other.mode == 2) {
            if (static_cast<Franko_Dynamic_Array<T>*>(data)->data == nullptr)
                franko_panic("Franko dynamic array is not initialized");
            const uint32_t copyCount = (static_cast<Franko_Dynamic_Array<T>*>(data)->count < other.count) ? static_cast<Franko_Dynamic_Array<T>*>(data)->count : other.count;
            if (copyCount > 0)
                std::memcpy(static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<T*>(other.data), sizeof(T) * static_cast<size_t>(copyCount));
        }
        else if (mode == 3 && other.mode == 3) {
            if (static_cast<Franko_Dynamic_Array<T>*>(data)->data == nullptr)
                franko_panic("Franko dynamic array is not initialized");
            if (static_cast<Franko_Dynamic_Array<T>*>(other.data)->data == nullptr)
                franko_panic("Franko dynamic array source is not initialized");
            const uint32_t copyCount = (static_cast<Franko_Dynamic_Array<T>*>(data)->count < static_cast<Franko_Dynamic_Array<T>*>(other.data)->count) ? static_cast<Franko_Dynamic_Array<T>*>(data)->count : static_cast<Franko_Dynamic_Array<T>*>(other.data)->count;
            if (copyCount > 0)
                std::memcpy(static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<Franko_Dynamic_Array<T>*>(other.data)->data, sizeof(T) * static_cast<size_t>(copyCount));
        }
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer memcpy called on incompatible array types");
    }

    void memcpy(const Franko_Fat_Pointer<T> other, uint32_t dstStart, uint32_t srcStart, uint32_t elementCount) {
        if (mode == 2 && other.mode == 2) {
#if !FRANKO_BOUNDS_UNCHECKED
            if (dstStart > count)
                franko_panic("Franko static array memcpy destination start out of bounds");
            if (srcStart > other.count)
                franko_panic("Franko static array memcpy source start out of bounds");
            if (elementCount > count - dstStart)
                franko_panic("Franko static array memcpy destination range out of bounds");
            if (elementCount > other.count - srcStart)
                franko_panic("Franko static array memcpy source range out of bounds");
#endif
            if (elementCount > 0)
                std::memcpy(static_cast<T*>(data) + dstStart, static_cast<T*>(other.data) + srcStart, sizeof(T) * static_cast<size_t>(elementCount));
        }
        else if (mode == 2 && other.mode == 3) {
            if (static_cast<Franko_Dynamic_Array<T>*>(other.data)->data == nullptr)
                franko_panic("Franko dynamic array source is not initialized");
#if !FRANKO_BOUNDS_UNCHECKED
            if (dstStart > count)
                franko_panic("Franko static array memcpy destination start out of bounds");
            if (srcStart > static_cast<Franko_Dynamic_Array<T>*>(other.data)->count)
                franko_panic("Franko dynamic array memcpy source start out of bounds");
            if (elementCount > count - dstStart)
                franko_panic("Franko static array memcpy destination range out of bounds");
            if (elementCount > static_cast<Franko_Dynamic_Array<T>*>(other.data)->count - srcStart)
                franko_panic("Franko dynamic array memcpy source range out of bounds");
#endif
            if (elementCount > 0)
                std::memcpy(static_cast<T*>(data) + dstStart, static_cast<Franko_Dynamic_Array<T>*>(other.data)->data + srcStart, sizeof(T) * static_cast<size_t>(elementCount));
        }
        else if (mode == 3 && other.mode == 2) {
            if (static_cast<Franko_Dynamic_Array<T>*>(data)->data == nullptr)
                franko_panic("Franko dynamic array is not initialized");
#if !FRANKO_BOUNDS_UNCHECKED
            if (dstStart > static_cast<Franko_Dynamic_Array<T>*>(data)->count)
                franko_panic("Franko dynamic array memcpy destination start out of bounds");
            if (srcStart > other.count)
                franko_panic("Franko static array memcpy source start out of bounds");
            if (elementCount > static_cast<Franko_Dynamic_Array<T>*>(data)->count - dstStart)
                franko_panic("Franko dynamic array memcpy destination range out of bounds");
            if (elementCount > other.count - srcStart)
                franko_panic("Franko static array memcpy source range out of bounds");
#endif
            if (elementCount > 0)
                std::memcpy(static_cast<Franko_Dynamic_Array<T>*>(data)->data + dstStart, static_cast<T*>(other.data) + srcStart, sizeof(T) * static_cast<size_t>(elementCount));
        }
        else if (mode == 3 && other.mode == 3) {
            if (static_cast<Franko_Dynamic_Array<T>*>(data)->data == nullptr)
                franko_panic("Franko dynamic array is not initialized");
            if (static_cast<Franko_Dynamic_Array<T>*>(other.data)->data == nullptr)
                franko_panic("Franko dynamic array source is not initialized");
#if !FRANKO_BOUNDS_UNCHECKED
            if (dstStart > static_cast<Franko_Dynamic_Array<T>*>(data)->count)
                franko_panic("Franko dynamic array memcpy destination start out of bounds");
            if (srcStart > static_cast<Franko_Dynamic_Array<T>*>(other.data)->count)
                franko_panic("Franko dynamic array memcpy source start out of bounds");
            if (elementCount > static_cast<Franko_Dynamic_Array<T>*>(data)->count - dstStart)
                franko_panic("Franko dynamic array memcpy destination range out of bounds");
            if (elementCount > static_cast<Franko_Dynamic_Array<T>*>(other.data)->count - srcStart)
                franko_panic("Franko dynamic array memcpy source range out of bounds");
#endif
            if (elementCount > 0)
                std::memcpy(static_cast<Franko_Dynamic_Array<T>*>(data)->data + dstStart, static_cast<Franko_Dynamic_Array<T>*>(other.data)->data + srcStart, sizeof(T) * static_cast<size_t>(elementCount));
        }
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer memcpy called on incompatible array types");
    }

    void memmove(uint32_t dstStart, uint32_t srcStart, uint32_t elementCount) {
        if (mode == 2) {
#if !FRANKO_BOUNDS_UNCHECKED
            if (dstStart > count)
                franko_panic("Franko static array memmove destination start out of bounds");
            if (srcStart > count)
                franko_panic("Franko static array memmove source start out of bounds");
            if (elementCount > count - dstStart)
                franko_panic("Franko static array memmove destination range out of bounds");
            if (elementCount > count - srcStart)
                franko_panic("Franko static array memmove source range out of bounds");
#endif
            if (elementCount > 0)
                std::memmove(static_cast<T*>(data) + dstStart, static_cast<T*>(data) + srcStart, sizeof(T) * static_cast<size_t>(elementCount));
        }
        else if (mode == 3) {
            if (static_cast<Franko_Dynamic_Array<T>*>(data)->data == nullptr)
                franko_panic("Franko dynamic array is not initialized");
#if !FRANKO_BOUNDS_UNCHECKED
            if (dstStart > static_cast<Franko_Dynamic_Array<T>*>(data)->count)
                franko_panic("Franko dynamic array memmove destination start out of bounds");
            if (srcStart > static_cast<Franko_Dynamic_Array<T>*>(data)->count)
                franko_panic("Franko dynamic array memmove source start out of bounds");
            if (elementCount > static_cast<Franko_Dynamic_Array<T>*>(data)->count - dstStart)
                franko_panic("Franko dynamic array memmove destination range out of bounds");
            if (elementCount > static_cast<Franko_Dynamic_Array<T>*>(data)->count - srcStart)
                franko_panic("Franko dynamic array memmove source range out of bounds");
#endif
            if (elementCount > 0)
                std::memmove(static_cast<Franko_Dynamic_Array<T>*>(data)->data + dstStart, static_cast<Franko_Dynamic_Array<T>*>(data)->data + srcStart, sizeof(T) * static_cast<size_t>(elementCount));
        }
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer memmove called on non-array");
    }

    // memcmp: lexicographic raw-byte comparison, C semantics (the sign
    // of the first differing byte; only the sign is meaningful).
    // The compiler's memcpy-safe-element rule is the only gate. The
    // whole form is lexicographic across lengths: on an equal shared
    // prefix the shorter array is smaller. The ranged form compares
    // exactly `elementCount` elements.

    int32_t memcmp(const Franko_Fat_Pointer<T> other) const {
        if (mode == 2 && other.mode == 2) {
            const uint32_t shared = (count < other.count) ? count : other.count;
            if (shared > 0) {
                const int result = std::memcmp(static_cast<T*>(data), static_cast<T*>(other.data), sizeof(T) * static_cast<size_t>(shared));
                if (result != 0)
                    return result;
            }
            if (count < other.count)
                return -1;
            if (count > other.count)
                return 1;
            return 0;
        }
        else if (mode == 2 && other.mode == 3) {
            if (static_cast<Franko_Dynamic_Array<T>*>(other.data)->data == nullptr)
                franko_panic("Franko dynamic array source is not initialized");
            const uint32_t shared = (count < static_cast<Franko_Dynamic_Array<T>*>(other.data)->count) ? count : static_cast<Franko_Dynamic_Array<T>*>(other.data)->count;
            if (shared > 0) {
                const int result = std::memcmp(static_cast<T*>(data), static_cast<Franko_Dynamic_Array<T>*>(other.data)->data, sizeof(T) * static_cast<size_t>(shared));
                if (result != 0)
                    return result;
            }
            if (count < static_cast<Franko_Dynamic_Array<T>*>(other.data)->count)
                return -1;
            if (count > static_cast<Franko_Dynamic_Array<T>*>(other.data)->count)
                return 1;
            return 0;
        }
        else if (mode == 3 && other.mode == 2) {
            if (static_cast<Franko_Dynamic_Array<T>*>(data)->data == nullptr)
                franko_panic("Franko dynamic array is not initialized");
            const uint32_t shared = (static_cast<Franko_Dynamic_Array<T>*>(data)->count < other.count) ? static_cast<Franko_Dynamic_Array<T>*>(data)->count : other.count;
            if (shared > 0) {
                const int result = std::memcmp(static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<T*>(other.data), sizeof(T) * static_cast<size_t>(shared));
                if (result != 0)
                    return result;
            }
            if (static_cast<Franko_Dynamic_Array<T>*>(data)->count < other.count)
                return -1;
            if (static_cast<Franko_Dynamic_Array<T>*>(data)->count > other.count)
                return 1;
            return 0;
        }
        else if (mode == 3 && other.mode == 3) {
            if (static_cast<Franko_Dynamic_Array<T>*>(data)->data == nullptr)
                franko_panic("Franko dynamic array is not initialized");
            if (static_cast<Franko_Dynamic_Array<T>*>(other.data)->data == nullptr)
                franko_panic("Franko dynamic array source is not initialized");
            const uint32_t shared = (static_cast<Franko_Dynamic_Array<T>*>(data)->count < static_cast<Franko_Dynamic_Array<T>*>(other.data)->count) ? static_cast<Franko_Dynamic_Array<T>*>(data)->count : static_cast<Franko_Dynamic_Array<T>*>(other.data)->count;
            if (shared > 0) {
                const int result = std::memcmp(static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<Franko_Dynamic_Array<T>*>(other.data)->data, sizeof(T) * static_cast<size_t>(shared));
                if (result != 0)
                    return result;
            }
            if (static_cast<Franko_Dynamic_Array<T>*>(data)->count < static_cast<Franko_Dynamic_Array<T>*>(other.data)->count)
                return -1;
            if (static_cast<Franko_Dynamic_Array<T>*>(data)->count > static_cast<Franko_Dynamic_Array<T>*>(other.data)->count)
                return 1;
            return 0;
        }
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer memcmp called on incompatible array types");
    }

    int32_t memcmp(const Franko_Fat_Pointer<T> other, uint32_t dstStart, uint32_t srcStart, uint32_t elementCount) const {
        if (mode == 2 && other.mode == 2) {
#if !FRANKO_BOUNDS_UNCHECKED
            if (dstStart > count)
                franko_panic("Franko static array memcmp destination start out of bounds");
            if (srcStart > other.count)
                franko_panic("Franko static array memcmp source start out of bounds");
            if (elementCount > count - dstStart)
                franko_panic("Franko static array memcmp destination range out of bounds");
            if (elementCount > other.count - srcStart)
                franko_panic("Franko static array memcmp source range out of bounds");
#endif
            if (elementCount > 0)
                return std::memcmp(static_cast<T*>(data) + dstStart, static_cast<T*>(other.data) + srcStart, sizeof(T) * static_cast<size_t>(elementCount));
            return 0;
        }
        else if (mode == 2 && other.mode == 3) {
            if (static_cast<Franko_Dynamic_Array<T>*>(other.data)->data == nullptr)
                franko_panic("Franko dynamic array source is not initialized");
#if !FRANKO_BOUNDS_UNCHECKED
            if (dstStart > count)
                franko_panic("Franko static array memcmp destination start out of bounds");
            if (srcStart > static_cast<Franko_Dynamic_Array<T>*>(other.data)->count)
                franko_panic("Franko dynamic array memcmp source start out of bounds");
            if (elementCount > count - dstStart)
                franko_panic("Franko static array memcmp destination range out of bounds");
            if (elementCount > static_cast<Franko_Dynamic_Array<T>*>(other.data)->count - srcStart)
                franko_panic("Franko dynamic array memcmp source range out of bounds");
#endif
            if (elementCount > 0)
                return std::memcmp(static_cast<T*>(data) + dstStart, static_cast<Franko_Dynamic_Array<T>*>(other.data)->data + srcStart, sizeof(T) * static_cast<size_t>(elementCount));
            return 0;
        }
        else if (mode == 3 && other.mode == 2) {
            if (static_cast<Franko_Dynamic_Array<T>*>(data)->data == nullptr)
                franko_panic("Franko dynamic array is not initialized");
#if !FRANKO_BOUNDS_UNCHECKED
            if (dstStart > static_cast<Franko_Dynamic_Array<T>*>(data)->count)
                franko_panic("Franko dynamic array memcmp destination start out of bounds");
            if (srcStart > other.count)
                franko_panic("Franko static array memcmp source start out of bounds");
            if (elementCount > static_cast<Franko_Dynamic_Array<T>*>(data)->count - dstStart)
                franko_panic("Franko dynamic array memcmp destination range out of bounds");
            if (elementCount > other.count - srcStart)
                franko_panic("Franko static array memcmp source range out of bounds");
#endif
            if (elementCount > 0)
                return std::memcmp(static_cast<Franko_Dynamic_Array<T>*>(data)->data + dstStart, static_cast<T*>(other.data) + srcStart, sizeof(T) * static_cast<size_t>(elementCount));
            return 0;
        }
        else if (mode == 3 && other.mode == 3) {
            if (static_cast<Franko_Dynamic_Array<T>*>(data)->data == nullptr)
                franko_panic("Franko dynamic array is not initialized");
            if (static_cast<Franko_Dynamic_Array<T>*>(other.data)->data == nullptr)
                franko_panic("Franko dynamic array source is not initialized");
#if !FRANKO_BOUNDS_UNCHECKED
            if (dstStart > static_cast<Franko_Dynamic_Array<T>*>(data)->count)
                franko_panic("Franko dynamic array memcmp destination start out of bounds");
            if (srcStart > static_cast<Franko_Dynamic_Array<T>*>(other.data)->count)
                franko_panic("Franko dynamic array memcmp source start out of bounds");
            if (elementCount > static_cast<Franko_Dynamic_Array<T>*>(data)->count - dstStart)
                franko_panic("Franko dynamic array memcmp destination range out of bounds");
            if (elementCount > static_cast<Franko_Dynamic_Array<T>*>(other.data)->count - srcStart)
                franko_panic("Franko dynamic array memcmp source range out of bounds");
#endif
            if (elementCount > 0)
                return std::memcmp(static_cast<Franko_Dynamic_Array<T>*>(data)->data + dstStart, static_cast<Franko_Dynamic_Array<T>*>(other.data)->data + srcStart, sizeof(T) * static_cast<size_t>(elementCount));
            return 0;
        }
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer memcmp called on incompatible array types");
    }

    // ==========================================================
    // Byte-array and bit intrinsics (see franko_byte_arrays above).
    // The compiler only emits these for byte (uint8_t) arrays.
    // ==========================================================

    uint32_t memchr(uint8_t value) const {
        if (mode == 2)
            return franko_byte_arrays::memchr_impl("Franko_Fat_Pointer", "memchr", static_cast<T*>(data), count, 0, count, value);
        else if (mode == 3)
            return franko_byte_arrays::memchr_impl("Franko_Fat_Pointer", "memchr", static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<Franko_Dynamic_Array<T>*>(data)->count, 0, static_cast<Franko_Dynamic_Array<T>*>(data)->count, value);
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer memchr called on non-array");
    }

    uint32_t memchr(uint8_t value, uint32_t start, uint32_t elementCount) const {
        if (mode == 2)
            return franko_byte_arrays::memchr_impl("Franko_Fat_Pointer", "memchr", static_cast<T*>(data), count, start, elementCount, value);
        else if (mode == 3)
            return franko_byte_arrays::memchr_impl("Franko_Fat_Pointer", "memchr", static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<Franko_Dynamic_Array<T>*>(data)->count, start, elementCount, value);
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer memchr called on non-array");
    }

    uint32_t memmem(const Franko_Fat_Pointer<T> needle) const {
        if (needle.mode == 0)
            franko_byte_arrays::fail("Franko_Fat_Pointer", "memmem", "source pointer is null");
        if (mode == 2) {
            if (needle.mode == 2)
                return franko_byte_arrays::memmem_impl("Franko_Fat_Pointer", "memmem", static_cast<T*>(data), count, 0, count, static_cast<T*>(needle.data), needle.count);
            else if (needle.mode == 3)
                return franko_byte_arrays::memmem_impl("Franko_Fat_Pointer", "memmem", static_cast<T*>(data), count, 0, count, static_cast<Franko_Dynamic_Array<T>*>(needle.data)->data, static_cast<Franko_Dynamic_Array<T>*>(needle.data)->count);
        } else if (mode == 3) {
            if (needle.mode == 2)
                return franko_byte_arrays::memmem_impl("Franko_Fat_Pointer", "memmem", static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<Franko_Dynamic_Array<T>*>(data)->count, 0, static_cast<Franko_Dynamic_Array<T>*>(data)->count, static_cast<T*>(needle.data), needle.count);
            else if (needle.mode == 3)
                return franko_byte_arrays::memmem_impl("Franko_Fat_Pointer", "memmem", static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<Franko_Dynamic_Array<T>*>(data)->count, 0, static_cast<Franko_Dynamic_Array<T>*>(data)->count, static_cast<Franko_Dynamic_Array<T>*>(needle.data)->data, static_cast<Franko_Dynamic_Array<T>*>(needle.data)->count);
        }
        franko_panic("Internal compiler error: Franko_Fat_Pointer memmem called on non-array");
    }

    uint32_t memmem(const Franko_Fat_Pointer<T> needle, uint32_t start, uint32_t elementCount) const {
        if (needle.mode == 0)
            franko_byte_arrays::fail("Franko_Fat_Pointer", "memmem", "source pointer is null");
        if (mode == 2) {
            if (needle.mode == 2)
                return franko_byte_arrays::memmem_impl("Franko_Fat_Pointer", "memmem", static_cast<T*>(data), count, start, elementCount, static_cast<T*>(needle.data), needle.count);
            else if (needle.mode == 3)
                return franko_byte_arrays::memmem_impl("Franko_Fat_Pointer", "memmem", static_cast<T*>(data), count, start, elementCount, static_cast<Franko_Dynamic_Array<T>*>(needle.data)->data, static_cast<Franko_Dynamic_Array<T>*>(needle.data)->count);
        } else if (mode == 3) {
            if (needle.mode == 2)
                return franko_byte_arrays::memmem_impl("Franko_Fat_Pointer", "memmem", static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<Franko_Dynamic_Array<T>*>(data)->count, start, elementCount, static_cast<T*>(needle.data), needle.count);
            else if (needle.mode == 3)
                return franko_byte_arrays::memmem_impl("Franko_Fat_Pointer", "memmem", static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<Franko_Dynamic_Array<T>*>(data)->count, start, elementCount, static_cast<Franko_Dynamic_Array<T>*>(needle.data)->data, static_cast<Franko_Dynamic_Array<T>*>(needle.data)->count);
        }
        franko_panic("Internal compiler error: Franko_Fat_Pointer memmem called on non-array");
    }

    uint16_t read_uint16_LE(uint32_t offset) const {
        if (mode == 2)
            return franko_byte_arrays::read_int_impl<uint16_t, true>("Franko_Fat_Pointer", "read_uint16_LE", static_cast<T*>(data), count, offset);
        else if (mode == 3)
            return franko_byte_arrays::read_int_impl<uint16_t, true>("Franko_Fat_Pointer", "read_uint16_LE", static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<Franko_Dynamic_Array<T>*>(data)->count, offset);
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer read_uint16_LE called on non-array");
    }

    uint16_t read_uint16_BE(uint32_t offset) const {
        if (mode == 2)
            return franko_byte_arrays::read_int_impl<uint16_t, false>("Franko_Fat_Pointer", "read_uint16_BE", static_cast<T*>(data), count, offset);
        else if (mode == 3)
            return franko_byte_arrays::read_int_impl<uint16_t, false>("Franko_Fat_Pointer", "read_uint16_BE", static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<Franko_Dynamic_Array<T>*>(data)->count, offset);
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer read_uint16_BE called on non-array");
    }

    void write_uint16_LE(uint32_t offset, uint16_t value) {
        if (mode == 2)
            franko_byte_arrays::write_int_impl<uint16_t, true>("Franko_Fat_Pointer", "write_uint16_LE", static_cast<T*>(data), count, offset, value);
        else if (mode == 3)
            franko_byte_arrays::write_int_impl<uint16_t, true>("Franko_Fat_Pointer", "write_uint16_LE", static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<Franko_Dynamic_Array<T>*>(data)->count, offset, value);
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer write_uint16_LE called on non-array");
    }

    void write_uint16_BE(uint32_t offset, uint16_t value) {
        if (mode == 2)
            franko_byte_arrays::write_int_impl<uint16_t, false>("Franko_Fat_Pointer", "write_uint16_BE", static_cast<T*>(data), count, offset, value);
        else if (mode == 3)
            franko_byte_arrays::write_int_impl<uint16_t, false>("Franko_Fat_Pointer", "write_uint16_BE", static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<Franko_Dynamic_Array<T>*>(data)->count, offset, value);
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer write_uint16_BE called on non-array");
    }

    uint32_t read_uint32_LE(uint32_t offset) const {
        if (mode == 2)
            return franko_byte_arrays::read_int_impl<uint32_t, true>("Franko_Fat_Pointer", "read_uint32_LE", static_cast<T*>(data), count, offset);
        else if (mode == 3)
            return franko_byte_arrays::read_int_impl<uint32_t, true>("Franko_Fat_Pointer", "read_uint32_LE", static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<Franko_Dynamic_Array<T>*>(data)->count, offset);
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer read_uint32_LE called on non-array");
    }

    uint32_t read_uint32_BE(uint32_t offset) const {
        if (mode == 2)
            return franko_byte_arrays::read_int_impl<uint32_t, false>("Franko_Fat_Pointer", "read_uint32_BE", static_cast<T*>(data), count, offset);
        else if (mode == 3)
            return franko_byte_arrays::read_int_impl<uint32_t, false>("Franko_Fat_Pointer", "read_uint32_BE", static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<Franko_Dynamic_Array<T>*>(data)->count, offset);
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer read_uint32_BE called on non-array");
    }

    void write_uint32_LE(uint32_t offset, uint32_t value) {
        if (mode == 2)
            franko_byte_arrays::write_int_impl<uint32_t, true>("Franko_Fat_Pointer", "write_uint32_LE", static_cast<T*>(data), count, offset, value);
        else if (mode == 3)
            franko_byte_arrays::write_int_impl<uint32_t, true>("Franko_Fat_Pointer", "write_uint32_LE", static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<Franko_Dynamic_Array<T>*>(data)->count, offset, value);
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer write_uint32_LE called on non-array");
    }

    void write_uint32_BE(uint32_t offset, uint32_t value) {
        if (mode == 2)
            franko_byte_arrays::write_int_impl<uint32_t, false>("Franko_Fat_Pointer", "write_uint32_BE", static_cast<T*>(data), count, offset, value);
        else if (mode == 3)
            franko_byte_arrays::write_int_impl<uint32_t, false>("Franko_Fat_Pointer", "write_uint32_BE", static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<Franko_Dynamic_Array<T>*>(data)->count, offset, value);
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer write_uint32_BE called on non-array");
    }

    uint64_t read_uint64_LE(uint32_t offset) const {
        if (mode == 2)
            return franko_byte_arrays::read_int_impl<uint64_t, true>("Franko_Fat_Pointer", "read_uint64_LE", static_cast<T*>(data), count, offset);
        else if (mode == 3)
            return franko_byte_arrays::read_int_impl<uint64_t, true>("Franko_Fat_Pointer", "read_uint64_LE", static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<Franko_Dynamic_Array<T>*>(data)->count, offset);
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer read_uint64_LE called on non-array");
    }

    uint64_t read_uint64_BE(uint32_t offset) const {
        if (mode == 2)
            return franko_byte_arrays::read_int_impl<uint64_t, false>("Franko_Fat_Pointer", "read_uint64_BE", static_cast<T*>(data), count, offset);
        else if (mode == 3)
            return franko_byte_arrays::read_int_impl<uint64_t, false>("Franko_Fat_Pointer", "read_uint64_BE", static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<Franko_Dynamic_Array<T>*>(data)->count, offset);
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer read_uint64_BE called on non-array");
    }

    void write_uint64_LE(uint32_t offset, uint64_t value) {
        if (mode == 2)
            franko_byte_arrays::write_int_impl<uint64_t, true>("Franko_Fat_Pointer", "write_uint64_LE", static_cast<T*>(data), count, offset, value);
        else if (mode == 3)
            franko_byte_arrays::write_int_impl<uint64_t, true>("Franko_Fat_Pointer", "write_uint64_LE", static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<Franko_Dynamic_Array<T>*>(data)->count, offset, value);
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer write_uint64_LE called on non-array");
    }

    void write_uint64_BE(uint32_t offset, uint64_t value) {
        if (mode == 2)
            franko_byte_arrays::write_int_impl<uint64_t, false>("Franko_Fat_Pointer", "write_uint64_BE", static_cast<T*>(data), count, offset, value);
        else if (mode == 3)
            franko_byte_arrays::write_int_impl<uint64_t, false>("Franko_Fat_Pointer", "write_uint64_BE", static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<Franko_Dynamic_Array<T>*>(data)->count, offset, value);
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer write_uint64_BE called on non-array");
    }

    int16_t read_int16_LE(uint32_t offset) const {
        if (mode == 2)
            return franko_byte_arrays::read_int_impl<int16_t, true>("Franko_Fat_Pointer", "read_int16_LE", static_cast<T*>(data), count, offset);
        else if (mode == 3)
            return franko_byte_arrays::read_int_impl<int16_t, true>("Franko_Fat_Pointer", "read_int16_LE", static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<Franko_Dynamic_Array<T>*>(data)->count, offset);
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer read_int16_LE called on non-array");
    }

    int16_t read_int16_BE(uint32_t offset) const {
        if (mode == 2)
            return franko_byte_arrays::read_int_impl<int16_t, false>("Franko_Fat_Pointer", "read_int16_BE", static_cast<T*>(data), count, offset);
        else if (mode == 3)
            return franko_byte_arrays::read_int_impl<int16_t, false>("Franko_Fat_Pointer", "read_int16_BE", static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<Franko_Dynamic_Array<T>*>(data)->count, offset);
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer read_int16_BE called on non-array");
    }

    void write_int16_LE(uint32_t offset, int16_t value) {
        if (mode == 2)
            franko_byte_arrays::write_int_impl<int16_t, true>("Franko_Fat_Pointer", "write_int16_LE", static_cast<T*>(data), count, offset, value);
        else if (mode == 3)
            franko_byte_arrays::write_int_impl<int16_t, true>("Franko_Fat_Pointer", "write_int16_LE", static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<Franko_Dynamic_Array<T>*>(data)->count, offset, value);
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer write_int16_LE called on non-array");
    }

    void write_int16_BE(uint32_t offset, int16_t value) {
        if (mode == 2)
            franko_byte_arrays::write_int_impl<int16_t, false>("Franko_Fat_Pointer", "write_int16_BE", static_cast<T*>(data), count, offset, value);
        else if (mode == 3)
            franko_byte_arrays::write_int_impl<int16_t, false>("Franko_Fat_Pointer", "write_int16_BE", static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<Franko_Dynamic_Array<T>*>(data)->count, offset, value);
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer write_int16_BE called on non-array");
    }

    int32_t read_int32_LE(uint32_t offset) const {
        if (mode == 2)
            return franko_byte_arrays::read_int_impl<int32_t, true>("Franko_Fat_Pointer", "read_int32_LE", static_cast<T*>(data), count, offset);
        else if (mode == 3)
            return franko_byte_arrays::read_int_impl<int32_t, true>("Franko_Fat_Pointer", "read_int32_LE", static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<Franko_Dynamic_Array<T>*>(data)->count, offset);
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer read_int32_LE called on non-array");
    }

    int32_t read_int32_BE(uint32_t offset) const {
        if (mode == 2)
            return franko_byte_arrays::read_int_impl<int32_t, false>("Franko_Fat_Pointer", "read_int32_BE", static_cast<T*>(data), count, offset);
        else if (mode == 3)
            return franko_byte_arrays::read_int_impl<int32_t, false>("Franko_Fat_Pointer", "read_int32_BE", static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<Franko_Dynamic_Array<T>*>(data)->count, offset);
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer read_int32_BE called on non-array");
    }

    void write_int32_LE(uint32_t offset, int32_t value) {
        if (mode == 2)
            franko_byte_arrays::write_int_impl<int32_t, true>("Franko_Fat_Pointer", "write_int32_LE", static_cast<T*>(data), count, offset, value);
        else if (mode == 3)
            franko_byte_arrays::write_int_impl<int32_t, true>("Franko_Fat_Pointer", "write_int32_LE", static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<Franko_Dynamic_Array<T>*>(data)->count, offset, value);
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer write_int32_LE called on non-array");
    }

    void write_int32_BE(uint32_t offset, int32_t value) {
        if (mode == 2)
            franko_byte_arrays::write_int_impl<int32_t, false>("Franko_Fat_Pointer", "write_int32_BE", static_cast<T*>(data), count, offset, value);
        else if (mode == 3)
            franko_byte_arrays::write_int_impl<int32_t, false>("Franko_Fat_Pointer", "write_int32_BE", static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<Franko_Dynamic_Array<T>*>(data)->count, offset, value);
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer write_int32_BE called on non-array");
    }

    int64_t read_int64_LE(uint32_t offset) const {
        if (mode == 2)
            return franko_byte_arrays::read_int_impl<int64_t, true>("Franko_Fat_Pointer", "read_int64_LE", static_cast<T*>(data), count, offset);
        else if (mode == 3)
            return franko_byte_arrays::read_int_impl<int64_t, true>("Franko_Fat_Pointer", "read_int64_LE", static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<Franko_Dynamic_Array<T>*>(data)->count, offset);
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer read_int64_LE called on non-array");
    }

    int64_t read_int64_BE(uint32_t offset) const {
        if (mode == 2)
            return franko_byte_arrays::read_int_impl<int64_t, false>("Franko_Fat_Pointer", "read_int64_BE", static_cast<T*>(data), count, offset);
        else if (mode == 3)
            return franko_byte_arrays::read_int_impl<int64_t, false>("Franko_Fat_Pointer", "read_int64_BE", static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<Franko_Dynamic_Array<T>*>(data)->count, offset);
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer read_int64_BE called on non-array");
    }

    void write_int64_LE(uint32_t offset, int64_t value) {
        if (mode == 2)
            franko_byte_arrays::write_int_impl<int64_t, true>("Franko_Fat_Pointer", "write_int64_LE", static_cast<T*>(data), count, offset, value);
        else if (mode == 3)
            franko_byte_arrays::write_int_impl<int64_t, true>("Franko_Fat_Pointer", "write_int64_LE", static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<Franko_Dynamic_Array<T>*>(data)->count, offset, value);
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer write_int64_LE called on non-array");
    }

    void write_int64_BE(uint32_t offset, int64_t value) {
        if (mode == 2)
            franko_byte_arrays::write_int_impl<int64_t, false>("Franko_Fat_Pointer", "write_int64_BE", static_cast<T*>(data), count, offset, value);
        else if (mode == 3)
            franko_byte_arrays::write_int_impl<int64_t, false>("Franko_Fat_Pointer", "write_int64_BE", static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<Franko_Dynamic_Array<T>*>(data)->count, offset, value);
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer write_int64_BE called on non-array");
    }

    uint8_t read_bit(uint64_t bitIndex) const {
        if (mode == 2)
            return franko_byte_arrays::read_bit_impl("Franko_Fat_Pointer", "read_bit", static_cast<T*>(data), count, bitIndex);
        else if (mode == 3)
            return franko_byte_arrays::read_bit_impl("Franko_Fat_Pointer", "read_bit", static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<Franko_Dynamic_Array<T>*>(data)->count, bitIndex);
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer read_bit called on non-array");
    }

    void write_bit(uint64_t bitIndex, uint8_t value) {
        if (mode == 2)
            franko_byte_arrays::write_bit_impl("Franko_Fat_Pointer", "write_bit", static_cast<T*>(data), count, bitIndex, value);
        else if (mode == 3)
            franko_byte_arrays::write_bit_impl("Franko_Fat_Pointer", "write_bit", static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<Franko_Dynamic_Array<T>*>(data)->count, bitIndex, value);
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer write_bit called on non-array");
    }

    void flip_bit(uint64_t bitIndex) {
        if (mode == 2)
            franko_byte_arrays::flip_bit_impl("Franko_Fat_Pointer", "flip_bit", static_cast<T*>(data), count, bitIndex);
        else if (mode == 3)
            franko_byte_arrays::flip_bit_impl("Franko_Fat_Pointer", "flip_bit", static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<Franko_Dynamic_Array<T>*>(data)->count, bitIndex);
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer flip_bit called on non-array");
    }

    void memset_not() {
        if (mode == 2)
            franko_byte_arrays::memset_not_impl("Franko_Fat_Pointer", "memset_not", static_cast<T*>(data), count, 0, count);
        else if (mode == 3)
            franko_byte_arrays::memset_not_impl("Franko_Fat_Pointer", "memset_not", static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<Franko_Dynamic_Array<T>*>(data)->count, 0, static_cast<Franko_Dynamic_Array<T>*>(data)->count);
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer memset_not called on non-array");
    }

    void memset_not(uint32_t start, uint32_t elementCount) {
        if (mode == 2)
            franko_byte_arrays::memset_not_impl("Franko_Fat_Pointer", "memset_not", static_cast<T*>(data), count, start, elementCount);
        else if (mode == 3)
            franko_byte_arrays::memset_not_impl("Franko_Fat_Pointer", "memset_not", static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<Franko_Dynamic_Array<T>*>(data)->count, start, elementCount);
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer memset_not called on non-array");
    }

    void memset_xor(uint8_t value) {
        if (mode == 2)
            franko_byte_arrays::memset_xor_impl("Franko_Fat_Pointer", "memset_xor", static_cast<T*>(data), count, 0, count, value);
        else if (mode == 3)
            franko_byte_arrays::memset_xor_impl("Franko_Fat_Pointer", "memset_xor", static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<Franko_Dynamic_Array<T>*>(data)->count, 0, static_cast<Franko_Dynamic_Array<T>*>(data)->count, value);
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer memset_xor called on non-array");
    }

    void memset_xor(uint8_t value, uint32_t start, uint32_t elementCount) {
        if (mode == 2)
            franko_byte_arrays::memset_xor_impl("Franko_Fat_Pointer", "memset_xor", static_cast<T*>(data), count, start, elementCount, value);
        else if (mode == 3)
            franko_byte_arrays::memset_xor_impl("Franko_Fat_Pointer", "memset_xor", static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<Franko_Dynamic_Array<T>*>(data)->count, start, elementCount, value);
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer memset_xor called on non-array");
    }

    void memset_and(uint8_t value) {
        if (mode == 2)
            franko_byte_arrays::memset_and_impl("Franko_Fat_Pointer", "memset_and", static_cast<T*>(data), count, 0, count, value);
        else if (mode == 3)
            franko_byte_arrays::memset_and_impl("Franko_Fat_Pointer", "memset_and", static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<Franko_Dynamic_Array<T>*>(data)->count, 0, static_cast<Franko_Dynamic_Array<T>*>(data)->count, value);
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer memset_and called on non-array");
    }

    void memset_and(uint8_t value, uint32_t start, uint32_t elementCount) {
        if (mode == 2)
            franko_byte_arrays::memset_and_impl("Franko_Fat_Pointer", "memset_and", static_cast<T*>(data), count, start, elementCount, value);
        else if (mode == 3)
            franko_byte_arrays::memset_and_impl("Franko_Fat_Pointer", "memset_and", static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<Franko_Dynamic_Array<T>*>(data)->count, start, elementCount, value);
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer memset_and called on non-array");
    }

    void memset_or(uint8_t value) {
        if (mode == 2)
            franko_byte_arrays::memset_or_impl("Franko_Fat_Pointer", "memset_or", static_cast<T*>(data), count, 0, count, value);
        else if (mode == 3)
            franko_byte_arrays::memset_or_impl("Franko_Fat_Pointer", "memset_or", static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<Franko_Dynamic_Array<T>*>(data)->count, 0, static_cast<Franko_Dynamic_Array<T>*>(data)->count, value);
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer memset_or called on non-array");
    }

    void memset_or(uint8_t value, uint32_t start, uint32_t elementCount) {
        if (mode == 2)
            franko_byte_arrays::memset_or_impl("Franko_Fat_Pointer", "memset_or", static_cast<T*>(data), count, start, elementCount, value);
        else if (mode == 3)
            franko_byte_arrays::memset_or_impl("Franko_Fat_Pointer", "memset_or", static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<Franko_Dynamic_Array<T>*>(data)->count, start, elementCount, value);
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer memset_or called on non-array");
    }

    void swap_bits(uint64_t firstBit, uint64_t secondBit) {
        if (mode == 2)
            franko_byte_arrays::swap_bits_impl("Franko_Fat_Pointer", "swap_bits", static_cast<T*>(data), count, firstBit, secondBit);
        else if (mode == 3)
            franko_byte_arrays::swap_bits_impl("Franko_Fat_Pointer", "swap_bits", static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<Franko_Dynamic_Array<T>*>(data)->count, firstBit, secondBit);
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer swap_bits called on non-array");
    }

    void reverse_bits(uint64_t startBit, uint64_t bitCount) {
        if (mode == 2)
            franko_byte_arrays::reverse_bits_impl("Franko_Fat_Pointer", "reverse_bits", static_cast<T*>(data), count, startBit, bitCount);
        else if (mode == 3)
            franko_byte_arrays::reverse_bits_impl("Franko_Fat_Pointer", "reverse_bits", static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<Franko_Dynamic_Array<T>*>(data)->count, startBit, bitCount);
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer reverse_bits called on non-array");
    }

    uint64_t count_trailing_zeros(uint64_t bitIndex) const {
        if (mode == 2)
            return franko_byte_arrays::count_trailing_zeros_impl("Franko_Fat_Pointer", "count_trailing_zeros", static_cast<T*>(data), count, bitIndex);
        else if (mode == 3)
            return franko_byte_arrays::count_trailing_zeros_impl("Franko_Fat_Pointer", "count_trailing_zeros", static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<Franko_Dynamic_Array<T>*>(data)->count, bitIndex);
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer count_trailing_zeros called on non-array");
    }

    uint64_t count_leading_zeros(uint64_t bitIndex) const {
        if (mode == 2)
            return franko_byte_arrays::count_leading_zeros_impl("Franko_Fat_Pointer", "count_leading_zeros", static_cast<T*>(data), count, bitIndex);
        else if (mode == 3)
            return franko_byte_arrays::count_leading_zeros_impl("Franko_Fat_Pointer", "count_leading_zeros", static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<Franko_Dynamic_Array<T>*>(data)->count, bitIndex);
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer count_leading_zeros called on non-array");
    }

    void shift_bits_left(uint64_t bitCount) {
        if (mode == 2)
            franko_byte_arrays::shift_bits_left_impl("Franko_Fat_Pointer", "shift_bits_left", static_cast<T*>(data), count, bitCount);
        else if (mode == 3)
            franko_byte_arrays::shift_bits_left_impl("Franko_Fat_Pointer", "shift_bits_left", static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<Franko_Dynamic_Array<T>*>(data)->count, bitCount);
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer shift_bits_left called on non-array");
    }

    void shift_bits_right(uint64_t bitCount) {
        if (mode == 2)
            franko_byte_arrays::shift_bits_right_impl("Franko_Fat_Pointer", "shift_bits_right", static_cast<T*>(data), count, bitCount);
        else if (mode == 3)
            franko_byte_arrays::shift_bits_right_impl("Franko_Fat_Pointer", "shift_bits_right", static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<Franko_Dynamic_Array<T>*>(data)->count, bitCount);
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer shift_bits_right called on non-array");
    }

    uint64_t popcount() const {
        if (mode == 2)
            return franko_byte_arrays::popcount_impl("Franko_Fat_Pointer", "popcount", static_cast<T*>(data), count);
        else if (mode == 3)
            return franko_byte_arrays::popcount_impl("Franko_Fat_Pointer", "popcount", static_cast<Franko_Dynamic_Array<T>*>(data)->data, static_cast<Franko_Dynamic_Array<T>*>(data)->count);
        else
            franko_panic("Internal compiler error: Franko_Fat_Pointer popcount called on non-array");
    }
};

// Pointer identity is address identity. Count/mode are derived from the
// pointee and never distinguish two pointers to the same storage.
template <typename T>
constexpr bool operator==(const Franko_Fat_Pointer<T>& left, const Franko_Fat_Pointer<T>& right) {
    return left.data == right.data;
}

template <typename T>
constexpr bool operator!=(const Franko_Fat_Pointer<T>& left, const Franko_Fat_Pointer<T>& right) {
    return !(left == right);
}

template <typename T>
constexpr bool operator==(const Franko_Fat_Pointer<T>& pointer, std::nullptr_t) {
    return pointer.data == nullptr;
}

template <typename T>
constexpr bool operator!=(const Franko_Fat_Pointer<T>& pointer, std::nullptr_t) {
    return pointer.data != nullptr;
}

template <typename T>
constexpr bool operator==(std::nullptr_t, const Franko_Fat_Pointer<T>& pointer) {
    return pointer.data == nullptr;
}

template <typename T>
constexpr bool operator!=(std::nullptr_t, const Franko_Fat_Pointer<T>& pointer) {
    return pointer.data != nullptr;
}

template <typename T>
constexpr Franko_Fat_Pointer<T> franko_make_fat_pointer(T* data, uint32_t count, uint8_t mode) {
    return Franko_Fat_Pointer<T>(data, count, mode);
}

template <typename T>
constexpr Franko_Fat_Pointer<T> franko_make_fat_pointer(const T* data, uint32_t count, uint8_t mode) {
    return Franko_Fat_Pointer<T>(const_cast<T*>(data), count, mode);
}

// Mode 3 must have its own name: a pointer to a Franko_Dynamic_Array<T>
// is indistinguishable (after array-to-pointer decay) from a static
// array whose element type is Franko_Dynamic_Array<T>.
template <typename T>
Franko_Fat_Pointer<T> franko_make_dynamic_fat_pointer(Franko_Dynamic_Array<T>* array) {
    Franko_Fat_Pointer<T> fp;
    fp.data = array;
    fp.count = 0;
    fp.mode = 3;
    return fp;
}
