#include <graphics.h>
#include <conio.h>

int main() {
    int gd = DETECT, gm;
    initgraph(&gd, &gm, "");

    setbkcolor(WHITE);
    cleardevice();

    setcolor(BLACK);
    line(100, 100, 100, 400);

    setfillstyle(SOLID_FILL, GREEN);
    bar(100, 150, 400, 350);

    setfillstyle(SOLID_FILL, RED);
    fillellipse(250, 250, 60, 60);

    getch();
    closegraph(0);
    return 0;
}
