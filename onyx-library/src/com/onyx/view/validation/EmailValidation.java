package com.onyx.view.validation;

import javafx.scene.control.TextField;

/**
 * Created by timothy.osborn on 9/12/14.
 */
public class EmailValidation extends AbstractValidation implements Validation
{
    @Override
    public boolean isValid()
    {
        TextField textField = (TextField) node;
        String text = textField.getText();
        if(text == null)
        {
            return true;
        }
        return text.matches("^([\\w\\-\\.]+)@((\\[([0-9]{1,3}\\.){3}[0-9]{1,3}\\])|(([\\w\\-]+\\.)+)([a-zA-Z]{2,4}))$");
    }
}
