package studio.wave.audio;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import studio.wave.AudioFixtures;
import studio.wave.model.*;
import javax.sound.sampled.AudioSystem;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** An actual audio-server round trip. It never plays a tone through the user's physical speakers. */
@Tag("audio-integration")
class PipeWireIntegrationTest {
    @TempDir Path folder;

    @Test void capturesTheSelectedDeviceSignalAndRoutesPlaybackToTheSelectedSink() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String sink = "wave_test_signal_" + suffix, silentSink = "wave_test_silence_" + suffix;
        List<String> modules = new ArrayList<>();
        try (RecordingService recorder = new RecordingService(); PlaybackService player = new PlaybackService()) {
            modules.add(loadSink(sink)); modules.add(loadSink(silentSink));
            AudioDevice input = new AudioDevice(sink + ".monitor", "Integration signal", AudioDevice.Type.INPUT, false, false);
            AudioDevice silentInput = new AudioDevice(silentSink + ".monitor", "Integration silence", AudioDevice.Type.INPUT, false, false);
            AudioDevice output = new AudioDevice(sink, "Integration output", AudioDevice.Type.OUTPUT, false, false);
            CompletableFuture<Throwable> captureFailure = new CompletableFuture<>();
            var takes = recorder.start(List.of(input, silentInput), folder, 0, captureFailure::complete);
            var fixture = AudioFixtures.track(folder.resolve("reference.wav"), AudioFixtures.tone(1.2, 440, .35), 0);
            CompletableFuture<Throwable> completed = new CompletableFuture<>(); player.start(List.of(fixture), List.of(output), 0, completed::complete);
            assertNull(completed.get(8, TimeUnit.SECONDS), "playback must finish without error");
            Thread.sleep(160); recorder.stop();
            assertFalse(captureFailure.isDone(), "capture must stop cleanly");
            assertEquals(2, takes.size());
            AudioTrack recorded = takes.get(0), silence = takes.get(1);
            assertTrue(recorded.duration() >= 1.2); assertTrue(recorded.waveform().maximum() > .30, "tone must reach the selected input");
            assertEquals(0, silence.waveform().maximum(), .0001, "the unselected output must stay silent");
            assertEquals(recorded.waveform().frames(), WavFiles.inspect(recorded.file()).frames(), "WAV duration must match captured sample count");
            assertEquals(recorded.waveform().maximum(), WavFiles.waveform(recorded.file()).maximum(), .00001, "displayed waveform must match saved audio");
            byte[] pcm;
            try (var audio = AudioSystem.getAudioInputStream(recorded.file().toFile())) { pcm = audio.readAllBytes(); }
            assertTrue(energy(pcm, 440) > 100 * energy(pcm, 733), "captured audio must contain the known 440 Hz signal, not generated/random data");
            System.out.printf(Locale.ROOT, "REAL PIPEWIRE ROUND TRIP: %.2f sec, %d frames, peak %.4f; secondary input peak %.4f%n", recorded.duration(), recorded.waveform().frames(), recorded.waveform().maximum(), silence.waveform().maximum());
        } finally {
            Collections.reverse(modules);
            for (String module : modules) if (module.matches("\\d+")) ProcessSupport.run(Duration.ofSeconds(4), "pactl", "unload-module", module);
        }
    }

    @Test void unavailableInputProducesAnErrorInsteadOfFakeRecording() throws Exception {
        try (RecordingService recorder = new RecordingService()) {
            AudioDevice missing = new AudioDevice("wave_nonexistent_" + UUID.randomUUID(), "Missing input", AudioDevice.Type.INPUT, false, false);
            assertThrows(java.io.IOException.class, () -> recorder.start(List.of(missing), folder, 0, failure -> {}));
        }
    }

    @Test void unavailableOutputDoesNotFallBackToTheUsersSpeakers() throws Exception {
        try (PlaybackService player = new PlaybackService()) {
            var fixture = AudioFixtures.track(folder.resolve("reference.wav"), AudioFixtures.tone(.1, 440, .2), 0);
            AudioDevice missing = new AudioDevice("wave_nonexistent_" + UUID.randomUUID(), "Missing output", AudioDevice.Type.OUTPUT, false, false);
            assertThrows(java.io.IOException.class, () -> player.start(List.of(fixture), List.of(missing), 0, failure -> {}));
        }
    }

    @Test void disconnectStopsCaptureAndKeepsTheRecordedWav() throws Exception {
        String sink = "wave_test_disconnect_" + UUID.randomUUID().toString().substring(0, 8);
        String module = loadSink(sink);
        try (RecordingService recorder = new RecordingService()) {
            CompletableFuture<Throwable> failure = new CompletableFuture<>();
            AudioDevice device = new AudioDevice(sink + ".monitor", "Disconnect test", AudioDevice.Type.INPUT, false, false);
            var take = recorder.start(List.of(device), folder, 0, failure::complete).getFirst();
            ProcessSupport.run(Duration.ofSeconds(4), "pactl", "unload-module", module); module = null;
            assertNotNull(failure.get(5, TimeUnit.SECONDS));
            assertThrows(java.io.IOException.class, recorder::stop);
            assertTrue(WavFiles.inspect(take.file()).frames() > 0);
            assertFalse(take.recording());
        } finally {
            if (module != null) ProcessSupport.run(Duration.ofSeconds(4), "pactl", "unload-module", module);
        }
    }

    private static String loadSink(String name) throws Exception {
        return ProcessSupport.run(Duration.ofSeconds(4), "pactl", "load-module", "module-null-sink", "sink_name=" + name, "rate=48000", "channels=1", "channel_map=mono").strip();
    }
    private static double energy(byte[] data, double frequency) {
        double real = 0, imaginary = 0;
        for (int i = 0; i < data.length / 2; i++) {
            double sample = (short) ((data[i * 2] & 255) | data[i * 2 + 1] << 8) / 32768.0;
            double angle = 2 * Math.PI * frequency * i / 48000;
            real += sample * Math.cos(angle); imaginary += sample * Math.sin(angle);
        }
        return real * real + imaginary * imaginary;
    }
}
