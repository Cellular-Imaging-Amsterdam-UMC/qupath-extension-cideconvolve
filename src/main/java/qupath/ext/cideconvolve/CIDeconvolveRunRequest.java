package qupath.ext.cideconvolve;

import java.nio.file.Path;

public class CIDeconvolveRunRequest {

    private final Path outputDirectory;
    private final CIDeconvolveParameters parameters;

    public CIDeconvolveRunRequest(Path outputDirectory, CIDeconvolveParameters parameters) {
        this.outputDirectory = outputDirectory;
        this.parameters = parameters;
    }

    public Path getOutputDirectory() {
        return outputDirectory;
    }

    public CIDeconvolveParameters getParameters() {
        return parameters;
    }
}
