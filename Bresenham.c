#include <graphics.h>
#include <conio.h>
#include <math.h>

void bresenham(int x1, int y1, int x2, int y2) {
    int dx = x2 - x1;
    int dy = y2 - y1;
    int pk = 2 * dy - dx;

    int x = x1;
    int y = y1;

    putpixel(x, y, BLACK);
    for(int k = 0; k < dx; k++) {
        if(pk < 0) {
            x = x + 1;
            pk = pk + 2 * dy;
        }
        else {
            x = x + 1;
            y = y + 1;
            pk = pk + 2 * dy - 2 * dx;
        }
        putpixel(x, y, BLACK);
        delay(5);
    }
}

int main() {
    int gd = DETECT, gm;
    initgraph(&gd, &gm, "");

    setbkcolor(WHITE);
    cleardevice();
    
    bresenham(100, 100, 400, 300);

    getch();
    closegraph(0);
    return 0;
}