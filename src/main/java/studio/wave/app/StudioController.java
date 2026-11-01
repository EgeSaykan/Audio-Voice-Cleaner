package studio.wave.app;

import studio.wave.audio.*;
import studio.wave.model.*;
import studio.wave.storage.*;
import javax.swing.SwingUtilities;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Coordinates asynchronous audio/storage work; views contain no capture or filesystem logic. */
public final class StudioController {
    public enum State { READY, WORKING, STARTING, RECORDING, STOPPING, PLAYING }
    private final Session session = new Session();
    private final DeviceService deviceService = new DeviceService();
    private final RecordingService recorder = new RecordingService();
    private final PlaybackService player = new PlaybackService();
    private final RecordingLibrary library = new RecordingLibrary();
    private final SessionStore store = new SessionStore();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("wave-operations").factory());
    private List<AudioDevice> devices = List.of();
    private List<Path> files = List.of();
    private List<AudioTrack> currentTakes = List.of();
    private State state = State.READY;
    private String status = "Choose your inputs and a folder, then record your first take.";
    private Runnable changed = () -> {}, ticked = () -> {};
    private Consumer<String> errorHandler = message -> {};
    private long playbackGeneration;
    private boolean closing, initialized;

    public StudioController() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { recorder.close(); } catch (IOException ignored) { }
            player.close();
        }, "wave-audio-shutdown"));
    }
    public void setListeners(Runnable changed, Runnable ticked, Consumer<String> errorHandler) {
        this.changed = changed; this.ticked = ticked; this.errorHandler = errorHandler;
    }
    public Session session() { return session; }
    public State state() { return state; }
    public String status() { return status; }
    public List<Path> files() { return files; }
    public List<AudioDevice> devices(AudioDevice.Type type) { return devices.stream().filter(d -> d.type() == type).toList(); }
    public List<AudioDevice> selected(AudioDevice.Type type) {
        Set<String> ids = type == AudioDevice.Type.INPUT ? session.inputs() : session.outputs();
        return devices(type).stream().filter(d -> ids.contains(d.id())).toList();
    }
    public boolean busy() { return closing || state == State.WORKING || state == State.STARTING || state == State.STOPPING; }
    public boolean editable() { return !busy() && state != State.RECORDING; }

    public void discoverDevices() {
        if (!editable() || state == State.PLAYING) return;
        task("Finding audio inputs and outputs…", deviceService::discover, found -> {
            devices = found;
            Set<String> ids = new HashSet<>(); found.forEach(d -> ids.add(d.id()));
            session.inputs().retainAll(ids); session.outputs().retainAll(ids);
            if (!initialized) {
                devices(AudioDevice.Type.INPUT).stream().findFirst().ifPresent(d -> session.inputs().add(d.id()));
                devices(AudioDevice.Type.OUTPUT).stream().findFirst().ifPresent(d -> session.outputs().add(d.id()));
                initialized = true;
            }
            status = devices(AudioDevice.Type.INPUT).size() + " inputs · " + devices(AudioDevice.Type.OUTPUT).size() + " outputs available";
        });
    }

    public void selectDevices(AudioDevice.Type type, Set<String> selected) {
        if (!editable()) return;
        if (state == State.PLAYING) pause();
        Set<String> target = type == AudioDevice.Type.INPUT ? session.inputs() : session.outputs();
        target.clear();
        for (AudioDevice device : devices(type)) if (selected.contains(device.id())) target.add(device.id());
        status = target.size() + (type == AudioDevice.Type.INPUT ? " input(s) selected" : " output(s) selected"); changed.run();
    }

    public void selectFolder(Path folder, Runnable afterLoaded) {
        if (!editable()) return;
        if (state == State.PLAYING) pause();
        Path path = folder.toAbsolutePath().normalize();
        task("Opening recording folder…", () -> library.list(path), found -> {
            session.setFolder(path); files = found;
            status = "Opened " + path.getFileName() + " · " + found.size() + " audio files";
            if (afterLoaded != null) SwingUtilities.invokeLater(afterLoaded);
        });
    }
    public void refreshLibrary() {
        if (session.folder() != null && editable()) selectFolder(session.folder(), null);
    }
    public void importFile(Path file) {
        if (!editable()) return;
        if (state == State.PLAYING) pause();
        task("Reading the actual audio samples…", () -> library.load(file), track -> {
            session.tracks().add(track); status = "Loaded " + file.getFileName() + " · " + String.format(Locale.ROOT, "%.1f seconds", track.duration());
        });
    }

    public void startRecording() {
        if (!editable()) return;
        if (session.folder() == null) { fail(new IOException("Choose a folder for your WAV recordings first")); return; }
        List<AudioDevice> inputs = selected(AudioDevice.Type.INPUT);
        if (inputs.isEmpty()) { fail(new IOException("Choose at least one input device")); return; }
        if (state == State.PLAYING) pause();
        double start = session.cursor(); Path folder = session.folder();
        state = State.STARTING; status = "Opening " + inputs.size() + " input stream(s)…"; changed.run();
        worker.submit(() -> {
            try {
                List<AudioTrack> takes = recorder.start(inputs, folder, start, failure -> SwingUtilities.invokeLater(() -> {
                    if (!closing && state == State.RECORDING) stopRecording(failure);
                }));
                SwingUtilities.invokeLater(() -> {
                    if (closing) return;
                    currentTakes = takes; session.tracks().addAll(takes); state = State.RECORDING;
                    status = "Recording real input audio · WAV files are written directly to your folder"; changed.run();
                });
            } catch (Exception e) { report(e); }
        });
    }
    public void stopRecording() { stopRecording(null); }
    private void stopRecording(Throwable streamFailure) {
        if (state != State.RECORDING) return;
        state = State.STOPPING; status = "Finalizing WAV recordings…"; changed.run();
        worker.submit(() -> {
            Throwable error = streamFailure;
            try { recorder.stop(); } catch (IOException e) { error = e; }
            List<Path> found = List.of();
            try { found = library.list(session.folder()); } catch (IOException e) { if (error == null) error = e; }
            List<Path> finishedFiles = found; Throwable failure = error;
            SwingUtilities.invokeLater(() -> {
                if (closing) return;
                session.setCursor(currentTakes.stream().mapToDouble(AudioTrack::end).max().orElse(session.cursor()));
                boolean silent = currentTakes.stream().allMatch(t -> t.waveform().maximum() == 0);
                files = finishedFiles; state = State.READY;
                status = currentTakes.size() + " WAV recording(s) saved" + (silent ? " · input was silent; check microphone mute/gain" : " · ready to play");
                currentTakes = List.of(); changed.run();
                if (failure != null) fail(failure);
            });
        });
    }

    public void togglePlayback() {
        if (state == State.PLAYING) { pause(); return; }
        if (!editable() || session.tracks().isEmpty()) return;
        List<AudioDevice> outputs = selected(AudioDevice.Type.OUTPUT);
        if (outputs.isEmpty()) { fail(new IOException("Select an output device for playback")); return; }
        if (session.cursor() >= session.end()) session.setCursor(0);
        List<AudioTrack> tracks = List.copyOf(session.tracks()); double start = session.cursor();
        long generation = ++playbackGeneration;
        state = State.WORKING; status = "Opening playback output…"; changed.run();
        worker.submit(() -> {
            try {
                player.start(tracks, outputs, start, failure -> SwingUtilities.invokeLater(() -> {
                    if (closing || generation != playbackGeneration) return;
                    ++playbackGeneration;
                    state = State.READY;
                    if (failure == null) { session.setCursor(session.end()); status = "Playback finished"; changed.run(); }
                    else fail(failure);
                }));
                SwingUtilities.invokeLater(() -> {
                    if (closing || generation != playbackGeneration) return;
                    state = State.PLAYING; status = "Playing to " + outputs.size() + " selected output(s)"; changed.run();
                });
            } catch (Exception e) { report(e); }
        });
    }
    private void pause() {
        session.setCursor(Math.min(session.end(), player.position())); ++playbackGeneration;
        worker.submit(player::stop); state = State.READY; status = "Playback paused"; changed.run();
    }
    public void seek(double seconds) {
        if (!editable()) return;
        if (state == State.PLAYING) pause();
        session.setCursor(Math.max(0, Math.min(session.end(), seconds))); ticked.run();
    }
    public void mute(AudioTrack track) {
        if (editable()) { track.setMuted(!track.muted()); ticked.run(); }
    }
    public void rename(String name) {
        if (name != null && !name.isBlank() && editable()) { session.setName(name.strip()); changed.run(); }
    }
    public void save() {
        if (!editable()) return;
        if (state == State.PLAYING) pause();
        task("Saving session and audio references…", () -> store.save(session), path -> status = "Saved " + path.getFileName() + " · WAV audio is in your recording folder");
    }
    public void tick() {
        if (state == State.RECORDING) {
            session.setCursor(currentTakes.stream().mapToDouble(AudioTrack::end).max().orElse(session.cursor())); ticked.run();
            if (currentTakes.stream().anyMatch(t -> !t.recording())) stopRecording(new IOException("An input stream ended. The captured audio has been kept."));
        } else if (state == State.PLAYING) { session.setCursor(Math.min(session.end(), player.position())); ticked.run(); }
    }
    public float inputLevel(String id) {
        if (state != State.RECORDING) return 0;
        return currentTakes.stream().filter(t -> t.inputId().equals(id)).findFirst().map(t -> t.waveform().level()).orElse(0f);
    }
    public void close(Runnable onClosed) {
        if (closing) return;
        closing = true; ++playbackGeneration; status = "Closing audio streams…"; changed.run();
        worker.submit(() -> {
            try { recorder.close(); } catch (IOException ignored) { }
            player.close(); SwingUtilities.invokeLater(onClosed);
        });
        worker.shutdown();
    }
    private <T> void task(String message, Callable<T> job, Consumer<T> success) {
        state = State.WORKING; status = message; changed.run();
        worker.submit(() -> {
            try {
                T result = job.call();
                SwingUtilities.invokeLater(() -> { if (!closing) { state = State.READY; success.accept(result); changed.run(); } });
            } catch (Exception e) { report(e); }
        });
    }
    private void report(Throwable error) { SwingUtilities.invokeLater(() -> { if (!closing) { state = State.READY; fail(error); } }); }
    private void fail(Throwable error) {
        status = error.getMessage() == null ? error.toString() : error.getMessage(); changed.run(); errorHandler.accept(status);
    }
}
