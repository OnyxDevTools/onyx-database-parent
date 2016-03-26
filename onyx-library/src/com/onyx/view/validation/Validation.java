package com.onyx.view.validation;

import javafx.scene.control.Control;

/**
 * Created by timothy.osborn on 9/12/14.
 */
public interface Validation
{
    public void setNode(Control control);
    public void setErrorMessage(String message);
    boolean isValid();
    boolean invalidate(boolean setFocus);
    void setInvalidWithMessage(String message);
}
