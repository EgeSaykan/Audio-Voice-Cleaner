package studio.wave.ui.library;

import studio.wave.storage.RecordingLibrary;
import javax.swing.JButton;
import java.awt.*;
import java.nio.file.Path;
import static studio.wave.ui.Theme.*;
import static studio.wave.ui.Icons.drawIcon;

public final class RecordingFileCard extends JButton {
    private final Path file;
    public RecordingFileCard(Path file, Runnable load) {
        this.file = file; setPreferredSize(new Dimension(230, 68)); setMaximumSize(new Dimension(Integer.MAX_VALUE, 68));
        setContentAreaFilled(false); setBorderPainted(false); setFocusPainted(false); setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText("Add recording: " + file.getFileName()); getAccessibleContext().setAccessibleName("Add recording " + file.getFileName()); addActionListener(e -> load.run());
    }
    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = graphics(graphics); g.setColor(getModel().isRollover() ? RAISED : CARD); g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
        if (hasFocus()) { g.setColor(MINT); g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 12, 12); }
        drawIcon(g, "file", 12, 22, 22, MINT); g.setColor(TEXT); g.setFont(BODY.deriveFont(Font.BOLD, 11));
        g.drawString(ellipsize(g, file.getFileName().toString(), getWidth() - 67), 46, 27);
        g.setColor(MUTED); g.setFont(BODY.deriveFont(9f)); g.drawString(RecordingLibrary.extension(file).toUpperCase(java.util.Locale.ROOT) + "  ·  Local audio", 46, 46); g.dispose();
    }
}
