#include <graphics.h>
#include <conio.h>
#include <math.h>

int main() {
    int gd = DETECT, gm;
    initgraph(&gd, &gm, "");

    line(320, 0, 320, 480);
    line(0, 240, 640, 240);

    int x1 = 40, y1 = 40;
    int x2 = 120, y2 = 40;
    int x3 = 80, y3 = 100;

    // Draw original triangle
    setcolor(WHITE);
    line(320+x1,240-y1,320+x2,240-y2);
    line(320+x2,240-y2,320+x3,240-y3);
    line(320+x3,240-y3,320+x1,240-y1);

    float angle = 45 * 3.14159 / 180;

    // Arbitrary pivot
    float xp = 80;
    float yp = 60;

    // Draw pivot point
    setcolor(YELLOW);
    circle(320+xp,240-yp,3);

    // Rotate each point
    float nx1 = xp + (x1-xp)*cos(angle) - (y1-yp)*sin(angle);
    float ny1 = yp + (x1-xp)*sin(angle) + (y1-yp)*cos(angle);

    float nx2 = xp + (x2-xp)*cos(angle) - (y2-yp)*sin(angle);
    float ny2 = yp + (x2-xp)*sin(angle) + (y2-yp)*cos(angle);

    float nx3 = xp + (x3-xp)*cos(angle) - (y3-yp)*sin(angle);
    float ny3 = yp + (x3-xp)*sin(angle) + (y3-yp)*cos(angle);

    setcolor(RED);
    line(320+(int)nx1,240-(int)ny1,320+(int)nx2,240-(int)ny2);
    line(320+(int)nx2,240-(int)ny2,320+(int)nx3,240-(int)ny3);
    line(320+(int)nx3,240-(int)ny3,320+(int)nx1,240-(int)ny1);

    getch();
    closegraph(0);
}