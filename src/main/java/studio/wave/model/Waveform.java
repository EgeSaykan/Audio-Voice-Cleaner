package studio.wave.model;

import java.util.Arrays;

/** Compact peak envelope built exclusively from real signed 16-bit little-endian PCM. */
public final class Waveform {
    public static final int SAMPLE_RATE = 48_000;
    public static final int FRAMES_PER_PEAK = 480;
    private float[] peaks = new float[1024];
    private int peakCount, bucketFrames;
    private float bucketPeak, maximum, level;
    private long frames;

    public synchronized void append(byte[] pcm, int offset, int length) {
        if ((length & 1) != 0) throw new IllegalArgumentException("PCM must contain whole 16-bit samples");
        float blockPeak = 0;
        for (int i = offset; i < offset + length; i += 2) {
            int sample = (short) ((pcm[i] & 0xff) | (pcm[i + 1] << 8));
            float amplitude = Math.abs(sample / 32768f);
            bucketPeak = Math.max(bucketPeak, amplitude);
            blockPeak = Math.max(blockPeak, amplitude);
            frames++;
            if (++bucketFrames == FRAMES_PER_PEAK) {
                if (peakCount == peaks.length) peaks = Arrays.copyOf(peaks, peaks.length * 2);
                peaks[peakCount++] = bucketPeak;
                bucketFrames = 0;
                bucketPeak = 0;
            }
        }
        maximum = Math.max(maximum, blockPeak);
        level = blockPeak;
    }

    public synchronized float[] view(double fromSeconds, double secondsPerBar, int bars) {
        float[] result = new float[bars];
        double bucketsPerSecond = SAMPLE_RATE / (double) FRAMES_PER_PEAK;
        for (int i = 0; i < bars; i++) {
            double from = fromSeconds + i * secondsPerBar, to = from + secondsPerBar;
            if (to <= 0) continue;
            int first = Math.max(0, (int) Math.floor(from * bucketsPerSecond));
            int end = Math.min(peakCount + (bucketFrames > 0 ? 1 : 0), (int) Math.ceil(to * bucketsPerSecond));
            for (int j = first; j < end; j++) result[i] = Math.max(result[i], j == peakCount ? bucketPeak : peaks[j]);
        }
        return result;
    }

    public synchronized long frames() { return frames; }
    public synchronized double duration() { return frames / (double) SAMPLE_RATE; }
    public synchronized float level() { return level; }
    public synchronized float maximum() { return maximum; }
}
