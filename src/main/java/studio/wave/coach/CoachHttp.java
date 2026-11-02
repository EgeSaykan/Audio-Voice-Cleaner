package studio.wave.coach;

import com.google.gson.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static java.net.http.HttpRequest.BodyPublishers;

/** Local HTTP transport with one deadline shared by all requests for a backend job. */
final class CoachHttp {
    private static final HttpClient CLIENT = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(3))
            .proxy(java.net.ProxySelector.of(null)).build();
    final URI base;
    private final String name;
    private final CoachConfig config;
    private final long started = System.nanoTime();

    CoachHttp(URI base, String name, CoachConfig config) { this.base = base; this.name = name; this.config = config; }
    private Duration remaining() throws IOException {
        var left = config.timeout().minusNanos(System.nanoTime() - started);
        if (!left.isPositive()) throw new IOException(name + " job timed out");
        return left;
    }
    HttpRequest.Builder request(String path) throws IOException {
        return HttpRequest.newBuilder(base.resolve(path)).timeout(remaining());
    }
    private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) throws IOException, InterruptedException {
        try { return CLIENT.send(request, handler); }
        catch (java.net.ConnectException e) { throw new IOException(name + " is unavailable at " + base + ". Start the local service.", e); }
        catch (HttpTimeoutException e) { throw new IOException(name + " timed out", e); }
    }
    JsonObject json(HttpRequest req) throws IOException, InterruptedException {
        var response = send(req, HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofString(), 8 * 1024 * 1024));
        try {
            JsonObject obj = JsonParser.parseString(response.body()).getAsJsonObject();
            if (response.statusCode() / 100 != 2) throw new IOException(name + " HTTP " + response.statusCode() + " " + (obj.has("detail") ? obj.get("detail") : obj.get("error")));
            return obj;
        } catch (JsonParseException | IllegalStateException e) { throw new IOException(name + " returned an invalid response (HTTP " + response.statusCode() + ")", e); }
    }
    String id(JsonObject object, String key) throws IOException {
        var value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsString().matches("[A-Za-z0-9_-]{1,100}"))
            throw new IOException(name + " returned an invalid " + key);
        return value.getAsString();
    }
    JsonObject upload(String path, Path audio, Map<String, String> fields) throws IOException, InterruptedException {
        if (Files.size(audio) > config.uploadBytes()) throw new IOException("Prepared audio exceeds the upload limit");
        String boundary = "wave-" + UUID.randomUUID();
        List<HttpRequest.BodyPublisher> parts = new ArrayList<>();
        fields.forEach((key, value) -> parts.add(BodyPublishers.ofString("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + key + "\"\r\n\r\n" + value + "\r\n")));
        parts.add(BodyPublishers.ofString("--" + boundary + "\r\nContent-Disposition: form-data; name=\"audio\"; filename=\"recording.wav\"\r\nContent-Type: audio/wav\r\n\r\n"));
        parts.add(BodyPublishers.ofFile(audio));
        parts.add(BodyPublishers.ofString("\r\n--" + boundary + "--\r\n"));
        return json(request(path).header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(BodyPublishers.concat(parts.toArray(HttpRequest.BodyPublisher[]::new))).build());
    }
    void await(String path) throws IOException, InterruptedException {
        while (true) {
            var job = json(request(path).GET().build());
            switch (id(job, "status")) {
                case "succeeded" -> { return; }
                case "queued", "running" -> Thread.sleep(Duration.ofMillis(Math.min(config.poll().toMillis(), remaining().toMillis())));
                case "failed" -> throw new IOException(name + " processing failed: " + job.get("error"));
                default -> throw new IOException(name + " returned an unknown job status");
            }
        }
    }
    void download(String path, Path output) throws IOException, InterruptedException {
        var result = send(request(path).GET().build(), HttpResponse.BodyHandlers.limiting(
                HttpResponse.BodyHandlers.ofFile(output, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE), config.uploadBytes()));
        if (result.statusCode() != 200 || Files.size(output) > config.uploadBytes()) throw new IOException(name + " returned an invalid audio download");
    }
}
