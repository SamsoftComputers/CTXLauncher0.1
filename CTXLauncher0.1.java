/*
 * ╔═══════════════════════════════════════════════════════════════════════════════╗
 * ║                        CTXLauncher v0.1                                       ║
 * ║              Minecraft Java Edition Launcher - Offline Mode                   ║
 * ║                    Team Flames / Samsoft / Cat OS                              ║
 * ╠═══════════════════════════════════════════════════════════════════════════════╣
 * ║  Full Library Support • Native Extraction • Multithreaded Asset Downloads     ║
 * ╚═══════════════════════════════════════════════════════════════════════════════╝
 *
 * Compile: javac CTXLauncher.java
 * Run: java CTXLauncher
 */

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
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
import javax.net.ssl.*;

public class CTXLauncher {
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // CONSTANTS
    // ═══════════════════════════════════════════════════════════════════════════════
    private static final String VERSION_MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest.json";
    private static final String RESOURCES_URL = "https://resources.download.minecraft.net/";
    private static final String LAUNCHER_NAME = "CTXLauncher";
    private static final String LAUNCHER_VERSION = "0.1";
    
    private static final int DOWNLOAD_TIMEOUT = 30000;
    private static final int DOWNLOAD_THREADS = 8;
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // DIRECTORIES
    // ═══════════════════════════════════════════════════════════════════════════════
    private File gameDirectory;
    private File versionsDir;
    private File librariesDir;
    private File assetsDir;
    private File nativesDir;
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // UI COMPONENTS
    // ═══════════════════════════════════════════════════════════════════════════════
    private JFrame frame;
    private JTextField usernameField;
    private JPasswordField tokenField;
    private JSlider ramSlider;
    private JComboBox<String> versionTypeCombo;
    private JComboBox<String> versionCombo;
    private MojangPlayButton playButton;
    private JButton refreshButton;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JTextArea logArea;
    private JCheckBox showSnapshotsCheck;
    private JCheckBox showOldVersionsCheck;
    private JTextField gameDirField;
    private JTextField jvmArgsField;
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // DATA
    // ═══════════════════════════════════════════════════════════════════════════════
    private Map<String, VersionInfo> versionCache = new HashMap<>();
    private List<VersionInfo> allVersions = new ArrayList<>();
    private ExecutorService executor = Executors.newFixedThreadPool(4);
    private volatile boolean isDownloading = false;
    private AtomicInteger downloadedFiles = new AtomicInteger(0);
    private AtomicInteger totalFiles = new AtomicInteger(0);
    private AtomicInteger failedFiles = new AtomicInteger(0);
    
    // Version info container
    private static class VersionInfo {
        String id;
        String type;
        String url;
        String releaseTime;
        
        VersionInfo(String id, String type, String url, String releaseTime) {
            this.id = id;
            this.type = type;
            this.url = url;
            this.releaseTime = releaseTime;
        }
        
        @Override
        public String toString() {
            return id;
        }
    }
    
    public CTXLauncher() {
        initGameDirectory();
        createUI();
        loadVersionManifest();
    }
    
    private void initGameDirectory() {
        String os = System.getProperty("os.name").toLowerCase();
        String userHome = System.getProperty("user.home");
        
        if (os.contains("win")) {
            gameDirectory = new File(System.getenv("APPDATA"), ".minecraft");
        } else if (os.contains("mac")) {
            gameDirectory = new File(userHome, "Library/Application Support/minecraft");
        } else {
            gameDirectory = new File(userHome, ".minecraft");
        }
        
        versionsDir = new File(gameDirectory, "versions");
        librariesDir = new File(gameDirectory, "libraries");
        assetsDir = new File(gameDirectory, "assets");
        nativesDir = new File(gameDirectory, "natives");
        
        // Create directories
        versionsDir.mkdirs();
        new File(assetsDir, "indexes").mkdirs();
        new File(assetsDir, "objects").mkdirs();
        librariesDir.mkdirs();
        nativesDir.mkdirs();
    }
    
    private void createUI() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Fall back to default
        }
        
        frame = new JFrame(LAUNCHER_NAME + " " + LAUNCHER_VERSION);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(700, 600);
        frame.setLocationRelativeTo(null);
        frame.setResizable(true);
        
        // Main panel with border layout
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        mainPanel.setBackground(new Color(40, 40, 45));
        
        // Header panel with logo/title
        JPanel headerPanel = createHeaderPanel();
        mainPanel.add(headerPanel, BorderLayout.NORTH);
        
        // Center panel with tabs
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setBackground(new Color(50, 50, 55));
        tabbedPane.setForeground(Color.WHITE);
        
        tabbedPane.addTab("Play", createPlayPanel());
        tabbedPane.addTab("Settings", createSettingsPanel());
        tabbedPane.addTab("Log", createLogPanel());
        
        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        
        // Bottom panel with progress
        JPanel bottomPanel = createBottomPanel();
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
        
        frame.setContentPane(mainPanel);
        frame.setVisible(true);
    }
    
    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        
        JLabel titleLabel = new JLabel(LAUNCHER_NAME + " " + LAUNCHER_VERSION);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
        titleLabel.setForeground(new Color(100, 180, 255));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        JLabel subtitleLabel = new JLabel("Minecraft Java Edition Launcher");
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitleLabel.setForeground(new Color(180, 180, 180));
        subtitleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        JPanel titlePanel = new JPanel(new GridLayout(2, 1));
        titlePanel.setOpaque(false);
        titlePanel.add(titleLabel);
        titlePanel.add(subtitleLabel);
        
        panel.add(titlePanel, BorderLayout.CENTER);
        panel.setBorder(new EmptyBorder(0, 0, 15, 0));
        
        return panel;
    }
    
    private JPanel createPlayPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(new Color(50, 50, 55));
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 8, 8, 8);
        
        // Username
        gbc.gridx = 0; gbc.gridy = 0;
        JLabel usernameLabel = createLabel("Username (Offline):");
        panel.add(usernameLabel, gbc);
        
        gbc.gridx = 1; gbc.gridwidth = 2;
        usernameField = createTextField("Player");
        usernameField.setToolTipText("Your in-game username for offline mode");
        panel.add(usernameField, gbc);
        
        // Version Type Filter
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1;
        JLabel versionTypeLabel = createLabel("Version Type:");
        panel.add(versionTypeLabel, gbc);
        
        gbc.gridx = 1; gbc.gridwidth = 2;
        versionTypeCombo = new JComboBox<>(new String[]{"Release", "Snapshot", "Old Beta", "Old Alpha", "All"});
        versionTypeCombo.setBackground(new Color(20, 60, 120));
        versionTypeCombo.setForeground(new Color(100, 180, 255));
        versionTypeCombo.addActionListener(e -> filterVersions());
        panel.add(versionTypeCombo, gbc);
        
        // Version Selector
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 1;
        JLabel versionLabel = createLabel("Version:");
        panel.add(versionLabel, gbc);
        
        gbc.gridx = 1;
        versionCombo = new JComboBox<>();
        versionCombo.setBackground(new Color(20, 60, 120));
        versionCombo.setForeground(new Color(100, 180, 255));
        versionCombo.setPreferredSize(new Dimension(200, 30));
        panel.add(versionCombo, gbc);
        
        gbc.gridx = 2; gbc.gridwidth = 1;
        refreshButton = new JButton("↻");
        refreshButton.setToolTipText("Refresh version list");
        refreshButton.setBackground(new Color(20, 60, 120));
        refreshButton.setForeground(new Color(100, 180, 255));
        refreshButton.setFocusPainted(false);
        refreshButton.addActionListener(e -> loadVersionManifest());
        panel.add(refreshButton, gbc);
        
        // RAM Allocation
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 1;
        JLabel ramLabel = createLabel("RAM Allocation:");
        panel.add(ramLabel, gbc);
        
        gbc.gridx = 1; gbc.gridwidth = 2;
        JPanel ramPanel = new JPanel(new BorderLayout(5, 0));
        ramPanel.setOpaque(false);
        
        ramSlider = new JSlider(1, 16, 4);
        ramSlider.setMajorTickSpacing(4);
        ramSlider.setMinorTickSpacing(1);
        ramSlider.setPaintTicks(true);
        ramSlider.setSnapToTicks(true);
        ramSlider.setBackground(new Color(50, 50, 55));
        ramSlider.setForeground(Color.WHITE);
        
        JLabel ramValueLabel = createLabel("4 GB");
        ramSlider.addChangeListener(e -> {
            int value = ramSlider.getValue();
            ramValueLabel.setText(value + " GB");
        });
        
        ramPanel.add(ramSlider, BorderLayout.CENTER);
        ramPanel.add(ramValueLabel, BorderLayout.EAST);
        panel.add(ramPanel, gbc);
        
        // Play Button - Mojang Style
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 3;
        gbc.insets = new Insets(25, 8, 8, 8);
        playButton = new MojangPlayButton();
        playButton.addActionListener(e -> launchGame());
        panel.add(playButton, gbc);
        
        return panel;
    }
    
    private JPanel createSettingsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(new Color(50, 50, 55));
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;
        
        // Game Directory
        gbc.gridx = 0; gbc.gridy = 0;
        JLabel gameDirLabel = createLabel("Game Directory:");
        panel.add(gameDirLabel, gbc);
        
        gbc.gridx = 1; gbc.gridwidth = 2;
        gameDirField = createTextField(gameDirectory.getAbsolutePath());
        gameDirField.setEditable(false);
        panel.add(gameDirField, gbc);
        
        gbc.gridx = 3; gbc.gridwidth = 1;
        JButton browseButton = new JButton("Browse");
        browseButton.setBackground(new Color(20, 60, 120));
        browseButton.setForeground(new Color(100, 180, 255));
        browseButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(gameDirectory);
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                gameDirectory = chooser.getSelectedFile();
                gameDirField.setText(gameDirectory.getAbsolutePath());
                initGameDirectory();
            }
        });
        panel.add(browseButton, gbc);
        
        // JVM Arguments
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1;
        JLabel jvmLabel = createLabel("JVM Arguments:");
        panel.add(jvmLabel, gbc);
        
        gbc.gridx = 1; gbc.gridwidth = 3;
        jvmArgsField = createTextField("-XX:+UseG1GC -XX:+UnlockExperimentalVMOptions -XX:G1NewSizePercent=20");
        jvmArgsField.setToolTipText("Additional JVM arguments for performance tuning");
        panel.add(jvmArgsField, gbc);
        
        // Version filters
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        showSnapshotsCheck = new JCheckBox("Show Snapshots");
        showSnapshotsCheck.setBackground(new Color(50, 50, 55));
        showSnapshotsCheck.setForeground(Color.WHITE);
        showSnapshotsCheck.addActionListener(e -> filterVersions());
        panel.add(showSnapshotsCheck, gbc);
        
        gbc.gridx = 2; gbc.gridwidth = 2;
        showOldVersionsCheck = new JCheckBox("Show Old Versions");
        showOldVersionsCheck.setBackground(new Color(50, 50, 55));
        showOldVersionsCheck.setForeground(Color.WHITE);
        showOldVersionsCheck.addActionListener(e -> filterVersions());
        panel.add(showOldVersionsCheck, gbc);
        
        // Open folders buttons
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 1;
        JButton openGameDirBtn = new JButton("Open Game Folder");
        openGameDirBtn.setBackground(new Color(20, 60, 120));
        openGameDirBtn.setForeground(new Color(100, 180, 255));
        openGameDirBtn.addActionListener(e -> {
            try {
                Desktop.getDesktop().open(gameDirectory);
            } catch (IOException ex) {
                log("Failed to open game directory: " + ex.getMessage());
            }
        });
        panel.add(openGameDirBtn, gbc);
        
        gbc.gridx = 1;
        JButton openVersionsBtn = new JButton("Open Versions");
        openVersionsBtn.setBackground(new Color(20, 60, 120));
        openVersionsBtn.setForeground(new Color(100, 180, 255));
        openVersionsBtn.addActionListener(e -> {
            try {
                Desktop.getDesktop().open(versionsDir);
            } catch (IOException ex) {
                log("Failed to open versions directory: " + ex.getMessage());
            }
        });
        panel.add(openVersionsBtn, gbc);
        
        // Spacer
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 4; gbc.weighty = 1.0;
        panel.add(Box.createVerticalGlue(), gbc);
        
        return panel;
    }
    
    private JPanel createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(50, 50, 55));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setBackground(new Color(30, 30, 35));
        logArea.setForeground(new Color(200, 200, 200));
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setCaretColor(Color.WHITE);
        
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(70, 70, 75)));
        panel.add(scrollPane, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setOpaque(false);
        
        JButton clearButton = new JButton("Clear Log");
        clearButton.setBackground(new Color(20, 60, 120));
        clearButton.setForeground(new Color(100, 180, 255));
        clearButton.addActionListener(e -> logArea.setText(""));
        buttonPanel.add(clearButton);
        
        JButton copyButton = new JButton("Copy Log");
        copyButton.setBackground(new Color(20, 60, 120));
        copyButton.setForeground(new Color(100, 180, 255));
        copyButton.addActionListener(e -> {
            logArea.selectAll();
            logArea.copy();
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
        buttonPanel.add(copyButton);
        
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 5));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(10, 0, 0, 0));
        
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setString("Ready");
        progressBar.setBackground(new Color(60, 60, 65));
        progressBar.setForeground(new Color(100, 180, 255));
        progressBar.setBorder(BorderFactory.createLineBorder(new Color(70, 70, 75)));
        
        statusLabel = new JLabel("Welcome to " + LAUNCHER_NAME);
        statusLabel.setForeground(new Color(180, 180, 180));
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        
        panel.add(progressBar, BorderLayout.CENTER);
        panel.add(statusLabel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private JLabel createLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(Color.WHITE);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        return label;
    }
    
    private JTextField createTextField(String defaultText) {
        JTextField field = new JTextField(defaultText, 20);
        field.setBackground(new Color(60, 60, 65));
        field.setForeground(Color.WHITE);
        field.setCaretColor(Color.WHITE);
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(80, 80, 85)),
            BorderFactory.createEmptyBorder(5, 8, 5, 8)
        ));
        return field;
    }
    
    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = String.format("[%tT] ", new Date());
            logArea.append(timestamp + message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
        System.out.println(message);
    }
    
    private void setStatus(String status) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(status);
        });
        log(status);
    }
    
    private void setProgress(int value, String text) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setValue(value);
            progressBar.setString(text);
        });
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // VERSION MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════════════
    private void loadVersionManifest() {
        setStatus("Fetching version manifest...");
        setProgress(0, "Loading versions...");
        playButton.setEnabled(false);
        
        executor.submit(() -> {
            try {
                setupSSL();
                
                URL url = new URL(VERSION_MANIFEST_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", LAUNCHER_NAME + "/" + LAUNCHER_VERSION);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder json = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    json.append(line);
                }
                reader.close();
                
                parseVersionManifest(json.toString());
                setProgress(100, "Ready");
                setStatus("Loaded " + allVersions.size() + " versions");
                
                SwingUtilities.invokeLater(() -> {
                    filterVersions();
                    playButton.setEnabled(true);
                });
                
            } catch (Exception e) {
                setStatus("Failed to fetch versions: " + e.getMessage());
                log("Error: " + e.toString());
                SwingUtilities.invokeLater(() -> playButton.setEnabled(true));
            }
        });
    }
    
    private void setupSSL() throws Exception {
        TrustManager[] trustAllCerts = new TrustManager[]{
            new X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
                public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
            }
        };
        
        SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
    }
    
    private void parseVersionManifest(String json) {
        allVersions.clear();
        versionCache.clear();
        
        // Simple JSON parsing without external libraries
        Pattern versionPattern = Pattern.compile(
            "\\{\\s*\"id\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"type\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"url\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"time\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"releaseTime\"\\s*:\\s*\"([^\"]+)\"\\s*\\}"
        );
        
        Matcher matcher = versionPattern.matcher(json);
        while (matcher.find()) {
            String id = matcher.group(1);
            String type = matcher.group(2);
            String versionUrl = matcher.group(3);
            String releaseTime = matcher.group(5);
            
            VersionInfo info = new VersionInfo(id, type, versionUrl, releaseTime);
            allVersions.add(info);
            versionCache.put(id, info);
        }
        
        log("Parsed " + allVersions.size() + " versions from manifest");
    }
    
    private void filterVersions() {
        versionCombo.removeAllItems();
        
        String selectedType = (String) versionTypeCombo.getSelectedItem();
        
        for (VersionInfo version : allVersions) {
            boolean include = false;
            
            switch (selectedType) {
                case "Release":
                    include = version.type.equals("release");
                    break;
                case "Snapshot":
                    include = version.type.equals("snapshot");
                    break;
                case "Old Beta":
                    include = version.type.equals("old_beta");
                    break;
                case "Old Alpha":
                    include = version.type.equals("old_alpha");
                    break;
                case "All":
                    include = true;
                    break;
            }
            
            if (include) {
                versionCombo.addItem(version.id);
            }
        }
        
        if (versionCombo.getItemCount() > 0) {
            versionCombo.setSelectedIndex(0);
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // LAUNCH LOGIC
    // ═══════════════════════════════════════════════════════════════════════════════
    private void launchGame() {
        if (isDownloading) return;
        
        String username = usernameField.getText().trim();
        if (username.isEmpty() || !username.matches("^[a-zA-Z0-9_]+$")) {
            JOptionPane.showMessageDialog(frame, "Please enter a valid username (letters, numbers, underscore only)!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (username.length() > 16) {
            username = username.substring(0, 16);
        }
        
        String selectedVersion = (String) versionCombo.getSelectedItem();
        if (selectedVersion == null) {
            JOptionPane.showMessageDialog(frame, "Please select a version!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        VersionInfo versionInfo = versionCache.get(selectedVersion);
        if (versionInfo == null) {
            JOptionPane.showMessageDialog(frame, "Version info not found!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        final String finalUsername = username;
        final int ram = ramSlider.getValue();
        
        isDownloading = true;
        playButton.setEnabled(false);
        playButton.setText("⏳ PREPARING...");
        setStatus("Preparing " + selectedVersion + "...");
        setProgress(0, "Starting...");
        
        executor.submit(() -> {
            try {
                setupSSL();
                
                String versionDir = versionsDir.getAbsolutePath() + "/" + selectedVersion;
                new File(versionDir).mkdirs();
                String nativesPath = nativesDir.getAbsolutePath() + "/" + selectedVersion;
                new File(nativesPath).mkdirs();
                
                // Step 1: Download version JSON
                setProgress(5, "Downloading version info...");
                String jsonPath = versionDir + "/" + selectedVersion + ".json";
                if (!new File(jsonPath).exists()) {
                    downloadFile(versionInfo.url, jsonPath);
                }
                String versionJsonContent = new String(Files.readAllBytes(Paths.get(jsonPath)));
                log("Downloaded version JSON");
                
                // Step 2: Download client JAR
                setProgress(10, "Downloading Minecraft client...");
                String jarUrl = extractNestedJsonValue(versionJsonContent, "downloads", "client", "url");
                String jarPath = versionDir + "/" + selectedVersion + ".jar";
                if (jarUrl != null && !new File(jarPath).exists()) {
                    downloadFile(jarUrl, jarPath);
                    log("Downloaded client JAR");
                }
                
                // Step 3: Download libraries
                setProgress(15, "Downloading libraries...");
                downloadAllLibraries(versionJsonContent);
                log("Libraries downloaded");
                
                // Step 4: Extract natives
                setProgress(35, "Extracting natives...");
                extractNatives(versionJsonContent, nativesPath);
                log("Natives extracted");
                
                // Step 5: Download assets
                setProgress(40, "Downloading assets...");
                downloadAllAssets(versionJsonContent);
                log("Assets downloaded");
                
                // Launch!
                setProgress(100, "Launching...");
                launchMinecraft(selectedVersion, finalUsername, ram, nativesPath, versionJsonContent);
                
            } catch (Exception e) {
                e.printStackTrace();
                setStatus("Launch failed: " + e.getMessage());
                log("Error: " + e.toString());
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(frame, "Failed to launch:\n" + e.getMessage(), "Launch Error", JOptionPane.ERROR_MESSAGE);
                });
            } finally {
                isDownloading = false;
                SwingUtilities.invokeLater(() -> {
                    playButton.setEnabled(true);
                    playButton.setText("▶  PLAY");
                });
            }
        });
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // LIBRARY DOWNLOAD
    // ═══════════════════════════════════════════════════════════════════════════════
    private void downloadAllLibraries(String jsonContent) throws Exception {
        int libStart = jsonContent.indexOf("\"libraries\"");
        if (libStart == -1) return;
        
        int arrayStart = jsonContent.indexOf("[", libStart);
        if (arrayStart == -1) return;
        int arrayEnd = findMatchingBracket(jsonContent, arrayStart);
        String librariesArray = jsonContent.substring(arrayStart, arrayEnd + 1);
        
        List<String[]> toDownload = new ArrayList<>();
        String osName = getOsName();
        
        int pos = 0;
        while ((pos = librariesArray.indexOf("{", pos)) != -1) {
            int objEnd = findMatchingBrace(librariesArray, pos);
            String libObj = librariesArray.substring(pos, objEnd + 1);
            
            if (!checkLibraryRules(libObj, osName)) {
                pos = objEnd + 1;
                continue;
            }
            
            int downloadsPos = libObj.indexOf("\"downloads\"");
            if (downloadsPos != -1) {
                int artifactPos = libObj.indexOf("\"artifact\"", downloadsPos);
                if (artifactPos != -1) {
                    String path = extractJsonValue(libObj.substring(artifactPos), "path");
                    String url = extractJsonValue(libObj.substring(artifactPos), "url");
                    if (path != null && url != null) {
                        toDownload.add(new String[]{path, url});
                    }
                }
                
                int classifiersPos = libObj.indexOf("\"classifiers\"", downloadsPos);
                if (classifiersPos != -1) {
                    String nativeKey = getNativeKey(libObj, osName);
                    if (nativeKey != null) {
                        int nativePos = libObj.indexOf("\"" + nativeKey + "\"", classifiersPos);
                        if (nativePos != -1) {
                            String path = extractJsonValue(libObj.substring(nativePos), "path");
                            String url = extractJsonValue(libObj.substring(nativePos), "url");
                            if (path != null && url != null) {
                                toDownload.add(new String[]{path, url});
                            }
                        }
                    }
                }
            }
            
            pos = objEnd + 1;
        }
        
        int total = toDownload.size();
        int current = 0;
        
        for (String[] item : toDownload) {
            String path = item[0];
            String url = item[1];
            String fullPath = librariesDir.getAbsolutePath() + "/" + path;
            
            if (!new File(fullPath).exists()) {
                try {
                    new File(fullPath).getParentFile().mkdirs();
                    downloadFile(url, fullPath);
                } catch (Exception e) {
                    log("Failed to download library: " + path);
                }
            }
            
            current++;
            int progress = 15 + (20 * current / Math.max(total, 1));
            final int c = current;
            final int t = total;
            SwingUtilities.invokeLater(() -> {
                setProgress(progress, "Libraries: " + c + "/" + t);
            });
        }
    }
    
    private boolean checkLibraryRules(String libObj, String osName) {
        int rulesPos = libObj.indexOf("\"rules\"");
        if (rulesPos == -1) return true;
        
        boolean allowed = false;
        int arrayStart = libObj.indexOf("[", rulesPos);
        if (arrayStart == -1) return true;
        int arrayEnd = findMatchingBracket(libObj, arrayStart);
        String rulesArray = libObj.substring(arrayStart, arrayEnd + 1);
        
        int pos = 0;
        while ((pos = rulesArray.indexOf("{", pos)) != -1) {
            int objEnd = findMatchingBrace(rulesArray, pos);
            String ruleObj = rulesArray.substring(pos, objEnd + 1);
            
            String action = extractJsonValue(ruleObj, "action");
            boolean isAllow = "allow".equals(action);
            
            int osPos = ruleObj.indexOf("\"os\"");
            if (osPos == -1) {
                allowed = isAllow;
            } else {
                String ruleName = extractJsonValue(ruleObj.substring(osPos), "name");
                if (ruleName != null && ruleName.equals(osName)) {
                    allowed = isAllow;
                }
            }
            
            pos = objEnd + 1;
        }
        
        return allowed;
    }
    
    private String getNativeKey(String libObj, String osName) {
        int nativesPos = libObj.indexOf("\"natives\"");
        if (nativesPos == -1) return "natives-" + osName;
        
        int braceStart = libObj.indexOf("{", nativesPos);
        if (braceStart == -1) return "natives-" + osName;
        int braceEnd = findMatchingBrace(libObj, braceStart);
        String nativesObj = libObj.substring(braceStart, braceEnd + 1);
        
        String key = extractJsonValue(nativesObj, osName);
        if (key != null) {
            key = key.replace("${arch}", System.getProperty("os.arch").contains("64") ? "64" : "32");
            return key;
        }
        
        return "natives-" + osName;
    }
    
    private void extractNatives(String jsonContent, String nativesDir) throws Exception {
        int libStart = jsonContent.indexOf("\"libraries\"");
        if (libStart == -1) return;
        
        int arrayStart = jsonContent.indexOf("[", libStart);
        if (arrayStart == -1) return;
        int arrayEnd = findMatchingBracket(jsonContent, arrayStart);
        String librariesArray = jsonContent.substring(arrayStart, arrayEnd + 1);
        
        String osName = getOsName();
        
        int pos = 0;
        while ((pos = librariesArray.indexOf("{", pos)) != -1) {
            int objEnd = findMatchingBrace(librariesArray, pos);
            String libObj = librariesArray.substring(pos, objEnd + 1);
            
            if (!checkLibraryRules(libObj, osName)) {
                pos = objEnd + 1;
                continue;
            }
            
            int classifiersPos = libObj.indexOf("\"classifiers\"");
            if (classifiersPos != -1) {
                String nativeKey = getNativeKey(libObj, osName);
                int nativePos = libObj.indexOf("\"" + nativeKey + "\"", classifiersPos);
                if (nativePos != -1) {
                    String path = extractJsonValue(libObj.substring(nativePos), "path");
                    if (path != null) {
                        String jarPath = librariesDir.getAbsolutePath() + "/" + path;
                        if (new File(jarPath).exists()) {
                            extractJar(jarPath, nativesDir);
                        }
                    }
                }
            }
            
            pos = objEnd + 1;
        }
    }
    
    private void extractJar(String jarPath, String destDir) {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(jarPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                
                if (name.startsWith("META-INF/") || entry.isDirectory()) {
                    continue;
                }
                
                if (name.endsWith(".dll") || name.endsWith(".so") || name.endsWith(".dylib") || name.endsWith(".jnilib")) {
                    File outFile = new File(destDir, new File(name).getName());
                    
                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log("Failed to extract natives from: " + jarPath);
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // ASSET DOWNLOAD - MULTITHREADED
    // ═══════════════════════════════════════════════════════════════════════════════
    private void downloadAllAssets(String jsonContent) throws Exception {
        String assetIndexId = extractNestedJsonValue(jsonContent, "assetIndex", "id");
        String assetIndexUrl = extractNestedJsonValue(jsonContent, "assetIndex", "url");
        
        if (assetIndexId == null || assetIndexUrl == null) {
            log("No asset index found");
            return;
        }
        
        // Download asset index
        String indexPath = assetsDir.getAbsolutePath() + "/indexes/" + assetIndexId + ".json";
        if (!new File(indexPath).exists()) {
            downloadFile(assetIndexUrl, indexPath);
        }
        
        String indexContent = new String(Files.readAllBytes(Paths.get(indexPath)));
        
        // Parse all asset hashes
        List<String> assetHashes = new ArrayList<>();
        
        int objectsPos = indexContent.indexOf("\"objects\"");
        if (objectsPos == -1) return;
        
        int braceStart = indexContent.indexOf("{", objectsPos);
        if (braceStart == -1) return;
        int braceEnd = findMatchingBrace(indexContent, braceStart);
        String objectsSection = indexContent.substring(braceStart, braceEnd + 1);
        
        int pos = 0;
        while ((pos = objectsSection.indexOf("\"hash\"", pos)) != -1) {
            int colonPos = objectsSection.indexOf(":", pos);
            int quoteStart = objectsSection.indexOf("\"", colonPos);
            int quoteEnd = objectsSection.indexOf("\"", quoteStart + 1);
            
            if (quoteEnd > quoteStart) {
                String hash = objectsSection.substring(quoteStart + 1, quoteEnd);
                if (hash.length() >= 2) {
                    assetHashes.add(hash);
                }
            }
            pos = quoteEnd + 1;
        }
        
        // Filter to missing assets only
        List<String> missingAssets = new ArrayList<>();
        for (String hash : assetHashes) {
            String prefix = hash.substring(0, 2);
            String assetPath = assetsDir.getAbsolutePath() + "/objects/" + prefix + "/" + hash;
            if (!new File(assetPath).exists()) {
                missingAssets.add(hash);
            }
        }
        
        if (missingAssets.isEmpty()) {
            log("All assets already downloaded!");
            return;
        }
        
        totalFiles.set(missingAssets.size());
        downloadedFiles.set(0);
        failedFiles.set(0);
        
        log("Downloading " + missingAssets.size() + " assets...");
        
        // Parallel download
        ExecutorService assetExecutor = Executors.newFixedThreadPool(DOWNLOAD_THREADS);
        List<Future<?>> futures = new ArrayList<>();
        
        for (String hash : missingAssets) {
            futures.add(assetExecutor.submit(() -> {
                try {
                    String prefix = hash.substring(0, 2);
                    String assetPath = assetsDir.getAbsolutePath() + "/objects/" + prefix + "/" + hash;
                    String assetUrl = RESOURCES_URL + prefix + "/" + hash;
                    
                    new File(assetPath).getParentFile().mkdirs();
                    downloadFile(assetUrl, assetPath);
                    
                    int done = downloadedFiles.incrementAndGet();
                    int total = totalFiles.get();
                    
                    if (done % 50 == 0 || done == total) {
                        int progress = 40 + (55 * done / total);
                        SwingUtilities.invokeLater(() -> {
                            setProgress(progress, "Assets: " + done + "/" + total);
                        });
                    }
                } catch (Exception e) {
                    failedFiles.incrementAndGet();
                }
            }));
        }
        
        // Wait for completion
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception e) {}
        }
        
        assetExecutor.shutdown();
        assetExecutor.awaitTermination(60, TimeUnit.MINUTES);
        
        int failed = failedFiles.get();
        if (failed > 0) {
            log("Warning: " + failed + " assets failed to download");
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // GAME LAUNCH
    // ═══════════════════════════════════════════════════════════════════════════════
    private void launchMinecraft(String version, String username, int ram, String nativesPath, String jsonContent) {
        try {
            String versionDir = versionsDir.getAbsolutePath() + "/" + version;
            String jarPath = versionDir + "/" + version + ".jar";
            
            String mainClass = extractJsonValue(jsonContent, "mainClass");
            if (mainClass == null) mainClass = "net.minecraft.client.main.Main";
            
            // Build classpath
            List<String> classpathList = new ArrayList<>();
            classpathList.add(jarPath);
            addLibrariesToClasspath(jsonContent, classpathList);
            
            String sep = System.getProperty("os.name").toLowerCase().contains("win") ? ";" : ":";
            String classpath = String.join(sep, classpathList);
            
            // Build command
            List<String> cmd = new ArrayList<>();
            cmd.add(getJavaPath());
            cmd.add("-Xmx" + ram + "G");
            cmd.add("-Xms512M");
            cmd.add("-Djava.library.path=" + nativesPath);
            
            // Additional JVM args
            String extraArgs = jvmArgsField.getText().trim();
            if (!extraArgs.isEmpty()) {
                for (String arg : extraArgs.split("\\s+")) {
                    if (!arg.isEmpty()) {
                        cmd.add(arg);
                    }
                }
            }
            
            // Mac specific
            if (System.getProperty("os.name").toLowerCase().contains("mac")) {
                cmd.add("-XstartOnFirstThread");
            }
            
            // Offline mode - block auth hosts
            cmd.add("-Dminecraft.api.auth.host=http://0.0.0.0");
            cmd.add("-Dminecraft.api.account.host=http://0.0.0.0");
            cmd.add("-Dminecraft.api.session.host=http://0.0.0.0");
            cmd.add("-Dminecraft.api.services.host=http://0.0.0.0");
            
            cmd.add("-cp");
            cmd.add(classpath);
            cmd.add(mainClass);
            
            // Game arguments
            String uuid = generateOfflineUUID(username);
            String assetIndex = extractNestedJsonValue(jsonContent, "assetIndex", "id");
            if (assetIndex == null) assetIndex = "legacy";
            
            cmd.add("--username"); cmd.add(username);
            cmd.add("--version"); cmd.add(version);
            cmd.add("--gameDir"); cmd.add(gameDirectory.getAbsolutePath());
            cmd.add("--assetsDir"); cmd.add(assetsDir.getAbsolutePath());
            cmd.add("--assetIndex"); cmd.add(assetIndex);
            cmd.add("--uuid"); cmd.add(uuid);
            cmd.add("--accessToken"); cmd.add("0");
            cmd.add("--userType"); cmd.add("legacy");
            cmd.add("--versionType"); cmd.add(LAUNCHER_NAME);
            
            log("═══════════════════════════════════════════════════════════════════════");
            log("🚀 LAUNCHING MINECRAFT " + version);
            log("═══════════════════════════════════════════════════════════════════════");
            log("Main Class: " + mainClass);
            log("Username: " + username);
            log("Memory: " + ram + "GB");
            log("Natives: " + nativesPath);
            log("Libraries: " + classpathList.size());
            log("Asset Index: " + assetIndex);
            log("═══════════════════════════════════════════════════════════════════════");
            
            setStatus("Launching Minecraft " + version + "...");
            
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(gameDirectory);
            pb.inheritIO();
            Process process = pb.start();
            
            // Monitor process in background
            new Thread(() -> {
                try {
                    int exitCode = process.waitFor();
                    SwingUtilities.invokeLater(() -> {
                        if (exitCode == 0) {
                            setStatus("Game closed normally");
                        } else {
                            setStatus("Game exited with code: " + exitCode);
                        }
                        setProgress(100, "Ready");
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
            
            setStatus("Minecraft " + version + " is running!");
            
        } catch (Exception e) {
            e.printStackTrace();
            log("Launch error: " + e.getMessage());
            throw new RuntimeException("Failed to launch Minecraft: " + e.getMessage());
        }
    }
    
    private String getJavaPath() {
        String javaHome = System.getProperty("java.home");
        String os = System.getProperty("os.name").toLowerCase();
        
        if (os.contains("win")) {
            return javaHome + "\\bin\\java.exe";
        } else {
            return javaHome + "/bin/java";
        }
    }
    
    private void addLibrariesToClasspath(String jsonContent, List<String> classpathList) {
        int libStart = jsonContent.indexOf("\"libraries\"");
        if (libStart == -1) return;
        
        int arrayStart = jsonContent.indexOf("[", libStart);
        if (arrayStart == -1) return;
        int arrayEnd = findMatchingBracket(jsonContent, arrayStart);
        String librariesArray = jsonContent.substring(arrayStart, arrayEnd + 1);
        
        String osName = getOsName();
        
        int pos = 0;
        while ((pos = librariesArray.indexOf("{", pos)) != -1) {
            int objEnd = findMatchingBrace(librariesArray, pos);
            String libObj = librariesArray.substring(pos, objEnd + 1);
            
            if (!checkLibraryRules(libObj, osName)) {
                pos = objEnd + 1;
                continue;
            }
            
            int downloadsPos = libObj.indexOf("\"downloads\"");
            if (downloadsPos != -1) {
                int artifactPos = libObj.indexOf("\"artifact\"", downloadsPos);
                if (artifactPos != -1) {
                    String path = extractJsonValue(libObj.substring(artifactPos), "path");
                    if (path != null) {
                        String fullPath = librariesDir.getAbsolutePath() + "/" + path;
                        if (new File(fullPath).exists() && !classpathList.contains(fullPath)) {
                            classpathList.add(fullPath);
                        }
                    }
                }
            }
            
            pos = objEnd + 1;
        }
    }
    
    private String generateOfflineUUID(String username) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(("OfflinePlayer:" + username).getBytes());
            
            digest[6] = (byte) ((digest[6] & 0x0f) | 0x30);
            digest[8] = (byte) ((digest[8] & 0x3f) | 0x80);
            
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            String hex = sb.toString();
            
            return hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" +
                   hex.substring(12, 16) + "-" + hex.substring(16, 20) + "-" +
                   hex.substring(20, 32);
                   
        } catch (Exception e) {
            return "00000000-0000-0000-0000-000000000000";
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════════════════════
    private String getOsName() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) return "windows";
        if (os.contains("mac")) return "osx";
        return "linux";
    }
    
    private void downloadFile(String urlStr, String destPath) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(DOWNLOAD_TIMEOUT);
        conn.setReadTimeout(DOWNLOAD_TIMEOUT);
        conn.setRequestProperty("User-Agent", LAUNCHER_NAME + "/" + LAUNCHER_VERSION);
        
        new File(destPath).getParentFile().mkdirs();
        
        try (InputStream in = conn.getInputStream();
             FileOutputStream out = new FileOutputStream(destPath)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
    }
    
    private String extractJsonValue(String json, String key) {
        int keyPos = json.indexOf("\"" + key + "\"");
        if (keyPos == -1) return null;
        
        int colonPos = json.indexOf(":", keyPos);
        if (colonPos == -1) return null;
        
        int valueStart = colonPos + 1;
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }
        
        if (valueStart >= json.length()) return null;
        
        if (json.charAt(valueStart) == '"') {
            int valueEnd = json.indexOf("\"", valueStart + 1);
            if (valueEnd == -1) return null;
            return json.substring(valueStart + 1, valueEnd);
        }
        
        return null;
    }
    
    private String extractNestedJsonValue(String json, String... keys) {
        String current = json;
        for (int i = 0; i < keys.length - 1; i++) {
            int keyPos = current.indexOf("\"" + keys[i] + "\"");
            if (keyPos == -1) return null;
            int bracePos = current.indexOf("{", keyPos);
            if (bracePos == -1) return null;
            int braceEnd = findMatchingBrace(current, bracePos);
            current = current.substring(bracePos, braceEnd + 1);
        }
        return extractJsonValue(current, keys[keys.length - 1]);
    }
    
    private int findMatchingBracket(String s, int start) {
        int count = 0;
        for (int i = start; i < s.length(); i++) {
            if (s.charAt(i) == '[') count++;
            else if (s.charAt(i) == ']') count--;
            if (count == 0) return i;
        }
        return s.length() - 1;
    }
    
    private int findMatchingBrace(String s, int start) {
        int count = 0;
        for (int i = start; i < s.length(); i++) {
            if (s.charAt(i) == '{') count++;
            else if (s.charAt(i) == '}') count--;
            if (count == 0) return i;
        }
        return s.length() - 1;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // MOJANG PLAY BUTTON
    // ═══════════════════════════════════════════════════════════════════════════════
    static class MojangPlayButton extends JButton {
        private static final Color GREEN_PRIMARY = new Color(0x3C8527);
        private static final Color GREEN_HOVER = new Color(0x4A9E30);
        private static final Color GREEN_PRESSED = new Color(0x2E6B1E);
        private static final Color GREEN_DISABLED = new Color(0x4A4A4A);
        
        private boolean isHovered = false;
        private boolean isPressed = false;
        private String displayText = "▶  PLAY";
        
        public MojangPlayButton() {
            super("▶  PLAY");
            setFont(new Font("Segoe UI", Font.BOLD, 18));
            setForeground(Color.WHITE);
            setPreferredSize(new Dimension(220, 55));
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    if (isEnabled()) { isHovered = true; repaint(); }
                }
                @Override
                public void mouseExited(MouseEvent e) {
                    isHovered = false; repaint();
                }
                @Override
                public void mousePressed(MouseEvent e) {
                    if (isEnabled()) { isPressed = true; repaint(); }
                }
                @Override
                public void mouseReleased(MouseEvent e) {
                    isPressed = false; repaint();
                }
            });
        }
        
        @Override
        public void setText(String text) {
            this.displayText = text;
            repaint();
        }
        
        @Override
        public String getText() {
            return displayText;
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            
            int w = getWidth();
            int h = getHeight();
            
            // Determine background color
            Color bgColor;
            if (!isEnabled()) {
                bgColor = GREEN_DISABLED;
            } else if (isPressed) {
                bgColor = GREEN_PRESSED;
            } else if (isHovered) {
                bgColor = GREEN_HOVER;
            } else {
                bgColor = GREEN_PRIMARY;
            }
            
            // Draw shadow
            g2.setColor(new Color(0, 0, 0, 80));
            g2.fillRoundRect(3, 5, w - 6, h - 6, 8, 8);
            
            // Draw main button body
            g2.setColor(bgColor);
            g2.fillRoundRect(0, 0, w - 3, h - 5, 8, 8);
            
            // Draw highlight gradient on top half
            GradientPaint highlight = new GradientPaint(
                0, 0, new Color(255, 255, 255, 40),
                0, h / 2, new Color(255, 255, 255, 0)
            );
            g2.setPaint(highlight);
            g2.fillRoundRect(1, 1, w - 5, h / 2 - 3, 7, 7);
            
            // Draw border
            g2.setColor(new Color(0, 0, 0, 60));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(0, 0, w - 4, h - 6, 8, 8);
            
            // Draw inner highlight
            g2.setColor(new Color(255, 255, 255, 25));
            g2.drawRoundRect(1, 1, w - 6, h - 8, 7, 7);
            
            // Draw text with shadow
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int textX = (w - fm.stringWidth(displayText)) / 2;
            int textY = (h - 5 + fm.getAscent() - fm.getDescent()) / 2;
            
            // Text shadow
            g2.setColor(new Color(0, 0, 0, 100));
            g2.drawString(displayText, textX + 1, textY + 1);
            
            // Text
            g2.setColor(isEnabled() ? Color.WHITE : new Color(180, 180, 180));
            g2.drawString(displayText, textX, textY);
            
            g2.dispose();
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // MAIN
    // ═══════════════════════════════════════════════════════════════════════════════
    public static void main(String[] args) {
        System.out.println("╔═══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                     CTXLauncher v0.1                                  ║");
        System.out.println("║                Team Flames / Samsoft / Cat OS                         ║");
        System.out.println("║     Full Library & Native Support • Asset Downloads • Offline Mode    ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════════════╝");
        
        SwingUtilities.invokeLater(() -> {
            new CTXLauncher();
        });
    }
}
