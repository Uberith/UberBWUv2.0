package com.example;

import com.example.config.Config;
import com.example.config.ConfigKeys;
import com.example.config.ConfigService;
import com.example.ui.ConfigPanel;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Sample script demonstrating profile-aware configuration persistence.
 * This is a minimal example and not tied to BWU runtime APIs.
 */
public class MyScript {
    private static final Logger LOG = Logger.getLogger(MyScript.class.getName());

    private final ConfigService configService = new ConfigService();
    private volatile Config config;
    private String activeProfile = "default";

    private JFrame frame;
    private ConfigPanel panel;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** Called on script enable/start. */
    public void enable(String profile) {
        activeProfile = configService.ensureProfileName(profile);
        config = configService.load(activeProfile);
        SwingUtilities.invokeLater(this::showUI);
    }

    private void showUI() {
        List<String> profiles = configService.listProfiles();
        frame = new JFrame("Config Demo");
        panel = new ConfigPanel(configService, config, profiles);
        wirePanelActions();
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.CENTER);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void wirePanelActions() {
        panel.getLoadButton().addActionListener(e -> switchProfile((String) JOptionPane.showInputDialog(
                frame, "Load profile:", "Load", JOptionPane.QUESTION_MESSAGE, null,
                configService.listProfiles().toArray(), activeProfile)));

        panel.getSaveButton().addActionListener(e -> configService.save(activeProfile, panel.getConfig()));

        panel.getSaveAsButton().addActionListener(e -> {
            String requested = panel.getRequestedProfileName();
            if (requested == null || requested.isBlank()) {
                JOptionPane.showMessageDialog(frame, "Profile name cannot be blank.");
                return;
            }
            switchProfile(requested.trim());
            configService.save(activeProfile, panel.getConfig());
        });

        panel.getResetButton().addActionListener(e -> {
            // Discard local file by overwriting with defaults
            Config defaults = Config.fromProperties(toPropsDefaults());
            config = defaults;
            panel.bindValues(defaults);
        });
    }

    private java.util.Properties toPropsDefaults() {
        java.util.Properties p = new java.util.Properties();
        for (var e : ConfigKeys.DEFAULTS.entrySet()) p.setProperty(e.getKey(), e.getValue());
        return p;
    }

    /** Main run loop; uses delays from config but avoids long blocking I/O. */
    public void run() {
        running.set(true);
        new Thread(() -> {
            while (running.get()) {
                try {
                    int min = Math.max(0, config.delayMinMs);
                    int max = Math.max(min, config.delayMaxMs);
                    int sleep = min + (int) (Math.random() * (max - min + 1));
                    LOG.info("Tick; sleeping " + sleep + "ms (profile=" + activeProfile + ")");
                    Thread.sleep(sleep);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception ignored) {
                }
            }
        }, "demo-loop").start();
    }

    /** Called on script disable/stop. */
    public void disable() {
        running.set(false);
        configService.save(activeProfile, config);
        if (frame != null) frame.dispose();
    }

    /** Switches profiles by saving current and loading the target; updates UI. */
    public void switchProfile(String name) {
        try { configService.save(activeProfile, panel.getConfig()); } catch (Exception ignored) {}
        activeProfile = configService.ensureProfileName(name);
        config = configService.load(activeProfile);
        if (panel != null) {
            panel.refreshProfiles(configService.listProfiles());
            panel.bindValues(config);
        }
    }

    public static void main(String[] args) {
        MyScript script = new MyScript();
        script.enable(args.length > 0 ? args[0] : "default");
        script.run();
        Runtime.getRuntime().addShutdownHook(new Thread(script::disable));
    }
}

