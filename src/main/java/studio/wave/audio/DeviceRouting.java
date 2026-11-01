package studio.wave.audio;

import com.google.gson.*;
import studio.wave.model.AudioDevice;
import java.io.IOException;
import java.time.Duration;

/** Guards against the audio server silently falling back to a different device. */
public final class DeviceRouting {
    private DeviceRouting() { }

    public static String resolve(AudioDevice device) throws IOException {
        String list = device.type() == AudioDevice.Type.INPUT ? "sources" : "sinks";
        JsonArray devices = JsonParser.parseString(ProcessSupport.run(Duration.ofSeconds(3), "pactl", "-f", "json", "list", list)).getAsJsonArray();
        for (JsonElement item : devices) {
            JsonObject value = item.getAsJsonObject();
            if (value.get("name").getAsString().equals(device.id())) return value.get("index").getAsString();
        }
        throw new IOException("Device is no longer available: " + device.name() + ". Refresh the device list.");
    }

    public static void verify(Process process, AudioDevice.Type type, String expectedIndex) throws IOException {
        String list = type == AudioDevice.Type.INPUT ? "source-outputs" : "sink-inputs";
        String endpoint = type == AudioDevice.Type.INPUT ? "source" : "sink";
        long deadline = System.nanoTime() + 3_000_000_000L;
        do {
            JsonArray streams = JsonParser.parseString(ProcessSupport.run(Duration.ofSeconds(2), "pactl", "-f", "json", "list", list)).getAsJsonArray();
            for (JsonElement element : streams) {
                JsonObject stream = element.getAsJsonObject();
                JsonObject properties = stream.getAsJsonObject("properties");
                JsonElement pid = properties == null ? null : properties.get("application.process.id");
                if (pid != null && pid.getAsString().equals(Long.toString(process.pid()))) {
                    JsonElement target = stream.get(endpoint);
                    String actual = target == null || target.isJsonNull() ? "" : target.getAsString();
                    // A newly created playback stream can exist before the session manager links it.
                    if (actual.isBlank() || actual.equals("4294967295") || actual.equals("-1")) continue;
                    if (!actual.equals(expectedIndex)) throw new IOException("The audio server routed the stream to a different device (expected " + expectedIndex + ", connected " + actual + "); operation stopped.");
                    return;
                }
            }
            if (!process.isAlive()) throw new IOException("Audio device connection failed");
            try { Thread.sleep(25); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Device connection interrupted", e); }
        } while (System.nanoTime() < deadline);
        throw new IOException("Could not verify the selected audio device connection");
    }
}
