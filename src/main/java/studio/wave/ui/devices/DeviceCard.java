package studio.wave.ui.devices;

import studio.wave.model.AudioDevice;
import javax.swing.JPanel;
import java.awt.*;
import java.util.Locale;
import java.util.function.Supplier;
import static studio.wave.ui.Theme.*;
import static studio.wave.ui.Icons.drawIcon;

public final class DeviceCard extends JPanel {
    private final AudioDevice device;
    private final Supplier<Float> level;

    public DeviceCard(AudioDevice device, Supplier<Float> level) {
        this.device = device; this.level = level; setOpaque(false);
        int height = device.type() == AudioDevice.Type.INPUT ? 119 : 100;
        setPreferredSize(new Dimension(210, height)); setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        setAlignmentX(0); setToolTipText(device.name() + " — " + device.detail());
        getAccessibleContext().setAccessibleName(device.name() + ", " + device.detail());
    }
    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = graphics(graphics);
        boolean input = device.type() == AudioDevice.Type.INPUT;
        Color tint = input ? MINT : LILAC;
        g.setColor(CARD); g.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
        drawIcon(g, input ? "mic" : "speaker", 13, 13, 21, tint);
        g.setFont(BODY.deriveFont(Font.BOLD, 8)); g.setColor(MUTED);
        String badge = device.systemDefault() ? "SYSTEM DEFAULT" : input ? "INPUT" : "OUTPUT";
        g.drawString(badge, getWidth() - 14 - g.getFontMetrics().stringWidth(badge), 26);
        g.setColor(TEXT); g.setFont(BODY.deriveFont(Font.BOLD, 11));
        g.drawString(ellipsize(g, device.name(), getWidth() - 26), 13, 54);
        g.setColor(device.muted() ? CORAL : MUTED); g.setFont(BODY.deriveFont(9f));
        g.drawString(device.muted() ? "Muted in system settings" : input ? "48 kHz · 16-bit PCM · mono" : "Selected for playback", 13, 73);
        if (input) {
            float amplitude = level.get();
            double db = amplitude <= 0 ? -90 : 20 * Math.log10(amplitude);
            int count = Math.max(1, (getWidth() - 26) / 7), lit = (int) Math.round(count * Math.max(0, (db + 60) / 60));
            for (int i = 0; i < count; i++) { g.setColor(i < lit ? i >= count - 2 ? CORAL : MINT : LINE); g.fillRoundRect(13 + 7 * i, 88, 4, 5, 2, 2); }
            g.setFont(BODY.deriveFont(8f)); g.setColor(DIM); g.drawString("INPUT LEVEL", 13, 111);
            String value = amplitude == 0 ? "−∞ dB" : String.format(Locale.ROOT, "%.1f dB", db);
            g.drawString(value, getWidth() - 13 - g.getFontMetrics().stringWidth(value), 111);
        }
        g.dispose();
    }
}
