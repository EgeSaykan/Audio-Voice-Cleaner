package studio.wave.coach;

import com.google.gson.GsonBuilder;
import studio.wave.audio.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** One coach window owns its temporary PCM copies. Successful references outlive the window. */
public final class VoiceImpactService implements AutoCloseable {
    public interface Analysis { Delivery analyze(Path wav, double seconds) throws IOException, InterruptedException; }
    public interface Enhancement {
        void ready() throws IOException, InterruptedException;
        void edit(Path wav, String text, String preset, int iterations, Path output) throws IOException, InterruptedException;
    }
    public record Take(Path source, Path playback, double start, double seconds, Delivery report) {}
    public record Reference(Path audio, Delivery report) {}
    private final CoachConfig config;
    private final Analysis analysis;
    private final Enhancement editor;
    private final Path scratch;

    public VoiceImpactService(CoachConfig config) throws IOException {
        this(config, new IamSpeakerClient(config), new StepAudioEditXClient(config));
    }
    VoiceImpactService(CoachConfig config, Analysis analysis, Enhancement editor) throws IOException {
        this.config = config; this.analysis = analysis; this.editor = editor;
        scratch = Files.createTempDirectory("wave-voice-");
    }
    public void editorReady() throws IOException, InterruptedException { editor.ready(); }
    public double clipLimit() { return config.clipLimit(); }

    public Take analyze(Path source, double start, Double length, Consumer<String> progress) throws IOException, InterruptedException {
        if (!Files.isRegularFile(source) || !Files.isReadable(source) || Files.size(source) == 0)
            throw new IOException("Recording is missing, empty or unreadable.");
        if (!(start >= 0) || !Double.isFinite(start) || (length == null && start != 0)
                || (length != null && !(length > 0 && length <= clipLimit()))) throw new IOException("Invalid clip selection");
        progress.accept("Preparing recording…");
        Path clip = scratch.resolve(UUID.randomUUID() + ".wav");
        convert(source, clip, 48000, start, length);
        double seconds = duration(clip);
        if (length != null && Math.abs(seconds - length) > .03) throw new IOException("The selection extends past the end of the recording.");
        return new Take(source, clip, start, seconds, measure(clip, seconds, progress));
    }
    private Delivery measure(Path input, double seconds, Consumer<String> progress) throws IOException, InterruptedException {
        Path sample = Files.createTempFile(scratch, "analysis-", ".wav");
        try {
            convert(input, sample, 16000, 0, null);
            progress.accept("Transcribing and measuring delivery with iamspeaker…");
            return analysis.analyze(sample, seconds);
        } finally { Files.deleteIfExists(sample); }
    }
    public Reference enhance(Take take, String text, String preset, int iterations, Consumer<String> progress) throws IOException, InterruptedException {
        if (take.seconds > clipLimit() + .0001) throw new IOException("Select and analyze a section of up to " + clipLimit() + " seconds.");
        if (text == null || text.isBlank() || iterations < 1 || iterations > 2) throw new IOException("Check the transcript and iterations.");
        // Stage in the destination filesystem so publication of audio and report is atomic.
        Path root = take.source.toAbsolutePath().getParent().resolve(config.output());
        Files.createDirectories(root);
        Path stage = Files.createTempDirectory(root, ".pending-");
        try {
            Path raw = stage.resolve("model.wav"), reference = stage.resolve("AI-enhanced.wav");
            progress.accept("Generating AI-modified reference…");
            editor.edit(take.playback, text.strip(), preset, iterations, raw);
            convert(raw, reference, 48000, 0, null);
            double seconds = duration(reference);
            if (seconds < take.seconds * .25 || seconds > take.seconds * 3 + 1 || WavFiles.waveform(reference).maximum() < .001)
                throw new IOException("Invalid model output: silent or implausible duration.");
            Delivery report = measure(reference, seconds, progress);
            Files.delete(raw);
            var metadata = Map.of("source", take.source.toAbsolutePath().toString(), "startSeconds", take.start,
                    "original", take.report, "intendedTranscript", text.strip(), "preset", preset,
                    "iterations", iterations, "enhanced", report, "comparison", take.report.compare(report, text));
            Files.writeString(stage.resolve("comparison.json"), new GsonBuilder().setPrettyPrinting().create().toJson(metadata));
            Path published = root.resolve(UUID.randomUUID().toString());
            Files.move(stage, published, StandardCopyOption.ATOMIC_MOVE);
            return new Reference(published.resolve(reference.getFileName()), report);
        } finally { deleteTree(stage); }
    }
    private void convert(Path input, Path output, int rate, double start, Double length) throws IOException {
        var args = new ArrayList<>(List.of(config.ffmpeg(), "-nostdin", "-loglevel", "error", "-xerror", "-y", "-protocol_whitelist", "file,pipe",
                "-ss", String.valueOf(start), "-i", input.toAbsolutePath().toString(), "-map", "0:a:0", "-vn"));
        if (length != null) args.addAll(List.of("-t", length.toString()));
        long limit = (rate / 16000L) * config.uploadBytes();
        args.addAll(List.of("-map_metadata", "-1", "-ac", "1", "-ar", String.valueOf(rate), "-c:a", "pcm_s16le", "-fs",
                Long.toString(limit + 65536), output.toString()));
        try { ProcessSupport.run(config.timeout(), args.toArray(String[]::new)); }
        catch (IOException e) { throw new IOException("Could not decode audio with FFmpeg: " + e.getMessage(), e); }
        if (Files.size(output) > limit) throw new IOException("Recording exceeds the configured decoded audio size limit.");
    }
    private static double duration(Path path) throws IOException {
        var wav = WavFiles.inspect(path);
        if (wav.sampleRate() <= 0 || wav.frames() == 0) throw new IOException("Audio is empty or has an invalid sample rate.");
        return wav.frames() / (double) wav.sampleRate();
    }
    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        }
    }
    public void close() throws IOException { deleteTree(scratch); }
}
