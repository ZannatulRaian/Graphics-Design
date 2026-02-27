#include <graphics.h>
#include <conio.h>

int main() {
    int gd = DETECT, gm;
    initgraph(&gd, &gm, "");

    // House body
    rectangle(200, 250, 450, 400);

    // Roof
    line(200, 250, 325, 150);
    line(325, 150, 450, 250);

    // Door
    rectangle(300, 320, 350, 400);

    // Window
    rectangle(230, 280, 280, 330);

    getch();
    closegraph(0);
    return 0;
}
