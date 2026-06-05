#include "FrankoRuntime.hpp"

int main() {
    uint32_t n;
    n = 80;
    Franko_Dynamic_Array<uint8_t> isPrime;
    isPrime.init(static_cast<uint32_t>((n + 1)));
    isPrime.memset(1);
    (isPrime[0]) = 0;
    if (static_cast<uint8_t>((n >= 1)))
    {
        (isPrime[1]) = 0;
    }
    uint32_t p;
    p = 2;
    while (static_cast<uint8_t>((static_cast<uint32_t>((p * p)) <= n)))
    {
        if (static_cast<uint8_t>(((isPrime[p]) != 0)))
        {
            uint32_t j;
            j = static_cast<uint32_t>((p * p));
            while (static_cast<uint8_t>((j <= n)))
            {
                (isPrime[j]) = 0;
                j = static_cast<uint32_t>((j + p));
            }
        }
        p = static_cast<uint32_t>((p + 1));
    }
    std::cout << 3001 << '\n';
    uint32_t i;
    i = 2;
    while (static_cast<uint8_t>((i <= n)))
    {
        if (static_cast<uint8_t>(((isPrime[i]) != 0)))
        {
            std::cout << i << '\n';
        }
        i = static_cast<uint32_t>((i + 1));
    }
    std::cout << 3002 << '\n';
    isPrime.uninit();
    return 0;
}