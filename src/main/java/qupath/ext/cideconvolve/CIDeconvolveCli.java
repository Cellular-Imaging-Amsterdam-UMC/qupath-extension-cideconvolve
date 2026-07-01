package qupath.ext.cideconvolve;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.Path;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public final class CIDeconvolveCli {

    private static final Pattern ZARR_FORMAT_3 = Pattern.compile("\"zarr_format\"\\s*:\\s*3\\b");

    private static final String PYTHON_ENTRYPOINT =
            "from ci_deconvolve.cli import main; raise SystemExit(main())";

    private CIDeconvolveCli() {
    }

    public static List<String> buildValidationCommand(Path pythonExecutable) {
        return List.of(pythonExecutable.toString(), "-c", PYTHON_ENTRYPOINT, "--validate-env");
    }

    public static List<String> buildRunCommand(
            Path pythonExecutable,
            Path inputPath,
            Path outputDirectory,
            CIDeconvolveParameters parameters
    ) {
        List<String> command = new ArrayList<>();
        command.add(pythonExecutable.toString());
        command.add("-c");
        command.add(PYTHON_ENTRYPOINT);
        command.add("--input");
        command.add(inputPath.toString());
        command.add("--output");
        command.add(outputDirectory.toString());
        command.add("--output-format");
        command.add(parameters.getOutputFormat());
        command.add("--projection");
        command.add(parameters.isCreateProjection() ? "max-z" : "none");
        command.add("--iterations");
        command.add(Integer.toString(parameters.getIterations()));
        command.add("--device");
        command.add(parameters.getDevice());
        command.add("--background");
        command.add(parameters.getBackground());
        command.add("--offset");
        command.add(parameters.getOffset());
        command.add("--damping");
        command.add(parameters.getDamping());
        command.add("--prefilter-sigma");
        command.add(Double.toString(parameters.getPrefilterSigma()));
        command.add("--start");
        command.add(parameters.getStart());
        command.add("--convergence");
        command.add(parameters.getConvergence());
        command.add("--rel-threshold");
        command.add(Double.toString(parameters.getRelThreshold()));
        command.add("--check-every");
        command.add(Integer.toString(parameters.getCheckEvery()));
        command.add("--na");
        command.add(Double.toString(parameters.getNa()));
        command.add("--refractive-index");
        command.add(Double.toString(parameters.getRefractiveIndex()));
        command.add("--sample-ri");
        command.add(Double.toString(parameters.getSampleRi()));
        command.add("--microscope-type");
        command.add(parameters.getMicroscopeType());
        command.add("--emission-wl");
        command.add(parameters.getEmissionWavelength());
        command.add("--excitation-wl");
        command.add(parameters.getExcitationWavelength());
        command.add("--pinhole-airy");
        command.add(parameters.getPinholeAiry());
        command.add("--pixel-size-xy");
        command.add(Double.toString(parameters.getPixelSizeXy()));
        command.add("--pixel-size-z");
        command.add(Double.toString(parameters.getPixelSizeZ()));
        if (parameters.isOverruleMetadata())
            command.add("--overrule-metadata");
        command.add("--two-d-mode");
        command.add(parameters.getTwoDMode());
        command.add("--two-d-wf-aggressiveness");
        command.add(parameters.getTwoDWfAggressiveness());
        command.add("--two-d-wf-bg-radius-um");
        command.add(Double.toString(parameters.getTwoDWfBgRadiusUm()));
        command.add("--two-d-wf-bg-scale");
        command.add(Double.toString(parameters.getTwoDWfBgScale()));
        if (!parameters.isSaveQcMips())
            command.add("--no-qc-mips");
        if (!parameters.isWriteManifest())
            command.add("--no-manifest");
        if (parameters.isVerbose())
            command.add("--verbose");
        return command;
    }

    public static boolean isSupportedDirectInput(Path path) {
        if (path == null || path.getFileName() == null)
            return false;
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".ome.tif")
                || name.endsWith(".ome.tiff")
                || name.endsWith(".zarr")
                || name.endsWith(".ome.zarr");
    }

    public static Path expectedOutputPath(Path inputPath, Path outputDirectory, String outputFormat) {
        String suffix = CIDeconvolveParameters.FORMAT_OME_ZARR.equals(outputFormat)
                ? "_decon.ome.zarr"
                : "_decon.ome.tiff";
        return outputDirectory.resolve(stem(inputPath) + suffix);
    }

    static String stem(Path path) {
        String name = path.getFileName().toString();
        String lower = name.toLowerCase(Locale.ROOT);
        for (String suffix : List.of(".ome.tiff", ".ome.tif", ".ome.zarr", ".zarr")) {
            if (lower.endsWith(suffix))
                return name.substring(0, name.length() - suffix.length());
        }
        int dot = name.lastIndexOf('.');
        return dot < 1 ? name : name.substring(0, dot);
    }

    public static String printableCommand(List<String> command) {
        List<String> escaped = new ArrayList<>();
        for (String part : command) {
            if (part.contains(" ") || part.contains("\t"))
                escaped.add("\"" + part.replace("\"", "\\\"") + "\"");
            else
                escaped.add(part);
        }
        return String.join(" ", escaped);
    }

    public static boolean isZarrV3(Path path) {
        if (path == null || !Files.isDirectory(path))
            return false;
        Path metadata = path.resolve("zarr.json");
        if (!Files.isRegularFile(metadata))
            return false;
        try {
            String text = Files.readString(metadata);
            return ZARR_FORMAT_3.matcher(text).find();
        } catch (IOException e) {
            return false;
        }
    }

    public record PhysicalPixelSizes(double x, double y, double z) {
    }

    static Optional<PhysicalPixelSizes> readPhysicalPixelSizes(Path zarrPath) {
        if (zarrPath == null || !Files.isDirectory(zarrPath))
            return Optional.empty();
        Path attrs = zarrPath.resolve(".zattrs");
        if (!Files.isRegularFile(attrs))
            return Optional.empty();
        try {
            JsonObject root = JsonParser.parseString(Files.readString(attrs)).getAsJsonObject();
            Optional<PhysicalPixelSizes> cideconvolveSizes = readCIDeconvolvePixelSizes(root);
            if (cideconvolveSizes.isPresent())
                return cideconvolveSizes;
            return readMultiscalePixelSizes(root);
        } catch (IOException | IllegalStateException e) {
            return Optional.empty();
        }
    }

    private static Optional<PhysicalPixelSizes> readCIDeconvolvePixelSizes(JsonObject root) {
        JsonObject cideconvolve = object(root.get("cideconvolve"));
        if (cideconvolve == null)
            return Optional.empty();
        JsonObject sizes = object(cideconvolve.get("physical_pixel_sizes_um"));
        if (sizes == null)
            return Optional.empty();
        Double x = number(sizes.get("x"));
        Double y = number(sizes.get("y"));
        Double z = number(sizes.get("z"));
        return pixelSizes(x, y, z);
    }

    private static Optional<PhysicalPixelSizes> readMultiscalePixelSizes(JsonObject root) {
        JsonArray multiscales = array(root.get("multiscales"));
        if (multiscales == null)
            return Optional.empty();
        for (JsonElement multiscaleElement : multiscales) {
            JsonObject multiscale = object(multiscaleElement);
            if (multiscale == null)
                continue;
            JsonArray datasets = array(multiscale.get("datasets"));
            if (datasets == null)
                continue;
            for (JsonElement datasetElement : datasets) {
                JsonObject dataset = object(datasetElement);
                if (dataset == null)
                    continue;
                JsonArray transforms = array(dataset.get("coordinateTransformations"));
                if (transforms == null)
                    continue;
                for (JsonElement transformElement : transforms) {
                    JsonObject transform = object(transformElement);
                    if (transform == null || !"scale".equals(string(transform.get("type"))))
                        continue;
                    List<Double> values = numbers(array(transform.get("scale")));
                    if (values.size() >= 5)
                        return pixelSizes(values.get(4), values.get(3), values.get(2));
                    if (values.size() >= 3)
                        return pixelSizes(values.get(values.size() - 1), values.get(values.size() - 2), 1.0);
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<PhysicalPixelSizes> pixelSizes(Double x, Double y, Double z) {
        if (isPositiveFinite(x) && isPositiveFinite(y) && isPositiveFinite(z))
            return Optional.of(new PhysicalPixelSizes(x, y, z));
        return Optional.empty();
    }

    private static JsonObject object(JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static JsonArray array(JsonElement element) {
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private static String string(JsonElement element) {
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }

    private static Double number(JsonElement element) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber())
            return null;
        return parseDouble(element.getAsString());
    }

    private static List<Double> numbers(JsonArray array) {
        List<Double> values = new ArrayList<>();
        if (array == null)
            return values;
        for (JsonElement element : array) {
            Double value = number(element);
            if (value != null)
                values.add(value);
        }
        return values;
    }

    private static Double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isPositiveFinite(Double value) {
        return value != null && Double.isFinite(value) && value > 0.0;
    }
}
