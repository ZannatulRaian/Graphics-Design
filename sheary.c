#include <graphics.h>
#include <conio.h>

int main() {
    int gd = DETECT, gm;
    initgraph(&gd, &gm, "");

    // Draw coordinate axes
    line(320, 0, 320, 480);   // Y-axis
    line(0, 240, 640, 240);   // X-axis

    // Triangle positioned vertically (along Y direction)
    int x1 = 0,  y1 = 40;
    int x2 = 0,  y2 = 120;
    int x3 = 80, y3 = 80;

    // ORIGINAL TRIANGLE
    setcolor(WHITE);
    line(320+x1, 240-y1, 320+x2, 240-y2);
    line(320+x2, 240-y2, 320+x3, 240-y3);
    line(320+x3, 240-y3, 320+x1, 240-y1);

    float shy = 0.5;

    // Y-SHEAR transformation
    float nx1 = x1;
    float ny1 = y1 + shy * x1;

    float nx2 = x2;
    float ny2 = y2 + shy * x2;

    float nx3 = x3;
    float ny3 = y3 + shy * x3;

    // Draw sheared triangle
    setcolor(RED);
    line(320+nx1, 240-ny1, 320+nx2, 240-ny2);
    line(320+nx2, 240-ny2, 320+nx3, 240-ny3);
    line(320+nx3, 240-ny3, 320+nx1, 240-ny1);

    getch();
    closegraph(0);
}