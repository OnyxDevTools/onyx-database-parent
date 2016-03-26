package com.onyx.view.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.io.InputStream;

public class SpringFXMLLoader
{

    private static ApplicationContext applicationContext;

    public static ApplicationContext getApplicationContext()
    {
        return applicationContext;
    }

    private Object controller;

    public Object getController()
    {
        return controller;
    }

    public SpringFXMLLoader(ApplicationContext context)
    {
        applicationContext = context;
    }

    public SpringFXMLLoader()
    {

    }

    public Object load(String url)
    {
        InputStream fxmlStream = null;
        try
        {
            fxmlStream = this.getClass().getClassLoader().getResourceAsStream(url);
            final FXMLLoader loader = new FXMLLoader();
            Object parent = null;
            try {
                parent = loader.load(fxmlStream);

                if(parent instanceof Throwable)
                {
                    throw((Throwable) parent);
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
            controller = loader.getController();
            applicationContext.getAutowireCapableBeanFactory().autowireBean(controller);
            return parent;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        finally
        {
            if (fxmlStream != null)
            {
                try {
                    fxmlStream.close();
                } catch (IOException e) {
                }
            }
        }
        return null;
    }


}