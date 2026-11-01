package studio.wave.model;

/** A routable PipeWire/PulseAudio endpoint; monitor sources never enter the input catalog. */
public record AudioDevice(String id, String name, Type type, boolean systemDefault, boolean muted) {
    public enum Type { INPUT, OUTPUT }

    public String detail() {
        return (type == Type.INPUT ? "Microphone / audio input" : "Playback destination")
                + (muted ? " · system muted" : "");
    }
}
