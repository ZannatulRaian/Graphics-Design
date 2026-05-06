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

    // ORIGINAL
    setcolor(WHITE);
    line(320+x1,240-y1,320+x2,240-y2);
    line(320+x2,240-y2,320+x3,240-y3);
    line(320+x3,240-y3,320+x1,240-y1);

    float angle = 45 * 3.14159 / 180;

    // rotation around origin (0,0) in Cartesian plane
    int nx1 = (int)(x1*cos(angle) - y1*sin(angle));
    int ny1 = (int)(x1*sin(angle) + y1*cos(angle));

    int nx2 = (int)(x2*cos(angle) - y2*sin(angle));
    int ny2 = (int)(x2*sin(angle) + y2*cos(angle));

    int nx3 = (int)(x3*cos(angle) - y3*sin(angle));
    int ny3 = (int)(x3*sin(angle) + y3*cos(angle));

    // shift so it stays visible (right side but not too far)
    int shiftX = 0;

    setcolor(RED);
    line(320+nx1+shiftX,240-ny1,320+nx2+shiftX,240-ny2);
    line(320+nx2+shiftX,240-ny2,320+nx3+shiftX,240-ny3);
    line(320+nx3+shiftX,240-ny3,320+nx1+shiftX,240-ny1);

    getch();
    closegraph(0);
}