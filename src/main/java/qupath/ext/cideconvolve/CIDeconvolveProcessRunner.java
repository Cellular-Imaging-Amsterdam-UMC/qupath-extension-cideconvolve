package qupath.ext.cideconvolve;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

public class CIDeconvolveProcessRunner {

    private volatile Process currentProcess;
    private volatile long currentPid = -1L;

    public ProcessResult run(List<String> command, Consumer<String> logConsumer, BooleanSupplier cancelRequested)
            throws IOException, InterruptedException {
        return run(command, logConsumer, cancelRequested, pid -> {
        });
    }

    public ProcessResult run(
            List<String> command,
            Consumer<String> logConsumer,
            BooleanSupplier cancelRequested,
            LongConsumer pidConsumer
    )
            throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        currentProcess = process;
        currentPid = process.pid();
        pidConsumer.accept(currentPid);

        StringBuilder output = new StringBuilder();
        Thread readerThread = new Thread(() -> readOutput(process, logConsumer, output), "cideconvolve-output");
        readerThread.setDaemon(true);
        readerThread.start();

        try {
            while (process.isAlive()) {
                if (cancelRequested.getAsBoolean()) {
                    logConsumer.accept("Cancel requested; terminating Python process tree for PID " + process.pid());
                    destroyProcessTree(process);
                    if (!process.waitFor(2, TimeUnit.SECONDS))
                        process.destroyForcibly();
                    break;
                }
                process.waitFor(200, TimeUnit.MILLISECONDS);
            }

            int exitCode = process.waitFor();
            readerThread.join(2000);
            return new ProcessResult(exitCode, output.toString(), process.pid());
        } catch (InterruptedException e) {
            logConsumer.accept("Interrupted; terminating Python process tree for PID " + process.pid());
            destroyProcessTree(process);
            Thread.currentThread().interrupt();
            throw e;
        } finally {
            currentProcess = null;
            currentPid = -1L;
        }
    }

    public OptionalLong currentPid() {
        long pid = currentPid;
        return pid > 0 ? OptionalLong.of(pid) : OptionalLong.empty();
    }

    public void cancel() {
        Process process = currentProcess;
        if (process != null)
            destroyProcessTree(process);
    }

    private static void destroyProcessTree(Process process) {
        ProcessHandle handle = process.toHandle();
        handle.descendants().forEach(child -> {
            try {
                child.destroy();
            } catch (IllegalStateException ignored) {
            }
        });
        process.destroy();
        destroyProcessTreeWithTaskkill(handle.pid());
        handle.descendants().forEach(child -> {
            if (child.isAlive()) {
                try {
                    child.destroyForcibly();
                } catch (IllegalStateException ignored) {
                }
            }
        });
    }

    private static void destroyProcessTreeWithTaskkill(long pid) {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win"))
            return;
        try {
            Process taskkill = new ProcessBuilder(
                    "taskkill", "/PID", Long.toString(pid), "/T", "/F")
                    .redirectErrorStream(true)
                    .start();
            taskkill.waitFor(2, TimeUnit.SECONDS);
        } catch (IOException ignored) {
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void readOutput(Process process, Consumer<String> logConsumer, StringBuilder output) {
        try (var reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                synchronized (output) {
                    output.append(line).append(System.lineSeparator());
                }
                logConsumer.accept(line);
            }
        } catch (IOException e) {
            logConsumer.accept("Error reading process output: " + e.getMessage());
        }
    }
}
