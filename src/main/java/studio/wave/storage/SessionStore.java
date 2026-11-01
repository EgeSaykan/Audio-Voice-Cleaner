package studio.wave.storage;

import com.google.gson.*;
import studio.wave.model.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/** Persists real WAV references and session settings without overwriting previous takes. */
public final class SessionStore {
    public Path save(Session session) throws IOException {
        Path folder = session.folder();
        if (folder == null) throw new IOException("Choose a recording folder first");
        Files.createDirectories(folder);
        JsonObject document = new JsonObject();
        document.addProperty("application", "Wave Studio"); document.addProperty("version", 2);
        document.addProperty("name", session.name()); document.addProperty("cursorSeconds", session.cursor());
        document.addProperty("sampleRate", Waveform.SAMPLE_RATE); document.addProperty("sampleBits", 16); document.addProperty("channels", 1);
        JsonArray inputs = new JsonArray(), outputs = new JsonArray(), tracks = new JsonArray();
        session.inputs().forEach(inputs::add); session.outputs().forEach(outputs::add);
        document.add("inputs", inputs); document.add("outputs", outputs);
        for (AudioTrack track : session.tracks()) {
            Path source = track.file().toAbsolutePath().normalize();
            Path destination = folder.toAbsolutePath().normalize();
            if (!source.startsWith(destination)) {
                Path imported = destination.resolve("import-" + UUID.randomUUID().toString().substring(0, 8) + ".wav");
                Files.copy(source, imported); track.setFile(imported); source = imported;
            }
            JsonObject entry = new JsonObject();
            entry.addProperty("id", track.id()); entry.addProperty("name", track.name()); entry.addProperty("source", track.source());
            entry.addProperty("startSeconds", track.start()); entry.addProperty("durationSeconds", track.duration());
            entry.addProperty("muted", track.muted()); entry.addProperty("file", destination.relativize(source).toString()); tracks.add(entry);
        }
        document.add("tracks", tracks);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path path = folder.resolve("wave-session-" + stamp + "-" + UUID.randomUUID().toString().substring(0, 8) + ".json");
        return Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(document) + "\n", StandardOpenOption.CREATE_NEW);
    }
}
