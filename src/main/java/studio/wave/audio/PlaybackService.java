package studio.wave.audio;

import studio.wave.model.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Sends the real mixed PCM stream to the explicitly selected output device(s). */
public final class PlaybackService implements AutoCloseable {
    private final List<Process> outputs = new ArrayList<>();
    private final List<CompletableFuture<String>> errors = new ArrayList<>();
    private volatile boolean stopping = true;
    private volatile long writtenFrames, startedNanos;
    private double start;
    private CompletableFuture<Void> finished = CompletableFuture.completedFuture(null);

    public synchronized void start(List<AudioTrack> tracks, List<AudioDevice> devices, double startSeconds,
                                   Consumer<Throwable> onComplete) throws IOException {
        stop();
        if (devices.isEmpty()) throw new IOException("Select at least one output device");
        PcmMixer mixer = new PcmMixer(tracks, startSeconds);
        List<String> endpoints = new ArrayList<>();
        try {
            for (AudioDevice device : devices) {
                if (device.type() != AudioDevice.Type.OUTPUT) throw new IOException("Playback requires output devices");
                String endpoint = DeviceRouting.resolve(device);
                endpoints.add(endpoint);
                Process process = ProcessSupport.start(List.of("pacat", "--playback", "--raw", "--device=" + endpoint,
                        "--format=s16le", "--rate=" + Waveform.SAMPLE_RATE, "--channels=1", "--latency-msec=50",
                        "--property=node.dont-fallback=true", "--property=node.dont-reconnect=true",
                        "--client-name=Wave Studio", "--stream-name=Timeline playback"));
                outputs.add(process); errors.add(ProcessSupport.collect(process.getErrorStream()));
            }
        } catch (IOException e) { mixer.close(); outputs.forEach(ProcessSupport::terminate); outputs.clear(); errors.clear(); throw e; }
        start = startSeconds; writtenFrames = 0; startedNanos = System.nanoTime(); stopping = false;
        finished = new CompletableFuture<>();
        // Immutable lists keep the worker independent of later stop/start operations.
        List<Process> processes = List.copyOf(outputs);
        List<CompletableFuture<String>> diagnostics = List.copyOf(errors);
        CompletableFuture<Void> completion = finished;
        Thread.ofVirtual().name("wave-playback").start(() -> {
            Throwable failure = null;
            try (mixer) {
                byte[] block = new byte[1920]; int length;
                boolean verified = false;
                while (!stopping && (length = mixer.read(block)) > 0) {
                    for (Process process : processes) process.getOutputStream().write(block, 0, length);
                    if (!verified) {
                        for (int i = 0; i < processes.size(); i++) DeviceRouting.verify(processes.get(i), AudioDevice.Type.OUTPUT, endpoints.get(i));
                        verified = true; startedNanos = System.nanoTime();
                    }
                    writtenFrames += length / 2;
                }
                for (int i = 0; i < processes.size(); i++) {
                    Process process = processes.get(i);
                    process.getOutputStream().close();
                    if (!stopping && (!process.waitFor(5, TimeUnit.SECONDS) || process.exitValue() != 0))
                        throw new IOException("Output playback failed: " + diagnostics.get(i).get(1, TimeUnit.SECONDS).strip());
                }
            } catch (Throwable e) { if (!stopping) failure = e; }
            finally { processes.forEach(ProcessSupport::terminate); }
            boolean notify = !stopping;
            completion.complete(null);
            if (notify) onComplete.accept(failure);
        });
    }

    public double position() {
        double elapsed = Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000_000.0);
        return start + Math.min(elapsed, writtenFrames / (double) Waveform.SAMPLE_RATE);
    }

    public synchronized void stop() {
        stopping = true;
        outputs.forEach(ProcessSupport::terminate);
        try { finished.get(3, TimeUnit.SECONDS); }
        catch (Exception ignored) { }
        outputs.clear(); errors.clear();
    }
    @Override public void close() { stop(); }
}
