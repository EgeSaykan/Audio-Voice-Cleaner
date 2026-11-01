package studio.wave.ui;

import java.awt.*;
import java.awt.geom.Path2D;

public final class Icons {
    private Icons() {}
public static void drawIcon(Graphics2D original, String icon, int x, int y, int size, Color color) {
    Graphics2D g = (Graphics2D) original.create(); g.translate(x, y); g.scale(size / 24.0, size / 24.0);
    g.setColor(color); g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    Path2D p = new Path2D.Double();
    switch (icon) {
        case "plus" -> { g.drawLine(12, 5, 12, 19); g.drawLine(5, 12, 19, 12); }
        case "minus" -> g.drawLine(5, 12, 19, 12);
        case "wave" -> { g.drawLine(3, 10, 3, 14); g.drawLine(7, 5, 7, 19); g.drawLine(12, 2, 12, 22); g.drawLine(17, 6, 17, 18); g.drawLine(21, 10, 21, 14); }
        case "mic" -> { g.drawRoundRect(8, 2, 8, 13, 8, 8); g.drawArc(4, 6, 16, 13, 180, 180); g.drawLine(12, 19, 12, 22); g.drawLine(8, 22, 16, 22); }
        case "speaker", "muted" -> {
            p.moveTo(3, 9); p.lineTo(7, 9); p.lineTo(12, 5); p.lineTo(12, 19); p.lineTo(7, 15); p.lineTo(3, 15); p.closePath(); g.draw(p);
            if (icon.equals("speaker")) { g.drawArc(11, 7, 8, 10, -65, 130); g.drawArc(10, 3, 14, 18, -55, 110); }
            else { g.drawLine(17, 9, 23, 15); g.drawLine(17, 15, 23, 9); }
        }
        case "folder" -> { p.moveTo(3, 5); p.lineTo(9, 5); p.lineTo(12, 8); p.lineTo(21, 8); p.lineTo(21, 19); p.lineTo(3, 19); p.closePath(); g.draw(p); }
        case "play" -> { p.moveTo(8, 4); p.lineTo(20, 12); p.lineTo(8, 20); p.closePath(); g.fill(p); }
        case "pause" -> { g.fillRoundRect(6, 4, 4, 16, 2, 2); g.fillRoundRect(14, 4, 4, 16, 2, 2); }
        case "stop" -> g.fillRoundRect(5, 5, 14, 14, 3, 3);
        case "record" -> g.fillOval(5, 5, 14, 14);
        case "save" -> { p.moveTo(4, 3); p.lineTo(17, 3); p.lineTo(21, 7); p.lineTo(21, 21); p.lineTo(4, 21); p.closePath(); g.draw(p); g.drawRect(8, 3, 8, 6); g.drawRect(8, 14, 9, 7); }
        case "rewind" -> { g.drawLine(5, 5, 5, 19); p.moveTo(18, 5); p.lineTo(8, 12); p.lineTo(18, 19); p.closePath(); g.draw(p); }
        case "arrowRight", "arrowLeft" -> { if (icon.equals("arrowLeft")) { g.translate(24, 0); g.scale(-1, 1); } g.drawLine(5, 12, 19, 12); g.drawLine(14, 7, 19, 12); g.drawLine(14, 17, 19, 12); }
        case "check" -> { p.moveTo(5, 12); p.lineTo(10, 17); p.lineTo(20, 6); g.draw(p); }
        case "search" -> { g.drawOval(3, 3, 13, 13); g.drawLine(14, 14, 21, 21); }
        case "headphones" -> { g.drawArc(3, 3, 18, 20, 0, 180); g.drawRoundRect(3, 12, 4, 9, 3, 3); g.drawRoundRect(17, 12, 4, 9, 3, 3); }
        case "lock" -> { g.drawRoundRect(5, 10, 14, 12, 3, 3); g.drawArc(8, 2, 8, 15, 0, 180); g.drawLine(12, 15, 12, 18); }
        case "info" -> { g.drawOval(3, 3, 18, 18); g.drawLine(12, 11, 12, 17); g.fillOval(11, 6, 2, 2); }
        case "grid" -> { for (int a = 3; a <= 13; a += 10) for (int b = 3; b <= 13; b += 10) g.drawRoundRect(a, b, 6, 6, 2, 2); }
        case "edit" -> { p.moveTo(5, 15); p.lineTo(16, 4); p.lineTo(20, 8); p.lineTo(9, 19); p.lineTo(4, 20); p.closePath(); g.draw(p); g.drawLine(14, 6, 18, 10); }
        case "cursor" -> { p.moveTo(5, 3); p.lineTo(19, 13); p.lineTo(13, 14); p.lineTo(10, 21); p.closePath(); g.draw(p); }
        case "refresh" -> { g.drawArc(4, 4, 16, 16, 40, 290); g.drawLine(20, 3, 20, 9); g.drawLine(14, 9, 20, 9); }
        case "file" -> { p.moveTo(5, 2); p.lineTo(14, 2); p.lineTo(20, 8); p.lineTo(20, 22); p.lineTo(5, 22); p.closePath(); g.draw(p); g.drawLine(14, 2, 14, 8); g.drawLine(14, 8, 20, 8); g.drawLine(9, 14, 9, 18); g.drawLine(13, 12, 13, 18); g.drawLine(17, 15, 17, 18); }
    }
    g.dispose();
}
}
