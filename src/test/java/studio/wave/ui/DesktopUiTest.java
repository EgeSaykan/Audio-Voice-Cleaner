package studio.wave.ui;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import studio.wave.AudioFixtures;
import studio.wave.app.StudioController;
import studio.wave.model.AudioDevice;
import studio.wave.ui.devices.*;
import studio.wave.ui.library.RecordingFileCard;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

@Tag("desktop")
class DesktopUiTest {
    @TempDir Path folder;
    private StudioController controller;
    private StudioFrame frame;

    @Test void desktopDialogsLibraryAndLayoutUseTheRealModel() throws Exception {
        Path pictures = Path.of(System.getProperty("wave.screenshots")); Files.createDirectories(pictures);
        Path audio = folder.resolve("Integration recording.wav"); AudioFixtures.track(audio, AudioFixtures.tone(3, 440, .5), 0);
        Files.writeString(folder.resolve("notes.txt"), "not audio");
        try {
            edt(() -> { Theme.applyTheme(); controller = new StudioController(); frame = new StudioFrame(controller); frame.setSize(1510, 940); frame.setVisible(true); controller.discoverDevices(); return null; });
            await(() -> !controller.busy());
            edt(() -> {
                assertTrue(controller.session().tracks().isEmpty(), "no fabricated demo recordings");
                assertFalse(controller.devices(AudioDevice.Type.INPUT).isEmpty());
                assertTrue(controller.devices(AudioDevice.Type.INPUT).stream().noneMatch(d -> d.id().endsWith(".monitor")));
                assertTrue(controller.devices(AudioDevice.Type.OUTPUT).stream().allMatch(d -> d.type() == AudioDevice.Type.OUTPUT));
                snapshot(frame.getContentPane(), pictures.resolve("workspace.png"));
                Set<String> before = Set.copyOf(controller.session().inputs());
                DevicePickerDialog inputs = new DevicePickerDialog(frame, AudioDevice.Type.INPUT, controller.devices(AudioDevice.Type.INPUT), before, ids -> controller.selectDevices(AudioDevice.Type.INPUT, ids));
                inputs.setModal(false); inputs.setVisible(true);
                List<DeviceChoice> choices = descendants(inputs, DeviceChoice.class);
                assertTrue(choices.stream().allMatch(c -> c.device().type() == AudioDevice.Type.INPUT));
                choices.stream().filter(c -> before.contains(c.device().id())).findFirst().orElseThrow().doClick();
                assertEquals(before, controller.session().inputs(), "pending clicks don't immediately commit");
                named(inputs, "applyDevices", JButton.class).doClick();
                assertTrue(controller.session().inputs().isEmpty()); assertFalse(named(frame, "record", JButton.class).isEnabled());
                controller.selectDevices(AudioDevice.Type.INPUT, before);
                DevicePickerDialog showInputs = new DevicePickerDialog(frame, AudioDevice.Type.INPUT, controller.devices(AudioDevice.Type.INPUT), before, ids -> {});
                showInputs.setModal(false); showInputs.setVisible(true); snapshot(showInputs.getContentPane(), pictures.resolve("inputs.png")); showInputs.dispose();
                DevicePickerDialog showOutputs = new DevicePickerDialog(frame, AudioDevice.Type.OUTPUT, controller.devices(AudioDevice.Type.OUTPUT), controller.session().outputs(), ids -> {});
                showOutputs.setModal(false); showOutputs.setVisible(true);
                assertTrue(descendants(showOutputs, DeviceChoice.class).stream().allMatch(c -> c.device().type() == AudioDevice.Type.OUTPUT));
                snapshot(showOutputs.getContentPane(), pictures.resolve("outputs.png")); showOutputs.dispose();
                controller.selectFolder(folder, null); return null;
            });
            await(() -> !controller.busy());
            edt(() -> {
                assertEquals(1, controller.files().size());
                JTextField search = named(frame, "recordingSearch", JTextField.class); search.setText("does-not-match");
                assertEquals(0, descendants(frame, RecordingFileCard.class).size()); search.setText("");
                assertEquals(1, descendants(frame, RecordingFileCard.class).size());
                descendants(frame, RecordingFileCard.class).getFirst().doClick(); return null;
            });
            await(() -> !controller.busy());
            edt(() -> {
                assertEquals(1, controller.session().tracks().size()); assertEquals(3, controller.session().tracks().getFirst().duration());
                controller.seek(1.25); assertEquals(1.25, controller.session().cursor());
                controller.mute(controller.session().tracks().getFirst()); assertTrue(controller.session().tracks().getFirst().muted());
                controller.mute(controller.session().tracks().getFirst()); controller.rename("Verified recording"); controller.save(); return null;
            });
            await(() -> !controller.busy());
            edt(() -> { frame.validate(); snapshot(frame.getContentPane(), pictures.resolve("recording.png")); frame.setSize(1120, 730); return null; });
            Thread.sleep(150);
            edt(() -> {
                frame.validate(); assertTrue(named(frame, "record", JButton.class).isShowing());
                assertTrue(named(frame, "save", JButton.class).isEnabled()); snapshot(frame.getContentPane(), pictures.resolve("compact.png")); return null;
            });
        } finally {
            if (frame != null) { edt(() -> { frame.close(); return null; }); await(() -> !frame.isDisplayable()); }
        }
    }
    @Test @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="WAVE_SPEECH_SAMPLE", matches=".+")
    void coachDialogAnalyzesSpeechFromTheRecordingLibrary() throws Exception {
        Files.copy(Path.of(System.getenv("WAVE_SPEECH_SAMPLE")), folder.resolve("Speech sample.wav"));
        try {
            edt(() -> {
                Theme.applyTheme(); controller = new StudioController(); frame = new StudioFrame(controller);
                frame.setVisible(true); controller.selectFolder(folder, null); return null;
            });
            await(() -> !controller.busy());
            SwingUtilities.invokeLater(() -> named(frame, "voiceCoach", JButton.class).doClick());
            await(() -> Arrays.stream(frame.getOwnedWindows()).anyMatch(w -> w instanceof JDialog && w.isShowing()));
            JDialog coach = edt(() -> (JDialog) Arrays.stream(frame.getOwnedWindows()).filter(Window::isShowing).findFirst().orElseThrow());
            try {
                edt(() -> { named(coach, "analyzeDelivery", JButton.class).doClick(); return null; });
                long deadline = System.nanoTime() + java.time.Duration.ofMinutes(2).toNanos();
                while (edt(() -> named(coach, "coachTranscript", JTextArea.class).getText().isBlank())) {
                    if (System.nanoTime() > deadline) fail("No transcript appeared in the coach dialog");
                    Thread.sleep(100);
                }
                edt(() -> {
                    assertTrue(named(coach, "deliveryReport", JTextArea.class).getText().contains("words/min"));
                    snapshot(coach.getContentPane(), Path.of(System.getProperty("wave.screenshots"), "voice-coach.png"));
                    return null;
                });
            } finally {
                edt(() -> { coach.dispatchEvent(new java.awt.event.WindowEvent(coach, java.awt.event.WindowEvent.WINDOW_CLOSING)); return null; });
            }
        } finally {
            if (frame != null) { edt(() -> { frame.close(); return null; }); await(() -> !frame.isDisplayable()); }
        }
    }
    private static <T> T edt(Callable<T> task) throws Exception {
        AtomicReference<T> value = new AtomicReference<>(); AtomicReference<Throwable> error = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> { try { value.set(task.call()); } catch (Throwable e) { error.set(e); } });
        if (error.get() != null) throw new AssertionError(error.get()); return value.get();
    }
    private static void await(Callable<Boolean> ready) throws Exception {
        long deadline = System.nanoTime() + 8_000_000_000L;
        while (!edt(ready)) { if (System.nanoTime() > deadline) fail("Timed out waiting for UI operation"); Thread.sleep(25); }
    }
    private static <T extends Component> List<T> descendants(Container parent, Class<T> type) {
        List<T> result = new ArrayList<>();
        for (Component c : parent.getComponents()) { if (type.isInstance(c)) result.add(type.cast(c)); if (c instanceof Container nested) result.addAll(descendants(nested, type)); }
        return result;
    }
    private static <T extends Component> T named(Container parent, String name, Class<T> type) { return descendants(parent, type).stream().filter(c -> name.equals(c.getName())).findFirst().orElseThrow(); }
    private static void snapshot(Component component, Path path) throws Exception {
        BufferedImage image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics(); component.printAll(g); g.dispose(); ImageIO.write(image, "png", path.toFile());
    }
}
