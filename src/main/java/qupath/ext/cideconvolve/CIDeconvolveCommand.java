package qupath.ext.cideconvolve;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.Commands;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.CroppedImageServer;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServers;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.images.writers.ImageWriterTools;
import qupath.lib.objects.PathObject;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;
import qupath.lib.regions.ImageRegion;
import qupath.lib.roi.interfaces.ROI;

import java.awt.Desktop;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public class CIDeconvolveCommand {

    private static final Logger logger = LoggerFactory.getLogger(CIDeconvolveCommand.class);
    private static final String TITLE = CIDeconvolveExtension.NAME;
    private static final Pattern UNSAFE_FILENAME_CHARS = Pattern.compile("[^A-Za-z0-9._-]+");
    private static final com.google.gson.Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final QuPathGUI qupath;
    private Task<Void> runningTask;
    private volatile CIDeconvolveProcessRunner runningProcessRunner;

    public CIDeconvolveCommand(QuPathGUI qupath) {
        this.qupath = qupath;
    }

    public void runDeconvolution() {
        if (runningTask != null && runningTask.isRunning()) {
            Dialogs.showPlainMessage(TITLE, "Deconvolution is already running.");
            return;
        }

        ImageData<BufferedImage> imageData = getActiveImageData();
        if (imageData == null || imageData.getServer() == null) {
            Dialogs.showErrorMessage(TITLE, "No active image is available.");
            return;
        }
        ImageServer<BufferedImage> server = imageData.getServer();

        Path python = getPythonExecutable();
        if (python == null)
            return;

        Optional<CIDeconvolveRunRequest> request = new CIDeconvolveRunDialog(
                qupath, getDefaultOutputDirectory(server), getSettingsDirectory(server)).show();
        if (request.isEmpty())
            return;

        CIDeconvolvePrefs.defaultOutputFormatProperty().set(request.get().getParameters().getOutputFormat());

        RunLog log = createRunLog(request.get().getParameters());
        Task<Void> task = createRunTask(imageData, python, request.get(), OutputDisplay.from(imageData), log);
        runningTask = task;
        configureCancel(log, task);
        new Thread(task, "cideconvolve-run").start();
    }

    public void runDeconvolution(CIDeconvolveParameters parameters, Path outputDirectory) {
        if (runningTask != null && runningTask.isRunning()) {
            Dialogs.showPlainMessage(TITLE, "Deconvolution is already running.");
            return;
        }
        ImageData<BufferedImage> imageData = getActiveImageData();
        if (imageData == null || imageData.getServer() == null) {
            Dialogs.showErrorMessage(TITLE, "No active image is available.");
            return;
        }
        Path python = getPythonExecutable();
        if (python == null)
            return;
        CIDeconvolveRunRequest request = new CIDeconvolveRunRequest(outputDirectory, parameters);
        CIDeconvolvePrefs.defaultOutputFormatProperty().set(parameters.getOutputFormat());
        RunLog log = createRunLog(parameters);
        Task<Void> task = createRunTask(imageData, python, request, OutputDisplay.from(imageData), log);
        runningTask = task;
        configureCancel(log, task);
        new Thread(task, "cideconvolve-run").start();
    }

    public CIDeconvolveResult runDeconvolutionBlocking(CIDeconvolveParameters parameters, Path outputDirectory) {
        ImageData<BufferedImage> imageData = getActiveImageData();
        if (imageData == null || imageData.getServer() == null)
            throw new IllegalStateException("No active image is available.");
        Path python = getPythonExecutable();
        if (python == null)
            throw new IllegalStateException("CIDeconvolve Python executable is not configured.");
        CIDeconvolveRunRequest request = new CIDeconvolveRunRequest(outputDirectory, parameters);
        RunLog log = new RunLog(null, null, null, new java.util.concurrent.CopyOnWriteArrayList<>());
        AtomicBoolean cancelled = new AtomicBoolean(false);
        try {
            return executeRun(imageData, python, request, log, cancelled::get);
        } catch (Exception e) {
            appendLog(log, "ERROR: " + e.getMessage());
            return new CIDeconvolveResult(
                    CIDeconvolveResult.Status.FAILED,
                    List.of(),
                    List.of(),
                    log.lines(),
                    List.of(e.getMessage()),
                    Duration.ZERO);
        }
    }

    public CIDeconvolveResult runDeconvolutionBlockingAndImport(CIDeconvolveParameters parameters, Path outputDirectory) {
        ImageData<BufferedImage> imageData = getActiveImageData();
        if (imageData == null || imageData.getServer() == null)
            throw new IllegalStateException("No active image is available.");
        var display = OutputDisplay.from(imageData);
        var project = qupath.getProject();
        Path python = getPythonExecutable();
        if (python == null)
            throw new IllegalStateException("CIDeconvolve Python executable is not configured.");
        CIDeconvolveRunRequest request = new CIDeconvolveRunRequest(outputDirectory, parameters);
        RunLog log = new RunLog(null, null, null, new java.util.concurrent.CopyOnWriteArrayList<>());
        AtomicBoolean cancelled = new AtomicBoolean(false);
        try {
            CIDeconvolveResult result = executeRun(imageData, python, request, log, cancelled::get);
            if (result.getStatus() == CIDeconvolveResult.Status.SUCCESS)
                openOutputsOnFxAndWait(result.getOutputPaths(), outputDirectory, project, display, log);
            return new CIDeconvolveResult(
                    result.getStatus(),
                    result.getInputPaths(),
                    result.getOutputPaths(),
                    log.lines(),
                    result.getFailures(),
                    result.getDuration());
        } catch (Exception e) {
            appendLog(log, "ERROR: " + e.getMessage());
            return new CIDeconvolveResult(
                    CIDeconvolveResult.Status.FAILED,
                    List.of(),
                    List.of(),
                    log.lines(),
                    List.of(e.getMessage()),
                    Duration.ZERO);
        }
    }

    public CIDeconvolveRunHandle runDeconvolutionAsync(CIDeconvolveParameters parameters, Path outputDirectory) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        CompletableFuture<CIDeconvolveResult> future = CompletableFuture.supplyAsync(() -> {
            try {
                return runDeconvolutionBlocking(parameters, outputDirectory, cancelled);
            } catch (Exception e) {
                return new CIDeconvolveResult(
                        CIDeconvolveResult.Status.FAILED,
                        List.of(),
                        List.of(),
                        List.of("ERROR: " + e.getMessage()),
                        List.of(e.getMessage()),
                        Duration.ZERO);
            }
        });
        return new CIDeconvolveRunHandle(future, cancelled);
    }

    private CIDeconvolveResult runDeconvolutionBlocking(
            CIDeconvolveParameters parameters,
            Path outputDirectory,
            AtomicBoolean cancelled
    ) throws Exception {
        ImageData<BufferedImage> imageData = getActiveImageData();
        if (imageData == null || imageData.getServer() == null)
            throw new IllegalStateException("No active image is available.");
        Path python = getPythonExecutable();
        if (python == null)
            throw new IllegalStateException("CIDeconvolve Python executable is not configured.");
        return executeRun(
                imageData,
                python,
                new CIDeconvolveRunRequest(outputDirectory, parameters),
                new RunLog(null, null, null, new java.util.concurrent.CopyOnWriteArrayList<>()),
                cancelled::get);
    }

    private void configureCancel(RunLog log, Task<Void> task) {
        Button cancelButton = log.cancelButton();
        if (cancelButton == null)
            return;
        cancelButton.setOnAction(e -> {
            if (runningTask != null && runningTask.isRunning()) {
                appendLog(log, "Cancel requested.");
                CIDeconvolveProcessRunner processRunner = runningProcessRunner;
                if (processRunner != null) {
                    processRunner.currentPid().ifPresent(pid -> appendLog(log, "Terminating Python PID " + pid));
                    processRunner.cancel();
                }
                runningTask.cancel();
                cancelButton.setDisable(true);
            }
        });
        task.runningProperty().addListener((v, wasRunning, isRunning) -> {
            if (!isRunning)
                cancelButton.setDisable(true);
        });
    }

    public void validatePythonEnvironment() {
        Path python = getPythonExecutable();
        if (python == null)
            return;

        StringBuilder log = new StringBuilder();
        try {
            var result = new CIDeconvolveProcessRunner().run(
                    CIDeconvolveCli.buildValidationCommand(python),
                    line -> log.append(line).append(System.lineSeparator()),
                    () -> false);
            PythonValidationResult validation = new PythonValidationResult(result.getExitCode(), log.toString());
            if (validation.isValid())
                Dialogs.showPlainMessage(TITLE, validation.getMessage());
            else
                Dialogs.showErrorMessage(TITLE, validation.getMessage());
        } catch (IOException e) {
            Dialogs.showErrorMessage(TITLE, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Dialogs.showErrorMessage(TITLE, e.getMessage());
        }
    }

    private Task<Void> createRunTask(
            ImageData<BufferedImage> imageData,
            Path python,
            CIDeconvolveRunRequest request,
            OutputDisplay display,
            RunLog log
    ) {
        var project = qupath.getProject();
        return new Task<>() {
            @Override
            protected Void call() throws Exception {
                CIDeconvolveResult result = executeRun(imageData, python, request, log, this::isCancelled);
                if (result.getStatus() == CIDeconvolveResult.Status.SUCCESS) {
                    Platform.runLater(() -> {
                        if (log.stage() != null)
                            log.stage().close();
                        openOutputs(result.getOutputPaths(), request.getOutputDirectory(), project, display, log);
                    });
                }
                return null;
            }

            @Override
            protected void failed() {
                runningTask = null;
                Throwable error = getException();
                logger.error("CIDeconvolve failed", error);
                appendLog(log, "ERROR: " + (error == null ? "Unknown error" : error.getMessage()));
                Dialogs.showErrorMessage(TITLE, error == null ? "Unknown error" : error.getMessage());
            }

            @Override
            protected void succeeded() {
                runningTask = null;
            }

            @Override
            protected void cancelled() {
                runningTask = null;
                appendLog(log, "Cancelled.");
            }
        };
    }

    private CIDeconvolveResult executeRun(
            ImageData<BufferedImage> imageData,
            Path python,
            CIDeconvolveRunRequest request,
            RunLog log,
            CancelChecker cancelChecker
    ) throws Exception {
        Instant start = Instant.now();
        List<Path> inputPaths = List.of();
        List<Path> outputPaths = new java.util.ArrayList<>();
        List<String> failures = new java.util.ArrayList<>();
        var channels = OutputDisplay.from(imageData).channels();
        try {
            Files.createDirectories(request.getOutputDirectory());
            appendLog(log, "Output format: " + request.getParameters().getOutputFormat());
            inputPaths = prepareInputs(imageData, request, cancelChecker, log);
            var processRunner = new CIDeconvolveProcessRunner();
            for (int i = 0; i < inputPaths.size(); i++) {
                if (cancelChecker.isCancelled()) {
                    appendLog(log, "Cancelled.");
                    return result(CIDeconvolveResult.Status.CANCELLED, inputPaths, outputPaths, log, failures, start);
                }
                Path inputPath = inputPaths.get(i);
                Path outputPath = CIDeconvolveCli.expectedOutputPath(
                        inputPath, request.getOutputDirectory(), request.getParameters().getOutputFormat());
                outputPaths.add(outputPath);
                appendLog(log, String.format("Run %d/%d input: %s", i + 1, inputPaths.size(), inputPath));
                appendLog(log, "Expected output: " + outputPath);
                appendLog(log, "Executing:");
                List<String> command = CIDeconvolveCli.buildRunCommand(
                        python, inputPath, request.getOutputDirectory(), request.getParameters());
                appendLog(log, CIDeconvolveCli.printableCommand(command));

                Path pidFile = request.getOutputDirectory().resolve(".cideconvolve-python.pid");
                runningProcessRunner = processRunner;
                ProcessResult processResult;
                try {
                    processResult = processRunner.run(
                            command,
                            line -> appendLog(log, line),
                            cancelChecker::isCancelled,
                            pid -> writePidFile(pidFile, pid, log));
                } finally {
                    runningProcessRunner = null;
                    removePidFile(pidFile, log);
                }
                if (cancelChecker.isCancelled() || processResult.getExitCode() == 130) {
                    appendLog(log, "Cancelled.");
                    return result(CIDeconvolveResult.Status.CANCELLED, inputPaths, outputPaths, log, failures, start);
                }
                if (processResult.getExitCode() != 0)
                    throw new IOException("ci_deconvolve failed with exit code " + processResult.getExitCode());
                if (!Files.exists(outputPath))
                    throw new IOException("Expected output was not found: " + outputPath);
                try {
                    ChannelColors.write(outputPath, channels);
                } catch (Exception e) {
                    logger.warn("Unable to save source channel colors in {}", outputPath, e);
                    appendLog(log, "Unable to save source channel colors in the export: " + e.getMessage());
                }
                copyProvenance(inputPath, outputPath);
            }
            if (!request.getParameters().isKeepTemporaryInputs())
                cleanupTemporaryInputs(inputPaths, log);
            return result(CIDeconvolveResult.Status.SUCCESS, inputPaths, outputPaths, log, failures, start);
        } catch (Exception e) {
            failures.add(e.getMessage());
            throw e;
        }
    }

    private static void writePidFile(Path pidFile, long pid, RunLog log) {
        try {
            Files.writeString(pidFile, Long.toString(pid));
            appendLog(log, "Python PID: " + pid + " (stored in " + pidFile + ")");
        } catch (IOException e) {
            appendLog(log, "Unable to write Python PID file: " + e.getMessage());
        }
    }

    private static void removePidFile(Path pidFile, RunLog log) {
        try {
            Files.deleteIfExists(pidFile);
        } catch (IOException e) {
            appendLog(log, "Unable to remove Python PID file: " + e.getMessage());
        }
    }

    private static void copyProvenance(Path inputPath, Path outputPath) throws IOException {
        Path inputProvenance = provenancePath(inputPath);
        if (Files.isRegularFile(inputProvenance))
            Files.copy(inputProvenance, provenancePath(outputPath), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static void cleanupTemporaryInputs(List<Path> inputPaths, RunLog log) {
        for (Path inputPath : inputPaths) {
            Path parent = inputPath.getParent();
            if (parent == null)
                continue;
            String parentName = parent.getFileName() == null ? "" : parent.getFileName().toString();
            boolean isManagedTemp = ".ci_deconvolve_tmp".equals(parentName)
                    || parentName.startsWith("qupath-cideconvolve-");
            if (!isManagedTemp)
                continue;
            try {
                Files.deleteIfExists(inputPath);
                Files.deleteIfExists(provenancePath(inputPath));
                appendLog(log, "Removed temporary input: " + inputPath);
            } catch (IOException e) {
                appendLog(log, "Unable to remove temporary input: " + inputPath + " (" + e.getMessage() + ")");
            }
        }
    }

    private static CIDeconvolveResult result(
            CIDeconvolveResult.Status status,
            List<Path> inputPaths,
            List<Path> outputPaths,
            RunLog log,
            List<String> failures,
            Instant start
    ) {
        return new CIDeconvolveResult(
                status,
                inputPaths,
                outputPaths,
                log.lines(),
                failures,
                Duration.between(start, Instant.now()));
    }

    private List<Path> prepareInputs(
            ImageData<BufferedImage> imageData,
            CIDeconvolveRunRequest request,
            CancelChecker cancelChecker,
            RunLog log
    )
            throws IOException {
        ImageServer<BufferedImage> server = imageData.getServer();
        String roiMode = request.getParameters().getRoiMode();
        if (CIDeconvolveParameters.ROI_SELECTED.equals(roiMode))
            return prepareCroppedInputs(imageData, selectedObjects(imageData), "selected", request, cancelChecker, log);
        if (CIDeconvolveParameters.ROI_ANNOTATION_CLASS.equals(roiMode))
            return prepareCroppedInputs(
                    imageData,
                    annotationClassObjects(imageData, request.getParameters().getAnnotationClass()),
                    sanitizeFilePart(request.getParameters().getAnnotationClass()),
                    request,
                    cancelChecker,
                    log);

        Path directInput = getDirectInputPath(server);
        if (directInput != null) {
            appendLog(log, "Using active image directly: " + directInput);
            return List.of(directInput);
        }
        if (cancelChecker.isCancelled())
            throw new IOException("Cancelled before export.");
        Path tempDir = Files.createTempDirectory("qupath-cideconvolve-");
        Path tempInput = tempDir.resolve("active-image.ome.tiff");
        appendLog(log, "Exporting active image to temporary OME-TIFF: " + tempInput);
        ImageWriterTools.writeImage(server, tempInput.toString());
        return List.of(tempInput);
    }

    private List<Path> prepareCroppedInputs(
            ImageData<BufferedImage> imageData,
            List<PathObject> objects,
            String label,
            CIDeconvolveRunRequest request,
            CancelChecker cancelChecker,
            RunLog log
    ) throws IOException {
        if (objects.isEmpty())
            throw new IOException("No annotation ROI was found for region mode: "
                    + request.getParameters().getRoiMode());

        ImageServer<BufferedImage> server = imageData.getServer();
        String baseName = imageBaseName(server);
        Path tempDir = request.getOutputDirectory().resolve(".ci_deconvolve_tmp");
        Files.createDirectories(tempDir);
        List<Path> paths = new java.util.ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            if (cancelChecker.isCancelled())
                throw new IOException("Cancelled before ROI export.");
            PathObject object = objects.get(i);
            ROI roi = object.getROI();
            ImageRegion region = cropRegion(server, roi);
            Path tempInput = tempDir.resolve(baseName + "_" + label + "_" + String.format("%03d", i + 1) + ".ome.tiff");
            Files.deleteIfExists(tempInput);
            appendLog(
                    log,
                    String.format(
                            "Exporting ROI %d/%d XY crop x=%d y=%d w=%d h=%d with full Z/T extent: %s",
                            i + 1, objects.size(),
                            region.getX(), region.getY(), region.getWidth(), region.getHeight(),
                            tempInput));
            ImageWriterTools.writeImage(new CroppedImageServer(server, region), tempInput.toString());
            writeRoiProvenance(imageData, object, region, request, tempInput);
            paths.add(tempInput);
        }
        return paths;
    }

    private void writeRoiProvenance(
            ImageData<BufferedImage> imageData,
            PathObject object,
            ImageRegion region,
            CIDeconvolveRunRequest request,
            Path inputPath
    ) throws IOException {
        ImageServer<BufferedImage> server = imageData.getServer();
        java.util.Map<String, Object> provenance = new java.util.LinkedHashMap<>();
        provenance.put("created_at", java.time.OffsetDateTime.now().toString());
        provenance.put("source_uris", server.getURIs().stream().map(URI::toString).toList());
        provenance.put("roi_mode", request.getParameters().getRoiMode());
        provenance.put("annotation_class", request.getParameters().getAnnotationClass());
        provenance.put("annotation_name", object.getName());
        provenance.put("annotation_display_name", object.getDisplayedName());
        provenance.put("annotation_id", object.getID().toString());
        provenance.put("crop_x", region.getX());
        provenance.put("crop_y", region.getY());
        provenance.put("crop_width", region.getWidth());
        provenance.put("crop_height", region.getHeight());
        provenance.put("full_z_slices", server.nZSlices());
        provenance.put("full_timepoints", server.nTimepoints());
        provenance.put("temporary_input", inputPath.toString());
        Files.writeString(provenancePath(inputPath), GSON.toJson(provenance));
    }

    private static Path provenancePath(Path imagePath) {
        return imagePath.resolveSibling(imagePath.getFileName().toString() + ".cideconvolve.provenance.json");
    }

    private List<PathObject> selectedObjects(ImageData<BufferedImage> imageData) {
        return imageData.getHierarchy()
                .getSelectionModel()
                .getSelectedObjects()
                .stream()
                .filter(this::hasAreaRoi)
                .sorted(java.util.Comparator.comparing(this::objectDisplayName))
                .toList();
    }

    private List<PathObject> annotationClassObjects(ImageData<BufferedImage> imageData, String annotationClass) {
        String className = Objects.toString(annotationClass, "").trim();
        return imageData.getHierarchy()
                .getAnnotationObjects()
                .stream()
                .filter(this::hasAreaRoi)
                .filter(object -> pathClassMatches(object, className))
                .sorted(java.util.Comparator.comparing(this::objectDisplayName))
                .toList();
    }

    private boolean hasAreaRoi(PathObject object) {
        if (object == null || !object.hasROI())
            return false;
        ROI roi = object.getROI();
        return roi != null && roi.isArea() && !roi.isEmpty()
                && roi.getBoundsWidth() > 0
                && roi.getBoundsHeight() > 0;
    }

    private boolean pathClassMatches(PathObject object, String className) {
        if (className.isBlank() || object.getPathClass() == null)
            return false;
        String pathClassName = object.getPathClass().getName();
        String pathClassText = object.getPathClass().toString();
        return className.equalsIgnoreCase(pathClassName)
                || className.equalsIgnoreCase(pathClassText)
                || object.getPathClass().toSet().stream().anyMatch(className::equalsIgnoreCase);
    }

    private String objectDisplayName(PathObject object) {
        String name = object == null ? "" : object.getDisplayedName();
        return name == null ? "" : name;
    }

    private static ImageRegion cropRegion(ImageServer<BufferedImage> server, ROI roi) throws IOException {
        int x = Math.max(0, (int) Math.floor(roi.getBoundsX()));
        int y = Math.max(0, (int) Math.floor(roi.getBoundsY()));
        int maxX = Math.min(server.getWidth(), (int) Math.ceil(roi.getBoundsX() + roi.getBoundsWidth()));
        int maxY = Math.min(server.getHeight(), (int) Math.ceil(roi.getBoundsY() + roi.getBoundsHeight()));
        int width = maxX - x;
        int height = maxY - y;
        if (width <= 0 || height <= 0)
            throw new IOException("ROI bounds are outside the image.");
        return ImageRegion.createInstance(x, y, width, height, 0, 0);
    }

    private String imageBaseName(ImageServer<BufferedImage> server) {
        Path localPath = getLocalServerPath(server);
        if (localPath != null && localPath.getFileName() != null)
            return sanitizeFilePart(CIDeconvolveCli.stem(localPath));
        String name = server.getMetadata() == null ? "active-image" : server.getMetadata().getName();
        if (name == null || name.isBlank())
            name = "active-image";
        return sanitizeFilePart(name);
    }

    private static String sanitizeFilePart(String value) {
        String sanitized = UNSAFE_FILENAME_CHARS.matcher(Objects.toString(value, "roi").trim()).replaceAll("_");
        sanitized = sanitized.replaceAll("_+", "_");
        if (sanitized.isBlank() || ".".equals(sanitized) || "..".equals(sanitized))
            return "roi";
        return sanitized;
    }

    private Path getDirectInputPath(ImageServer<BufferedImage> server) {
        Path path = getLocalServerPath(server);
        return CIDeconvolveCli.isSupportedDirectInput(path) ? path : null;
    }

    Path getDefaultOutputDirectory(ImageServer<BufferedImage> server) {
        Path projectPath = null;
        var project = qupath.getProject();
        if (project != null)
            projectPath = project.getPath();
        return defaultOutputDirectory(projectPath, getLocalServerPath(server), CIDeconvolvePrefs.normalizedOutputLocation());
    }

    Path getSettingsDirectory(ImageServer<BufferedImage> server) {
        Path projectPath = null;
        var project = qupath.getProject();
        if (project != null)
            projectPath = project.getPath();
        Path base = projectDirectory(projectPath);
        if (base == null)
            base = dataDirectory(getLocalServerPath(server));
        if (base == null)
            base = Path.of(System.getProperty("user.home"));
        return base.resolve(CIDeconvolveSettingsStore.SETTINGS_DIRECTORY_NAME);
    }

    static Path defaultOutputDirectory(Path projectPath, Path dataPath, String mode) {
        Path base = null;
        if (CIDeconvolvePrefs.OUTPUT_LOCATION_DATA_FILE.equals(mode))
            base = dataDirectory(dataPath);
        if (base == null)
            base = projectDirectory(projectPath);
        if (base == null)
            base = dataDirectory(dataPath);
        if (base == null)
            base = Path.of(System.getProperty("user.home"));
        return base.resolve("cidecon");
    }

    private static Path projectDirectory(Path projectPath) {
        if (projectPath == null)
            return null;
        String name = projectPath.getFileName() == null ? "" : projectPath.getFileName().toString().toLowerCase();
        if (Files.isRegularFile(projectPath) || name.endsWith(".qpproj"))
            return projectPath.getParent();
        return projectPath;
    }

    private static Path dataDirectory(Path dataPath) {
        if (dataPath == null)
            return null;
        return dataPath.getParent();
    }

    private Path getLocalServerPath(ImageServer<BufferedImage> server) {
        var uris = server.getURIs();
        if (uris.size() != 1)
            return null;
        URI uri = uris.iterator().next();
        return GeneralTools.toPath(uri);
    }

    private record OutputDisplay(ImageData.ImageType imageType, List<ImageChannel> channels) {
        static OutputDisplay from(ImageData<?> data) {
            var server = data.getServer();
            return new OutputDisplay(data.getImageType(),
                    server.isRGB() ? List.of() : List.copyOf(server.getMetadata().getChannels()));
        }
    }

    private void openOutputs(List<Path> outputPaths, Path outputDirectory,
                             Project<BufferedImage> project, OutputDisplay display, RunLog log) {
        if (outputPaths.isEmpty())
            return;
        if (qupath.getProject() != project) {
            String message = "The active project changed during processing. The exports are in " + outputDirectory;
            appendLog(log, message);
            Dialogs.showWarningNotification(TITLE, message);
            return;
        }
        var compatible = outputPaths.stream().filter(path -> !CIDeconvolveCli.isZarrV3(path)).toList();
        for (Path output : outputPaths)
            appendLog(log, "Created " + output);
        if (compatible.isEmpty()) {
            Dialogs.showPlainMessage(TITLE, "QuPath 0.7 cannot open the NGFF/Zarr v3 outputs in " + outputDirectory);
            revealDirectory(outputDirectory);
            return;
        }
        try {
            if (project != null) {
                var entries = addOutputsToProject(project, compatible, display, log);
                qupath.refreshProject();
                if (entries.isEmpty()) {
                    Dialogs.showWarningNotification(TITLE, "Could not add exports to the project. Files are in " + outputDirectory);
                    return;
                }
                if (!qupath.openImageEntry(entries.getFirst())) {
                    appendLog(log, "Opening the first project result was cancelled.");
                    return;
                }
            } else if (!openOutputInViewer(compatible.getFirst(), display, log)) {
                return;
            }
            logLoadedPixelCalibration(qupath.getViewer().getServer(), log);
            appendLog(log, "Opened " + qupath.getDisplayedImageName(qupath.getImageData()));
        } catch (Exception e) {
            logger.warn("Could not open CI Deconvolve results", e);
            appendLog(log, "Unable to open output in QuPath: " + e.getMessage());
            Dialogs.showWarningNotification(TITLE, "Could not open the exports. Files are in " + outputDirectory);
        }
    }

    private void openOutputsOnFxAndWait(
            List<Path> outputPaths,
            Path outputDirectory,
            Project<BufferedImage> project,
            OutputDisplay display,
            RunLog log
    ) throws InterruptedException {
        if (outputPaths.isEmpty())
            return;
        if (Platform.isFxApplicationThread()) {
            openOutputs(outputPaths, outputDirectory, project, display, log);
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                openOutputs(outputPaths, outputDirectory, project, display, log);
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        latch.await();
        Throwable t = error.get();
        if (t != null)
            appendLog(log, "Unable to import/open CIDeconvolve outputs: " + t.getMessage());
    }

    private List<ProjectImageEntry<BufferedImage>> addOutputsToProject(
            Project<BufferedImage> project, List<Path> outputPaths, OutputDisplay display, RunLog log) throws IOException {
        var entries = new ArrayList<ProjectImageEntry<BufferedImage>>();
        for (Path outputPath : outputPaths) {
            ProjectImageEntry<BufferedImage> entry = null;
            try {
                var data = buildOutputData(outputPath, display, log);
                try (var server = data.getServer()) {
                    entry = project.addImage(server.getBuilder());
                    entry.setImageName(outputPath.getFileName().toString());
                    entry.putMetadataValue("CIDeconvolve output", "true");
                    entry.putMetadataValue("CIDeconvolve output path", outputPath.toString());
                    Path provenance = provenancePath(outputPath);
                    if (Files.isRegularFile(provenance))
                        entry.putMetadataValue("CIDeconvolve provenance", Files.readString(provenance));
                    entry.saveImageData(data);
                    entries.add(entry);
                }
            } catch (Exception e) {
                if (entry != null && !entries.contains(entry))
                    project.removeAllImages(List.of(entry), false);
                appendLog(log, "Unable to add output to QuPath project: " + outputPath + " (" + e.getMessage() + ")");
            }
        }
        if (!entries.isEmpty())
            project.syncChanges();
        if (!entries.isEmpty() && entries.size() != outputPaths.size())
            Dialogs.showWarningNotification(TITLE, "Some exports could not be added to the project.");
        return entries;
    }

    private ImageData<BufferedImage> buildOutputData(Path outputPath, OutputDisplay display, RunLog log) throws IOException {
        var server = ImageServers.buildServer(outputPath.toUri());
        try {
            applyCIDeconvolvePixelCalibration(server, outputPath, log);
            ChannelColors.apply(server, display.channels());
            var imageType = switch (Objects.requireNonNullElse(display.imageType(), ImageData.ImageType.UNSET)) {
                case UNSET -> server.isRGB() ? ImageData.ImageType.OTHER : ImageData.ImageType.FLUORESCENCE;
                case BRIGHTFIELD_H_DAB, BRIGHTFIELD_H_E, BRIGHTFIELD_OTHER ->
                        server.isRGB() ? display.imageType() : ImageData.ImageType.OTHER;
                default -> display.imageType();
            };
            var data = new ImageData<>(server, imageType);
            data.setChanged(false);
            return data;
        } catch (RuntimeException e) {
            try {
                server.close();
            } catch (Exception closeError) {
                e.addSuppressed(closeError);
            }
            throw e;
        }
    }

    private boolean openOutputInViewer(Path outputPath, OutputDisplay display, RunLog log) throws IOException {
        var viewer = qupath.getViewer();
        if (viewer == null)
            throw new IOException("No active QuPath viewer is available.");
        var source = viewer.getImageData();
        if (source != null && source.isChanged() && !Commands.promptToSaveImageData(qupath, source, true))
            return false;
        var data = buildOutputData(outputPath, display, log);
        try {
            viewer.setImageData(data);
        } catch (IOException | RuntimeException e) {
            try {
                data.getServer().close();
            } catch (Exception closeError) {
                e.addSuppressed(closeError);
            }
            throw e;
        }
        return true;
    }

    private void applyCIDeconvolvePixelCalibration(
            ImageServer<BufferedImage> server,
            Path outputPath,
            RunLog log
    ) {
        if (!Files.isDirectory(outputPath))
            return;
        CIDeconvolveCli.readPhysicalPixelSizes(outputPath).ifPresent(pixelSizes -> {
            try {
                var metadata = new qupath.lib.images.servers.ImageServerMetadata.Builder(server.getMetadata())
                        .pixelSizeMicrons(pixelSizes.x(), pixelSizes.y())
                        .zSpacingMicrons(pixelSizes.z())
                        .build();
                server.setMetadata(metadata);
                appendLog(
                        log,
                        String.format(
                                "Applied CIDeconvolve OME-Zarr pixel calibration: XY=%.6g x %.6g um, Z=%.6g um",
                                pixelSizes.x(), pixelSizes.y(), pixelSizes.z()));
            } catch (IllegalArgumentException e) {
                appendLog(log, "Unable to apply CIDeconvolve OME-Zarr pixel calibration: " + e.getMessage());
            }
        });
    }

    private void logLoadedPixelCalibration(ImageServer<BufferedImage> server, RunLog log) {
        var cal = server.getPixelCalibration();
        if (cal == null) {
            appendLog(log, "QuPath loaded pixel calibration: unavailable");
            return;
        }
        String xy = cal.hasPixelSizeMicrons()
                ? String.format("%.6g x %.6g um", cal.getPixelWidthMicrons(), cal.getPixelHeightMicrons())
                : cal.getPixelWidth() + " x " + cal.getPixelHeight() + " "
                        + cal.getPixelWidthUnit() + "/" + cal.getPixelHeightUnit();
        String z = cal.hasZSpacingMicrons()
                ? String.format("%.6g um", cal.getZSpacingMicrons())
                : cal.getZSpacing() + " " + cal.getZSpacingUnit();
        appendLog(log, "QuPath loaded pixel calibration: XY=" + xy + ", Z=" + z);
    }

    private RunLog createRunLog(CIDeconvolveParameters parameters) {
        if (CIDeconvolveParameters.LOG_QUPATH.equals(parameters.getLogTarget()))
            return new RunLog(null, null, null, new java.util.concurrent.CopyOnWriteArrayList<>());
        return createDialogLog();
    }

    private RunLog createDialogLog() {
        TextArea logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(false);
        logArea.getStyleClass().add("deconvolve-log");
        Button cancelButton = new Button("Stop");
        Button closeButton = new Button("Close");
        closeButton.getStyleClass().add("deconvolve-primary");

        HBox buttons = new HBox(8, cancelButton, closeButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.getStyleClass().add("deconvolve-footer");
        VBox pane = new VBox(logArea);
        pane.getStyleClass().add("deconvolve-body");
        VBox.setVgrow(logArea, Priority.ALWAYS);
        var root = new BorderPane(pane);
        var data = getActiveImageData();
        var subtitle = data == null ? "Python output" : ServerTools.getDisplayableImageName(data.getServer());
        root.setTop(CIDeconvolveWindowStyle.header("Deconvolution log", subtitle));
        root.setBottom(buttons);
        CIDeconvolveWindowStyle.apply(root);

        Stage stage = new Stage();
        stage.initOwner(qupath.getStage());
        stage.initModality(Modality.NONE);
        stage.setTitle(TITLE + " · Run log");
        CIDeconvolveIcons.addStageIcon(stage);
        stage.setMinWidth(440);
        stage.setMinHeight(280);
        stage.setScene(new Scene(root, 760, 480));
        closeButton.setOnAction(e -> stage.close());
        stage.show();
        return new RunLog(logArea, cancelButton, stage, new java.util.concurrent.CopyOnWriteArrayList<>());
    }

    private static void appendLog(RunLog log, String line) {
        log.lines().add(Objects.toString(line, ""));
        if (log.logArea() == null) {
            String message = Objects.toString(line, "");
            logger.info(message);
            System.out.println(message);
            return;
        }
        Runnable append = () -> {
            log.logArea().appendText(Objects.toString(line, "") + System.lineSeparator());
            log.logArea().positionCaret(log.logArea().getLength());
        };
        if (Platform.isFxApplicationThread())
            append.run();
        else
            Platform.runLater(append);
    }

    private Path getPythonExecutable() {
        String path = CIDeconvolvePrefs.pythonPathProperty().get();
        if (path == null || path.isBlank()) {
            Dialogs.showErrorMessage(TITLE, "Set the Python executable in the CI Deconvolve preferences first.");
            return null;
        }
        Path python = Path.of(path);
        if (!Files.isRegularFile(python)) {
            Dialogs.showErrorMessage(TITLE, "Python executable does not exist: " + python);
            return null;
        }
        return python;
    }

    private ImageData<BufferedImage> getActiveImageData() {
        var viewer = qupath.getViewer();
        if (viewer == null)
            return null;
        return viewer.getImageData();
    }

    private static void revealDirectory(Path outputDirectory) {
        try {
            if (Desktop.isDesktopSupported())
                Desktop.getDesktop().open(outputDirectory.toFile());
        } catch (IOException e) {
            Dialogs.showErrorMessage(TITLE, "Output folder: " + outputDirectory);
        }
    }

    private interface CancelChecker {
        boolean isCancelled();
    }

    private record RunLog(TextArea logArea, Button cancelButton, Stage stage, List<String> lines) {
        private boolean hasDialog() {
            return logArea != null;
        }
    }
}
