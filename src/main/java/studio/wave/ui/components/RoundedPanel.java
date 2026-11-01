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

public class RoundedPanel extends JPanel {
    final Color fill; final int radius;
    public RoundedPanel(Color fill, int radius) { this.fill = fill; this.radius = radius; setOpaque(false); }
    @Override protected void paintComponent(Graphics graphics) { Graphics2D g = graphics(graphics); g.setColor(fill); g.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius); g.dispose(); super.paintComponent(graphics); }
}
