package com.example.ui;

import com.example.config.Config;
import com.example.config.ConfigService;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Objects;

/**
 * Minimal Swing panel demonstrating live edits and persistence.
 * This panel is decoupled from I/O; it uses {@link ConfigService} provided by the caller.
 */
public class ConfigPanel extends JPanel {
    private final ConfigService service;

    private volatile Config config;

    private final JTextField bankName = new JTextField(16);
    private final JTextField foodName = new JTextField(16);
    private final JSpinner delayMin = new JSpinner(new SpinnerNumberModel(250, 0, 60_000, 50));
    private final JSpinner delayMax = new JSpinner(new SpinnerNumberModel(750, 0, 60_000, 50));
    private final JComboBox<String> profileDropdown = new JComboBox<>();
    private final JTextField profileName = new JTextField(12);
    private final JButton loadBtn = new JButton("Load Profile…");
    private final JButton saveBtn = new JButton("Save");
    private final JButton saveAsBtn = new JButton("Save As…");
    private final JButton resetBtn = new JButton("Reset to defaults");

    /**
     * Creates a new configuration panel bound to the given service and initial config.
     */
    public ConfigPanel(ConfigService service, Config initial, List<String> knownProfiles) {
        super(new GridBagLayout());
        this.service = Objects.requireNonNull(service);
        this.config = Objects.requireNonNull(initial);

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;

        int row = 0;
        addRow(c, row++, new JLabel("Profile:"), profileDropdown);
        addRow(c, row++, new JLabel("Bank Name:"), bankName);
        addRow(c, row++, new JLabel("Food Name:"), foodName);
        addRow(c, row++, new JLabel("Delay Min (ms):"), delayMin);
        addRow(c, row++, new JLabel("Delay Max (ms):"), delayMax);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.add(loadBtn);
        actions.add(saveBtn);
        actions.add(saveAsBtn);
        actions.add(new JLabel("As:"));
        actions.add(profileName);
        actions.add(resetBtn);

        c.gridx = 0; c.gridy = row; c.gridwidth = 2; c.weightx = 1.0;
        add(actions, c);

        refreshProfiles(knownProfiles);
        bindValues(initial);
        connectListeners();
    }

    private void addRow(GridBagConstraints base, int row, JComponent label, JComponent field) {
        GridBagConstraints c1 = (GridBagConstraints) base.clone();
        c1.gridx = 0; c1.gridy = row; c1.gridwidth = 1; c1.weightx = 0.0;
        add(label, c1);

        GridBagConstraints c2 = (GridBagConstraints) base.clone();
        c2.gridx = 1; c2.gridy = row; c2.gridwidth = 1; c2.weightx = 1.0;
        add(field, c2);
    }

    private void connectListeners() {
        bankName.getDocument().addDocumentListener(SimpleDoc.onChange(() -> {
            config = config.withBankName(bankName.getText());
        }));
        foodName.getDocument().addDocumentListener(SimpleDoc.onChange(() -> {
            config = config.withFoodName(foodName.getText());
        }));
        delayMin.addChangeListener(e -> {
            int min = (int) delayMin.getValue();
            int max = (int) delayMax.getValue();
            if (min > max) { max = min; delayMax.setValue(max); }
            config = config.withDelayMinMs(min).withDelayMaxMs(max);
        });
        delayMax.addChangeListener(e -> {
            int min = (int) delayMin.getValue();
            int max = (int) delayMax.getValue();
            if (min > max) { min = max; delayMin.setValue(min); }
            config = config.withDelayMinMs(min).withDelayMaxMs(max);
        });

        profileDropdown.addActionListener(e -> {
            String selected = (String) profileDropdown.getSelectedItem();
            if (selected != null && !selected.isBlank()) {
                profileName.setText(selected);
            }
        });
    }

    /** Refreshes the dropdown with known profile names. */
    public void refreshProfiles(List<String> profiles) {
        profileDropdown.removeAllItems();
        for (String p : profiles) profileDropdown.addItem(p);
        if (config != null) profileDropdown.setSelectedItem(config.profile);
    }

    /** Binds UI fields to the provided config values. */
    public void bindValues(Config cfg) {
        this.config = cfg;
        bankName.setText(cfg.bankName);
        foodName.setText(cfg.foodName);
        delayMin.setValue(Math.max(0, cfg.delayMinMs));
        delayMax.setValue(Math.max(0, cfg.delayMaxMs));
        profileDropdown.setSelectedItem(cfg.profile);
    }

    /** Returns the current in-memory config represented by the UI. */
    public Config getConfig() { return config; }

    public JButton getLoadButton() { return loadBtn; }
    public JButton getSaveButton() { return saveBtn; }
    public JButton getSaveAsButton() { return saveAsBtn; }
    public JButton getResetButton() { return resetBtn; }
    public String getRequestedProfileName() { return profileName.getText(); }

    // Simple DocumentListener adapter
    private static class SimpleDoc implements javax.swing.event.DocumentListener {
        private final Runnable r;
        private SimpleDoc(Runnable r) { this.r = r; }
        public static SimpleDoc onChange(Runnable r) { return new SimpleDoc(r); }
        @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { r.run(); }
        @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { r.run(); }
        @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { r.run(); }
    }
}

