#include "FrankoRuntime.hpp"

int main() {
    Franko_Static_Array<Franko_Static_Array<uint32_t, 12>, 12> grid;
    Franko_Static_Array<Franko_Static_Array<uint8_t, 12>, 12> visited;
    Franko_Static_Array<Franko_Static_Array<uint8_t, 12>, 12> path;
    Franko_Static_Array<Franko_Static_Array<uint32_t, 12>, 12> dist;
    Franko_Static_Array<Franko_Static_Array<uint32_t, 12>, 12> parentR;
    Franko_Static_Array<Franko_Static_Array<uint32_t, 12>, 12> parentC;
    Franko_Static_Array<uint32_t, 12> line;
    Franko_Dynamic_Array<uint32_t>* queue = new Franko_Dynamic_Array<uint32_t>();
    (*queue).init(256);
    uint32_t rows;
    rows = 12;
    uint32_t cols;
    cols = 12;
    uint32_t qCap;
    qCap = 256;
    uint32_t startR;
    startR = 1;
    uint32_t startC;
    startC = 1;
    uint32_t goalR;
    goalR = 10;
    uint32_t goalC;
    goalC = 10;
    uint32_t head;
    head = 0;
    uint32_t tail;
    tail = 0;
    uint32_t r;
    r = 0;
    uint32_t c;
    c = 0;
    uint32_t i;
    i = 0;
    uint32_t pos;
    pos = 0;
    uint32_t nr;
    nr = 0;
    uint32_t nc;
    nc = 0;
    uint8_t found;
    found = 0;
    uint32_t* answer;
    grid.memset(0);
    visited.memset(0);
    path.memset(0);
    dist.memset(0);
    i = 0;
    while (static_cast<uint8_t>((i < cols)))
    {
        ((grid[0])[i]) = 1;
        ((grid[static_cast<uint32_t>((rows - 1))])[i]) = 1;
        ((grid[i])[0]) = 1;
        ((grid[i])[static_cast<uint32_t>((cols - 1))]) = 1;
        i = static_cast<uint32_t>((i + 1));
    }
    c = 1;
    while (static_cast<uint8_t>((c < static_cast<uint32_t>((cols - 1)))))
    {
        if (static_cast<uint8_t>((c != 8)))
        {
            ((grid[3])[c]) = 1;
        }
        c = static_cast<uint32_t>((c + 1));
    }
    c = 1;
    while (static_cast<uint8_t>((c < static_cast<uint32_t>((cols - 1)))))
    {
        if (static_cast<uint8_t>((c != 2)))
        {
            ((grid[6])[c]) = 1;
        }
        c = static_cast<uint32_t>((c + 1));
    }
    c = 1;
    while (static_cast<uint8_t>((c < static_cast<uint32_t>((cols - 1)))))
    {
        if (static_cast<uint8_t>((c != 9)))
        {
            ((grid[8])[c]) = 1;
        }
        c = static_cast<uint32_t>((c + 1));
    }
    r = 1;
    while (static_cast<uint8_t>((r < static_cast<uint32_t>((rows - 1)))))
    {
        if (static_cast<uint8_t>((static_cast<uint8_t>((static_cast<uint8_t>((static_cast<uint8_t>((r != 2)) && static_cast<uint8_t>((r != 5)))) && static_cast<uint8_t>((r != 7)))) && static_cast<uint8_t>((r != 9)))))
        {
            ((grid[r])[5]) = 1;
        }
        r = static_cast<uint32_t>((r + 1));
    }
    ((grid[2])[2]) = 1;
    ((grid[2])[3]) = 1;
    ((grid[5])[8]) = 1;
    ((grid[9])[7]) = 1;
    ((grid[10])[7]) = 1;
    ((grid[startR])[startC]) = 2;
    ((grid[goalR])[goalC]) = 3;
    ((visited[startR])[startC]) = 1;
    ((dist[startR])[startC]) = 0;
    ((*queue)[tail]) = static_cast<uint32_t>((static_cast<uint32_t>((startR * cols)) + startC));
    tail = static_cast<uint32_t>((tail + 1));
    if (static_cast<uint8_t>((tail == qCap)))
    {
        tail = 0;
    }
    while (static_cast<uint8_t>((static_cast<uint8_t>((head != tail)) && static_cast<uint8_t>((found == 0)))))
    {
        pos = ((*queue)[head]);
        head = static_cast<uint32_t>((head + 1));
        if (static_cast<uint8_t>((head == qCap)))
        {
            head = 0;
        }
        r = static_cast<uint32_t>((pos / cols));
        c = static_cast<uint32_t>((pos - static_cast<uint32_t>((r * cols))));
        if (static_cast<uint8_t>((static_cast<uint8_t>((r == goalR)) && static_cast<uint8_t>((c == goalC)))))
        {
            found = 1;
        }
        if (static_cast<uint8_t>((found == 0)))
        {
            nr = static_cast<uint32_t>((r - 1));
            nc = c;
            if (static_cast<uint8_t>((static_cast<uint8_t>((((grid[nr])[nc]) != 1)) && static_cast<uint8_t>((((visited[nr])[nc]) == 0)))))
            {
                ((visited[nr])[nc]) = 1;
                ((dist[nr])[nc]) = static_cast<uint32_t>((((dist[r])[c]) + 1));
                ((parentR[nr])[nc]) = r;
                ((parentC[nr])[nc]) = c;
                ((*queue)[tail]) = static_cast<uint32_t>((static_cast<uint32_t>((nr * cols)) + nc));
                tail = static_cast<uint32_t>((tail + 1));
                if (static_cast<uint8_t>((tail == qCap)))
                {
                    tail = 0;
                }
            }
            nr = static_cast<uint32_t>((r + 1));
            nc = c;
            if (static_cast<uint8_t>((static_cast<uint8_t>((((grid[nr])[nc]) != 1)) && static_cast<uint8_t>((((visited[nr])[nc]) == 0)))))
            {
                ((visited[nr])[nc]) = 1;
                ((dist[nr])[nc]) = static_cast<uint32_t>((((dist[r])[c]) + 1));
                ((parentR[nr])[nc]) = r;
                ((parentC[nr])[nc]) = c;
                ((*queue)[tail]) = static_cast<uint32_t>((static_cast<uint32_t>((nr * cols)) + nc));
                tail = static_cast<uint32_t>((tail + 1));
                if (static_cast<uint8_t>((tail == qCap)))
                {
                    tail = 0;
                }
            }
            nr = r;
            nc = static_cast<uint32_t>((c - 1));
            if (static_cast<uint8_t>((static_cast<uint8_t>((((grid[nr])[nc]) != 1)) && static_cast<uint8_t>((((visited[nr])[nc]) == 0)))))
            {
                ((visited[nr])[nc]) = 1;
                ((dist[nr])[nc]) = static_cast<uint32_t>((((dist[r])[c]) + 1));
                ((parentR[nr])[nc]) = r;
                ((parentC[nr])[nc]) = c;
                ((*queue)[tail]) = static_cast<uint32_t>((static_cast<uint32_t>((nr * cols)) + nc));
                tail = static_cast<uint32_t>((tail + 1));
                if (static_cast<uint8_t>((tail == qCap)))
                {
                    tail = 0;
                }
            }
            nr = r;
            nc = static_cast<uint32_t>((c + 1));
            if (static_cast<uint8_t>((static_cast<uint8_t>((((grid[nr])[nc]) != 1)) && static_cast<uint8_t>((((visited[nr])[nc]) == 0)))))
            {
                ((visited[nr])[nc]) = 1;
                ((dist[nr])[nc]) = static_cast<uint32_t>((((dist[r])[c]) + 1));
                ((parentR[nr])[nc]) = r;
                ((parentC[nr])[nc]) = c;
                ((*queue)[tail]) = static_cast<uint32_t>((static_cast<uint32_t>((nr * cols)) + nc));
                tail = static_cast<uint32_t>((tail + 1));
                if (static_cast<uint8_t>((tail == qCap)))
                {
                    tail = 0;
                }
            }
        }
    }
    delete queue;
    queue = nullptr;
    if (static_cast<uint8_t>((found != 0)))
    {
        r = goalR;
        c = goalC;
        while (static_cast<uint8_t>((static_cast<uint8_t>((r != startR)) || static_cast<uint8_t>((c != startC)))))
        {
            ((path[r])[c]) = 1;
            nr = ((parentR[r])[c]);
            nc = ((parentC[r])[c]);
            r = nr;
            c = nc;
        }
        ((path[startR])[startC]) = 1;
    }
    std::cout << 2001 << '\n';
    if (static_cast<uint8_t>((found != 0)))
    {
        answer = (&((dist[goalR])[goalC]));
        std::cout << (*answer) << '\n';
    }
    else
    {
        std::cout << static_cast<int32_t>((-1)) << '\n';
    }
    std::cout << 2002 << '\n';
    r = 0;
    while (static_cast<uint8_t>((r < rows)))
    {
        line.memcpy((grid[r]));
        c = 0;
        while (static_cast<uint8_t>((c < cols)))
        {
            if (static_cast<uint8_t>((((path[r])[c]) != 0)))
            {
                (line[c]) = 4;
            }
            c = static_cast<uint32_t>((c + 1));
        }
        if (static_cast<uint8_t>((r == startR)))
        {
            (line[startC]) = 2;
        }
        if (static_cast<uint8_t>((r == goalR)))
        {
            (line[goalC]) = 3;
        }
        std::cout << (line[0]) << ' ' << (line[1]) << ' ' << (line[2]) << ' ' << (line[3]) << ' ' << (line[4]) << ' ' << (line[5]) << ' ' << (line[6]) << ' ' << (line[7]) << ' ' << (line[8]) << ' ' << (line[9]) << ' ' << (line[10]) << ' ' << (line[11]) << '\n';
        r = static_cast<uint32_t>((r + 1));
    }
    return 0;
}