package com.onyx.application.controller.impl;

import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.*;
import java.util.Properties;

/**
 * Created by timothy.osborn on 9/19/14.
 */
public abstract class ApplicationControllerBase
{
    protected static Stage initialScene;

    /**
     * Set the initial JavaFX Scene
     * @param scene
     */
    public static void setInitialStage(Stage scene)
    {
        initialScene = scene;
    }

    /**
     * getter for initial Scene
     * @return
     */

    public static Stage getInitialStage()
    {
        return initialScene;
    }

    public Properties getProperties(InputStream inputStream)
    {
        final Properties properties = new Properties();

        try
        {
            properties.load( inputStream );
        }
        catch ( Exception e ) { }
        finally {
            if(inputStream != null)
            {
                try {
                    inputStream.close();
                } catch (IOException e) {}
                inputStream = null;
            }
        }

        return properties;
    }
    /**
     * Get Runtime Properties manually from file since we cannot edit System.properties right now

     * @return
     */
    public Properties getProperties(String location)
    {
        InputStream inputStream = null;

        final File resourceFile = new File(location);
        try {
            inputStream = new FileInputStream(resourceFile);
        } catch (FileNotFoundException e) { }
        return this.getProperties(inputStream);
    }

    public void saveProperties(Properties properties, OutputStream outputStream)
    {
        try {
            properties.store(outputStream, "");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Save Runtime Properties
     *
     * @param properties
     */
    public void saveProperties(Properties properties, String location)
    {
        OutputStream outputStream = null;
        try
        {
            final File resourceFile = new File(location);
            if(!resourceFile.exists())
            {
                resourceFile.getParentFile().mkdirs();
                resourceFile.createNewFile();
            }
            outputStream = new FileOutputStream(resourceFile);
            properties.store(outputStream, "");
        }
        catch (Exception e )
        {
        }
        finally {
            if(outputStream != null)
            {
                try {
                    outputStream.flush();
                    outputStream.close();
                } catch (IOException e)
                {
                }
            }
        }
    }
}
