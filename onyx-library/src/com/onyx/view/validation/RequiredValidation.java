package com.onyx.view.validation;

import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;

/**
 * Created by timothy.osborn on 9/12/14.
 */
public class RequiredValidation extends AbstractValidation implements Validation
{
    @Override
    public boolean isValid()
    {
        String text = null;

        if(node instanceof TextField) {
            TextField textField = (TextField) node;
            text = textField.getText();
        }
        else if(node instanceof ComboBox)
        {
            ComboBox comboBox = (ComboBox) node;

            if(comboBox.isEditable())
            {
                return !(comboBox.getEditor().getText() == null || comboBox.getEditor().getText().length() == 0);
            }
            return !(comboBox.getSelectionModel().getSelectedItem() == null);
        }
        if(text == null || text.length() == 0)
        {
            return false;
        }
        return true;
    }
}
