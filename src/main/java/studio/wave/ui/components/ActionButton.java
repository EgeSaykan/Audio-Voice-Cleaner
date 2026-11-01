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

public class ActionButton extends JButton {
    private String icon; private final Color fill, ink;
    public ActionButton(String text, String icon, Color fill, Color ink) {
        super(text); this.icon = icon; this.fill = fill; this.ink = ink;
        setFont(BODY.deriveFont(Font.BOLD, 12)); setContentAreaFilled(false); setBorderPainted(false); setFocusPainted(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setBorder(new EmptyBorder(0, 17, 0, 17)); setPreferredSize(new Dimension(Math.max(88, text.length() * 7 + (icon == null ? 28 : 53)), 42));
        setMaximumSize(getPreferredSize());
    }
    public void setIconName(String name) { icon = name; repaint(); }
    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = graphics(graphics); Color bg = getModel().isPressed() ? fill.darker() : getModel().isRollover() ? blend(fill, Color.WHITE, .07f) : fill;
        g.setColor(isEnabled() ? bg : blend(fill, PANEL, .65f)); g.fillRoundRect(0, 0, getWidth(), getHeight(), 11, 11);
        if (hasFocus()) { g.setColor(MINT); g.drawRoundRect(2, 2, getWidth() - 5, getHeight() - 5, 9, 9); }
        g.setFont(getFont()); int textWidth = g.getFontMetrics().stringWidth(getText()); int total = textWidth + (icon == null ? 0 : 25);
        int x = (getWidth() - total) / 2;
        Color fg = isEnabled() ? ink : blend(ink, PANEL, .55f);
        if (icon != null) { drawIcon(g, icon, x, (getHeight() - 17) / 2, 17, fg); x += 25; }
        g.setColor(fg); g.drawString(getText(), x, (getHeight() - g.getFontMetrics().getHeight()) / 2 + g.getFontMetrics().getAscent()); g.dispose();
    }
}
