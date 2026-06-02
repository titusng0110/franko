#include "FrankoRuntime.hpp"

int main() {
    uint32_t n;
    n = 80;
    Franko_Dynamic_Array<uint8_t> isPrime;
    isPrime.init((n + 1));
    uint32_t i;
    i = 0;
    while ((i <= n))
    {
        isPrime[i] = 1;
        i = (i + 1);
    }
    isPrime[0] = 0;
    isPrime[1] = 0;
    uint32_t p;
    p = 2;
    while (((p * p) <= n))
    {
        if ((isPrime[p] != 0))
        {
            uint32_t j;
            j = (p * p);
            while ((j <= n))
            {
                isPrime[j] = 0;
                j = (j + p);
            }
        }
        p = (p + 1);
    }
    i = 2;
    while ((i <= n))
    {
        if ((isPrime[i] != 0))
        {
            std::cout << i << '\n';
        }
        i = (i + 1);
    }
    isPrime.uninit();
    return 0;
}