#include "FrankoRuntime.hpp"

void printArray(Franko_Static_Array<int32_t, 50>* data, uint32_t n);
void initData(Franko_Static_Array<int32_t, 50>* data);
void swap(Franko_Static_Array<int32_t, 50>* data, uint32_t a, uint32_t b);
uint32_t partition(Franko_Static_Array<int32_t, 50>* data, uint32_t low, uint32_t high);
void quicksort(Franko_Static_Array<int32_t, 50>* data, uint32_t n);
uint8_t isSorted(Franko_Static_Array<int32_t, 50>* data, uint32_t n);
int32_t main();

void printArray(Franko_Static_Array<int32_t, 50>* data, uint32_t n)
{
    uint32_t i;
    i = static_cast<uint32_t>(0);
    while (static_cast<uint8_t>((i < n)))
    {
        std::cout << ((*data)[i]) << '\n';
        i = static_cast<uint32_t>((i + 1));
    }
}

void initData(Franko_Static_Array<int32_t, 50>* data)
{
    ((*data)[static_cast<uint32_t>(0)]) = static_cast<int32_t>(42);
    ((*data)[static_cast<uint32_t>(1)]) = static_cast<int32_t>(7);
    ((*data)[static_cast<uint32_t>(2)]) = static_cast<int32_t>(19);
    ((*data)[static_cast<uint32_t>(3)]) = static_cast<int32_t>(3);
    ((*data)[static_cast<uint32_t>(4)]) = static_cast<int32_t>(100);
    ((*data)[static_cast<uint32_t>(5)]) = static_cast<int32_t>(55);
    ((*data)[static_cast<uint32_t>(6)]) = static_cast<int32_t>(1);
    ((*data)[static_cast<uint32_t>(7)]) = static_cast<int32_t>(88);
    ((*data)[static_cast<uint32_t>(8)]) = static_cast<int32_t>(12);
    ((*data)[static_cast<uint32_t>(9)]) = static_cast<int32_t>(33);
    ((*data)[static_cast<uint32_t>(10)]) = static_cast<int32_t>(21);
    ((*data)[static_cast<uint32_t>(11)]) = static_cast<int32_t>(5);
    ((*data)[static_cast<uint32_t>(12)]) = static_cast<int32_t>(77);
    ((*data)[static_cast<uint32_t>(13)]) = static_cast<int32_t>(60);
    ((*data)[static_cast<uint32_t>(14)]) = static_cast<int32_t>(2);
    ((*data)[static_cast<uint32_t>(15)]) = static_cast<int32_t>(50);
    ((*data)[static_cast<uint32_t>(16)]) = static_cast<int32_t>(91);
    ((*data)[static_cast<uint32_t>(17)]) = static_cast<int32_t>(14);
    ((*data)[static_cast<uint32_t>(18)]) = static_cast<int32_t>(39);
    ((*data)[static_cast<uint32_t>(19)]) = static_cast<int32_t>(73);
    ((*data)[static_cast<uint32_t>(20)]) = static_cast<int32_t>(8);
    ((*data)[static_cast<uint32_t>(21)]) = static_cast<int32_t>(65);
    ((*data)[static_cast<uint32_t>(22)]) = static_cast<int32_t>(27);
    ((*data)[static_cast<uint32_t>(23)]) = static_cast<int32_t>(44);
    ((*data)[static_cast<uint32_t>(24)]) = static_cast<int32_t>(6);
    ((*data)[static_cast<uint32_t>(25)]) = static_cast<int32_t>(99);
    ((*data)[static_cast<uint32_t>(26)]) = static_cast<int32_t>(31);
    ((*data)[static_cast<uint32_t>(27)]) = static_cast<int32_t>(58);
    ((*data)[static_cast<uint32_t>(28)]) = static_cast<int32_t>(16);
    ((*data)[static_cast<uint32_t>(29)]) = static_cast<int32_t>(82);
    ((*data)[static_cast<uint32_t>(30)]) = static_cast<int32_t>(24);
    ((*data)[static_cast<uint32_t>(31)]) = static_cast<int32_t>(70);
    ((*data)[static_cast<uint32_t>(32)]) = static_cast<int32_t>(11);
    ((*data)[static_cast<uint32_t>(33)]) = static_cast<int32_t>(47);
    ((*data)[static_cast<uint32_t>(34)]) = static_cast<int32_t>(35);
    ((*data)[static_cast<uint32_t>(35)]) = static_cast<int32_t>(94);
    ((*data)[static_cast<uint32_t>(36)]) = static_cast<int32_t>(29);
    ((*data)[static_cast<uint32_t>(37)]) = static_cast<int32_t>(63);
    ((*data)[static_cast<uint32_t>(38)]) = static_cast<int32_t>(18);
    ((*data)[static_cast<uint32_t>(39)]) = static_cast<int32_t>(76);
    ((*data)[static_cast<uint32_t>(40)]) = static_cast<int32_t>(4);
    ((*data)[static_cast<uint32_t>(41)]) = static_cast<int32_t>(53);
    ((*data)[static_cast<uint32_t>(42)]) = static_cast<int32_t>(68);
    ((*data)[static_cast<uint32_t>(43)]) = static_cast<int32_t>(10);
    ((*data)[static_cast<uint32_t>(44)]) = static_cast<int32_t>(97);
    ((*data)[static_cast<uint32_t>(45)]) = static_cast<int32_t>(26);
    ((*data)[static_cast<uint32_t>(46)]) = static_cast<int32_t>(41);
    ((*data)[static_cast<uint32_t>(47)]) = static_cast<int32_t>(80);
    ((*data)[static_cast<uint32_t>(48)]) = static_cast<int32_t>(22);
    ((*data)[static_cast<uint32_t>(49)]) = static_cast<int32_t>(56);
}

void swap(Franko_Static_Array<int32_t, 50>* data, uint32_t a, uint32_t b)
{
    int32_t temp;
    temp = static_cast<int32_t>(0);
    temp = ((*data)[a]);
    ((*data)[a]) = ((*data)[b]);
    ((*data)[b]) = temp;
}

uint32_t partition(Franko_Static_Array<int32_t, 50>* data, uint32_t low, uint32_t high)
{
    int32_t pivot;
    pivot = static_cast<int32_t>(0);
    uint32_t i;
    i = static_cast<uint32_t>(0);
    uint32_t j;
    j = static_cast<uint32_t>(0);
    pivot = ((*data)[high]);
    i = low;
    j = low;
    while (static_cast<uint8_t>((j < high)))
    {
        if (static_cast<uint8_t>((((*data)[j]) <= pivot)))
        {
            swap(data, i, j);
            i = static_cast<uint32_t>((i + 1));
        }
        j = static_cast<uint32_t>((j + 1));
    }
    swap(data, i, high);
    return i;
}

void quicksort(Franko_Static_Array<int32_t, 50>* data, uint32_t n)
{
    Franko_Static_Array<uint32_t, 128> stackLow;
    Franko_Static_Array<uint32_t, 128> stackHigh;
    uint32_t top;
    top = static_cast<uint32_t>(0);
    uint32_t low;
    low = static_cast<uint32_t>(0);
    uint32_t high;
    high = static_cast<uint32_t>(0);
    uint32_t pi;
    pi = static_cast<uint32_t>(0);
    (stackLow[top]) = static_cast<uint32_t>(0);
    (stackHigh[top]) = static_cast<uint32_t>((n - 1));
    top = static_cast<uint32_t>((top + 1));
    while (static_cast<uint8_t>((top != 0)))
    {
        top = static_cast<uint32_t>((top - 1));
        low = (stackLow[top]);
        high = (stackHigh[top]);
        if (static_cast<uint8_t>((low < high)))
        {
            pi = partition(data, low, high);
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
}

uint8_t isSorted(Franko_Static_Array<int32_t, 50>* data, uint32_t n)
{
    uint32_t k;
    k = static_cast<uint32_t>(1);
    uint8_t sorted;
    sorted = static_cast<uint8_t>(1);
    while (static_cast<uint8_t>((k < n)))
    {
        if (static_cast<uint8_t>((((*data)[k]) < ((*data)[static_cast<uint32_t>((k - 1))]))))
        {
            sorted = static_cast<uint8_t>(0);
        }
        k = static_cast<uint32_t>((k + 1));
    }
    return sorted;
}

int32_t main()
{
    Franko_Static_Array<int32_t, 50>* data = static_cast<Franko_Static_Array<int32_t, 50>*>(je_malloc(sizeof(Franko_Static_Array<int32_t, 50>)));
    if (!data) throw std::bad_alloc();
    new (data) Franko_Static_Array<int32_t, 50>;
    uint32_t n;
    n = static_cast<uint32_t>(50);
    uint8_t sorted;
    sorted = static_cast<uint8_t>(0);
    initData((&(*data)));
    std::cout << 2001 << '\n';
    printArray((&(*data)), n);
    quicksort((&(*data)), n);
    std::cout << 2002 << '\n';
    printArray((&(*data)), n);
    sorted = isSorted((&(*data)), n);
    std::cout << 2003 << '\n';
    std::cout << (+(sorted)) << '\n';
    data->~Franko_Static_Array<int32_t, 50>();
    je_free(data);
    data = nullptr;
    return static_cast<int32_t>(0);
}
