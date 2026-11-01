package studio.wave.ui.components;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.Collections;
import static studio.wave.ui.Theme.*;
import static studio.wave.ui.Icons.drawIcon;

public final class Badge extends JComponent {
    final String text; final Color ink, fill;
    public Badge(String text, Color ink, Color fill) { this.text = text; this.ink = ink; this.fill = fill; setPreferredSize(new Dimension(text.length() * 6 + 17, 23)); setMaximumSize(getPreferredSize()); }
    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = graphics(graphics); g.setColor(fill); g.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8); g.setFont(BODY.deriveFont(Font.BOLD, 8)); g.setColor(ink);
        g.drawString(text, (getWidth() - g.getFontMetrics().stringWidth(text)) / 2, (getHeight() + g.getFontMetrics().getAscent() - g.getFontMetrics().getDescent()) / 2); g.dispose();
    }
}
