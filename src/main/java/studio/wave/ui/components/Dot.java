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

public final class Dot extends JComponent {
    final Color tint; final int size;
    public Dot(Color tint, int size) { this.tint = tint; this.size = size; setPreferredSize(new Dimension(size, 15)); }
    @Override protected void paintComponent(Graphics graphics) { Graphics2D g = graphics(graphics); g.setColor(tint); g.fillOval(0, (getHeight() - size) / 2, size, size); g.dispose(); }
}
