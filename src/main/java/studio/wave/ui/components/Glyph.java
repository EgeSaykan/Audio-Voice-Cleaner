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

public final class Glyph extends JComponent {
    final String icon; final Color tint; final int size;
    public Glyph(String icon, Color tint, int size) { this.icon = icon; this.tint = tint; this.size = size; setPreferredSize(new Dimension(size, size)); setMaximumSize(getPreferredSize()); }
    @Override protected void paintComponent(Graphics graphics) { Graphics2D g = graphics(graphics); drawIcon(g, icon, 0, (getHeight() - size) / 2, size, tint); g.dispose(); }
}
