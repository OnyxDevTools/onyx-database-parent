package com.onyx.view.validation;

import javafx.scene.control.*;

/**
 * Created by timothy.osborn on 9/12/14.
 */
public abstract class AbstractValidation implements Validation
{

    protected String errorMessage;

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    protected Control node;

    public void setNode(Control control)
    {
        node = control;
    }

    public Control getNode() {
        return node;
    }

    /**
     * In-validate
     *
     * @param setFocus
     * @return
     */
    public boolean invalidate(boolean setFocus)
    {
        boolean pass = this.isValid();

        if(!pass)
        {
            this.setInvalidWithMessage(errorMessage);
        }
        else
        {
            if(node instanceof TextField) {
                node.getStyleClass().remove(0, node.getStyleClass().size());
                node.getStyleClass().add("text-field");
                node.getStyleClass().add("text-input");
                node.setTooltip(null);
            }
            else if(node instanceof ComboBox)
            {
                node.getStyleClass().remove(0, node.getStyleClass().size());
                node.getStyleClass().add("combo-box-base");
                node.getStyleClass().add("combo-box");
                node.setTooltip(null);
            }
            else if(node instanceof DatePicker)
            {
                node.getStyleClass().add("date-picker");
            }
        }
        return pass;
    }

    public void setInvalidWithMessage(String message)
    {
        if(node instanceof TextField) {
            node.getStyleClass().add("text-field-error");
        }
        else if(node instanceof ComboBox)
        {
            node.getStyleClass().add("combo-box-base-error");
        }
        else if(node instanceof DatePicker)
        {
            node.getStyleClass().add("date-picker-error");
        }
        final Tooltip tooltip = new Tooltip();
        tooltip.setText(message);
        node.setTooltip(tooltip);
        tooltip.setAutoHide(true);
    }
}
