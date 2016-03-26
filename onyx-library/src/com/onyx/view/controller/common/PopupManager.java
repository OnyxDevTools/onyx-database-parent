package com.onyx.view.controller.common;

import com.onyx.application.controller.impl.ApplicationControllerBase;
import com.onyx.view.util.SpringFXMLLoader;
import com.onyx.view.controller.IViewController;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Created by timothy.osborn on 9/11/14.
 */
public class PopupManager
{
    public static IViewController showPopup(String title, String view)
    {
        return showPopup(title, view, ApplicationControllerBase.getInitialStage(), Modality.NONE);
    }

    public static IViewController showPopup(String title, String view, Modality modality)
    {
        return showPopup(title, view, ApplicationControllerBase.getInitialStage(), modality);
    }

    public static IViewController showPopup(String title, String view, Window owner)
    {
        return showPopup(title, view, owner, Modality.NONE);
    }

    /**
     * Method used to show a popup
     *
     * @param title
     * @param view
     * @param owner
     * @param modality
     * @return
     */
    public static IViewController showPopup(String title, String view, Window owner, Modality modality)
    {
        Stage stage = new Stage();

        final SpringFXMLLoader loader = new SpringFXMLLoader();
        Parent root = (Parent) loader.load(view);

        stage.setScene(new Scene(root));
        stage.setTitle(title);
        if(modality != Modality.APPLICATION_MODAL) {
            stage.initModality(modality);
        }
        if(owner != null) {
            stage.initOwner(owner);
        }
        if(modality == Modality.APPLICATION_MODAL)
        {
            //stage.initStyle(StageStyle.UTILITY);
            //stage.initStyle(StageStyle.TRANSPARENT);
        }
        else
        {
            stage.initModality(modality);
        }

        stage.show();

        return (IViewController) loader.getController();
    }

    public static IViewController showFixedPopup(String title, String view)
    {
        return showFixedPopup(title, view, null, Modality.NONE);
    }

    public static IViewController showFixedPopup(String title, String view, Modality modality)
    {
        return showFixedPopup(title, view, null, modality);
    }


    public static IViewController showFixedPopup(String title, String view, Window owner)
    {
        return showFixedPopup(title, view, owner, Modality.NONE);
    }

    /**
     * Method for displaying a fixed size popup.
     *
     * @param title
     * @param view
     * @param owner
     * @param modality
     * @return
     */
    public static IViewController showFixedPopup(String title, String view, Window owner, Modality modality)
    {
        Stage stage = new Stage();

        final SpringFXMLLoader loader = new SpringFXMLLoader();
        Parent root = (Parent) loader.load(view);

        stage.setScene(new Scene(root));
        stage.setTitle(title);
        if(modality != Modality.APPLICATION_MODAL) {
            stage.initModality(modality);
        }
        if(owner != null) {
            stage.initOwner(owner);
        }
        if(modality == Modality.APPLICATION_MODAL)
        {
            //stage.initStyle(StageStyle.UTILITY);
            //stage.initStyle(StageStyle.TRANSPARENT);
        }
        else
        {
            stage.initModality(modality);
        }

        stage.setResizable(false);

        stage.show();

        return (IViewController) loader.getController();
    }

}
