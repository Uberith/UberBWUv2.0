package com.example.config;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides loading/saving of {@link Config} and manages profiles.
 * All file I/O is exception-safe and logs warnings instead of throwing.
 */
public class ConfigService {

    private static final Logger LOG = Logger.getLogger(ConfigService.class.getName());
    private static final String SCRIPT_ID = "com.uberith.uberchop";

    /**
     * Returns the script's workspace directory. If the BWU platform's workspace integration
     * is available, it will be used; otherwise falls back to ~/.bwu/<script-id>.
     */
    public Path getWorkspaceDir() {
        // Try BWU WorkspaceManager via reflection if present
        try {
            Class<?> wmClass = Class.forName("net.botwithus.ui.WorkspaceManager");
            Object manager = wmClass.getMethod("getManager").invoke(null);
            Object current = manager.getClass().getMethod("getCurrent").invoke(manager);
            // Preferred: derive workspace dir from current workspace uuid
            try {
                String uuid = String.valueOf(current.getClass().getField("uuid").get(current));
                if (uuid != null && !uuid.isBlank()) {
                    Path dir = Paths.get(System.getProperty("user.home"), ".botwithus", "workspaces", uuid);
                    Files.createDirectories(dir);
                    return dir;
                }
            } catch (NoSuchFieldException nsfe) {
                // Try getter
                try {
                    Object uuidObj = current.getClass().getMethod("getUuid").invoke(current);
                    if (uuidObj != null) {
                        Path dir = Paths.get(System.getProperty("user.home"), ".botwithus", "workspaces", String.valueOf(uuidObj));
                        Files.createDirectories(dir);
                        return dir;
                    }
                } catch (Throwable ignored) {
                }
            }
            // Fallback: try to use a directory getter if exposed
            Path dir = tryGetWorkspacePath(current);
            if (dir != null) return dir;
        } catch (Throwable t) {
            LOG.log(Level.FINE, "WorkspaceManager not available, using fallback", t);
        }

        Path fallback = Paths.get(System.getProperty("user.home"), ".botwithus", SCRIPT_ID);
        try {
            Files.createDirectories(fallback);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to ensure fallback workspace directory: " + fallback, e);
        }
        return fallback;
    }

    private Path tryGetWorkspacePath(Object currentWorkspace) {
        try {
            Object dirObj;
            // try getDirectory(): File/Path/String
            try {
                dirObj = currentWorkspace.getClass().getMethod("getDirectory").invoke(currentWorkspace);
            } catch (NoSuchMethodException nsme) {
                try {
                    dirObj = currentWorkspace.getClass().getMethod("getDir").invoke(currentWorkspace);
                } catch (NoSuchMethodException nsme2) {
                    dirObj = null;
                }
            }
            if (dirObj == null) return null;
            if (dirObj instanceof Path) return (Path) dirObj;
            if (dirObj instanceof java.io.File) return ((java.io.File) dirObj).toPath();
            if (dirObj instanceof String) return Paths.get((String) dirObj);
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * Resolves the configuration file path for a profile name under the workspace.
     */
    public Path configFile(String profile) {
        String prof = sanitizeProfile(ensureProfileName(profile));
        Path base = getWorkspaceDir().resolve("config").resolve("profiles");
        try {
            Files.createDirectories(base);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to create config directories: " + base, e);
        }
        return base.resolve(prof + ".properties");
    }

    /**
     * Loads configuration for the given profile. On error, logs and falls back to defaults.
     */
    public Config load(String profile) {
        Path file = configFile(profile);

        Properties props = new Properties();
        // Load defaults first
        for (var e : ConfigKeys.DEFAULTS.entrySet()) {
            props.setProperty(e.getKey(), e.getValue());
        }

        if (Files.exists(file)) {
            try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
                Properties loaded = new Properties();
                loaded.load(in);
                // overlay loaded on defaults
                for (String name : loaded.stringPropertyNames()) {
                    props.setProperty(name, loaded.getProperty(name));
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to read config: " + file + "; using defaults", e);
            }
        }

        // Version migration stub
        int currentVersion = safeInt(props.getProperty(ConfigKeys.CONFIG_VERSION_KEY), ConfigKeys.CONFIG_VERSION);
        if (currentVersion < ConfigKeys.CONFIG_VERSION) {
            try {
                migrate(props, currentVersion, ConfigKeys.CONFIG_VERSION);
                props.setProperty(ConfigKeys.CONFIG_VERSION_KEY, String.valueOf(ConfigKeys.CONFIG_VERSION));
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Migration failed; continuing with pre-migration values", e);
            }
        }

        return Config.fromProperties(props);
    }

    /**
     * Saves the provided configuration under the given profile, using atomic move when supported.
     */
    public void save(String profile, Config cfg) {
        Objects.requireNonNull(cfg, "cfg");
        Path file = configFile(profile);
        Path tmp = file.resolveSibling(file.getFileName().toString() + ".tmp");
        Properties out = cfg.toProperties();
        out.setProperty("last_saved_iso8601", DateTimeFormatter.ISO_INSTANT.format(Instant.now()));

        try (OutputStream os = new BufferedOutputStream(Files.newOutputStream(tmp))) {
            out.store(os, "BWU v2 script configuration");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to write temp config: " + tmp, e);
            return;
        }

        try {
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException amnse) {
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ioe) {
                LOG.log(Level.WARNING, "Failed to replace config: " + file, ioe);
                safeDelete(tmp);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to atomically replace config: " + file, e);
            safeDelete(tmp);
        }
    }

    /**
     * Lists available profile names by scanning the profiles directory.
     */
    public List<String> listProfiles() {
        Path dir = getWorkspaceDir().resolve("config").resolve("profiles");
        List<String> out = new ArrayList<>();
        try {
            if (Files.isDirectory(dir)) {
                try (var stream = Files.list(dir)) {
                    stream.filter(p -> p.getFileName().toString().endsWith(".properties"))
                          .forEach(p -> {
                              String name = p.getFileName().toString();
                              int i = name.lastIndexOf('.');
                              if (i > 0) out.add(name.substring(0, i));
                          });
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to list profiles in " + dir, e);
        }
        if (out.isEmpty()) out.add("default");
        return out;
    }

    /**
     * Ensures a non-empty profile name; returns "default" if null/blank.
     */
    public String ensureProfileName(String requested) {
        if (requested == null) return "default";
        String s = requested.trim();
        return s.isEmpty() ? "default" : s;
    }

    private String sanitizeProfile(String profile) {
        String s = profile.replaceAll("[^a-zA-Z0-9-_]", "");
        return s.isEmpty() ? "default" : s;
    }

    private void migrate(Properties in, int from, int to) {
        // TODO: Implement migrations when version increases.
    }

    private int safeInt(String v, int def) {
        try { return Integer.parseInt(v); } catch (Exception e) { return def; }
    }

    private void safeDelete(Path p) {
        try { Files.deleteIfExists(p); } catch (Exception ignored) {}
    }
}
