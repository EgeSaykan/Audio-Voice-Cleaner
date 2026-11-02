package studio.wave.coach;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import studio.wave.AudioFixtures;
import studio.wave.audio.WavFiles;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class VoiceImpactServiceTest {
    @TempDir Path folder;
    private HttpServer server;
    private CoachConfig config;
    private final List<String> requests = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final AtomicInteger polls = new AtomicInteger();
    private final AtomicReference<String> finalStatus = new AtomicReference<>("succeeded");
    private byte[] modelWav;

    @BeforeEach void localContracts() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::reply); server.start();
        URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        config = new CoachConfig(base, base, "ffmpeg", 1048576, 30, Duration.ofMillis(5), Duration.ofSeconds(8), folder.resolve("results"));
    }
    @AfterEach void stopServer() { server.stop(0); }
    private void reply(HttpExchange x) throws IOException {
        String path = x.getRequestURI().getPath();
        String body = new String(x.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1);
        requests.add(x.getRequestMethod() + " " + path + " " + body);
        String json = switch (path) {
            case "/api/sessions" -> "{\"id\":\"s1\"}";
            case "/api/sessions/s1/recordings" -> "{\"recordingId\":\"r1\",\"jobId\":\"j1\"}";
            case "/v1/ready" -> "{\"ready\":true}";
            case "/v1/edit" -> "{\"jobId\":\"e1\",\"status\":\"queued\"}";
            case "/v1/jobs/e1", "/api/jobs/j1" -> "{\"status\":\"" + (polls.getAndIncrement() == 0 ? "running" : finalStatus.get()) + "\",\"error\":\"backend failed\"}";
            case "/api/recordings/r1/analysis" -> """
                    {"recordingId":"r1","durationSec":1,"transcript":{"text":"Hello world","words":[]},"wpm":120,
                    "fillerWords":[],"pauseCount":0,"prosody":{"pitchMedianHz":182,"pitchRangeSemitones":2.1,"dynamicsDb":12.4,
                    "monotonePitch":true,"voicedRatio":0.8},"riskExpressions":[],"pronunciationIssues":[]}
                    """;
            default -> "{}";
        };
        byte[] response = path.endsWith("/audio") ? modelWav : json.getBytes(StandardCharsets.UTF_8);
        x.sendResponseHeaders(200, response.length);
        try (var output = x.getResponseBody()) { output.write(response); }
    }

    @Test void voiceOnlyJobAndEditRoundTrip() throws Exception {
        Path source = folder.resolve("original recording.wav");
        AudioFixtures.track(source, AudioFixtures.tone(2, 170, .4), 0);
        byte[] untouched = Files.readAllBytes(source);
        try (var service = new VoiceImpactService(config)) {
            var take = service.analyze(source, .25, 1d, s -> {});
            assertEquals(1, take.seconds(), .001); assertTrue(take.report().report().contains("2.1"));
            modelWav = Files.readAllBytes(take.playback()); service.editorReady();
            var a = service.enhance(take, "Hello moon", "More expressive", 1, s -> {});
            var b = service.enhance(take, "hello WORLD!", "Warmer", 2, s -> {});
            assertNotEquals(a.audio(), b.audio()); assertTrue(WavFiles.inspect(a.audio()).nativeFormat());
            assertTrue(Files.readString(a.audio().resolveSibling("comparison.json")).contains("Words differ"));
            assertTrue(take.report().compare(b.report(), "hello WORLD!").contains("words match"));
            assertArrayEquals(untouched, Files.readAllBytes(source));
            assertTrue(requests.stream().anyMatch(s -> s.startsWith("POST /api/sessions ") && s.contains("\"voiceOnly\":true")));
            assertTrue(requests.stream().anyMatch(s -> s.contains("name=\"scriptVersion\"\r\n\r\n0") && s.contains("name=\"transitions\"\r\n\r\n[]")));
            assertTrue(requests.stream().anyMatch(s -> s.startsWith("POST /v1/edit ") && s.contains("Hello moon") && s.contains("More expressive")));
            assertTrue(polls.get() >= 6);
            modelWav = new byte[12];
            assertThrows(IOException.class, () -> service.enhance(take, "Hello", "Warmer", 1, s -> {}));
            assertTrue(Files.exists(a.audio()));
            try (var files = Files.list(config.output())) { assertEquals(2, files.count()); }
        }
    }
    @Test void recognizerNoiseMarkersAreNotWords() {
        assertFalse(Delivery.hasSpeech("[BLANK_AUDIO] (music)"));
        assertTrue(Delivery.hasSpeech("I hear you."));
    }
    @Test void badAudioAndBadSectionBeforeUpload() throws Exception {
        try (var service = new VoiceImpactService(config)) {
            Path file = folder.resolve("bad.wav");
            assertThrows(IOException.class, () -> service.analyze(file, 0, null, s -> {}));
            Files.writeString(file, "broken");
            assertThrows(IOException.class, () -> service.analyze(file, 0, null, s -> {}));
            Files.delete(file);
            AudioFixtures.track(file, AudioFixtures.tone(1, 200, .4), 0);
            assertThrows(IOException.class, () -> service.analyze(file, .5, 1d, s -> {}));
            assertThrows(IOException.class, () -> service.analyze(file, 0, 31d, s -> {}));
            assertTrue(requests.isEmpty());
        }
    }
    @Test void failedJobNeverRetrievesResults() throws Exception {
        finalStatus.set("failed");
        Path file = folder.resolve("clip.wav"); AudioFixtures.track(file, AudioFixtures.tone(1, 200, .4), 0);
        var failure = assertThrows(IOException.class, () -> new IamSpeakerClient(config).analyze(file, 1));
        assertTrue(failure.getMessage().contains("backend failed"));
        assertFalse(requests.stream().anyMatch(s -> s.contains("/analysis ")));
    }
    @Test void pollingDeadlineAndOfflineServiceAreReported() throws Exception {
        finalStatus.set("running");
        var shortConfig = new CoachConfig(config.speaker(), config.editor(), "ffmpeg", 10000, 30, Duration.ofMillis(10), Duration.ofMillis(120), folder);
        var error = assertThrows(IOException.class, () -> new CoachHttp(config.editor(), "EditX", shortConfig).await("/v1/jobs/e1"));
        assertTrue(error.getMessage().contains("timed out"));
        server.stop(0);
        assertTrue(assertThrows(IOException.class, () -> new StepAudioEditXClient(config).ready()).getMessage().contains("unavailable"));
    }

    @Test @EnabledIfEnvironmentVariable(named="WAVE_SPEECH_SAMPLE", matches=".+")
    void actualLocalSpeechAnalysis() throws Exception {
        try (var service = new VoiceImpactService(CoachConfig.load())) {
            var take = service.analyze(Path.of(System.getenv("WAVE_SPEECH_SAMPLE")), 0, null, s -> {});
            var result = take.report(); assertFalse(result.transcript().text().isBlank()); assertTrue(result.wpm() > 0);
            assertTrue(result.transcript().words().stream().anyMatch(w -> w.endSec() > w.startSec()));
            assertTrue(result.pitchAvailable());
            System.out.printf(Locale.ROOT, "Real local analysis: %.2f seconds; %.1f WPM; %.1f Hz median; %.1f semitones; %d pauses.%n",
                    take.seconds(), result.wpm(), result.prosody().pitchMedianHz(), result.range(), result.pauseCount());
        }
    }
}
