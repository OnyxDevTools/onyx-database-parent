package com.onyx.view.controller.common;

import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.util.FormUtil;
import com.onyx.view.components.Alert;
import com.onyx.view.controller.IFormViewController;
import com.onyx.view.validation.Validation;
import com.onyx.view.validation.ValidationUtils;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Created by timothy.osborn on 9/11/14.
 */
public abstract class AbstractEditFormViewController implements IFormViewController {
    @FXML
    protected Button btnSave;

    @FXML
    protected Button btnClose;

    protected List<Validation> customValidators = new ArrayList<Validation>();

    protected List<Validation> requiredValidators = new ArrayList<Validation>();

    @Autowired
    protected PersistenceManager persistenceManager;

    /**
     * Init
     */
    public void init() {

        btnSave.getScene().addEventHandler(KeyEvent.KEY_PRESSED, new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent t) {
                if (t.getCode() == KeyCode.ESCAPE) {
                    close(null);
                } else if (t.getCode() == KeyCode.S && (t.isControlDown() || t.isShortcutDown())) {
                    save(null);
                }
            }
        });

        setupModel();
        setFormData();
        btnSave.setDisable(true);
    }

    @FXML
    public void save(ActionEvent event) {
        Platform.runLater(() -> {
            btnSave.setDisable(true);
            updateModel();

            if (event != null) {
                closeDialog();
            }
        });


    }

    public boolean validateForm() {
        return ValidationUtils.validateAll(requiredValidators, customValidators);
    }

    /**
     * Result Handlers for warning dialog if there are uncommitted saves
     */
    protected Consumer onClickOkSave = o -> {
        if (validateForm()) {
            save(null);
            closeDialog();
        }
    };

    protected Consumer onClickCancelSave = o -> closeDialog();

    @FXML
    protected void enableSave(ActionEvent event) {
        btnSave.setDisable(false);
    }

    final protected ChangeListener<Object> changeListener = new ChangeListener<Object>() {
        @Override
        public void changed(ObservableValue<?> observable, Object oldValue, Object newValue) {
            btnSave.setDisable(false);
        }
    };

    /**
     * Cancel, checks to see if there are modifications.  If so, we save em
     *
     * @param event
     */
    @FXML
    protected void close(ActionEvent event) {
        if (!btnSave.isDisabled()) {
            Alert.showWarningWithCancel("Warning", "You have modified the settings, would you like to save?", (Stage) btnClose.getScene().getWindow(), onClickOkSave, onClickCancelSave);
        } else {
            closeDialog();
        }
    }

    /**
     * Close Dialog
     */
    protected void closeDialog() {
        Platform.runLater(() -> {
            Stage stage = (Stage) btnSave.getScene().getWindow();
            stage.close();
        });
    }

    /**
     * Setup Change Listener
     *
     * @param properties
     */
    protected void setupChangeListener(ObservableValue[] properties) {
        for (ObservableValue property : properties) {
            property.addListener(changeListener);
        }
    }

    /**
     * Helper Util for setting date picker
     *
     * @param picker
     * @param date
     */
    protected void setDatePickerValue(DatePicker picker, Date date) {
        if (date != null) {
            Instant instant = date.toInstant();
            LocalDate localDate = instant.atZone(ZoneId.systemDefault()).toLocalDate();
            picker.setValue(localDate);
        } else {
            picker.setValue(null);
        }
    }

    /**
     * Get Integer Value from textfield
     *
     * @param field
     * @return
     */
    protected Integer getIntegerValue(TextField field) {
        try {
            return Integer.valueOf(field.getText());
        } catch (NumberFormatException e) {
            return null;
        }

    }

    /**
     * Get Date Value from date picker
     *
     * @param datePicker
     * @return
     */
    protected Date getDateValue(DatePicker datePicker) {
        if (datePicker.getValue() != null) {
            return new Date(java.sql.Date.valueOf(datePicker.getValue()).getTime());
        } else {
            return null;
        }
    }

    /**
     * clears all fields and sets the default focus
     *
     * @param form
     */
    public void resetForm(Pane form) {
        Iterator<Control> fieldIterator = FormUtil.getControls(form).iterator();
        while (fieldIterator.hasNext()) {
            Control field = (Control) fieldIterator.next();
            if (field.getClass().isAssignableFrom(TextField.class)) {
                ((TextField) field).clear();
            } else if (field.getClass().isAssignableFrom(PasswordField.class)) {
                ((PasswordField) field).clear();
            }
        }

        setDefaultFocus(form);
    }

    /**
     * by default the form will request focus on the first null field in a form
     */
    public void setDefaultFocus(Pane root) {
        Iterator<Control> fieldIterator = FormUtil.getControls(root, TextInputControl.class).iterator();
        while (fieldIterator.hasNext()) {
            Control field = (Control) fieldIterator.next();
            if (field.getClass().isAssignableFrom(TextField.class)) {
                String val = ((TextField) field).getText();
                if (val == null || val.isEmpty()) {
                    field.requestFocus();
                    break;
                }
            } else if (field.getClass().isAssignableFrom(PasswordField.class)) {
                String val = ((TextField) field).getText();
                if (val == null || val.isEmpty()) {
                    field.requestFocus();
                    break;
                }
            }
        }
    }

}
