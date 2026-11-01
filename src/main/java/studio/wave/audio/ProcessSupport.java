package studio.wave.audio;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;

/** Native commands are always argument lists, never interpolated shell commands. */
public final class ProcessSupport {
    private ProcessSupport() { }

    public static Process start(List<String> command) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.environment().put("LC_ALL", "C");
        return builder.start();
    }

    public static CompletableFuture<String> collect(InputStream stream) {
        CompletableFuture<String> future = new CompletableFuture<>();
        Thread.ofVirtual().name("wave-process-output").start(() -> {
            try (stream; ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] block = new byte[4096]; int n;
                while ((n = stream.read(block)) >= 0) if (bytes.size() < 2_000_000) bytes.write(block, 0, n);
                future.complete(bytes.toString(StandardCharsets.UTF_8));
            } catch (IOException e) { future.completeExceptionally(e); }
        });
        return future;
    }

    public static String run(Duration timeout, String... args) throws IOException {
        Process process = start(List.of(args));
        var stdout = collect(process.getInputStream());
        var stderr = collect(process.getErrorStream());
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) throw new IOException(args[0] + " timed out");
            String error = stderr.get(2, TimeUnit.SECONDS).strip();
            if (process.exitValue() != 0) throw new IOException(args[0] + ": " + error);
            return stdout.get(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new IOException("Operation interrupted", e);
        } catch (ExecutionException | TimeoutException e) { throw new IOException("Could not read " + args[0] + " output", e); }
        finally { terminate(process); }
    }

    public static void terminate(Process process) {
        if (process == null || !process.isAlive()) return;
        process.destroy();
        try { if (!process.waitFor(800, TimeUnit.MILLISECONDS)) process.destroyForcibly().waitFor(800, TimeUnit.MILLISECONDS); }
        catch (InterruptedException e) { process.destroyForcibly(); Thread.currentThread().interrupt(); }
    }
}
