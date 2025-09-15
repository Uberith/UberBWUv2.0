package com.example.config;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralizes configuration keys and default values for the script.
 */
public final class ConfigKeys {

    public static final String PROFILE = "profile";
    public static final String BANK_NAME = "bank_name";
    public static final String FOOD_NAME = "food_name";
    public static final String DELAY_MIN_MS = "delay_min_ms";
    public static final String DELAY_MAX_MS = "delay_max_ms";
    public static final String CONFIG_VERSION_KEY = "config_version";

    public static final int CONFIG_VERSION = 1;

    /**
     * Immutable map of default key-value pairs as strings.
     */
    public static final Map<String, String> DEFAULTS;

    static {
        Map<String, String> m = new HashMap<>();
        m.put(PROFILE, "default");
        m.put(BANK_NAME, "Varrock West");
        m.put(FOOD_NAME, "Lobster");
        m.put(DELAY_MIN_MS, "250");
        m.put(DELAY_MAX_MS, "750");
        m.put(CONFIG_VERSION_KEY, Integer.toString(CONFIG_VERSION));
        DEFAULTS = Collections.unmodifiableMap(m);
    }

    private ConfigKeys() {}
}

