#include "FrankoRuntime.hpp"

int32_t main();

int32_t main()
{
    uint32_t n;
    n = static_cast<uint32_t>(80);
    Franko_Dynamic_Array<uint8_t> isPrime;
    isPrime.init(static_cast<uint32_t>((n + 1)));
    isPrime.memset(static_cast<uint8_t>(1));
    (isPrime[static_cast<uint32_t>(0)]) = static_cast<uint8_t>(0);
    if (static_cast<uint8_t>((n >= 1)))
    {
        (isPrime[static_cast<uint32_t>(1)]) = static_cast<uint8_t>(0);
    }
    uint32_t p;
    p = static_cast<uint32_t>(2);
    while (static_cast<uint8_t>((static_cast<uint32_t>((p * p)) <= n)))
    {
        if (static_cast<uint8_t>(((isPrime[p]) != 0)))
        {
            uint32_t j;
            j = static_cast<uint32_t>((p * p));
            while (static_cast<uint8_t>((j <= n)))
            {
                (isPrime[j]) = static_cast<uint8_t>(0);
                j = static_cast<uint32_t>((j + p));
            }
        }
        p = static_cast<uint32_t>((p + 1));
    }
    std::cout << 1001 << '\n';
    uint32_t i;
    i = static_cast<uint32_t>(2);
    while (static_cast<uint8_t>((i <= n)))
    {
        if (static_cast<uint8_t>(((isPrime[i]) != 0)))
        {
            std::cout << i << '\n';
        }
        i = static_cast<uint32_t>((i + 1));
    }
    std::cout << 1002 << '\n';
    isPrime.uninit();
    return static_cast<int32_t>(0);
}
