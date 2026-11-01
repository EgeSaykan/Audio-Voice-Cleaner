package studio.wave;

import studio.wave.app.StudioController;
import studio.wave.ui.StudioFrame;
import studio.wave.ui.Theme;
import javax.swing.SwingUtilities;

/** Application entry point. Audio, state, persistence and views live in their own packages. */
public final class WaveStudio {
    private WaveStudio() { }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Theme.applyTheme();
            StudioController controller = new StudioController();
            StudioFrame window = new StudioFrame(controller);
            window.setVisible(true);
            controller.discoverDevices();
        });
    }
}
