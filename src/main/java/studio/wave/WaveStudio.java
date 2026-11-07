package studio.wave;

import studio.wave.app.StudioController;
import studio.wave.ui.StudioFrame;
import studio.wave.ui.Theme;
import javax.swing.SwingUtilities;

/** Application entry point. Audio, state, persistence and views live in their own packages. */
public final class WaveStudio {
    private WaveStudio() { }

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--combine")) {
            try { studio.wave.audio.VoiceCombiner.main(java.util.Arrays.copyOfRange(args, 1, args.length)); }
            catch (java.io.IOException e) { System.err.println(e.getMessage()); System.exit(1); }
            return;
        }
        SwingUtilities.invokeLater(() -> {
            Theme.applyTheme();
            StudioController controller = new StudioController();
            StudioFrame window = new StudioFrame(controller);
            window.setVisible(true);
            controller.discoverDevices();
        });
    }
}
