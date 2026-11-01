package studio.wave.ui;

import studio.wave.app.StudioController;
import studio.wave.ui.components.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Locale;
import static studio.wave.ui.Theme.*;

public final class TransportPanel extends JPanel {
    private final StudioController controller;
    private final JLabel time = label("00:00.00", 28, TEXT, Font.PLAIN), state = label("READY WHEN YOU ARE", 10, MUTED, Font.BOLD), status = label("", 11, MUTED, Font.PLAIN);
    private final IconButton play = new IconButton("play", "Play / pause (Space)"), reset = new IconButton("rewind", "Return to start (Home)");
    private final ActionButton record = new ActionButton("Record", "record", CORAL, BG), save = new ActionButton("Save session", "save", RAISED, TEXT);

    public TransportPanel(StudioController controller, Runnable onRecord, Runnable onSave) {
        super(new BorderLayout()); this.controller = controller; setBackground(PANEL); setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, LINE));
        JPanel transport = new JPanel(new BorderLayout(20, 0)); transport.setOpaque(false); transport.setBorder(new EmptyBorder(19, 28, 19, 28));
        JPanel clock = vertical(PANEL); clock.setPreferredSize(new Dimension(228, 66)); clock.add(state); clock.add(gap(7)); clock.add(time); transport.add(clock, BorderLayout.WEST);
        JPanel controls = row(PANEL, 12); reset.setPreferredSize(new Dimension(38, 44)); play.setPreferredSize(new Dimension(44, 44)); play.setButtonBackground(RAISED);
        record.setPreferredSize(new Dimension(133, 46)); save.setPreferredSize(new Dimension(147, 46));
        record.setName("record"); save.setName("save"); play.setName("play"); reset.setName("reset");
        record.addActionListener(e -> onRecord.run()); save.addActionListener(e -> onSave.run()); play.addActionListener(e -> controller.togglePlayback()); reset.addActionListener(e -> controller.seek(0));
        controls.add(reset); controls.add(play); controls.add(Box.createHorizontalStrut(3)); controls.add(record); controls.add(save);
        JPanel center = new JPanel(new GridBagLayout()); center.setOpaque(false); center.add(controls); transport.add(center, BorderLayout.CENTER);
        JPanel meta = vertical(PANEL); meta.setPreferredSize(new Dimension(222, 55));
        JLabel a = label("48 kHz  /  16-bit PCM  /  Mono", 11, TEXT, Font.PLAIN), b = label("One WAV file per selected input", 10, MUTED, Font.PLAIN);
        a.setAlignmentX(1); b.setAlignmentX(1); meta.add(gap(11)); meta.add(a); meta.add(gap(8)); meta.add(b); transport.add(meta, BorderLayout.EAST); add(transport, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout()); footer.setBackground(BG); footer.setBorder(new EmptyBorder(9, 26, 9, 26));
        JPanel left = new JPanel(new BorderLayout(8, 0)); left.setOpaque(false); left.add(new Dot(MINT, 5), BorderLayout.WEST); left.add(status, BorderLayout.CENTER); footer.add(left, BorderLayout.CENTER);
        footer.add(label("WAVE STUDIO    /    PIPEWIRE AUDIO", 8, DIM, Font.BOLD), BorderLayout.EAST); add(footer, BorderLayout.SOUTH); refresh();
    }
    public void refresh() {
        boolean recording = controller.state() == StudioController.State.RECORDING, playing = controller.state() == StudioController.State.PLAYING;
        record.setText(recording ? "Stop" : controller.state() == StudioController.State.STARTING ? "Opening…" : controller.state() == StudioController.State.STOPPING ? "Saving…" : "Record");
        record.setIconName(recording ? "stop" : "record"); record.setEnabled(recording || controller.editable() && !controller.session().inputs().isEmpty());
        record.setToolTipText(recording ? "Stop and finalize the WAV files (R)" : "Record the selected input devices (R)");
        play.setIconName(playing ? "pause" : "play"); play.setEnabled(controller.editable() && !controller.session().tracks().isEmpty() && !controller.session().outputs().isEmpty());
        reset.setEnabled(controller.editable()); save.setEnabled(controller.editable() && !controller.session().tracks().isEmpty());
        state.setText(recording ? "RECORDING LIVE INPUT" : playing ? "PLAYING YOUR RECORDINGS" : controller.busy() ? "WORKING…" : "READY WHEN YOU ARE");
        state.setForeground(recording ? CORAL : MUTED); time.setForeground(recording ? CORAL : TEXT);
        status.setText(controller.status()); status.setToolTipText(controller.status()); tick();
    }
    public void tick() {
        long hundredths = Math.round(controller.session().cursor() * 100);
        time.setText(String.format(Locale.ROOT, "%02d:%02d.%02d", hundredths / 6000, hundredths / 100 % 60, hundredths % 100));
    }
}
