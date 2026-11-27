package qupath.ext.cideconvolve;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

final class CIDeconvolveWindowStyle {

    private CIDeconvolveWindowStyle() {
    }

    static void apply(Parent root) {
        root.getStyleClass().add("ci-deconvolve");
        root.getStylesheets().add(CIDeconvolveWindowStyle.class.getResource("workspace.css").toExternalForm());
    }

    static HBox header(String title, String subtitle) {
        var heading = new Label(title);
        heading.getStyleClass().add("deconvolve-title");
        var context = new Label(subtitle);
        context.getStyleClass().add("deconvolve-context");
        context.setWrapText(true);
        var text = new VBox(4, heading, context);
        HBox.setHgrow(text, Priority.ALWAYS);
        var header = new HBox(10, CIDeconvolveIcons.iconView(32), text);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("deconvolve-header");
        return header;
    }

    static VBox card(String title, Node... fields) {
        var heading = new Label(title);
        heading.getStyleClass().add("deconvolve-card-heading");
        var card = new VBox(7, heading);
        card.getChildren().addAll(fields);
        card.getStyleClass().add("deconvolve-card");
        return card;
    }
}
