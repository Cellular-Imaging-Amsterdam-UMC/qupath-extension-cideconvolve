package qupath.ext.cideconvolve;

import javafx.application.Platform;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.StrokeLineCap;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import qupath.lib.common.GeneralTools;

import java.util.List;

final class CIDeconvolveIcons {

    private static List<Image> icons;

    private CIDeconvolveIcons() {
    }

    private static List<Image> icons() {
        if (icons == null)
            icons = List.of(draw(16), draw(32), draw(48), draw(64));
        return icons;
    }

    static ImageView iconView(double size) {
        var view = new ImageView(size <= 16 ? icons().getFirst() : icons().get(1));
        view.setFitWidth(size);
        view.setFitHeight(size);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        return view;
    }

    static void addStageIcon(Stage stage) {
        stage.getIcons().setAll(icons());
        if (GeneralTools.isWindows()) {
            Runnable updateCaption = () -> Platform.runLater(() -> {
                if (stage.isShowing())
                    WindowsCaptionIcon.apply(stage.getTitle(), icons().getFirst(), icons().get(1));
            });
            if (stage.isShowing())
                updateCaption.run();
            else
                stage.addEventHandler(WindowEvent.WINDOW_SHOWN, event -> updateCaption.run());
        }
    }

    private static Image draw(int size) {
        var canvas = new Canvas(size, size);
        var graphics = canvas.getGraphicsContext2D();
        graphics.scale(size / 48.0, size / 48.0);
        graphics.setFill(new LinearGradient(0, 0, 48, 48, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#237f6a")), new Stop(1, Color.web("#10483f"))));
        graphics.fillRoundRect(0, 0, 48, 48, 11, 11);

        // A diffuse spot resolves into a sharp point.
        graphics.setFill(Color.web("#7ed4af", 0.30));
        graphics.fillOval(5, 14, 19, 20);
        graphics.setFill(Color.web("#91deb8", 0.55));
        graphics.fillOval(8, 17, 13, 14);
        graphics.setFill(Color.web("#aef0cc"));
        graphics.fillOval(11, 20.5, 7, 7);

        graphics.setLineWidth(2.2);
        graphics.setLineCap(StrokeLineCap.ROUND);
        graphics.setStroke(Color.web("#91dfc0"));
        graphics.strokePolyline(new double[]{22, 28, 25}, new double[]{24, 24, 21}, 3);
        graphics.strokeLine(28, 24, 25, 27);
        graphics.setFill(Color.web("#deffec"));
        graphics.fillPolygon(new double[]{36, 38.3, 43, 38.3, 36, 33.7, 30, 33.7},
                new double[]{15, 21.7, 24, 26.3, 33, 26.3, 24, 21.7}, 8);

        var snapshot = new SnapshotParameters();
        snapshot.setFill(Color.TRANSPARENT);
        return canvas.snapshot(snapshot, null);
    }
}
