package studio.wave.audio;

import org.junit.jupiter.api.Test;
import studio.wave.model.AudioDevice;
import static org.junit.jupiter.api.Assertions.*;

class DeviceServiceTest {
    @Test void excludesSpeakerMonitorsAndKeepsOnlyRealSources() throws Exception {
        String json = """
                [
                  {"name":"mic.usb","description":"USB microphone","monitor_source":"","mute":false},
                  {"name":"speaker.monitor","description":"Monitor of speakers","monitor_source":"speaker"},
                  {"name":"unusual-monitor-name","properties":{"device.class":"monitor"}},
                  {"name":"legacy-monitor","monitor_of_sink":42},
                  {"name":"camera","description":"Camera mic","monitor_of_sink":4294967295,"mute":true}
                ]
                """;
        var devices = DeviceService.parse(json, AudioDevice.Type.INPUT, "camera");
        assertEquals(2, devices.size()); assertEquals("camera", devices.getFirst().id());
        assertTrue(devices.getFirst().systemDefault()); assertTrue(devices.getFirst().muted());
        assertTrue(devices.stream().allMatch(d -> d.type() == AudioDevice.Type.INPUT));
    }
    @Test void sinksRemainOutputsEvenWhenTheyHaveMonitorSources() throws Exception {
        var devices = DeviceService.parse("[{\"name\":\"speaker\",\"monitor_source\":\"speaker.monitor\",\"description\":\"Speakers\"}]", AudioDevice.Type.OUTPUT, "speaker");
        assertEquals(1, devices.size()); assertEquals(AudioDevice.Type.OUTPUT, devices.getFirst().type());
    }
    @Test void malformedDiscoveryIsAnErrorNotDemoDevices() {
        assertThrows(java.io.IOException.class, () -> DeviceService.parse("not json", AudioDevice.Type.INPUT, ""));
    }
}
