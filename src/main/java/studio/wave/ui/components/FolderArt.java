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

public final class FolderArt extends JComponent {
    public FolderArt() { setPreferredSize(new Dimension(190, 145)); setMaximumSize(new Dimension(190, 145)); }
    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = graphics(graphics);
        g.setColor(color("222A2A")); g.fillOval(31, 6, 128, 128);
        g.setColor(color("2B3533")); g.drawOval(16, -9, 158, 158);
        g.translate(47, 47); g.rotate(-.08, 48, 35);
        Path2D back = new Path2D.Double(); back.moveTo(0, 8); back.quadTo(0, 0, 8, 0); back.lineTo(34, 0); back.lineTo(46, 12); back.lineTo(87, 12); back.quadTo(96, 12, 96, 20); back.lineTo(96, 66); back.lineTo(0, 66); back.closePath();
        g.setColor(color("526D60")); g.fill(back);
        g.setColor(color("9BBBAA")); g.fillRoundRect(8, 15, 77, 50, 5, 5);
        g.setColor(MINT); g.fillRoundRect(-3, 25, 103, 48, 8, 8);
        drawIcon(g, "wave", 35, 37, 28, color("526D60"));
        g.dispose();
    }
}
