package studio.wave.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.Collections;
import static studio.wave.ui.Theme.*;
import static studio.wave.ui.Icons.drawIcon;

public final class Theme {
    private Theme() {}
    public static final Color BG = color("121417"), PANEL = color("191C20"), CARD = color("22262B"),
            RAISED = color("2A2F35"), LINE = color("30353B"), TEXT = color("F0F3F2"),
            MUTED = color("919A9F"), DIM = color("667177"), MINT = color("B7E5CD"),
            CORAL = color("F4A18D"), LILAC = color("BDAFE8");
    public static final Font BODY = new Font("Inter", Font.PLAIN, 13);

    public static void applyTheme() {
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); }
        catch (Exception ignored) { }
        for (Object key : Collections.list(UIManager.getDefaults().keys())) {
            if (key.toString().endsWith(".font")) UIManager.put(key, BODY);
        }
        UIManager.put("Panel.background", PANEL);
        UIManager.put("OptionPane.background", PANEL);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("List.background", CARD);
        UIManager.put("List.foreground", TEXT);
        UIManager.put("List.selectionBackground", color("3F6156"));
        UIManager.put("List.selectionForeground", TEXT);
        UIManager.put("Table.background", CARD);
        UIManager.put("Table.foreground", TEXT);
        UIManager.put("Table.selectionBackground", color("3F6156"));
        UIManager.put("Table.selectionForeground", TEXT);
        UIManager.put("TableHeader.background", RAISED);
        UIManager.put("TableHeader.foreground", TEXT);
        UIManager.put("TextField.background", CARD);
        UIManager.put("TextField.foreground", TEXT);
        UIManager.put("TextField.caretForeground", MINT);
        UIManager.put("TextField.selectionBackground", color("3F6156"));
        UIManager.put("TextField.selectionForeground", TEXT);
        UIManager.put("ComboBox.background", CARD);
        UIManager.put("ComboBox.foreground", TEXT);
        UIManager.put("Button.background", RAISED);
        UIManager.put("Button.foreground", TEXT);
        UIManager.put("ToggleButton.background", RAISED);
        UIManager.put("ToggleButton.foreground", TEXT);
        UIManager.put("ToolTip.background", RAISED);
        UIManager.put("ToolTip.foreground", TEXT);
        UIManager.put("ToolTip.border", BorderFactory.createEmptyBorder(8, 10, 8, 10));
        UIManager.put("ScrollPane.background", PANEL);
        UIManager.put("Viewport.background", PANEL);
        UIManager.put("FileChooser.useSystemExtensionHiding", false);
    }

    public static Color color(String hex) { return Color.decode("#" + hex); }

    public static Color alpha(Color color, int alpha) { return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha); }

    public static Color blend(Color a, Color b, float amount) { return new Color((int) (a.getRed() * (1 - amount) + b.getRed() * amount), (int) (a.getGreen() * (1 - amount) + b.getGreen() * amount), (int) (a.getBlue() * (1 - amount) + b.getBlue() * amount)); }

    public static Graphics2D graphics(Graphics graphics) { Graphics2D g = (Graphics2D) graphics.create(); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON); g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE); return g; }

    public static JLabel label(String text, int size, Color color, int style) { JLabel label = new JLabel(text); label.setFont(BODY.deriveFont(style, (float) size)); label.setForeground(color); label.setAlignmentX(0); return label; }

    public static JPanel vertical(Color background) { JPanel panel = new JPanel(); panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS)); panel.setBackground(background); return panel; }

    public static JPanel row(Color background, int gap) { JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, gap, 0)); panel.setBackground(background); return panel; }

    public static Component gap(int height) { return Box.createVerticalStrut(height); }

    public static JScrollPane scroll(JComponent content, Color background) {
        JScrollPane pane = new JScrollPane(content); pane.setBorder(null); pane.setOpaque(false); pane.getViewport().setBackground(background);
        pane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER); pane.getVerticalScrollBar().setUnitIncrement(20);
        pane.getVerticalScrollBar().setPreferredSize(new Dimension(6, 0)); pane.getVerticalScrollBar().setUI(new BasicScrollBarUI() {
            @Override protected void configureScrollBarColors() { thumbColor = color("414A4F"); trackColor = background; }
            @Override protected JButton createDecreaseButton(int orientation) { return zero(); }
            @Override protected JButton createIncreaseButton(int orientation) { return zero(); }
            private JButton zero() { JButton b = new JButton(); b.setPreferredSize(new Dimension(0, 0)); b.setMinimumSize(new Dimension(0, 0)); b.setMaximumSize(new Dimension(0, 0)); return b; }
            @Override protected void paintThumb(Graphics g, JComponent c, Rectangle bounds) { Graphics2D gg = graphics(g); gg.setColor(thumbColor); gg.fillRoundRect(bounds.x + 1, bounds.y, Math.max(3, bounds.width - 2), bounds.height, 6, 6); gg.dispose(); }
        });
        return pane;
    }

    public static String ellipsize(Graphics2D g, String text, int width) { if (width <= 0) return ""; if (g.getFontMetrics().stringWidth(text) <= width) return text; int n = text.length(); while (n > 0 && g.getFontMetrics().stringWidth(text.substring(0, n) + "…") > width) n--; return text.substring(0, n) + "…"; }

    public static BufferedImage logoImage() { BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB); Graphics2D g = graphics(image.createGraphics()); g.setColor(MINT); g.fillRoundRect(0, 0, 64, 64, 20, 20); drawIcon(g, "wave", 14, 14, 36, color("263C32")); g.dispose(); return image; }

}
