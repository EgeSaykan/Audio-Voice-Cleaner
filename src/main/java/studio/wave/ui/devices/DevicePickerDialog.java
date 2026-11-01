package studio.wave.ui.devices;

import studio.wave.model.AudioDevice;
import studio.wave.ui.components.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;
import static studio.wave.ui.Theme.*;

public final class DevicePickerDialog extends JDialog {
    private final Set<String> pending;
    private final List<AudioDevice> devices;
    private final JPanel available = vertical(PANEL), selected = vertical(PANEL);
    private final JLabel availableLabel = label("", 11, MUTED, Font.BOLD), selectedLabel = label("", 11, MINT, Font.BOLD);

    public DevicePickerDialog(JFrame owner, AudioDevice.Type type, List<AudioDevice> devices, Set<String> selection, Consumer<Set<String>> onApply) {
        super(owner, type == AudioDevice.Type.INPUT ? "Choose input devices" : "Choose output devices", true);
        this.devices = devices.stream().filter(d -> d.type() == type).toList();
        pending = new LinkedHashSet<>(); this.devices.forEach(d -> { if (selection.contains(d.id())) pending.add(d.id()); });
        setSize(820, 570); setMinimumSize(new Dimension(720, 480)); setLocationRelativeTo(owner);
        JPanel root = new JPanel(new BorderLayout(0, 25)); root.setBackground(BG); root.setBorder(new EmptyBorder(28, 28, 24, 28));
        JPanel header = vertical(BG); header.add(label(type == AudioDevice.Type.INPUT ? "CAPTURE YOUR VOICE" : "HEAR YOUR RECORDINGS", 9, MINT, Font.BOLD)); header.add(gap(10));
        header.add(label(getTitle(), 25, TEXT, Font.BOLD)); header.add(gap(10));
        header.add(label("Click a device to move it between columns. You can select more than one.", 12, MUTED, Font.PLAIN)); root.add(header, BorderLayout.NORTH);
        JPanel columns = new JPanel(new GridLayout(1, 2, 20, 0)); columns.setOpaque(false);
        columns.add(column(availableLabel, available)); columns.add(column(selectedLabel, selected)); root.add(columns, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout()); footer.setOpaque(false);
        footer.add(label(type == AudioDevice.Type.INPUT ? "Only capture inputs are shown." : "Only playback outputs are shown.", 10, MUTED, Font.PLAIN), BorderLayout.CENTER);
        JPanel actions = row(BG, 10);
        ActionButton cancel = new ActionButton("Cancel", null, RAISED, TEXT); cancel.addActionListener(e -> dispose());
        ActionButton apply = new ActionButton("Apply selection", "check", MINT, BG); apply.setName("applyDevices");
        apply.addActionListener(e -> { onApply.accept(Set.copyOf(pending)); dispose(); }); actions.add(cancel); actions.add(apply);
        footer.add(actions, BorderLayout.EAST); root.add(footer, BorderLayout.SOUTH); setContentPane(root); getRootPane().setDefaultButton(apply);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW); rebuild();
    }
    private JPanel column(JLabel title, JPanel list) {
        RoundedPanel panel = new RoundedPanel(PANEL, 16); panel.setLayout(new BorderLayout(0, 18)); panel.setBorder(new EmptyBorder(17, 14, 14, 14));
        panel.add(title, BorderLayout.NORTH); panel.add(scroll(list, PANEL), BorderLayout.CENTER); return panel;
    }
    private void rebuild() {
        available.removeAll(); selected.removeAll();
        availableLabel.setText("AVAILABLE / UNSELECTED  ·  " + (devices.size() - pending.size())); selectedLabel.setText("SELECTED  ·  " + pending.size());
        for (AudioDevice device : devices) {
            boolean picked = pending.contains(device.id());
            DeviceChoice choice = new DeviceChoice(device, picked, () -> { if (!pending.remove(device.id())) pending.add(device.id()); rebuild(); });
            JPanel list = picked ? selected : available; list.add(choice); list.add(gap(9));
        }
        if (pending.isEmpty()) { selected.add(gap(24)); selected.add(label("Your selected devices appear here.", 11, MUTED, Font.PLAIN)); }
        if (pending.size() == devices.size()) { available.add(gap(24)); available.add(label(devices.isEmpty() ? "No devices found. Refresh the sidebar." : "All devices are selected.", 11, MUTED, Font.PLAIN)); }
        available.revalidate(); available.repaint(); selected.revalidate(); selected.repaint();
    }
}
