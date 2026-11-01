package studio.wave.ui.devices;

import studio.wave.model.AudioDevice;
import javax.swing.JButton;
import java.awt.*;
import static studio.wave.ui.Theme.*;
import static studio.wave.ui.Icons.drawIcon;

public final class DeviceChoice extends JButton {
    private final AudioDevice device;
    private final boolean selected;
    public DeviceChoice(AudioDevice device, boolean selected, Runnable action) {
        this.device = device; this.selected = selected;
        setPreferredSize(new Dimension(290, 76)); setMaximumSize(new Dimension(Integer.MAX_VALUE, 76));
        setContentAreaFilled(false); setBorderPainted(false); setFocusPainted(false); setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText(device.name() + " — click to " + (selected ? "remove" : "select"));
        getAccessibleContext().setAccessibleName(device.name() + (selected ? ", selected" : ", available"));
        addActionListener(e -> action.run());
    }
    public AudioDevice device() { return device; }
    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = graphics(graphics);
        g.setColor(getModel().isRollover() ? RAISED : CARD); g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
        if (hasFocus()) { g.setColor(MINT); g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 12, 12); }
        drawIcon(g, device.type() == AudioDevice.Type.INPUT ? "mic" : "speaker", 13, 26, 21, selected ? MINT : MUTED);
        g.setFont(BODY.deriveFont(Font.BOLD, 11)); g.setColor(TEXT); g.drawString(ellipsize(g, device.name(), getWidth() - 93), 45, 29);
        g.setFont(BODY.deriveFont(9f)); g.setColor(MUTED); g.drawString(ellipsize(g, device.detail(), getWidth() - 93), 45, 48);
        drawIcon(g, selected ? "arrowLeft" : "arrowRight", getWidth() - 30, 29, 15, selected ? MINT : MUTED); g.dispose();
    }
}
