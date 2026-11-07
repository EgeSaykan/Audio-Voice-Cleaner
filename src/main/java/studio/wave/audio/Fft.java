package studio.wave.audio;

/** In-place radix-2 FFT. Forward is unscaled, inverse divides by the transform size. */
final class Fft {
    private Fft() { }
    static void transform(double[] re, double[] im, boolean inverse) {
        int n = re.length;
        if (n != im.length || Integer.bitCount(n) != 1) throw new IllegalArgumentException("FFT size must be a power of two");
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) { double t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t; }
        }
        for (int size = 2; size <= n; size <<= 1) {
            double angle = (inverse ? 2 : -2) * Math.PI / size, wr = Math.cos(angle), wi = Math.sin(angle);
            for (int start = 0; start < n; start += size) {
                double r = 1, z = 0;
                for (int j = 0; j < size / 2; j++) {
                    int a = start + j, b = a + size / 2;
                    double tr = r * re[b] - z * im[b], ti = r * im[b] + z * re[b];
                    re[b] = re[a] - tr; im[b] = im[a] - ti; re[a] += tr; im[a] += ti;
                    double next = r * wr - z * wi; z = r * wi + z * wr; r = next;
                }
            }
        }
        if (inverse) for (int i = 0; i < n; i++) { re[i] /= n; im[i] /= n; }
    }
}
