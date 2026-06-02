#include "FrankoRuntime.hpp"

int main() {
    Franko_Static_Array<Franko_Static_Array<uint32_t, 15>, 15> map;
    Franko_Static_Array<Franko_Static_Array<uint8_t, 15>, 15> visited;
    Franko_Static_Array<Franko_Static_Array<uint32_t, 15>, 15> dist;
    Franko_Static_Array<Franko_Static_Array<uint8_t, 15>, 15> path;
    Franko_Static_Array<Franko_Static_Array<uint32_t, 15>, 15> parentR;
    Franko_Static_Array<Franko_Static_Array<uint32_t, 15>, 15> parentC;
    Franko_Static_Array<uint32_t, 10000>* q = static_cast<Franko_Static_Array<uint32_t, 10000>*>(std::malloc(sizeof(Franko_Static_Array<uint32_t, 10000>)));
    Franko_Static_Array<uint32_t, 15> line;
    uint32_t rows;
    rows = 15;
    uint32_t cols;
    cols = 15;
    uint32_t startR;
    startR = 1;
    uint32_t startC;
    startC = 1;
    uint32_t goalR;
    goalR = 13;
    uint32_t goalC;
    goalC = 13;
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
    uint8_t done;
    done = 0;
    uint8_t bfsActive;
    bfsActive = 1;
    map.memset(0);
    visited.memset(0);
    dist.memset(0);
    path.memset(0);
    i = 0;
    while ((i < cols))
    {
        map[0][i] = 1;
        map[(rows - 1)][i] = 1;
        map[i][0] = 1;
        map[i][(cols - 1)] = 1;
        i = (i + 1);
    }
    c = 1;
    while ((c < (cols - 1)))
    {
        if ((c != 7))
        {
            map[3][c] = 1;
        }
        c = (c + 1);
    }
    c = 1;
    while ((c < (cols - 1)))
    {
        if ((c != 4))
        {
            map[6][c] = 1;
        }
        c = (c + 1);
    }
    c = 1;
    while ((c < (cols - 1)))
    {
        if ((c != 11))
        {
            map[9][c] = 1;
        }
        c = (c + 1);
    }
    c = 1;
    while ((c < (cols - 1)))
    {
        if ((c != 6))
        {
            map[12][c] = 1;
        }
        c = (c + 1);
    }
    map[startR][startC] = 2;
    map[goalR][goalC] = 3;
    visited[startR][startC] = 1;
    dist[startR][startC] = 0;
    (*q)[tail] = ((startR * cols) + startC);
    tail = (tail + 1);
    if ((tail == 10000))
    {
        tail = 0;
    }
    while ((bfsActive != 0))
    {
        if ((head != tail))
        {
            pos = (*q)[head];
            head = (head + 1);
            if ((head == 10000))
            {
                head = 0;
            }
            r = (pos / cols);
            c = (pos - (r * cols));
            if ((r == goalR))
            {
                if ((c == goalC))
                {
                    found = 1;
                    bfsActive = 0;
                }
            }
            if ((bfsActive != 0))
            {
                nr = (r - 1);
                nc = c;
                if ((map[nr][nc] != 1))
                {
                    if ((visited[nr][nc] == 0))
                    {
                        visited[nr][nc] = 1;
                        dist[nr][nc] = (dist[r][c] + 1);
                        parentR[nr][nc] = r;
                        parentC[nr][nc] = c;
                        (*q)[tail] = ((nr * cols) + nc);
                        tail = (tail + 1);
                        if ((tail == 10000))
                        {
                            tail = 0;
                        }
                    }
                }
                nr = (r + 1);
                nc = c;
                if ((map[nr][nc] != 1))
                {
                    if ((visited[nr][nc] == 0))
                    {
                        visited[nr][nc] = 1;
                        dist[nr][nc] = (dist[r][c] + 1);
                        parentR[nr][nc] = r;
                        parentC[nr][nc] = c;
                        (*q)[tail] = ((nr * cols) + nc);
                        tail = (tail + 1);
                        if ((tail == 10000))
                        {
                            tail = 0;
                        }
                    }
                }
                nr = r;
                nc = (c - 1);
                if ((map[nr][nc] != 1))
                {
                    if ((visited[nr][nc] == 0))
                    {
                        visited[nr][nc] = 1;
                        dist[nr][nc] = (dist[r][c] + 1);
                        parentR[nr][nc] = r;
                        parentC[nr][nc] = c;
                        (*q)[tail] = ((nr * cols) + nc);
                        tail = (tail + 1);
                        if ((tail == 10000))
                        {
                            tail = 0;
                        }
                    }
                }
                nr = r;
                nc = (c + 1);
                if ((map[nr][nc] != 1))
                {
                    if ((visited[nr][nc] == 0))
                    {
                        visited[nr][nc] = 1;
                        dist[nr][nc] = (dist[r][c] + 1);
                        parentR[nr][nc] = r;
                        parentC[nr][nc] = c;
                        (*q)[tail] = ((nr * cols) + nc);
                        tail = (tail + 1);
                        if ((tail == 10000))
                        {
                            tail = 0;
                        }
                    }
                }
            }
        }
        else
        {
            bfsActive = 0;
        }
    }
    if ((found != 0))
    {
        r = goalR;
        c = goalC;
        done = 0;
        while ((done == 0))
        {
            path[r][c] = 1;
            if ((r == startR))
            {
                if ((c == startC))
                {
                    done = 1;
                }
            }
            if ((done == 0))
            {
                nr = parentR[r][c];
                nc = parentC[r][c];
                r = nr;
                c = nc;
            }
        }
    }
    std::cout << 1001 << '\n';
    if ((found != 0))
    {
        std::cout << dist[goalR][goalC] << '\n';
    }
    else
    {
        std::cout << (-1) << '\n';
    }
    std::cout << 1002 << '\n';
    r = 0;
    while ((r < rows))
    {
        c = 0;
        while ((c < cols))
        {
            line[c] = map[r][c];
            if ((path[r][c] != 0))
            {
                line[c] = 4;
            }
            c = (c + 1);
        }
        if ((r == startR))
        {
            line[startC] = 2;
        }
        if ((r == goalR))
        {
            line[goalC] = 3;
        }
        std::cout << line[0] << ' ' << line[1] << ' ' << line[2] << ' ' << line[3] << ' ' << line[4] << ' ' << line[5] << ' ' << line[6] << ' ' << line[7] << ' ' << line[8] << ' ' << line[9] << ' ' << line[10] << ' ' << line[11] << ' ' << line[12] << ' ' << line[13] << ' ' << line[14] << '\n';
        r = (r + 1);
    }
    std::free(q);
    return 0;
}