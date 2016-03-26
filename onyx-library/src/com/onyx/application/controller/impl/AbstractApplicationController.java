package com.onyx.application.controller.impl;

import com.onyx.application.controller.ApplicationController;
import com.onyx.view.util.SpringFXMLLoader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.client.RestTemplate;

import java.util.Date;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by timothy.osborn on 9/5/14.
 */

public abstract class AbstractApplicationController extends ApplicationControllerBase
{

    protected static com.onyx.application.controller.ApplicationController instance;

    @Value("${request.endpoint}")
    protected String requestEndpoint;

    public static final String PING = "/ping";
    public static final String ONYX_SERVER_RUN = "onyx.server.running";
    public static final String ONYX_SERVER_START_TIME = "onyx.server.start";
    public static final String RUNTIME_PROPERTY_FILE = "/onyx/server/runtime.properties";

    private static final int SLEEP_INTERVAL = 4000;
    public static final String NOT_RUNNING = "false";
    public static final String RUNNING = "true";

    public static ConfigurableApplicationContext applicationContext;
    public static Class alternativeAnnotationConfig;

    protected String runtimePropertyLocation;

    public String getRuntimePropertyLocation()
    {
        return runtimePropertyLocation;
    }

    public AbstractApplicationController()
    {
        runtimePropertyLocation = System.getProperty("user.home") + RUNTIME_PROPERTY_FILE;
    }

    /**
     * Localhost Endpoint
     */
    public static String serverEndpoint;

    @Value("${local.endpoint}")
    public void setServerEndpoint(String value)
    {
        serverEndpoint = value;
    }

    public static String getURL()
    {
        return serverEndpoint + PING;
    }

    public static ConfigurableApplicationContext initializeSpringContext(Class _alternativeAnnotationConfig)
    {
        alternativeAnnotationConfig = _alternativeAnnotationConfig;

        try
        {
            // If the port is already taken build the java fx application context
            if(applicationContext == null)
            {
                applicationContext = new AnnotationConfigApplicationContext(alternativeAnnotationConfig);
                instance = applicationContext.getBean(com.onyx.application.controller.ApplicationController.class);
                new SpringFXMLLoader(applicationContext);
            }
        }
        catch (Exception e)
        {
            // If the port is already taken build the java fx application context
            if(applicationContext == null)
            {
                applicationContext = new AnnotationConfigApplicationContext(alternativeAnnotationConfig);
                instance = applicationContext.getBean(com.onyx.application.controller.ApplicationController.class);
            }
        }

        new SpringFXMLLoader(applicationContext);

        // Instance should not be null after initializing the spring context

        return applicationContext;
    }

    /**
     * Static getter for instance
     * @return
     */
    public static ApplicationController getInstance()
    {
        return instance;
    }

    public static void setInstance(ApplicationController controller)
    {
        instance = controller;
    }

    /**
     * Is Running, meaning the ping is working
     *s
     * @return
     */
    public boolean isRunning()
    {
        Boolean response = false;
        try
        {
            final RestTemplate template = applicationContext.getBean(RestTemplate.class);
            response = template.getForObject(getURL(), Boolean.class);
        }
        catch (Exception e){
        }
        return response;
    }

    /**
     * Kill Bootstrapped Server
     */
    public boolean stop()
    {
        final Properties properties = getProperties(getRuntimePropertyLocation());
        properties.setProperty(ONYX_SERVER_RUN, "false");
        saveProperties(properties, getRuntimePropertyLocation());
        return (!isRunning());
    }

    public ApplicationContext getApplicationContext()
    {
        return applicationContext;
    }

    /**
     * Start Thread that sweeps around and ensures that the app quits when the runtime properties change
     * @return
     */
    public Runnable startKillHookThread()
    {
        final Runnable killThread = () -> {
            while (true)
            {
                final Properties runtimeProperties = getProperties(getRuntimePropertyLocation());
                String value = runtimeProperties.getProperty(ONYX_SERVER_RUN);

                if (isRunning() && NOT_RUNNING.equals(value))
                {
                    applicationContext.close();
                    applicationContext = null;
                    initializeSpringContext(alternativeAnnotationConfig);
                } else {

                    try {
                        Thread.sleep(SLEEP_INTERVAL);
                    } catch (InterruptedException e) {
                    }
                }
            }
        };

        final ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(killThread);

        return killThread;
    }

    public String getRequestURL() {
        return requestEndpoint;
    }

}
