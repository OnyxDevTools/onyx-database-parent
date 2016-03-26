package com.onyx.view.validation;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.Control;
import javafx.scene.control.TextField;

import java.util.List;
import java.util.function.Consumer;

public class ValidationUtils {

    public ValidationUtils() {

    }

    public static Validation registerOnDemandValidator(Control control, Validation validator, String errorMessage)
    {
        validator.setErrorMessage(errorMessage);
        validator.setNode(control);
        return validator;
    }

    public static Validation registerOnDemandValidatorWithCallBack(Control control, CallbackValidation validator, String errorMessage, Consumer<Boolean> callback)
    {

        validator.setErrorMessage(errorMessage);
        validator.setNode(control);
        validator.setResponseCallback(callback);
        return validator;
    }

    public static Validation registerChangeValidator(TextField control, Validation validator, String errorMessage)
    {
        validator.setErrorMessage(errorMessage);
        validator.setNode(control);

        control.textProperty().addListener(new ChangeListener<String>()
        {
            @Override
            public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
                validator.invalidate(true);
            }
        });

        return validator;
    }

    public static Validation registerFocusValidator(Control control, Validation validator, String errorMessage)
    {
        validator.setErrorMessage(errorMessage);
        validator.setNode(control);

        control.focusedProperty().addListener(new ChangeListener<Boolean>()
        {
            @Override
            public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue)
            {
                validator.invalidate(newValue);
            }
        });

        return validator;
    }

    public static Validation registerFocusValidatorWithCallback(Control control, CallbackValidation validator, String errorMessage, Consumer<Boolean> responseCallBack)
    {
        validator.setErrorMessage(errorMessage);
        validator.setNode(control);

        control.focusedProperty().addListener(new ChangeListener<Boolean>()
        {
            @Override
            public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue)
            {
                validator.isValid(responseCallBack);
            }
        });

        return validator;
    }

    public static boolean validateAll(Validation[] validators)
    {
        boolean pass = true;

        for(Validation validator : validators)
        {
            if(validator.invalidate(false) == false)
            {
                pass = false;
            }
        }

        return pass;
    }

    public static boolean validateAll(List<Validation> validators)
    {
        boolean pass = true;

        for(Validation validator : validators)
        {
            if(validator.invalidate(false) == false)
            {
                pass = false;
            }
        }

        return pass;
    }

    public static boolean validateAll(List<Validation>... validatorLists)
    {
        boolean pass = true;

        for(List<Validation> validatorList : validatorLists) {
            for (Validation validator : validatorList) {
                if (validator.invalidate(false) == false) {
                    pass = false;
                }
            }
        }
        return pass;
    }
}
