#include <graphics.h>
#include <conio.h>

int main() {
    int gd = DETECT, gm;
    initgraph(&gd, &gm, "");

    line(320, 0, 320, 480);
    line(0, 240, 640, 240);

    int x1 = 40, y1 = 40;
    int x2 = 120, y2 = 40;
    int x3 = 80, y3 = 100;

    // ORIGINAL (left)
    setcolor(WHITE);
    line(320+x1,240-y1,320+x2,240-y2);
    line(320+x2,240-y2,320+x3,240-y3);
    line(320+x3,240-y3,320+x1,240-y1);

    float sx = 1.6, sy = 1.6;

    int nx1 = x1*sx, ny1 = y1*sy;
    int nx2 = x2*sx, ny2 = y2*sy;
    int nx3 = x3*sx, ny3 = y3*sy;

    // RIGHT SIDE (safe shift)
    int shiftX = 120;

    setcolor(RED);
    line(320+nx1+shiftX,240-ny1,320+nx2+shiftX,240-ny2);
    line(320+nx2+shiftX,240-ny2,320+nx3+shiftX,240-ny3);
    line(320+nx3+shiftX,240-ny3,320+nx1+shiftX,240-ny1);

    getch();
    closegraph(0);
}