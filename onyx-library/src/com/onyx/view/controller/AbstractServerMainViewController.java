package com.onyx.view.controller;

import com.onyx.application.controller.ApplicationController;
import com.onyx.application.controller.impl.AbstractApplicationController;
import com.onyx.view.util.IconUtil;
import com.onyx.view.util.SpringFXMLLoader;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;

import java.io.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;

/**
 * Created by timothy.osborn on 9/4/14.
 */
@Controller
public abstract class AbstractServerMainViewController implements IViewController
{

    protected static long MEGABYTE = 100000;

    protected File logFile;

    protected BufferedReader logFileReader;

    public static final String ONYX_IS_RUNNING = "Onyx Server started ";
    public static final String ONYX_IS_NOT_RUNNING = "Onyx Server is not running!";
    protected static final int CHECK_STATUS_INTERVAL = 1;

    @Autowired
    protected ApplicationController serverController;

    @Autowired
    protected ExecutorService executorService;

    @FXML
    protected GridPane mainPane;

    @FXML
    protected ToggleButton btnOnOff;

    @FXML
    protected Button btnSettings;

    @FXML
    protected Label lblStatus;

    @FXML
    protected ProgressIndicator progressIndicator;

    @FXML
    protected ImageView statusIcon;

    @FXML
    protected TextArea logTextArea;

    @Value("${logging.file}")
    protected String logFilePath;

    /**
     * Click Settings Button and instantiate settings dialog
     *
     * @param event
     * @throws IOException
     */
    @FXML
    protected void clickSettings(ActionEvent event) throws IOException
    {
        Stage stage = new Stage();

        final SpringFXMLLoader loader = new SpringFXMLLoader();
        Parent root = (Parent)loader.load("view/Settings.fxml");

        stage.setScene(new Scene(root));
        stage.setTitle("Server Settings");
        stage.initModality(Modality.NONE);
        stage.initOwner(((Node)event.getSource()).getScene().getWindow() );
        stage.show();

        IViewController controller = (IViewController) loader.getController();
        controller.init();

    }

    /**
     * Toggle On Off
     *
     * @param event
     */
    @FXML
    protected void toggleOnOff(ActionEvent event)
    {
        btnOnOff.setDisable(true);
        progressIndicator.setVisible(true);
        progressIndicator.setProgress(-1.0f);
        statusIcon.setVisible(false);

        if(btnOnOff.isSelected())
        {
            serverController.start();
        }
        else
        {
            serverController.stop();
        }
    }

    /**
     * Initialize, do not use standard initialize interface due to spring autowiring not being available
     *
     */
    public void init() {

        logFile = new File(logFilePath);

        btnOnOff.setDisable(true);

        try {
            FileReader reader = new FileReader(logFile);
            logFileReader = new BufferedReader(reader);

            //  Skip to last meg of log
            if(logFile.length() > MEGABYTE)
            {
                logFileReader.skip(logFile.length() - MEGABYTE);
            }
        } catch (FileNotFoundException e) {
        }
        catch (IOException e)
        {
        }

        final Timeline timer = new Timeline(new KeyFrame(Duration.seconds(CHECK_STATUS_INTERVAL), new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event)
            {
                Properties properties = serverController.getProperties(serverController.getRuntimePropertyLocation());

                if (serverController != null && serverController.isRunning()) {
                    if (btnOnOff.isDisabled() && btnOnOff.isSelected())
                    {
                        progressIndicator.setVisible(false);
                        btnOnOff.setDisable(false);
                        lblStatus.setText(ONYX_IS_RUNNING + new Date(Long.valueOf(properties.getProperty(AbstractApplicationController.ONYX_SERVER_START_TIME))));
                        statusIcon.setVisible(true);
                        statusIcon.setImage(new Image(IconUtil.TICK));
                    }
                    else if(!btnOnOff.isDisabled() && serverController.isRunning())
                    {
                        lblStatus.setText(ONYX_IS_RUNNING + new Date(Long.valueOf(properties.getProperty(AbstractApplicationController.ONYX_SERVER_START_TIME))));
                        statusIcon.setVisible(true);
                        statusIcon.setImage(new Image(IconUtil.TICK));
                        btnOnOff.setSelected(true);
                    }
                } else if (serverController != null && !serverController.isRunning()) {
                    if (btnOnOff.isDisabled() && !btnOnOff.isSelected()) {
                        progressIndicator.setVisible(false);
                        btnOnOff.setDisable(false);
                        lblStatus.setText(ONYX_IS_NOT_RUNNING);
                        statusIcon.setVisible(true);
                        statusIcon.setImage(new Image(IconUtil.WARNING));
                    }
                    else if(!btnOnOff.isDisabled() && !serverController.isRunning())
                    {
                        lblStatus.setText(ONYX_IS_NOT_RUNNING);
                        statusIcon.setVisible(true);
                        statusIcon.setImage(new Image(IconUtil.WARNING));
                        btnOnOff.setSelected(false);
                    }
                }

                logTextArea.appendText(read(logFile));
            }
        }));

        timer.setCycleCount(Timeline.INDEFINITE);
        timer.play();

    }

    /**
     * Read LogFile line by line
     *
     * @param file
     * @return
     */
    private String read(File file)
    {
        if(logFileReader == null)
        {
            return "";
        }
        final List<String> lines = new ArrayList<String>();
        String line;
        try {

            while ((line = logFileReader.readLine()) != null)
            {
                lines.add(line);
            }

        } catch (IOException ex)
        {
        }

        final StringBuilder sb = new StringBuilder();
        for (String s : lines)
        {
            sb.append(s);
            sb.append("\n");
        }

        return sb.toString();
    }
}
