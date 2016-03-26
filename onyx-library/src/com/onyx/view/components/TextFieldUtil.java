package com.onyx.view.components;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by timothy.osborn on 9/11/14.
 */
public class TextFieldUtil {

    public static void makeNumeric(final TextField tf)
    {
        // TODO: Draw buttons for increment and decrement

        tf.textProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(final ObservableValue<? extends String> ov, final String oldValue, final String newValue) {
                try
                {
                    if(newValue == null || newValue.length() == 0)
                    {
                        return;
                    }
                    Long.parseLong(newValue);
                }catch (NumberFormatException e)
                {
                    tf.textProperty().setValue(oldValue);
                    tf.setText(oldValue);
                }
            }
        });
    }

    /**
     * Adds a static mask to the specified text field.
     *
     * @param tf   the text field.
     * @param mask the mask to apply.
     *             Example of usage: addMask(txtDate, "  /  /    ");
     */
    public static void addMask(final TextField tf, final String mask) {
        tf.setText(mask);
        tf.textProperty().setValue(mask);
        addTextLimiter(tf, mask.length());

        tf.textProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(final ObservableValue<? extends String> ov, final String oldValue, final String newValue) {
                String value = stripMask(tf.getText(), mask);
                tf.setText(merge(value, mask));
            }
        });

        tf.setOnKeyPressed(new EventHandler<KeyEvent>() {
            @Override
            public void handle(final KeyEvent e) {
                int caretPosition = tf.getCaretPosition();
                if (caretPosition < mask.length() - 1 && mask.charAt(caretPosition) != ' ' && e.getCode() != KeyCode.BACK_SPACE && e.getCode() != KeyCode.LEFT) {
                    tf.positionCaret(caretPosition + 1);
                }
            }
        });

        KeyEvent keyEvent = new KeyEvent(KeyEvent.KEY_TYPED, " ", " ", KeyCode.SPACE, false, false, false, false);
        tf.fireEvent(keyEvent);

    }

    static String merge(final String value, final String mask) {
        final StringBuilder sb = new StringBuilder(mask);
        int k = 0;
        for (int i = 0; i < mask.length(); i++) {
            if (mask.charAt(i) == ' ' && k < value.length()) {
                sb.setCharAt(i, value.charAt(k));
                k++;
            }
        }
        return sb.toString();
    }

    static String stripMask(String text, final String mask) {
        final Set<String> maskChars = new HashSet<>();
        for (int i = 0; i < mask.length(); i++) {
            char c = mask.charAt(i);
            if (c != ' ') {
                maskChars.add(String.valueOf(c));
            }
        }
        for (String c : maskChars) {
            if (text != null) {
                text = text.replace(c, "");
            } else {
                text = mask;
            }
        }
        return text;
    }

    public static void addTextLimiter(final TextField tf, final int maxLength) {
        tf.textProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(final ObservableValue<? extends String> ov, final String oldValue, final String newValue) {
                if (tf.getText() != null) {
                    if (tf.getText().length() > maxLength) {
                        String s = tf.getText().substring(0, maxLength);
                        tf.setText(s);
                    }
                }
            }
        });
    }
}
