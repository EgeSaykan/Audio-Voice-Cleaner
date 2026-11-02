package studio.wave.ui.coach;

import studio.wave.app.StudioController;
import studio.wave.audio.PlaybackService;
import studio.wave.coach.*;
import studio.wave.model.*;
import studio.wave.ui.Theme;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** All decoding, HTTP, file copies and audio device operations run off the event thread. */
public final class VoiceCoachDialog extends JDialog {
    private final VoiceImpactService service;
    private final List<Path> recordings;
    private final List<AudioDevice> outputs;
    private final JComboBox<String> files, preset = new JComboBox<>(new String[]{"More expressive", "More authoritative", "Warmer",
            "Better storyteller", "More energetic", "More conversational", "More composed"});
    private final JCheckBox section = new JCheckBox("Analyze a section");
    private final JSpinner start = new JSpinner(new SpinnerNumberModel(0d, 0d, 864000d, .5d)), length;
    private final JSpinner passes = new JSpinner(new SpinnerNumberModel(1, 1, 2, 1));
    private final JTextArea report = area(false), transcript = area(true), comparison = area(false);
    private final JTabbedPane tabs = new JTabbedPane();
    private final JLabel status = new JLabel(), hardware = new JLabel("Checking EditX…");
    private final JButton analyze = new JButton("Analyze delivery"), enhance = new JButton("Generate enhanced reference"),
            original = new JButton("Play original"), edited = new JButton("Play reference"),
            pause = new JButton("Pause"), replay = new JButton("Replay"), export = new JButton("Export WAV…"), retry = new JButton("Check EditX");
    private final JProgressBar progress = new JProgressBar();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(Thread.ofVirtual().factory());
    private final PlaybackService player = new PlaybackService();
    private VoiceImpactService.Take take;
    private VoiceImpactService.Reference reference;
    private boolean busy, ready, closing, playing;
    private Future<?> current;
    private Path lastPlayed;
    private double resumeAt;
    private long playbackGeneration;

    public static void open(Component owner, StudioController controller) {
        if (!controller.editable() || controller.files().isEmpty()) return;
        if (controller.state() == StudioController.State.PLAYING) controller.togglePlayback();
        try {
            var service = new VoiceImpactService(CoachConfig.load());
            new VoiceCoachDialog(SwingUtilities.getWindowAncestor(owner), controller, service).setVisible(true);
        } catch (Exception e) { JOptionPane.showMessageDialog(owner, e.getMessage(), "Voice Impact Coach", JOptionPane.ERROR_MESSAGE); }
    }
    private VoiceCoachDialog(Window owner, StudioController controller, VoiceImpactService service) {
        super(owner, "Voice Impact Coach", ModalityType.APPLICATION_MODAL);
        this.service = service;
        recordings = List.copyOf(controller.files()); outputs = controller.selected(AudioDevice.Type.OUTPUT);
        files = new JComboBox<>(recordings.stream().map(p -> p.getFileName().toString()).toArray(String[]::new));
        length = new JSpinner(new SpinnerNumberModel(service.clipLimit(), .01, service.clipLimit(), .5));
        var root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        var north = vertical();
        north.add(row(new JLabel("Recording"), files));
        north.add(row(section, new JLabel("Start (s)"), start, new JLabel("Length (s)"), length, analyze));
        section.setOpaque(false); section.setForeground(Theme.TEXT);
        analyze.setBackground(Theme.MINT); analyze.setForeground(Theme.BG);
        root.add(north, BorderLayout.NORTH);
        tabs.addTab("Delivery", new JScrollPane(report));
        tabs.addTab("Transcript · editable", new JScrollPane(transcript));
        tabs.addTab("Compare recordings", new JScrollPane(comparison));
        root.add(tabs);
        var footer = vertical();
        footer.add(row(new JLabel("Reference style"), preset, new JLabel("Passes"), passes, enhance));
        footer.add(row(original, edited, pause, replay, export));
        footer.add(row(hardware, retry));
        footer.add(row(status)); progress.setPreferredSize(new Dimension(100, 4)); footer.add(progress); root.add(footer, BorderLayout.SOUTH);
        setContentPane(root); setMinimumSize(new Dimension(720, 500)); setSize(820, 610); setLocationRelativeTo(owner);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() { @Override public void windowClosing(WindowEvent e) { closeCoach(); } });
        files.addActionListener(e -> invalidateTake()); section.addActionListener(e -> invalidateTake());
        start.addChangeListener(e -> invalidateTake()); length.addChangeListener(e -> invalidateTake());
        analyze.addActionListener(e -> analyze()); enhance.addActionListener(e -> enhance()); retry.addActionListener(e -> checkEditor());
        original.addActionListener(e -> play(take.playback(), false)); edited.addActionListener(e -> play(reference.audio(), false));
        pause.addActionListener(e -> pause()); replay.addActionListener(e -> play(lastPlayed, true));
        export.addActionListener(e -> export());
        report.setText("Choose a recording and click Analyze delivery. For a long recording, you can analyze the whole take or select a short section.");
        analyze.setName("analyzeDelivery"); report.setName("deliveryReport"); transcript.setName("coachTranscript");
        refresh(); checkEditor();
    }
    private static JTextArea area(boolean editable) {
        var text = new JTextArea(); text.setEditable(editable); text.setLineWrap(true); text.setWrapStyleWord(true);
        text.setBackground(Theme.CARD); text.setForeground(Theme.TEXT); text.setCaretColor(Theme.MINT);
        text.setFont(Theme.BODY); text.setMargin(new Insets(16, 16, 16, 16)); return text;
    }
    private static JPanel vertical() { var p = new JPanel(); p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS)); return p; }
    private static JPanel row(Component... items) { var p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3)); for (var c : items) p.add(c); return p; }

    private void refresh() {
        for (var component : List.of(files, section, analyze, preset, passes, transcript)) component.setEnabled(!busy);
        start.setEnabled(!busy && section.isSelected()); length.setEnabled(start.isEnabled());
        enhance.setEnabled(!busy && ready && take != null && take.seconds() <= service.clipLimit() + .0001);
        original.setEnabled(!busy && take != null); edited.setEnabled(!busy && reference != null);
        replay.setEnabled(!busy && lastPlayed != null); pause.setEnabled(!busy && playing);
        export.setEnabled(!busy && reference != null); progress.setIndeterminate(busy);
    }
    private void invalidateTake() {
        stopPreview(); take = null; reference = null; report.setText(""); transcript.setText(""); comparison.setText("");
        status.setText("Analyze the selected audio."); refresh();
    }
    private void checkEditor() {
        retry.setEnabled(false);
        Thread.ofVirtual().start(() -> {
            String error = null;
            try { service.editorReady(); } catch (Exception e) { error = e.getMessage(); }
            final String message = error;
            SwingUtilities.invokeLater(() -> {
                if (closing) return;
                ready = message == null;
                hardware.setText(ready ? "EditX ready" : "Enhancement unavailable");
                hardware.setToolTipText(message); if (!ready) status.setText(message);
                retry.setEnabled(true); refresh();
            });
        });
    }
    private void analyze() {
        Path file = recordings.get(files.getSelectedIndex());
        double from = section.isSelected() ? (double) start.getValue() : 0;
        Double seconds = section.isSelected() ? (Double) length.getValue() : null;
        invalidateTake();
        run(() -> service.analyze(file, from, seconds, this::progress), result -> {
            take = result; report.setText(take.report().report()); transcript.setText(take.report().transcript().text());
            report.setCaretPosition(0); transcript.setCaretPosition(0); tabs.setSelectedIndex(0);
            status.setText(take.seconds() > service.clipLimit() ? "Full take analyzed. Select and analyze a short section to generate a reference."
                    : "Analysis complete. Review the transcript before generating a reference.");
        });
    }
    private void enhance() {
        String text = transcript.getText(), style = (String) preset.getSelectedItem(); int iterations = (int) passes.getValue();
        var source = take; stopPreview();
        run(() -> service.enhance(source, text, style, iterations, this::progress), result -> {
            reference = result; comparison.setText(source.report().compare(result.report(), text) + "\n\nAI-modified reference measurements\n" + result.report().report() + "\n\nSaved to " + result.audio());
            comparison.setCaretPosition(0); tabs.setSelectedIndex(2); status.setText("AI-modified reference saved.");
        });
    }
    private void export() {
        var chooser = new JFileChooser(take.source().toAbsolutePath().getParent().toFile());
        chooser.setSelectedFile(new java.io.File("AI-enhanced-" + reference.audio().getParent().getFileName() + ".wav"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        var dest = chooser.getSelectedFile().toPath(); var audio = reference.audio();
        run(() -> Files.copy(audio, dest), saved -> status.setText("Exported " + saved));
    }
    private void play(Path file, boolean restart) {
        long generation = ++playbackGeneration;
        double offset = !restart && file.equals(lastPlayed) ? resumeAt : 0;
        lastPlayed = file; resumeAt = 0;
        run(() -> {
            var track = new AudioTrack("Coach preview", "", "", 0, file, new Waveform());
            player.start(List.of(track), outputs, offset, error -> SwingUtilities.invokeLater(() -> {
                if (!closing && generation == playbackGeneration) {
                    playing = false; resumeAt = 0; status.setText(error == null ? "Playback finished." : error.getMessage()); refresh();
                }
            }));
            return file;
        }, ignored -> { playing = true; status.setText("Playing " + (file.equals(take.playback()) ? "original." : "AI-modified reference.")); });
    }
    private void pause() {
        playbackGeneration++;
        run(() -> { double pos = player.position(); player.stop(); return pos; }, pos -> { playing = false; resumeAt = pos; status.setText("Playback paused."); });
    }
    private void stopPreview() {
        playbackGeneration++; playing = false; lastPlayed = null; resumeAt = 0;
        worker.submit(player::stop);
    }
    private void progress(String message) { SwingUtilities.invokeLater(() -> { if (!closing) status.setText(message); }); }
    private <T> void run(Callable<T> task, Consumer<T> success) {
        busy = true; refresh();
        current = worker.submit(() -> {
            try {
                T value = task.call(); SwingUtilities.invokeLater(() -> { if (!closing) success.accept(value); });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> { if (!closing) status.setText(e.getMessage()); });
            } finally {
                SwingUtilities.invokeLater(() -> { if (!closing) { busy = false; refresh(); } });
            }
        });
    }
    private void closeCoach() {
        closing = true;
        if (current != null) current.cancel(true);
        worker.submit(() -> { player.close(); service.close(); return null; });
        worker.shutdown(); dispose();
    }
}
