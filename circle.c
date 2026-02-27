#include <graphics.h>
#include <conio.h>

int main() {
    int gd = DETECT, gm;
    initgraph(&gd, &gm, "");

    setcolor(YELLOW);              // Border color
    circle(300, 200, 100);         // Draw circle

    setfillstyle(SOLID_FILL, RED); // Fill style + fill color
    floodfill(300, 200, YELLOW);   // Fill inside circle

    getch();
    closegraph(0);
    return 0;
}
