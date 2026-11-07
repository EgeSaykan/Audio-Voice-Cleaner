package studio.wave.audio;

import com.google.gson.GsonBuilder;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** Offline microphone diversity enhancement using only deterministic signal processing. */
public final class VoiceCombiner {
    private static final int RATE = 48000, FFT = 2048, HOP = 512, BINS = FFT / 2 + 1;
    private static final long MAX_SAMPLES = 28_800_000; // ten minutes total across inputs
    public record SourceReport(String file, double gain, double qualityDb, double alignmentConfidence,
                               List<RecordingAlignment.Anchor> alignment, double meanContribution) { }
    public record Report(String method, String reference, double durationSeconds, double outputGain,
                         List<SourceReport> sources, List<String> notes) { }
    public record Result(Path audio, Path report, Report measurements) { }
    private record Input(Path path, float[] samples, BitSet clipped, double gain, double quality, double[] noise) { }

    /** Inputs must already be native PCM. Publication reserves fresh filenames and never overwrites. */
    public Result combine(List<Path> paths, Path folder, Consumer<String> progress) throws IOException {
        if (paths.size() < 2 || paths.size() > 8) throw new IOException("Choose 2–8 simultaneous recordings.");
        Set<Path> unique = new HashSet<>(); long total = 0, longest = 0;
        for (Path path : paths) {
            if (!unique.add(path.toRealPath())) throw new IOException("Choose different recording files.");
            var info = WavFiles.inspect(path);
            if (!info.nativeFormat()) throw new IOException("Combine requires 48 kHz, 16-bit mono WAV recordings.");
            if (info.frames() < RATE) throw new IOException("Each recording must be at least 1 second long.");
            total += info.frames(); longest = Math.max(longest, info.frames());
        }
        if (total > MAX_SAMPLES || (total + longest * 2) * 4 > Runtime.getRuntime().maxMemory() / 2)
            throw new IOException("These recordings are too large to combine in memory. Use shorter takes (up to 10 minutes total across all inputs).");
        List<Input> inputs = new ArrayList<>();
        for (Path path : paths) { RecordingAlignment.interrupted(); progress.accept("Measuring microphone quality: " + path.getFileName()); inputs.add(load(path)); }
        Input reference = inputs.stream().max(Comparator.comparingDouble(Input::quality)).orElseThrow();
        if (reference.quality <= -99) throw new IOException("No audible signal was found in these recordings.");
        // Keep the best microphone's clock, duration and phase as the output's reference.
        inputs.remove(reference); inputs.addFirst(reference);
        List<RecordingAlignment.Mapping> maps = new ArrayList<>();
        maps.add(new RecordingAlignment.Mapping(List.of(new RecordingAlignment.Anchor(0, 0, 1)), 1));
        for (int m = 1; m < inputs.size(); m++) {
            progress.accept("Aligning timing and clock drift: " + inputs.get(m).path.getFileName());
            maps.add(RecordingAlignment.estimate(reference.samples, inputs.get(m).samples));
        }
        progress.accept("Combining the clearest speech bands…");
        double[] contributions = new double[inputs.size()];
        float[] output = enhance(inputs, maps, contributions, progress);
        double peak = 0, power = 0;
        for (float v : output) { peak = Math.max(peak, Math.abs(v)); power += v * v; }
        // Constant gain only: no compressor pumping. Leave one dB of peak headroom.
        double gain = Math.min(4, Math.min(.12 / Math.sqrt(Math.max(1e-20, power / output.length)), .891 / Math.max(1e-12, peak)));
        List<SourceReport> sources = new ArrayList<>();
        double weights = Arrays.stream(contributions).sum();
        for (int m = 0; m < inputs.size(); m++) {
            Input in = inputs.get(m); var map = maps.get(m);
            sources.add(new SourceReport(in.path.toAbsolutePath().toString(), in.gain, in.quality,
                    map.confidence(), map.anchors(), contributions[m] / Math.max(1, weights)));
        }
        Report report = new Report("GCC delay tracking + noise-weighted spectral diversity + coherence/Wiener suppression (no learned models)",
                reference.path.toAbsolutePath().toString(), output.length / (double) RATE, gain, sources,
                List.of("Duration follows the reference microphone; other channels contribute only where they overlap.",
                        "Shared dominant speech is favored; speaker identity and complete removal of other voices are not guaranteed.",
                        "Quality and contribution are signal estimates, not measured intelligibility or clean-reference SNR.",
                        "Noise is estimated from the quietest frames; continuous speech or shared noise can limit improvement."));
        RecordingAlignment.interrupted(); Files.createDirectories(folder);
        String id = "combined-voice-" + UUID.randomUUID();
        Path audio = folder.resolve(id + ".wav"), metadata = folder.resolve(id + ".json");
        boolean audioOwned = false, metadataOwned = false;
        try {
            try (WavFiles.Writer writer = new WavFiles.Writer(audio)) {
                audioOwned = true;
                byte[] pcm = new byte[8192];
                for (int pos = 0; pos < output.length;) {
                    RecordingAlignment.interrupted(); int count = Math.min(pcm.length / 2, output.length - pos);
                    for (int i = 0; i < count; i++) {
                        int value = (int) Math.round(Math.clamp(output[pos++] * gain, -.999, .999) * 32767);
                        pcm[2 * i] = (byte) value; pcm[2 * i + 1] = (byte) (value >> 8);
                    }
                    writer.write(pcm, count * 2);
                }
            }
            Files.createFile(metadata); metadataOwned = true;
            Files.writeString(metadata, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
            return new Result(audio, metadata, report);
        } catch (IOException | RuntimeException e) {
            if (audioOwned) Files.deleteIfExists(audio);
            if (metadataOwned) Files.deleteIfExists(metadata);
            throw e;
        }
    }

    private static Input load(Path path) throws IOException {
        var info = WavFiles.inspect(path); float[] samples = new float[(int) info.frames()];
        BitSet clipped = new BitSet();
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            file.seek(info.offset()); byte[] buffer = new byte[8192]; int pos = 0;
            double previous = 0, filtered = 0, pole = Math.exp(-2 * Math.PI * 65 / RATE);
            while (pos < samples.length) {
                RecordingAlignment.interrupted(); int count = Math.min(buffer.length / 2, samples.length - pos); file.readFully(buffer, 0, count * 2);
                for (int i = 0; i < count; i++) {
                    double raw = (short) ((buffer[i * 2] & 255) | buffer[i * 2 + 1] << 8) / 32768.0;
                    if (Math.abs(raw) >= .985) clipped.set(pos / HOP);
                    filtered = raw - previous + pole * filtered; previous = raw; samples[pos++] = (float) filtered;
                }
            }
        }
        int blocks = samples.length / FFT; double[] energies = new double[blocks];
        Integer[] order = new Integer[blocks];
        for (int b = 0; b < blocks; b++) {
            order[b] = b;
            for (int i = 0; i < FFT; i++) { double x = samples[b * FFT + i]; energies[b] += x * x / FFT; }
        }
        Arrays.sort(order, Comparator.comparingDouble(b -> energies[b]));
        double speech = energies[order[(int) (blocks * .8)]], floor = energies[order[(int) (blocks * .15)]];
        double gain = Math.min(100, .08 / Math.sqrt(Math.max(1e-12, speech)));
        double quality = speech > 1e-10 ? 10 * Math.log10(Math.max(1e-12, speech) / Math.max(1e-12, floor)) - 20.0 * clipped.cardinality() / blocks : -100;
        for (int i = 0; i < samples.length; i++) samples[i] *= (float) gain;
        double[] noise = new double[BINS];
        int count = Math.max(1, Math.min(200, blocks / 5));
        double[] re = new double[FFT], im = new double[FFT];
        for (int b = 0; b < count; b++) {
            // Sample across the lowest-energy fifth, not only from the first seconds.
            int index = order[b * Math.max(1, blocks / 5) / count];
            spectrum(samples, index * FFT, 0, re, im);
            for (int k = 0; k < BINS; k++) noise[k] += (re[k] * re[k] + im[k] * im[k]) / count;
        }
        double[] smooth = noise.clone();
        for (int k = 0; k < BINS; k++) {
            double sum = 0; int n = 0;
            for (int j = Math.max(0, k - 2); j <= Math.min(BINS - 1, k + 2); j++) { sum += noise[j]; n++; }
            smooth[k] = Math.max(1e-8, sum / n);
        }
        return new Input(path, samples, clipped, gain, quality, smooth);
    }

    private static float[] enhance(List<Input> inputs, List<RecordingAlignment.Mapping> maps,
                                   double[] contributions, Consumer<String> progress) throws IOException {
        int channels = inputs.size(), length = inputs.getFirst().samples.length;
        float[] output = new float[length], norm = new float[length];
        double[][] re = new double[channels][FFT], im = new double[channels][FFT];
        double[][] power = new double[channels][BINS], crossR = new double[channels][BINS], crossI = new double[channels][BINS];
        double[][] priorWeight = new double[channels][BINS];
        double[] filteredGain = new double[BINS], rr = new double[FFT], ii = new double[FFT];
        Arrays.fill(filteredGain, 1);
        double[] availability = new double[channels], weight = new double[channels], clean = new double[channels];
        int frames = 0;
        for (int pos = -FFT + HOP; pos < length; pos += HOP) {
            RecordingAlignment.interrupted(); frames++;
            if (frames % 500 == 0) progress.accept("Combining speech… " + Math.min(99, 100 * Math.max(pos, 0) / length) + "%");
            for (int m = 0; m < channels; m++) {
                Input in = inputs.get(m); double delay = maps.get(m).delay(pos + FFT / 2.0);
                spectrum(in.samples, pos, delay, re[m], im[m]);
                double framePower = 0;
                for (int k = 0; k < BINS; k++) framePower += re[m][k] * re[m][k] + im[m][k] * im[m][k];
                int first = Math.max(0, (int) (pos + delay)), last = Math.min(in.samples.length - 1, (int) (pos + delay + FFT - 1));
                int clipped = in.clipped.nextSetBit(first / HOP);
                availability[m] = first > last ? 0 : clipped >= 0 && clipped <= last / HOP ? .001 : 1;
                // A channel which ended must not fade valid reference speech out.
                if (pos + FFT / 2.0 + delay < 0 || pos + FFT / 2.0 + delay >= in.samples.length) availability[m] = 0;
                if (framePower < 1e-12) availability[m] = 0; // digital dropout, not a quiet but usable microphone
                for (int k = 0; k < BINS; k++) {
                    double p = re[m][k] * re[m][k] + im[m][k] * im[m][k];
                    power[m][k] = .8 * power[m][k] + .2 * p;
                    double cr = re[m][k] * re[0][k] + im[m][k] * im[0][k];
                    double ci = im[m][k] * re[0][k] - re[m][k] * im[0][k];
                    crossR[m][k] = .8 * crossR[m][k] + .2 * cr;
                    crossI[m][k] = .8 * crossI[m][k] + .2 * ci;
                }
            }
            Arrays.fill(rr, 0); Arrays.fill(ii, 0);
            for (int k = 1; k < BINS - 1; k++) {
                double sum = 0, coherent = 0, confidenceSum = 0;
                double referenceCoherence = 0; int peers = 0;
                for (int m = 1; m < channels; m++) if (availability[m] > .1) {
                    referenceCoherence += coherence(power, crossR, crossI, m, k); peers++;
                }
                referenceCoherence = peers > 0 ? referenceCoherence / peers : 1;
                for (int m = 0; m < channels; m++) {
                    double p = power[m][k], noise = inputs.get(m).noise[k];
                    double coherence = m == 0 ? referenceCoherence : coherence(power, crossR, crossI, m, k);
                    // Incoherent excess energy reveals a temporary noise burst that the quiet-frame
                    // noise profile cannot predict. Apply symmetrically, including to the reference.
                    if (peers > 0 && availability[0] > .1) noise = Math.max(noise, .5 * (1 - coherence) * p);
                    clean[m] = Math.clamp(1 - noise / Math.max(1e-12, p), 0, 1);
                    // Penalize noise, but allow a quiet/temporarily obscured reference to be rescued.
                    double reliability = m == 0 ? 1 : .1 + .9 * coherence;
                    double desired = availability[m] * reliability * (.02 + clean[m] * clean[m]) / Math.max(1e-5, noise);
                    priorWeight[m][k] = .65 * priorWeight[m][k] + .35 * desired;
                    weight[m] = availability[m] == 0 ? 0 : priorWeight[m][k] * availability[m];
                    sum += weight[m];
                    if (m > 0 && availability[m] > .1) { coherent += coherence; confidenceSum++; }
                }
                double gain = 0;
                for (int m = 0; m < channels; m++) {
                    double w = weight[m] / Math.max(1e-20, sum);
                    // Correct the microphone's residual phase response before averaging. Cross spectra
                    // are smoothed over time so a single noisy frame cannot dictate its phase.
                    double phase = m == 0 ? 0 : Math.atan2(crossI[m][k], crossR[m][k]);
                    double c = Math.cos(phase), s = Math.sin(phase);
                    rr[k] += w * (re[m][k] * c + im[m][k] * s);
                    ii[k] += w * (im[m][k] * c - re[m][k] * s);
                    gain += w * Math.sqrt(Math.max(.025, clean[m]));
                    contributions[m] += w;
                }
                // Conservative floor retains consonants and avoids hard gates / musical noise.
                if (confidenceSum > 0) gain *= .75 + .25 * Math.sqrt(coherent / confidenceSum);
                gain = Math.max(.12, gain);
                filteredGain[k] = gain > filteredGain[k] ? .35 * filteredGain[k] + .65 * gain : .85 * filteredGain[k] + .15 * gain;
                double hz = k * RATE / (double) FFT;
                double band = Math.min(1, Math.pow(hz / 100, 2)) * (hz > 10000 ? Math.max(0, (14000 - hz) / 4000) : 1);
                rr[k] *= filteredGain[k] * band; ii[k] *= filteredGain[k] * band;
                rr[FFT - k] = rr[k]; ii[FFT - k] = -ii[k];
            }
            Fft.transform(rr, ii, true);
            for (int i = 0; i < FFT; i++) {
                int at = pos + i; if (at < 0 || at >= length) continue;
                double w = window(i); output[at] += (float) (rr[i] * w); norm[at] += (float) (w * w);
            }
        }
        for (int i = 0; i < length; i++) output[i] /= Math.max(1e-8f, norm[i]);
        return output;
    }

    private static double coherence(double[][] power, double[][] re, double[][] im, int m, int k) {
        return Math.clamp((re[m][k] * re[m][k] + im[m][k] * im[m][k]) / Math.max(1e-18, power[m][k] * power[0][k]), 0, 1);
    }

    private static void spectrum(float[] samples, int pos, double delay, double[] re, double[] im) {
        Arrays.fill(im, 0);
        for (int i = 0; i < FFT; i++) {
            double at = pos + i + delay; int index = (int) Math.floor(at);
            double x = index >= 0 && index < samples.length ? samples[index] : 0;
            double y = index + 1 >= 0 && index + 1 < samples.length ? samples[index + 1] : 0;
            re[i] = (x + (at - index) * (y - x)) * window(i);
        }
        Fft.transform(re, im, false);
    }
    private static double window(int i) { return .5 - .5 * Math.cos(2 * Math.PI * i / FFT); }

    /** Also available without a desktop: --combine output-folder take1.wav take2.wav [...] */
    public static void main(String[] args) throws IOException {
        if (args.length < 3) throw new IOException("Usage: VoiceCombiner output-folder take1.wav take2.wav [...]");
        Result result = new VoiceCombiner().combine(Arrays.stream(args).skip(1).map(Path::of).toList(), Path.of(args[0]), System.out::println);
        System.out.println("Saved " + result.audio); System.out.println("Report " + result.report);
    }
}
