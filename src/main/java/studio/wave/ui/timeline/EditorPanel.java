package studio.wave.ui.timeline;

import studio.wave.app.StudioController;
import studio.wave.ui.components.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import static studio.wave.ui.Theme.*;

public final class EditorPanel extends JPanel {
    private final StudioController controller;
    private final JLabel title = label("Untitled session", 27, TEXT, Font.BOLD), trackCount = label("0 tracks", 12, MUTED, Font.PLAIN);
    private final TimelinePanel timeline;

    public EditorPanel(StudioController controller, Runnable record) {
        super(new BorderLayout(0, 17)); this.controller = controller; timeline = new TimelinePanel(controller, record);
        setBackground(BG); setBorder(new EmptyBorder(30, 28, 22, 28));
        JPanel top = vertical(BG); top.add(label("THE RECORDING ROOM", 9, MINT, Font.BOLD)); top.add(gap(12));
        JPanel heading = new JPanel(new BorderLayout(10, 0)); heading.setOpaque(false); heading.setAlignmentX(0); heading.add(title, BorderLayout.CENTER);
        IconButton rename = new IconButton("edit", "Rename session"); rename.addActionListener(e -> {
            if (!controller.editable()) return;
            controller.rename((String) JOptionPane.showInputDialog(this, "Give this session a name", "Rename session", JOptionPane.PLAIN_MESSAGE, null, null, controller.session().name()));
        });
        heading.add(rename, BorderLayout.EAST); top.add(heading); top.add(gap(7));
        top.add(label("Every idea starts with a sound. Make yours heard.", 12, MUTED, Font.PLAIN)); top.add(gap(20));
        JPanel tools = new JPanel(new BorderLayout()); tools.setOpaque(false); tools.setAlignmentX(0);
        JPanel tabs = row(BG, 11); tabs.add(new Glyph("wave", MINT, 18)); tabs.add(label("Timeline", 13, TEXT, Font.BOLD)); tabs.add(label("/", 14, DIM, Font.PLAIN)); tabs.add(trackCount); tools.add(tabs, BorderLayout.WEST);
        JPanel zoom = row(BG, 5); IconButton out = new IconButton("minus", "Zoom out"), in = new IconButton("plus", "Zoom in");
        out.setPreferredSize(new Dimension(29, 29)); in.setPreferredSize(new Dimension(29, 29));
        JLabel scale = label("30s", 10, MUTED, Font.PLAIN); scale.setPreferredSize(new Dimension(38, 20)); scale.setHorizontalAlignment(SwingConstants.CENTER);
        out.addActionListener(e -> { timeline.zoom(1.5); scale.setText((int) timeline.viewSeconds() + "s"); });
        in.addActionListener(e -> { timeline.zoom(1 / 1.5); scale.setText((int) timeline.viewSeconds() + "s"); });
        zoom.add(new Glyph("search", DIM, 13)); zoom.add(out); zoom.add(scale); zoom.add(in); tools.add(zoom, BorderLayout.EAST); top.add(tools);
        add(top, BorderLayout.NORTH); add(scroll(timeline, BG), BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout()); footer.setOpaque(false);
        footer.add(label("Click to seek · Shift + scroll to pan", 10, DIM, Font.PLAIN), BorderLayout.WEST); footer.add(label("Space  play / pause", 10, DIM, Font.PLAIN), BorderLayout.EAST); add(footer, BorderLayout.SOUTH);
    }
    public void refresh() {
        title.setText(controller.session().name());
        int count = controller.session().tracks().size();
        trackCount.setText(count + (count == 1 ? " track" : " tracks"));
        timeline.refresh();
    }
    public void tick() { timeline.ensureCursorVisible(); timeline.repaint(); }
}
