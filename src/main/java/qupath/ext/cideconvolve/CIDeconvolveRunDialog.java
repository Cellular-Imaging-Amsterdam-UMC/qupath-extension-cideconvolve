package qupath.ext.cideconvolve;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
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
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.objects.classes.PathClass;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class CIDeconvolveRunDialog {

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
        dialog.setTitle("CIDeconvolve");
        dialog.setHeaderText("Run ci-deconvolve on the active QuPath image");
        Node graphic = CIDeconvolveIcons.iconView(48);
        if (graphic != null)
            dialog.setGraphic(graphic);
        dialog.initOwner(qupath.getStage());

        var okButtonType = new ButtonType("Run", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okButtonType, ButtonType.CANCEL);

        var controls = new Controls(defaultOutputDirectory, settingsDirectory, availableAnnotationClasses());
        dialog.getDialogPane().setContent(createContent(controls));
        Node okButton = dialog.getDialogPane().lookupButton(okButtonType);
        okButton.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                readRequest(controls);
            } catch (IllegalArgumentException e) {
                Dialogs.showErrorMessage("CIDeconvolve", e.getMessage());
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
        dialog.setOnShown(e -> {
            var window = dialog.getDialogPane().getScene().getWindow();
            if (window instanceof javafx.stage.Stage stage)
                CIDeconvolveIcons.addStageIcon(stage);
        });
        return dialog.showAndWait();
    }

    private Node createContent(Controls controls) {
        GridPane main = new GridPane();
        main.setHgap(8);
        main.setVgap(6);
        main.setPadding(new Insets(8));

        int row = 0;
        main.add(createSettingsContent(controls), 0, row++, 4, 1);
        main.add(createOutputContent(controls), 0, row++, 4, 1);
        main.add(createRoiContent(controls), 0, row++, 4, 1);
        addRow(main, row++, "Iterations", controls.iterations);
        addRow(main, row++, "Start estimate", controls.start);
        addRow(main, row++, "Device", controls.device);
        addRow(main, row++, "Microscope", controls.microscopeType);
        addPairRow(main, row++, "NA", controls.na, "Sample RI", controls.sampleRi);
        addPairRow(main, row++, "Emission wl", controls.emissionWl, "Excitation wl", controls.excitationWl);
        addRow(main, row++, "Pinhole Airy", controls.pinholeAiry);
        addPairRow(main, row++, "Pixel XY (um)", controls.pixelSizeXy, "Pixel Z (um)", controls.pixelSizeZ);
        main.add(controls.overruleMetadata, 1, row++, 3, 1);

        TitledPane advanced = new TitledPane("Advanced", createAdvancedContent(controls));
        advanced.setExpanded(false);
        main.add(advanced, 0, row, 4, 1);

        ScrollPane scrollPane = new ScrollPane(main);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportWidth(672);
        scrollPane.setPrefViewportHeight(520);
        return scrollPane;
    }

    private Node createSettingsContent(Controls controls) {
        controls.refreshSavedSettings();

        Button restore = new Button("Restore");
        restore.setDisable(!controls.hasLastSettings());
        restore.setOnAction(e -> loadSettings(CIDeconvolveSettingsStore.lastSettingsPath(settingsDirectory), controls));

        Button save = new Button("Save...");
        save.setOnAction(e -> saveSettingsAs(controls));

        Button load = new Button("Load...");
        load.setOnAction(e -> loadSettingsFromFile(controls));

        controls.savedSettings.setOnAction(e -> {
            if (controls.applyingSettings)
                return;
            Path selected = controls.savedSettings.getValue();
            if (selected != null)
                loadSettings(selected, controls);
        });

        HBox row = new HBox(6, new Label("Settings:"), controls.savedSettings, restore, save, load);
        HBox.setHgrow(controls.savedSettings, Priority.ALWAYS);
        return row;
    }

    private Node createOutputContent(Controls controls) {
        HBox row = new HBox(8, new Label("Output format"), controls.outputFormat, controls.createProjection);
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
                new Label("Region:"),
                controls.fullImage,
                controls.selectedObjects,
                controls.annotationClassMode,
                controls.annotationClass);
        HBox.setHgrow(controls.annotationClass, Priority.ALWAYS);
        return row;
    }

    private Node createAdvancedContent(Controls controls) {
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.setPadding(new Insets(8, 0, 0, 0));
        int row = 0;
        addRow(grid, row++, "Log output", controls.logTarget);
        grid.add(controls.saveQcMips, 1, row++, 3, 1);
        grid.add(controls.writeManifest, 1, row++, 3, 1);
        grid.add(controls.keepTemporaryInputs, 1, row++, 3, 1);
        addRow(grid, row++, "Background", controls.background);
        addRow(grid, row++, "Offset", controls.offset);
        addRow(grid, row++, "Damping", controls.damping);
        addRow(grid, row++, "Prefilter sigma", controls.prefilterSigma);
        addRow(grid, row++, "Convergence", controls.convergence);
        addRow(grid, row++, "Rel. threshold", controls.relThreshold);
        addRow(grid, row++, "Check every", controls.checkEvery);
        addRow(grid, row++, "Immersion RI", controls.refractiveIndex);
        addRow(grid, row++, "2D mode", controls.twoDMode);
        addRow(grid, row++, "2D WF strength", controls.twoDWfAggressiveness);
        addRow(grid, row++, "2D WF bg radius", controls.twoDWfBgRadiusUm);
        addRow(grid, row, "2D WF bg scale", controls.twoDWfBgScale);
        return grid;
    }

    private static void addRow(GridPane grid, int row, String label, Node control) {
        Label labelNode = new Label(label);
        labelNode.setLabelFor(control);
        grid.add(labelNode, 0, row);
        grid.add(control, 1, row, 3, 1);
        GridPane.setHgrow(control, Priority.ALWAYS);
    }

    private static void addPairRow(GridPane grid, int row, String label1, Node control1, String label2, Node control2) {
        Label labelNode1 = new Label(label1);
        labelNode1.setLabelFor(control1);
        Label labelNode2 = new Label(label2);
        labelNode2.setLabelFor(control2);
        grid.add(labelNode1, 0, row);
        grid.add(control1, 1, row);
        grid.add(labelNode2, 2, row);
        grid.add(control2, 3, row);
        GridPane.setHgrow(control1, Priority.ALWAYS);
        GridPane.setHgrow(control2, Priority.ALWAYS);
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
        parameters.setDamping(text(controls.damping, "Damping"));
        parameters.setPrefilterSigma(parseDouble(controls.prefilterSigma, "Prefilter sigma"));
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
        controls.damping.setText(parameters.getDamping());
        controls.prefilterSigma.setText(Double.toString(parameters.getPrefilterSigma()));
        controls.start.setValue(parameters.getStart());
        controls.convergence.setValue(parameters.getConvergence());
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
            Dialogs.showErrorMessage("CIDeconvolve", "Unable to save last settings: " + e.getMessage());
        }
    }

    private void saveSettingsAs(Controls controls) {
        CIDeconvolveRunRequest request;
        try {
            request = readRequest(controls);
        } catch (IllegalArgumentException e) {
            Dialogs.showErrorMessage("CIDeconvolve", e.getMessage());
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save CIDeconvolve settings");
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
            Dialogs.showErrorMessage("CIDeconvolve", "Unable to save settings: " + e.getMessage());
        }
    }

    private void loadSettingsFromFile(Controls controls) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Load CIDeconvolve settings");
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
            Dialogs.showErrorMessage("CIDeconvolve", "Unable to load settings: " + e.getMessage());
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
        private final RadioButton fullImage = new RadioButton("Full Image");
        private final RadioButton selectedObjects = new RadioButton("Selected");
        private final RadioButton annotationClassMode = new RadioButton("Class");
        private final ComboBox<String> annotationClass = new ComboBox<>();
        private final CheckBox createProjection = new CheckBox("Create max-Z projection");
        private final CheckBox saveQcMips = new CheckBox("Save QC MIPs");
        private final CheckBox writeManifest = new CheckBox("Write run manifest");
        private final CheckBox keepTemporaryInputs = new CheckBox("Keep temporary exported inputs");
        private final Spinner<Integer> iterations = new Spinner<>(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 10_000, 40));
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
        private final TextField damping = textField("none");
        private final TextField prefilterSigma = textField("0.0");
        private final ComboBox<String> start = combo("auto", "auto", "flat", "percentile_flat",
                "observed", "observed_bgsub", "lowpass", "lowpass_bgsub", "hybrid");
        private final ComboBox<String> convergence = combo("auto", "auto", "fixed", "none");
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
            checkEvery.setEditable(true);
            saveQcMips.setSelected(true);
            writeManifest.setSelected(true);
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
