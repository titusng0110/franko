#pragma once

// FrankoRuntimeMax.hpp - MAX BUILD variant of FrankoRuntime.hpp:
// bounds checking and unsafe_cast range checking are stripped for
// speed. Out-of-range array access and out-of-range unsafe_cast are
// UNDEFINED BEHAVIOR in this build (raw pointer semantics, wrapping
// casts). Everything else - null/uninitialized/lifecycle checks,
// element construction/destruction, the resize ordering contract -
// matches FrankoRuntime.hpp. Allocations use bundled jemalloc.

#include <cstddef>   // for size_t
#include <cstdint>   // for uint8_t, uint32_t, int32_t
#include <cstring>   // for std::memcpy, std::memset, std::memmove
#include <limits>    // for std::numeric_limits (franko_unsafe_cast bounds)
#include <new>       // for placement new
#include <stdexcept> // for std::runtime_error
#include <type_traits> // for std::is_integral, std::is_signed, std::make_unsigned
#include <jemalloc/jemalloc.h> // for je_malloc, je_calloc, je_realloc, je_free

[[noreturn]] inline void franko_panic(const char* message) {
    throw std::runtime_error(message);
}

// ============================================================
// franko_unsafe_cast<To>(v): UNCHECKED integer conversion (max build)
// ============================================================
//
// Emitted for Franko unsafe_cast<T>(expr) conversions, which may lose
// data (narrowing, signedness changes, ...). Value-preserving widening
// conversions use plain cast<T>(expr) and compile to a static_cast
// instead, so they never reach this.
//
// MAX BUILD: the out-of-range panic of the checked runtime is stripped
// for speed - an out-of-range value simply wraps/truncates like a raw
// C++ cast (undefined behavior for signed overflow).
template <typename To, typename From>
To franko_unsafe_cast(From v) {
    static_assert(std::is_integral<To>::value,
            "franko_unsafe_cast requires an integral target type");
    static_assert(std::is_integral<From>::value,
            "franko_unsafe_cast requires an integral source type");
    static_assert(!std::is_same<To, bool>::value,
            "franko_unsafe_cast does not support bool");
    static_assert(!std::is_same<From, bool>::value,
            "franko_unsafe_cast does not support bool");

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
        return data[index];
    }

    T& operator[](uint32_t index) {
        return data[index];
    }

    void memset(uint8_t byteValue) {
        if (N > 0) {
            std::memset(data, byteValue, sizeof(T) * static_cast<size_t>(N));
        }
    }

    void memset(uint8_t byteValue, uint32_t start, uint32_t count) {
        if (count > 0) {
            std::memset(data + start, byteValue, sizeof(T) * static_cast<size_t>(count));
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
    void memcpy(const Franko_Static_Array<T, M>* other, uint32_t dstStart, uint32_t srcStart, uint32_t count) {
        if (other == nullptr)
            franko_panic("Franko_Static_Array memcpy source pointer is null");

        if (count > 0) {
            std::memcpy(data + dstStart, other->data + srcStart, sizeof(T) * static_cast<size_t>(count));
        }
    }

    void memcpy(const Franko_Dynamic_Array<T>* other, uint32_t dstStart, uint32_t srcStart, uint32_t count) {
        if (other == nullptr)
            franko_panic("Franko_Static_Array memcpy source pointer is null");

        if (other->data == nullptr)
            franko_panic("Franko_Static_Array memcpy from uninitialized dynamic array");

        if (count > 0) {
            std::memcpy(data + dstStart, other->data + srcStart, sizeof(T) * static_cast<size_t>(count));
        }
    }

    void memmove(uint32_t dstStart, uint32_t srcStart, uint32_t count) {
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
    // False for every real array. The compiler sets it true on the hidden
    // borrowed headers it synthesizes for static-to-dynamic array pointer
    // view decays: their data points at storage owned by a
    // Franko_Static_Array, so destruction must not free it.
    bool borrowed = false;

    uint32_t length() const { return count; }

    ~Franko_Dynamic_Array() {
        if (!borrowed && data != nullptr) {
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

        if (n < oldCount) {
            // Destroy the dropped tail while its storage is still alive:
            // realloc may release the tail first, so destroying afterwards
            // would run destructors on freed memory.
            destroy_elements(data + n, oldCount - n);
        }

        T* newData = static_cast<T*>(je_realloc(data, sizeof(T) * static_cast<size_t>(n)));

        if (!newData) {
            // The buffer is unchanged on realloc failure, but a shrinking
            // resize already destroyed its dropped tail: narrow count so
            // the destroyed elements are never destroyed again.
            count = (n < oldCount) ? n : oldCount;
            return -1;
        }

        // je_realloc byte-moves existing elements, which is fine for the
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
        je_free(data);
        data = nullptr;
        count = 0;
    }

    const T& operator[](uint32_t index) const {
        return data[index];
    }

    T& operator[](uint32_t index) {
        return data[index];
    }

    void memset(uint8_t byteValue) {
        if (count > 0) {
            std::memset(data, byteValue, sizeof(T) * static_cast<size_t>(count));
        }
    }

    void memset(uint8_t byteValue, uint32_t start, uint32_t count) {
        if (count > 0) {
            std::memset(data + start, byteValue, sizeof(T) * static_cast<size_t>(count));
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

    void memcpy(const Franko_Dynamic_Array<T>* other, uint32_t dstStart, uint32_t srcStart, uint32_t count) {
        if (other == nullptr)
            franko_panic("Franko_Dynamic_Array memcpy source pointer is null");

        if (other->data == nullptr)
            franko_panic("Franko_Dynamic_Array memcpy from uninitialized source array");

        if (count > 0) {
            std::memcpy(data + dstStart, other->data + srcStart, sizeof(T) * static_cast<size_t>(count));
        }
    }

    template <uint32_t M>
    void memcpy(const Franko_Static_Array<T, M>* other, uint32_t dstStart, uint32_t srcStart, uint32_t count) {
        if (other == nullptr)
            franko_panic("Franko_Dynamic_Array memcpy source pointer is null");

        if (count > 0) {
            std::memcpy(data + dstStart, other->data + srcStart, sizeof(T) * static_cast<size_t>(count));
        }
    }

    void memmove(uint32_t dstStart, uint32_t srcStart, uint32_t count) {
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