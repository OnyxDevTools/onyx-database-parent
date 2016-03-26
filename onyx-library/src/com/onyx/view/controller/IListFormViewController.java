package com.onyx.view.controller;

import javafx.event.ActionEvent;

/**
 * Created by timothy.osborn on 9/13/14.
 */
public interface IListFormViewController
{
    void add(ActionEvent event);
    void edit(ActionEvent event);
    void delete(ActionEvent event);
    void close(ActionEvent event);
    void fillView(Boolean active);
}
