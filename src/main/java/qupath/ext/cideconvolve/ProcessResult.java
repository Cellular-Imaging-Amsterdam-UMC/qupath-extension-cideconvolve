package qupath.ext.cideconvolve;

public class ProcessResult {

    private final int exitCode;
    private final String output;
    private final long pid;

    public ProcessResult(int exitCode, String output) {
        this(exitCode, output, -1L);
    }

    public ProcessResult(int exitCode, String output, long pid) {
        this.exitCode = exitCode;
        this.output = output == null ? "" : output;
        this.pid = pid;
    }

    public int getExitCode() {
        return exitCode;
    }

    public String getOutput() {
        return output;
    }

    public long getPid() {
        return pid;
    }
}
