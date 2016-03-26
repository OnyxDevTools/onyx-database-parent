package com.onyx.view.validation;

import javafx.scene.control.TextField;

/**
 * Created by timothy.osborn on 9/12/14.
 */
public class PhoneNumberValidation extends AbstractValidation implements Validation
{
    @Override
    public boolean isValid()
    {
        TextField textField = (TextField) node;
        String text = textField.getText();
        if(text == null || text.equals("(   )   -    "))
        {
            return true;
        }
        return text.matches("\\(\\d\\d\\d\\)\\d\\d\\d-\\d\\d\\d\\d");
    }
}
