package studio.wave.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import studio.wave.AudioFixtures;
import studio.wave.model.AudioTrack;
import javax.swing.SwingUtilities;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class CombinedRecordingTest {
    @TempDir Path folder;
    @Test void combinesOffEventThreadAndMutesOnlyTheSelectedTracks() throws Exception {
        byte[] pcm = AudioFixtures.tone(2, 337, .2);
        // A chirp makes the delay unambiguous instead of a periodic test tone.
        for (int i = 0; i < pcm.length / 2; i++) {
            double t = i / 48000.0; int value = (int) (5000 * Math.sin(2 * Math.PI * (200 * t + 200 * t * t)));
            pcm[2 * i] = (byte) value; pcm[2 * i + 1] = (byte) (value >> 8);
        }
        AudioTrack a = AudioFixtures.track(folder.resolve("a.wav"), pcm, 3);
        AudioTrack b = AudioFixtures.track(folder.resolve("b.wav"), pcm, 3);
        AudioTrack untouched = AudioFixtures.track(folder.resolve("other.wav"), pcm, 0);
        StudioController controller = new StudioController(); CountDownLatch done = new CountDownLatch(1), closed = new CountDownLatch(1);
        AtomicReference<String> error = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                controller.setListeners(() -> {}, () -> {}, e -> { error.set(e); done.countDown(); });
                controller.session().setFolder(folder); controller.session().tracks().addAll(List.of(a, b, untouched));
                controller.combineRecordings(List.of(a.file(), b.file()), result -> {
                    assertTrue(SwingUtilities.isEventDispatchThread()); done.countDown();
                });
                assertEquals(StudioController.State.WORKING, controller.state());
            });
            assertTrue(done.await(15, TimeUnit.SECONDS)); assertNull(error.get());
            SwingUtilities.invokeAndWait(() -> {
                assertEquals(StudioController.State.READY, controller.state());
                assertEquals(4, controller.session().tracks().size());
                assertTrue(a.muted()); assertTrue(b.muted()); assertFalse(untouched.muted());
                AudioTrack output = controller.session().tracks().getLast();
                assertFalse(output.muted()); assertEquals(3, output.start()); assertEquals(2, output.duration(), .001);
                assertEquals(3, controller.session().cursor()); assertTrue(controller.files().contains(output.file()));
            });
        } finally {
            SwingUtilities.invokeAndWait(() -> controller.close(closed::countDown)); assertTrue(closed.await(5, TimeUnit.SECONDS));
        }
    }
}
