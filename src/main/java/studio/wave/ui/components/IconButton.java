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

public final class IconButton extends JButton {
    private String icon; private Color background;
    public IconButton(String icon, String tooltip) {
        this.icon = icon; setPreferredSize(new Dimension(34, 34)); setMaximumSize(new Dimension(34, 34));
        setContentAreaFilled(false); setBorderPainted(false); setFocusPainted(false); setToolTipText(tooltip);
        getAccessibleContext().setAccessibleName(tooltip); setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
    public void setIconName(String name) { icon = name; repaint(); }
    public void setButtonBackground(Color color) { background = color; repaint(); }
    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = graphics(graphics);
        if (background != null || getModel().isRollover()) { g.setColor(getModel().isRollover() ? RAISED.brighter() : background); g.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10); }
        if (hasFocus()) { g.setColor(MINT); g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 10, 10); }
        drawIcon(g, icon, (getWidth() - 18) / 2, (getHeight() - 18) / 2, 18, isEnabled() ? TEXT : DIM); g.dispose();
    }
}
