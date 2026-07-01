package qupath.ext.cideconvolve;

public class PythonValidationResult {

    private static final String MIN_CIDECONVOLVE_VERSION = "0.2.0";

    private final int exitCode;
    private final String output;

    public PythonValidationResult(int exitCode, String output) {
        this.exitCode = exitCode;
        this.output = output == null ? "" : output;
    }

    public int getExitCode() {
        return exitCode;
    }

    public String getOutput() {
        return output;
    }

    public boolean isValid() {
        return exitCode == 0
                && output.contains("CIDECONVOLVE_OK")
                && compareVersions(value("ci_deconvolve"), MIN_CIDECONVOLVE_VERSION) >= 0;
    }

    public String getMessage() {
        if (isValid())
            return output;
        String version = value("ci_deconvolve");
        if (exitCode == 0 && output.contains("CIDECONVOLVE_OK") && compareVersions(version, MIN_CIDECONVOLVE_VERSION) < 0)
            return "ci-deconvolve " + MIN_CIDECONVOLVE_VERSION + " or newer is required, found "
                    + (version == null || version.isBlank() ? "unknown" : version) + ".\n\n" + output;
        if (output.isBlank())
            return "Python validation failed with exit code " + exitCode;
        return output;
    }

    String value(String key) {
        String prefix = key + "=";
        for (String line : output.split("\\R")) {
            if (line.startsWith(prefix))
                return line.substring(prefix.length()).trim();
        }
        return null;
    }

    private static int compareVersions(String version, String minimum) {
        if (version == null || version.isBlank())
            return -1;
        String[] a = version.split("[^0-9]+");
        String[] b = minimum.split("[^0-9]+");
        int n = Math.max(a.length, b.length);
        for (int i = 0; i < n; i++) {
            int ai = i < a.length && !a[i].isBlank() ? Integer.parseInt(a[i]) : 0;
            int bi = i < b.length && !b[i].isBlank() ? Integer.parseInt(b[i]) : 0;
            if (ai != bi)
                return Integer.compare(ai, bi);
        }
        return 0;
    }
}
