#include "FrankoRuntime.hpp"

int main() {
    Franko_Static_Array<int32_t, 50>* data = new Franko_Static_Array<int32_t, 50>();
    Franko_Static_Array<uint32_t, 128> stackLow;
    Franko_Static_Array<uint32_t, 128> stackHigh;
    uint32_t n;
    n = 50;
    uint32_t top;
    top = 0;
    uint32_t low;
    low = 0;
    uint32_t high;
    high = 0;
    uint32_t i;
    i = 0;
    uint32_t j;
    j = 0;
    uint32_t pi;
    pi = 0;
    int32_t pivot;
    pivot = 0;
    int32_t temp;
    temp = 0;
    uint32_t k;
    k = 0;
    uint8_t sorted;
    sorted = 1;
    ((*data)[0]) = 42;
    ((*data)[1]) = 7;
    ((*data)[2]) = 19;
    ((*data)[3]) = 3;
    ((*data)[4]) = 100;
    ((*data)[5]) = 55;
    ((*data)[6]) = 1;
    ((*data)[7]) = 88;
    ((*data)[8]) = 12;
    ((*data)[9]) = 33;
    ((*data)[10]) = 21;
    ((*data)[11]) = 5;
    ((*data)[12]) = 77;
    ((*data)[13]) = 60;
    ((*data)[14]) = 2;
    ((*data)[15]) = 50;
    ((*data)[16]) = 91;
    ((*data)[17]) = 14;
    ((*data)[18]) = 39;
    ((*data)[19]) = 73;
    ((*data)[20]) = 8;
    ((*data)[21]) = 65;
    ((*data)[22]) = 27;
    ((*data)[23]) = 44;
    ((*data)[24]) = 6;
    ((*data)[25]) = 99;
    ((*data)[26]) = 31;
    ((*data)[27]) = 58;
    ((*data)[28]) = 16;
    ((*data)[29]) = 82;
    ((*data)[30]) = 24;
    ((*data)[31]) = 70;
    ((*data)[32]) = 11;
    ((*data)[33]) = 47;
    ((*data)[34]) = 35;
    ((*data)[35]) = 94;
    ((*data)[36]) = 29;
    ((*data)[37]) = 63;
    ((*data)[38]) = 18;
    ((*data)[39]) = 76;
    ((*data)[40]) = 4;
    ((*data)[41]) = 53;
    ((*data)[42]) = 68;
    ((*data)[43]) = 10;
    ((*data)[44]) = 97;
    ((*data)[45]) = 26;
    ((*data)[46]) = 41;
    ((*data)[47]) = 80;
    ((*data)[48]) = 22;
    ((*data)[49]) = 56;
    std::cout << 5001 << '\n';
    std::cout << ((*data)[0]) << ' ' << ((*data)[1]) << ' ' << ((*data)[2]) << ' ' << ((*data)[3]) << ' ' << ((*data)[4]) << ' ' << ((*data)[5]) << ' ' << ((*data)[6]) << ' ' << ((*data)[7]) << ' ' << ((*data)[8]) << ' ' << ((*data)[9]) << ' ' << ((*data)[10]) << ' ' << ((*data)[11]) << ' ' << ((*data)[12]) << ' ' << ((*data)[13]) << ' ' << ((*data)[14]) << ' ' << ((*data)[15]) << ' ' << ((*data)[16]) << ' ' << ((*data)[17]) << ' ' << ((*data)[18]) << ' ' << ((*data)[19]) << ' ' << ((*data)[20]) << ' ' << ((*data)[21]) << ' ' << ((*data)[22]) << ' ' << ((*data)[23]) << ' ' << ((*data)[24]) << ' ' << ((*data)[25]) << ' ' << ((*data)[26]) << ' ' << ((*data)[27]) << ' ' << ((*data)[28]) << ' ' << ((*data)[29]) << ' ' << ((*data)[30]) << ' ' << ((*data)[31]) << ' ' << ((*data)[32]) << ' ' << ((*data)[33]) << ' ' << ((*data)[34]) << ' ' << ((*data)[35]) << ' ' << ((*data)[36]) << ' ' << ((*data)[37]) << ' ' << ((*data)[38]) << ' ' << ((*data)[39]) << ' ' << ((*data)[40]) << ' ' << ((*data)[41]) << ' ' << ((*data)[42]) << ' ' << ((*data)[43]) << ' ' << ((*data)[44]) << ' ' << ((*data)[45]) << ' ' << ((*data)[46]) << ' ' << ((*data)[47]) << ' ' << ((*data)[48]) << ' ' << ((*data)[49]) << '\n';
    (stackLow[top]) = 0;
    (stackHigh[top]) = static_cast<uint32_t>((n - 1));
    top = static_cast<uint32_t>((top + 1));
    while (static_cast<uint8_t>((top != 0)))
    {
        top = static_cast<uint32_t>((top - 1));
        low = (stackLow[top]);
        high = (stackHigh[top]);
        if (static_cast<uint8_t>((low < high)))
        {
            pivot = ((*data)[high]);
            i = low;
            j = low;
            while (static_cast<uint8_t>((j < high)))
            {
                if (static_cast<uint8_t>((((*data)[j]) <= pivot)))
                {
                    temp = ((*data)[i]);
                    ((*data)[i]) = ((*data)[j]);
                    ((*data)[j]) = temp;
                    i = static_cast<uint32_t>((i + 1));
                }
                j = static_cast<uint32_t>((j + 1));
            }
            temp = ((*data)[i]);
            ((*data)[i]) = ((*data)[high]);
            ((*data)[high]) = temp;
            pi = i;
            if (static_cast<uint8_t>((pi > low)))
            {
                (stackLow[top]) = low;
                (stackHigh[top]) = static_cast<uint32_t>((pi - 1));
                top = static_cast<uint32_t>((top + 1));
            }
            if (static_cast<uint8_t>((pi < high)))
            {
                (stackLow[top]) = static_cast<uint32_t>((pi + 1));
                (stackHigh[top]) = high;
                top = static_cast<uint32_t>((top + 1));
            }
        }
    }
    std::cout << 5002 << '\n';
    std::cout << ((*data)[0]) << ' ' << ((*data)[1]) << ' ' << ((*data)[2]) << ' ' << ((*data)[3]) << ' ' << ((*data)[4]) << ' ' << ((*data)[5]) << ' ' << ((*data)[6]) << ' ' << ((*data)[7]) << ' ' << ((*data)[8]) << ' ' << ((*data)[9]) << ' ' << ((*data)[10]) << ' ' << ((*data)[11]) << ' ' << ((*data)[12]) << ' ' << ((*data)[13]) << ' ' << ((*data)[14]) << ' ' << ((*data)[15]) << ' ' << ((*data)[16]) << ' ' << ((*data)[17]) << ' ' << ((*data)[18]) << ' ' << ((*data)[19]) << ' ' << ((*data)[20]) << ' ' << ((*data)[21]) << ' ' << ((*data)[22]) << ' ' << ((*data)[23]) << ' ' << ((*data)[24]) << ' ' << ((*data)[25]) << ' ' << ((*data)[26]) << ' ' << ((*data)[27]) << ' ' << ((*data)[28]) << ' ' << ((*data)[29]) << ' ' << ((*data)[30]) << ' ' << ((*data)[31]) << ' ' << ((*data)[32]) << ' ' << ((*data)[33]) << ' ' << ((*data)[34]) << ' ' << ((*data)[35]) << ' ' << ((*data)[36]) << ' ' << ((*data)[37]) << ' ' << ((*data)[38]) << ' ' << ((*data)[39]) << ' ' << ((*data)[40]) << ' ' << ((*data)[41]) << ' ' << ((*data)[42]) << ' ' << ((*data)[43]) << ' ' << ((*data)[44]) << ' ' << ((*data)[45]) << ' ' << ((*data)[46]) << ' ' << ((*data)[47]) << ' ' << ((*data)[48]) << ' ' << ((*data)[49]) << '\n';
    k = 1;
    while (static_cast<uint8_t>((k < n)))
    {
        if (static_cast<uint8_t>((((*data)[k]) < ((*data)[static_cast<uint32_t>((k - 1))]))))
        {
            sorted = 0;
        }
        k = static_cast<uint32_t>((k + 1));
    }
    std::cout << 5003 << '\n';
    std::cout << (+(sorted)) << '\n';
    delete data;
    data = nullptr;
    return 0;
}