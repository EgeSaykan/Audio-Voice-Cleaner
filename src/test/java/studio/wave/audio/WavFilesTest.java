package studio.wave.audio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import studio.wave.AudioFixtures;
import javax.sound.sampled.AudioSystem;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class WavFilesTest {
    @TempDir Path folder;
    @Test void streamsRealPcmIntoAStandardsCompliantWav() throws Exception {
        byte[] pcm = AudioFixtures.tone(1, 440, .4); Path file = folder.resolve("take.wav");
        try (WavFiles.Writer writer = new WavFiles.Writer(file)) { writer.write(pcm, pcm.length); }
        assertEquals(96044, Files.size(file)); var info = WavFiles.inspect(file);
        assertEquals(48000, info.frames()); assertTrue(info.nativeFormat());
        try (var stream = AudioSystem.getAudioInputStream(file.toFile())) { assertArrayEquals(pcm, stream.readAllBytes()); }
        assertEquals(.4, WavFiles.waveform(file).maximum(), .001);
    }
    @Test void existingAudioIsNeverOverwritten() throws Exception {
        Path file = folder.resolve("existing.wav"); Files.writeString(file, "keep this data");
        assertThrows(FileAlreadyExistsException.class, () -> new WavFiles.Writer(file)); assertEquals("keep this data", Files.readString(file));
    }
    @Test void rejectsTruncatedAndInvalidAudio() throws Exception {
        Path file = folder.resolve("bad.wav"); Files.writeString(file, "not-a-wave-file");
        assertThrows(java.io.IOException.class, () -> WavFiles.inspect(file));
    }
}
