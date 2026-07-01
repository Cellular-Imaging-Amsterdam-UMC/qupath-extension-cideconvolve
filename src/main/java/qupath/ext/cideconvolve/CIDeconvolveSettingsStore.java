package qupath.ext.cideconvolve;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

final class CIDeconvolveSettingsStore {

    static final String SETTINGS_DIRECTORY_NAME = "cideconsettings";
    static final String LAST_SETTINGS_NAME = "_last_settings.json";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private CIDeconvolveSettingsStore() {
    }

    static Path lastSettingsPath(Path settingsDirectory) {
        return settingsDirectory.resolve(LAST_SETTINGS_NAME);
    }

    static void save(Path path, CIDeconvolveParameters parameters) throws IOException {
        Files.createDirectories(path.getParent());
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(parameters, writer);
        }
    }

    static CIDeconvolveParameters load(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            CIDeconvolveParameters parameters = GSON.fromJson(reader, CIDeconvolveParameters.class);
            if (parameters == null)
                throw new IOException("Settings file is empty: " + path);
            parameters.normalize();
            return parameters;
        } catch (JsonSyntaxException e) {
            throw new IOException("Settings file is not valid JSON: " + path, e);
        }
    }

    static List<Path> listSavedSettings(Path settingsDirectory) {
        if (settingsDirectory == null || !Files.isDirectory(settingsDirectory))
            return List.of();
        try (Stream<Path> paths = Files.list(settingsDirectory)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName() != null)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".json"))
                    .filter(path -> !LAST_SETTINGS_NAME.equalsIgnoreCase(path.getFileName().toString()))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }
}
