#include <graphics.h>
#include <conio.h>

int main() {
    int gd = DETECT, gm;
    initgraph(&gd, &gm, "");
    
    setbkcolor(WHITE);
    cleardevice();

    setcolor(BLUE);
    setlinestyle(SOLID_LINE, 0, 3);

    line(100, 100, 400, 300);

    getch();
    closegraph(0);
    return 0;

}