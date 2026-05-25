import java.awt.*;
import java.awt.event.*;
import java.util.*;
import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.Timer;

public class ElevatorSimulation extends JFrame {

    // === CONFIGURATION ===
    static final int NUM_FLOORS = 8;
    static final int MIN_FLOOR_HEIGHT = 80;
    static final int MIN_BUILDING_W = 350;
    static final int SHAFT_W = 120;
    static final int CAB_H = 70;
    static final int PANEL_W = 260;
    static final int STATS_W = 340;
    static final int MIN_WIN_H = NUM_FLOORS * MIN_FLOOR_HEIGHT + 40;
    static final int MIN_WIN_W = MIN_BUILDING_W + PANEL_W + STATS_W + 60;

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

    // === STATE ===
    double cabY;
    int currentFloor = 0;  // Start at ground floor (0 = G)
    int targetFloor = 0;
    boolean moving = false;
    boolean doorsOpen = false;
    double doorAnim = 0;   // 0 = closed, 1 = fully open
    boolean doorOpening = false;
    boolean doorClosing = false;
    int doorWait = 0;
    double speedMultiplier = 1.0;
    boolean randomMode = false;
    int screenShake = 0;
    boolean soundEnabled = true;
    int passengerPickMode = 0;
    int pickedStartFloor = -1;
    Rectangle[] inlineFloorBounds = new Rectangle[NUM_FLOORS];
    Rectangle inlineCancelBounds = new Rectangle();
    Rectangle inlineNoDestBounds = new Rectangle();
    { for(int _i=0;_i<NUM_FLOORS;_i++) inlineFloorBounds[_i]=new Rectangle(); }
    
    enum Dir { IDLE, UP, DOWN }
    Dir dir = Dir.IDLE;

    // === DATA STRUCTURES ===
    Set<Integer> requests = new TreeSet<>();  // Floors to visit
    Set<Integer> pending = new HashSet<>();   // Pending requests
    java.util.List<Particle> particles = new ArrayList<>();
    java.util.List<Passenger> passengers = new ArrayList<>(); // Passengers waiting outside
    java.util.List<Passenger> inElevator = new ArrayList<>(); // Passengers inside elevator
    Random rng = new Random();
    boolean[] callUp = new boolean[NUM_FLOORS];
    boolean[] callDn = new boolean[NUM_FLOORS];

    // === STATISTICS ===
    int totalTrips = 0;
    int totalPassengers = 0;
    long startTime = System.currentTimeMillis();
    double avgWaitTime = 0;
    int maxWaitTime = 0;
    java.util.List<Integer> waitTimes = new ArrayList<>();
    int[] floorVisits = new int[NUM_FLOORS];
    
    // === TIMING ===
    Timer animTimer;
    Timer randomTimer;
    static final int ANIM_MS = 16;
    static final double BASE_SPEED = 2.0;
    static final double DOOR_SPD = 0.035;
    static final int DOOR_HOLD = 130;

    // === SOUND ===
    Clip dingClip, doorOpenClip, doorCloseClip;
    
    // === UI COMPONENTS ===
    DrawPanel canvas;
    
    // Control bounds
    Rectangle speedDownBounds = new Rectangle();
    Rectangle speedUpBounds = new Rectangle();
    Rectangle randomBtnBounds = new Rectangle();
    Rectangle soundBtnBounds = new Rectangle();
    Rectangle addPassBounds = new Rectangle();
    
    public ElevatorSimulation() {
        super("Elevator Simulation – G to 7");
        cabY = floorToY(0);  // Start at ground floor
        
        initSounds();
        initUI();
        initTimers();
        
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(MIN_WIN_W, MIN_WIN_H));
        setSize(MIN_WIN_W + 100, MIN_WIN_H + 50);
    }
    
    void initSounds() {
        try {
            dingClip = createTone(880, 0.15f, 0.3f);
            doorOpenClip = createNoise(0.2f, 0.1f, 800);
            doorCloseClip = createNoise(0.25f, 0.1f, 600);
        } catch (Exception e) {
            System.out.println("Sound init failed: " + e.getMessage());
        }
    }
    
    Clip createTone(float freq, float duration, float volume) throws Exception {
        int sampleRate = 44100;
        int samples = (int)(duration * sampleRate);
        byte[] data = new byte[samples * 2];
        
        for (int i = 0; i < samples; i++) {
            double t = i / (double)sampleRate;
            double envelope = Math.exp(-t * 5);
            short val = (short)(Math.sin(2 * Math.PI * freq * t) * 32767 * volume * envelope);
            data[i * 2] = (byte)(val & 0xFF);
            data[i * 2 + 1] = (byte)((val >> 8) & 0xFF);
        }
        
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        Clip clip = AudioSystem.getClip();
        clip.open(format, data, 0, data.length);
        return clip;
    }
    
    Clip createNoise(float duration, float volume, int freq) throws Exception {
        int sampleRate = 44100;
        int samples = (int)(duration * sampleRate);
        byte[] data = new byte[samples * 2];
        
        for (int i = 0; i < samples; i++) {
            double t = i / (double)sampleRate;
            double envelope = Math.exp(-t * 3);
            short val = (short)((rng.nextDouble() * 2 - 1) * 32767 * volume * envelope * 0.3);
            data[i * 2] = (byte)(val & 0xFF);
            data[i * 2 + 1] = (byte)((val >> 8) & 0xFF);
        }
        
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        Clip clip = AudioSystem.getClip();
        clip.open(format, data, 0, data.length);
        return clip;
    }
    
    void playSound(Clip clip) {
        if (soundEnabled && clip != null) {
            clip.setFramePosition(0);
            clip.start();
        }
    }
    
    void initUI() {
        canvas = new DrawPanel();
        canvas.setBackground(C_BG);
        canvas.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { handleClick(e.getX(), e.getY()); }
        });
        canvas.addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                canvas.hx = e.getX(); canvas.hy = e.getY(); canvas.repaint();
            }
        });
        add(canvas);
    }
    
    void initTimers() {
        animTimer = new Timer(ANIM_MS, e -> tick());
        animTimer.start();
        
        randomTimer = new Timer(2500, e -> {
            if (randomMode && rng.nextDouble() < 0.4) {
                spawnRandomPassenger();
            }
        });
        randomTimer.start();
    }

    // Dynamic sizing based on current window dimensions
    int getFloorHeight() { return Math.max(MIN_FLOOR_HEIGHT, (getHeight() - 40) / NUM_FLOORS); }
    int getBuildingX() { return 20; }
    int getBuildingW() { return Math.max(MIN_BUILDING_W, (getWidth() - PANEL_W - STATS_W - 60)); }
    int getShaftX() { return getBuildingX() + (getBuildingW() / 2) - SHAFT_W / 2; }
    int getPanelX() { return getBuildingX() + getBuildingW() + 15; }
    int getStatsX() { return getPanelX() + PANEL_W + 10; }

    int floorToY(int f) {
        // f=0 (G) at bottom, f=7 at top
        return (NUM_FLOORS - 1 - f) * getFloorHeight();
    }

    int lobbyTop(int f) { return floorToY(f) + 8; }
    int slabY(int f) { return floorToY(f) + getFloorHeight() - 14; }
    int lobbyH(int f) { return slabY(f) - lobbyTop(f); }

    void tick() {
        double speed = BASE_SPEED * speedMultiplier;
        
        // Door animation
        if (doorOpening) {
            doorAnim = Math.min(1.0, doorAnim + DOOR_SPD * speedMultiplier);
            if (doorAnim >= 1.0) {
                doorOpening = false;
                doorsOpen = true;
                doorWait = (int)(DOOR_HOLD / speedMultiplier);
                spawnParticles();
                playSound(doorOpenClip);
            }
        } else if (doorsOpen) {
            // Exit first, then board — doors must be fully open for both
            checkPassengerExit();
            boardPassengers();
            
            // Check if any passenger inside still needs a destination
            boolean passengerNeedsDestination = false;
            for (Passenger p : inElevator) {
                if (p.destFloor < 0) {
                    passengerNeedsDestination = true;
                    break;
                }
            }

            // If someone needs to pick a destination, use a longer hold but still count down
            // (never freeze infinitely — player had their chance each tick)
            int holdTicks = passengerNeedsDestination
                    ? (int)(DOOR_HOLD * 4 / speedMultiplier)   // ~4x longer grace period
                    : (int)(DOOR_HOLD / speedMultiplier);
            if (doorWait > holdTicks) doorWait = holdTicks;   // cap on entry
            if (doorWait-- <= 0) {
                doorsOpen = false;
                doorClosing = true;
            }
        } else if (doorClosing) {
            doorAnim = Math.max(0.0, doorAnim - DOOR_SPD * speedMultiplier);
            if (doorAnim <= 0.0) {
                doorClosing = false;
                playSound(doorCloseClip);
                nextMove();
            }
        }
        
        // Elevator movement
        if (moving) {
            double ty = floorToY(targetFloor);
            if (Math.abs(cabY - ty) < speed) {
                // Arrived at floor
                cabY = ty;
                currentFloor = targetFloor;
                moving = false;
                pending.remove(currentFloor);
                
                // Clear call buttons
                if (dir == Dir.UP) callUp[currentFloor] = false;
                if (dir == Dir.DOWN) callDn[currentFloor] = false;
                
                totalTrips++;
                floorVisits[currentFloor]++;
                screenShake = 5;
                playSound(dingClip);
                updateStats();
                
                // Open doors
                doorOpening = true;
            } else {
                cabY += (ty < cabY) ? -speed : speed;
            }
        }
        
        // Update waiting passengers (outside)
        for (Passenger p : passengers) {
            if (!p.inElevator && !p.arrived) {
                p.update();
            }
        }
        
        // Update passengers inside elevator
        for (Passenger p : inElevator) {
            p.updateInElevator();
        }
        
        // Remove arrived passengers from both lists
        passengers.removeIf(p -> p.arrived);
        inElevator.removeIf(p -> p.arrived);
        
        // Update particles
        particles.removeIf(p -> { p.update(); return p.dead(); });
        
        if (screenShake > 0) screenShake--;
        
        canvas.repaint();
    }
    
    void updateStats() {
        if (!waitTimes.isEmpty()) {
            int sum = 0;
            maxWaitTime = 0;
            for (int w : waitTimes) {
                sum += w;
                maxWaitTime = Math.max(maxWaitTime, w);
            }
            avgWaitTime = sum / (double)waitTimes.size();
        }
    }
    
    void spawnRandomPassenger() {
        int start = rng.nextInt(NUM_FLOORS);
        int dest;
        do {
            // Weighted random destination - more realistic
            double rand = rng.nextDouble();
            if (rand < 0.3) {
                dest = 0;  // 30% go to ground
            } else if (rand < 0.7) {
                dest = 1 + rng.nextInt(3);  // 40% go to floors 1-3
            } else {
                dest = rng.nextInt(NUM_FLOORS);  // 30% go anywhere
            }
        } while (dest == start);
        spawnPassenger(start, dest);
    }
    
    void spawnPassenger(int startFloor, int destFloor) {
        Passenger p = new Passenger(startFloor, destFloor);
        passengers.add(p);
        totalPassengers++;
        
        // Set call button only if destination is set
        if (destFloor >= 0) {
            if (destFloor > startFloor) callUp[startFloor] = true;
            else if (destFloor < startFloor) callDn[startFloor] = true;
        }
        
        request(startFloor);
    }
    
    void showAddPassengerDialog() {
        final int BSZ = 44, GAP = 8, COLS = 4, PAD = 16;
        final Color BG      = new Color(18, 24, 40);
        final Color BTN_IDLE= new Color(48, 62, 88);
        final Color BTN_HOV2= new Color(68, 88, 128);
        final Color BTN_SEL = new Color(58, 198, 118);
        final Color BTN_DIS = new Color(30, 38, 55);
        final Color BORDER  = new Color(48, 68, 98);

        int[] selectedStart = {-1};

        JDialog dlg = new JDialog(this, "Add Passenger", true);
        dlg.setUndecorated(false);
        dlg.getContentPane().setBackground(BG);
        dlg.setLayout(new BorderLayout(0, 0));

        // ── header label (updates between steps)
        JLabel header = new JLabel("Start Floor:", SwingConstants.CENTER);
        header.setForeground(new Color(98, 158, 238));
        header.setFont(new Font("Consolas", Font.BOLD, 13));
        header.setBorder(BorderFactory.createEmptyBorder(14, PAD, 6, PAD));
        dlg.add(header, BorderLayout.NORTH);

        // ── grid panel (custom-painted floor buttons)
        int rows = (NUM_FLOORS + COLS - 1) / COLS;
        int gridW = COLS * BSZ + (COLS - 1) * GAP + PAD * 2;
        int gridH = rows * BSZ + (rows - 1) * GAP + PAD * 2;

        int[] hoveredFloor = {-1};
        // Each button state: 0=idle, 1=hovered, 2=selected, 3=disabled
        JPanel grid = new JPanel(null) {
            @Override public Dimension getPreferredSize() { return new Dimension(gridW, gridH); }
            @Override protected void paintComponent(Graphics g0) {
                super.paintComponent(g0);
                Graphics2D g = (Graphics2D) g0;
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(BG); g.fillRect(0,0,getWidth(),getHeight());

                for (int i = NUM_FLOORS - 1; i >= 0; i--) {
                    int idx = NUM_FLOORS - 1 - i;
                    int col = idx % COLS, row = idx / COLS;
                    int bx = PAD + col * (BSZ + GAP);
                    int by = PAD + row * (BSZ + GAP);
                    String lbl = (i == 0) ? "G" : String.valueOf(i);

                    boolean isSel  = (i == selectedStart[0]);
                    boolean isHov  = (i == hoveredFloor[0]);
                    boolean isDis  = (selectedStart[0] >= 0 && i == selectedStart[0]);
                    // In dest step, disable the start floor
                    boolean inDestStep = selectedStart[0] >= 0;
                    boolean disableThis = inDestStep && i == selectedStart[0];

                    Color bg2 = disableThis ? BTN_DIS : isSel ? BTN_SEL : isHov ? BTN_HOV2 : BTN_IDLE;

                    // glow for selected
                    if (isSel) {
                        g.setColor(new Color(58, 198, 118, 40));
                        g.fillRoundRect(bx-3, by-3, BSZ+6, BSZ+6, 12, 12);
                    }

                    GradientPaint gp = new GradientPaint(bx, by, bg2.brighter(), bx, by+BSZ, bg2);
                    g.setPaint(gp);
                    g.fillRoundRect(bx, by, BSZ, BSZ, 8, 8);

                    Color borderCol = isSel ? new Color(58,198,118) : disableThis ? new Color(35,45,65) : BORDER;
                    g.setColor(borderCol);
                    g.setStroke(new BasicStroke(isSel ? 2 : 1));
                    g.drawRoundRect(bx, by, BSZ, BSZ, 8, 8);

                    g.setColor(disableThis ? new Color(60,70,90) : isSel ? Color.BLACK : new Color(198,215,240));
                    g.setFont(new Font("Consolas", Font.BOLD, 15));
                    FontMetrics fm = g.getFontMetrics();
                    g.drawString(lbl, bx + (BSZ - fm.stringWidth(lbl))/2, by + (BSZ + fm.getAscent() - fm.getDescent())/2);
                }
            }
        };
        grid.setBackground(BG);

        // ── bottom bar: action button + cancel
        JLabel actionBtn = new JLabel("ADD & WAIT FOR DESTINATION", SwingConstants.CENTER);
        actionBtn.setFont(new Font("Consolas", Font.BOLD, 11));
        actionBtn.setOpaque(true);
        actionBtn.setBackground(BTN_IDLE);
        actionBtn.setForeground(new Color(130, 150, 180));
        actionBtn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER, 1),
            BorderFactory.createEmptyBorder(7, 0, 7, 0)));
        actionBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JPanel bottom = new JPanel(new BorderLayout(0, 4));
        bottom.setBackground(BG);
        bottom.setBorder(BorderFactory.createEmptyBorder(4, PAD, PAD, PAD));
        bottom.add(actionBtn, BorderLayout.CENTER);

        JLabel cancelLbl = new JLabel("✕  Cancel", SwingConstants.CENTER);
        cancelLbl.setFont(new Font("Consolas", Font.PLAIN, 11));
        cancelLbl.setForeground(new Color(160, 80, 80));
        cancelLbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        cancelLbl.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        bottom.add(cancelLbl, BorderLayout.SOUTH);

        dlg.add(grid, BorderLayout.CENTER);
        dlg.add(bottom, BorderLayout.SOUTH);

        // ── floor button positions helper
        java.util.function.Function<Point, Integer> pointToFloor = pt -> {
            for (int i = NUM_FLOORS - 1; i >= 0; i--) {
                int idx = NUM_FLOORS - 1 - i;
                int col = idx % COLS, row = idx / COLS;
                int bx = PAD + col * (BSZ + GAP);
                int by = PAD + row * (BSZ + GAP);
                if (pt.x >= bx && pt.x <= bx+BSZ && pt.y >= by && pt.y <= by+BSZ) return i;
            }
            return -1;
        };

        // ── mouse interactions on grid
        grid.addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                int f = pointToFloor.apply(e.getPoint());
                if (f != hoveredFloor[0]) { hoveredFloor[0] = f; grid.repaint(); }
            }
        });
        grid.addMouseListener(new MouseAdapter() {
            @Override public void mouseExited(MouseEvent e) { hoveredFloor[0] = -1; grid.repaint(); }
            @Override public void mousePressed(MouseEvent e) {
                int f = pointToFloor.apply(e.getPoint());
                if (f < 0) return;
                boolean inDestStep = selectedStart[0] >= 0;
                if (!inDestStep) {
                    // Step 1: pick start floor
                    selectedStart[0] = f;
                    header.setText("Destination Floor:");
                    // update action button to reflect "choose on board"
                    actionBtn.setText("ADD & WAIT FOR DESTINATION");
                    actionBtn.setBackground(new Color(48, 62, 88));
                    actionBtn.setForeground(new Color(198, 215, 240));
                    grid.repaint();
                } else {
                    // Step 2: pick destination
                    if (f == selectedStart[0]) return; // same floor — ignore
                    dlg.dispose();
                    spawnPassenger(selectedStart[0], f);
                }
            }
        });

        // action button: "add with no dest" (only enabled after start is chosen)
        actionBtn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                if (selectedStart[0] >= 0) actionBtn.setBackground(BTN_HOV2);
            }
            @Override public void mouseExited(MouseEvent e) {
                if (selectedStart[0] >= 0) actionBtn.setBackground(BTN_IDLE);
            }
            @Override public void mousePressed(MouseEvent e) {
                if (selectedStart[0] < 0) return; // not yet chosen start
                dlg.dispose();
                spawnPassenger(selectedStart[0], -1);
            }
        });

        cancelLbl.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { dlg.dispose(); }
            @Override public void mouseEntered(MouseEvent e) { cancelLbl.setForeground(new Color(218, 98, 78)); }
            @Override public void mouseExited(MouseEvent e)  { cancelLbl.setForeground(new Color(160, 80, 80)); }
        });

        dlg.pack();
        dlg.setResizable(false);
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }
    
    void styleButton(JButton button, Color bg) {
        button.setBackground(bg);
        button.setForeground(Color.BLACK);
        button.setFont(new Font("Consolas", Font.BOLD, 11));
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createLineBorder(new Color(48, 68, 98), 2));
        button.setPreferredSize(new Dimension(120, 30));
        
        button.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                button.setBackground(bg.brighter());
            }
            @Override public void mouseExited(MouseEvent e) {
                button.setBackground(bg);
            }
        });
    }
    
    void boardPassengers() {
        if (!doorsOpen) return;
        if (moving) return;

        int shaftX = getShaftX();
        double elevatorX = shaftX + SHAFT_W / 2.0;

        // Find the first passenger waiting on this floor (queue order)
        Passenger next = null;
        for (Passenger p : passengers) {
            if (p.startFloor == currentFloor && !p.inElevator && !p.arrived) {
                next = p;
                break;
            }
        }

        if (next == null) return;

        double dx = elevatorX - next.x;
        if (Math.abs(dx) > 5) {
            // Walk toward elevator
            next.x += Math.signum(dx) * 2;
            next.walkOffset = Math.sin(System.currentTimeMillis() / 80.0) * 4;
        } else {
            // Board
            next.inElevator = true;
            next.walkOffset = 0;
            next.elevatorXOffset = (rng.nextDouble() - 0.5) * 40;
            inElevator.add(next);
            passengers.remove(next);

            // Record wait time at actual board moment
            if (next.waitStart > 0) {
                int wait = (int)((System.currentTimeMillis() - next.waitStart) / 1000);
                waitTimes.add(wait);
                updateStats();
            }

            // Request destination floor if already known
            if (next.destFloor >= 0) {
                request(next.destFloor);
            }
        }
    }

    void checkPassengerExit() {
        if (!doorsOpen) return;

        Iterator<Passenger> iter = inElevator.iterator();
        while (iter.hasNext()) {
            Passenger p = iter.next();
            if (p.destFloor == currentFloor && !p.arrived) {
                p.inElevator = false;
                p.arrived = true;
                spawnExitParticles();
                iter.remove();
            }
        }
    }

    void spawnExitParticles() {
        int shaftX = getShaftX();
        double cx = shaftX + SHAFT_W / 2.0;
        double cy = cabY + CAB_H / 2.0;
        for (int i = 0; i < 20; i++) {
            Color c = new Color(255, 200, 50, 220);
            particles.add(new Particle(cx, cy,
                (rng.nextDouble()-0.5)*6, -rng.nextDouble()*4,
                c));
        }
    }

    void spawnParticles() {
        int shaftX = getShaftX();
        double cx = shaftX + SHAFT_W / 2.0;
        double cy = cabY + CAB_H / 2.0;
        for (int i = 0; i < 30; i++) {
            Color c = new Color(200, 220, 255, 180);
            particles.add(new Particle(cx, cy,
                (rng.nextDouble()-0.5)*4, -rng.nextDouble()*3,
                c));
        }
    }

    void nextMove() {
        if (moving || doorOpening || doorsOpen || doorClosing) return;
        
        // Collect all destination floors from passengers inside (only valid destinations)
        Set<Integer> destFloors = new HashSet<>();
        for (Passenger p : inElevator) {
            if (p.destFloor >= 0) {
                destFloors.add(p.destFloor);
            }
        }
        
        // Combine with external requests
        Set<Integer> allRequests = new HashSet<>(requests);
        allRequests.addAll(destFloors);
        
        if (allRequests.isEmpty()) {
            dir = Dir.IDLE;
            return;
        }

        Integer nextFloor = null;

        if (dir == Dir.IDLE) {
            nextFloor = findClosestFloor(allRequests);
            if (nextFloor != null) {
                dir = (nextFloor > currentFloor) ? Dir.UP : Dir.DOWN;
            }
        } else if (dir == Dir.UP) {
            nextFloor = findNextFloorUp(allRequests);
            if (nextFloor == null) {
                nextFloor = findNextFloorDown(allRequests);
                if (nextFloor != null) dir = Dir.DOWN;
            }
        } else {
            nextFloor = findNextFloorDown(allRequests);
            if (nextFloor == null) {
                nextFloor = findNextFloorUp(allRequests);
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

    Integer findClosestFloor(Set<Integer> allRequests) {
        Integer closest = null;
        int minDist = Integer.MAX_VALUE;
        for (int f : allRequests) {
            int dist = Math.abs(f - currentFloor);
            if (dist < minDist) {
                minDist = dist;
                closest = f;
            }
        }
        return closest;
    }

    Integer findNextFloorUp(Set<Integer> allRequests) {
        Integer next = null;
        for (int f : allRequests) {
            if (f > currentFloor) {
                if (next == null || f < next) {
                    next = f;
                }
            }
        }
        return next;
    }

    Integer findNextFloorDown(Set<Integer> allRequests) {
        Integer next = null;
        for (int f : allRequests) {
            if (f < currentFloor) {
                if (next == null || f > next) {
                    next = f;
                }
            }
        }
        return next;
    }

    void request(int floor) {
        // If elevator at this floor with doors closed, open them
        if (floor == currentFloor && !moving && !doorsOpen && !doorOpening && !doorClosing) {
            doorOpening = true;
            return;
        }
        
        if (pending.contains(floor)) return;
        
        pending.add(floor);
        requests.add(floor);
        nextMove();
    }

    void handleClick(int mx, int my) {
        // Control buttons
        if (speedDownBounds.contains(mx, my)) {
            speedMultiplier = Math.max(0.25, speedMultiplier - 0.25);
            canvas.repaint();
            return;
        }
        if (speedUpBounds.contains(mx, my)) {
            speedMultiplier = Math.min(3.0, speedMultiplier + 0.25);
            canvas.repaint();
            return;
        }
        if (randomBtnBounds.contains(mx, my)) {
            randomMode = !randomMode;
            canvas.repaint();
            return;
        }
        if (soundBtnBounds.contains(mx, my)) {
            soundEnabled = !soundEnabled;
            canvas.repaint();
            return;
        }
        if (addPassBounds.contains(mx, my)) {
            passengerPickMode = 1;
            pickedStartFloor = -1;
            canvas.repaint();
            return;
        }
        // Inline floor picker: cancel
        if (passengerPickMode > 0 && inlineCancelBounds.contains(mx, my)) {
            passengerPickMode = 0;
            pickedStartFloor = -1;
            canvas.repaint();
            return;
        }
        // Inline floor picker: add with no destination (step 1 only)
        if (passengerPickMode == 1 && inlineNoDestBounds.contains(mx, my)) {
            passengerPickMode = 0;
            int sf = pickedStartFloor;
            pickedStartFloor = -1;
            canvas.repaint();
            if (sf >= 0) spawnPassenger(sf, -1);
            return;
        }
        // Inline floor picker: floor button click
        if (passengerPickMode > 0) {
            for (int i = 0; i < NUM_FLOORS; i++) {
                if (inlineFloorBounds[i].contains(mx, my)) {
                    if (passengerPickMode == 1) {
                        // picked start floor — move to step 2
                        pickedStartFloor = i;
                        passengerPickMode = 2;
                    } else {
                        // picked destination
                        if (i != pickedStartFloor) {
                            int sf2 = pickedStartFloor;
                            passengerPickMode = 0;
                            pickedStartFloor = -1;
                            canvas.repaint();
                            spawnPassenger(sf2, i);
                        }
                    }
                    canvas.repaint();
                    return;
                }
            }
        }
        
        // Floor panel buttons
        int px = getPanelX();
        int sx = px + 14, sy = 50, bsz = 36, cols = 4;
        for (int i = NUM_FLOORS-1; i >= 0; i--) {
            int col = (NUM_FLOORS-1-i) % cols, row = (NUM_FLOORS-1-i) / cols;
            int bx = sx + col * (bsz + 8), by = sy + row * (bsz + 8);
            if (mx >= bx && mx <= bx + bsz && my >= by && my <= by + bsz) {
                // If there's an unassigned passenger inside (destFloor < 0), assign destination first
                Passenger unassigned = null;
                for (Passenger p : inElevator) {
                    if (p.destFloor < 0) { unassigned = p; break; }
                }
                if (unassigned != null && doorsOpen) {
                    unassigned.destFloor = i;
                    request(i);
                } else {
                    request(i);
                }
                return;
            }
        }
        
        // Wall call buttons
        for (int f = 0; f < NUM_FLOORS; f++) {
            int[] cb = callBtnPos(f);
            if (f < NUM_FLOORS-1 && mx >= cb[0] && mx <= cb[0] + 18 && my >= cb[1] && my <= cb[1] + 16) { 
                callUp[f] = true; 
                request(f); 
                return; 
            }
            if (f > 0 && mx >= cb[0] && mx <= cb[0] + 18 && my >= cb[1] + 18 && my <= cb[1] + 34) { 
                callDn[f] = true; 
                request(f); 
                return; 
            }
        }
    }

    int[] callBtnPos(int f) {
        int shaftX = getShaftX();
        int doorRight = shaftX + SHAFT_W + 8;
        int btnX = doorRight + 10;
        int lt = lobbyTop(f);
        int lh = lobbyH(f);
        int btnY = lt + lh/2 - 18;
        return new int[]{btnX, btnY};
    }

    class Particle {
        double x, y, vx, vy; 
        Color c; 
        int life = 40;
        
        Particle(double x, double y, double vx, double vy, Color c) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy; this.c = c;
        }
        void update() {
            x += vx; 
            y += vy; 
            vy += 0.15;
            life--;
        }
        boolean dead() { return life <= 0; }
    }
    
    class Passenger {
        int startFloor, destFloor;
        double x, y;
        boolean inElevator = false;
        boolean arrived = false;
        long waitStart;
        Color color;
        double walkOffset = 0;
        double waitX;
        double elevatorXOffset = 0;
        
        Passenger(int start, int dest) {
            this.startFloor = start;
            this.destFloor = dest;
            this.waitStart = System.currentTimeMillis();
            
            int lt = lobbyTop(start);
            int lh = lobbyH(start);
            y = lt + lh - 25;
            
            // Random waiting position away from elevator
            int floorLeft = getBuildingX() + 30;
            int floorRight = getShaftX() - 30;
            waitX = floorLeft + rng.nextInt(Math.max(1, floorRight - floorLeft));
            x = waitX;
            
            Color[] colors = {
                new Color(255, 100, 100), new Color(100, 255, 100), 
                new Color(100, 100, 255), new Color(255, 255, 100),
                new Color(255, 100, 255), new Color(100, 255, 255),
                new Color(255, 150, 50), new Color(150, 100, 255)
            };
            color = colors[rng.nextInt(colors.length)];
        }
        
        void update() {
            if (arrived || inElevator) return;
            
            // Idle animation while waiting
            walkOffset = Math.sin(System.currentTimeMillis() / 300.0) * 2;
            
            // Only walk toward elevator when doors are fully open and this passenger is next in queue
            if (currentFloor == startFloor && doorsOpen && !moving) {
                // Check if this is the first waiting passenger on this floor (i.e. at the front of queue)
                boolean isNext = true;
                for (Passenger other : passengers) {
                    if (other != this && other.startFloor == startFloor
                            && !other.inElevator && !other.arrived
                            && other.waitStart < waitStart) {
                        isNext = false;
                        break;
                    }
                }
                if (isNext) {
                    int shaftX = getShaftX();
                    double elevatorX = shaftX + SHAFT_W / 2.0;
                    double dx = elevatorX - x;
                    if (Math.abs(dx) > 5) {
                        x += Math.signum(dx) * 2;
                        walkOffset = Math.sin(System.currentTimeMillis() / 80.0) * 4;
                    }
                } else {
                    // Return to / stay at waiting position
                    double dx = waitX - x;
                    if (Math.abs(dx) > 2) x += Math.signum(dx) * 0.5;
                }
            } else {
                // Return to waiting position if elevator isn't here or doors not open
                double dx = waitX - x;
                if (Math.abs(dx) > 2) {
                    x += Math.signum(dx) * 0.5;
                }
            }
        }
        
        void updateInElevator() {
            if (!inElevator || arrived) return;

            // Update position to stay inside elevator - use fixed offset
            int shaftX = getShaftX();
            x = shaftX + SHAFT_W/2 + elevatorXOffset;
            y = cabY + CAB_H - 25;
        }
        
        void draw(Graphics2D g) {
            if (arrived) return;

            int px = (int)x;
            int py = (int)(y + walkOffset);

            // Shadow
            g.setColor(new Color(0, 0, 0, 60));
            g.fillOval(px - 2, (int)y + 18, 14, 6);

            // Body - keep inside elevator
            int bodyY = py;
            if (inElevator) {
                int cabBottom = (int)cabY + CAB_H - 5;
                if (bodyY + 16 > cabBottom) {
                    bodyY = cabBottom - 16;
                }
            }

            g.setColor(color);
            g.fillRoundRect(px, bodyY, 10, 16, 4, 4);

            // Head
            g.setColor(new Color(255, 220, 180));
            g.fillOval(px + 1, bodyY - 8, 8, 8);

            // Show destination only if set
            if (destFloor >= 0) {
                if (!inElevator) {
                    g.setColor(Color.WHITE);
                    g.setFont(new Font("Arial", Font.BOLD, 8));
                    String dest = destFloor == 0 ? "G" : String.valueOf(destFloor);
                    g.drawString(dest, px + 2, bodyY - 12);
                } else {
                    g.setColor(new Color(255, 255, 200));
                    g.setFont(new Font("Arial", Font.BOLD, 8));
                    String dest = destFloor == 0 ? "G" : String.valueOf(destFloor);
                    g.drawString(dest, px + 2, bodyY - 12);
                }
            } else if (inElevator) {
                // Show "?" when no destination set yet but in elevator
                g.setColor(new Color(255, 200, 100));
                g.setFont(new Font("Arial", Font.BOLD, 9));
                g.drawString("?", px + 3, bodyY - 12);
            }
        }

    }
    
    class DrawPanel extends JPanel {
        int hx = -1, hy = -1;
        
        @Override protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D)g0;
            
            int shakeX = 0, shakeY = 0;
            if (screenShake > 0) {
                shakeX = rng.nextInt(screenShake * 2) - screenShake;
                shakeY = rng.nextInt(screenShake * 2) - screenShake;
            }
            g.translate(shakeX, shakeY);
            
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            
            // Background
            g.setColor(C_BG);
            g.fillRect(0, 0, getWidth(), getHeight());
            
            // Draw building
            for (int f = 0; f < NUM_FLOORS; f++) drawLobby(g, f);
            drawShaft(g);
            drawCable(g);
            
            // Draw waiting passengers (outside only - those in inElevator list are not here)
            for (Passenger p : passengers) {
                p.draw(g);
            }
            
            // Draw elevator cabin
            drawCabin(g);
            
            // Draw passengers inside elevator (only when doors are open or opening)
            if (doorAnim > 0 || doorsOpen) {
                for (Passenger p : inElevator) {
                    p.draw(g);
                }
            }
            
            // Draw particles
            for (Particle p : particles) {
                int alpha = (int)(p.life / 40.0 * 255);
                g.setColor(new Color(p.c.getRed(), p.c.getGreen(), p.c.getBlue(), alpha));
                g.fillOval((int)p.x - 3, (int)p.y - 3, 6, 6);
            }
            
            // Draw UI panels
            drawPanel(g);
            drawStatsPanel(g);
            drawBottomBar(g);
        }

        void drawLobby(Graphics2D g, int f) {
            int lx = getBuildingX();
            int lt = lobbyTop(f);
            int lw = getBuildingW();
            int lh = lobbyH(f);
            int sl = slabY(f);

            // Wall
            GradientPaint wallGrad = new GradientPaint(lx, lt, C_WALL_LIGHT, lx, sl, C_WALL_SHADOW);
            g.setPaint(wallGrad);
            g.fillRect(lx, lt, lw, lh);
            
            // Picture frames on wall
            drawPictureFrames(g, lx, lt, lw, lh, f);
            
            // Tile lines
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
            
            // Spotlights
            drawSpotlight(g, lx + lw/4, lt, lw/4, lh);
            drawSpotlight(g, lx + 3*lw/4, lt, lw/4, lh);

            // Floor tiles
            int floorH = 14;
            int floorY = sl - floorH;
            for (int tx = lx; tx < lx+lw; tx += 30) {
                boolean dark = ((tx-lx)/30 % 2 == 0);
                g.setColor(dark ? C_FLOOR_TILE : C_FLOOR_TILE2);
                g.fillRect(tx, floorY, 30, floorH);
            }
            g.setColor(new Color(100, 90, 78));
            g.drawLine(lx, floorY, lx+lw, floorY);

            // Plants - right side only
            int plantW = 22, plantH = lh - 12;
            int rightPlantX = getShaftX() + SHAFT_W + 45;
            if (rightPlantX < lx+lw-32) drawPlant(g, rightPlantX, floorY - plantH, plantW, plantH);

            // Elevator door frame
            int fdW = SHAFT_W + 12;
            int fdH = (int)(lh * 0.80);
            int fdX = getShaftX() - 7;
            int fdY = floorY - fdH;

            g.setColor(C_DOOR_FRAME);
            g.fillRect(fdX, fdY, fdW, fdH);

            // Door animation
            boolean cabAtThisFloor = (Math.abs(cabY - floorToY(f)) < 4);
            double fda = cabAtThisFloor ? doorAnim : 0.0;

            int idW = fdW - 10;
            int idH = fdH - 8;
            int idX = fdX + 5;
            int idY = fdY + 4;

            // Draw elevator interior (dark)
            g.setColor(C_DOOR_INSIDE);
            g.fillRect(idX, idY, idW, idH);

            if (fda < 0.98) {
                int half = (int)(fda * idW / 2);
                int lpW = idW/2 - half;
                
                // Left door
                if (lpW > 0) {
                    GradientPaint lp = new GradientPaint(idX, idY, C_DOOR_SHINE, idX + lpW, idY, C_DOOR_METAL2);
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

                // Right door
                int rpX = idX + idW/2 + half;
                int rpW = idW/2 - half;
                if (rpW > 0) {
                    GradientPaint rp = new GradientPaint(rpX, idY, C_DOOR_METAL2, rpX + rpW, idY, C_DOOR_SHINE);
                    g.setPaint(rp);
                    g.fillRect(rpX, idY, rpW, idH);
                    g.setColor(new Color(80, 82, 88));
                    g.drawRect(rpX, idY, rpW, idH);
                    if (half < 5) {
                        g.setColor(new Color(100, 102, 108));
                        g.fillRoundRect(rpX + 1, idY + idH/2 - 8, 4, 16, 2, 2);
                    }
                }

                // Interior glow when doors open
                if (half > 0 && fda > 0.05) {
                    int gapX = idX + idW/2 - half;
                    int gapW = 2 * half;
                    GradientPaint glow = new GradientPaint(gapX, idY, new Color(255,240,200,(int)(fda*180)), gapX+gapW, idY, C_DOOR_INSIDE);
                    g.setPaint(glow);
                    g.fillRect(gapX, idY, gapW, idH);
                }
            } else {
                // Doors fully open - show interior details
                g.setColor(C_DOOR_INSIDE);
                g.fillRect(idX, idY, idW, idH);
                g.setColor(new Color(40, 42, 55));
                g.fillRect(idX, idY, 6, idH);
                g.fillRect(idX+idW-6, idY, 6, idH);
            }

            // Header
            GradientPaint header = new GradientPaint(fdX, fdY, new Color(200,200,205), fdX, fdY+8, new Color(130,130,138));
            g.setPaint(header);
            g.fillRect(fdX, fdY, fdW, 8);

            // Floor LED
            drawFloorLED(g, fdX + fdW/2 - 15, fdY - 17, f, cabAtThisFloor);

            // Floor label
            g.setColor(new Color(240,232,218));
            g.setFont(new Font("Consolas", Font.BOLD, 13));
            String lbl = f == 0 ? "G" : String.valueOf(f);
            g.drawString(lbl, lx + 6, lt + lh/2 + 5);

            // Call buttons
            int[] cb = callBtnPos(f);
            if (f < NUM_FLOORS-1) drawWallBtn(g, cb[0], cb[1], true, callUp[f], hx, hy);
            if (f > 0) drawWallBtn(g, cb[0], cb[1]+18, false, callDn[f], hx, hy);

            // Slab
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
            // Smaller pots - scale down by 30%
            int potW = (int)(pw * 0.5);
            int potH = (int)(ph * 0.2);
            int potX = px + (pw - potW) / 2;
            int potY = py + ph - potH;

            // Random pot color based on position (consistent per floor)
            Color[] potColors = {
                new Color(210, 105, 30),  // Chocolate
                new Color(205, 92, 92),   // Indian Red
                new Color(70, 130, 180),  // Steel Blue
                new Color(255, 140, 0),   // Dark Orange
                new Color(128, 0, 128),   // Purple
                new Color(0, 128, 128),   // Teal
            };
            Color potColor = potColors[(px + py) % potColors.length];

            // Draw pot with gradient
            GradientPaint potGrad = new GradientPaint(potX, potY, potColor.brighter(), 
                                                      potX, potY + potH, potColor.darker());
            g.setPaint(potGrad);
            int[] potXs = {potX, potX + potW, potX + potW - 4, potX + 4};
            int[] potYs = {potY, potY, potY + potH, potY + potH};
            g.fillPolygon(potXs, potYs, 4);

            // Pot rim
            g.setColor(potColor.brighter());
            g.fillRect(potX, potY, potW, 4);
            g.setColor(new Color(60, 60, 60));
            g.setStroke(new BasicStroke(1));
            g.drawPolygon(potXs, potYs, 4);

            // Soil
            g.setColor(new Color(50, 40, 30));
            g.fillRect(potX + 3, potY + 2, potW - 6, 4);

            // Varied plant types based on position
            int plantType = (px * 3 + py * 7) % 3;
            int stemX = potX + potW / 2;
            int stemY = potY;

            if (plantType == 0) {
                // Tall cactus style
                Color cactusColor = new Color(80, 160, 80);
                g.setColor(cactusColor);
                g.fillRoundRect(stemX - 4, stemY - 18, 8, 18, 4, 4);
                g.fillRoundRect(stemX - 7, stemY - 10, 4, 8, 2, 2);
                g.fillRoundRect(stemX + 3, stemY - 12, 4, 7, 2, 2);
                // Spines
                g.setColor(new Color(200, 200, 150));
                for (int i = 0; i < 5; i++) {
                    int sy = stemY - 5 - i * 4;
                    g.drawLine(stemX - 6, sy, stemX - 8, sy);
                    g.drawLine(stemX + 6, sy, stemX + 8, sy);
                }
            } else if (plantType == 1) {
                // Colorful flower style
                Color[] flowerColors = {
                    new Color(255, 100, 100), new Color(255, 200, 50), 
                    new Color(150, 100, 255), new Color(255, 100, 200)
                };
                Color flowerColor = flowerColors[(px + py) % flowerColors.length];

                // Stem
                g.setColor(new Color(60, 140, 60));
                g.fillRect(stemX - 1, stemY - 14, 2, 14);

                // Leaves
                g.fillOval(stemX - 6, stemY - 8, 5, 4);
                g.fillOval(stemX + 1, stemY - 10, 5, 4);

                // Flower petals
                g.setColor(flowerColor);
                for (int i = 0; i < 5; i++) {
                    double angle = i * 2 * Math.PI / 5;
                    int fx = stemX + (int)(Math.cos(angle) * 4);
                    int fy = stemY - 22 + (int)(Math.sin(angle) * 4);
                    g.fillOval(fx - 3, fy - 3, 6, 6);
                }
                g.setColor(new Color(255, 255, 100));
                g.fillOval(stemX - 2, stemY - 17, 4, 4);
            } else {
                // Fern/leafy style with varied colors
                Color[] leafColors = {
                    new Color(60, 160, 80), new Color(100, 180, 60), 
                    new Color(80, 140, 100), new Color(120, 160, 40)
                };

                for (int i = 0; i < 4; i++) {
                    g.setColor(leafColors[i % leafColors.length]);
                    int lx = stemX + (i - 2) * 3;
                    int ly = stemY - 10 - (i % 2) * 5;
                    int[] leafX = {stemX, lx - 3, lx + 3};
                    int[] leafY = {stemY, ly, ly + 3};
                    g.fillPolygon(leafX, leafY, 3);
                }
            }
        }

        void drawPictureFrames(Graphics2D g, int wallX, int wallY, int wallW, int wallH, int floor) {
            // One small picture frame per floor, positioned higher up
            int frameW = 20;
            int frameH = 15;
            int frameY = wallY + wallH / 5;  // Higher up (1/5 from top)
            int frameX = wallX + 15;  // Left side

            drawSingleFrame(g, frameX, frameY, frameW, frameH, floor);
        }
        void drawSingleFrame(Graphics2D g, int x, int y, int w, int h, int seed) {
            // Frame border
            Color[] frameColors = {
                new Color(101, 67, 33),    // Dark wood
                new Color(139, 69, 19),    // Saddle brown
                new Color(160, 82, 45),    // Sienna
                new Color(112, 128, 144),  // Slate gray
                new Color(70, 70, 70),     // Dark gray
            };
            Color frameColor = frameColors[seed % frameColors.length];

            // Shadow
            g.setColor(new Color(0, 0, 0, 40));
            g.fillRect(x + 2, y + 2, w, h);

            // Frame
            g.setColor(frameColor);
            g.fillRect(x, y, w, h);

            // Inner bevel
            g.setColor(frameColor.brighter());
            g.fillRect(x, y, w, 2);
            g.fillRect(x, y, 2, h);
            g.setColor(frameColor.darker());
            g.fillRect(x, y + h - 2, w, 2);
            g.fillRect(x + w - 2, y, 2, h);

            // Picture content (abstract art)
            int innerX = x + 3;
            int innerY = y + 3;
            int innerW = w - 6;
            int innerH = h - 6;

            // Background
            Color[] bgColors = {
                new Color(135, 206, 235), // Sky blue
                new Color(255, 218, 185), // Peach
                new Color(144, 238, 144), // Light green
                new Color(255, 192, 203), // Pink
                new Color(230, 230, 250), // Lavender
            };
            g.setColor(bgColors[seed % bgColors.length]);
            g.fillRect(innerX, innerY, innerW, innerH);

            // Abstract shapes
            Random frameRng = new Random(seed);
            int shapeType = frameRng.nextInt(3);

            if (shapeType == 0) {
                // Circle
                g.setColor(new Color(frameRng.nextInt(200), frameRng.nextInt(200), frameRng.nextInt(200)));
                int cx = innerX + innerW/2;
                int cy = innerY + innerH/2;
                int r = Math.min(innerW, innerH) / 3;
                g.fillOval(cx - r, cy - r, r*2, r*2);
            } else if (shapeType == 1) {
                // Rectangle
                g.setColor(new Color(frameRng.nextInt(200), frameRng.nextInt(200), frameRng.nextInt(200)));
                int rw = innerW / 2;
                int rh = innerH / 2;
                g.fillRect(innerX + (innerW-rw)/2, innerY + (innerH-rh)/2, rw, rh);
            } else {
                // Lines
                g.setColor(new Color(frameRng.nextInt(150), frameRng.nextInt(150), frameRng.nextInt(150)));
                g.setStroke(new BasicStroke(2));
                for (int i = 0; i < 3; i++) {
                    int y1 = innerY + frameRng.nextInt(innerH);
                    int y2 = innerY + frameRng.nextInt(innerH);
                    g.drawLine(innerX, y1, innerX + innerW, y2);
                }
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
            int shaftX = getShaftX();
            g.setColor(new Color(15, 18, 28));
            g.fillRect(shaftX, 0, SHAFT_W, getHeight());
            g.setColor(new Color(55, 70, 95));
            g.setStroke(new BasicStroke(4));
            g.drawLine(shaftX + 8, 5, shaftX + 8, getHeight());
            g.drawLine(shaftX + SHAFT_W - 8, 5, shaftX + SHAFT_W - 8, getHeight());
            g.setColor(new Color(70, 88, 115));
            g.setStroke(new BasicStroke(1));
            for (int y = 15; y < getHeight(); y += 28) {
                g.fillOval(shaftX + 5, y, 6, 6);
                g.fillOval(shaftX + SHAFT_W - 11, y, 6, 6);
            }
            g.setColor(new Color(38, 48, 68));
            g.setStroke(new BasicStroke(2));
            g.drawLine(shaftX, 0, shaftX, getHeight());
            g.drawLine(shaftX + SHAFT_W, 0, shaftX + SHAFT_W, getHeight());
        }

        void drawCable(Graphics2D g) {
            int shaftX = getShaftX();
            int cx = shaftX + SHAFT_W/2, top = (int)cabY;
            g.setColor(C_CABLE);
            g.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(cx-10, 0, cx-10, top);
            g.drawLine(cx+10, 0, cx+10, top);
            g.setStroke(new BasicStroke(1));
            g.setColor(new Color(100,80,40));
            g.drawLine(cx, 0, cx, top);
        }

        void drawCabin(Graphics2D g) {
            int shaftX = getShaftX();
            int x = shaftX + 10;
            int y = (int)cabY;
            int w = SHAFT_W - 20;

            // Shadow
            g.setColor(new Color(0,0,0,100));
            g.fillRoundRect(x+4, y+6, w, CAB_H, 6, 6);

            // Body
            GradientPaint bodyGrad = new GradientPaint(x, y, C_CAB_TOP, x, y+CAB_H, C_CAB_BOT);
            g.setPaint(bodyGrad);
            g.fillRoundRect(x, y, w, CAB_H, 6, 6);

            // Top light
            g.setColor(new Color(235, 245, 255));
            g.fillRoundRect(x+3, y+2, w-6, 9, 3, 3);

            // Door area
            int dh = CAB_H - 14;
            int dy = y + 12;
            int dtw = w - 14;
            int half = (int)(doorAnim * dtw / 2);
            int mid = x + 7 + dtw/2;

            // Interior (dark)
            g.setColor(new Color(18, 20, 32));
            g.fillRect(x+7, dy, dtw, dh);
            
            // Interior light when doors open
            if (doorAnim > 0.05) {
                g.setColor(new Color(255, 242, 205, (int)(doorAnim*0.55*255)));
                g.fillRect(x+7+half, dy, dtw-2*half, dh);
            }

            // Left door
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
            
            // Right door
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

            // Floor indicator
            g.setColor(new Color(18, 18, 22));
            g.fillRoundRect(x+w/2-14, y-20, 28, 18, 4, 4);
            g.setColor(C_LED_ON);
            g.setFont(new Font("Consolas", Font.BOLD, 12));
            String ft = currentFloor==0?"G":String.valueOf(currentFloor);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(ft, x+w/2-fm.stringWidth(ft)/2, y-5);

            // Direction arrows
            if (dir==Dir.UP) fillTri(g,x+w/2,y+3,true, C_UP);
            if (dir==Dir.DOWN) fillTri(g,x+w/2,y+3,false, C_DOWN);

            // Border
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
            int px = getPanelX();
            int ph = getHeight() - 20;
            
            g.setPaint(new GradientPaint(px, 0, new Color(24,34,54), px+PANEL_W-10, 0, new Color(16,22,40)));
            g.fillRoundRect(px, 10, PANEL_W-10, ph, 12, 12);
            g.setColor(new Color(48,68,98));
            g.setStroke(new BasicStroke(2));
            g.drawRoundRect(px, 10, PANEL_W-10, ph, 12, 12);

            g.setColor(new Color(98,158,238));
            g.setFont(new Font("Consolas", Font.BOLD, 14));
            g.drawString(">> FLOOR SELECT <<", px+14, 32);
            g.setColor(new Color(48,68,98));
            g.drawLine(px+8, 38, px+PANEL_W-18, 38);

            // Floor buttons
            int bsz=36, cols=4, sy2=50, sx2=px+14;
            for (int i=NUM_FLOORS-1; i>=0; i--) {
                int col=(NUM_FLOORS-1-i)%cols, row=(NUM_FLOORS-1-i)/cols;
                int bx=sx2+col*(bsz+8), by=sy2+row*(bsz+8);
                boolean hov2=hx>=bx&&hx<=bx+bsz&&hy>=by&&hy<=by+bsz;
                drawFloorBtn(g, bx, by, bsz, i, pending.contains(i), i==currentFloor, hov2);
            }
            
            int divY=sy2+((NUM_FLOORS+cols-1)/cols)*(bsz+8)+5;
            g.setColor(new Color(48,68,98));
            g.drawLine(px+8, divY, px+PANEL_W-18, divY);

            // Status
            int iy=divY+18;
            g.setColor(new Color(78,118,178));
            g.setFont(new Font("Consolas", Font.BOLD, 12));
            g.drawString("STATUS", px+14, iy);
            g.setFont(new Font("Consolas", Font.PLAIN, 12));
            g.setColor(C_TEXT);
            String s;
            if (moving) s=(dir==Dir.UP?"^ Moving UP":"v Moving DOWN");
            else if (doorOpening) s="<> Opening doors...";
            else if (doorsOpen) s="<  Doors Open  >";
            else if (doorClosing) s=">< Closing doors...";
            else s="* Idle";
            g.drawString(s, px+14, iy+18);
            g.setColor(new Color(78,118,178));
            g.drawString("FLOOR  : "+(currentFloor==0?"Ground":"Floor "+currentFloor), px+14, iy+36);
            
            StringBuilder sb=new StringBuilder();
            for(int f: new TreeSet<>(pending)) sb.append(f==0?"G":f).append(" ");
            g.setColor(C_TEXT);
            g.drawString("QUEUE  : "+(sb.length()==0?"--":sb.toString().trim()), px+14, iy+54);
            
            drawMini(g, px+14, iy+72);
            
            // Controls
            int cy = iy + 170;
            g.setColor(new Color(48, 68, 98));
            g.drawLine(px + 8, cy - 8, px + PANEL_W - 18, cy - 8);
            
            // Speed
            g.setColor(C_LED_ON);
            g.setFont(new Font("Consolas", Font.BOLD, 10));
            g.drawString("SPEED: " + String.format("%.0f%%", speedMultiplier * 100), px + 14, cy + 12);
            
            int speedBtnSize = 20;
            int btnX = px + 14;
            speedDownBounds.setBounds(btnX + 80, cy - 5, speedBtnSize, speedBtnSize);
            speedUpBounds.setBounds(btnX + 105, cy - 5, speedBtnSize, speedBtnSize);
            boolean speedDownHover = hx >= btnX + 80 && hx <= btnX + 100 && hy >= cy - 5 && hy <= cy + 15;
            boolean speedUpHover = hx >= btnX + 105 && hx <= btnX + 125 && hy >= cy - 5 && hy <= cy + 15;
            drawSmallButton(g, btnX + 80, cy - 5, speedBtnSize, "-", speedDownHover);
            drawSmallButton(g, btnX + 105, cy - 5, speedBtnSize, "+", speedUpHover);
            
            // Random
            cy += 26;
            int btnW = PANEL_W - 34;
            int btnH = 22;
            boolean randomHover = hx >= btnX && hx <= btnX + btnW && hy >= cy && hy <= cy + btnH;
            randomBtnBounds.setBounds(btnX, cy, btnW, btnH);
            drawCompactButton(g, btnX, cy, btnW, btnH, "RANDOM: " + (randomMode ? "ON" : "OFF"), randomHover, randomMode);
            
            // Sound
            cy += 26;
            boolean soundHover = hx >= btnX && hx <= btnX + btnW && hy >= cy && hy <= cy + btnH;
            soundBtnBounds.setBounds(btnX, cy, btnW, btnH);
            drawCompactButton(g, btnX, cy, btnW, btnH, "SOUND: " + (soundEnabled ? "ON" : "OFF"), soundHover, soundEnabled);
            
            // Add passenger button (hidden while picker is open)
            cy += 26;
            if (passengerPickMode == 0) {
                boolean addHover = hx >= btnX && hx <= btnX + btnW && hy >= cy && hy <= cy + btnH;
                addPassBounds.setBounds(btnX, cy, btnW, btnH);
                drawCompactButton(g, btnX, cy, btnW, btnH, "+ PASSENGER", addHover, false);
            } else {
                addPassBounds.setBounds(0, 0, 0, 0);

                // header
                cy += 2;
                g.setColor(new Color(98, 158, 238));
                g.setFont(new Font("Consolas", Font.BOLD, 11));
                String hdr = passengerPickMode == 1 ? "START FLOOR:" : "DESTINATION:";
                g.drawString(hdr, btnX, cy + 11);
                cy += 18;

                // floor grid
                int ibsz = 36, igap = 8, icols = 4;
                for (int i = NUM_FLOORS - 1; i >= 0; i--) {
                    int idx  = NUM_FLOORS - 1 - i;
                    int icol = idx % icols, irow = idx / icols;
                    int ibx  = btnX + icol * (ibsz + igap);
                    int iby  = cy   + irow * (ibsz + igap);
                    inlineFloorBounds[i].setBounds(ibx, iby, ibsz, ibsz);
                    boolean disableBtn = (passengerPickMode == 2 && i == pickedStartFloor);
                    boolean hovBtn = !disableBtn && hx >= ibx && hx <= ibx+ibsz && hy >= iby && hy <= iby+ibsz;
                    if (disableBtn) {
                        g.setColor(new Color(30, 38, 55));
                        g.fillRoundRect(ibx, iby, ibsz, ibsz, 8, 8);
                        g.setColor(new Color(42, 52, 72));
                        g.setStroke(new BasicStroke(1));
                        g.drawRoundRect(ibx, iby, ibsz, ibsz, 8, 8);
                        g.setColor(new Color(60, 70, 90));
                        g.setFont(new Font("Consolas", Font.BOLD, 14));
                        String dl = i==0?"G":String.valueOf(i);
                        FontMetrics dfm = g.getFontMetrics();
                        g.drawString(dl, ibx+(ibsz-dfm.stringWidth(dl))/2, iby+(ibsz+dfm.getAscent()-dfm.getDescent())/2);
                    } else {
                        drawFloorBtn(g, ibx, iby, ibsz, i, false, false, hovBtn);
                    }
                }
                int gridRows = (NUM_FLOORS + icols - 1) / icols;
                cy += gridRows * (ibsz + igap) + 4;

                // add-no-dest button (step 1 only)
                if (passengerPickMode == 1) {
                    boolean noDestHov = hx >= btnX && hx <= btnX + btnW && hy >= cy && hy <= cy + btnH;
                    inlineNoDestBounds.setBounds(btnX, cy, btnW, btnH);
                    drawCompactButton(g, btnX, cy, btnW, btnH, "ADD & WAIT FOR DEST", noDestHov, false);
                    cy += btnH + 4;
                } else {
                    inlineNoDestBounds.setBounds(0, 0, 0, 0);
                }

                // cancel link
                boolean cancelHov = hx >= btnX && hx <= btnX + btnW && hy >= cy && hy <= cy + 16;
                g.setColor(cancelHov ? new Color(218, 98, 78) : new Color(160, 80, 80));
                g.setFont(new Font("Consolas", Font.PLAIN, 11));
                FontMetrics cfm = g.getFontMetrics();
                g.drawString("\u2715  Cancel", btnX + (btnW - cfm.stringWidth("\u2715  Cancel"))/2, cy + 12);
                inlineCancelBounds.setBounds(btnX, cy, btnW, 16);
            }
        }
        
        void drawCompactButton(Graphics2D g, int x, int y, int w, int h, String text, boolean hover, boolean active) {
            Color bg = active ? C_BTN_ACT : hover ? C_BTN_HOV : C_BTN_IDLE;
            g.setColor(bg);
            g.fillRoundRect(x, y, w, h, 6, 6);
            g.setColor(active ? new Color(0, 200, 100) : new Color(48, 68, 98));
            g.setStroke(new BasicStroke(1));
            g.drawRoundRect(x, y, w, h, 6, 6);
            g.setColor(active ? Color.BLACK : C_TEXT);
            g.setFont(new Font("Consolas", Font.BOLD, 10));
            FontMetrics fm = g.getFontMetrics();
            g.drawString(text, x + (w - fm.stringWidth(text)) / 2, y + h/2 + 4);
        }
        
        void drawSmallButton(Graphics2D g, int x, int y, int size, String text, boolean hover) {
            g.setColor(hover ? C_BTN_HOV : C_BTN_IDLE);
            g.fillRoundRect(x, y, size, size, 4, 4);
            g.setColor(new Color(48, 68, 98));
            g.drawRoundRect(x, y, size, size, 4, 4);
            g.setColor(C_TEXT);
            g.setFont(new Font("Consolas", Font.BOLD, 12));
            FontMetrics fm = g.getFontMetrics();
            g.drawString(text, x + (size - fm.stringWidth(text)) / 2, y + size/2 + 5);
        }

        void drawFloorBtn(Graphics2D g, int x, int y, int sz, int floor,
                          boolean pend, boolean cur, boolean hov) {
            Color bg = cur && !moving ? C_BTN_ACT : pend ? C_BTN_PEND : hov ? C_BTN_HOV : C_BTN_IDLE;
            
            if (pend) {
                g.setColor(new Color(200, 140, 30, 55));
                g.fillRoundRect(x - 3, y - 3, sz + 6, sz + 6, 10, 10);
            }
            
            GradientPaint gp = new GradientPaint(x, y, bg.brighter(), x, y + sz, bg);
            g.setPaint(gp);
            g.fillRoundRect(x, y, sz, sz, 8, 8);
            
            g.setColor(pend ? Color.WHITE : cur ? new Color(18, 18, 18) : C_TEXT);
            g.setFont(new Font("Consolas", Font.BOLD, 14));
            String lbl = floor == 0 ? "G" : String.valueOf(floor);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(lbl, x + (sz - fm.stringWidth(lbl)) / 2, y + (sz + fm.getAscent() - fm.getDescent()) / 2);
            
            g.setColor(pend ? new Color(255, 180, 50) : cur ? new Color(58, 198, 118) : new Color(48, 68, 98));
            g.setStroke(new BasicStroke(pend ? 2 : 1));
            g.drawRoundRect(x, y, sz, sz, 8, 8);
        }

        void drawMini(Graphics2D g, int x, int y) {
            int bw = 22, bh = 8, gap = 2, totalH = NUM_FLOORS * (bh + gap);
            
            g.setColor(new Color(18, 26, 42));
            g.fillRoundRect(x, y, bw + 4, totalH + 4, 4, 4);
            
            for (int f = NUM_FLOORS - 1; f >= 0; f--) {
                int fy = y + 2 + (NUM_FLOORS - 1 - f) * (bh + gap);
                Color c = f == currentFloor ? C_BTN_ACT : 
                          pending.contains(f) ? C_BTN_PEND : new Color(28, 38, 58);
                g.setColor(c);
                g.fillRoundRect(x + 2, fy, bw, bh, 3, 3);
            }
            
            double maxY = floorToY(0), minY = floorToY(NUM_FLOORS - 1);
            double norm = (cabY - minY) / (maxY - minY);
            int my2 = (int)(y + 2 + norm * (totalH - bh));
            
            g.setColor(new Color(255, 198, 48, 188));
            g.fillRoundRect(x, my2, bw + 4, bh + 2, 3, 3);
        }
        
        void drawStatsPanel(Graphics2D g) {
            int sx = getStatsX();
            int sy = 15;
            int sw = STATS_W - 10;
            int sh = getHeight() - 30;

            g.setColor(new Color(15, 20, 35, 240));
            g.fillRoundRect(sx, sy, sw, sh, 12, 12);
            g.setColor(C_LED_ON);
            g.setStroke(new BasicStroke(2));
            g.drawRoundRect(sx, sy, sw, sh, 12, 12);

            g.setFont(new Font("Consolas", Font.BOLD, 14));
            g.drawString("◊ STATS ◊", sx + 20, sy + 28);
            g.drawLine(sx + 10, sy + 34, sx + sw - 10, sy + 34);

            g.setFont(new Font("Consolas", Font.PLAIN, 12));
            int ly = sy + 55;
            int lineH = 22;

            long runtime = (System.currentTimeMillis() - startTime) / 1000;

            drawStatLine(g, "TIME:", formatTime(runtime), sx + 10, ly);
            drawStatLine(g, "TRIPS:", String.valueOf(totalTrips), sx + 10, ly + lineH);
            drawStatLine(g, "PASS:", String.valueOf(totalPassengers), sx + 10, ly + lineH * 2);
            drawStatLine(g, "AVG:", String.format("%.1fs", avgWaitTime), sx + 10, ly + lineH * 3);
            drawStatLine(g, "MAX:", maxWaitTime + "s", sx + 10, ly + lineH * 4);

            g.setColor(C_LED_ON);
            g.setFont(new Font("Consolas", Font.BOLD, 12));
            g.drawString("VISITS:", sx + 10, ly + lineH * 6);

            int barY = ly + lineH * 7;
            int maxVisits = Math.max(1, Arrays.stream(floorVisits).max().orElse(1));
            int barW = (sw - 20) / NUM_FLOORS;

            for (int f = 0; f < NUM_FLOORS; f++) {
                int barH = (int)(50 * floorVisits[f] / (double)maxVisits);
                int bx = sx + 10 + f * barW;

                g.setColor(new Color(40, 50, 70));
                g.fillRect(bx, barY + 50 - barH, barW - 2, barH);

                g.setColor(floorVisits[f] > 0 ? C_BTN_ACT : new Color(60, 70, 90));
                g.fillRect(bx, barY + 50 - barH, barW - 2, barH);

                g.setColor(C_TEXT);
                g.setFont(new Font("Arial", Font.PLAIN, 10));
                g.drawString(f == 0 ? "G" : String.valueOf(f), bx + 2, barY + 64);
            }

            g.setColor(C_LED_ON);
            g.setFont(new Font("Consolas", Font.BOLD, 12));
            g.drawString("EFFICIENCY:", sx + 10, barY + 90);

            double efficiency = totalTrips > 0 ? Math.min(100, 50 + (totalPassengers * 10.0 / totalTrips)) : 50;
            int meterW = sw - 20;
            int meterFill = (int)(meterW * efficiency / 100);

            g.setColor(new Color(40, 50, 70));
            g.fillRoundRect(sx + 10, barY + 100, meterW, 16, 8, 8);

            GradientPaint effGrad = new GradientPaint(sx + 10, 0, C_DOWN, sx + 10 + meterW, 0, C_UP);
            g.setPaint(effGrad);
            g.fillRoundRect(sx + 10, barY + 100, meterFill, 16, 8, 8);

            g.setColor(C_TEXT);
            g.setFont(new Font("Consolas", Font.BOLD, 11));
            g.drawString(String.format("%.0f%%", efficiency), sx + sw/2 - 15, barY + 112);

            // NEW: Passenger info section
            g.setColor(C_LED_ON);
            g.setFont(new Font("Consolas", Font.BOLD, 12));
            g.drawString("PASSENGERS:", sx + 10, barY + 145);

            g.setFont(new Font("Consolas", Font.PLAIN, 11));
            g.setColor(C_TEXT);
            g.drawString("Waiting: " + passengers.size(), sx + 10, barY + 168);
            g.drawString("Inside: " + inElevator.size(), sx + 10, barY + 190);
        }

        void drawStatLine(Graphics2D g, String label, String value, int x, int y) {
            g.setColor(new Color(100, 120, 150));
            g.setFont(new Font("Consolas", Font.PLAIN, 12));
            g.drawString(label, x, y);
            g.setColor(C_TEXT);
            g.drawString(value, x + 60, y);
        }
        
        String formatTime(long seconds) {
            long mins = seconds / 60;
            long secs = seconds % 60;
            return String.format("%d:%02d", mins, secs);
        }

        void drawBottomBar(Graphics2D g) {
            g.setColor(new Color(8, 12, 22, 210));
            g.fillRect(0, getHeight() - 20, getWidth(), 20);
            g.setColor(new Color(55, 75, 108));
            g.setFont(new Font("Consolas", Font.PLAIN, 10));
            g.drawString("Click FLOOR SELECT panel or wall call buttons (▲▼) | Elevator Simulation G–7 | +PASSENGER to add", 10, getHeight() - 5);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            new ElevatorSimulation().setVisible(true);
        });
    }
        
}