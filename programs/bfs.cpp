#include "FrankoRuntime.hpp"

void initArrays(Franko_Static_Array<Franko_Static_Array<uint32_t, 13>, 12>* grid, Franko_Static_Array<Franko_Static_Array<uint8_t, 13>, 12>* visited, Franko_Static_Array<Franko_Static_Array<uint8_t, 13>, 12>* path, Franko_Static_Array<Franko_Static_Array<uint32_t, 13>, 12>* dist, Franko_Static_Array<Franko_Static_Array<uint32_t, 13>, 12>* parentR, Franko_Static_Array<Franko_Static_Array<uint32_t, 13>, 12>* parentC);
void tryVisit(Franko_Static_Array<Franko_Static_Array<uint32_t, 13>, 12>* grid, Franko_Static_Array<Franko_Static_Array<uint8_t, 13>, 12>* visited, Franko_Static_Array<Franko_Static_Array<uint32_t, 13>, 12>* dist, Franko_Static_Array<Franko_Static_Array<uint32_t, 13>, 12>* parentR, Franko_Static_Array<Franko_Static_Array<uint32_t, 13>, 12>* parentC, Franko_Dynamic_Array<uint32_t>* queue, uint32_t* tail, uint32_t qCap, uint32_t cols, uint32_t r, uint32_t c, uint32_t nr, uint32_t nc);
uint8_t runBfs(Franko_Static_Array<Franko_Static_Array<uint32_t, 13>, 12>* grid, Franko_Static_Array<Franko_Static_Array<uint8_t, 13>, 12>* visited, Franko_Static_Array<Franko_Static_Array<uint32_t, 13>, 12>* dist, Franko_Static_Array<Franko_Static_Array<uint32_t, 13>, 12>* parentR, Franko_Static_Array<Franko_Static_Array<uint32_t, 13>, 12>* parentC, Franko_Dynamic_Array<uint32_t>* queue, uint32_t qCap, uint32_t cols, uint32_t startR, uint32_t startC, uint32_t goalR, uint32_t goalC);
void reconstructPath(Franko_Static_Array<Franko_Static_Array<uint8_t, 13>, 12>* path, Franko_Static_Array<Franko_Static_Array<uint32_t, 13>, 12>* parentR, Franko_Static_Array<Franko_Static_Array<uint32_t, 13>, 12>* parentC, uint32_t startR, uint32_t startC, uint32_t goalR, uint32_t goalC);
void printSolvedGrid(Franko_Static_Array<Franko_Static_Array<uint32_t, 13>, 12>* grid, Franko_Static_Array<Franko_Static_Array<uint8_t, 13>, 12>* path, Franko_Static_Array<uint32_t, 13>* line, uint32_t rows, uint32_t cols, uint32_t startR, uint32_t startC, uint32_t goalR, uint32_t goalC);
int32_t main();

void initArrays(Franko_Static_Array<Franko_Static_Array<uint32_t, 13>, 12>* grid, Franko_Static_Array<Franko_Static_Array<uint8_t, 13>, 12>* visited, Franko_Static_Array<Franko_Static_Array<uint8_t, 13>, 12>* path, Franko_Static_Array<Franko_Static_Array<uint32_t, 13>, 12>* dist, Franko_Static_Array<Franko_Static_Array<uint32_t, 13>, 12>* parentR, Franko_Static_Array<Franko_Static_Array<uint32_t, 13>, 12>* parentC)
{
    (((*grid)[static_cast<uint32_t>(0)])[static_cast<uint32_t>(0)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(0)])[static_cast<uint32_t>(1)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(0)])[static_cast<uint32_t>(2)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(0)])[static_cast<uint32_t>(3)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(0)])[static_cast<uint32_t>(4)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(0)])[static_cast<uint32_t>(5)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(0)])[static_cast<uint32_t>(6)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(0)])[static_cast<uint32_t>(7)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(0)])[static_cast<uint32_t>(8)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(0)])[static_cast<uint32_t>(9)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(0)])[static_cast<uint32_t>(10)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(0)])[static_cast<uint32_t>(11)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(0)])[static_cast<uint32_t>(12)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(1)])[static_cast<uint32_t>(0)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(1)])[static_cast<uint32_t>(1)]) = static_cast<uint32_t>(2);
    (((*grid)[static_cast<uint32_t>(1)])[static_cast<uint32_t>(2)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(1)])[static_cast<uint32_t>(3)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(1)])[static_cast<uint32_t>(4)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(1)])[static_cast<uint32_t>(5)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(1)])[static_cast<uint32_t>(6)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(1)])[static_cast<uint32_t>(7)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(1)])[static_cast<uint32_t>(8)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(1)])[static_cast<uint32_t>(9)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(1)])[static_cast<uint32_t>(10)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(1)])[static_cast<uint32_t>(11)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(1)])[static_cast<uint32_t>(12)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(2)])[static_cast<uint32_t>(0)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(2)])[static_cast<uint32_t>(1)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(2)])[static_cast<uint32_t>(2)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(2)])[static_cast<uint32_t>(3)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(2)])[static_cast<uint32_t>(4)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(2)])[static_cast<uint32_t>(5)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(2)])[static_cast<uint32_t>(6)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(2)])[static_cast<uint32_t>(7)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(2)])[static_cast<uint32_t>(8)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(2)])[static_cast<uint32_t>(9)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(2)])[static_cast<uint32_t>(10)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(2)])[static_cast<uint32_t>(11)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(2)])[static_cast<uint32_t>(12)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(3)])[static_cast<uint32_t>(0)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(3)])[static_cast<uint32_t>(1)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(3)])[static_cast<uint32_t>(2)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(3)])[static_cast<uint32_t>(3)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(3)])[static_cast<uint32_t>(4)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(3)])[static_cast<uint32_t>(5)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(3)])[static_cast<uint32_t>(6)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(3)])[static_cast<uint32_t>(7)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(3)])[static_cast<uint32_t>(8)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(3)])[static_cast<uint32_t>(9)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(3)])[static_cast<uint32_t>(10)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(3)])[static_cast<uint32_t>(11)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(3)])[static_cast<uint32_t>(12)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(4)])[static_cast<uint32_t>(0)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(4)])[static_cast<uint32_t>(1)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(4)])[static_cast<uint32_t>(2)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(4)])[static_cast<uint32_t>(3)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(4)])[static_cast<uint32_t>(4)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(4)])[static_cast<uint32_t>(5)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(4)])[static_cast<uint32_t>(6)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(4)])[static_cast<uint32_t>(7)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(4)])[static_cast<uint32_t>(8)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(4)])[static_cast<uint32_t>(9)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(4)])[static_cast<uint32_t>(10)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(4)])[static_cast<uint32_t>(11)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(4)])[static_cast<uint32_t>(12)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(5)])[static_cast<uint32_t>(0)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(5)])[static_cast<uint32_t>(1)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(5)])[static_cast<uint32_t>(2)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(5)])[static_cast<uint32_t>(3)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(5)])[static_cast<uint32_t>(4)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(5)])[static_cast<uint32_t>(5)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(5)])[static_cast<uint32_t>(6)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(5)])[static_cast<uint32_t>(7)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(5)])[static_cast<uint32_t>(8)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(5)])[static_cast<uint32_t>(9)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(5)])[static_cast<uint32_t>(10)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(5)])[static_cast<uint32_t>(11)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(5)])[static_cast<uint32_t>(12)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(6)])[static_cast<uint32_t>(0)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(6)])[static_cast<uint32_t>(1)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(6)])[static_cast<uint32_t>(2)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(6)])[static_cast<uint32_t>(3)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(6)])[static_cast<uint32_t>(4)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(6)])[static_cast<uint32_t>(5)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(6)])[static_cast<uint32_t>(6)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(6)])[static_cast<uint32_t>(7)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(6)])[static_cast<uint32_t>(8)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(6)])[static_cast<uint32_t>(9)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(6)])[static_cast<uint32_t>(10)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(6)])[static_cast<uint32_t>(11)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(6)])[static_cast<uint32_t>(12)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(7)])[static_cast<uint32_t>(0)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(7)])[static_cast<uint32_t>(1)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(7)])[static_cast<uint32_t>(2)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(7)])[static_cast<uint32_t>(3)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(7)])[static_cast<uint32_t>(4)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(7)])[static_cast<uint32_t>(5)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(7)])[static_cast<uint32_t>(6)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(7)])[static_cast<uint32_t>(7)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(7)])[static_cast<uint32_t>(8)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(7)])[static_cast<uint32_t>(9)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(7)])[static_cast<uint32_t>(10)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(7)])[static_cast<uint32_t>(11)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(7)])[static_cast<uint32_t>(12)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(8)])[static_cast<uint32_t>(0)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(8)])[static_cast<uint32_t>(1)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(8)])[static_cast<uint32_t>(2)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(8)])[static_cast<uint32_t>(3)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(8)])[static_cast<uint32_t>(4)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(8)])[static_cast<uint32_t>(5)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(8)])[static_cast<uint32_t>(6)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(8)])[static_cast<uint32_t>(7)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(8)])[static_cast<uint32_t>(8)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(8)])[static_cast<uint32_t>(9)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(8)])[static_cast<uint32_t>(10)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(8)])[static_cast<uint32_t>(11)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(8)])[static_cast<uint32_t>(12)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(9)])[static_cast<uint32_t>(0)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(9)])[static_cast<uint32_t>(1)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(9)])[static_cast<uint32_t>(2)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(9)])[static_cast<uint32_t>(3)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(9)])[static_cast<uint32_t>(4)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(9)])[static_cast<uint32_t>(5)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(9)])[static_cast<uint32_t>(6)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(9)])[static_cast<uint32_t>(7)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(9)])[static_cast<uint32_t>(8)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(9)])[static_cast<uint32_t>(9)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(9)])[static_cast<uint32_t>(10)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(9)])[static_cast<uint32_t>(11)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(9)])[static_cast<uint32_t>(12)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(10)])[static_cast<uint32_t>(0)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(10)])[static_cast<uint32_t>(1)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(10)])[static_cast<uint32_t>(2)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(10)])[static_cast<uint32_t>(3)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(10)])[static_cast<uint32_t>(4)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(10)])[static_cast<uint32_t>(5)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(10)])[static_cast<uint32_t>(6)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(10)])[static_cast<uint32_t>(7)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(10)])[static_cast<uint32_t>(8)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(10)])[static_cast<uint32_t>(9)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(10)])[static_cast<uint32_t>(10)]) = static_cast<uint32_t>(0);
    (((*grid)[static_cast<uint32_t>(10)])[static_cast<uint32_t>(11)]) = static_cast<uint32_t>(3);
    (((*grid)[static_cast<uint32_t>(10)])[static_cast<uint32_t>(12)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(11)])[static_cast<uint32_t>(0)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(11)])[static_cast<uint32_t>(1)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(11)])[static_cast<uint32_t>(2)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(11)])[static_cast<uint32_t>(3)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(11)])[static_cast<uint32_t>(4)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(11)])[static_cast<uint32_t>(5)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(11)])[static_cast<uint32_t>(6)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(11)])[static_cast<uint32_t>(7)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(11)])[static_cast<uint32_t>(8)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(11)])[static_cast<uint32_t>(9)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(11)])[static_cast<uint32_t>(10)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(11)])[static_cast<uint32_t>(11)]) = static_cast<uint32_t>(1);
    (((*grid)[static_cast<uint32_t>(11)])[static_cast<uint32_t>(12)]) = static_cast<uint32_t>(1);
    (*visited).memset(static_cast<uint8_t>(0));
    (*path).memset(static_cast<uint8_t>(0));
    (*dist).memset(static_cast<uint8_t>(0));
    (*parentR).memset(static_cast<uint8_t>(0));
    (*parentC).memset(static_cast<uint8_t>(0));
}

void tryVisit(Franko_Static_Array<Franko_Static_Array<uint32_t, 13>, 12>* grid, Franko_Static_Array<Franko_Static_Array<uint8_t, 13>, 12>* visited, Franko_Static_Array<Franko_Static_Array<uint32_t, 13>, 12>* dist, Franko_Static_Array<Franko_Static_Array<uint32_t, 13>, 12>* parentR, Franko_Static_Array<Franko_Static_Array<uint32_t, 13>, 12>* parentC, Franko_Dynamic_Array<uint32_t>* queue, uint32_t* tail, uint32_t qCap, uint32_t cols, uint32_t r, uint32_t c, uint32_t nr, uint32_t nc)
{
    if (static_cast<uint8_t>((static_cast<uint8_t>(((((*grid)[nr])[nc]) != 1)) && static_cast<uint8_t>(((((*visited)[nr])[nc]) == 0)))))
    {
        (((*visited)[nr])[nc]) = static_cast<uint8_t>(1);
        (((*dist)[nr])[nc]) = static_cast<uint32_t>(((((*dist)[r])[c]) + 1));
        (((*parentR)[nr])[nc]) = r;
        (((*parentC)[nr])[nc]) = c;
        ((*queue)[(*tail)]) = static_cast<uint32_t>((static_cast<uint32_t>((nr * cols)) + nc));
        (*tail) = static_cast<uint32_t>(((*tail) + 1));
        if (static_cast<uint8_t>(((*tail) == qCap)))
        {
            (*tail) = static_cast<uint32_t>(0);
        }
    }
}

uint8_t runBfs(Franko_Static_Array<Franko_Static_Array<uint32_t, 13>, 12>* grid, Franko_Static_Array<Franko_Static_Array<uint8_t, 13>, 12>* visited, Franko_Static_Array<Franko_Static_Array<uint32_t, 13>, 12>* dist, Franko_Static_Array<Franko_Static_Array<uint32_t, 13>, 12>* parentR, Franko_Static_Array<Franko_Static_Array<uint32_t, 13>, 12>* parentC, Franko_Dynamic_Array<uint32_t>* queue, uint32_t qCap, uint32_t cols, uint32_t startR, uint32_t startC, uint32_t goalR, uint32_t goalC)
{
    uint32_t head;
    head = static_cast<uint32_t>(0);
    uint32_t tail;
    tail = static_cast<uint32_t>(0);
    uint32_t r;
    r = static_cast<uint32_t>(0);
    uint32_t c;
    c = static_cast<uint32_t>(0);
    uint32_t pos;
    pos = static_cast<uint32_t>(0);
    uint32_t nr;
    nr = static_cast<uint32_t>(0);
    uint32_t nc;
    nc = static_cast<uint32_t>(0);
    uint8_t found;
    found = static_cast<uint8_t>(0);
    (((*visited)[startR])[startC]) = static_cast<uint8_t>(1);
    (((*dist)[startR])[startC]) = static_cast<uint32_t>(0);
    ((*queue)[tail]) = static_cast<uint32_t>((static_cast<uint32_t>((startR * cols)) + startC));
    tail = static_cast<uint32_t>((tail + 1));
    if (static_cast<uint8_t>((tail == qCap)))
    {
        tail = static_cast<uint32_t>(0);
    }
    while (static_cast<uint8_t>((static_cast<uint8_t>((head != tail)) && static_cast<uint8_t>((found == 0)))))
    {
        pos = ((*queue)[head]);
        head = static_cast<uint32_t>((head + 1));
        if (static_cast<uint8_t>((head == qCap)))
        {
            head = static_cast<uint32_t>(0);
        }
        r = static_cast<uint32_t>((pos / cols));
        c = static_cast<uint32_t>((pos - static_cast<uint32_t>((r * cols))));
        if (static_cast<uint8_t>((static_cast<uint8_t>((r == goalR)) && static_cast<uint8_t>((c == goalC)))))
        {
            found = static_cast<uint8_t>(1);
        }
        if (static_cast<uint8_t>((found == 0)))
        {
            nr = static_cast<uint32_t>((r - 1));
            nc = c;
            tryVisit(grid, visited, dist, parentR, parentC, queue, (&tail), qCap, cols, r, c, nr, nc);
            nr = static_cast<uint32_t>((r + 1));
            nc = c;
            tryVisit(grid, visited, dist, parentR, parentC, queue, (&tail), qCap, cols, r, c, nr, nc);
            nr = r;
            nc = static_cast<uint32_t>((c - 1));
            tryVisit(grid, visited, dist, parentR, parentC, queue, (&tail), qCap, cols, r, c, nr, nc);
            nr = r;
            nc = static_cast<uint32_t>((c + 1));
            tryVisit(grid, visited, dist, parentR, parentC, queue, (&tail), qCap, cols, r, c, nr, nc);
        }
    }
    return found;
}

void reconstructPath(Franko_Static_Array<Franko_Static_Array<uint8_t, 13>, 12>* path, Franko_Static_Array<Franko_Static_Array<uint32_t, 13>, 12>* parentR, Franko_Static_Array<Franko_Static_Array<uint32_t, 13>, 12>* parentC, uint32_t startR, uint32_t startC, uint32_t goalR, uint32_t goalC)
{
    uint32_t r;
    r = static_cast<uint32_t>(0);
    uint32_t c;
    c = static_cast<uint32_t>(0);
    uint32_t nr;
    nr = static_cast<uint32_t>(0);
    uint32_t nc;
    nc = static_cast<uint32_t>(0);
    r = goalR;
    c = goalC;
    while (static_cast<uint8_t>((static_cast<uint8_t>((r != startR)) || static_cast<uint8_t>((c != startC)))))
    {
        (((*path)[r])[c]) = static_cast<uint8_t>(1);
        nr = (((*parentR)[r])[c]);
        nc = (((*parentC)[r])[c]);
        r = nr;
        c = nc;
    }
    (((*path)[startR])[startC]) = static_cast<uint8_t>(1);
}

void printSolvedGrid(Franko_Static_Array<Franko_Static_Array<uint32_t, 13>, 12>* grid, Franko_Static_Array<Franko_Static_Array<uint8_t, 13>, 12>* path, Franko_Static_Array<uint32_t, 13>* line, uint32_t rows, uint32_t cols, uint32_t startR, uint32_t startC, uint32_t goalR, uint32_t goalC)
{
    uint32_t r;
    r = static_cast<uint32_t>(0);
    uint32_t c;
    c = static_cast<uint32_t>(0);
    while (static_cast<uint8_t>((r < rows)))
    {
        (*line).memcpy((&((*grid)[r])));
        c = static_cast<uint32_t>(0);
        while (static_cast<uint8_t>((c < cols)))
        {
            if (static_cast<uint8_t>(((((*path)[r])[c]) != 0)))
            {
                ((*line)[c]) = static_cast<uint32_t>(4);
            }
            c = static_cast<uint32_t>((c + 1));
        }
        if (static_cast<uint8_t>((r == startR)))
        {
            ((*line)[startC]) = static_cast<uint32_t>(2);
        }
        if (static_cast<uint8_t>((r == goalR)))
        {
            ((*line)[goalC]) = static_cast<uint32_t>(3);
        }
        std::cout << ((*line)[static_cast<uint32_t>(0)]) << ' ' << ((*line)[static_cast<uint32_t>(1)]) << ' ' << ((*line)[static_cast<uint32_t>(2)]) << ' ' << ((*line)[static_cast<uint32_t>(3)]) << ' ' << ((*line)[static_cast<uint32_t>(4)]) << ' ' << ((*line)[static_cast<uint32_t>(5)]) << ' ' << ((*line)[static_cast<uint32_t>(6)]) << ' ' << ((*line)[static_cast<uint32_t>(7)]) << ' ' << ((*line)[static_cast<uint32_t>(8)]) << ' ' << ((*line)[static_cast<uint32_t>(9)]) << ' ' << ((*line)[static_cast<uint32_t>(10)]) << ' ' << ((*line)[static_cast<uint32_t>(11)]) << ' ' << ((*line)[static_cast<uint32_t>(12)]) << '\n';
        r = static_cast<uint32_t>((r + 1));
    }
}

int32_t main()
{
    Franko_Static_Array<Franko_Static_Array<uint32_t, 13>, 12> grid;
    Franko_Static_Array<Franko_Static_Array<uint8_t, 13>, 12> visited;
    Franko_Static_Array<Franko_Static_Array<uint8_t, 13>, 12> path;
    Franko_Static_Array<Franko_Static_Array<uint32_t, 13>, 12> dist;
    Franko_Static_Array<Franko_Static_Array<uint32_t, 13>, 12> parentR;
    Franko_Static_Array<Franko_Static_Array<uint32_t, 13>, 12> parentC;
    Franko_Static_Array<uint32_t, 13> line;
    Franko_Dynamic_Array<uint32_t>* queue = static_cast<Franko_Dynamic_Array<uint32_t>*>(je_malloc(sizeof(Franko_Dynamic_Array<uint32_t>)));
    if (!queue) throw std::bad_alloc();
    new (queue) Franko_Dynamic_Array<uint32_t>;
    uint32_t rows;
    rows = static_cast<uint32_t>(12);
    uint32_t cols;
    cols = static_cast<uint32_t>(13);
    uint32_t qCap;
    qCap = static_cast<uint32_t>(256);
    uint32_t startR;
    startR = static_cast<uint32_t>(1);
    uint32_t startC;
    startC = static_cast<uint32_t>(1);
    uint32_t goalR;
    goalR = static_cast<uint32_t>(10);
    uint32_t goalC;
    goalC = static_cast<uint32_t>(11);
    uint8_t found;
    found = static_cast<uint8_t>(0);
    int32_t qStatus;
    qStatus = static_cast<int32_t>(0);
    uint32_t* answer;
    qStatus = (*queue).init_zero(qCap);
    if (static_cast<uint8_t>((qStatus != 0)))
    {
        queue->~Franko_Dynamic_Array<uint32_t>();
        je_free(queue);
        queue = nullptr;
        return static_cast<int32_t>(static_cast<int32_t>((-1)));
    }
    initArrays((&grid), (&visited), (&path), (&dist), (&parentR), (&parentC));
    found = runBfs((&grid), (&visited), (&dist), (&parentR), (&parentC), (&(*queue)), qCap, cols, startR, startC, goalR, goalC);
    (*queue).uninit();
    queue->~Franko_Dynamic_Array<uint32_t>();
    je_free(queue);
    queue = nullptr;
    if (static_cast<uint8_t>((found != 0)))
    {
        reconstructPath((&path), (&parentR), (&parentC), startR, startC, goalR, goalC);
    }
    std::cout << 4001 << '\n';
    if (static_cast<uint8_t>((found != 0)))
    {
        answer = (&((dist[goalR])[goalC]));
        std::cout << (*answer) << '\n';
    }
    else
    {
        std::cout << static_cast<int32_t>((-1)) << '\n';
    }
    std::cout << 4002 << '\n';
    printSolvedGrid((&grid), (&path), (&line), rows, cols, startR, startC, goalR, goalC);
    return static_cast<int32_t>(0);
}
