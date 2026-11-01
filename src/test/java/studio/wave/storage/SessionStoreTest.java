package studio.wave.storage;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import studio.wave.AudioFixtures;
import studio.wave.model.Session;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class SessionStoreTest {
    @TempDir Path folder;
    @Test void savesRealAudioReferencesAndKeepsOriginals() throws Exception {
        Path library = Files.createDirectory(folder.resolve("library"));
        var track = AudioFixtures.track(folder.resolve("original.wav"), AudioFixtures.tone(.1, 440, .3), 2.5);
        Session session = new Session(); session.setName("A \"real\" take"); session.setFolder(library); session.tracks().add(track); session.inputs().add("mic"); session.outputs().add("speaker");
        SessionStore store = new SessionStore(); Path first = store.save(session), second = store.save(session);
        assertNotEquals(first, second); assertTrue(Files.exists(folder.resolve("original.wav")));
        var json = JsonParser.parseString(Files.readString(first)).getAsJsonObject(); assertEquals(2, json.get("version").getAsInt());
        assertFalse(json.has("uiPreview")); var item = json.getAsJsonArray("tracks").get(0).getAsJsonObject();
        Path audio = library.resolve(item.get("file").getAsString()); assertTrue(Files.isRegularFile(audio));
        assertArrayEquals(Files.readAllBytes(folder.resolve("original.wav")), Files.readAllBytes(audio)); assertEquals(2.5, item.get("startSeconds").getAsDouble());
    }
    @Test void libraryFiltersAndReadsActualWaveforms() throws Exception {
        var track = AudioFixtures.track(folder.resolve("voice.wav"), AudioFixtures.tone(.5, 220, .6), 0); Files.writeString(folder.resolve("notes.txt"), "notes");
        RecordingLibrary library = new RecordingLibrary(); assertEquals(1, library.list(folder).size());
        var loaded = library.load(track.file()); assertEquals(.5, loaded.duration()); assertEquals(.6, loaded.waveform().maximum(), .001);
    }
}
