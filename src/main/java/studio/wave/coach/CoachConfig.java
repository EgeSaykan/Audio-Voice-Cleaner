package studio.wave.coach;

import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

public record CoachConfig(URI speaker, URI editor, String ffmpeg, long uploadBytes, double clipLimit,
                          Duration poll, Duration timeout, Path output) {
    public static CoachConfig load() throws IOException {
        var properties = new Properties();
        Path path = Path.of(System.getProperty("wave.coach.config", "coach.properties"));
        if (Files.exists(path)) try (var reader = Files.newBufferedReader(path)) { properties.load(reader); }
        try {
            var config = new CoachConfig(local(properties.getProperty("iamspeaker.url", "http://127.0.0.1:3000")),
                    local(properties.getProperty("editx.url", "http://127.0.0.1:7861")),
                    properties.getProperty("ffmpeg", "ffmpeg"), Math.multiplyExact(1048576L, Long.parseLong(properties.getProperty("upload.mb", "50"))),
                    Double.parseDouble(properties.getProperty("clip.seconds", "30")),
                    Duration.ofMillis(Long.parseLong(properties.getProperty("poll.ms", "750"))),
                    Duration.ofSeconds(Long.parseLong(properties.getProperty("timeout.seconds", "600"))),
                    Path.of(properties.getProperty("output.dir", ".voice-coach")));
            if (config.uploadBytes < 1 || !(config.clipLimit > 0 && config.clipLimit <= 30)
                    || !config.poll.isPositive() || !config.timeout.isPositive()) throw new IllegalArgumentException("Check size and time limits");
            return config;
        } catch (IllegalArgumentException | ArithmeticException e) { throw new IOException("Invalid voice coach settings: " + e.getMessage(), e); }
    }

    private static URI local(String address) {
        URI uri = URI.create(address);
        if (!"http".equals(uri.getScheme()) || !Set.of("localhost", "127.0.0.1", "[::1]").contains(Objects.toString(uri.getHost(), ""))
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null || !Set.of("", "/").contains(uri.getPath()))
            throw new IllegalArgumentException("Use a local HTTP address. For another computer, use an SSH tunnel.");
        return uri;
    }
}
