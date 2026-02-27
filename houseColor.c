#include <graphics.h>
#include <conio.h>

int main() {
    int gd = DETECT, gm;
    initgraph(&gd, &gm, "");

    // ---------- House Body ----------
    setcolor(BROWN);             // Outline color
    setfillstyle(SOLID_FILL, LIGHTRED);  // Fill color
    rectangle(200, 250, 450, 400);
    floodfill(201, 251, BROWN); // Fill inside the rectangle

    // ---------- Roof ----------
    setcolor(DARKGRAY);
    setfillstyle(SOLID_FILL, RED); // Roof color
    int roof[8] = {200, 250, 325, 150, 450, 250, 200, 250};
    fillpoly(4, roof); // 4 points of the triangle roof

    // ---------- Door ----------
    setcolor(BLACK);
    setfillstyle(SOLID_FILL, DARKGRAY);
    rectangle(300, 320, 350, 400);
    floodfill(301, 321, BLACK);

    // ---------- Windows ----------
    setcolor(BLUE);
    setfillstyle(SOLID_FILL, CYAN);
    rectangle(230, 280, 280, 330);
    floodfill(231, 281, BLUE);

    rectangle(370, 280, 420, 330);
    floodfill(371, 281, BLUE);

    // ---------- Grass ----------
    setcolor(GREEN);
    setfillstyle(SOLID_FILL, GREEN);
    rectangle(0, 400, 640, 480);
    floodfill(1, 401, GREEN);

    getch();
    closegraph(0);
    return 0;
}
