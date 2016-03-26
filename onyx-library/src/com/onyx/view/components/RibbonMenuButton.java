package com.onyx.view.components;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.MenuButton;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;

/**
 * Created by timothy.osborn on 9/6/14.
 */
public class RibbonMenuButton extends Button {

    public RibbonMenuButton() {
        super();
        imagePathProperty();
    }

    public RibbonMenuButton(String text) {
        super(text);
        imagePathProperty();
    }

    public RibbonMenuButton(String text, Node graphic) {
        super(text, graphic);
        imagePathProperty();
    }


    private SimpleStringProperty imagePath;

    /**
     * The text to display in the label. The text may be null.
     */
    public final StringProperty imagePathProperty() {
        if (imagePath == null) {
            imagePath = new SimpleStringProperty(this, "imagePath", "");
        }
        return imagePath;
    }

    public void setImagePath(String value) {
        this.imagePath.setValue(value);

        final ImageView imageView = new ImageView();
        imageView.setFitHeight(30.0);
        imageView.setFitWidth(30.0);

        imageView.setPreserveRatio(false);
        imageView.setSmooth(true);
        imageView.setCache(true);

        final Image image = new Image(value);
        imageView.setImage(image);

        this.setGraphic(imageView);
        this.getGraphic().setLayoutY(0.0);
        setContentDisplay(ContentDisplay.TOP);

    }

    public String getImagePath() {
        return imagePath.getValue();
    }

    /**
     * Static usage for Ribbon Munu Button
     *
     * @param button
     * @param icon
     */
    public static void addIconToButton(ButtonBase button, String icon) {

        button.setContentDisplay(ContentDisplay.TOP);
        button.setMnemonicParsing(true);

        final ImageView imageView = new ImageView();
        imageView.setFitHeight(30.0);
        imageView.setFitWidth(30.0);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setCache(true);

        final Image image = new Image(icon);
        imageView.setImage(image);

        if (button instanceof MenuButton)
        {
            final VBox vBox = new VBox();
            vBox.setAlignment(Pos.TOP_RIGHT);
            vBox.setPrefWidth(40);
            vBox.setPrefHeight(30);
            vBox.getChildren().add(imageView);

            button.setGraphic(vBox);

        } else
        {
            button.setGraphic(imageView);
        }
    }
}
