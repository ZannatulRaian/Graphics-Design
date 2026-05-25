import java.awt.*;
import java.awt.event.*;
import java.util.*;
import javax.swing.*;
import javax.swing.Timer;

public class ElevatorSimulation extends JFrame {

    static final int NUM_FLOORS   = 8;
    static final int FLOOR_HEIGHT = 82;
    static final int BUILDING_X   = 20;
    static final int BUILDING_W   = 380;
    static final int SHAFT_X      = BUILDING_X + (BUILDING_W / 2) - 50;
    static final int SHAFT_W      = 100;
    static final int CAB_H        = 56;
    static final int PANEL_W      = 255;
    static final int WIN_H        = NUM_FLOORS * FLOOR_HEIGHT + 20;
    static final int WIN_W        = BUILDING_X + BUILDING_W + PANEL_W + 30;

    // ── Lift colours
    static final Color C_BG       = new Color(12, 16, 28);
    static final Color C_CAB_TOP  = new Color(210, 225, 245);
    static final Color C_CAB_BOT  = new Color(175, 192, 218);
    static final Color C_DOOR_L   = new Color(178, 178, 182);
    static final Color C_DOOR_R   = new Color(160, 162, 168);
    static final Color C_CABLE    = new Color(160, 130, 80);
    static final Color C_LED_ON   = new Color(255, 212, 48);
    static final Color C_BTN_IDLE = new Color(48, 62, 88);
    static final Color C_BTN_HOV  = new Color(68, 88, 128);
    static final Color C_BTN_PEND = new Color(198, 138, 28);
    static final Color C_BTN_ACT  = new Color(58, 198, 118);
    static final Color C_TEXT     = new Color(198, 215, 240);
    static final Color C_UP       = new Color(78, 218, 138);
    static final Color C_DOWN     = new Color(218, 98, 78);

    // ── Building / lobby colours
    static final Color C_WALL_BEIGE   = new Color(220, 208, 186);
    static final Color C_WALL_SHADOW  = new Color(185, 172, 150);
    static final Color C_WALL_LIGHT   = new Color(238, 228, 210);
    static final Color C_SLAB_TOP     = new Color(80, 72, 62);
    static final Color C_SLAB_FACE    = new Color(55, 50, 42);
    static final Color C_FLOOR_TILE   = new Color(195, 188, 175);
    static final Color C_FLOOR_TILE2  = new Color(210, 204, 192);
    static final Color C_DOOR_FRAME   = new Color(100, 95, 90);
    static final Color C_DOOR_METAL   = new Color(175, 178, 182);
    static final Color C_DOOR_METAL2  = new Color(140, 142, 148);
    static final Color C_DOOR_SHINE   = new Color(220, 222, 228);
    static final Color C_DOOR_INSIDE  = new Color(22, 24, 35);

    // ── State
    double  cabY;
    int     currentFloor = 0;
    int     targetFloor  = 0;
    boolean moving       = false;
    boolean doorsOpen    = false;
    double  doorAnim     = 0;
    boolean doorOpening  = false;
    boolean doorClosing  = false;
    int     doorWait     = 0;

    enum Dir { IDLE, UP, DOWN }
    Dir dir = Dir.IDLE;

    // All pending requests
    Set<Integer> requests = new TreeSet<>();
    Set<Integer> pending = new HashSet<>();

    java.util.List<Particle> parts = new ArrayList<>();
    Random rng = new Random();

    boolean[] callUp = new boolean[NUM_FLOORS];
    boolean[] callDn = new boolean[NUM_FLOORS];

    Timer animTimer;
    static final int    ANIM_MS    = 16;
    static final double SPEED      = 2.0;
    static final double DOOR_SPD   = 0.035;
    static final int    DOOR_HOLD  = 130;

    DrawPanel canvas;

    public ElevatorSimulation() {
        super("Elevator Simulation  –  G to 7");
        cabY = floorToY(0);
        canvas = new DrawPanel();
        canvas.setPreferredSize(new Dimension(WIN_W, WIN_H));
        canvas.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { handleClick(e.getX(), e.getY()); }
        });
        canvas.addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                canvas.hx = e.getX(); canvas.hy = e.getY(); canvas.repaint();
            }
        });
        add(canvas);
        pack();
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);
        animTimer = new Timer(ANIM_MS, e -> tick());
        animTimer.start();
    }

    int floorToY(int f) {
        return (NUM_FLOORS - 1 - f) * FLOOR_HEIGHT;
    }

    int lobbyTop(int f)    { return floorToY(f) + 8; }
    int slabY(int f)       { return floorToY(f) + FLOOR_HEIGHT - 14; }
    int lobbyH(int f)      { return slabY(f) - lobbyTop(f); }

    void tick() {
        if (doorOpening) {
            doorAnim = Math.min(1, doorAnim + DOOR_SPD);
            if (doorAnim >= 1) { 
                doorOpening = false; 
                doorsOpen = true; 
                doorWait = DOOR_HOLD; 
                spawnParticles(); 
            }
        } else if (doorsOpen) {
            if (doorWait-- <= 0) { 
                doorsOpen = false; 
                doorClosing = true; 
            }
        } else if (doorClosing) {
            doorAnim = Math.max(0, doorAnim - DOOR_SPD);
            if (doorAnim <= 0) { 
                doorClosing = false; 
                nextMove(); 
            }
        }
        
        if (moving) {
            double ty = floorToY(targetFloor);
            if (Math.abs(cabY - ty) < SPEED) {
                cabY = ty; 
                currentFloor = targetFloor; 
                moving = false;
                pending.remove(currentFloor);
                if (dir == Dir.UP)   callUp[currentFloor] = false;
                if (dir == Dir.DOWN) callDn[currentFloor] = false;
                doorOpening = true; 
                doorAnim = 0;
            } else {
                cabY += (ty < cabY) ? -SPEED : SPEED;
            }
        }
        
        parts.removeIf(p -> { p.update(); return p.dead(); });
        canvas.repaint();
    }

    // REAL ELEVATOR ALGORITHM (SCAN/Elevator Algorithm)
    // Continue in current direction, serve all requests in that direction first
    // Then reverse direction if no more requests in current direction
    void nextMove() {
        if (moving || doorOpening || doorsOpen || doorClosing) return;
        if (requests.isEmpty()) {
            dir = Dir.IDLE;
            return;
        }

        Integer nextFloor = null;

        if (dir == Dir.IDLE) {
            // Pick closest floor to start
            nextFloor = findClosestFloor();
            dir = (nextFloor > currentFloor) ? Dir.UP : Dir.DOWN;
        } else if (dir == Dir.UP) {
            // Look for requests above current floor
            nextFloor = findNextFloorUp();
            // If no requests above, reverse direction
            if (nextFloor == null) {
                nextFloor = findNextFloorDown();
                if (nextFloor != null) dir = Dir.DOWN;
            }
        } else { // dir == Dir.DOWN
            // Look for requests below current floor
            nextFloor = findNextFloorDown();
            // If no requests below, reverse direction
            if (nextFloor == null) {
                nextFloor = findNextFloorUp();
                if (nextFloor != null) dir = Dir.UP;
            }
        }

        if (nextFloor != null) {
            targetFloor = nextFloor;
            moving = true;
            requests.remove(nextFloor);
        } else {
            dir = Dir.IDLE;
        }
    }

    // Find closest floor (for initial idle state)
    Integer findClosestFloor() {
        Integer closest = null;
        int minDist = Integer.MAX_VALUE;
        for (int f : requests) {
            int dist = Math.abs(f - currentFloor);
            if (dist < minDist) {
                minDist = dist;
                closest = f;
            }
        }
        return closest;
    }

    // Find next floor in UP direction (above current)
    Integer findNextFloorUp() {
        Integer next = null;
        for (int f : requests) {
            if (f > currentFloor) {
                if (next == null || f < next) {
                    next = f;
                }
            }
        }
        return next;
    }

    // Find next floor in DOWN direction (below current)
    Integer findNextFloorDown() {
        Integer next = null;
        for (int f : requests) {
            if (f < currentFloor) {
                if (next == null || f > next) {
                    next = f;
                }
            }
        }
        return next;
    }

    void request(int floor) {
        if (floor == currentFloor && !moving && !doorsOpen && !doorOpening && !doorClosing) {
            doorOpening = true; 
            doorAnim = 0; 
            return;
        }
        if (pending.contains(floor)) return;
        
        pending.add(floor);
        requests.add(floor);
        nextMove();
    }

    void spawnParticles() {
        double cx = SHAFT_X + SHAFT_W / 2.0, cy = cabY + CAB_H / 2.0;
        for (int i = 0; i < 45; i++)
            parts.add(new Particle(cx, cy,
                (rng.nextDouble()-0.5)*5, (rng.nextDouble()-1.5)*3,
                new Color(255, 50+rng.nextInt(180), 0, 220)));
    }

    void handleClick(int mx, int my) {
        // panel buttons
        int px = BUILDING_X + BUILDING_W + 20, sx = px+14, sy = 50, bsz = 36, cols = 4;
        for (int i = NUM_FLOORS-1; i >= 0; i--) {
            int col=(NUM_FLOORS-1-i)%cols, row=(NUM_FLOORS-1-i)/cols;
            int bx=sx+col*(bsz+8), by=sy+row*(bsz+8);
            if (mx>=bx&&mx<=bx+bsz&&my>=by&&my<=by+bsz) { request(i); return; }
        }
        // call buttons on wall
        for (int f = 0; f < NUM_FLOORS; f++) {
            int[] cb = callBtnPos(f);
            if (f < NUM_FLOORS-1 && mx>=cb[0]&&mx<=cb[0]+18&&my>=cb[1]&&my<=cb[1]+16) { 
                callUp[f]=true; 
                request(f); 
                return; 
            }
            if (f > 0 && mx>=cb[0]&&mx<=cb[0]+18&&my>=cb[1]+18&&my<=cb[1]+34) { 
                callDn[f]=true; 
                request(f); 
                return; 
            }
        }
    }

    int[] callBtnPos(int f) {
        int doorRight = SHAFT_X + SHAFT_W + 8;
        int btnX = doorRight + 10;
        int lt = lobbyTop(f);
        int lh = lobbyH(f);
        int btnY = lt + lh/2 - 18;
        return new int[]{btnX, btnY};
    }

    class Particle {
        double x, y, vx, vy; Color c; int life=40;
        Particle(double x,double y,double vx,double vy,Color c){this.x=x;this.y=y;this.vx=vx;this.vy=vy;this.c=c;}
        void update(){x+=vx;y+=vy;vy+=0.15;life--;}
        boolean dead(){return life<=0;}
    }

    class DrawPanel extends JPanel {
        int hx=-1, hy=-1;
        @Override protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D)g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            
            g.setColor(C_BG);
            g.fillRect(0, 0, WIN_W, WIN_H);
            
            for (int f = 0; f < NUM_FLOORS; f++) drawLobby(g, f);
            drawShaft(g);
            drawCable(g);
            drawCabin(g);
            for (Particle p : parts) {
                g.setColor(new Color(p.c.getRed(),p.c.getGreen(),p.c.getBlue(),(int)(p.life/40f*200)));
                g.fillOval((int)p.x-3,(int)p.y-3,6,6);
            }
            drawPanel(g);
            drawBottomBar(g);
        }

        void drawLobby(Graphics2D g, int f) {
            int lx  = BUILDING_X;
            int lt  = lobbyTop(f);
            int lw  = BUILDING_W;
            int lh  = lobbyH(f);
            int sl  = slabY(f);

            GradientPaint wallGrad = new GradientPaint(lx, lt, C_WALL_LIGHT, lx, sl, C_WALL_SHADOW);
            g.setPaint(wallGrad);
            g.fillRect(lx, lt, lw, lh);

            g.setColor(new Color(165, 152, 130, 120));
            g.setStroke(new BasicStroke(1));
            int tileH = lh / 3;
            for (int row = 1; row <= 2; row++) {
                int ty = lt + row * tileH;
                g.drawLine(lx, ty, lx + lw, ty);
            }
            int tileW = 60;
            g.setColor(new Color(165, 152, 130, 70));
            for (int tx = lx; tx < lx + lw; tx += tileW) {
                g.drawLine(tx, lt, tx, sl);
            }

            drawSpotlight(g, lx + lw/4,      lt, lw/4, lh);
            drawSpotlight(g, lx + 3*lw/4,    lt, lw/4, lh);

            int floorH = 14;
            int floorY = sl - floorH;
            for (int tx = lx; tx < lx+lw; tx += 30) {
                boolean dark = ((tx-lx)/30 % 2 == 0);
                g.setColor(dark ? C_FLOOR_TILE : C_FLOOR_TILE2);
                g.fillRect(tx, floorY, 30, floorH);
            }
            g.setColor(new Color(100, 90, 78));
            g.setStroke(new BasicStroke(1));
            g.drawLine(lx, floorY, lx+lw, floorY);

            int plantW = 22, plantH = lh - 12;
            int leftPlantX  = SHAFT_X - 70;
            int rightPlantX = SHAFT_X + SHAFT_W + 45;
            if (leftPlantX  > lx + 4)  drawPlant(g, leftPlantX,  floorY - plantH, plantW, plantH);
            if (rightPlantX < lx+lw-32) drawPlant(g, rightPlantX, floorY - plantH, plantW, plantH);

            int fdW   = SHAFT_W + 12;
            int fdH   = (int)(lh * 0.80);
            int fdX   = SHAFT_X - 7;
            int fdY   = floorY - fdH;

            g.setColor(C_DOOR_FRAME);
            g.fillRect(fdX, fdY, fdW, fdH);

            boolean cabAtThisFloor = (Math.abs(cabY - floorToY(f)) < 4);
            double fda = cabAtThisFloor ? doorAnim : 0.0;

            int idW = fdW - 10;
            int idH = fdH - 8;
            int idX = fdX + 5;
            int idY = fdY + 4;

            if (fda < 0.98) {
                int half = (int)(fda * idW / 2);
                int lpW = idW/2 - half;
                if (lpW > 0) {
                    GradientPaint lp = new GradientPaint(idX, idY, C_DOOR_SHINE,
                            idX + lpW, idY, C_DOOR_METAL2);
                    g.setPaint(lp);
                    g.fillRect(idX, idY, lpW, idH);
                    g.setColor(new Color(80, 82, 88));
                    g.setStroke(new BasicStroke(1));
                    g.drawRect(idX, idY, lpW, idH);
                    if (half < 5) {
                        g.setColor(new Color(100, 102, 108));
                        g.fillRoundRect(idX + lpW - 5, idY + idH/2 - 8, 4, 16, 2, 2);
                    }
                }

                int rpX = idX + idW/2 + half;
                int rpW = idW/2 - half;
                if (rpW > 0) {
                    GradientPaint rp = new GradientPaint(rpX, idY, C_DOOR_METAL2,
                            rpX + rpW, idY, C_DOOR_SHINE);
                    g.setPaint(rp);
                    g.fillRect(rpX, idY, rpW, idH);
                    g.setColor(new Color(80, 82, 88));
                    g.setStroke(new BasicStroke(1));
                    g.drawRect(rpX, idY, rpW, idH);
                    if (half < 5) {
                        g.setColor(new Color(100, 102, 108));
                        g.fillRoundRect(rpX + 1, idY + idH/2 - 8, 4, 16, 2, 2);
                    }
                }

                if (half > 0) {
                    int gapX = idX + idW/2 - half;
                    int gapW = 2 * half;
                    if (fda > 0.05) {
                        GradientPaint glow = new GradientPaint(gapX, idY,
                            new Color(255,240,200,(int)(fda*180)), gapX+gapW, idY, C_DOOR_INSIDE);
                        g.setPaint(glow);
                        g.fillRect(gapX, idY, gapW, idH);
                    }
                }
            } else {
                g.setColor(C_DOOR_INSIDE);
                g.fillRect(idX, idY, idW, idH);
                g.setColor(new Color(40, 42, 55));
                g.fillRect(idX, idY, 6, idH);
                g.fillRect(idX+idW-6, idY, 6, idH);
            }

            GradientPaint header = new GradientPaint(fdX, fdY, new Color(200,200,205),
                    fdX, fdY+8, new Color(130,130,138));
            g.setPaint(header);
            g.fillRect(fdX, fdY, fdW, 8);

            drawFloorLED(g, fdX + fdW/2 - 15, fdY - 17, f, cabAtThisFloor);

            g.setColor(new Color(240,232,218));
            g.setFont(new Font("Consolas", Font.BOLD, 13));
            String lbl = f == 0 ? "G" : String.valueOf(f);
            g.drawString(lbl, lx + 6, lt + lh/2 + 5);

            int[] cb = callBtnPos(f);
            if (f < NUM_FLOORS-1) drawWallBtn(g, cb[0], cb[1],    true,  callUp[f], hx,hy);
            if (f > 0)             drawWallBtn(g, cb[0], cb[1]+18, false, callDn[f], hx,hy);

            g.setColor(C_SLAB_TOP);
            g.fillRect(lx, sl, lw, 6);
            g.setColor(C_SLAB_FACE);
            g.fillRect(lx, sl+6, lw, 5);
        }

        void drawSpotlight(Graphics2D g, int cx, int top, int radius, int height) {
            int steps = 8;
            for (int i = steps; i >= 1; i--) {
                float alpha = (i / (float)steps) * 0.18f;
                int w = radius * i / steps;
                int h = height * i / steps;
                g.setColor(new Color(255, 248, 220, (int)(alpha * 255)));
                g.fillOval(cx - w/2, top, w, h);
            }
            g.setColor(new Color(255, 255, 230, 180));
            g.fillOval(cx - 4, top + 1, 8, 5);
        }

        void drawPlant(Graphics2D g, int px, int py, int pw, int ph) {
            int potH = ph / 4;
            int potY = py + ph - potH;
            g.setColor(new Color(240, 238, 232));
            int[] potX = {px, px+pw, px+pw-4, px+4};
            int[] potYa = {potY, potY, potY+potH, potY+potH};
            g.fillPolygon(potX, potYa, 4);
            g.setColor(new Color(180, 175, 165));
            g.setStroke(new BasicStroke(1));
            g.drawPolygon(potX, potYa, 4);
            g.setColor(new Color(80, 60, 40));
            g.fillRect(px+2, potY+2, pw-4, 4);
            Color lc1 = new Color(60, 120, 55);
            Color lc2 = new Color(45, 100, 42);
            Color lc3 = new Color(80, 145, 65);
            int stemX = px + pw/2, stemY = potY;
            int[][] leaves = {
                {stemX-10, stemY-28, 16, 22},
                {stemX-2,  stemY-36, 14, 20},
                {stemX+2,  stemY-26, 15, 22},
                {stemX-14, stemY-18, 12, 18},
                {stemX+4,  stemY-18, 12, 18},
            };
            Color[] lcs = {lc1, lc2, lc3, lc1, lc2};
            for (int i = 0; i < leaves.length; i++) {
                g.setColor(lcs[i]);
                g.fillOval(leaves[i][0], leaves[i][1], leaves[i][2], leaves[i][3]);
                g.setColor(new Color(30, 80, 28, 80));
                g.setStroke(new BasicStroke(1));
                g.drawOval(leaves[i][0], leaves[i][1], leaves[i][2], leaves[i][3]);
            }
        }

        void drawFloorLED(Graphics2D g, int x, int y, int floor, boolean active) {
            g.setColor(new Color(30, 30, 30));
            g.fillRoundRect(x, y, 30, 15, 4, 4);
            g.setColor(active ? C_LED_ON : new Color(60, 60, 60));
            g.setFont(new Font("Consolas", Font.BOLD, 10));
            String lbl = floor == 0 ? "G" : String.valueOf(floor);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(lbl, x + (30 - fm.stringWidth(lbl))/2, y + 11);
            if (active && dir != Dir.IDLE) {
                g.setColor(dir == Dir.UP ? new Color(80, 220, 120) : new Color(220, 80, 80));
                int ax = x + 22, ay = y + 3;
                if (dir == Dir.UP) {
                    int[] xs={ax,ax+4,ax+8}; int[] ys={ay+6,ay,ay+6}; g.fillPolygon(xs,ys,3);
                } else {
                    int[] xs={ax,ax+4,ax+8}; int[] ys={ay,ay+6,ay}; g.fillPolygon(xs,ys,3);
                }
            }
            g.setColor(new Color(70, 70, 70));
            g.setStroke(new BasicStroke(1));
            g.drawRoundRect(x, y, 30, 15, 4, 4);
        }

        void drawWallBtn(Graphics2D g, int x, int y, boolean up, boolean active, int mx, int my) {
            boolean hov = mx>=x&&mx<=x+18&&my>=y&&my<=y+16;
            Color bg = active ? (up ? new Color(50,180,100) : new Color(200,70,60))
                              : hov ? new Color(80,90,110) : new Color(55,60,72);
            g.setColor(new Color(40, 40, 45));
            g.fillRoundRect(x-2, y-2, 22, 20, 4, 4);
            g.setColor(bg);
            g.fillRoundRect(x, y, 18, 16, 3, 3);
            g.setColor(active ? Color.WHITE : new Color(190, 200, 215));
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int acx = x+9, acy = y+8;
            if (up) {
                int[] xs={acx-4,acx+4,acx}; int[] ys={acy+3,acy+3,acy-3}; g.fillPolygon(xs,ys,3);
            } else {
                int[] xs={acx-4,acx+4,acx}; int[] ys={acy-3,acy-3,acy+3}; g.fillPolygon(xs,ys,3);
            }
            if (active) {
                g.setColor(new Color(120, 255, 160, 80));
                g.setStroke(new BasicStroke(2));
                g.drawRoundRect(x, y, 18, 16, 3, 3);
            }
        }

        void drawShaft(Graphics2D g) {
            g.setColor(new Color(15, 18, 28));
            g.fillRect(SHAFT_X, 0, SHAFT_W, WIN_H);
            g.setColor(new Color(55, 70, 95));
            g.setStroke(new BasicStroke(4));
            g.drawLine(SHAFT_X + 8,          5, SHAFT_X + 8,          WIN_H);
            g.drawLine(SHAFT_X + SHAFT_W - 8, 5, SHAFT_X + SHAFT_W - 8, WIN_H);
            g.setColor(new Color(70, 88, 115));
            g.setStroke(new BasicStroke(1));
            for (int y = 15; y < WIN_H; y += 28) {
                g.fillOval(SHAFT_X + 5,           y, 6, 6);
                g.fillOval(SHAFT_X + SHAFT_W - 11, y, 6, 6);
            }
            g.setColor(new Color(38, 48, 68));
            g.setStroke(new BasicStroke(2));
            g.drawLine(SHAFT_X,          0, SHAFT_X,          WIN_H);
            g.drawLine(SHAFT_X + SHAFT_W, 0, SHAFT_X + SHAFT_W, WIN_H);
        }

        void drawCable(Graphics2D g) {
            int cx = SHAFT_X + SHAFT_W/2, top = (int)cabY;
            g.setColor(C_CABLE);
            g.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(cx-10,0,cx-10,top);
            g.drawLine(cx+10,0,cx+10,top);
            g.setStroke(new BasicStroke(1));
            g.setColor(new Color(100,80,40));
            g.drawLine(cx,0,cx,top);
        }

        void drawCabin(Graphics2D g) {
            int x = SHAFT_X + 10;
            int y = (int)cabY;
            int w = SHAFT_W - 20;

            g.setColor(new Color(0,0,0,100));
            g.fillRoundRect(x+4, y+6, w, CAB_H, 6, 6);

            g.setPaint(new GradientPaint(x, y, C_CAB_TOP, x, y+CAB_H, C_CAB_BOT));
            g.fillRoundRect(x, y, w, CAB_H, 6, 6);

            g.setColor(new Color(235, 245, 255));
            g.fillRoundRect(x+3, y+2, w-6, 9, 3, 3);

            int dh   = CAB_H - 14;
            int dy   = y + 12;
            int dtw  = w - 14;
            int half = (int)(doorAnim * dtw / 2);
            int mid  = x + 7 + dtw/2;

            g.setColor(new Color(18, 20, 32));
            g.fillRect(x+7, dy, dtw, dh);
            if (doorAnim > 0.05) {
                g.setColor(new Color(255, 242, 205, (int)(doorAnim*0.55*255)));
                g.fillRect(x+7+half, dy, dtw-2*half, dh);
            }

            int lw2 = dtw/2 - half;
            if (lw2 > 0) {
                g.setPaint(new GradientPaint(x+7, dy, C_DOOR_SHINE, x+7+lw2, dy, C_DOOR_METAL2));
                g.fillRect(x+7, dy, lw2, dh);
                g.setColor(new Color(70, 72, 80));
                g.setStroke(new BasicStroke(1));
                g.drawRect(x+7, dy, lw2, dh);
                if (half < 6) { 
                    g.setColor(new Color(100,102,110)); 
                    g.fillRoundRect(mid-half-5, dy+dh/2-7, 3,14,2,2); 
                }
            }
            
            int rw2 = dtw/2 - half;
            if (rw2 > 0) {
                g.setPaint(new GradientPaint(mid+half, dy, C_DOOR_METAL2, mid+half+rw2, dy, C_DOOR_SHINE));
                g.fillRect(mid+half, dy, rw2, dh);
                g.setColor(new Color(70, 72, 80));
                g.setStroke(new BasicStroke(1));
                g.drawRect(mid+half, dy, rw2, dh);
                if (half < 6) { 
                    g.setColor(new Color(100,102,110)); 
                    g.fillRoundRect(mid+half+2, dy+dh/2-7, 3,14,2,2); 
                }
            }

            g.setColor(new Color(18, 18, 22));
            g.fillRoundRect(x+w/2-14, y-20, 28, 18, 4, 4);
            g.setColor(C_LED_ON);
            g.setFont(new Font("Consolas", Font.BOLD, 12));
            String ft = currentFloor==0?"G":String.valueOf(currentFloor);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(ft, x+w/2-fm.stringWidth(ft)/2, y-5);

            if (dir==Dir.UP)   fillTri(g,x+w/2,y+3,true,  C_UP);
            if (dir==Dir.DOWN) fillTri(g,x+w/2,y+3,false, C_DOWN);

            g.setColor(new Color(95,115,155));
            g.setStroke(new BasicStroke(2));
            g.drawRoundRect(x, y, w, CAB_H, 6, 6);
        }

        void fillTri(Graphics2D g, int cx, int ty, boolean up, Color c) {
            g.setColor(c);
            int[] xs={cx-6,cx+6,cx};
            int[] ys = up ? new int[]{ty+7,ty+7,ty} : new int[]{ty,ty,ty+7};
            g.fillPolygon(xs,ys,3);
        }

        void drawPanel(Graphics2D g) {
            int px = BUILDING_X + BUILDING_W + 20;
            int ph = WIN_H - 20;
            g.setPaint(new GradientPaint(px,0,new Color(24,34,54),px+PANEL_W-10,0,new Color(16,22,40)));
            g.fillRoundRect(px, 10, PANEL_W-10, ph, 12, 12);
            g.setColor(new Color(48,68,98));
            g.setStroke(new BasicStroke(2));
            g.drawRoundRect(px, 10, PANEL_W-10, ph, 12, 12);

            g.setColor(new Color(98,158,238));
            g.setFont(new Font("Consolas", Font.BOLD, 14));
            g.drawString(">> FLOOR SELECT <<", px+14, 32);
            g.setColor(new Color(48,68,98));
            g.drawLine(px+8, 38, px+PANEL_W-18, 38);

            int bsz=36,cols=4,sy2=50,sx2=px+14;
            for (int i=NUM_FLOORS-1;i>=0;i--) {
                int col=(NUM_FLOORS-1-i)%cols, row=(NUM_FLOORS-1-i)/cols;
                int bx=sx2+col*(bsz+8), by=sy2+row*(bsz+8);
                boolean hov2=hx>=bx&&hx<=bx+bsz&&hy>=by&&hy<=by+bsz;
                drawFloorBtn(g,bx,by,bsz,i,pending.contains(i),i==currentFloor,hov2);
            }
            int divY=sy2+((NUM_FLOORS+cols-1)/cols)*(bsz+8)+5;
            g.setColor(new Color(48,68,98));
            g.drawLine(px+8,divY,px+PANEL_W-18,divY);

            int iy=divY+18;
            g.setColor(new Color(78,118,178));
            g.setFont(new Font("Consolas",Font.BOLD,12));
            g.drawString("STATUS",px+14,iy);
            g.setFont(new Font("Consolas",Font.PLAIN,12));
            g.setColor(C_TEXT);
            String s;
            if      (moving)       s=(dir==Dir.UP?"^ Moving UP":"v Moving DOWN");
            else if (doorOpening)  s="<> Opening doors...";
            else if (doorsOpen)    s="<  Doors Open  >";
            else if (doorClosing)  s=">< Closing doors...";
            else                   s="* Idle";
            g.drawString(s, px+14, iy+18);
            g.setColor(new Color(78,118,178));
            g.drawString("FLOOR  : "+(currentFloor==0?"Ground":"Floor "+currentFloor),px+14,iy+36);
            StringBuilder sb=new StringBuilder();
            for(int f: new TreeSet<>(pending)) sb.append(f==0?"G":f).append(" ");
            g.setColor(C_TEXT);
            g.drawString("QUEUE  : "+(sb.length()==0?"--":sb.toString().trim()),px+14,iy+54);
            drawMini(g,px+14,iy+72);
        }

        void drawFloorBtn(Graphics2D g,int x,int y,int sz,int floor,
                          boolean pend,boolean cur,boolean hov) {
            Color bg=cur&&!moving?C_BTN_ACT:pend?C_BTN_PEND:hov?C_BTN_HOV:C_BTN_IDLE;
            if(pend){g.setColor(new Color(200,140,30,55));g.fillRoundRect(x-3,y-3,sz+6,sz+6,10,10);}
            g.setPaint(new GradientPaint(x,y,bg.brighter(),x,y+sz,bg));
            g.fillRoundRect(x,y,sz,sz,8,8);
            g.setColor(pend?Color.WHITE:cur?new Color(18,18,18):C_TEXT);
            g.setFont(new Font("Consolas",Font.BOLD,14));
            String lbl=floor==0?"G":String.valueOf(floor);
            FontMetrics fm=g.getFontMetrics();
            g.drawString(lbl,x+(sz-fm.stringWidth(lbl))/2,y+(sz+fm.getAscent()-fm.getDescent())/2);
            g.setColor(pend?new Color(255,180,50):cur?new Color(58,198,118):new Color(48,68,98));
            g.setStroke(new BasicStroke(pend?2:1));
            g.drawRoundRect(x,y,sz,sz,8,8);
        }

        void drawMini(Graphics2D g,int x,int y) {
            int bw=20,bh=8,gap=2,totalH=NUM_FLOORS*(bh+gap);
            g.setColor(new Color(18,26,42));
            g.fillRoundRect(x,y,bw+4,totalH+4,4,4);
            for(int f=NUM_FLOORS-1;f>=0;f--) {
                int fy=y+2+(NUM_FLOORS-1-f)*(bh+gap);
                g.setColor(f==currentFloor?C_BTN_ACT:pending.contains(f)?C_BTN_PEND:new Color(28,38,58));
                g.fillRoundRect(x+2,fy,bw,bh,3,3);
            }
            double maxY=floorToY(0),minY=floorToY(NUM_FLOORS-1);
            double norm=(cabY-minY)/(maxY-minY);
            int my2=(int)(y+2+norm*(totalH-bh));
            g.setColor(new Color(255,198,48,188));
            g.fillRoundRect(x,my2,bw+4,bh+2,3,3);
        }

        void drawBottomBar(Graphics2D g) {
            g.setColor(new Color(8,12,22,210));
            g.fillRect(0,WIN_H-20,WIN_W,20);
            g.setColor(new Color(55,75,108));
            g.setFont(new Font("Consolas",Font.PLAIN,10));
            g.drawString("Click FLOOR SELECT panel or wall call buttons (▲▼) on each floor  |  Elevator Simulation  G–7",10,WIN_H-5);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ElevatorSimulation().setVisible(true));
    }
}