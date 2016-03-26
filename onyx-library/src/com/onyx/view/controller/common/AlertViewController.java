package com.onyx.view.controller.common;

import com.onyx.view.controller.IViewController;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextBuilder;
import javafx.stage.Stage;

import java.util.function.Consumer;

/**
 * Created by timothy.osborn on 9/8/14.
 */
public class AlertViewController implements IViewController
{

    @FXML
    private GridPane mainGrid;

    @FXML
    private Button btnOk;

    @FXML
    private Button btnDiscard;

    @FXML
    private Label lblWarning;

    @FXML
    private ImageView iconView;

    @FXML
    private Button btnCancel;

    @FXML
    private HBox buttonLayout;

    @FXML
    private HBox messageLayout;

    private String okButtonText = null;

    private String cancelButtonText = null;

    public String getOkButtonText()
    {
        return okButtonText;
    }

    public void setOkButtonText(String okButtonText)
    {
        this.okButtonText = okButtonText;
    }

    public String getCancelButtonText()
    {
        return cancelButtonText;
    }

    public void setCancelButtonText(String cancelButtonText)
    {
        this.cancelButtonText = cancelButtonText;
    }

    /**
     * Responders
     */
    private Consumer<Boolean> onCancel;

    public void setOnCancel(Consumer<Boolean> onCancel) {
        this.onCancel = onCancel;
    }

    private Consumer<Boolean> onOk;

    public void setOnOk(Consumer<Boolean> onOk) {
        this.onOk = onOk;
    }

    /**
     * Hide OK Button
     */
    public void hideOkButton()
    {
        buttonLayout.getChildren().remove(btnOk);
    }

    /**
     * Hide Discard Button
     */
    public void hideDiscardButton(){
        buttonLayout.getChildren().remove(btnDiscard);
    }

    /**
     * Hide OK Button
     */
    public void hideCancelButton()
    {
        buttonLayout.getChildren().remove(btnCancel);
    }

    /**
     * Set Icon
     *
     * @param imagePath
     */
    public void setIcon(String imagePath)
    {
        Image image = new Image(imagePath);
        iconView.setImage(image);
    }

    /**
     * Hide Icon
     *
     */
    public void hideIcon()
    {
        messageLayout.getChildren().remove(iconView);
    }
    /**
     * Set Warning Label
     *
     * @param warningLabel
     */
    public void setWarningLabel(String warningLabel)
    {
        lblWarning.setText(warningLabel);
    }

    /**
     * Click OK
     *
     * @param event
     */
    @FXML
    protected void clickOk(ActionEvent event)
    {
        if(onOk != null)
        {
            onOk.accept(true);
        }
        closeDialog();
    }

    /**
     * Click Cancel
     *
     * @param event
     */
    @FXML
    protected void clickDiscard(ActionEvent event)
    {
        if(onCancel != null) {
            onCancel.accept(true);
        }
        closeDialog();
    }

    /**
     * Click Cancel
     *
     * @param event
     */
    @FXML
    protected void clickCancel(ActionEvent event)
    {
        closeDialog();
    }

    /**
     * Close Dialog
     */
    public void closeDialog()
    {
        Stage stage = (Stage) lblWarning.getScene().getWindow();
        stage.close();
    }

    @Override
    public void init()
    {
        float width = com.sun.javafx.tk.Toolkit.getToolkit().getFontLoader().computeStringWidth(lblWarning.getText(), lblWarning.getFont());
        lblWarning.setPrefWidth(width);
        lblWarning.setMinWidth(width);
        mainGrid.setPrefWidth(width + 80);
        mainGrid.setMinWidth(width + 80);
        mainGrid.getScene().getWindow().setWidth(mainGrid.getPrefWidth());
        mainGrid.getScene().getWindow().centerOnScreen();

        if(okButtonText != null)
        {
            btnOk.setText(okButtonText);
        }

        if(cancelButtonText != null)
        {
            btnDiscard.setText(cancelButtonText);
        }
    }
}
