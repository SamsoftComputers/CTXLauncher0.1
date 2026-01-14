/*
 * CTXLauncher v0.3 - Minecraft Java Edition Launcher (Offline Mode)
 * Team Flames / Samsoft / Cat OS
 * Fixed: -XstartOnFirstThread for macOS GLFW requirement
 * Run: java CTXLauncher.java
 */

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.regex.*;
import java.util.zip.*;

public class CTXLauncher {
    private static final String VERSION_MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest.json";
    private static final String RESOURCES_URL = "https://resources.download.minecraft.net/";
    private static final String LAUNCHER_NAME = "CTXLauncher";
    private static final String LAUNCHER_VERSION = "0.3";
    private static final int DOWNLOAD_TIMEOUT = 30000;
    private static final int DOWNLOAD_THREADS = 8;

    private static final String OS_NAME = System.getProperty("os.name").toLowerCase();
    private static final String OS_ARCH = System.getProperty("os.arch").toLowerCase();
    private static final boolean IS_WINDOWS = OS_NAME.contains("win");
    private static final boolean IS_MAC = OS_NAME.contains("mac");
    private static final boolean IS_ARM = OS_ARCH.contains("aarch64") || OS_ARCH.contains("arm");

    private File gameDirectory, versionsDir, librariesDir, assetsDir, nativesDir;
    private JFrame frame;
    private JTextField usernameField, gameDirField, jvmArgsField;
    private JSlider ramSlider;
    private JComboBox<String> versionTypeCombo, versionCombo;
    private JButton playButton;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JTextArea logArea;

    private Map<String, VersionInfo> versionCache = new ConcurrentHashMap<>();
    private List<VersionInfo> allVersions = new CopyOnWriteArrayList<>();
    private ExecutorService executor = Executors.newFixedThreadPool(DOWNLOAD_THREADS);
    private volatile boolean isDownloading = false;
    private AtomicInteger downloadedFiles = new AtomicInteger(0);
    private AtomicInteger totalFiles = new AtomicInteger(0);
    private List<File> nativeJars = Collections.synchronizedList(new ArrayList<>());

    private static class VersionInfo {
        String id, type, url;
        VersionInfo(String id, String type, String url) { this.id = id; this.type = type; this.url = url; }
        public String toString() { return id; }
    }

    public static void main(String[] args) { SwingUtilities.invokeLater(CTXLauncher::new); }

    public CTXLauncher() {
        initDirs();
        createUI();
        loadVersionManifest();
    }

    private void initDirs() {
        String home = System.getProperty("user.home");
        if (IS_WINDOWS) gameDirectory = new File(System.getenv("APPDATA"), ".minecraft");
        else if (IS_MAC) gameDirectory = new File(home, "Library/Application Support/minecraft");
        else gameDirectory = new File(home, ".minecraft");
        
        versionsDir = new File(gameDirectory, "versions");
        librariesDir = new File(gameDirectory, "libraries");
        assetsDir = new File(gameDirectory, "assets");
        nativesDir = new File(gameDirectory, "natives");
        
        versionsDir.mkdirs();
        new File(assetsDir, "indexes").mkdirs();
        new File(assetsDir, "objects").mkdirs();
        librariesDir.mkdirs();
        nativesDir.mkdirs();
        
        log("OS: " + OS_NAME + " / Arch: " + OS_ARCH + (IS_ARM ? " (ARM)" : " (x64)"));
        log("Game Dir: " + gameDirectory);
    }

    private void createUI() {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception e) {}
        
        frame = new JFrame(LAUNCHER_NAME + " " + LAUNCHER_VERSION);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(700, 600);
        frame.setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10)) {
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setPaint(new GradientPaint(0, 0, new Color(0, 102, 204), 0, getHeight(), Color.WHITE));
                g2d.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        JLabel title = new JLabel(LAUNCHER_NAME + " v" + LAUNCHER_VERSION, SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 28));
        title.setForeground(new Color(100, 180, 255));
        mainPanel.add(title, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Play", createPlayPanel());
        tabs.addTab("Settings", createSettingsPanel());
        tabs.addTab("Log", createLogPanel());
        mainPanel.add(tabs, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(5, 5));
        bottom.setOpaque(false);
        statusLabel = new JLabel("Ready");
        statusLabel.setForeground(Color.WHITE);
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        bottom.add(statusLabel, BorderLayout.NORTH);
        bottom.add(progressBar, BorderLayout.CENTER);
        mainPanel.add(bottom, BorderLayout.SOUTH);

        frame.setContentPane(mainPanel);
        frame.setVisible(true);
    }

    private JPanel createPlayPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(20, 20, 20, 20));
        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(8, 8, 8, 8);

        g.gridx = 0; g.gridy = 0; g.gridwidth = 1;
        p.add(label("Username:"), g);
        g.gridx = 1; g.gridwidth = 2;
        usernameField = textField("Player");
        p.add(usernameField, g);

        g.gridx = 0; g.gridy = 1; g.gridwidth = 1;
        p.add(label("Type:"), g);
        g.gridx = 1; g.gridwidth = 2;
        versionTypeCombo = new JComboBox<>(new String[]{"Release", "Snapshot", "Old Beta", "Old Alpha", "All"});
        styleCombo(versionTypeCombo);
        versionTypeCombo.addActionListener(e -> filterVersions());
        p.add(versionTypeCombo, g);

        g.gridx = 0; g.gridy = 2; g.gridwidth = 1;
        p.add(label("Version:"), g);
        g.gridx = 1;
        versionCombo = new JComboBox<>();
        styleCombo(versionCombo);
        p.add(versionCombo, g);
        g.gridx = 2;
        JButton refresh = new JButton("↻");
        refresh.addActionListener(e -> loadVersionManifest());
        p.add(refresh, g);

        g.gridx = 0; g.gridy = 3; g.gridwidth = 1;
        p.add(label("RAM:"), g);
        g.gridx = 1; g.gridwidth = 2;
        JPanel ramP = new JPanel(new BorderLayout(5, 0));
        ramP.setOpaque(false);
        ramSlider = new JSlider(1, 16, 4);
        ramSlider.setOpaque(false);
        JLabel ramLbl = label("4 GB");
        ramSlider.addChangeListener(e -> ramLbl.setText(ramSlider.getValue() + " GB"));
        ramP.add(ramSlider, BorderLayout.CENTER);
        ramP.add(ramLbl, BorderLayout.EAST);
        p.add(ramP, g);

        g.gridx = 0; g.gridy = 4; g.gridwidth = 3;
        g.insets = new Insets(25, 8, 8, 8);
        playButton = new JButton("PLAY") {
            protected void paintComponent(Graphics gr) {
                Graphics2D g2 = (Graphics2D) gr;
                g2.setColor(getModel().isPressed() ? new Color(60,140,60) : getModel().isRollover() ? new Color(80,180,80) : new Color(70,160,70));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 20));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(), (getWidth()-fm.stringWidth(getText()))/2, (getHeight()+fm.getAscent()-fm.getDescent())/2);
            }
        };
        playButton.setPreferredSize(new Dimension(200, 50));
        playButton.setContentAreaFilled(false);
        playButton.setBorderPainted(false);
        playButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        playButton.addActionListener(e -> launchGame());
        p.add(playButton, g);

        return p;
    }

    private JPanel createSettingsPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(20, 20, 20, 20));
        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(8, 8, 8, 8);

        g.gridx = 0; g.gridy = 0; g.gridwidth = 1;
        p.add(label("Game Dir:"), g);
        g.gridx = 1; g.gridwidth = 2;
        gameDirField = textField(gameDirectory.getAbsolutePath());
        gameDirField.setEditable(false);
        p.add(gameDirField, g);

        g.gridx = 0; g.gridy = 1; g.gridwidth = 1;
        p.add(label("JVM Args:"), g);
        g.gridx = 1; g.gridwidth = 2;
        jvmArgsField = textField("-XX:+UseG1GC -XX:+UnlockExperimentalVMOptions");
        p.add(jvmArgsField, g);

        return p;
    }

    private JPanel createLogPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(10, 10, 10, 10));
        logArea = new JTextArea();
        logArea.setBackground(new Color(20, 20, 25));
        logArea.setForeground(new Color(100, 255, 100));
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setEditable(false);
        p.add(new JScrollPane(logArea), BorderLayout.CENTER);
        return p;
    }

    private JLabel label(String t) { JLabel l = new JLabel(t); l.setForeground(Color.WHITE); return l; }
    private JTextField textField(String t) {
        JTextField f = new JTextField(t);
        f.setBackground(new Color(20, 60, 120));
        f.setForeground(new Color(100, 180, 255));
        f.setCaretColor(Color.WHITE);
        return f;
    }
    private void styleCombo(JComboBox<?> c) { c.setBackground(new Color(20, 60, 120)); c.setForeground(new Color(100, 180, 255)); }

    private void log(String m) {
        System.out.println(m);
        SwingUtilities.invokeLater(() -> { if (logArea != null) { logArea.append("[" + new java.text.SimpleDateFormat("HH:mm:ss").format(new Date()) + "] " + m + "\n"); logArea.setCaretPosition(logArea.getDocument().getLength()); }});
    }
    private void setStatus(String s) { SwingUtilities.invokeLater(() -> { if (statusLabel != null) statusLabel.setText(s); }); }
    private void setProgress(int v) { SwingUtilities.invokeLater(() -> { if (progressBar != null) { progressBar.setValue(v); progressBar.setString(v + "%"); }}); }

    private void loadVersionManifest() {
        setStatus("Loading versions...");
        executor.submit(() -> {
            try {
                String json = downloadString(VERSION_MANIFEST_URL);
                allVersions.clear();
                versionCache.clear();
                Pattern pat = Pattern.compile("\\{[^{}]*\"id\"\\s*:\\s*\"([^\"]+)\"[^{}]*\"type\"\\s*:\\s*\"([^\"]+)\"[^{}]*\"url\"\\s*:\\s*\"([^\"]+)\"[^{}]*\\}", Pattern.DOTALL);
                Matcher m = pat.matcher(json);
                while (m.find()) {
                    VersionInfo v = new VersionInfo(m.group(1), m.group(2), m.group(3));
                    allVersions.add(v);
                    versionCache.put(v.id, v);
                }
                SwingUtilities.invokeLater(() -> { filterVersions(); setStatus("Ready - " + allVersions.size() + " versions"); });
                log("Loaded " + allVersions.size() + " versions");
            } catch (Exception e) { log("Error: " + e.getMessage()); setStatus("Error loading versions"); }
        });
    }

    private void filterVersions() {
        if (versionCombo == null) return;
        String type = (String) versionTypeCombo.getSelectedItem();
        versionCombo.removeAllItems();
        for (VersionInfo v : allVersions) {
            boolean ok = type.equals("All") || (type.equals("Release") && v.type.equals("release")) || (type.equals("Snapshot") && v.type.equals("snapshot")) || (type.equals("Old Beta") && v.type.equals("old_beta")) || (type.equals("Old Alpha") && v.type.equals("old_alpha"));
            if (ok) versionCombo.addItem(v.id);
        }
        if (versionCombo.getItemCount() > 0) versionCombo.setSelectedIndex(0);
    }

    private void launchGame() {
        if (isDownloading) return;
        String ver = (String) versionCombo.getSelectedItem();
        if (ver == null) { JOptionPane.showMessageDialog(frame, "Select a version"); return; }
        String user = usernameField.getText().trim().replaceAll("[^a-zA-Z0-9_]", "_");
        if (user.isEmpty()) user = "Player";
        if (user.length() > 16) user = user.substring(0, 16);
        final String finalUser = user;

        playButton.setEnabled(false);
        playButton.setText("LAUNCHING...");
        isDownloading = true;
        downloadedFiles.set(0);
        totalFiles.set(0);
        nativeJars.clear();

        executor.submit(() -> {
            try {
                VersionInfo vi = versionCache.get(ver);
                if (vi == null) throw new Exception("Version not found");
                log("════════════════════════════════════════════════");
                log("Launching Minecraft " + ver);
                log("════════════════════════════════════════════════");

                // Download version JSON
                File verDir = new File(versionsDir, ver);
                verDir.mkdirs();
                File jsonFile = new File(verDir, ver + ".json");
                String vJson;
                if (!jsonFile.exists()) {
                    log("Downloading version JSON...");
                    vJson = downloadString(vi.url);
                    Files.write(jsonFile.toPath(), vJson.getBytes("UTF-8"));
                } else {
                    vJson = new String(Files.readAllBytes(jsonFile.toPath()), "UTF-8");
                }

                // Download client JAR
                File clientJar = new File(verDir, ver + ".jar");
                if (!clientJar.exists() || clientJar.length() < 1000) {
                    Pattern p = Pattern.compile("\"client\"\\s*:\\s*\\{[^}]*\"url\"\\s*:\\s*\"([^\"]+)\"", Pattern.DOTALL);
                    Matcher m = p.matcher(vJson);
                    if (m.find()) {
                        log("Downloading client JAR...");
                        setStatus("Downloading Minecraft...");
                        downloadFile(m.group(1), clientJar);
                        log("Client: " + (clientJar.length()/1024/1024) + " MB");
                    } else throw new Exception("Client URL not found");
                }

                // Download libraries
                setStatus("Downloading libraries...");
                downloadLibraries(vJson);

                // Extract natives
                File nDir = new File(nativesDir, ver);
                nDir.mkdirs();
                File[] oldNatives = nDir.listFiles();
                if (oldNatives != null) for (File old : oldNatives) if (old.isFile()) old.delete();
                
                log("Extracting natives to: " + nDir.getAbsolutePath());
                int extractedCount = 0;
                for (File nj : nativeJars) {
                    if (!nj.exists()) continue;
                    try (ZipFile zf = new ZipFile(nj)) {
                        Enumeration<? extends ZipEntry> en = zf.entries();
                        while (en.hasMoreElements()) {
                            ZipEntry ze = en.nextElement();
                            String nm = ze.getName();
                            if (ze.isDirectory() || nm.startsWith("META-INF")) continue;
                            if (nm.endsWith(".dll") || nm.endsWith(".so") || nm.endsWith(".dylib") || nm.endsWith(".jnilib")) {
                                File out = new File(nDir, new File(nm).getName());
                                try (InputStream in = zf.getInputStream(ze); FileOutputStream fos = new FileOutputStream(out)) {
                                    byte[] buf = new byte[8192]; int len;
                                    while ((len = in.read(buf)) > 0) fos.write(buf, 0, len);
                                }
                                extractedCount++;
                            }
                        }
                    } catch (Exception e) { log("Native extract error: " + e.getMessage()); }
                }
                log("Extracted " + extractedCount + " native libraries");
                
                // Check for LWJGL natives, extract from library folder if needed
                boolean hasLwjgl = false;
                File[] nativeFiles = nDir.listFiles();
                if (nativeFiles != null) for (File f : nativeFiles) if (f.getName().contains("lwjgl")) { hasLwjgl = true; break; }
                if (!hasLwjgl) {
                    log("Extracting LWJGL natives from library folder...");
                    extractLwjglNatives(nDir);
                }

                // Download assets
                setStatus("Downloading assets...");
                downloadAssets(vJson);

                // Build classpath
                StringBuilder cp = new StringBuilder();
                findJars(librariesDir, cp);
                cp.append(File.pathSeparator).append(clientJar.getAbsolutePath());

                // Get main class
                Pattern mcP = Pattern.compile("\"mainClass\"\\s*:\\s*\"([^\"]+)\"");
                Matcher mcM = mcP.matcher(vJson);
                String mainClass = mcM.find() ? mcM.group(1) : "net.minecraft.client.main.Main";

                // Get asset index
                Pattern aiP = Pattern.compile("\"assets\"\\s*:\\s*\"([^\"]+)\"");
                Matcher aiM = aiP.matcher(vJson);
                String assetIdx = aiM.find() ? aiM.group(1) : ver;

                // Generate UUID
                String uuid = genUUID(finalUser);

                // Build command
                List<String> cmd = new ArrayList<>();
                cmd.add(getJava());
                
                // ═══════════════════════════════════════════════════════════════════
                // CRITICAL: macOS requires -XstartOnFirstThread for GLFW/LWJGL
                // This MUST be the first JVM argument after java executable
                // ═══════════════════════════════════════════════════════════════════
                if (IS_MAC) {
                    cmd.add("-XstartOnFirstThread");
                    log("Added -XstartOnFirstThread for macOS GLFW compatibility");
                }
                
                cmd.add("-Xmx" + ramSlider.getValue() + "G");
                cmd.add("-Xms1G");
                
                // Native library paths
                cmd.add("-Djava.library.path=" + nDir.getAbsolutePath());
                cmd.add("-Dorg.lwjgl.librarypath=" + nDir.getAbsolutePath());
                cmd.add("-Djna.library.path=" + nDir.getAbsolutePath());
                
                // Enable native access for newer Java versions
                cmd.add("--enable-native-access=ALL-UNNAMED");
                
                // Launcher branding
                cmd.add("-Dminecraft.launcher.brand=" + LAUNCHER_NAME);
                cmd.add("-Dminecraft.launcher.version=" + LAUNCHER_VERSION);
                
                // Additional JVM args from settings
                String jvmArgs = jvmArgsField.getText().trim();
                if (!jvmArgs.isEmpty()) {
                    for (String a : jvmArgs.split("\\s+")) {
                        if (!a.isEmpty()) cmd.add(a);
                    }
                }
                
                // Classpath and main class
                cmd.add("-cp");
                cmd.add(cp.toString());
                cmd.add(mainClass);
                
                // Game arguments
                cmd.add("--username"); cmd.add(finalUser);
                cmd.add("--version"); cmd.add(ver);
                cmd.add("--gameDir"); cmd.add(gameDirectory.getAbsolutePath());
                cmd.add("--assetsDir"); cmd.add(assetsDir.getAbsolutePath());
                cmd.add("--assetIndex"); cmd.add(assetIdx);
                cmd.add("--uuid"); cmd.add(uuid);
                cmd.add("--accessToken"); cmd.add("0");
                cmd.add("--userType"); cmd.add("legacy");

                log("════════════════════════════════════════════════");
                log("Main Class: " + mainClass);
                log("Username: " + finalUser);
                log("Natives: " + nDir.getAbsolutePath());
                if (IS_MAC) log("macOS Mode: -XstartOnFirstThread enabled");
                log("════════════════════════════════════════════════");
                
                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.directory(gameDirectory);
                pb.redirectErrorStream(true);
                
                // Environment variables
                Map<String, String> env = pb.environment();
                env.put("JAVA_LIBRARY_PATH", nDir.getAbsolutePath());
                if (IS_MAC) env.put("DYLD_LIBRARY_PATH", nDir.getAbsolutePath());
                else if (!IS_WINDOWS) env.put("LD_LIBRARY_PATH", nDir.getAbsolutePath());
                
                Process proc = pb.start();

                new Thread(() -> {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                        String line;
                        while ((line = br.readLine()) != null) log("[MC] " + line);
                    } catch (Exception e) {}
                    try {
                        int exit = proc.waitFor();
                        log("Game exited with code: " + exit);
                    } catch (Exception e) {}
                }).start();

                setStatus("Minecraft launched!");
                setProgress(100);
                log("Minecraft " + ver + " started!");

            } catch (Exception e) {
                log("ERROR: " + e.getMessage());
                e.printStackTrace();
                setStatus("Launch failed");
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame, "Error: " + e.getMessage()));
            } finally {
                isDownloading = false;
                SwingUtilities.invokeLater(() -> { playButton.setEnabled(true); playButton.setText("PLAY"); });
            }
        });
    }

    private void downloadLibraries(String vJson) throws Exception {
        String osName = IS_WINDOWS ? "windows" : IS_MAC ? "osx" : "linux";
        
        // Native classifiers to look for (macOS ARM64 uses different naming)
        List<String> nativeKeys = new ArrayList<>();
        if (IS_MAC && IS_ARM) {
            nativeKeys.add("natives-macos-arm64");
            nativeKeys.add("natives-osx-arm64");
        }
        if (IS_MAC) {
            nativeKeys.add("natives-macos");
            nativeKeys.add("natives-osx");
        }
        nativeKeys.add("natives-" + osName);

        // Find artifact downloads
        Pattern artP = Pattern.compile("\"artifact\"\\s*:\\s*\\{[^}]*\"path\"\\s*:\\s*\"([^\"]+)\"[^}]*\"url\"\\s*:\\s*\"([^\"]+)\"", Pattern.DOTALL);
        Matcher artM = artP.matcher(vJson);
        List<String[]> libs = new ArrayList<>();
        while (artM.find()) libs.add(new String[]{artM.group(1), artM.group(2), "false"});

        // Find native classifiers
        for (String nativeKey : nativeKeys) {
            Pattern natP = Pattern.compile("\"" + Pattern.quote(nativeKey) + "\"\\s*:\\s*\\{[^}]*\"path\"\\s*:\\s*\"([^\"]+)\"[^}]*\"url\"\\s*:\\s*\"([^\"]+)\"", Pattern.DOTALL);
            Matcher natM = natP.matcher(vJson);
            while (natM.find()) {
                String path = natM.group(1);
                boolean exists = false;
                for (String[] lib : libs) if (lib[0].equals(path)) { exists = true; break; }
                if (!exists) libs.add(new String[]{path, natM.group(2), "true"});
            }
        }

        log("Found " + libs.size() + " libraries");
        totalFiles.addAndGet(libs.size());

        ExecutorService dl = Executors.newFixedThreadPool(DOWNLOAD_THREADS);
        for (String[] lib : libs) {
            dl.submit(() -> {
                try {
                    File f = new File(librariesDir, lib[0]);
                    if (!f.exists() || f.length() == 0) {
                        f.getParentFile().mkdirs();
                        downloadFile(lib[1], f);
                    }
                    if (lib[2].equals("true")) nativeJars.add(f);
                    downloadedFiles.incrementAndGet();
                    setProgress((int)(downloadedFiles.get() * 100.0 / Math.max(1, totalFiles.get())));
                } catch (Exception e) { log("Download failed: " + lib[0]); }
            });
        }
        dl.shutdown();
        dl.awaitTermination(5, TimeUnit.MINUTES);
        log("Libraries done. Native JARs: " + nativeJars.size());
    }
    
    private void extractLwjglNatives(File nDir) {
        // Determine correct LWJGL native suffix for this platform
        String osSuffix = IS_WINDOWS ? "windows" : IS_MAC ? (IS_ARM ? "macos-arm64" : "macos") : "linux";
        File lwjglDir = new File(librariesDir, "org/lwjgl");
        if (!lwjglDir.exists()) return;
        
        List<File> lwjglNatives = new ArrayList<>();
        findLwjglNatives(lwjglDir, lwjglNatives, osSuffix);
        log("Found " + lwjglNatives.size() + " LWJGL native JARs");
        
        for (File nj : lwjglNatives) {
            try (ZipFile zf = new ZipFile(nj)) {
                Enumeration<? extends ZipEntry> en = zf.entries();
                while (en.hasMoreElements()) {
                    ZipEntry ze = en.nextElement();
                    String nm = ze.getName();
                    if (ze.isDirectory() || nm.startsWith("META-INF")) continue;
                    if (nm.endsWith(".dll") || nm.endsWith(".so") || nm.endsWith(".dylib") || nm.endsWith(".jnilib")) {
                        File out = new File(nDir, new File(nm).getName());
                        if (!out.exists()) {
                            try (InputStream in = zf.getInputStream(ze); FileOutputStream fos = new FileOutputStream(out)) {
                                byte[] buf = new byte[8192]; int len;
                                while ((len = in.read(buf)) > 0) fos.write(buf, 0, len);
                            }
                            log("  Extracted: " + out.getName());
                        }
                    }
                }
            } catch (Exception e) {}
        }
    }
    
    private void findLwjglNatives(File dir, List<File> result, String osSuffix) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) findLwjglNatives(f, result, osSuffix);
            else if (f.getName().endsWith(".jar") && f.getName().contains("natives") && f.getName().contains(osSuffix)) {
                result.add(f);
            }
        }
    }

    private void downloadAssets(String vJson) throws Exception {
        Pattern idxP = Pattern.compile("\"assetIndex\"\\s*:\\s*\\{[^}]*\"id\"\\s*:\\s*\"([^\"]+)\"[^}]*\"url\"\\s*:\\s*\"([^\"]+)\"", Pattern.DOTALL);
        Matcher idxM = idxP.matcher(vJson);
        if (!idxM.find()) { log("No asset index"); return; }

        String idxId = idxM.group(1), idxUrl = idxM.group(2);
        File idxFile = new File(assetsDir, "indexes/" + idxId + ".json");
        String idxJson;
        if (!idxFile.exists()) {
            idxJson = downloadString(idxUrl);
            Files.write(idxFile.toPath(), idxJson.getBytes("UTF-8"));
        } else {
            idxJson = new String(Files.readAllBytes(idxFile.toPath()), "UTF-8");
        }

        Pattern hashP = Pattern.compile("\"hash\"\\s*:\\s*\"([a-f0-9]{40})\"");
        Matcher hashM = hashP.matcher(idxJson);
        Set<String> hashes = new LinkedHashSet<>();
        while (hashM.find()) hashes.add(hashM.group(1));

        int need = 0;
        for (String h : hashes) {
            File af = new File(assetsDir, "objects/" + h.substring(0,2) + "/" + h);
            if (!af.exists()) need++;
        }
        if (need == 0) { log("All assets present"); return; }

        log("Downloading " + need + " assets...");
        totalFiles.addAndGet(need);
        ExecutorService dl = Executors.newFixedThreadPool(DOWNLOAD_THREADS);
        for (String h : hashes) {
            File af = new File(assetsDir, "objects/" + h.substring(0,2) + "/" + h);
            if (af.exists()) continue;
            dl.submit(() -> {
                try {
                    af.getParentFile().mkdirs();
                    downloadFile(RESOURCES_URL + h.substring(0,2) + "/" + h, af);
                    downloadedFiles.incrementAndGet();
                    setProgress((int)(downloadedFiles.get() * 100.0 / Math.max(1, totalFiles.get())));
                } catch (Exception e) {}
            });
        }
        dl.shutdown();
        dl.awaitTermination(10, TimeUnit.MINUTES);
    }

    private void findJars(File dir, StringBuilder sb) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) findJars(f, sb);
            else if (f.getName().endsWith(".jar") && !f.getName().contains("natives-")) {
                if (sb.length() > 0) sb.append(File.pathSeparator);
                sb.append(f.getAbsolutePath());
            }
        }
    }

    private String genUUID(String name) {
        try {
            byte[] hash = MessageDigest.getInstance("MD5").digest(("OfflinePlayer:" + name).getBytes("UTF-8"));
            hash[6] = (byte)((hash[6] & 0x0f) | 0x30);
            hash[8] = (byte)((hash[8] & 0x3f) | 0x80);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 16; i++) {
                sb.append(String.format("%02x", hash[i]));
                if (i == 3 || i == 5 || i == 7 || i == 9) sb.append("-");
            }
            return sb.toString();
        } catch (Exception e) { return "00000000-0000-0000-0000-000000000000"; }
    }

    private String getJava() {
        String jh = System.getProperty("java.home");
        File j = new File(jh, "bin/" + (IS_WINDOWS ? "java.exe" : "java"));
        return j.exists() ? j.getAbsolutePath() : "java";
    }

    private String downloadString(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        c.setConnectTimeout(DOWNLOAD_TIMEOUT);
        c.setReadTimeout(DOWNLOAD_TIMEOUT);
        c.setRequestProperty("User-Agent", LAUNCHER_NAME);
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append("\n");
            return sb.toString();
        } finally { c.disconnect(); }
    }

    private void downloadFile(String url, File dest) throws Exception {
        HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        c.setConnectTimeout(DOWNLOAD_TIMEOUT);
        c.setReadTimeout(DOWNLOAD_TIMEOUT);
        c.setRequestProperty("User-Agent", LAUNCHER_NAME);
        if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
        dest.getParentFile().mkdirs();
        try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[8192]; int len;
            while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
        } finally { c.disconnect(); }
    }
}
