package studio.wave.ui.library;

import studio.wave.app.StudioController;
import studio.wave.ui.components.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import static studio.wave.ui.Theme.*;

public final class LibraryPanel extends JPanel {
    private final StudioController controller;
    private final Runnable chooseFolder;
    private final JPanel body = vertical(PANEL);
    private final JLabel caption = label("YOUR LOCAL LIBRARY", 10, MUTED, Font.BOLD);
    private final IconButton folderButton = new IconButton("folder", "Choose recording folder (Ctrl+O)");
    private Path displayedFolder;
    private List<Path> displayedFiles;
    private String query = "";

    public LibraryPanel(StudioController controller, Runnable chooseFolder) {
        super(new BorderLayout(0, 23)); this.controller = controller; this.chooseFolder = chooseFolder;
        setPreferredSize(new Dimension(286, 400)); setBackground(PANEL);
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, LINE), new EmptyBorder(26, 21, 22, 21)));
        JPanel heading = new JPanel(new BorderLayout()); heading.setOpaque(false);
        JPanel text = vertical(PANEL); text.add(label("Recordings", 16, TEXT, Font.BOLD)); text.add(gap(9)); text.add(caption);
        heading.add(text, BorderLayout.CENTER); folderButton.addActionListener(e -> chooseFolder.run()); heading.add(folderButton, BorderLayout.EAST);
        add(heading, BorderLayout.NORTH); add(body, BorderLayout.CENTER);
        JPanel footer = row(PANEL, 7); footer.add(new Glyph("lock", DIM, 12)); footer.add(label("Your files stay on this device", 10, DIM, Font.PLAIN)); add(footer, BorderLayout.SOUTH);
        refresh();
    }
    public void refresh() {
        folderButton.setEnabled(controller.editable());
        Path folder = controller.session().folder(); List<Path> files = controller.files();
        if (Objects.equals(displayedFolder, folder) && Objects.equals(displayedFiles, files)) return;
        if (!Objects.equals(displayedFolder, folder)) query = "";
        displayedFolder = folder; displayedFiles = files; body.removeAll();
        if (folder == null) empty(); else populated(folder, files);
        body.revalidate(); body.repaint();
    }
    private void empty() {
        caption.setText("YOUR LOCAL LIBRARY"); body.add(Box.createVerticalGlue());
        FolderArt art = new FolderArt(); art.setAlignmentX(.5f); body.add(art); body.add(gap(23));
        center("A home for your recordings", 13, TEXT, Font.BOLD); body.add(gap(11));
        center("Choose a folder for your audio.", 11, MUTED, Font.PLAIN); body.add(gap(5));
        center("Every take is saved as a real WAV.", 11, MUTED, Font.PLAIN); body.add(gap(24));
        ActionButton select = new ActionButton("Choose a folder", "folder", MINT, BG); select.setAlignmentX(.5f); select.addActionListener(e -> chooseFolder.run()); body.add(select); body.add(gap(17));
        center("WAV, MP3, FLAC + more", 9, DIM, Font.PLAIN); body.add(Box.createVerticalGlue());
    }
    private void center(String text, int size, Color color, int weight) { JLabel label = label(text, size, color, weight); label.setAlignmentX(.5f); body.add(label); }
    private void populated(Path folder, List<Path> files) {
        caption.setText(files.size() + " AUDIO FILE" + (files.size() == 1 ? "" : "S"));
        RoundedPanel path = new RoundedPanel(CARD, 12); path.setLayout(new BorderLayout(8, 0)); path.setBorder(new EmptyBorder(11, 11, 11, 8));
        path.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48)); path.setAlignmentX(0); path.add(new Glyph("folder", MINT, 17), BorderLayout.WEST);
        JLabel name = label(folder.getFileName() == null ? folder.toString() : folder.getFileName().toString(), 11, TEXT, Font.BOLD); name.setToolTipText(folder.toString()); path.add(name, BorderLayout.CENTER);
        IconButton refresh = new IconButton("refresh", "Refresh recording folder"); refresh.setPreferredSize(new Dimension(25, 25)); refresh.addActionListener(e -> controller.refreshLibrary()); path.add(refresh, BorderLayout.EAST); body.add(path); body.add(gap(14));
        RoundedPanel search = new RoundedPanel(BG, 10); search.setLayout(new BorderLayout(9, 0)); search.setBorder(new EmptyBorder(10, 11, 10, 11));
        search.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40)); search.setAlignmentX(0); search.add(new Glyph("search", MUTED, 15), BorderLayout.WEST);
        JTextField field = new JTextField(query) {
            @Override protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                if (getText().isEmpty()) { Graphics2D g = graphics(graphics); g.setFont(getFont()); g.setColor(DIM); g.drawString("Search recordings…", 0, (getHeight() - g.getFontMetrics().getHeight()) / 2 + g.getFontMetrics().getAscent()); g.dispose(); }
            }
        };
        field.setName("recordingSearch"); field.setFont(BODY.deriveFont(11f)); field.setBorder(null); field.setOpaque(false); field.getAccessibleContext().setAccessibleName("Search recordings");
        search.add(field, BorderLayout.CENTER); body.add(search); body.add(gap(13));
        JPanel list = vertical(PANEL);
        Runnable filter = () -> {
            query = field.getText(); list.removeAll();
            List<Path> filtered = files.stream().filter(file -> file.getFileName().toString().toLowerCase(java.util.Locale.ROOT).contains(query.toLowerCase(java.util.Locale.ROOT))).toList();
            for (Path file : filtered) { list.add(new RecordingFileCard(file, () -> controller.importFile(file))); list.add(gap(8)); }
            if (filtered.isEmpty()) { list.add(gap(24)); list.add(label(files.isEmpty() ? "Your folder is ready." : "No matching recordings", 12, TEXT, Font.BOLD)); list.add(gap(9)); list.add(label(files.isEmpty() ? "Press Record to capture your first take." : "Try a different filename.", 10, MUTED, Font.PLAIN)); }
            list.revalidate(); list.repaint();
        };
        field.getDocument().addDocumentListener(new DocumentListener() { public void insertUpdate(DocumentEvent e) { filter.run(); } public void removeUpdate(DocumentEvent e) { filter.run(); } public void changedUpdate(DocumentEvent e) { filter.run(); } });
        filter.run(); JScrollPane scrolling = scroll(list, PANEL); scrolling.setAlignmentX(0); body.add(scrolling); body.add(gap(13));
        body.add(label("Click a recording to add it to the timeline.", 9, DIM, Font.PLAIN)); body.add(gap(14));
        ActionButton change = new ActionButton("Change folder", "folder", RAISED, TEXT); change.setAlignmentX(0); change.addActionListener(e -> chooseFolder.run()); body.add(change);
    }
}
