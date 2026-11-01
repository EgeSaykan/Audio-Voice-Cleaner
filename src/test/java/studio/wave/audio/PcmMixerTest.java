package studio.wave.audio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import studio.wave.AudioFixtures;
import studio.wave.model.AudioTrack;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PcmMixerTest {
    @TempDir Path folder;
    @Test void mixesOffsetsMuteSeekAndClippingFromRealWavSamples() throws Exception {
        AudioTrack first = AudioFixtures.track(folder.resolve("one.wav"), AudioFixtures.constant(20000, 4), 0);
        AudioTrack second = AudioFixtures.track(folder.resolve("two.wav"), AudioFixtures.constant(20000, 4), 2 / 48000.0);
        byte[] data = new byte[12];
        try (PcmMixer mixer = new PcmMixer(List.of(first, second), 0)) {
            assertEquals(12, mixer.read(data)); assertArrayEquals(new int[]{20000, 20000, 32767, 32767, 20000, 20000}, samples(data)); assertEquals(-1, mixer.read(data));
        }
        first.setMuted(true);
        try (PcmMixer mixer = new PcmMixer(List.of(first, second), 0)) { mixer.read(data); assertArrayEquals(new int[]{0, 0, 20000, 20000, 20000, 20000}, samples(data)); }
        try (PcmMixer mixer = new PcmMixer(List.of(first, second), 2 / 48000.0)) { assertEquals(8, mixer.read(data)); assertEquals(20000, samples(data)[0]); }
    }
    private static int[] samples(byte[] data) { int[] values = new int[data.length / 2]; for (int i = 0; i < values.length; i++) values[i] = (short) ((data[2 * i] & 255) | data[2 * i + 1] << 8); return values; }
}
