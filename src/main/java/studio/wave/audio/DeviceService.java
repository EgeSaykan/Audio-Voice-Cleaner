package studio.wave.audio;

import com.google.gson.*;
import studio.wave.model.AudioDevice;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class DeviceService {
    public List<AudioDevice> discover() throws IOException {
        Duration timeout = Duration.ofSeconds(4);
        String source = ProcessSupport.run(timeout, "pactl", "get-default-source").strip();
        String sink = ProcessSupport.run(timeout, "pactl", "get-default-sink").strip();
        List<AudioDevice> devices = new ArrayList<>(parse(ProcessSupport.run(timeout, "pactl", "-f", "json", "list", "sources"), AudioDevice.Type.INPUT, source));
        devices.addAll(parse(ProcessSupport.run(timeout, "pactl", "-f", "json", "list", "sinks"), AudioDevice.Type.OUTPUT, sink));
        return devices;
    }

    public static List<AudioDevice> parse(String json, AudioDevice.Type type, String defaultId) throws IOException {
        try {
            List<AudioDevice> result = new ArrayList<>();
            for (JsonElement element : JsonParser.parseString(json).getAsJsonArray()) {
                JsonObject item = element.getAsJsonObject();
                String id = text(item, "name");
                if (id.isEmpty() || type == AudioDevice.Type.INPUT && isMonitor(item)) continue;
                String name = text(item, "description");
                result.add(new AudioDevice(id, name.isBlank() ? id : name, type, id.equals(defaultId), item.has("mute") && item.get("mute").getAsBoolean()));
            }
            result.sort(Comparator.comparing(AudioDevice::systemDefault).reversed().thenComparing(AudioDevice::name));
            return List.copyOf(result);
        } catch (RuntimeException e) { throw new IOException("Could not read the system audio device list", e); }
    }

    private static boolean isMonitor(JsonObject item) {
        JsonObject properties = item.has("properties") ? item.getAsJsonObject("properties") : new JsonObject();
        String monitorOf = text(item, "monitor_of_sink");
        return text(item, "name").endsWith(".monitor") || text(properties, "device.class").equals("monitor")
                || !text(item, "monitor_source").isBlank()
                || !monitorOf.isBlank() && !List.of("4294967295", "-1", "null").contains(monitorOf);
    }

    private static String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }
}
