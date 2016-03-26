package com.onyx.view.components;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

/**
 * Created by timothy.osborn on 9/6/14.
 */
public class RibbonToggleButton extends ToggleButton
{
    public RibbonToggleButton()
    {
        super();
        imagePathProperty();
    }

    public RibbonToggleButton(String text)
    {
        super(text);
        imagePathProperty();
    }

    public RibbonToggleButton(String text, Node graphic)
    {
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

    public void setImagePath(String value)
    {
        this.imagePath.setValue(value);

        final ImageView imageView = new ImageView();
        imageView.setFitHeight(30.0);
        imageView.setFitWidth(30.0);

        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setCache(true);

        final Image image = new Image(value);
        imageView.setImage(image);

        this.setGraphic(imageView);
        this.getGraphic().setLayoutY(0.0);
        setContentDisplay(ContentDisplay.TOP);

    }

    public String getImagePath()
    {
        return imagePath.getValue();
    }
}
