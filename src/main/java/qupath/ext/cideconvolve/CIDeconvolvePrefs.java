package qupath.ext.cideconvolve;

import javafx.beans.property.StringProperty;
import org.controlsfx.control.PropertySheet;
import qupath.fx.prefs.controlsfx.PropertyItemBuilder;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.prefs.PathPrefs;

import java.util.List;

public final class CIDeconvolvePrefs {

    public static final String CATEGORY = CIDeconvolveExtension.NAME;
    public static final String OUTPUT_LOCATION_PROJECT = "project folder";
    public static final String OUTPUT_LOCATION_DATA_FILE = "original data file folder";

    private static final StringProperty pythonPath = PathPrefs.createPersistentPreference(
            "cideconvolve.pythonPath", "");
    private static final StringProperty outputLocation = PathPrefs.createPersistentPreference(
            "cideconvolve.outputLocation", OUTPUT_LOCATION_PROJECT);
    private static final StringProperty defaultOutputFormat = PathPrefs.createPersistentPreference(
            "cideconvolve.defaultOutputFormat", CIDeconvolveParameters.FORMAT_OME_ZARR);

    private CIDeconvolvePrefs() {
    }

    public static StringProperty pythonPathProperty() {
        return pythonPath;
    }

    public static StringProperty outputLocationProperty() {
        return outputLocation;
    }

    public static StringProperty defaultOutputFormatProperty() {
        return defaultOutputFormat;
    }

    public static String normalizedOutputLocation() {
        String value = outputLocation.get();
        if (OUTPUT_LOCATION_DATA_FILE.equals(value) || "data-file".equals(value))
            return OUTPUT_LOCATION_DATA_FILE;
        return OUTPUT_LOCATION_PROJECT;
    }

    public static void installPreferenceItems(QuPathGUI qupath) {
        List<PropertySheet.Item> items = qupath.getPreferencePane().getPropertySheet().getItems();
        items.add(new PropertyItemBuilder<>(pythonPath, String.class)
                .propertyType(PropertyItemBuilder.PropertyType.GENERAL)
                .name("Python executable")
                .category(CATEGORY)
                .description("Full path to the Python 3.12 executable containing ci-deconvolve and PyTorch.")
                .build());
        items.add(new PropertyItemBuilder<>(outputLocation, String.class)
                .propertyType(PropertyItemBuilder.PropertyType.GENERAL)
                .name("Output location")
                .category(CATEGORY)
                .description("Where to create the cidecon output folder.")
                .choices(List.of(OUTPUT_LOCATION_PROJECT, OUTPUT_LOCATION_DATA_FILE))
                .build());
        items.add(new PropertyItemBuilder<>(defaultOutputFormat, String.class)
                .propertyType(PropertyItemBuilder.PropertyType.GENERAL)
                .name("Default output format")
                .category(CATEGORY)
                .description("Default ci-deconvolve output format: ome-zarr or ome-tiff.")
                .choices(List.of(CIDeconvolveParameters.FORMAT_OME_ZARR, CIDeconvolveParameters.FORMAT_OME_TIFF))
                .build());
    }
}
