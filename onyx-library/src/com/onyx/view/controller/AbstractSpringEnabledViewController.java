package com.onyx.view.controller;

import com.onyx.view.util.SpringFXMLLoader;
import javafx.scene.control.DatePicker;

import java.util.Date;

/**
 * Created by timothy.osborn on 9/23/14.
 */
public abstract class AbstractSpringEnabledViewController
{
    public void initialize()
    {
        SpringFXMLLoader.getApplicationContext().getAutowireCapableBeanFactory().autowireBean(this);
    }

    /**
     * Get Date Value from date picker
     *
     * @param datePicker
     * @return
     */
    protected Date getDateValue(DatePicker datePicker)
    {
        if(datePicker.getValue() != null) {
            return new Date(java.sql.Date.valueOf(datePicker.getValue()).getTime());
        }
        else
        {
            return null;
        }
    }
}
