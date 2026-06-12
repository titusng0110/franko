#pragma once

#include <cstddef>   // for size_t
#include <cstdint>   // for uint8_t, uint32_t, int32_t
#include <cstring>   // for std::memcpy, std::memset, std::memmove
#include <stdexcept> // for std::runtime_error
#include <iostream>  // for std::cout
#include <jemalloc/jemalloc.h> // for je_malloc, je_calloc, je_realloc, je_free

[[noreturn]] inline void franko_panic(const char* message) {
    throw std::runtime_error(message);
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

        const uint32_t count = (N < other->length) ? N : other->length;

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

        if (srcStart > other->length)
            franko_panic("Franko_Static_Array memcpy source start out of bounds");

        if (count > N - dstStart)
            franko_panic("Franko_Static_Array memcpy destination range out of bounds");

        if (count > other->length - srcStart)
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
    uint32_t length = 0;
    T* data = nullptr;

    ~Franko_Dynamic_Array() {
        if (data != nullptr) {
            je_free(data);
            data = nullptr;
            length = 0;
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

        data = newData;
        length = n;

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

        data = newData;
        length = n;

        return 0;
    }

    int32_t resize(uint32_t n) {
        if (data == nullptr)
            franko_panic("Franko_Dynamic_Array not initialized");

        if (n == 0)
            franko_panic("Cannot allocate 0 memory for Franko_Dynamic_Array");

        T* newData = static_cast<T*>(je_realloc(data, sizeof(T) * static_cast<size_t>(n)));

        if (!newData)
            return -1;

        data = newData;
        length = n;

        return 0;
    }

    void uninit() {
        if (data == nullptr)
            franko_panic("Franko_Dynamic_Array already uninitialized");

        je_free(data);
        data = nullptr;
        length = 0;
    }

    const T& operator[](uint32_t index) const {
        if (data == nullptr)
            franko_panic("Franko_Dynamic_Array not initialized");

        if (index >= length)
            franko_panic("Franko_Dynamic_Array index out of bounds");

        return data[index];
    }

    T& operator[](uint32_t index) {
        if (data == nullptr)
            franko_panic("Franko_Dynamic_Array not initialized");

        if (index >= length)
            franko_panic("Franko_Dynamic_Array index out of bounds");

        return data[index];
    }

    void memset(uint8_t byteValue) {
        if (data == nullptr)
            franko_panic("Franko_Dynamic_Array memset on uninitialized array");

        if (length > 0) {
            std::memset(data, byteValue, sizeof(T) * static_cast<size_t>(length));
        }
    }

    void memset(uint8_t byteValue, uint32_t start, uint32_t count) {
        if (data == nullptr)
            franko_panic("Franko_Dynamic_Array memset on uninitialized array");

        if (start > length)
            franko_panic("Franko_Dynamic_Array memset start out of bounds");

        if (count > length - start)
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

        const uint32_t count = (length < other->length) ? length : other->length;

        if (count > 0) {
            std::memcpy(data, other->data, sizeof(T) * static_cast<size_t>(count));
        }
    }

    template <uint32_t M>
    void memcpy(const Franko_Static_Array<T, M>* other) {
        if (data == nullptr)
            franko_panic("Franko_Dynamic_Array memcpy on uninitialized target array");

        if (other == nullptr)
            franko_panic("Franko_Dynamic_Array memcpy source address is null");

        const uint32_t count = (length < M) ? length : M;

        if (count > 0) {
            std::memcpy(data, other->data, sizeof(T) * static_cast<size_t>(count));
        }
    }

    void memcpy(const Franko_Dynamic_Array<T>* other, uint32_t dstStart, uint32_t srcStart, uint32_t count) {
        if (data == nullptr)
            franko_panic("Franko_Dynamic_Array memcpy on uninitialized target array");

        if (other == nullptr)
            franko_panic("Franko_Dynamic_Array memcpy source address is null");

        if (other->data == nullptr)
            franko_panic("Franko_Dynamic_Array memcpy from uninitialized source array");

        if (dstStart > length)
            franko_panic("Franko_Dynamic_Array memcpy destination start out of bounds");

        if (srcStart > other->length)
            franko_panic("Franko_Dynamic_Array memcpy source start out of bounds");

        if (count > length - dstStart)
            franko_panic("Franko_Dynamic_Array memcpy destination range out of bounds");

        if (count > other->length - srcStart)
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

        if (dstStart > length)
            franko_panic("Franko_Dynamic_Array memcpy destination start out of bounds");

        if (srcStart > M)
            franko_panic("Franko_Dynamic_Array memcpy source start out of bounds");

        if (count > length - dstStart)
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

        if (dstStart > length)
            franko_panic("Franko_Dynamic_Array memmove destination start out of bounds");

        if (srcStart > length)
            franko_panic("Franko_Dynamic_Array memmove source start out of bounds");

        if (count > length - dstStart)
            franko_panic("Franko_Dynamic_Array memmove destination range out of bounds");

        if (count > length - srcStart)
            franko_panic("Franko_Dynamic_Array memmove source range out of bounds");

        if (count > 0) {
            std::memmove(data + dstStart, data + srcStart, sizeof(T) * static_cast<size_t>(count));
        }
    }
};