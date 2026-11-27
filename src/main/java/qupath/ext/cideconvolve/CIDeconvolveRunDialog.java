package qupath.ext.cideconvolve;

import javafx.animation.PauseTransition;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Button;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.util.StringConverter;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.objects.classes.PathClass;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class CIDeconvolveRunDialog {

    private static final String TITLE = CIDeconvolveExtension.NAME;
    private final QuPathGUI qupath;
    private final Path defaultOutputDirectory;
    private final Path settingsDirectory;

    public CIDeconvolveRunDialog(QuPathGUI qupath, Path defaultOutputDirectory, Path settingsDirectory) {
        this.qupath = qupath;
        this.defaultOutputDirectory = defaultOutputDirectory;
        this.settingsDirectory = settingsDirectory;
    }

    public Optional<CIDeconvolveRunRequest> show() {
        Dialog<CIDeconvolveRunRequest> dialog = new Dialog<>();
        dialog.setTitle(TITLE);
        dialog.initOwner(qupath.getStage());
        dialog.setResizable(true);

        var pane = dialog.getDialogPane();
        CIDeconvolveWindowStyle.apply(pane);
        var data = qupath.getImageData();
        var subtitle = data == null ? "Deconvolution settings" : ServerTools.getDisplayableImageName(data.getServer());
        pane.setHeader(CIDeconvolveWindowStyle.header(TITLE, subtitle));
        pane.setPrefWidth(790);

        var okButtonType = new ButtonType("Run Deconvolution", ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().addAll(okButtonType, ButtonType.CANCEL);

        var controls = new Controls(defaultOutputDirectory, settingsDirectory, availableAnnotationClasses());
        var advanced = new TitledPane("Advanced settings", createAdvancedContent(controls));
        advanced.setAnimated(false);
        advanced.setExpanded(false);
        var scroll = createContent(controls, advanced);
        pane.setContent(scroll);
        // Measure the viewport after the native dialog has settled on its monitor.
        var refit = new PauseTransition(Duration.millis(100));
        refit.setOnFinished(event -> fitSettingsWindow(scroll));
        advanced.expandedProperty().addListener((observable, previous, expanded) ->
                refit.playFromStart());
        var okButton = (Button) pane.lookupButton(okButtonType);
        okButton.getStyleClass().add("deconvolve-primary");
        okButton.setMinWidth(Region.USE_PREF_SIZE);
        ButtonBar.setButtonUniformSize(okButton, false);
        ButtonBar.setButtonUniformSize(pane.lookupButton(ButtonType.CANCEL), false);
        okButton.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                readRequest(controls);
            } catch (IllegalArgumentException e) {
                Dialogs.showErrorMessage(TITLE, e.getMessage());
                event.consume();
            }
        });

        dialog.setResultConverter(buttonType -> {
            if (buttonType != okButtonType)
                return null;
            var request = readRequest(controls);
            saveLastSettings(request.getParameters());
            return request;
        });
        dialog.setOnShowing(e -> {
            if (pane.getScene().getWindow() instanceof Stage stage) {
                stage.setMinWidth(660);
                stage.setMinHeight(360);
            }
        });
        dialog.setOnShown(e -> {
            if (pane.getScene().getWindow() instanceof Stage stage)
                CIDeconvolveIcons.addStageIcon(stage);
            refit.playFromStart();
        });
        return dialog.showAndWait();
    }

    private static void fitSettingsWindow(ScrollPane scroll) {
        var scene = scroll.getScene();
        if (scene == null || !(scene.getWindow() instanceof Stage stage) || !stage.isShowing() || stage.isMaximized())
            return;
        scene.getRoot().applyCss();
        scene.getRoot().layout();
        var viewport = scroll.getViewportBounds();
        if (viewport.getWidth() <= 0 || viewport.getHeight() <= 0)
            return;

        var screen = Screen.getScreensForRectangle(stage.getX() + stage.getWidth() / 2,
                        stage.getY() + stage.getHeight() / 2, 1, 1)
                .stream().findFirst().orElse(Screen.getPrimary()).getVisualBounds();
        double chrome = stage.getHeight() - viewport.getHeight();
        double required = Math.ceil(scroll.getContent().prefHeight(viewport.getWidth()) + chrome + 2);
        double height = Math.max(stage.getMinHeight(), Math.min(required, screen.getHeight()));
        stage.setHeight(height);
        stage.setY(Math.clamp(stage.getY(), screen.getMinY(), Math.max(screen.getMinY(), screen.getMaxY() - height)));
    }

    private ScrollPane createContent(Controls controls, TitledPane advanced) {
        var algorithm = parameterGrid();
        addPairRow(algorithm, 0, "Iterations", controls.iterations, "Start estimate", controls.start);
        addPairRow(algorithm, 1, "Convergence", controls.convergence, "Relative threshold", controls.relThreshold);
        addPairRow(algorithm, 2, "T start", controls.timeStart, "T stop", controls.timeStop);
        addField(algorithm, 3, 0, "T step", controls.timeStep);

        var body = new VBox(8,
                CIDeconvolveWindowStyle.card("Image region", createRoiContent(controls)),
                CIDeconvolveWindowStyle.card("Deconvolution", algorithm),
                CIDeconvolveWindowStyle.card("Microscope and image metadata", createMetadataContent(controls),
                        controls.overruleMetadata),
                CIDeconvolveWindowStyle.card("Output", createOutputContent(controls)),
                CIDeconvolveWindowStyle.card("Saved settings", createSettingsContent(controls)),
                advanced);
        body.getStyleClass().add("deconvolve-body");

        var scrollPane = new ScrollPane(body);
        scrollPane.getStyleClass().add("deconvolve-scroll");
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setPrefViewportWidth(770);
        return scrollPane;
    }

    private Node createSettingsContent(Controls controls) {
        controls.refreshSavedSettings();

        Button restore = new Button("Restore");
        restore.setDisable(!controls.hasLastSettings());
        restore.setOnAction(e -> loadSettings(CIDeconvolveSettingsStore.lastSettingsPath(settingsDirectory), controls));

        Button save = new Button("Save…");
        save.setOnAction(e -> saveSettingsAs(controls));

        Button load = new Button("Load…");
        load.setOnAction(e -> loadSettingsFromFile(controls));

        controls.savedSettings.setOnAction(e -> {
            if (controls.applyingSettings)
                return;
            Path selected = controls.savedSettings.getValue();
            if (selected != null)
                loadSettings(selected, controls);
        });

        HBox row = new HBox(7, controls.savedSettings, restore, save, load);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(controls.savedSettings, Priority.ALWAYS);
        return row;
    }

    private Node createOutputContent(Controls controls) {
        var format = new Label("Output format");
        format.setLabelFor(controls.outputFormat);
        HBox row = new HBox(10, format, controls.outputFormat, controls.createProjection);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(controls.outputFormat, Priority.ALWAYS);
        return row;
    }

    private Node createRoiContent(Controls controls) {
        ToggleGroup group = new ToggleGroup();
        controls.fullImage.setToggleGroup(group);
        controls.selectedObjects.setToggleGroup(group);
        controls.annotationClassMode.setToggleGroup(group);
        controls.fullImage.setSelected(true);
        controls.annotationClass.setMaxWidth(Double.MAX_VALUE);
        controls.annotationClass.disableProperty().bind(controls.annotationClassMode.selectedProperty().not());

        HBox row = new HBox(10,
                controls.fullImage,
                controls.selectedObjects,
                controls.annotationClassMode,
                controls.annotationClass);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(controls.annotationClass, Priority.ALWAYS);
        return row;
    }

    private Node createMetadataContent(Controls controls) {
        var grid = parameterGrid();
        addPairRow(grid, 0, "Microscope", controls.microscopeType, "Pinhole (Airy)", controls.pinholeAiry);
        addPairRow(grid, 1, "Numerical aperture", controls.na, "Immersion RI", controls.refractiveIndex);
        addPairRow(grid, 2, "Sample RI", controls.sampleRi, "Excitation (nm)", controls.excitationWl);
        addPairRow(grid, 3, "Emission (nm)", controls.emissionWl, "Pixel size XY (µm)", controls.pixelSizeXy);
        addField(grid, 4, 0, "Pixel size Z (µm)", controls.pixelSizeZ);
        return grid;
    }

    private Node createAdvancedContent(Controls controls) {
        var grid = parameterGrid();
        addPairRow(grid, 0, "Log output", controls.logTarget, "Device", controls.device);
        addPairRow(grid, 1, "Check every", controls.checkEvery, "Background", controls.background);
        addPairRow(grid, 2, "Offset", controls.offset, "Prefilter sigma", controls.prefilterSigma);
        addPairRow(grid, 3, "SNR mode", controls.snrMode, "SNR value", controls.snrValue);
        addPairRow(grid, 4, "Acuity", controls.acuity, "2D mode", controls.twoDMode);
        addPairRow(grid, 5, "2D WF strength", controls.twoDWfAggressiveness, "2D WF bg radius", controls.twoDWfBgRadiusUm);
        addField(grid, 6, 0, "2D WF bg scale", controls.twoDWfBgScale);
        var checkboxes = new FlowPane(14, 6, controls.saveQcMips, controls.writeManifest, controls.keepTemporaryInputs);
        return new VBox(8, grid, checkboxes);
    }

    private static GridPane parameterGrid() {
        var grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(7);
        grid.getColumnConstraints().addAll(new ColumnConstraints(124), valueColumn(),
                new ColumnConstraints(124), valueColumn());
        return grid;
    }

    private static void addPairRow(GridPane grid, int row, String label1, Region control1, String label2, Region control2) {
        addField(grid, row, 0, label1, control1);
        addField(grid, row, 2, label2, control2);
    }

    private static void addField(GridPane grid, int row, int column, String text, Region control) {
        var label = new Label(text);
        label.setLabelFor(control);
        grid.add(label, column, row);
        grid.add(control, column + 1, row);
        control.setMaxWidth(Double.MAX_VALUE);
    }

    private static ColumnConstraints valueColumn() {
        var column = new ColumnConstraints(95, 160, Double.MAX_VALUE);
        column.setHgrow(Priority.ALWAYS);
        return column;
    }

    private CIDeconvolveRunRequest readRequest(Controls controls) {
        Path outputDir = controls.outputDir;
        if (outputDir == null)
            throw new IllegalArgumentException("No output folder could be resolved.");

        CIDeconvolveParameters parameters = new CIDeconvolveParameters();
        parameters.setOutputFormat(controls.outputFormat.getValue());
        String roiMode = readRoiMode(controls);
        parameters.setRoiMode(roiMode);
        if (CIDeconvolveParameters.ROI_ANNOTATION_CLASS.equals(roiMode))
            parameters.setAnnotationClass(comboText(controls.annotationClass, "Annotation class"));
        else
            parameters.setAnnotationClass(controls.annotationClass.getValue());
        parameters.setLogTarget(controls.logTarget.getValue());
        parameters.setCreateProjection(controls.createProjection.isSelected());
        parameters.setIterations(controls.iterations.getValue());
        parameters.setTimeStart(controls.timeStart.getValue());
        parameters.setTimeStop(controls.timeStop.getValue());
        parameters.setTimeStep(controls.timeStep.getValue());
        parameters.setDevice(controls.device.getValue());
        parameters.setMicroscopeType(controls.microscopeType.getValue());
        parameters.setNa(parseDouble(controls.na, "NA"));
        parameters.setEmissionWavelength(text(controls.emissionWl, "Emission wavelength"));
        parameters.setExcitationWavelength(text(controls.excitationWl, "Excitation wavelength"));
        parameters.setPinholeAiry(text(controls.pinholeAiry, "Pinhole Airy"));
        parameters.setPixelSizeXy(parseDouble(controls.pixelSizeXy, "Pixel XY"));
        parameters.setPixelSizeZ(parseDouble(controls.pixelSizeZ, "Pixel Z"));
        parameters.setOverruleMetadata(controls.overruleMetadata.isSelected());
        parameters.setBackground(text(controls.background, "Background"));
        parameters.setOffset(text(controls.offset, "Offset"));
        parameters.setPrefilterSigma(parseDouble(controls.prefilterSigma, "Prefilter sigma"));
        parameters.setSnrMode(controls.snrMode.getValue());
        parameters.setSnrValue(parseDouble(controls.snrValue, "SNR value"));
        parameters.setAcuity(parseDouble(controls.acuity, "Acuity"));
        parameters.setStart(controls.start.getValue());
        parameters.setConvergence(controls.convergence.getValue());
        parameters.setRelThreshold(parseDouble(controls.relThreshold, "Relative threshold"));
        parameters.setCheckEvery(controls.checkEvery.getValue());
        parameters.setRefractiveIndex(parseDouble(controls.refractiveIndex, "Immersion RI"));
        parameters.setSampleRi(parseDouble(controls.sampleRi, "Sample RI"));
        parameters.setTwoDMode(controls.twoDMode.getValue());
        parameters.setTwoDWfAggressiveness(controls.twoDWfAggressiveness.getValue());
        parameters.setTwoDWfBgRadiusUm(parseDouble(controls.twoDWfBgRadiusUm, "2D WF background radius"));
        parameters.setTwoDWfBgScale(parseDouble(controls.twoDWfBgScale, "2D WF background scale"));
        parameters.setSaveQcMips(controls.saveQcMips.isSelected());
        parameters.setWriteManifest(controls.writeManifest.isSelected());
        parameters.setKeepTemporaryInputs(controls.keepTemporaryInputs.isSelected());
        return new CIDeconvolveRunRequest(outputDir, parameters);
    }

    private void applyParameters(Controls controls, CIDeconvolveParameters parameters) {
        parameters.normalize();
        controls.outputFormat.setValue(parameters.getOutputFormat());
        applyRoiMode(controls, parameters.getRoiMode());
        setAnnotationClass(controls, parameters.getAnnotationClass());
        controls.logTarget.setValue(parameters.getLogTarget());
        controls.createProjection.setSelected(parameters.isCreateProjection());
        controls.iterations.getValueFactory().setValue(parameters.getIterations());
        controls.timeStart.getValueFactory().setValue(parameters.getTimeStart());
        controls.timeStop.getValueFactory().setValue(parameters.getTimeStop());
        controls.timeStep.getValueFactory().setValue(parameters.getTimeStep());
        controls.device.setValue(parameters.getDevice());
        controls.microscopeType.setValue(parameters.getMicroscopeType());
        controls.na.setText(Double.toString(parameters.getNa()));
        controls.emissionWl.setText(parameters.getEmissionWavelength());
        controls.excitationWl.setText(parameters.getExcitationWavelength());
        controls.pinholeAiry.setText(parameters.getPinholeAiry());
        controls.pixelSizeXy.setText(Double.toString(parameters.getPixelSizeXy()));
        controls.pixelSizeZ.setText(Double.toString(parameters.getPixelSizeZ()));
        controls.overruleMetadata.setSelected(parameters.isOverruleMetadata());
        controls.background.setText(parameters.getBackground());
        controls.offset.setText(parameters.getOffset());
        controls.prefilterSigma.setText(Double.toString(parameters.getPrefilterSigma()));
        controls.snrMode.setValue(parameters.getSnrMode());
        controls.snrValue.setText(Double.toString(parameters.getSnrValue()));
        controls.acuity.setText(Double.toString(parameters.getAcuity()));
        controls.start.setValue(parameters.getStart());
        controls.convergence.setValue("auto".equals(parameters.getConvergence()) ? "fixed" : parameters.getConvergence());
        controls.relThreshold.setText(Double.toString(parameters.getRelThreshold()));
        controls.checkEvery.getValueFactory().setValue(parameters.getCheckEvery());
        controls.refractiveIndex.setText(Double.toString(parameters.getRefractiveIndex()));
        controls.sampleRi.setText(Double.toString(parameters.getSampleRi()));
        controls.twoDMode.setValue(parameters.getTwoDMode());
        controls.twoDWfAggressiveness.setValue(parameters.getTwoDWfAggressiveness());
        controls.twoDWfBgRadiusUm.setText(Double.toString(parameters.getTwoDWfBgRadiusUm()));
        controls.twoDWfBgScale.setText(Double.toString(parameters.getTwoDWfBgScale()));
        controls.saveQcMips.setSelected(parameters.isSaveQcMips());
        controls.writeManifest.setSelected(parameters.isWriteManifest());
        controls.keepTemporaryInputs.setSelected(parameters.isKeepTemporaryInputs());
    }

    private void saveLastSettings(CIDeconvolveParameters parameters) {
        try {
            CIDeconvolveSettingsStore.save(CIDeconvolveSettingsStore.lastSettingsPath(settingsDirectory), parameters);
        } catch (IOException e) {
            Dialogs.showErrorMessage(TITLE, "Unable to save last settings: " + e.getMessage());
        }
    }

    private void saveSettingsAs(Controls controls) {
        CIDeconvolveRunRequest request;
        try {
            request = readRequest(controls);
        } catch (IllegalArgumentException e) {
            Dialogs.showErrorMessage(TITLE, e.getMessage());
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save CI Deconvolve settings");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON files", "*.json"));
        chooser.setInitialDirectory(settingsDirectory.toFile());
        chooser.setInitialFileName("settings.json");
        var file = chooser.showSaveDialog(qupath.getStage());
        if (file == null)
            return;
        try {
            CIDeconvolveSettingsStore.save(file.toPath(), request.getParameters());
            controls.refreshSavedSettings();
            controls.savedSettings.setValue(file.toPath());
        } catch (IOException e) {
            Dialogs.showErrorMessage(TITLE, "Unable to save settings: " + e.getMessage());
        }
    }

    private void loadSettingsFromFile(Controls controls) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Load CI Deconvolve settings");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON files", "*.json"));
        chooser.setInitialDirectory(settingsDirectory.toFile());
        var file = chooser.showOpenDialog(qupath.getStage());
        if (file != null)
            loadSettings(file.toPath(), controls);
    }

    private void loadSettings(Path path, Controls controls) {
        try {
            controls.applyingSettings = true;
            applyParameters(controls, CIDeconvolveSettingsStore.load(path));
            controls.refreshSavedSettings();
            if (!CIDeconvolveSettingsStore.LAST_SETTINGS_NAME.equalsIgnoreCase(path.getFileName().toString()))
                controls.savedSettings.setValue(path);
        } catch (IOException e) {
            Dialogs.showErrorMessage(TITLE, "Unable to load settings: " + e.getMessage());
        } finally {
            controls.applyingSettings = false;
        }
    }

    private static String text(TextField field, String name) {
        String value = field.getText().trim();
        if (value.isEmpty())
            throw new IllegalArgumentException(name + " cannot be empty.");
        return value;
    }

    private static String comboText(ComboBox<String> combo, String name) {
        String value = Optional.ofNullable(combo.getValue()).orElse("").trim();
        if (value.isEmpty())
            throw new IllegalArgumentException(name + " cannot be empty.");
        return value;
    }

    private static String readRoiMode(Controls controls) {
        if (controls.selectedObjects.isSelected())
            return CIDeconvolveParameters.ROI_SELECTED;
        if (controls.annotationClassMode.isSelected())
            return CIDeconvolveParameters.ROI_ANNOTATION_CLASS;
        return CIDeconvolveParameters.ROI_FULL_IMAGE;
    }

    private static void applyRoiMode(Controls controls, String roiMode) {
        if (CIDeconvolveParameters.ROI_SELECTED.equals(roiMode))
            controls.selectedObjects.setSelected(true);
        else if (CIDeconvolveParameters.ROI_ANNOTATION_CLASS.equals(roiMode))
            controls.annotationClassMode.setSelected(true);
        else
            controls.fullImage.setSelected(true);
    }

    private static void setAnnotationClass(Controls controls, String annotationClass) {
        String value = annotationClass == null || annotationClass.isBlank() ? null : annotationClass.trim();
        if (value != null && !controls.annotationClass.getItems().contains(value))
            controls.annotationClass.getItems().add(value);
        controls.annotationClass.setValue(value);
    }

    private static double parseDouble(TextField field, String name) {
        try {
            return Double.parseDouble(text(field, name));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be a number.");
        }
    }

    private static TextField textField(String value) {
        TextField field = new TextField(value);
        field.setMaxWidth(Double.MAX_VALUE);
        return field;
    }

    private static ComboBox<String> combo(String value, String... values) {
        ComboBox<String> combo = new ComboBox<>();
        combo.getItems().setAll(List.of(values));
        combo.setValue(value);
        combo.setMaxWidth(Double.MAX_VALUE);
        return combo;
    }

    private List<String> availableAnnotationClasses() {
        Set<String> names = new LinkedHashSet<>();
        var project = qupath.getProject();
        if (project != null)
            project.getPathClasses().forEach(pathClass -> addPathClassName(names, pathClass));
        var viewer = qupath.getViewer();
        if (viewer != null && viewer.getImageData() != null) {
            viewer.getImageData().getHierarchy().getAnnotationObjects().stream()
                    .map(object -> object.getPathClass())
                    .forEach(pathClass -> addPathClassName(names, pathClass));
        }
        return names.stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private static void addPathClassName(Set<String> names, PathClass pathClass) {
        if (pathClass == null || !pathClass.isValid())
            return;
        String name = pathClass.toString();
        if (name != null && !name.isBlank())
            names.add(name.trim());
    }

    private static class Controls {
        private final Path outputDir;
        private final Path settingsDirectory;
        private final ComboBox<Path> savedSettings = new ComboBox<>();
        private boolean applyingSettings;
        private final ComboBox<String> outputFormat = combo(defaultOutputFormat(),
                CIDeconvolveParameters.FORMAT_OME_ZARR, CIDeconvolveParameters.FORMAT_OME_TIFF);
        private final ComboBox<String> logTarget = combo(CIDeconvolveParameters.LOG_DIALOG,
                CIDeconvolveParameters.LOG_DIALOG, CIDeconvolveParameters.LOG_QUPATH);
        private final RadioButton fullImage = new RadioButton("Full image");
        private final RadioButton selectedObjects = new RadioButton("Selected objects");
        private final RadioButton annotationClassMode = new RadioButton("Annotation class");
        private final ComboBox<String> annotationClass = new ComboBox<>();
        private final CheckBox createProjection = new CheckBox("Create max-Z projection");
        private final CheckBox saveQcMips = new CheckBox("Save QC MIPs");
        private final CheckBox writeManifest = new CheckBox("Write run manifest");
        private final CheckBox keepTemporaryInputs = new CheckBox("Keep temporary exported inputs");
        private final Spinner<Integer> iterations = new Spinner<>(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 10_000, 40));
        private final Spinner<Integer> timeStart = new Spinner<>(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 1_000_000, 1));
        private final Spinner<Integer> timeStop = new Spinner<>(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 1_000_000, 0));
        private final Spinner<Integer> timeStep = new Spinner<>(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 1_000_000, 1));
        private final ComboBox<String> device = combo("auto", "auto", "cpu", "cuda");
        private final ComboBox<String> microscopeType = combo("confocal", "confocal", "widefield");
        private final TextField na = textField("1.4");
        private final TextField emissionWl = textField("520");
        private final TextField excitationWl = textField("488");
        private final TextField pinholeAiry = textField("1.0");
        private final TextField pixelSizeXy = textField("0.065");
        private final TextField pixelSizeZ = textField("0.2");
        private final CheckBox overruleMetadata = new CheckBox("Overrule image metadata");
        private final TextField background = textField("auto");
        private final TextField offset = textField("auto");
        private final TextField prefilterSigma = textField("0.0");
        private final ComboBox<String> snrMode = combo("none", "none", "auto", "manual");
        private final TextField snrValue = textField("4.0");
        private final TextField acuity = textField("0.0");
        private final ComboBox<String> start = combo("auto", "auto", "flat", "percentile_flat",
                "observed", "observed_bgsub", "lowpass", "lowpass_bgsub", "hybrid");
        private final ComboBox<String> convergence = combo("fixed", "fixed", "none");
        private final TextField relThreshold = textField("0.005");
        private final Spinner<Integer> checkEvery = new Spinner<>(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 10_000, 5));
        private final TextField refractiveIndex = textField("1.515");
        private final TextField sampleRi = textField("1.47");
        private final ComboBox<String> twoDMode = combo("auto", "auto", "legacy_2d");
        private final ComboBox<String> twoDWfAggressiveness = combo("balanced",
                "conservative", "balanced", "strong");
        private final TextField twoDWfBgRadiusUm = textField("0.5");
        private final TextField twoDWfBgScale = textField("1.0");

        private Controls(Path defaultOutputDirectory, Path settingsDirectory, List<String> annotationClasses) {
            outputDir = defaultOutputDir(defaultOutputDirectory);
            this.settingsDirectory = settingsDirectory;
            try {
                java.nio.file.Files.createDirectories(settingsDirectory);
            } catch (IOException ignored) {
            }
            iterations.setEditable(true);
            timeStart.setEditable(true);
            timeStop.setEditable(true);
            timeStep.setEditable(true);
            checkEvery.setEditable(true);
            saveQcMips.setSelected(false);
            writeManifest.setSelected(false);
            annotationClass.getItems().setAll(annotationClasses);
            annotationClass.setPromptText("Choose class");
            annotationClass.setMaxWidth(Double.MAX_VALUE);
            logTarget.setConverter(new StringConverter<>() {
                @Override
                public String toString(String value) {
                    if (CIDeconvolveParameters.LOG_QUPATH.equals(value))
                        return "QuPath log";
                    return "Dialog window";
                }

                @Override
                public String fromString(String string) {
                    return "QuPath log".equals(string)
                            ? CIDeconvolveParameters.LOG_QUPATH
                            : CIDeconvolveParameters.LOG_DIALOG;
                }
            });
            savedSettings.setMaxWidth(Double.MAX_VALUE);
            savedSettings.setPromptText("Choose saved file");
            savedSettings.setConverter(new StringConverter<>() {
                @Override
                public String toString(Path path) {
                    return path == null || path.getFileName() == null ? "" : path.getFileName().toString();
                }

                @Override
                public Path fromString(String string) {
                    return null;
                }
            });
        }

        private void refreshSavedSettings() {
            savedSettings.getItems().setAll(CIDeconvolveSettingsStore.listSavedSettings(settingsDirectory));
        }

        private boolean hasLastSettings() {
            return java.nio.file.Files.isRegularFile(CIDeconvolveSettingsStore.lastSettingsPath(settingsDirectory));
        }

        private static Path defaultOutputDir(Path defaultOutputDirectory) {
            if (defaultOutputDirectory != null)
                return defaultOutputDirectory;
            return Path.of(System.getProperty("user.home"), "cidecon");
        }

        private static String defaultOutputFormat() {
            String format = CIDeconvolvePrefs.defaultOutputFormatProperty().get();
            if (CIDeconvolveParameters.FORMAT_OME_TIFF.equals(format))
                return format;
            return CIDeconvolveParameters.FORMAT_OME_ZARR;
        }
    }
}
