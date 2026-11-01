package studio.wave.model;

import java.nio.file.Path;
import java.util.UUID;

public final class AudioTrack {
    private final String id = UUID.randomUUID().toString();
    private final String name, source, inputId;
    private final double start;
    private final Waveform waveform;
    private Path file;
    private volatile boolean muted, recording;

    public AudioTrack(String name, String source, String inputId, double start, Path file, Waveform waveform) {
        this.name = name; this.source = source; this.inputId = inputId;
        this.start = start; this.file = file; this.waveform = waveform;
    }

    public String id() { return id; }
    public String name() { return name; }
    public String source() { return source; }
    public String inputId() { return inputId; }
    public double start() { return start; }
    public Path file() { return file; }
    public Waveform waveform() { return waveform; }
    public double duration() { return waveform.duration(); }
    public double end() { return start + duration(); }
    public boolean muted() { return muted; }
    public boolean recording() { return recording; }
    public void setMuted(boolean muted) { this.muted = muted; }
    public void setRecording(boolean recording) { this.recording = recording; }
    public void setFile(Path file) { this.file = file; }
}
