#include <graphics.h>
#include <conio.h>

int main() {
    int gd = DETECT, gm;
    initgraph(&gd, &gm, "");

    line(320, 0, 320, 480);
    line(0, 240, 640, 240);

    int x1 = 40, y1 = 0;
    int x2 = 120, y2 = 0;
    int x3 = 80, y3 = 80;

    // ORIGINAL
    setcolor(WHITE);
    line(320+x1,240-y1,320+x2,240-y2);
    line(320+x2,240-y2,320+x3,240-y3);
    line(320+x3,240-y3,320+x1,240-y1);

    float shx = 0.5;

    // X-SHEAR (y unchanged)
    int nx1 = x1 + shx * y1;
    int ny1 = y1;

    int nx2 = x2 + shx * y2;
    int ny2 = y2;

    int nx3 = x3 + shx * y3;
    int ny3 = y3;

    // NO SHIFT → intentional overlap
    setcolor(RED);
    line(320+nx1,240-ny1,320+nx2,240-ny2);
    line(320+nx2,240-ny2,320+nx3,240-ny3);
    line(320+nx3,240-ny3,320+nx1,240-ny1);

    getch();
    closegraph(0);
}