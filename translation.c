#include <graphics.h>
#include <conio.h>

int main() {
    int gd = DETECT, gm;
    initgraph(&gd, &gm, "");

    // Draw coordinate axes
    line(320, 0, 320, 480);
    line(0, 240, 640, 240);

    // Original triangle (Cartesian coordinates)
    int x1 = 100,  y1 = 100;
    int x2 = 200, y2 = 100;
    int x3 = 150, y3 = 70;

    // Convert to screen coordinates
    int ox1 = 320 + x1;
    int oy1 = 240 + y1;
    int ox2 = 320 + x2;
    int oy2 = 240 + y2;
    int ox3 = 320 + x3;
    int oy3 = 240 + y3;

    setcolor(WHITE);
    line(ox1, oy1, ox2, oy2);
    line(ox2, oy2, ox3, oy3);
    line(ox3, oy3, ox1, oy1);

    // Translation values
    int tx = 80;   // move right
    int ty = 100;  // move up

    // Translated coordinates (Cartesian)
    int nx1 = x1 + tx, ny1 = y1 + ty;
    int nx2 = x2 + tx, ny2 = y2 + ty;
    int nx3 = x3 + tx, ny3 = y3 + ty;

    // Convert translated points to screen coordinates
    int sx1 = 320 + nx1;
    int sy1 = 240 + ny1;
    int sx2 = 320 + nx2;
    int sy2 = 240 + ny2;
    int sx3 = 320 + nx3;
    int sy3 = 240 + ny3;

    setcolor(RED);
    line(sx1, sy1, sx2, sy2);
    line(sx2, sy2, sx3, sy3);
    line(sx3, sy3, sx1, sy1);

    getch();
    closegraph(0);

    return 0;
}