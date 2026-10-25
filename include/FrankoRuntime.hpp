#pragma once

#include <cstddef>   // for size_t
#include <cstdint>   // for uint8_t, uint32_t, int32_t
#include <cstring>   // for std::memcpy, std::memset, std::memmove
#include <limits>    // for std::numeric_limits (franko_safe_cast bounds)
#include <new>       // for placement new
#include <stdexcept> // for std::runtime_error
#include <type_traits> // for std::is_integral, std::is_signed, std::make_unsigned
#include <iostream>  // for std::cout
#include <jemalloc/jemalloc.h> // for je_malloc, je_calloc, je_realloc, je_free

[[noreturn]] inline void franko_panic(const char* message) {
    throw std::runtime_error(message);
}

// ============================================================
// franko_safe_cast<To>(v): runtime-checked integer conversion
// ============================================================
//
// Emitted for Franko cast<T>(expr) conversions that can lose data
// (narrowing, signedness changes, ...). Value-preserving conversions
// compile to a plain static_cast instead and never reach this.
//
// Throws via franko_panic when v lies outside To's range; otherwise
// returns static_cast<To>(v).
//
// The range test dispatches on the signedness combination so no
// branch ever compares signed and unsigned values (warning-clean
// under -Wall -Wextra -Wpedantic -Wshadow).
template <typename To, typename From,
        bool FromSigned = std::is_signed<From>::value,
        bool ToSigned = std::is_signed<To>::value>
struct franko_cast_range;

// Signed source, signed target: direct comparison promotes safely.
template <typename To, typename From>
struct franko_cast_range<To, From, true, true> {
    static bool below_min(From v) { return v < std::numeric_limits<To>::min(); }
    static bool above_max(From v) { return v > std::numeric_limits<To>::max(); }
};

// Unsigned source, unsigned target: v >= 0 == min(To), so only the
// upper bound can fail. Both sides unsigned: no sign warnings.
template <typename To, typename From>
struct franko_cast_range<To, From, false, false> {
    static bool below_min(From) { return false; }
    static bool above_max(From v) { return v > std::numeric_limits<To>::max(); }
};

// Signed source, unsigned target: negatives always fail; the upper
// bound compares non-negative values as unsigned.
template <typename To, typename From>
struct franko_cast_range<To, From, true, false> {
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
struct franko_cast_range<To, From, false, true> {
    static bool below_min(From) { return false; }
    static bool above_max(From v) {
        typedef typename std::make_unsigned<To>::type unsigned_to;
        return v > static_cast<unsigned_to>(std::numeric_limits<To>::max());
    }
};

template <typename To, typename From>
To franko_safe_cast(From v) {
    static_assert(std::is_integral<To>::value,
            "franko_safe_cast requires an integral target type");
    static_assert(std::is_integral<From>::value,
            "franko_safe_cast requires an integral source type");
    static_assert(!std::is_same<To, bool>::value,
            "franko_safe_cast does not support bool");
    static_assert(!std::is_same<From, bool>::value,
            "franko_safe_cast does not support bool");

    if (franko_cast_range<To, From>::below_min(v)
            || franko_cast_range<To, From>::above_max(v)) {
        franko_panic("franko_safe_cast: value out of range for target type");
    }

    return static_cast<To>(v);
}

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

    void memset(uint8_t byteValue, uint32_t start, uint32_t count) {
        if (start > N)
            franko_panic("Franko_Static_Array memset start out of bounds");

        if (count > N - start)
            franko_panic("Franko_Static_Array memset range out of bounds");

        if (count > 0) {
            std::memset(data + start, byteValue, sizeof(T) * static_cast<size_t>(count));
        }
    }

    template <uint32_t M>
    void memcpy(const Franko_Static_Array<T, M>* other) {
        if (other == nullptr)
            franko_panic("Franko_Static_Array memcpy source address is null");

        const uint32_t count = (N < M) ? N : M;

        if (count > 0) {
            std::memcpy(data, other->data, sizeof(T) * static_cast<size_t>(count));
        }
    }

    void memcpy(const Franko_Dynamic_Array<T>* other) {
        if (other == nullptr)
            franko_panic("Franko_Static_Array memcpy source address is null");

        if (other->data == nullptr)
            franko_panic("Franko_Static_Array memcpy from uninitialized dynamic array");

        const uint32_t count = (N < other->count) ? N : other->count;

        if (count > 0) {
            std::memcpy(data, other->data, sizeof(T) * static_cast<size_t>(count));
        }
    }

    template <uint32_t M>
    void memcpy(const Franko_Static_Array<T, M>* other, uint32_t dstStart, uint32_t srcStart, uint32_t count) {
        if (other == nullptr)
            franko_panic("Franko_Static_Array memcpy source address is null");

        if (dstStart > N)
            franko_panic("Franko_Static_Array memcpy destination start out of bounds");

        if (srcStart > M)
            franko_panic("Franko_Static_Array memcpy source start out of bounds");

        if (count > N - dstStart)
            franko_panic("Franko_Static_Array memcpy destination range out of bounds");

        if (count > M - srcStart)
            franko_panic("Franko_Static_Array memcpy source range out of bounds");

        if (count > 0) {
            std::memcpy(data + dstStart, other->data + srcStart, sizeof(T) * static_cast<size_t>(count));
        }
    }

    void memcpy(const Franko_Dynamic_Array<T>* other, uint32_t dstStart, uint32_t srcStart, uint32_t count) {
        if (other == nullptr)
            franko_panic("Franko_Static_Array memcpy source address is null");

        if (other->data == nullptr)
            franko_panic("Franko_Static_Array memcpy from uninitialized dynamic array");

        if (dstStart > N)
            franko_panic("Franko_Static_Array memcpy destination start out of bounds");

        if (srcStart > other->count)
            franko_panic("Franko_Static_Array memcpy source start out of bounds");

        if (count > N - dstStart)
            franko_panic("Franko_Static_Array memcpy destination range out of bounds");

        if (count > other->count - srcStart)
            franko_panic("Franko_Static_Array memcpy source range out of bounds");

        if (count > 0) {
            std::memcpy(data + dstStart, other->data + srcStart, sizeof(T) * static_cast<size_t>(count));
        }
    }

    void memmove(uint32_t dstStart, uint32_t srcStart, uint32_t count) {
        if (dstStart > N)
            franko_panic("Franko_Static_Array memmove destination start out of bounds");

        if (srcStart > N)
            franko_panic("Franko_Static_Array memmove source start out of bounds");

        if (count > N - dstStart)
            franko_panic("Franko_Static_Array memmove destination range out of bounds");

        if (count > N - srcStart)
            franko_panic("Franko_Static_Array memmove source range out of bounds");

        if (count > 0) {
            std::memmove(data + dstStart, data + srcStart, sizeof(T) * static_cast<size_t>(count));
        }
    }
};

// ============================================================
// Franko_Dynamic_Array<T>
// ============================================================

template <typename T>
struct Franko_Dynamic_Array {
    uint32_t count = 0;
    T* data = nullptr;

    uint32_t length() const { return count; }

    ~Franko_Dynamic_Array() {
        if (data != nullptr) {
            destroy_elements(data, count);
            je_free(data);
            data = nullptr;
            count = 0;
        }
    }

    int32_t init(uint32_t n) {
        if (data != nullptr)
            franko_panic("Franko_Dynamic_Array already initialized");

        if (n == 0)
            franko_panic("Cannot allocate 0 memory for Franko_Dynamic_Array");

        T* newData = static_cast<T*>(je_malloc(sizeof(T) * static_cast<size_t>(n)));

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

        T* newData = static_cast<T*>(je_calloc(static_cast<size_t>(n), sizeof(T)));

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

        T* newData = static_cast<T*>(je_realloc(data, sizeof(T) * static_cast<size_t>(n)));

        if (!newData)
            return -1;

        // je_realloc byte-moves existing elements, which is fine for the
        // runtime's trivially-relocatable element types. Only the changed
        // tail needs lifetime handling.
        if (n > oldCount)
            construct_elements(newData + oldCount, n - oldCount);
        else if (n < oldCount)
            destroy_elements(newData + n, oldCount - n);

        data = newData;
        count = n;

        return 0;
    }

    void uninit() {
        if (data == nullptr)
            franko_panic("Franko_Dynamic_Array already uninitialized");

        destroy_elements(data, count);
        je_free(data);
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

    void memset(uint8_t byteValue, uint32_t start, uint32_t count) {
        if (data == nullptr)
            franko_panic("Franko_Dynamic_Array memset on uninitialized array");

        if (start > this->count)
            franko_panic("Franko_Dynamic_Array memset start out of bounds");

        if (count > this->count - start)
            franko_panic("Franko_Dynamic_Array memset range out of bounds");

        if (count > 0) {
            std::memset(data + start, byteValue, sizeof(T) * static_cast<size_t>(count));
        }
    }

    void memcpy(const Franko_Dynamic_Array<T>* other) {
        if (data == nullptr)
            franko_panic("Franko_Dynamic_Array memcpy on uninitialized target array");

        if (other == nullptr)
            franko_panic("Franko_Dynamic_Array memcpy source address is null");

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
            franko_panic("Franko_Dynamic_Array memcpy source address is null");

        const uint32_t copyCount = (count < M) ? count : M;

        if (copyCount > 0) {
            std::memcpy(data, other->data, sizeof(T) * static_cast<size_t>(copyCount));
        }
    }

    void memcpy(const Franko_Dynamic_Array<T>* other, uint32_t dstStart, uint32_t srcStart, uint32_t count) {
        if (data == nullptr)
            franko_panic("Franko_Dynamic_Array memcpy on uninitialized target array");

        if (other == nullptr)
            franko_panic("Franko_Dynamic_Array memcpy source address is null");

        if (other->data == nullptr)
            franko_panic("Franko_Dynamic_Array memcpy from uninitialized source array");

        if (dstStart > this->count)
            franko_panic("Franko_Dynamic_Array memcpy destination start out of bounds");

        if (srcStart > other->count)
            franko_panic("Franko_Dynamic_Array memcpy source start out of bounds");

        if (count > this->count - dstStart)
            franko_panic("Franko_Dynamic_Array memcpy destination range out of bounds");

        if (count > other->count - srcStart)
            franko_panic("Franko_Dynamic_Array memcpy source range out of bounds");

        if (count > 0) {
            std::memcpy(data + dstStart, other->data + srcStart, sizeof(T) * static_cast<size_t>(count));
        }
    }

    template <uint32_t M>
    void memcpy(const Franko_Static_Array<T, M>* other, uint32_t dstStart, uint32_t srcStart, uint32_t count) {
        if (data == nullptr)
            franko_panic("Franko_Dynamic_Array memcpy on uninitialized target array");

        if (other == nullptr)
            franko_panic("Franko_Dynamic_Array memcpy source address is null");

        if (dstStart > this->count)
            franko_panic("Franko_Dynamic_Array memcpy destination start out of bounds");

        if (srcStart > M)
            franko_panic("Franko_Dynamic_Array memcpy source start out of bounds");

        if (count > this->count - dstStart)
            franko_panic("Franko_Dynamic_Array memcpy destination range out of bounds");

        if (count > M - srcStart)
            franko_panic("Franko_Dynamic_Array memcpy source range out of bounds");

        if (count > 0) {
            std::memcpy(data + dstStart, other->data + srcStart, sizeof(T) * static_cast<size_t>(count));
        }
    }

    void memmove(uint32_t dstStart, uint32_t srcStart, uint32_t count) {
        if (data == nullptr)
            franko_panic("Franko_Dynamic_Array memmove on uninitialized array");

        if (dstStart > this->count)
            franko_panic("Franko_Dynamic_Array memmove destination start out of bounds");

        if (srcStart > this->count)
            franko_panic("Franko_Dynamic_Array memmove source start out of bounds");

        if (count > this->count - dstStart)
            franko_panic("Franko_Dynamic_Array memmove destination range out of bounds");

        if (count > this->count - srcStart)
            franko_panic("Franko_Dynamic_Array memmove source range out of bounds");

        if (count > 0) {
            std::memmove(data + dstStart, data + srcStart, sizeof(T) * static_cast<size_t>(count));
        }
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