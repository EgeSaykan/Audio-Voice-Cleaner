package studio.wave.ui.devices;

import studio.wave.app.StudioController;
import studio.wave.model.AudioDevice;
import studio.wave.ui.components.IconButton;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.function.Consumer;
import static studio.wave.ui.Theme.*;

/** The horizontal divider separates capture inputs above from playback outputs below. */
public final class DeviceSidebar extends JPanel {
    private final StudioController controller;
    private final JPanel inputs = vertical(PANEL), outputs = vertical(PANEL);
    private final JLabel inputCount = label("", 9, MUTED, Font.BOLD), outputCount = label("", 9, MUTED, Font.BOLD);
    private final IconButton inputAdd = new IconButton("plus", "Choose input devices"), outputAdd = new IconButton("plus", "Choose output devices");
    private final IconButton refresh = new IconButton("refresh", "Refresh audio devices");

    public DeviceSidebar(StudioController controller, Consumer<AudioDevice.Type> picker) {
        super(new BorderLayout()); this.controller = controller;
        setBackground(PANEL); setPreferredSize(new Dimension(266, 400));
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, LINE));
        JPanel header = new JPanel(new BorderLayout()); header.setOpaque(false); header.setBorder(new EmptyBorder(14, 22, 10, 17));
        header.add(label("Audio Devices", 16, TEXT, Font.BOLD), BorderLayout.CENTER);
        header.add(refresh, BorderLayout.EAST); refresh.addActionListener(e -> controller.discoverDevices()); add(header, BorderLayout.NORTH);
        JPanel sections = new JPanel(new GridLayout(2, 1)); sections.setOpaque(false);
        sections.add(section("Inputs", inputCount, inputAdd, inputs, false));
        sections.add(section("Outputs", outputCount, outputAdd, outputs, true)); add(sections, BorderLayout.CENTER);
        inputAdd.addActionListener(e -> picker.accept(AudioDevice.Type.INPUT));
        outputAdd.addActionListener(e -> picker.accept(AudioDevice.Type.OUTPUT));
        refresh();
    }
    private JPanel section(String name, JLabel count, JButton add, JPanel list, boolean lower) {
        JPanel panel = new JPanel(new BorderLayout(0, 10)); panel.setBackground(PANEL);
        panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(lower ? 1 : 0, 0, 0, 0, LINE), new EmptyBorder(12, 22, 12, 20)));
        JPanel header = new JPanel(new BorderLayout()); header.setOpaque(false);
        JPanel titles = vertical(PANEL); titles.add(label(name, 14, lower ? LILAC : MINT, Font.BOLD)); titles.add(gap(7)); titles.add(count);
        header.add(titles, BorderLayout.CENTER); header.add(add, BorderLayout.EAST);
        panel.add(header, BorderLayout.NORTH); panel.add(scroll(list, PANEL), BorderLayout.CENTER);
        panel.add(label(lower ? "Playback goes to selected outputs" : "Meters show actual recording levels", 9, DIM, Font.PLAIN), BorderLayout.SOUTH);
        return panel;
    }
    public void refresh() {
        populate(inputs, inputCount, AudioDevice.Type.INPUT); populate(outputs, outputCount, AudioDevice.Type.OUTPUT);
        inputAdd.setEnabled(controller.editable()); outputAdd.setEnabled(controller.editable());
        refresh.setEnabled(controller.editable() && controller.state() != StudioController.State.PLAYING);
    }
    private void populate(JPanel panel, JLabel count, AudioDevice.Type type) {
        panel.removeAll(); var selected = controller.selected(type);
        count.setText(selected.size() + " SELECTED / " + controller.devices(type).size() + " AVAILABLE");
        for (AudioDevice device : selected) { panel.add(new DeviceCard(device, () -> controller.inputLevel(device.id()))); panel.add(gap(10)); }
        if (selected.isEmpty()) { panel.add(gap(10)); panel.add(label(type == AudioDevice.Type.INPUT ? "No inputs selected" : "No outputs selected", 11, MUTED, Font.PLAIN)); panel.add(gap(7)); panel.add(label("Click + to choose a device.", 10, DIM, Font.PLAIN)); }
        panel.revalidate(); panel.repaint();
    }
    public void refreshLevels() { inputs.repaint(); }
}
