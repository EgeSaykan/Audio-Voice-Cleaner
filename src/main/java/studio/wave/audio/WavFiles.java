package studio.wave.audio;

import studio.wave.model.Waveform;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Streaming RIFF/WAVE I/O. The application uses 48 kHz, 16-bit mono PCM throughout. */
public final class WavFiles {
    private WavFiles() { }
    public record Info(long offset, long frames, int sampleRate, int channels, int bits) {
        public boolean nativeFormat() { return sampleRate == Waveform.SAMPLE_RATE && channels == 1 && bits == 16; }
    }

    public static Info inspect(Path path) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            if (!tag(file).equals("RIFF")) throw new IOException("Not a PCM WAV file: " + path.getFileName());
            uint(file);
            if (!tag(file).equals("WAVE")) throw new IOException("Invalid WAV header");
            int format = 0, channels = 0, rate = 0, bits = 0;
            long offset = -1, length = 0;
            while (file.getFilePointer() + 8 <= file.length()) {
                String tag = tag(file); long size = uint(file), begin = file.getFilePointer();
                if (size > file.length() - begin) throw new IOException("Truncated WAV chunk");
                if (tag.equals("fmt ") && size >= 16) {
                    format = ushort(file); channels = ushort(file); rate = (int) uint(file); uint(file); ushort(file); bits = ushort(file);
                } else if (tag.equals("data")) { offset = begin; length = size; }
                if (offset >= 0 && format != 0) break;
                file.seek(begin + size + (size & 1));
            }
            if (format != 1 || offset < 0 || channels == 0 || bits != 16) throw new IOException("WAV must be decoded to 16-bit PCM first");
            return new Info(offset, length / (channels * 2L), rate, channels, bits);
        }
    }

    public static Waveform waveform(Path path) throws IOException {
        Info info = inspect(path);
        if (!info.nativeFormat()) throw new IOException("Audio must be normalized to 48 kHz / 16-bit / mono first");
        Waveform waveform = new Waveform();
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            file.seek(info.offset()); long remaining = info.frames() * 2; byte[] block = new byte[8192];
            while (remaining > 0) {
                int n = (int) Math.min(block.length, remaining); file.readFully(block, 0, n); waveform.append(block, 0, n); remaining -= n;
            }
        }
        return waveform;
    }

    public static final class Writer implements AutoCloseable {
        private final RandomAccessFile file;
        private long bytes, checkpoint;
        private boolean closed;
        public Writer(Path path) throws IOException {
            Files.createFile(path); // reserve, and never truncate an existing recording
            file = new RandomAccessFile(path.toFile(), "rw");
            header(); file.seek(44);
        }
        public synchronized void write(byte[] pcm, int length) throws IOException {
            if (closed) throw new IOException("Recording is closed");
            if ((length & 1) != 0) throw new IOException("Incomplete PCM frame");
            if (bytes + length > 0xffff_ffffL - 36) throw new IOException("WAV reached its 4 GB limit; start another take");
            file.write(pcm, 0, length); bytes += length;
            if (bytes - checkpoint >= Waveform.SAMPLE_RATE * 2L) { long end = file.getFilePointer(); header(); file.seek(end); checkpoint = bytes; }
        }
        private void header() throws IOException {
            file.seek(0); file.writeBytes("RIFF"); writeInt(file, (int) (36 + bytes)); file.writeBytes("WAVEfmt ");
            writeInt(file, 16); writeShort(file, 1); writeShort(file, 1); writeInt(file, Waveform.SAMPLE_RATE);
            writeInt(file, Waveform.SAMPLE_RATE * 2); writeShort(file, 2); writeShort(file, 16);
            file.writeBytes("data"); writeInt(file, (int) bytes);
        }
        @Override public synchronized void close() throws IOException {
            if (closed) return;
            closed = true;
            try { header(); file.getFD().sync(); } finally { file.close(); }
        }
    }

    private static String tag(RandomAccessFile file) throws IOException { byte[] bytes = new byte[4]; file.readFully(bytes); return new String(bytes, StandardCharsets.US_ASCII); }
    private static int ushort(RandomAccessFile file) throws IOException { return Short.toUnsignedInt(Short.reverseBytes(file.readShort())); }
    private static long uint(RandomAccessFile file) throws IOException { return Integer.toUnsignedLong(Integer.reverseBytes(file.readInt())); }
    private static void writeInt(RandomAccessFile file, int value) throws IOException { file.writeInt(Integer.reverseBytes(value)); }
    private static void writeShort(RandomAccessFile file, int value) throws IOException { file.writeShort(Short.reverseBytes((short) value)); }
}
