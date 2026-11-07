package studio.wave.audio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.RandomAccessFile;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class VoiceCombinerTest {
    @TempDir Path folder;
    private static final int RATE = 48000;

    @Test void alignmentTracksDelayClockDriftAndReversedPolarity() throws Exception {
        float[] a = voice(5), b = new float[a.length];
        Random random = new Random(7);
        for (int i = 0; i < b.length; i++) b[i] = (float) (-.6 * sample(a, (i - 350) / 1.0003) + .005 * random.nextGaussian());
        var mapping = RecordingAlignment.estimate(a, b);
        assertEquals(350 + RATE * .0003, mapping.delay(RATE), 5);
        assertEquals(350 + 4 * RATE * .0003, mapping.delay(4 * RATE), 5);
        assertTrue(mapping.confidence() > .7);
    }

    @Test void complementaryNoiseImprovesAgainstKnownCleanSignalAndPreservesInputs() throws Exception {
        float[] clean = voice(5), a = clean.clone(), b = clean.clone(); Random random = new Random(19);
        for (int i = 0; i < a.length; i++) {
            boolean first = i < a.length / 2;
            a[i] += (float) ((first ? .065 : .012) * random.nextGaussian());
            b[i] += (float) ((first ? .012 : .065) * random.nextGaussian());
        }
        Path pa = write("a.wav", a), pb = write("b.wav", b);
        byte[] before = Files.readAllBytes(pa);
        var result = new VoiceCombiner().combine(List.of(pa, pb), folder, s -> {});
        float[] output = read(result.audio());
        assertEquals(clean.length, output.length);
        double inputSnr = Math.max(snr(clean, a), snr(clean, b)), outputSnr = snr(clean, output);
        float[] average = new float[a.length]; for (int i = 0; i < a.length; i++) average[i] = (a[i] + b[i]) / 2;
        System.out.printf(Locale.ROOT, "Complementary noise: input %.2f dB, average %.2f dB, combined %.2f dB%n", inputSnr, snr(clean, average), outputSnr);
        assertTrue(outputSnr > inputSnr + 2, "Combination should improve known-reference SNR by at least 2 dB");
        assertTrue(outputSnr > snr(clean, average) + 2, "Adaptive selection should outperform a plain average by at least 2 dB");
        assertArrayEquals(before, Files.readAllBytes(pa));
        assertTrue(Files.isRegularFile(result.report()));
        assertTrue(result.measurements().sources().stream().allMatch(s -> s.meanContribution() > .1));
        assertTrue(maxAbs(output) <= .892);
        assertNotEquals(result.audio(), new VoiceCombiner().combine(List.of(pa, pb), folder, s -> {}).audio());
    }

    @Test void unrelatedSilentDuplicateAndInvalidInputsAreRejectedWithoutPublishing() throws Exception {
        Path silence = write("silence.wav", new float[RATE * 2]), silence2 = write("silence2.wav", new float[RATE * 2]);
        Path output = folder.resolve("results"); VoiceCombiner service = new VoiceCombiner();
        assertThrows(java.io.IOException.class, () -> service.combine(List.of(silence, silence2), output, s -> {}));
        assertThrows(java.io.IOException.class, () -> service.combine(List.of(silence, silence), output, s -> {}));
        assertThrows(java.io.IOException.class, () -> service.combine(List.of(silence), output, s -> {}));
        Random random = new Random(99); float[] a = new float[3 * RATE], b = new float[a.length];
        for (int i = 0; i < a.length; i++) { a[i] = (float) (.1 * random.nextGaussian()); b[i] = (float) (.1 * random.nextGaussian()); }
        Path pa = write("unrelated-a.wav", a), pb = write("unrelated-b.wav", b);
        assertThrows(java.io.IOException.class, () -> service.combine(List.of(pa, pb), output, s -> {}));
        assertFalse(Files.exists(output));
    }

    @Test void clippingDropoutsAndUnequalLengthsDoNotDestroyTheSharedVoice() throws Exception {
        float[] clean = voice(5), a = clean.clone(), b = Arrays.copyOf(clean, clean.length - RATE / 2);
        Random random = new Random(33);
        for (int i = 0; i < a.length; i++) a[i] += (float) (.008 * random.nextGaussian());
        for (int i = RATE; i < RATE + RATE / 3; i++) a[i] = i % 2 == 0 ? 1 : -1;
        Arrays.fill(b, 2 * RATE, 2 * RATE + RATE / 3, 0);
        var result = new VoiceCombiner().combine(List.of(write("clip.wav", a), write("drop.wav", b)), folder, s -> {});
        float[] out = read(result.audio());
        assertEquals(WavFiles.inspect(Path.of(result.measurements().reference())).frames(), out.length);
        assertTrue(maxAbs(out) <= .892);
        assertTrue(snr(Arrays.copyOf(clean, out.length), out) > snr(Arrays.copyOf(clean, out.length), Arrays.copyOf(a, out.length)) + 3);
    }

    @Test void cancellationDoesNotPublishPartialOutput() throws Exception {
        float[] clean = voice(2); Path a = write("cancel-a.wav", clean), b = write("cancel-b.wav", clean);
        try {
            Thread.currentThread().interrupt();
            assertThrows(java.io.IOException.class, () -> new VoiceCombiner().combine(List.of(a, b), folder.resolve("cancelled"), s -> {}));
            assertFalse(Files.exists(folder.resolve("cancelled")));
        } finally { Thread.interrupted(); }
    }

    @Test void fftRoundTripsWithoutChangingAmplitude() {
        double[] re = new double[2048], im = new double[2048]; Random random = new Random(1);
        for (int i = 0; i < re.length; i++) re[i] = random.nextGaussian();
        double[] original = re.clone(); Fft.transform(re, im, false); Fft.transform(re, im, true);
        assertArrayEquals(original, re, 1e-10);
    }

    private Path write(String name, float[] samples) throws Exception {
        Path p = folder.resolve(name); byte[] pcm = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++) { int v = (int) (Math.clamp(samples[i], -1, .99997) * 32768); pcm[2*i] = (byte) v; pcm[2*i+1] = (byte) (v >> 8); }
        try (var writer = new WavFiles.Writer(p)) { writer.write(pcm, pcm.length); } return p;
    }
    private static float[] read(Path path) throws Exception {
        var info = WavFiles.inspect(path); float[] samples = new float[(int) info.frames()];
        try (RandomAccessFile f = new RandomAccessFile(path.toFile(), "r")) {
            f.seek(info.offset()); for (int i = 0; i < samples.length; i++) samples[i] = Short.reverseBytes(f.readShort()) / 32768f;
        } return samples;
    }
    private static float[] voice(int seconds) {
        float[] x = new float[seconds * RATE]; Random random = new Random(2); double phase = 0, noise = 0;
        for (int i = 0; i < x.length; i++) {
            double t = i / (double) RATE;
            phase += 2 * Math.PI * (150 + 30 * Math.sin(t * 3.7)) / RATE;
            noise = .85 * noise + .15 * random.nextGaussian();
            double envelope = t < .4 || t > seconds - .4 ? 0 : Math.pow(.5 + .5 * Math.sin(t * 9), 2);
            double v = 0; for (int h = 1; h <= 14; h++) v += Math.sin(phase * h) / Math.sqrt(h);
            x[i] = (float) (envelope * (.05 * v + .03 * noise));
        } return x;
    }
    private static double sample(float[] x, double t) {
        int i = (int) Math.floor(t); if (i < 0 || i + 1 >= x.length) return 0;
        return x[i] + (t - i) * (x[i + 1] - x[i]);
    }
    /** Scale-invariant SNR: raising volume alone cannot improve the score. */
    private static double snr(float[] clean, float[] output) {
        double xy = 0, xx = 0, error = 0;
        for (int i = 0; i < clean.length; i++) { xy += clean[i] * output[i]; xx += clean[i] * clean[i]; }
        double scale = xy / Math.max(xx, 1e-20);
        for (int i = 0; i < clean.length; i++) error += Math.pow(output[i] - scale * clean[i], 2);
        return 10 * Math.log10(scale * scale * xx / Math.max(1e-20, error));
    }
    private static double maxAbs(float[] samples) { double max = 0; for (float x : samples) max = Math.max(max, Math.abs(x)); return max; }
}
