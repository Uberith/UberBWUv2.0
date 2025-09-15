package com.example.config;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Minimal tests for ConfigService behavior without external test frameworks.
 * Run manually by invoking the main method.
 */
public class ConfigServiceTest {

    public static void main(String[] args) throws Exception {
        ConfigService svc = new ConfigService();
        String profileA = "default";
        String profileB = "melee";

        // Use an isolated temp workspace by overriding user.home temporarily
        Path tmpHome = Files.createTempDirectory("cfgtest");
        String oldHome = System.getProperty("user.home");
        System.setProperty("user.home", tmpHome.toString());
        try {
            testLoadDefaultsWhenNoFile(svc, profileA);
            testSaveThenReloadRoundTrip(svc, profileA);
            testInvalidValuesClamped(svc, profileA);
            testProfileSwitchCreatesSeparateFiles(svc, profileA, profileB);
            testCorruptFileFallsBackForThatKey(svc, profileA);
            testAtomicWriteResistance(svc, profileA);
            System.out.println("All tests passed.");
        } finally {
            System.setProperty("user.home", oldHome);
            try { deleteRecursively(tmpHome); } catch (Exception ignored) {}
        }
    }

    private static void testLoadDefaultsWhenNoFile(ConfigService svc, String profile) {
        Config cfg = svc.load(profile);
        assert cfg.bankName != null && !cfg.bankName.isBlank();
        assert cfg.delayMinMs <= cfg.delayMaxMs;
    }

    private static void testSaveThenReloadRoundTrip(ConfigService svc, String profile) {
        Config cfg = svc.load(profile).withBankName("Edgeville").withFoodName("Shark").withDelayMinMs(100).withDelayMaxMs(200);
        svc.save(profile, cfg);
        Config re = svc.load(profile);
        assert "Edgeville".equals(re.bankName);
        assert "Shark".equals(re.foodName);
        assert re.delayMinMs == 100 && re.delayMaxMs == 200;
    }

    private static void testInvalidValuesClamped(ConfigService svc, String profile) throws IOException {
        Path f = svc.configFile(profile);
        Files.createDirectories(f.getParent());
        try (BufferedWriter w = Files.newBufferedWriter(f, StandardCharsets.UTF_8)) {
            w.write("delay_min_ms=-5\n");
            w.write("delay_max_ms=3\n");
        }
        Config c = svc.load(profile);
        assert c.delayMinMs == 0;
        assert c.delayMaxMs == 3;
        assert c.delayMinMs <= c.delayMaxMs;
    }

    private static void testProfileSwitchCreatesSeparateFiles(ConfigService svc, String a, String b) {
        Config ca = svc.load(a).withBankName("A");
        Config cb = svc.load(b).withBankName("B");
        svc.save(a, ca);
        svc.save(b, cb);
        Config ra = svc.load(a);
        Config rb = svc.load(b);
        assert "A".equals(ra.bankName);
        assert "B".equals(rb.bankName);
        List<String> profiles = svc.listProfiles();
        assert profiles.contains("default") && profiles.contains("melee");
    }

    private static void testCorruptFileFallsBackForThatKey(ConfigService svc, String profile) throws IOException {
        Path f = svc.configFile(profile);
        try (BufferedWriter w = Files.newBufferedWriter(f, StandardCharsets.UTF_8)) {
            w.write("delay_min_ms=abc\n");
            w.write("delay_max_ms=100\n");
        }
        Config c = svc.load(profile);
        assert c.delayMaxMs == 100;
        assert c.delayMinMs <= c.delayMaxMs; // falls back to default 250 vs 100 -> swaps/clamps
    }

    private static void testAtomicWriteResistance(ConfigService svc, String profile) throws IOException {
        Path file = svc.configFile(profile);
        Files.createDirectories(file.getParent());
        // Simulate partial/corrupt write
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write("delay_min_ms=1"); // missing newline and other keys
        }
        Config c = svc.load(profile);
        // Should at least not crash and have sane values
        assert c.delayMinMs >= 0;
        assert c.delayMaxMs >= c.delayMinMs;
    }

    private static void deleteRecursively(Path p) throws IOException {
        if (Files.notExists(p)) return;
        if (Files.isDirectory(p)) {
            try (var s = Files.list(p)) { s.forEach(q -> { try { deleteRecursively(q);} catch (IOException ignored){} }); }
        }
        Files.deleteIfExists(p);
    }
}

