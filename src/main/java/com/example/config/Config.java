package com.example.config;

import java.util.Objects;
import java.util.Properties;

/**
 * Immutable in-memory view of settings parsed from {@link Properties}.
 */
public final class Config {
    public final String bankName;
    public final String foodName;
    public final int delayMinMs;
    public final int delayMaxMs;
    public final String profile;
    public final int version;

    /**
     * Constructs an immutable configuration instance.
     */
    public Config(String bankName, String foodName, int delayMinMs, int delayMaxMs, String profile, int version) {
        this.bankName = bankName;
        this.foodName = foodName;
        this.delayMinMs = delayMinMs;
        this.delayMaxMs = delayMaxMs;
        this.profile = profile;
        this.version = version;
    }

    /**
     * Parses a configuration from properties, applying defaults and clamping invalid values.
     * Never throws; always returns a non-null Config.
     */
    public static Config fromProperties(Properties p) {
        Objects.requireNonNull(p, "properties");

        String bank = p.getProperty(ConfigKeys.BANK_NAME, ConfigKeys.DEFAULTS.get(ConfigKeys.BANK_NAME));
        if (bank == null || bank.isBlank()) bank = ConfigKeys.DEFAULTS.get(ConfigKeys.BANK_NAME);

        String food = p.getProperty(ConfigKeys.FOOD_NAME, ConfigKeys.DEFAULTS.get(ConfigKeys.FOOD_NAME));
        if (food == null || food.isBlank()) food = ConfigKeys.DEFAULTS.get(ConfigKeys.FOOD_NAME);

        int min = parsePositiveInt(p.getProperty(ConfigKeys.DELAY_MIN_MS, ConfigKeys.DEFAULTS.get(ConfigKeys.DELAY_MIN_MS)),
                Integer.parseInt(ConfigKeys.DEFAULTS.get(ConfigKeys.DELAY_MIN_MS)));
        int max = parsePositiveInt(p.getProperty(ConfigKeys.DELAY_MAX_MS, ConfigKeys.DEFAULTS.get(ConfigKeys.DELAY_MAX_MS)),
                Integer.parseInt(ConfigKeys.DEFAULTS.get(ConfigKeys.DELAY_MAX_MS)));

        // Ensure min <= max and values are reasonable
        if (min < 0) min = 0;
        if (max < 0) max = 0;
        if (min > max) {
            int t = min; min = max; max = t;
        }

        String prof = p.getProperty(ConfigKeys.PROFILE, ConfigKeys.DEFAULTS.get(ConfigKeys.PROFILE));
        if (prof == null || prof.isBlank()) prof = "default";

        int version = parsePositiveInt(p.getProperty(ConfigKeys.CONFIG_VERSION_KEY, String.valueOf(ConfigKeys.CONFIG_VERSION)),
                ConfigKeys.CONFIG_VERSION);

        return new Config(bank, food, min, max, prof, version);
    }

    /**
     * Serializes this configuration to {@link Properties} including all keys and config version.
     */
    public Properties toProperties() {
        Properties props = new Properties();
        props.setProperty(ConfigKeys.BANK_NAME, nullToEmpty(bankName));
        props.setProperty(ConfigKeys.FOOD_NAME, nullToEmpty(foodName));
        props.setProperty(ConfigKeys.DELAY_MIN_MS, Integer.toString(Math.max(0, delayMinMs)));
        props.setProperty(ConfigKeys.DELAY_MAX_MS, Integer.toString(Math.max(0, delayMaxMs)));
        props.setProperty(ConfigKeys.PROFILE, nullToEmpty(profile));
        props.setProperty(ConfigKeys.CONFIG_VERSION_KEY, Integer.toString(version <= 0 ? ConfigKeys.CONFIG_VERSION : version));
        return props;
    }

    /** Returns a new Config with the provided field changed. */
    public Config withBankName(String value) { return new Config(value, foodName, delayMinMs, delayMaxMs, profile, version); }
    public Config withFoodName(String value) { return new Config(bankName, value, delayMinMs, delayMaxMs, profile, version); }
    public Config withDelayMinMs(int value) { return new Config(bankName, foodName, value, delayMaxMs, profile, version); }
    public Config withDelayMaxMs(int value) { return new Config(bankName, foodName, delayMinMs, value, profile, version); }
    public Config withProfile(String value) { return new Config(bankName, foodName, delayMinMs, delayMaxMs, value, version); }
    public Config withVersion(int value) { return new Config(bankName, foodName, delayMinMs, delayMaxMs, profile, value); }

    private static int parsePositiveInt(String s, int def) {
        if (s == null) return def;
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }
}

