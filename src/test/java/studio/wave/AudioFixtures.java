package studio.wave;

import studio.wave.audio.WavFiles;
import studio.wave.model.*;
import java.io.IOException;
import java.nio.file.Path;

/** Deterministic signals are confined to tests; production only reads actual device/file PCM. */
public final class AudioFixtures {
    public static byte[] tone(double seconds, double frequency, double amplitude) {
        int frames = (int) (seconds * Waveform.SAMPLE_RATE); byte[] data = new byte[frames * 2];
        for (int i = 0; i < frames; i++) {
            int sample = (int) (Math.sin(2 * Math.PI * frequency * i / Waveform.SAMPLE_RATE) * amplitude * 32767);
            data[i * 2] = (byte) sample; data[i * 2 + 1] = (byte) (sample >> 8);
        }
        return data;
    }
    public static AudioTrack track(Path path, byte[] pcm, double offset) throws IOException {
        try (WavFiles.Writer writer = new WavFiles.Writer(path)) { writer.write(pcm, pcm.length); }
        return new AudioTrack(path.getFileName().toString(), "Test fixture", "test", offset, path, WavFiles.waveform(path));
    }
    public static byte[] constant(int sample, int frames) {
        byte[] data = new byte[frames * 2];
        for (int i = 0; i < frames; i++) { data[i * 2] = (byte) sample; data[i * 2 + 1] = (byte) (sample >> 8); }
        return data;
    }
}
