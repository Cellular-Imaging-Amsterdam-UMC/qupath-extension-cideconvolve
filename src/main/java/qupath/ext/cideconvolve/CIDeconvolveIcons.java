package qupath.ext.cideconvolve;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;

import java.io.IOException;

final class CIDeconvolveIcons {

    private static final String ICON_RESOURCE = "/qupath/ext/cideconvolve/icon.png";
    private static Image icon;

    private CIDeconvolveIcons() {
    }

    static Image icon() {
        if (icon != null)
            return icon;
        try (var stream = CIDeconvolveIcons.class.getResourceAsStream(ICON_RESOURCE)) {
            if (stream != null)
                icon = new Image(stream);
        } catch (IOException ignored) {
        }
        return icon;
    }

    static ImageView iconView(double size) {
        Image image = icon();
        if (image == null)
            return null;
        ImageView view = new ImageView(image);
        view.setFitWidth(size);
        view.setFitHeight(size);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        return view;
    }

    static void addStageIcon(Stage stage) {
        Image image = icon();
        if (image != null && stage != null)
            stage.getIcons().add(image);
    }
}
