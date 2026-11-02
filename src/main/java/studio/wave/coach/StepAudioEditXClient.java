package studio.wave.coach;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

final class StepAudioEditXClient implements VoiceImpactService.Enhancement {
    private final CoachConfig config;
    StepAudioEditXClient(CoachConfig config) { this.config = config; }
    public void ready() throws IOException, InterruptedException {
        var http = new CoachHttp(config.editor(), "Step-Audio-EditX", config);
        var health = http.json(http.request("/v1/ready").GET().build());
        if (!health.has("ready") || !health.get("ready").isJsonPrimitive() || !health.get("ready").getAsBoolean())
            throw new IOException("Step-Audio-EditX is running but the model is not ready.");
    }
    public void edit(Path audio, String transcript, String preset, int iterations, Path output) throws IOException, InterruptedException {
        var http = new CoachHttp(config.editor(), "Step-Audio-EditX", config);
        var request = http.upload("/v1/edit", audio, Map.of("transcript", transcript, "preset", preset, "iterations", String.valueOf(iterations)));
        String path = "/v1/jobs/" + http.id(request, "jobId");
        http.await(path);
        http.download(path + "/audio", output);
    }
}
