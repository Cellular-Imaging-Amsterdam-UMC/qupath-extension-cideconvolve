package qupath.ext.cideconvolve;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public class CIDeconvolveResult {

    public enum Status {
        SUCCESS,
        FAILED,
        CANCELLED
    }

    private final Status status;
    private final List<Path> inputPaths;
    private final List<Path> outputPaths;
    private final List<String> logs;
    private final List<String> failures;
    private final Duration duration;

    CIDeconvolveResult(
            Status status,
            List<Path> inputPaths,
            List<Path> outputPaths,
            List<String> logs,
            List<String> failures,
            Duration duration
    ) {
        this.status = status;
        this.inputPaths = List.copyOf(inputPaths);
        this.outputPaths = List.copyOf(outputPaths);
        this.logs = List.copyOf(logs);
        this.failures = List.copyOf(failures);
        this.duration = duration;
    }

    public Status getStatus() {
        return status;
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public List<Path> getInputPaths() {
        return inputPaths;
    }

    public List<Path> getOutputPaths() {
        return outputPaths;
    }

    public List<String> getLogs() {
        return logs;
    }

    public List<String> getFailures() {
        return failures;
    }

    public Duration getDuration() {
        return duration;
    }
}
