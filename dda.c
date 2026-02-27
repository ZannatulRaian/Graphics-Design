#include <graphics.h>
#include <conio.h>
#include <math.h>

void dda(int x1, int y1, int x2, int y2) {
    int dx = x2 - x1;
    int dy = y2 - y1;
    int steps = abs(dx) > abs(dy) ? abs(dx) : abs(dy);

    float xInc = dx / (float)steps;
    float yInc = dy / (float)steps;

    float x = x1;
    float y = y1;

    for(int i = 0; i <= steps; i++) {
        putpixel(round(x), round(y), BLACK);
        x += xInc;
        y += yInc;
    }
}

int main() {
    int gd = DETECT, gm;
    initgraph(&gd, &gm, "");
    
    setbkcolor(WHITE);
    cleardevice();

    dda(100, 100, 400, 300);

    getch();
    closegraph(0);
    return 0;
}