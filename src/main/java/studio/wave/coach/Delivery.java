package studio.wave.coach;

import java.util.*;
import java.util.stream.Collectors;
import static java.util.Locale.ROOT;

/** Fields follow the voice-only iamspeaker route. A missing prosody result is normal. */
public record Delivery(String sessionId, String recordingId, double durationSec, Transcript transcript, Double wpm, Integer pauseCount,
                       List<Filler> fillerWords, Prosody prosody, List<Issue> pronunciationIssues, List<Risk> riskExpressions) {
    public record Transcript(String text, List<Word> words) {}
    public record Word(String word, double startSec, double endSec, double confidence) {}
    public record Filler(String word, int count, List<Double> timestamps) {}
    public record Prosody(double pitchMedianHz, double pitchRangeSemitones, boolean monotonePitch, double dynamicsDb, double voicedRatio) {}
    public record Issue(String word, double timestamp, double confidence) {}
    public record Risk(String label, String category, int count) {}

    public boolean pitchAvailable() {
        // Upstream needs 20 voiced frames for its monotone judgement (40 ms window, 20 ms hop).
        return prosody != null && prosody.pitchMedianHz > 0 && (durationSec - .02) * 50 * prosody.voicedRatio >= 20;
    }
    Double range() { return pitchAvailable() ? prosody.pitchRangeSemitones : null; }
    Double dynamics() { return prosody != null && durationSec >= .1 ? prosody.dynamicsDb : null; }
    static String number(Number n) { return n == null ? "unavailable" : String.format(ROOT, "%.1f", n.doubleValue()); }

    public String report() {
        var lines = new ArrayList<String>();
        lines.add("Pace   " + number(wpm) + " words/min     •     Pauses ≥0.6 s   " + Objects.toString(pauseCount, "unavailable"));
        lines.add("Pitch range   " + number(range()) + " semitones     •     Median   " + number(pitchAvailable() ? prosody.pitchMedianHz : null) + " Hz");
        lines.add("Energy range   " + number(dynamics()) + " dB     •     Voiced frames   " + number(prosody == null ? null : prosody.voicedRatio * 100) + "%");
        lines.add("Fillers   " + (fillerWords.isEmpty() ? "none detected" : fillerWords.stream().map(f -> f.word + " × " + f.count).collect(Collectors.joining(", "))));
        if (!pronunciationIssues.isEmpty()) lines.add("Words to listen back to (recognizer uncertainty)   " + pronunciationIssues.stream()
                .limit(8).map(i -> i.word + " at " + number(i.timestamp) + " s").collect(Collectors.joining(", ")));
        if (!riskExpressions.isEmpty()) lines.add("Phrases to review   " + riskExpressions.stream().map(r -> r.label + " × " + r.count).collect(Collectors.joining(", ")));
        lines.add("\nPractice on this take");
        var advice = new ArrayList<String>();
        if (pitchAvailable() && prosody.monotonePitch) advice.add("Pitch stayed within " + number(range()) + " semitones. Lift one key phrase and listen back for the contrast.");
        if (!fillerWords.isEmpty()) advice.add("Try replacing one “" + fillerWords.getFirst().word + "” with a quiet beat before continuing.");
        if (wpm != null) advice.add(wpm > 180 ? "At " + number(wpm) + " words/min, leave space after the main point so a listener can follow it."
                : wpm < 110 ? "At " + number(wpm) + " words/min, try linking the words of one short phrase and compare its flow."
                : "Your pace was " + number(wpm) + " words/min. Repeat one sentence at that pace, then slow just its key phrase.");
        if (dynamics() != null) advice.add("The energy range was " + number(dynamics()) + " dB. Try stressing one word and compare it with its neighbors.");
        if (pauseCount != null) advice.add("You had " + pauseCount + " measured pauses. Listen to where they fall and place one before a new idea.");
        advice.stream().limit(3).forEach(a -> lines.add("• " + a));
        return String.join("\n", lines);
    }

    static boolean hasSpeech(String text) {
        // Whisper can return only non-speech annotations such as [BLANK_AUDIO] or (music).
        return text != null && !normalized(text.replaceAll("\\[[^\\]]*\\]|\\([^)]*\\)", "")).isEmpty();
    }
    static String normalized(String text) {
        return text.toLowerCase(ROOT).replaceAll("['’]", "").replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }
    public String compare(Delivery edited, String intended) {
        String match = normalized(intended).equals(normalized(edited.transcript.text))
                ? "Recognized words match after ignoring case and punctuation."
                : "Words differ. Listen for possible additions, omissions or substitutions.";
        String change = range() == null || edited.range() == null ? "" : String.format(ROOT,
                "\nPitch range changed by %+.1f semitones. Listen for which phrase carries the emphasis.", edited.range() - range());
        return "Original clip  →  AI-modified reference\nWords/min   " + number(wpm) + "  →  " + number(edited.wpm)
                + "\nPitch range (semitones)   " + number(range()) + "  →  " + number(edited.range())
                + "\nEnergy range (dB)   " + number(dynamics()) + "  →  " + number(edited.dynamics())
                + "\nPauses   " + Objects.toString(pauseCount, "unavailable") + "  →  " + Objects.toString(edited.pauseCount, "unavailable")
                + change + "\n\n" + match + "\nExpected: " + intended + "\nRecognized in reference: " + edited.transcript.text;
    }
}
