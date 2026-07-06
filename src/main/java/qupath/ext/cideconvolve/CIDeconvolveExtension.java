package qupath.ext.cideconvolve;

import javafx.scene.control.MenuItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.common.Version;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.tools.MenuTools;

public class CIDeconvolveExtension implements QuPathExtension {

    private static final Logger logger = LoggerFactory.getLogger(CIDeconvolveExtension.class);
    private static final Version QUPATH_VERSION = Version.parse("v0.7.0");
    private boolean installed;

    @Override
    public void installExtension(QuPathGUI qupath) {
        if (installed) {
            logger.debug("{} is already installed", getName());
            return;
        }
        installed = true;
        CIDeconvolvePrefs.installPreferenceItems(qupath);

        CIDeconvolveCommand command = new CIDeconvolveCommand(qupath);
        MenuItem run = new MenuItem("Run deconvolution...");
        run.setGraphic(CIDeconvolveIcons.iconView(16));
        run.setOnAction(e -> command.runDeconvolution());
        MenuItem validate = new MenuItem("Validate Python environment");
        validate.setOnAction(e -> command.validatePythonEnvironment());
        MenuItem exampleScript = new MenuItem("Open example Groovy script");
        exampleScript.setOnAction(e -> openExampleScript(qupath));
        MenuItem docs = new MenuItem("Open documentation");
        docs.setOnAction(e -> QuPathGUI.openInBrowser(
                "https://github.com/Cellular-Imaging-Amsterdam-UMC/qupath-extension-cideconvolve#readme"));

        MenuTools.addMenuItems(qupath.getMenu("Extensions>CIDeconvolve", true), run, validate, exampleScript, docs);
    }

    private static void openExampleScript(QuPathGUI qupath) {
        String script = """
                import qupath.ext.cideconvolve.CIDeconvolve

                /**
                 * CIDeconvolve QuPath script example.
                 *
                 * This script runs ci-deconvolve through the QuPath CIDeconvolve extension.
                 * It can process the full active image, selected annotations, or all
                 * annotations with a chosen class.
                 *
                 * Before running:
                 *   1. Install Python 3.12 with ci-deconvolve and PyTorch.
                 *   2. In QuPath, set Edit > Preferences > CIDeconvolve > Python executable.
                 *   3. Open an image in QuPath.
                 *
                 * Logging:
                 *   - runBlockingAndImport() returns a CIDeconvolveResult and imports/opens outputs.
                 *   - runBlocking() returns a CIDeconvolveResult without importing outputs.
                 *   - Printing result.getLogs() with println sends the run log to the
                 *     QuPath Script Editor log panel.
                 *   - .run() is JavaFX-friendly and returns immediately; use it for fire-and-forget
                 *     GUI-style runs, but not when you want script log output.
                 *
                 * Result:
                 *   - result.getStatus() is SUCCESS, FAILED, or CANCELLED.
                 *   - result.getInputPaths() lists the source or exported ROI inputs.
                 *   - result.getOutputPaths() lists the expected deconvolved outputs.
                 *   - result.getFailures() contains error messages, if any.
                 */

                // Put outputs in a cidecon subfolder of the current QuPath project.
                // If you are not using a project, replace PROJECT_BASE_DIR with a fixed path.
                def outputDir = buildFilePath(PROJECT_BASE_DIR, 'cidecon')
                mkdirs(outputDir)

                def result = CIDeconvolve.builder(getQuPath())

                        // Required: output folder.
                        .outputDirectory(outputDir)

                        // Output format: choose one.
                        .omeZarr()                         // Default GUI format, QuPath/OMERO-friendly NGFF v0.4
                        // .omeTiff()                       // Alternative single-file output

                        // Region mode: choose one.
                        .fullImage()                       // Whole active image
                        // .selectedObjects()              // Current selected annotation(s), XY crop, full Z/T
                        // .annotationClass('Tumor')        // All annotation ROIs with this QuPath class

                        // Logging choice for GUI runs. For this script, runBlockingAndImport()
                        // plus println below places logs in the Script Editor log panel.
                        .logToQuPathLog()
                        // .logToDialog()

                        // Basic CLI controls.
                        .createProjection(false)            // true writes max-Z output for 3D data
                        .iterations(40)                     // Richardson-Lucy iterations
                        .device('auto')                     // auto, cpu, cuda
                        .verbose(true)                      // include Python/core progress logs

                        // Microscope/PSF metadata. Image metadata is used when available unless
                        // overruleMetadata(true) is set.
                        .microscopeType('widefield')        // widefield or confocal
                        .na(1.4)                            // Objective numerical aperture
                        .refractiveIndex(1.515)             // Immersion refractive index
                        .sampleRi(1.47)                     // Sample refractive index fallback
                        .emissionWavelength('520')          // nm; comma-separated values allowed per channel
                        .excitationWavelength('488')        // nm; comma-separated values allowed per channel
                        .pinholeAiry('1.0')                 // AU; mainly relevant for confocal
                        .pixelSizeXy(0.065)                 // micrometer fallback
                        .pixelSizeZ(0.2)                    // micrometer fallback
                        .overruleMetadata(false)            // false = prefer image metadata

                        // Stabilization and convergence controls.
                        .background('auto')                 // auto or numeric
                        .offset('auto')                     // auto or numeric
                        .damping('none')                    // none/0 or numeric
                        .prefilterSigma(0.0)                // Gaussian prefilter sigma
                        .start('auto')                      // auto, flat, observed, hybrid, etc.
                        .convergence('auto')                // auto, fixed, none
                        .relThreshold(0.005)                // convergence threshold
                        .checkEvery(5)                      // convergence check interval

                        // 2D widefield fallback behavior.
                        .twoDMode('auto')                   // auto or legacy_2d
                        .twoDWfAggressiveness('balanced')   // conservative, balanced, strong
                        .twoDWfBgRadiusUm(0.5)
                        .twoDWfBgScale(1.0)

                        // Output extras.
                        .saveQcMips(false)                  // Optional MIP/source MIP QC side outputs
                        .writeManifest(false)               // Optional ci_deconvolve_manifest.json
                        .keepTemporaryInputs(false)         // Keep temporary ROI OME-TIFF exports for debugging

                        // Blocking script call. Returns CIDeconvolveResult, adds compatible
                        // outputs to the current project, and opens the first output.
                        .runBlockingAndImport()

                println 'CIDeconvolve status: ' + result.getStatus()
                println 'CIDeconvolve duration: ' + result.getDuration()
                println 'CIDeconvolve outputs:'
                result.getOutputPaths().each { println '  ' + it }

                if (!result.getFailures().isEmpty()) {
                    println 'CIDeconvolve failures:'
                    result.getFailures().each { println '  ' + it }
                }

                println ''
                println 'CIDeconvolve log:'
                result.getLogs().each { println it }
                """;
        var editor = qupath.getScriptEditor();
        if (editor == null)
            return;
        editor.showScript("CIDeconvolve example.groovy", script);
    }

    @Override
    public String getName() {
        return "CIDeconvolve extension";
    }

    @Override
    public String getDescription() {
        return "Run ci-deconvolve from an existing Python environment and open generated OME outputs in QuPath.";
    }

    @Override
    public Version getQuPathVersion() {
        return QUPATH_VERSION;
    }
}
