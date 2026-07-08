package qupath.ext.cideconvolve;

import java.util.List;

/**
 * User-facing parameters supported by ci-deconvolve 0.3.0.
 */
public class CIDeconvolveParameters {

    public static final String FORMAT_OME_TIFF = "ome-tiff";
    public static final String FORMAT_OME_ZARR = "ome-zarr";
    public static final String ROI_FULL_IMAGE = "full_image";
    public static final String ROI_SELECTED = "selected";
    public static final String ROI_ANNOTATION_CLASS = "annotation_class";
    public static final String LOG_DIALOG = "dialog";
    public static final String LOG_QUPATH = "qupath";

    private String outputFormat = FORMAT_OME_ZARR;
    private String roiMode = ROI_FULL_IMAGE;
    private String annotationClass = "";
    private String logTarget = LOG_DIALOG;
    private int iterations = 40;
    private int timeStart = 1;
    private int timeStop = 0;
    private int timeStep = 1;
    private String device = "auto";
    private String background = "auto";
    private String offset = "auto";
    private double prefilterSigma = 0.0;
    private String start = "auto";
    private String convergence = "auto";
    private double relThreshold = 0.005;
    private int checkEvery = 5;
    private double na = 1.4;
    private double refractiveIndex = 1.515;
    private double sampleRi = 1.47;
    private String microscopeType = "confocal";
    private String emissionWavelength = "520";
    private String excitationWavelength = "488";
    private String pinholeAiry = "1.0";
    private double pixelSizeXy = 0.065;
    private double pixelSizeZ = 0.2;
    private boolean overruleMetadata = false;
    private String twoDMode = "auto";
    private String twoDWfAggressiveness = "balanced";
    private double twoDWfBgRadiusUm = 0.5;
    private double twoDWfBgScale = 1.0;
    private boolean createProjection = false;
    private boolean verbose = true;
    private boolean saveQcMips = false;
    private boolean writeManifest = false;
    private boolean keepTemporaryInputs = false;

    public void normalize() {
        if (!FORMAT_OME_TIFF.equals(outputFormat))
            outputFormat = FORMAT_OME_ZARR;
        if (!List.of(ROI_FULL_IMAGE, ROI_SELECTED, ROI_ANNOTATION_CLASS).contains(roiMode))
            roiMode = ROI_FULL_IMAGE;
        if (annotationClass == null)
            annotationClass = "";
        annotationClass = annotationClass.trim();
        if (!List.of(LOG_DIALOG, LOG_QUPATH).contains(logTarget))
            logTarget = LOG_DIALOG;
        if (iterations < 1)
            iterations = 40;
        if (timeStart < 1)
            timeStart = 1;
        if (timeStop < 0)
            timeStop = 0;
        if (timeStep < 1)
            timeStep = 1;
        if (!List.of("auto", "cpu", "cuda").contains(device))
            device = "auto";
        if (!List.of("confocal", "widefield").contains(microscopeType))
            microscopeType = "confocal";
        if (!List.of("auto", "flat", "percentile_flat", "observed", "observed_bgsub", "lowpass", "lowpass_bgsub", "hybrid").contains(start))
            start = "auto";
        if (!List.of("auto", "fixed", "none").contains(convergence))
            convergence = "auto";
        if (checkEvery < 1)
            checkEvery = 5;
        if (!List.of("auto", "legacy_2d").contains(twoDMode))
            twoDMode = "auto";
        if (!List.of("conservative", "balanced", "strong").contains(twoDWfAggressiveness))
            twoDWfAggressiveness = "balanced";
        if (background == null || background.isBlank())
            background = "auto";
        if (offset == null || offset.isBlank())
            offset = "auto";
        if (emissionWavelength == null || emissionWavelength.isBlank())
            emissionWavelength = "520";
        if (excitationWavelength == null || excitationWavelength.isBlank())
            excitationWavelength = "488";
        if (pinholeAiry == null || pinholeAiry.isBlank())
            pinholeAiry = "1.0";
    }

    public String getOutputFormat() {
        return outputFormat;
    }

    public void setOutputFormat(String outputFormat) {
        this.outputFormat = outputFormat;
    }

    public String getRoiMode() {
        return roiMode;
    }

    public void setRoiMode(String roiMode) {
        this.roiMode = roiMode;
    }

    public String getAnnotationClass() {
        return annotationClass;
    }

    public void setAnnotationClass(String annotationClass) {
        this.annotationClass = annotationClass;
    }

    public String getLogTarget() {
        return logTarget;
    }

    public void setLogTarget(String logTarget) {
        this.logTarget = logTarget;
    }

    public int getIterations() {
        return iterations;
    }

    public void setIterations(int iterations) {
        this.iterations = iterations;
    }

    public int getTimeStart() {
        return timeStart;
    }

    public void setTimeStart(int timeStart) {
        this.timeStart = timeStart;
    }

    public int getTimeStop() {
        return timeStop;
    }

    public void setTimeStop(int timeStop) {
        this.timeStop = timeStop;
    }

    public int getTimeStep() {
        return timeStep;
    }

    public void setTimeStep(int timeStep) {
        this.timeStep = timeStep;
    }

    public String getDevice() {
        return device;
    }

    public void setDevice(String device) {
        this.device = device;
    }

    public String getBackground() {
        return background;
    }

    public void setBackground(String background) {
        this.background = background;
    }

    public String getOffset() {
        return offset;
    }

    public void setOffset(String offset) {
        this.offset = offset;
    }

    public double getPrefilterSigma() {
        return prefilterSigma;
    }

    public void setPrefilterSigma(double prefilterSigma) {
        this.prefilterSigma = prefilterSigma;
    }

    public String getStart() {
        return start;
    }

    public void setStart(String start) {
        this.start = start;
    }

    public String getConvergence() {
        return convergence;
    }

    public void setConvergence(String convergence) {
        this.convergence = convergence;
    }

    public double getRelThreshold() {
        return relThreshold;
    }

    public void setRelThreshold(double relThreshold) {
        this.relThreshold = relThreshold;
    }

    public int getCheckEvery() {
        return checkEvery;
    }

    public void setCheckEvery(int checkEvery) {
        this.checkEvery = checkEvery;
    }

    public double getNa() {
        return na;
    }

    public void setNa(double na) {
        this.na = na;
    }

    public double getRefractiveIndex() {
        return refractiveIndex;
    }

    public void setRefractiveIndex(double refractiveIndex) {
        this.refractiveIndex = refractiveIndex;
    }

    public double getSampleRi() {
        return sampleRi;
    }

    public void setSampleRi(double sampleRi) {
        this.sampleRi = sampleRi;
    }

    public String getMicroscopeType() {
        return microscopeType;
    }

    public void setMicroscopeType(String microscopeType) {
        this.microscopeType = microscopeType;
    }

    public String getEmissionWavelength() {
        return emissionWavelength;
    }

    public void setEmissionWavelength(String emissionWavelength) {
        this.emissionWavelength = emissionWavelength;
    }

    public String getExcitationWavelength() {
        return excitationWavelength;
    }

    public void setExcitationWavelength(String excitationWavelength) {
        this.excitationWavelength = excitationWavelength;
    }

    public String getPinholeAiry() {
        return pinholeAiry;
    }

    public void setPinholeAiry(String pinholeAiry) {
        this.pinholeAiry = pinholeAiry;
    }

    public double getPixelSizeXy() {
        return pixelSizeXy;
    }

    public void setPixelSizeXy(double pixelSizeXy) {
        this.pixelSizeXy = pixelSizeXy;
    }

    public double getPixelSizeZ() {
        return pixelSizeZ;
    }

    public void setPixelSizeZ(double pixelSizeZ) {
        this.pixelSizeZ = pixelSizeZ;
    }

    public boolean isOverruleMetadata() {
        return overruleMetadata;
    }

    public void setOverruleMetadata(boolean overruleMetadata) {
        this.overruleMetadata = overruleMetadata;
    }

    public String getTwoDMode() {
        return twoDMode;
    }

    public void setTwoDMode(String twoDMode) {
        this.twoDMode = twoDMode;
    }

    public String getTwoDWfAggressiveness() {
        return twoDWfAggressiveness;
    }

    public void setTwoDWfAggressiveness(String twoDWfAggressiveness) {
        this.twoDWfAggressiveness = twoDWfAggressiveness;
    }

    public double getTwoDWfBgRadiusUm() {
        return twoDWfBgRadiusUm;
    }

    public void setTwoDWfBgRadiusUm(double twoDWfBgRadiusUm) {
        this.twoDWfBgRadiusUm = twoDWfBgRadiusUm;
    }

    public double getTwoDWfBgScale() {
        return twoDWfBgScale;
    }

    public void setTwoDWfBgScale(double twoDWfBgScale) {
        this.twoDWfBgScale = twoDWfBgScale;
    }

    public boolean isCreateProjection() {
        return createProjection;
    }

    public void setCreateProjection(boolean createProjection) {
        this.createProjection = createProjection;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public boolean isSaveQcMips() {
        return saveQcMips;
    }

    public void setSaveQcMips(boolean saveQcMips) {
        this.saveQcMips = saveQcMips;
    }

    public boolean isWriteManifest() {
        return writeManifest;
    }

    public void setWriteManifest(boolean writeManifest) {
        this.writeManifest = writeManifest;
    }

    public boolean isKeepTemporaryInputs() {
        return keepTemporaryInputs;
    }

    public void setKeepTemporaryInputs(boolean keepTemporaryInputs) {
        this.keepTemporaryInputs = keepTemporaryInputs;
    }
}
