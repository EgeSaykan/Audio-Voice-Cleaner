package studio.wave.model;

import org.junit.jupiter.api.Test;
import studio.wave.AudioFixtures;
import static org.junit.jupiter.api.Assertions.*;

class WaveformTest {
    @Test void silenceStaysFlat() {
        Waveform waveform = new Waveform(); waveform.append(new byte[96000], 0, 96000);
        assertEquals(1, waveform.duration()); assertEquals(0, waveform.maximum());
        for (float peak : waveform.view(0, .01, 100)) assertEquals(0, peak);
    }
    @Test void signedSamplesProduceActualPeakLevelsAndDurations() {
        Waveform waveform = new Waveform(); byte[] pcm = AudioFixtures.constant(-16384, 481); waveform.append(pcm, 0, pcm.length);
        assertEquals(.5f, waveform.maximum()); assertEquals(.5f, waveform.level()); assertEquals(481, waveform.frames());
        assertEquals(.5f, waveform.view(0, .01, 3)[0]); assertEquals(.5f, waveform.view(0, .01, 3)[1]); assertEquals(0, waveform.view(0, .01, 3)[2]);
    }
    @Test void audioOutsideTheClipIsNotInvented() {
        Waveform waveform = new Waveform(); byte[] pcm = AudioFixtures.constant(16384, 480); waveform.append(pcm, 0, pcm.length);
        assertArrayEquals(new float[]{0, .5f, 0}, waveform.view(-.01, .01, 3));
        assertThrows(IllegalArgumentException.class, () -> waveform.append(new byte[3], 0, 3));
    }
}
