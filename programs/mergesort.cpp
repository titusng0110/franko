#include "FrankoRuntime.hpp"

Franko_Static_Array<int32_t, 50> a;
Franko_Static_Array<int32_t, 50> b;

void printArray(uint32_t n);
void initData();
void merge(uint32_t low, uint32_t mid, uint32_t high);
void mergeSort(uint32_t low, uint32_t high);
uint8_t isSorted(uint32_t n);
int32_t main();

void printArray(uint32_t n)
{
    uint32_t i;
    i = static_cast<uint32_t>(0);
    while (static_cast<uint8_t>((i < n)))
    {
        std::cout << (a[i]) << '\n';
        i = static_cast<uint32_t>((i + 1));
    }
}

void initData()
{
    (a[static_cast<uint32_t>(0)]) = static_cast<int32_t>(42);
    (a[static_cast<uint32_t>(1)]) = static_cast<int32_t>(7);
    (a[static_cast<uint32_t>(2)]) = static_cast<int32_t>(19);
    (a[static_cast<uint32_t>(3)]) = static_cast<int32_t>(3);
    (a[static_cast<uint32_t>(4)]) = static_cast<int32_t>(100);
    (a[static_cast<uint32_t>(5)]) = static_cast<int32_t>(55);
    (a[static_cast<uint32_t>(6)]) = static_cast<int32_t>(1);
    (a[static_cast<uint32_t>(7)]) = static_cast<int32_t>(88);
    (a[static_cast<uint32_t>(8)]) = static_cast<int32_t>(12);
    (a[static_cast<uint32_t>(9)]) = static_cast<int32_t>(33);
    (a[static_cast<uint32_t>(10)]) = static_cast<int32_t>(21);
    (a[static_cast<uint32_t>(11)]) = static_cast<int32_t>(5);
    (a[static_cast<uint32_t>(12)]) = static_cast<int32_t>(77);
    (a[static_cast<uint32_t>(13)]) = static_cast<int32_t>(60);
    (a[static_cast<uint32_t>(14)]) = static_cast<int32_t>(2);
    (a[static_cast<uint32_t>(15)]) = static_cast<int32_t>(50);
    (a[static_cast<uint32_t>(16)]) = static_cast<int32_t>(91);
    (a[static_cast<uint32_t>(17)]) = static_cast<int32_t>(14);
    (a[static_cast<uint32_t>(18)]) = static_cast<int32_t>(39);
    (a[static_cast<uint32_t>(19)]) = static_cast<int32_t>(73);
    (a[static_cast<uint32_t>(20)]) = static_cast<int32_t>(8);
    (a[static_cast<uint32_t>(21)]) = static_cast<int32_t>(65);
    (a[static_cast<uint32_t>(22)]) = static_cast<int32_t>(27);
    (a[static_cast<uint32_t>(23)]) = static_cast<int32_t>(44);
    (a[static_cast<uint32_t>(24)]) = static_cast<int32_t>(6);
    (a[static_cast<uint32_t>(25)]) = static_cast<int32_t>(99);
    (a[static_cast<uint32_t>(26)]) = static_cast<int32_t>(31);
    (a[static_cast<uint32_t>(27)]) = static_cast<int32_t>(58);
    (a[static_cast<uint32_t>(28)]) = static_cast<int32_t>(16);
    (a[static_cast<uint32_t>(29)]) = static_cast<int32_t>(82);
    (a[static_cast<uint32_t>(30)]) = static_cast<int32_t>(24);
    (a[static_cast<uint32_t>(31)]) = static_cast<int32_t>(70);
    (a[static_cast<uint32_t>(32)]) = static_cast<int32_t>(11);
    (a[static_cast<uint32_t>(33)]) = static_cast<int32_t>(47);
    (a[static_cast<uint32_t>(34)]) = static_cast<int32_t>(35);
    (a[static_cast<uint32_t>(35)]) = static_cast<int32_t>(94);
    (a[static_cast<uint32_t>(36)]) = static_cast<int32_t>(29);
    (a[static_cast<uint32_t>(37)]) = static_cast<int32_t>(63);
    (a[static_cast<uint32_t>(38)]) = static_cast<int32_t>(18);
    (a[static_cast<uint32_t>(39)]) = static_cast<int32_t>(76);
    (a[static_cast<uint32_t>(40)]) = static_cast<int32_t>(4);
    (a[static_cast<uint32_t>(41)]) = static_cast<int32_t>(53);
    (a[static_cast<uint32_t>(42)]) = static_cast<int32_t>(68);
    (a[static_cast<uint32_t>(43)]) = static_cast<int32_t>(10);
    (a[static_cast<uint32_t>(44)]) = static_cast<int32_t>(97);
    (a[static_cast<uint32_t>(45)]) = static_cast<int32_t>(26);
    (a[static_cast<uint32_t>(46)]) = static_cast<int32_t>(41);
    (a[static_cast<uint32_t>(47)]) = static_cast<int32_t>(80);
    (a[static_cast<uint32_t>(48)]) = static_cast<int32_t>(22);
    (a[static_cast<uint32_t>(49)]) = static_cast<int32_t>(56);
    b.memset(static_cast<uint8_t>(0));
}

void merge(uint32_t low, uint32_t mid, uint32_t high)
{
    uint32_t i;
    i = static_cast<uint32_t>(0);
    uint32_t j;
    j = static_cast<uint32_t>(0);
    uint32_t k;
    k = static_cast<uint32_t>(0);
    uint32_t count;
    count = static_cast<uint32_t>(0);
    i = low;
    j = static_cast<uint32_t>((mid + 1));
    k = low;
    while (static_cast<uint8_t>((static_cast<uint8_t>((i <= mid)) && static_cast<uint8_t>((j <= high)))))
    {
        if (static_cast<uint8_t>(((a[i]) <= (a[j]))))
        {
            (b[k]) = (a[i]);
            i = static_cast<uint32_t>((i + 1));
        }
        else
        {
            (b[k]) = (a[j]);
            j = static_cast<uint32_t>((j + 1));
        }
        k = static_cast<uint32_t>((k + 1));
    }
    while (static_cast<uint8_t>((i <= mid)))
    {
        (b[k]) = (a[i]);
        i = static_cast<uint32_t>((i + 1));
        k = static_cast<uint32_t>((k + 1));
    }
    while (static_cast<uint8_t>((j <= high)))
    {
        (b[k]) = (a[j]);
        j = static_cast<uint32_t>((j + 1));
        k = static_cast<uint32_t>((k + 1));
    }
    count = static_cast<uint32_t>((static_cast<uint32_t>((high - low)) + 1));
    a.memcpy((&b), low, low, count);
}

void mergeSort(uint32_t low, uint32_t high)
{
    uint32_t mid;
    mid = static_cast<uint32_t>(0);
    if (static_cast<uint8_t>((low < high)))
    {
        mid = static_cast<uint32_t>((low + static_cast<uint32_t>((static_cast<uint32_t>((high - low)) / 2))));
        mergeSort(low, mid);
        mergeSort(static_cast<uint32_t>((mid + 1)), high);
        merge(low, mid, high);
    }
}

uint8_t isSorted(uint32_t n)
{
    uint32_t k;
    k = static_cast<uint32_t>(1);
    uint8_t sorted;
    sorted = static_cast<uint8_t>(1);
    while (static_cast<uint8_t>((k < n)))
    {
        if (static_cast<uint8_t>(((a[k]) < (a[static_cast<uint32_t>((k - 1))]))))
        {
            sorted = static_cast<uint8_t>(0);
        }
        k = static_cast<uint32_t>((k + 1));
    }
    return sorted;
}

int32_t main()
{
    uint32_t n;
    n = static_cast<uint32_t>(50);
    uint8_t sorted;
    sorted = static_cast<uint8_t>(0);
    initData();
    std::cout << 3001 << '\n';
    printArray(n);
    mergeSort(static_cast<uint32_t>(0), static_cast<uint32_t>((n - 1)));
    std::cout << 3002 << '\n';
    printArray(n);
    sorted = isSorted(n);
    std::cout << 3003 << '\n';
    std::cout << (+(sorted)) << '\n';
    return static_cast<int32_t>(0);
}
