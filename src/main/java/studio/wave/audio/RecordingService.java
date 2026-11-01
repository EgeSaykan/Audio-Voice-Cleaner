package studio.wave.audio;

import studio.wave.model.*;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** One live PipeWire capture stream and one durable WAV file per selected input. */
public final class RecordingService implements AutoCloseable {
    private final List<Capture> active = new ArrayList<>();

    public synchronized List<AudioTrack> start(List<AudioDevice> inputs, Path folder, double start,
                                               Consumer<Throwable> onFailure) throws IOException {
        if (!active.isEmpty()) throw new IOException("A recording is already running");
        if (inputs.isEmpty()) throw new IOException("Select at least one input device");
        Files.createDirectories(folder);
        try {
            for (AudioDevice device : inputs) {
                if (device.type() != AudioDevice.Type.INPUT) throw new IOException("An output device cannot be recorded as a microphone");
                String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
                String slug = device.name().replaceAll("[^a-zA-Z0-9]+", "-").replaceAll("^-|-$", "");
                if (slug.length() > 45) slug = slug.substring(0, 45);
                Path path = folder.resolve("take-" + stamp + "-" + slug + "-" + UUID.randomUUID().toString().substring(0, 8) + ".wav");
                AudioTrack track = new AudioTrack("Voice take " + (active.size() + 1), device.name(), device.id(), start, path, new Waveform());
                active.add(new Capture(device, track, onFailure));
            }
            // A process existing is not proof of capture. Require actual PCM from every input.
            for (Capture capture : active) {
                capture.firstData.get(5, TimeUnit.SECONDS);
                DeviceRouting.verify(capture.process, AudioDevice.Type.INPUT, capture.endpoint);
            }
            return active.stream().map(capture -> capture.track).toList();
        } catch (Exception e) {
            try { stop(); } catch (IOException closeError) { e.addSuppressed(closeError); }
            throw new IOException("Could not start recording: " + message(e)
                    + ". Any partial WAV files remain in the recording folder.", e);
        }
    }

    public synchronized void stop() throws IOException {
        // Signal all streams first, so a slow device cannot delay stopping the others.
        for (Capture capture : active) capture.requestStop();
        IOException failure = null;
        for (Capture capture : active) {
            try { capture.await(); }
            catch (IOException e) { if (failure == null) failure = e; else failure.addSuppressed(e); }
        }
        active.clear();
        if (failure != null) throw failure;
    }

    @Override public void close() throws IOException { stop(); }

    private static final class Capture {
        final AudioTrack track;
        final Process process;
        final String endpoint;
        final CompletableFuture<String> stderr;
        final CompletableFuture<Void> firstData = new CompletableFuture<>(), finished = new CompletableFuture<>();
        volatile boolean stopping;

        Capture(AudioDevice device, AudioTrack track, Consumer<Throwable> onFailure) throws IOException {
            this.track = track;
            endpoint = DeviceRouting.resolve(device);
            WavFiles.Writer writer = new WavFiles.Writer(track.file());
            try {
                process = ProcessSupport.start(List.of("parec", "--record", "--raw", "--device=" + endpoint,
                        "--format=s16le", "--rate=" + Waveform.SAMPLE_RATE, "--channels=1", "--latency-msec=50",
                        "--property=node.dont-fallback=true", "--property=node.dont-reconnect=true",
                        "--client-name=Wave Studio", "--stream-name=" + device.name()));
            } catch (IOException e) { writer.close(); throw e; }
            stderr = ProcessSupport.collect(process.getErrorStream());
            track.setRecording(true);
            Thread.ofVirtual().name("wave-capture-" + track.id()).start(() -> {
                Throwable failure = null;
                try (writer; InputStream stream = process.getInputStream()) {
                    byte[] block = new byte[1920];
                    while (true) {
                        int length;
                        try { length = stream.readNBytes(block, 0, block.length); }
                        catch (IOException e) { if (stopping) break; throw e; }
                        if (length <= 0) break;
                        length -= length & 1;
                        if (length == 0) continue;
                        writer.write(block, length);
                        track.waveform().append(block, 0, length);
                        firstData.complete(null);
                    }
                    if (!stopping) throw new IOException("Input disconnected: " + device.name() + ". " + errors());
                } catch (Throwable e) { failure = e; }
                finally {
                    ProcessSupport.terminate(process);
                    track.setRecording(false);
                }
                if (failure == null) {
                    if (!firstData.isDone()) firstData.completeExceptionally(new IOException("No audio received from " + device.name()));
                    finished.complete(null);
                } else {
                    firstData.completeExceptionally(failure);
                    finished.completeExceptionally(failure);
                    if (!stopping) onFailure.accept(failure);
                }
            });
        }

        String errors() {
            try { return stderr.get(1, TimeUnit.SECONDS).strip(); }
            catch (Exception e) { return "The audio stream ended unexpectedly."; }
        }
        void requestStop() { stopping = true; process.destroy(); }
        void await() throws IOException {
            try { finished.get(4, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Recording stop interrupted", e); }
            catch (ExecutionException e) { throw new IOException(message(e), e); }
            catch (TimeoutException e) {
                ProcessSupport.terminate(process);
                try { finished.get(2, TimeUnit.SECONDS); }
                catch (Exception stopped) { throw new IOException("Recording could not be finalized", stopped); }
            }
        }
    }

    private static String message(Throwable error) {
        while (error.getCause() != null) error = error.getCause();
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
