package studio.wave.ui;

import studio.wave.app.StudioController;
import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Select explicit simultaneous takes; never silently combines unrelated library recordings. */
public final class CombineRecordingsDialog {
    private CombineRecordingsDialog() { }
    public static void open(Component owner, StudioController controller) {
        if (!controller.editable()) return;
        List<Path> candidates = controller.files().stream()
                .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".wav")).toList();
        if (candidates.size() < 2) {
            JOptionPane.showMessageDialog(owner, "Choose a folder with at least two WAV recordings of the same take, or record with multiple inputs first.",
                    "Combine recordings", JOptionPane.INFORMATION_MESSAGE); return;
        }
        JList<Path> list = new JList<>(candidates.toArray(Path[]::new));
        list.setName("combineSources"); list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> view, Object value, int index, boolean selected, boolean focus) {
                return super.getListCellRendererComponent(view, ((Path) value).getFileName().toString(), index, selected, focus);
            }
        });
        if (candidates.size() == 2) list.setSelectionInterval(0, 1);
        else for (int i = 0; i < candidates.size(); i++) if (controller.lastRecordingFiles().contains(candidates.get(i))) list.addSelectionInterval(i, i);
        JPanel panel = new JPanel(new BorderLayout(8, 12));
        panel.add(new JLabel("<html>Select 2–8 microphones that captured the same speech at the same time.<br>"
                + "Timing, microphone quality and noise are compared to create one clearer take.<br>"
                + "Original WAVs are kept. Source timeline tracks will be muted.<br>"
                + "48 kHz mono WAV · up to 10 minutes total across inputs · no AI models</html>"), BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(list); scroll.setPreferredSize(new Dimension(650, 200)); panel.add(scroll);
        if (JOptionPane.showConfirmDialog(owner, panel, "Combine recordings", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        List<Path> selected = list.getSelectedValuesList();
        if (selected.size() < 2 || selected.size() > 8) {
            JOptionPane.showMessageDialog(owner, "Select between 2 and 8 recordings."); return;
        }
        controller.combineRecordings(selected, result -> JOptionPane.showMessageDialog(owner,
                "Combined voice added to the timeline. Press Play to listen.\n\nSaved: " + result.audio().getFileName()
                        + "\nAlignment and microphone contributions: " + result.report().getFileName(), "Combined voice ready", JOptionPane.INFORMATION_MESSAGE));
    }
}
