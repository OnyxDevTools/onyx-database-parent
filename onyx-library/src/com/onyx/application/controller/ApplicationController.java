package com.onyx.application.controller;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * Created by timothy.osborn on 9/5/14.
 */

public interface ApplicationController
{
    boolean start();
    boolean stop();
    boolean isRunning();

    ApplicationContext getApplicationContext();

    Runnable startKillHookThread();

    Properties getProperties(String location);

    void saveProperties(Properties properties, String location);

    String getRuntimePropertyLocation();

    String getCredentials();
    String getRequestURL();

    void showLoginPrompt(Consumer<Boolean> loggedIn);

    Properties getProperties(InputStream inputStream);
}
