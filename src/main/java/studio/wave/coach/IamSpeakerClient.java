package studio.wave.coach;

import com.google.gson.*;
import java.io.IOException;
import java.net.http.HttpRequest.BodyPublishers;
import java.nio.file.Path;
import java.util.*;

final class IamSpeakerClient implements VoiceImpactService.Analysis {
    private final CoachConfig config;
    IamSpeakerClient(CoachConfig config) { this.config = config; }

    public Delivery analyze(Path wav, double seconds) throws IOException, InterruptedException {
        var http = new CoachHttp(config.speaker(), "iamspeaker", config);
        var gson = new Gson();
        var session = Map.of("targetDurationSec", Math.max(1, Math.round(seconds)), "tone", "casual", "language", "en",
                "genre", "talk", "voiceOnly", true, "slides", List.of(Map.of("textContent", "Voice practice")));
        var created = http.json(http.request("/api/sessions").header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(gson.toJson(session))).build());
        String sessionPath = "/api/sessions/" + http.id(created, "id");
        var upload = http.upload(sessionPath + "/recordings", wav, Map.of("scriptVersion", "0", "transitions", "[]"));
        String recording = http.id(upload, "recordingId");
        http.await("/api/jobs/" + http.id(upload, "jobId"));
        var data = http.json(http.request("/api/recordings/" + recording + "/analysis").GET().build());
        Delivery result = gson.fromJson(data, Delivery.class);
        if (result == null || !recording.equals(result.recordingId()) || result.transcript() == null)
            throw new IOException("Install the pinned iamspeaker voice-only patch to return transcripts and measurements.");
        if (!Delivery.hasSpeech(result.transcript().text()))
            throw new IOException("Transcription failed: no speech was recognized.");
        if (!(result.durationSec() > 0) || result.fillerWords() == null || result.pronunciationIssues() == null || result.riskExpressions() == null)
            throw new IOException("iamspeaker returned incomplete analysis");
        return result;
    }
}
