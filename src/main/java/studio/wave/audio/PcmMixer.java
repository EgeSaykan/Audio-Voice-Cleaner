package studio.wave.audio;

import studio.wave.model.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

/** Disk-streamed timeline mixing, including clip offsets, seek position, mute and saturation. */
public final class PcmMixer implements AutoCloseable {
    private record Source(AudioTrack track, RandomAccessFile file, WavFiles.Info info) { }
    private final List<Source> sources = new ArrayList<>();
    private long position, end;

    public PcmMixer(List<AudioTrack> tracks, double startSeconds) throws IOException {
        position = Math.max(0, Math.round(startSeconds * Waveform.SAMPLE_RATE));
        try {
            for (AudioTrack track : tracks) {
                WavFiles.Info info = WavFiles.inspect(track.file());
                if (!info.nativeFormat()) throw new IOException("Unsupported track format: " + track.name());
                sources.add(new Source(track, new RandomAccessFile(track.file().toFile(), "r"), info));
                end = Math.max(end, Math.round(track.start() * Waveform.SAMPLE_RATE) + info.frames());
            }
        } catch (IOException e) { close(); throw e; }
    }

    /** Returns a whole-frame byte count, or -1 at the end of the timeline. */
    public int read(byte[] pcm) throws IOException {
        int frames = (int) Math.min(pcm.length / 2, end - position);
        if (frames <= 0) return -1;
        int[] mixed = new int[frames];
        byte[] sourceBytes = new byte[frames * 2];
        for (Source source : sources) {
            if (source.track.muted()) continue;
            long trackStart = Math.round(source.track.start() * Waveform.SAMPLE_RATE);
            long from = Math.max(position, trackStart), to = Math.min(position + frames, trackStart + source.info.frames());
            if (from >= to) continue;
            int count = (int) (to - from), offset = (int) (from - position);
            source.file.seek(source.info.offset() + (from - trackStart) * 2);
            source.file.readFully(sourceBytes, 0, count * 2);
            for (int i = 0; i < count; i++) mixed[offset + i] += (short) ((sourceBytes[i * 2] & 255) | (sourceBytes[i * 2 + 1] << 8));
        }
        for (int i = 0; i < frames; i++) {
            int sample = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, mixed[i]));
            pcm[i * 2] = (byte) sample; pcm[i * 2 + 1] = (byte) (sample >> 8);
        }
        position += frames;
        return frames * 2;
    }

    @Override public void close() throws IOException {
        IOException failure = null;
        for (Source source : sources) try { source.file.close(); } catch (IOException e) { failure = e; }
        sources.clear();
        if (failure != null) throw failure;
    }
}
