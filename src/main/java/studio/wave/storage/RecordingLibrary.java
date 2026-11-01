package studio.wave.storage;

import studio.wave.audio.*;
import studio.wave.model.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;

public final class RecordingLibrary {
    private static final Set<String> EXTENSIONS = Set.of("wav", "wave", "mp3", "flac", "ogg", "opus", "m4a", "aif", "aiff", "aac");

    public List<Path> list(Path folder) throws IOException {
        try (Stream<Path> paths = Files.list(folder)) {
            return paths.filter(Files::isRegularFile).filter(path -> EXTENSIONS.contains(extension(path)))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT))).toList();
        }
    }

    public AudioTrack load(Path original) throws IOException {
        Path playable = original;
        boolean nativeWav;
        try { nativeWav = WavFiles.inspect(original).nativeFormat(); } catch (IOException e) { nativeWav = false; }
        if (!nativeWav) {
            // Converted PCM lives beside the library in an application-owned cache, never over the original.
            Path cache = original.toAbsolutePath().getParent().resolve(".wave-audio");
            Files.createDirectories(cache);
            playable = cache.resolve(UUID.randomUUID() + ".wav");
            try {
                ProcessSupport.run(Duration.ofMinutes(5), "ffmpeg", "-v", "error", "-nostdin", "-n", "-i", original.toAbsolutePath().toString(),
                        "-map", "0:a:0", "-vn", "-c:a", "pcm_s16le", "-ar", "48000", "-ac", "1", playable.toString());
            } catch (IOException e) {
                Files.deleteIfExists(playable);
                throw new IOException("Could not decode " + original.getFileName() + ". FFmpeg is required for this file format. " + e.getMessage(), e);
            }
        }
        return new AudioTrack(original.getFileName().toString(), "Local recording", "", 0, playable, WavFiles.waveform(playable));
    }

    public static String extension(Path path) {
        String name = path.getFileName().toString(); int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
