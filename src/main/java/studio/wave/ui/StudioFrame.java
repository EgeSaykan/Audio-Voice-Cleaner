package studio.wave.ui;

import studio.wave.app.StudioController;
import studio.wave.model.AudioDevice;
import studio.wave.ui.devices.*;
import studio.wave.ui.coach.VoiceCoachDialog;
import studio.wave.ui.library.LibraryPanel;
import studio.wave.ui.timeline.EditorPanel;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.Path;
import static studio.wave.ui.Theme.*;

/** Window composition and user dialogs. All operations are delegated to the controller. */
public final class StudioFrame extends JFrame {
    private final StudioController controller;
    private final DeviceSidebar devices;
    private final LibraryPanel library;
    private final EditorPanel editor;
    private final TransportPanel transport;
    private final Timer timer;

    public StudioFrame(StudioController controller) {
        super("Wave Studio — Audio Regulator"); this.controller = controller;
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE); setMinimumSize(new Dimension(1120, 730));
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setSize(Math.min(1510, screen.width - 70), Math.min(940, screen.height - 70)); setLocationRelativeTo(null); setIconImage(logoImage());
        devices = new DeviceSidebar(controller, this::showDevices); library = new LibraryPanel(controller, () -> chooseFolder(null));
        editor = new EditorPanel(controller, this::record); transport = new TransportPanel(controller, this::record, this::save);
        JPanel root = new JPanel(new BorderLayout()); root.setBackground(BG); root.add(new HeaderPanel(this::openCoach, () -> chooseFolder(null), this::combine), BorderLayout.NORTH);
        JPanel workspace = new JPanel(new BorderLayout()); workspace.setOpaque(false); workspace.add(devices, BorderLayout.WEST); workspace.add(library, BorderLayout.EAST); workspace.add(editor, BorderLayout.CENTER);
        root.add(workspace, BorderLayout.CENTER); root.add(transport, BorderLayout.SOUTH); setContentPane(root);
        controller.setListeners(this::refresh, this::tick, message -> JOptionPane.showMessageDialog(this, message, "Audio operation", JOptionPane.ERROR_MESSAGE));
        timer = new Timer(40, e -> controller.tick()); timer.start();
        addWindowListener(new WindowAdapter() { public void windowClosing(WindowEvent e) { close(); } public void windowClosed(WindowEvent e) { timer.stop(); } });
        bind("SPACE", "play", controller::togglePlayback); bind("R", "record", this::record); bind("control S", "save", this::save);
        bind("HOME", "home", () -> controller.seek(0)); bind("control O", "folder", () -> chooseFolder(null)); refresh();
    }
    public void close() { controller.close(() -> { timer.stop(); dispose(); }); }
    private void combine() {
        if (!controller.editable()) return;
        if (controller.session().folder() == null) { chooseFolder(this::combine); return; }
        CombineRecordingsDialog.open(this, controller);
    }
    private void refresh() { devices.refresh(); library.refresh(); editor.refresh(); transport.refresh(); }
    private void tick() { devices.refreshLevels(); editor.tick(); transport.tick(); }
    private void openCoach() {
        if (!controller.editable()) {
            JOptionPane.showMessageDialog(this, "Stop recording or wait for the current operation to finish, then open EditX.", "Voice Impact Coach", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (controller.session().folder() == null) { chooseFolder(this::openCoach); return; }
        if (controller.files().isEmpty()) {
            JOptionPane.showMessageDialog(this, "This folder has no supported audio files. Record a take or choose a folder containing audio, then open EditX.", "Voice Impact Coach", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        VoiceCoachDialog.open(getRootPane(), controller);
    }
    private void record() {
        if (controller.state() == StudioController.State.RECORDING) { controller.stopRecording(); return; }
        if (!controller.editable()) return;
        if (controller.session().inputs().isEmpty()) { showDevices(AudioDevice.Type.INPUT); return; }
        if (controller.session().folder() == null) chooseFolder(controller::startRecording); else controller.startRecording();
    }
    private void save() {
        if (!controller.editable() || controller.session().tracks().isEmpty()) return;
        if (controller.session().folder() == null) chooseFolder(controller::save); else controller.save();
    }
    private void showDevices(AudioDevice.Type type) {
        if (!controller.editable()) return;
        new DevicePickerDialog(this, type, controller.devices(type), type == AudioDevice.Type.INPUT ? controller.session().inputs() : controller.session().outputs(), selection -> controller.selectDevices(type, selection)).setVisible(true);
    }
    private void chooseFolder(Runnable then) {
        if (!controller.editable()) return;
        Path folder = controller.session().folder(); JFileChooser chooser = new JFileChooser(folder == null ? System.getProperty("user.home") : folder.toString());
        chooser.setDialogTitle("Choose a folder for your recordings"); chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY); chooser.setAcceptAllFileFilterUsed(false); chooser.setApproveButtonText("Choose folder");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) controller.selectFolder(chooser.getSelectedFile().toPath(), then);
    }
    private void bind(String key, String name, Runnable action) {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), name);
        getRootPane().getActionMap().put(name, new AbstractAction() { public void actionPerformed(ActionEvent e) {
            Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            if (focus instanceof javax.swing.text.JTextComponent && (key.equals("R") || key.equals("SPACE") || key.equals("HOME"))) return;
            action.run();
        } });
    }
}
