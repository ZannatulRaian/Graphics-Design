#include <graphics.h>
#include <conio.h>

int main() {
    int gd = DETECT, gm;
    initgraph(&gd, &gm, "");

    line(320, 0, 320, 480);
    line(0, 240, 640, 240);

    int x1 = 50, y1 = 50;
    int x2 = 150, y2 = 50;
    int x3 = 100, y3 = 120;

    // Original
    setcolor(WHITE);
    line(320+x1,240-y1,320+x2,240-y2);
    line(320+x2,240-y2,320+x3,240-y3);
    line(320+x3,240-y3,320+x1,240-y1);

    // Reflection over X-axis (y -> -y)
    int nx1 = x1, ny1 = -y1;
    int nx2 = x2, ny2 = -y2;
    int nx3 = x3, ny3 = -y3;

    setcolor(RED);
    line(320+nx1,240-ny1,320+nx2,240-ny2);
    line(320+nx2,240-ny2,320+nx3,240-ny3);
    line(320+nx3,240-ny3,320+nx1,240-ny1);

    getch();
    closegraph(0);
    return 0;
}