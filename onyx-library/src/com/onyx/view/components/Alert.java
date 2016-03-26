package com.onyx.view.components;

import com.onyx.view.util.IconUtil;
import com.onyx.view.util.SpringFXMLLoader;
import com.onyx.view.controller.common.AlertViewController;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.function.Consumer;

/**
 * Created by timothy.osborn on 9/8/14.
 */
public class Alert
{

    protected static final String VIEW_RESOURCE = "view/Alert.fxml";

    public static void show(String title, String message, Window parent, Consumer onOk) {
        Stage stage = new Stage();

        final SpringFXMLLoader loader = new SpringFXMLLoader();
        Parent root = (Parent)loader.load(VIEW_RESOURCE);

        stage.setScene(new Scene(root));
        stage.setTitle(title);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initOwner(parent);
        stage.show();

        final AlertViewController controller = (AlertViewController) loader.getController();
        controller.hideCancelButton();
        controller.setWarningLabel(message);
        controller.hideIcon();
        controller.setOnOk(onOk);
        controller.init();
    }

    public static void show(String title, String message, Window parent, Consumer onOk, String okButtonText, String cancelButtonText) {
        Stage stage = new Stage();

        final SpringFXMLLoader loader = new SpringFXMLLoader();
        Parent root = (Parent)loader.load(VIEW_RESOURCE);

        stage.setScene(new Scene(root));
        stage.setTitle(title);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initOwner(parent);
        stage.show();

        final AlertViewController controller = (AlertViewController) loader.getController();
        controller.hideCancelButton();
        controller.setWarningLabel(message);
        controller.hideIcon();
        controller.setOnOk(onOk);
        controller.setOkButtonText(okButtonText);
        controller.setCancelButtonText(cancelButtonText);
        controller.init();
    }

    public static void showWarning(String title, String message, Window parent, Consumer onOk) {
        Stage stage = new Stage();

        final SpringFXMLLoader loader = new SpringFXMLLoader();
        Parent root = (Parent)loader.load("view/Alert.fxml");

        stage.setScene(new Scene(root));
        stage.setTitle(title);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initOwner(parent);
        stage.show();

        AlertViewController controller = (AlertViewController) loader.getController();
        controller.hideCancelButton();
        controller.setWarningLabel(message);
        controller.setIcon(IconUtil.WARNING);
        controller.setOnOk(onOk);
        controller.init();
    }

    public static void showDeleteConfirmation(String objectName, String objectIdentifier, Window parent, Consumer<Boolean> onOk, Consumer<Boolean> onCancel){
        Stage stage = new Stage();

        final SpringFXMLLoader loader = new SpringFXMLLoader();
        Parent root = (Parent)loader.load(VIEW_RESOURCE);

        stage.setScene(new Scene(root));
        stage.setTitle("Confirm Delete");
        stage.initModality(Modality.WINDOW_MODAL);
        if(parent != null) {
            stage.initOwner(parent);
        }
        stage.show();

        AlertViewController controller = (AlertViewController) loader.getController();
        controller.setWarningLabel("Are you sure you want to delete " + objectName + ": " + objectIdentifier + "?");
        controller.hideIcon();
        controller.setOkButtonText("Delete");
        controller.setOnOk(onOk);
        controller.hideDiscardButton();
        controller.setOnCancel(onCancel);
        controller.init();
    }

    public static void showWithCancel(String title, String message, Window parent, Consumer<Boolean> onOk, Consumer<Boolean> onCancel) {
        Stage stage = new Stage();

        final SpringFXMLLoader loader = new SpringFXMLLoader();
        Parent root = (Parent)loader.load(VIEW_RESOURCE);

        stage.setScene(new Scene(root));
        stage.setTitle(title);
        stage.initModality(Modality.WINDOW_MODAL);
        if(parent != null) {
            stage.initOwner(parent);
        }
        stage.show();

        AlertViewController controller = (AlertViewController) loader.getController();
        controller.setWarningLabel(message);
        controller.hideIcon();
        controller.setOnOk(onOk);
        controller.setOnCancel(onCancel);
        controller.init();
    }

    public static void showWarningWithCancel(String title, String message, Window parent, Consumer<Boolean> onOk, Consumer<Boolean> onCancel) {
        Stage stage = new Stage();

        final SpringFXMLLoader loader = new SpringFXMLLoader();
        Parent root = (Parent)loader.load("view/Alert.fxml");

        stage.setScene(new Scene(root));
        stage.setTitle(title);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initOwner(parent);
        stage.show();

        AlertViewController controller = (AlertViewController) loader.getController();
        controller.setWarningLabel(message);
        controller.setIcon(IconUtil.WARNING);
        controller.setOnOk(onOk);
        controller.setOnCancel(onCancel);
        controller.init();
    }
}
