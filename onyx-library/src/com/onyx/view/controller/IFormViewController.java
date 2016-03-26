package com.onyx.view.controller;

import javafx.event.ActionEvent;

/**
 * Created by timothy.osborn on 9/11/14.
 */
public interface IFormViewController
{
    void setFormData();
    void getFormData();
    void updateModel();
    void setupModel();
    void save(ActionEvent event) throws Exception;
    boolean validateForm();
}
