package qupath.ext.cideconvolve;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CIDeconvolveCliTest {

    @TempDir
    Path tempDir;

    @Test
    void testSupportedDirectInputs() {
        assertTrue(CIDeconvolveCli.isSupportedDirectInput(Path.of("image.ome.tiff")));
        assertTrue(CIDeconvolveCli.isSupportedDirectInput(Path.of("image.ome.tif")));
        assertTrue(CIDeconvolveCli.isSupportedDirectInput(Path.of("image.ome.zarr")));
        assertTrue(CIDeconvolveCli.isSupportedDirectInput(Path.of("image.zarr")));
        assertFalse(CIDeconvolveCli.isSupportedDirectInput(Path.of("image.tif")));
        assertFalse(CIDeconvolveCli.isSupportedDirectInput(Path.of("image.ndpi")));
    }

    @Test
    void testExpectedOutputPath() {
        assertEquals(
                Path.of("out", "stack_decon.ome.zarr"),
                CIDeconvolveCli.expectedOutputPath(
                        Path.of("stack.ome.tiff"),
                        Path.of("out"),
                        CIDeconvolveParameters.FORMAT_OME_ZARR));
        assertEquals(
                Path.of("out", "stack_decon.ome.tiff"),
                CIDeconvolveCli.expectedOutputPath(
                        Path.of("stack.ome.zarr"),
                        Path.of("out"),
                        CIDeconvolveParameters.FORMAT_OME_TIFF));
    }

    @Test
    void testRunCommandUsesPyPiEntrypointAndHyphenatedFlags() {
        CIDeconvolveParameters parameters = new CIDeconvolveParameters();
        parameters.setOutputFormat(CIDeconvolveParameters.FORMAT_OME_ZARR);
        parameters.setIterations(12);
        parameters.setTimeStart(2);
        parameters.setTimeStop(8);
        parameters.setTimeStep(3);
        parameters.setDevice("cpu");
        parameters.setOverruleMetadata(true);
        parameters.setCreateProjection(true);
        parameters.setSnrMode(CIDeconvolveParameters.SNR_MANUAL);
        parameters.setSnrValue(4.2);
        parameters.setAcuity(25.0);

        List<String> command = CIDeconvolveCli.buildRunCommand(
                Path.of("C:/Python312/python.exe"),
                Path.of("input.ome.tiff"),
                Path.of("out"),
                parameters);

        assertEquals(Path.of("C:/Python312/python.exe").toString(), command.get(0));
        assertEquals("-c", command.get(1));
        assertTrue(command.get(2).contains("ci_deconvolve.cli"));
        assertFlagValue(command, "--input", "input.ome.tiff");
        assertFlagValue(command, "--output", "out");
        assertFlagValue(command, "--output-format", "ome-zarr");
        assertFlagValue(command, "--projection", "max-z");
        assertFlagValue(command, "--iterations", "12");
        assertFlagValue(command, "--t-start", "2");
        assertFlagValue(command, "--t-stop", "8");
        assertFlagValue(command, "--t-step", "3");
        assertFlagValue(command, "--device", "cpu");
        assertFalse(command.contains("--damping"));
        assertFlagValue(command, "--snr", "4.2");
        assertFlagValue(command, "--acuity", "25.0");
        assertTrue(command.contains("--overrule-metadata"));
        assertTrue(command.contains("--verbose"));
    }

    @Test
    void testDefaultOutputDirectoryModes() {
        assertEquals(
                Path.of("C:/project", "cidecon"),
                CIDeconvolveCommand.defaultOutputDirectory(
                        Path.of("C:/project/project.qpproj"),
                        Path.of("D:/data/image.ome.tiff"),
                        CIDeconvolvePrefs.OUTPUT_LOCATION_PROJECT));
        assertEquals(
                Path.of("D:/data", "cidecon"),
                CIDeconvolveCommand.defaultOutputDirectory(
                        Path.of("C:/project/project.qpproj"),
                        Path.of("D:/data/image.ome.tiff"),
                        CIDeconvolvePrefs.OUTPUT_LOCATION_DATA_FILE));
    }

    @Test
    void testValidationResultRequiresMarkerAndSuccessCode() {
        assertTrue(new PythonValidationResult(0, "CIDECONVOLVE_OK\nci_deconvolve=3.1.0\npython=3.12").isValid());
        assertFalse(new PythonValidationResult(1, "CIDECONVOLVE_OK\nci_deconvolve=3.1.0").isValid());
        assertFalse(new PythonValidationResult(0, "missing marker").isValid());
        assertFalse(new PythonValidationResult(0, "CIDECONVOLVE_OK\nci_deconvolve=0.2.0").isValid());
    }

    @Test
    void testDetectsZarrV3Output() throws IOException {
        Path zarr = tempDir.resolve("output.ome.zarr");
        Files.createDirectories(zarr);
        Files.writeString(zarr.resolve("zarr.json"), "{\"zarr_format\": 3}");

        assertTrue(CIDeconvolveCli.isZarrV3(zarr));
        assertFalse(CIDeconvolveCli.isZarrV3(tempDir.resolve("missing.ome.zarr")));
    }

    @Test
    void testReadsCIDeconvolvePixelSizesFromZarrAttrs() throws IOException {
        Path zarr = tempDir.resolve("output.ome.zarr");
        Files.createDirectories(zarr);
        Files.writeString(zarr.resolve(".zattrs"), """
                {
                  "multiscales": [{
                    "datasets": [{
                      "coordinateTransformations": [{"type": "scale", "scale": [1, 1, 0.2, 0.066813, 0.066813]}]
                    }]
                  }],
                  "cideconvolve": {
                    "physical_pixel_sizes_um": {"x": 0.066813, "y": 0.066813, "z": 0.2}
                  }
                }
                """);

        var pixelSizes = CIDeconvolveCli.readPhysicalPixelSizes(zarr).orElseThrow();

        assertEquals(0.066813, pixelSizes.x(), 1e-12);
        assertEquals(0.066813, pixelSizes.y(), 1e-12);
        assertEquals(0.2, pixelSizes.z(), 1e-12);
    }

    @Test
    void testReadsPixelSizesFromNgffScaleFallback() throws IOException {
        Path zarr = tempDir.resolve("scale-only.ome.zarr");
        Files.createDirectories(zarr);
        Files.writeString(zarr.resolve(".zattrs"), """
                {
                  "multiscales": [{
                    "datasets": [{
                      "coordinateTransformations": [{"type": "scale", "scale": [1, 1, 0.3, 0.12, 0.11]}]
                    }]
                  }]
                }
                """);

        var pixelSizes = CIDeconvolveCli.readPhysicalPixelSizes(zarr).orElseThrow();

        assertEquals(0.11, pixelSizes.x(), 1e-12);
        assertEquals(0.12, pixelSizes.y(), 1e-12);
        assertEquals(0.3, pixelSizes.z(), 1e-12);
    }

    @Test
    void testSettingsStoreRoundTripAndListSkipsLastSettings() throws IOException {
        Path settingsDir = tempDir.resolve(CIDeconvolveSettingsStore.SETTINGS_DIRECTORY_NAME);
        CIDeconvolveParameters parameters = new CIDeconvolveParameters();
        parameters.setOutputFormat(CIDeconvolveParameters.FORMAT_OME_TIFF);
        parameters.setRoiMode(CIDeconvolveParameters.ROI_ANNOTATION_CLASS);
        parameters.setAnnotationClass("cidecon");
        parameters.setIterations(17);
        parameters.setTimeStart(2);
        parameters.setTimeStop(4);
        parameters.setTimeStep(2);
        parameters.setCreateProjection(true);
        parameters.setSnrMode(CIDeconvolveParameters.SNR_MANUAL);
        parameters.setSnrValue(4.2);
        parameters.setAcuity(25.0);

        Path saved = settingsDir.resolve("my-settings.json");
        CIDeconvolveSettingsStore.save(saved, parameters);
        CIDeconvolveSettingsStore.save(CIDeconvolveSettingsStore.lastSettingsPath(settingsDir), parameters);

        CIDeconvolveParameters loaded = CIDeconvolveSettingsStore.load(saved);

        assertEquals(CIDeconvolveParameters.FORMAT_OME_TIFF, loaded.getOutputFormat());
        assertEquals(CIDeconvolveParameters.ROI_ANNOTATION_CLASS, loaded.getRoiMode());
        assertEquals("cidecon", loaded.getAnnotationClass());
        assertEquals(17, loaded.getIterations());
        assertEquals(2, loaded.getTimeStart());
        assertEquals(4, loaded.getTimeStop());
        assertEquals(2, loaded.getTimeStep());
        assertTrue(loaded.isCreateProjection());
        assertEquals(CIDeconvolveParameters.SNR_MANUAL, loaded.getSnrMode());
        assertEquals(4.2, loaded.getSnrValue(), 1e-12);
        assertEquals(25.0, loaded.getAcuity(), 1e-12);
        assertEquals(List.of(saved), CIDeconvolveSettingsStore.listSavedSettings(settingsDir));
    }

    private static void assertFlagValue(List<String> command, String flag, String value) {
        int index = command.indexOf(flag);
        assertTrue(index >= 0, "Missing flag " + flag);
        assertEquals(value, command.get(index + 1));
    }
}
