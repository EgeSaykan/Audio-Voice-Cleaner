package studio.wave.ui;

import studio.wave.ui.components.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import static studio.wave.ui.Theme.*;

public final class HeaderPanel extends JPanel {
    public HeaderPanel(Runnable openCoach, Runnable chooseFolder) {
        this(openCoach, chooseFolder, () -> {});
    }
    public HeaderPanel(Runnable openCoach, Runnable chooseFolder, Runnable combine) {
        super(new BorderLayout(20, 0)); setBackground(PANEL);
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, LINE), new EmptyBorder(17, 25, 17, 25)));
        JPanel brand = row(PANEL, 11); brand.add(new JLabel(new ImageIcon(logoImage().getScaledInstance(36, 36, Image.SCALE_SMOOTH))));
        JPanel names = vertical(PANEL); names.add(label("wave studio", 19, TEXT, Font.BOLD)); names.add(gap(3)); names.add(label("MAKE ROOM FOR YOUR VOICE", 8, MUTED, Font.BOLD)); brand.add(names); add(brand, BorderLayout.WEST);
        JMenuBar navigation = new JMenuBar(); navigation.setOpaque(false); navigation.setBorder(BorderFactory.createEmptyBorder());
        JMenu workspace = new JMenu("Workspace ▾"); workspace.setName("workspaceMenu");
        workspace.setFont(BODY.deriveFont(12f)); workspace.setForeground(TEXT);
        workspace.setToolTipText("Open voice editing tools or choose a recording folder");
        JMenuItem coach = new JMenuItem("Voice Impact Coach (EditX)…"); coach.setName("workspaceCoach");
        coach.addActionListener(e -> openCoach.run()); workspace.add(coach);
        JMenuItem folder = new JMenuItem("Choose recording folder…"); folder.setName("workspaceFolder");
        folder.addActionListener(e -> chooseFolder.run()); workspace.add(folder);
        JMenuItem combined = new JMenuItem("Combine recordings…"); combined.setName("workspaceCombine");
        combined.addActionListener(e -> combine.run()); workspace.add(combined); navigation.add(workspace);
        JPanel context = row(PANEL, 12); context.add(new Glyph("grid", MUTED, 16)); context.add(navigation); context.add(label("/", 15, DIM, Font.PLAIN)); context.add(label("Voice session", 12, TEXT, Font.PLAIN));
        JPanel center = new JPanel(new GridBagLayout()); center.setOpaque(false); center.add(context); add(center, BorderLayout.CENTER);
        JPanel actions = row(PANEL, 12); actions.add(new Badge("PIPEWIRE", MINT, color("26382F")));
        IconButton info = new IconButton("info", "About Wave Studio");
        info.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "Wave Studio\n\nSelect microphones under Inputs and playback devices under Outputs.\n"
                + "Record captures real input audio into separate 48 kHz / 16-bit mono WAV files.\n"
                + "The waveform and meters are derived from those audio samples.\n"
                + "Play mixes the timeline into the selected output devices.\nSave session stores the WAV references and timeline settings.\n\n"
                + "Space · play/pause    R · record/stop    Ctrl+S · save    Ctrl+O · choose folder", "Wave Studio", JOptionPane.INFORMATION_MESSAGE));
        actions.add(info); add(actions, BorderLayout.EAST);
    }
}
