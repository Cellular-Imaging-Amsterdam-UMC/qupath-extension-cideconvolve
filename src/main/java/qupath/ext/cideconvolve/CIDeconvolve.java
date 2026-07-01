package qupath.ext.cideconvolve;

import javafx.application.Platform;
import qupath.lib.gui.QuPathGUI;

import java.nio.file.Path;

public final class CIDeconvolve {

    private CIDeconvolve() {
    }

    public static Builder builder(QuPathGUI qupath) {
        return new Builder(qupath);
    }

    public static CIDeconvolveParameters defaultParameters() {
        return new CIDeconvolveParameters();
    }

    public static final class Builder {
        private final QuPathGUI qupath;
        private final CIDeconvolveParameters parameters = new CIDeconvolveParameters();
        private Path outputDirectory;

        private Builder(QuPathGUI qupath) {
            this.qupath = qupath;
        }

        public Builder outputDirectory(String outputDirectory) {
            return outputDirectory(Path.of(outputDirectory));
        }

        public Builder outputDirectory(Path outputDirectory) {
            this.outputDirectory = outputDirectory;
            return this;
        }

        public Builder outputFormat(String outputFormat) {
            parameters.setOutputFormat(outputFormat);
            return this;
        }

        public Builder fullImage() {
            parameters.setRoiMode(CIDeconvolveParameters.ROI_FULL_IMAGE);
            return this;
        }

        public Builder selectedObjects() {
            parameters.setRoiMode(CIDeconvolveParameters.ROI_SELECTED);
            return this;
        }

        public Builder annotationClass(String annotationClass) {
            parameters.setRoiMode(CIDeconvolveParameters.ROI_ANNOTATION_CLASS);
            parameters.setAnnotationClass(annotationClass);
            return this;
        }

        public Builder logToDialog() {
            parameters.setLogTarget(CIDeconvolveParameters.LOG_DIALOG);
            return this;
        }

        public Builder logToQuPathLog() {
            parameters.setLogTarget(CIDeconvolveParameters.LOG_QUPATH);
            return this;
        }

        public Builder omeZarr() {
            return outputFormat(CIDeconvolveParameters.FORMAT_OME_ZARR);
        }

        public Builder omeTiff() {
            return outputFormat(CIDeconvolveParameters.FORMAT_OME_TIFF);
        }

        public Builder createProjection(boolean createProjection) {
            parameters.setCreateProjection(createProjection);
            return this;
        }

        public Builder iterations(int iterations) {
            parameters.setIterations(iterations);
            return this;
        }

        public Builder device(String device) {
            parameters.setDevice(device);
            return this;
        }

        public Builder microscopeType(String microscopeType) {
            parameters.setMicroscopeType(microscopeType);
            return this;
        }

        public Builder na(double na) {
            parameters.setNa(na);
            return this;
        }

        public Builder emissionWavelength(String values) {
            parameters.setEmissionWavelength(values);
            return this;
        }

        public Builder excitationWavelength(String values) {
            parameters.setExcitationWavelength(values);
            return this;
        }

        public Builder pinholeAiry(String values) {
            parameters.setPinholeAiry(values);
            return this;
        }

        public Builder pixelSizeXy(double pixelSizeXy) {
            parameters.setPixelSizeXy(pixelSizeXy);
            return this;
        }

        public Builder pixelSizeZ(double pixelSizeZ) {
            parameters.setPixelSizeZ(pixelSizeZ);
            return this;
        }

        public Builder overruleMetadata(boolean overruleMetadata) {
            parameters.setOverruleMetadata(overruleMetadata);
            return this;
        }

        public Builder background(String background) {
            parameters.setBackground(background);
            return this;
        }

        public Builder offset(String offset) {
            parameters.setOffset(offset);
            return this;
        }

        public Builder damping(String damping) {
            parameters.setDamping(damping);
            return this;
        }

        public Builder prefilterSigma(double prefilterSigma) {
            parameters.setPrefilterSigma(prefilterSigma);
            return this;
        }

        public Builder start(String start) {
            parameters.setStart(start);
            return this;
        }

        public Builder convergence(String convergence) {
            parameters.setConvergence(convergence);
            return this;
        }

        public Builder relThreshold(double relThreshold) {
            parameters.setRelThreshold(relThreshold);
            return this;
        }

        public Builder checkEvery(int checkEvery) {
            parameters.setCheckEvery(checkEvery);
            return this;
        }

        public Builder refractiveIndex(double refractiveIndex) {
            parameters.setRefractiveIndex(refractiveIndex);
            return this;
        }

        public Builder sampleRi(double sampleRi) {
            parameters.setSampleRi(sampleRi);
            return this;
        }

        public Builder twoDMode(String twoDMode) {
            parameters.setTwoDMode(twoDMode);
            return this;
        }

        public Builder twoDWfAggressiveness(String twoDWfAggressiveness) {
            parameters.setTwoDWfAggressiveness(twoDWfAggressiveness);
            return this;
        }

        public Builder twoDWfBgRadiusUm(double twoDWfBgRadiusUm) {
            parameters.setTwoDWfBgRadiusUm(twoDWfBgRadiusUm);
            return this;
        }

        public Builder twoDWfBgScale(double twoDWfBgScale) {
            parameters.setTwoDWfBgScale(twoDWfBgScale);
            return this;
        }

        public Builder verbose(boolean verbose) {
            parameters.setVerbose(verbose);
            return this;
        }

        public Builder saveQcMips(boolean saveQcMips) {
            parameters.setSaveQcMips(saveQcMips);
            return this;
        }

        public Builder writeManifest(boolean writeManifest) {
            parameters.setWriteManifest(writeManifest);
            return this;
        }

        public Builder keepTemporaryInputs(boolean keepTemporaryInputs) {
            parameters.setKeepTemporaryInputs(keepTemporaryInputs);
            return this;
        }

        public CIDeconvolveParameters parameters() {
            parameters.normalize();
            return parameters;
        }

        public void run() {
            if (outputDirectory == null)
                throw new IllegalStateException("Set an output directory before running CIDeconvolve.");
            CIDeconvolveParameters runParameters = parameters();
            Runnable run = () -> new CIDeconvolveCommand(qupath).runDeconvolution(runParameters, outputDirectory);
            if (Platform.isFxApplicationThread())
                run.run();
            else
                Platform.runLater(run);
        }

        public CIDeconvolveResult runBlocking() {
            if (outputDirectory == null)
                throw new IllegalStateException("Set an output directory before running CIDeconvolve.");
            return new CIDeconvolveCommand(qupath).runDeconvolutionBlocking(parameters(), outputDirectory);
        }

        public CIDeconvolveResult runBlockingAndImport() {
            if (outputDirectory == null)
                throw new IllegalStateException("Set an output directory before running CIDeconvolve.");
            return new CIDeconvolveCommand(qupath).runDeconvolutionBlockingAndImport(parameters(), outputDirectory);
        }

        public CIDeconvolveRunHandle runAsync() {
            if (outputDirectory == null)
                throw new IllegalStateException("Set an output directory before running CIDeconvolve.");
            return new CIDeconvolveCommand(qupath).runDeconvolutionAsync(parameters(), outputDirectory);
        }
    }
}
