#include <cstdint>
#include <cstring>
#include <stdexcept>
#include <iostream> // for print


template <typename T, uint32_t N>
struct Franko_Static_Array;

template <typename T>
struct Franko_Dynamic_Array;

template <typename T, uint32_t N>
struct Franko_Static_Array {
    T data[N];

    const T& operator[](uint32_t index) const {
        if (index >= N) {
            throw std::runtime_error("Franko_Static_Array index out of bounds");
        }
        return data[index];
    }

    T& operator[](uint32_t index) {
        if (index >= N) {
            throw std::runtime_error("Franko_Static_Array index out of bounds");
        }
        return data[index];
    }

    template <uint32_t M>
    void memcpy(const Franko_Static_Array<T, M>& other) {
        const uint32_t count = (N < M) ? N : M;
        if (count > 0) {
            std::memcpy(data, other.data, sizeof(T) * count);
        }
    }

    void memcpy(const Franko_Dynamic_Array<T>& other) {
        if (other.data == nullptr)
            throw std::runtime_error("Cannot memcpy on uninitialized array");

        const uint32_t count = (N < other.length) ? N : other.length;
        if (count > 0)
            std::memcpy(data, other.data, sizeof(T) * count);
    }

    void memset(uint8_t byteValue) {
        if (N > 0) {
            std::memset(data, byteValue, sizeof(T) * N);
        }
    }
};

template <typename T>
struct Franko_Dynamic_Array {
    uint32_t length = 0;
    T* data = nullptr;

    void init(uint32_t n) {
        if (data != nullptr)
            throw std::runtime_error("Franko_Dynamic_Array already initialized");

        if (n == 0)
            throw std::runtime_error("Cannot allocate 0 memory for Franko_Dynamic_Array");

        length = n;
        data = static_cast<T*>(std::malloc(sizeof(T) * n));

        if (!data)
            throw std::bad_alloc();
    }

    void uninit() {
        if (data == nullptr)
            throw std::runtime_error("Franko_Dynamic_Array already uninitialized");

        std::free(data);
        data = nullptr;
        length = 0;
    }

    const T& operator[](uint32_t index) const {
        if (data == nullptr)
            throw std::runtime_error("Franko_Dynamic_Array not initialized");

        if (index >= length)
            throw std::runtime_error("Franko_Dynamic_Array index out of bounds");

        return data[index];
    }

    T& operator[](uint32_t index) {
        if (data == nullptr)
            throw std::runtime_error("Franko_Dynamic_Array not initialized");

        if (index >= length)
            throw std::runtime_error("Franko_Dynamic_Array index out of bounds");

        return data[index];
    }

    void memcpy(const Franko_Dynamic_Array<T>& other) {
        if (data == nullptr || other.data == nullptr)
            throw std::runtime_error("Franko_Dynamic_Array memcpy on uninitialized array");

        const uint32_t count = (length < other.length) ? length : other.length;
        if (count > 0)
            std::memcpy(data, other.data, sizeof(T) * count);
    }

    template <uint32_t M>
    void memcpy(const Franko_Static_Array<T, M>& other) {
        if (data == nullptr)
            throw std::runtime_error("Franko_Dynamic_Array memcpy on uninitialized array");
        
        const uint32_t count = (length < M) ? length : M;
        if (count > 0) {
            std::memcpy(data, other.data, sizeof(T) * count);
        }
    }

    void memset(uint8_t byteValue) {
        if (data == nullptr)
            throw std::runtime_error("Franko_Dynamic_Array memset on uninitialized array");

        if (length > 0)
            std::memset(data, byteValue, sizeof(T) * length);
    }
};