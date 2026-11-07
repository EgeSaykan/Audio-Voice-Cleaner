package studio.wave.audio;

import java.io.IOException;
import java.util.*;

/** Repeated, band-limited generalized cross correlation; no device clock is assumed exact. */
final class RecordingAlignment {
    record Anchor(double sample, double delay, double confidence) { }
    record Mapping(List<Anchor> anchors, double confidence) {
        double delay(double sample) {
            if (anchors.size() == 1 || sample <= anchors.getFirst().sample) return anchors.getFirst().delay;
            int lo = 0, hi = anchors.size() - 1;
            while (lo + 1 < hi) { int mid = (lo + hi) / 2; if (anchors.get(mid).sample <= sample) lo = mid; else hi = mid; }
            Anchor a = anchors.get(lo), b = anchors.get(hi);
            double fraction = Math.clamp((sample - a.sample) / (b.sample - a.sample), 0, 1);
            return a.delay + fraction * (b.delay - a.delay);
        }
    }

    static Mapping estimate(float[] reference, float[] other) throws IOException {
        int length = Math.min(reference.length, other.length);
        List<Anchor> candidates = new ArrayList<>();
        // A 0.34 s window tracks settling USB clocks; search covers +/-250 ms start offsets.
        for (int start = 0; start + 16384 <= length; start += 12000) {
            interrupted();
            Anchor point = correlate(reference, other, start, Math.min(12000, length / 4));
            if (point.confidence >= .12) candidates.add(point);
        }
        if (candidates.size() < 3) throw new IOException("Not enough matching audio to align these takes. Select simultaneous recordings of the same speech (at least 1 second).");
        // Discard isolated wrong peaks, but preserve a sustained change in a device's delay.
        List<Anchor> accepted = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            Anchor p = candidates.get(i);
            boolean neighbor = i > 0 && nearby(p, candidates.get(i - 1))
                    || i + 1 < candidates.size() && nearby(p, candidates.get(i + 1));
            if (neighbor) accepted.add(p);
        }
        if (accepted.size() < 3 || accepted.size() < (length / 12000) * .12)
            throw new IOException("The recordings do not align reliably. Check that they contain the same take.");
        return new Mapping(List.copyOf(accepted), accepted.stream().mapToDouble(Anchor::confidence).average().orElse(0));
    }

    private static boolean nearby(Anchor a, Anchor b) {
        return Math.abs(a.delay - b.delay) < 80 + Math.abs(a.sample - b.sample) * .02;
    }

    private static Anchor correlate(float[] a, float[] b, int start, int maxLag) {
        int window = 16384, n = 65536;
        double[] ar = new double[n], ai = new double[n], br = new double[n], bi = new double[n];
        // The second window extends on both sides; this avoids bias towards zero lag.
        for (int i = 0; i < window; i++) ar[i] = a[start + i];
        for (int i = 0; i < window + 2 * maxLag; i++) {
            int at = start - maxLag + i;
            if (at >= 0 && at < b.length) br[i] = b[at];
        }
        Fft.transform(ar, ai, false); Fft.transform(br, bi, false);
        double[] rawR = new double[n], rawI = new double[n];
        for (int k = 0; k < n; k++) {
            double frequency = Math.min(k, n - k) * 48000.0 / n;
            double r = ar[k] * br[k] + ai[k] * bi[k], z = ar[k] * bi[k] - ai[k] * br[k];
            double scale = frequency >= 180 && frequency <= 6000 ? Math.pow(Math.max(1e-18, Math.hypot(r, z)), -.8) : 0;
            rawR[k] = scale > 0 ? r : 0; rawI[k] = scale > 0 ? z : 0;
            ar[k] = r * scale; ai[k] = z * scale;
        }
        Fft.transform(ar, ai, true);
        Fft.transform(rawR, rawI, true);
        // Whitening handles different microphone responses; ordinary correlation is more reliable
        // for narrow-band/clean material where whitening emphasizes window-edge leakage.
        Anchor whitened = peak(a, b, start, maxLag, ar), ordinary = peak(a, b, start, maxLag, rawR);
        return whitened.confidence >= ordinary.confidence ? whitened : ordinary;
    }

    private static Anchor peak(float[] a, float[] b, int start, int maxLag, double[] ar) {
        int n = ar.length, window = 16384;
        int best = 0;
        for (int i = 1; i <= maxLag * 2; i++) if (Math.abs(ar[i]) > Math.abs(ar[best])) best = i;
        double left = Math.abs(ar[(best + n - 1) % n]), center = Math.abs(ar[best]), right = Math.abs(ar[best + 1]);
        double curvature = left - 2 * center + right;
        double fraction = curvature < -1e-15 ? Math.clamp(.5 * (left - right) / curvature, -.5, .5) : 0;
        int lag = best - maxLag;
        double ab = 0, aa = 0, bb = 0;
        // Waveform correlation validates the whitened peak and rejects unrelated noise.
        for (int i = 0; i < window; i++) {
            int j = start + i + lag;
            if (j < 0 || j >= b.length) continue;
            double x = a[start + i], y = b[j]; ab += x * y; aa += x * x; bb += y * y;
        }
        double confidence = Math.abs(ab) / Math.sqrt(Math.max(1e-30, aa * bb));
        return new Anchor(start + window / 2.0, lag + fraction, confidence);
    }

    static void interrupted() throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new IOException("Combining recordings cancelled");
    }
}
