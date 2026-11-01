package studio.wave.ui.timeline;

import studio.wave.app.StudioController;
import studio.wave.model.AudioTrack;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.util.Locale;
import static studio.wave.ui.Theme.*;
import static studio.wave.ui.Icons.drawIcon;

/** Paints sample-derived envelopes. Silence is a flat line; no synthetic waveform generator exists. */
public final class TimelinePanel extends JPanel implements Scrollable {
    private static final int TOP = 40, HEIGHT = 149, STRIDE = 165, INSET = 20;
    private final StudioController controller;
    private final Runnable record;
    private double viewSeconds = 30, viewStart;
    private int activeTrack = -1, lastTrackCount;
    private boolean dragging;

    public TimelinePanel(StudioController controller, Runnable record) {
        this.controller = controller; this.record = record;
        setOpaque(false); setFocusable(true); setToolTipText("Click or drag to seek. Shift + mouse wheel pans the timeline.");
        getAccessibleContext().setAccessibleName("Audio timeline. Left and right arrows move the playhead.");
        MouseAdapter mouse = new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow(); dragging = false;
                int index = (e.getY() - TOP) / STRIDE;
                if (e.getY() >= TOP && index < controller.session().tracks().size()) {
                    int y = e.getY() - TOP - index * STRIDE;
                    if (y < HEIGHT) activeTrack = index;
                    if (y >= 12 && y <= 40 && e.getX() > getWidth() - 50) { controller.mute(controller.session().tracks().get(index)); return; }
                }
                int blank = TOP + controller.session().tracks().size() * STRIDE + 7;
                if (e.getY() >= blank && e.getY() <= blank + 120) { record.run(); return; }
                dragging = true; seek(e.getX());
            }
            public void mouseDragged(MouseEvent e) { if (dragging) seek(e.getX()); }
            public void mouseReleased(MouseEvent e) { dragging = false; }
        };
        addMouseListener(mouse); addMouseMotionListener(mouse);
        addMouseWheelListener(e -> {
            if (!e.isShiftDown()) return;
            viewStart = Math.max(0, Math.min(Math.max(0, controller.session().end() - viewSeconds / 2), viewStart + e.getPreciseWheelRotation() * viewSeconds / 10)); repaint(); e.consume();
        });
        getInputMap().put(KeyStroke.getKeyStroke("LEFT"), "left"); getInputMap().put(KeyStroke.getKeyStroke("RIGHT"), "right");
        getActionMap().put("left", new AbstractAction() { public void actionPerformed(ActionEvent e) { controller.seek(controller.session().cursor() - .1); } });
        getActionMap().put("right", new AbstractAction() { public void actionPerformed(ActionEvent e) { controller.seek(controller.session().cursor() + .1); } });
    }
    private void seek(int x) { controller.seek(viewStart + (x - INSET) / (double) Math.max(1, getWidth() - INSET * 2) * viewSeconds); }
    public double viewSeconds() { return viewSeconds; }
    public void zoom(double factor) { viewSeconds = Math.max(5, Math.min(3600, viewSeconds * factor)); ensureCursorVisible(); repaint(); }
    public void ensureCursorVisible() {
        double cursor = controller.session().cursor();
        if (cursor < viewStart || cursor >= viewStart + viewSeconds) viewStart = Math.floor(cursor / viewSeconds) * viewSeconds;
    }
    public void refresh() {
        int count = controller.session().tracks().size();
        if (count != lastTrackCount) {
            activeTrack = count - 1; lastTrackCount = count; revalidate();
            if (count > 0) SwingUtilities.invokeLater(() -> scrollRectToVisible(new Rectangle(0, count == 1 ? 0 : TOP + activeTrack * STRIDE, 10, HEIGHT + TOP)));
        }
        ensureCursorVisible(); repaint();
    }
    @Override public Dimension getPreferredSize() { return new Dimension(450, TOP + controller.session().tracks().size() * STRIDE + 130); }
    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics); Graphics2D g = graphics(graphics);
        int w = getWidth(), usable = Math.max(1, w - INSET * 2);
        double step = viewSeconds <= 10 ? 1 : viewSeconds <= 45 ? 5 : viewSeconds <= 120 ? 15 : viewSeconds <= 600 ? 60 : 300;
        g.setFont(BODY.deriveFont(9f));
        for (double seconds = Math.ceil(viewStart / step) * step; seconds <= viewStart + viewSeconds + .001; seconds += step) {
            int x = INSET + (int) ((seconds - viewStart) / viewSeconds * usable);
            g.setColor(DIM); g.drawString(String.format("%02d:%02d", (int) seconds / 60, (int) seconds % 60), Math.max(0, Math.min(w - 33, x - 12)), 15);
            g.setColor(LINE); g.drawLine(x, 25, x, 32);
            for (int k = 1; k < 5 && seconds < viewStart + viewSeconds; k++) { int minor = x + (int) (step / viewSeconds * usable * k / 5); g.drawLine(minor, 29, minor, 32); }
        }
        var tracks = controller.session().tracks();
        for (int i = 0; i < tracks.size(); i++) paintTrack(g, tracks.get(i), i, usable);
        int blankY = TOP + tracks.size() * STRIDE + 7;
        g.setColor(color("2C3335")); g.setStroke(new BasicStroke(1, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1, new float[]{5, 6}, 0));
        g.drawRoundRect(1, blankY, w - 3, tracks.isEmpty() ? 116 : 68, 16, 16); g.setStroke(new BasicStroke(1));
        int shift = tracks.isEmpty() ? 24 : 0;
        drawIcon(g, tracks.isEmpty() ? "mic" : "plus", Math.max(16, w / 2 - 137), blankY + 23 + shift, 23, MINT);
        g.setFont(BODY.deriveFont(Font.BOLD, 12)); g.setColor(TEXT); g.drawString(tracks.isEmpty() ? "Ready for your first take" : "Room for another idea", Math.max(46, w / 2 - 100), blankY + 28 + shift);
        g.setFont(BODY.deriveFont(10f)); g.setColor(MUTED); g.drawString("Press Record to capture your inputs", Math.max(46, w / 2 - 100), blankY + 47 + shift);
        if (!tracks.isEmpty()) {
            int x = INSET + (int) ((controller.session().cursor() - viewStart) / viewSeconds * usable);
            g.setColor(controller.state() == StudioController.State.RECORDING ? CORAL : MINT); g.setStroke(new BasicStroke(1.3f));
            g.drawLine(x, 30, x, TOP + tracks.size() * STRIDE - 15);
            Path2D marker = new Path2D.Double(); marker.moveTo(x - 5, 25); marker.lineTo(x + 5, 25); marker.lineTo(x + 5, 32); marker.lineTo(x, 37); marker.lineTo(x - 5, 32); marker.closePath(); g.fill(marker);
        }
        g.dispose();
    }
    private void paintTrack(Graphics2D g, AudioTrack track, int index, int usable) {
        int y = TOP + index * STRIDE, w = getWidth(); Color tint = index % 2 == 0 ? MINT : LILAC;
        g.setColor(index == activeTrack ? color("202928") : color("1D2228")); g.fillRoundRect(0, y, w - 1, HEIGHT, 15, 15);
        g.setColor(index == activeTrack ? color("5D8071") : color("333944")); g.drawRoundRect(1, y, w - 3, HEIGHT, 15, 15);
        g.setColor(tint); g.fillRoundRect(14, y + 17, 4, 17, 4, 4);
        g.setFont(BODY.deriveFont(Font.BOLD, 12)); g.setColor(track.muted() ? MUTED : TEXT); g.drawString(ellipsize(g, track.name(), w - 210), 27, y + 29);
        g.setFont(BODY.deriveFont(8f)); g.setColor(track.recording() ? CORAL : MUTED); g.drawString(track.recording() ? "●  RECORDING" : "48 kHz  /  WAV", w - 130, y + 28);
        drawIcon(g, track.muted() ? "muted" : "speaker", w - 34, y + 16, 18, track.muted() ? CORAL : MUTED);
        g.setColor(alpha(tint, 28)); g.drawLine(INSET, y + 86, w - INSET, y + 86);
        int bars = Math.max(1, usable / 3);
        float[] peaks = track.waveform().view(viewStart - track.start(), viewSeconds / bars, bars);
        g.setColor(alpha(tint, track.muted() ? 40 : 210));
        for (int i = 0; i < peaks.length; i++) {
            if (peaks[i] <= 0) continue;
            int height = Math.max(1, Math.round(peaks[i] * 36));
            g.fillRoundRect(INSET + i * 3, y + 86 - height, 2, height * 2, 2, 2);
        }
        g.setFont(BODY.deriveFont(9f)); g.setColor(MUTED); g.drawString(ellipsize(g, track.source(), w - 140), 17, y + 136);
        g.setColor(tint); String duration = String.format(Locale.ROOT, "%.1f sec", track.duration()); g.drawString(duration, w - 18 - g.getFontMetrics().stringWidth(duration), y + 136);
    }
    public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 22; }
    public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) { return Math.max(100, visible.height - 40); }
    public boolean getScrollableTracksViewportWidth() { return true; }
    public boolean getScrollableTracksViewportHeight() { return false; }
}
