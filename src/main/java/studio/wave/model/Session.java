package studio.wave.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Workspace state is mutated on Swing's event thread; audio buffers synchronize independently. */
public final class Session {
    private String name = "Untitled session";
    private Path folder;
    private double cursor;
    private final List<AudioTrack> tracks = new ArrayList<>();
    private final Set<String> inputs = new LinkedHashSet<>(), outputs = new LinkedHashSet<>();

    public String name() { return name; }
    public void setName(String name) { this.name = name; }
    public Path folder() { return folder; }
    public void setFolder(Path folder) { this.folder = folder; }
    public double cursor() { return cursor; }
    public void setCursor(double cursor) { this.cursor = Math.max(0, cursor); }
    public List<AudioTrack> tracks() { return tracks; }
    public Set<String> inputs() { return inputs; }
    public Set<String> outputs() { return outputs; }
    public double end() { return tracks.stream().mapToDouble(AudioTrack::end).max().orElse(0); }
}
