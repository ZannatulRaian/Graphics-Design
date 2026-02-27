#include <graphics.h>
#include <conio.h>

int main() {
    int gd = DETECT, gm;
    initgraph(&gd, &gm, "");

    setcolor(BLUE);                 // Border color
    rectangle(200, 150, 450, 300);  // Draw rectangle

    setfillstyle(SOLID_FILL, GREEN); // Fill style + fill color
    floodfill(250, 200, BLUE);       // Fill inside rectangle

    getch();
    closegraph(0);
    return 0;
}
